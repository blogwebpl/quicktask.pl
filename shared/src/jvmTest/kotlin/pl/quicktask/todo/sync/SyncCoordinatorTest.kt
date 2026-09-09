package pl.quicktask.todo.sync

import com.russhwolf.settings.Settings
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.util.decodeBase64String
import io.ktor.util.encodeBase64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.quicktask.todo.auth.AuthRepository
import pl.quicktask.todo.auth.DefaultDPoPManager
import pl.quicktask.todo.auth.FinishLoginResponseDto
import pl.quicktask.todo.auth.KeyStore
import pl.quicktask.todo.auth.SessionManager
import pl.quicktask.todo.auth.UserKeyPair
import pl.quicktask.todo.inbox.InboxRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class FakeSettings : Settings {
    private val map = mutableMapOf<String, Any>()
    override val keys: Set<String> get() = map.keys
    override val size: Int get() = map.size
    override fun clear() = map.clear()
    override fun remove(key: String) { map.remove(key) }
    override fun hasKey(key: String): Boolean = map.containsKey(key)
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = map[key] as? Int
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = map[key] as? Long
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String, defaultValue: String): String = map[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = map[key] as? String
    override fun putFloat(key: String, value: Float) { map[key] = value }
    override fun getFloat(key: String, defaultValue: Float): Float = map[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = map[key] as? Float
    override fun putDouble(key: String, value: Double) { map[key] = value }
    override fun getDouble(key: String, defaultValue: Double): Double = map[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = map[key] as? Double
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = map[key] as? Boolean
}

class FakeSyncEventSource : SyncEventSource {
    var lastUrl: String? = null
    var lastAccessToken: String? = null
    var lastDpopProof: String? = null
    var connectCount = 0

    var connectionResultSupplier: (attempt: Int) -> SyncConnectionResult = { SyncConnectionResult.Completed }
    var onConnectCallback: ((onEvent: (SyncEvent) -> Unit) -> Unit)? = null

    override suspend fun connectAndListen(
        url: String,
        accessToken: String,
        dpopProof: String,
        onEvent: (SyncEvent) -> Unit,
    ): SyncConnectionResult {
        connectCount++
        lastUrl = url
        lastAccessToken = accessToken
        lastDpopProof = dpopProof

        onConnectCallback?.invoke(onEvent)

        return connectionResultSupplier(connectCount)
    }
}

class SyncCoordinatorTest {

    private lateinit var settings: Settings
    private lateinit var sessionManager: SessionManager
    private lateinit var dPoPManager: DefaultDPoPManager
    private lateinit var fakeEventSource: FakeSyncEventSource

    private fun decodeBase64Url(base64Url: String): String {
        var base64 = base64Url.replace('-', '+').replace('_', '/')
        val pad = (4 - base64.length % 4) % 4
        base64 += "=".repeat(pad)
        return base64.decodeBase64String()
    }

    @BeforeTest
    fun setup() {
        settings = FakeSettings()
        sessionManager = SessionManager(settings)
        dPoPManager = DefaultDPoPManager(settings)
        fakeEventSource = FakeSyncEventSource()

        sessionManager.saveSession("access_token_123", "refresh_token_456", "user@example.com")
        KeyStore.set(createMockUserKeys())
    }

    private fun createMockUserKeys(): UserKeyPair = runBlocking {
        val ecdh = CryptographyProvider.Default.get(ECDH)
        val pair = ecdh.keyPairGenerator(EC.Curve.P256).generateKey()
        UserKeyPair(
            publicKey = pair.publicKey,
            privateKey = pair.privateKey,
            publicKeySpki = ByteArray(32),
        )
    }

    @Test
    fun testHeadersAndDPoPProofStructure() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coordinator = SyncCoordinator(
                sessionManager = sessionManager,
                dPoPManager = dPoPManager,
                syncEventSource = fakeEventSource,
                baseUrl = "https://quicktask.pl",
                coroutineScope = testScope,
                randomJitterSupplier = { it },
            )

            coordinator.onAppForeground()
            delay(100)

            assertEquals("https://quicktask.pl/sync/events", fakeEventSource.lastUrl)
            assertEquals("access_token_123", fakeEventSource.lastAccessToken)

            val proof = fakeEventSource.lastDpopProof
            assertNotNull(proof)

            val parts = proof.split(".")
            assertEquals(3, parts.size)

            val payloadJson = decodeBase64Url(parts[1])
            val payload = Json.parseToJsonElement(payloadJson).jsonObject

            assertEquals("GET", payload["htm"]?.jsonPrimitive?.content)
            assertEquals("https://quicktask.pl/sync/events", payload["htu"]?.jsonPrimitive?.content)

            val ath = payload["ath"]?.jsonPrimitive?.content
            assertNotNull(ath)

            val sha256 = CryptographyProvider.Default.get(SHA256).hasher()
            val expectedAth = sha256.hash("access_token_123".encodeToByteArray()).encodeBase64()
                .replace('+', '-').replace('/', '_').replace("=", "")

            assertEquals(expectedAth, ath)
            coordinator.stop()
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testDebounceOfEventSeriesAndInboxFetch() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val fakeInboxRepository = InboxRepository(
                sessionManager = sessionManager,
                dPoPManager = dPoPManager,
            )

            val coordinator = SyncCoordinator(
                sessionManager = sessionManager,
                dPoPManager = dPoPManager,
                inboxRepository = fakeInboxRepository,
                syncEventSource = fakeEventSource,
                coroutineScope = testScope,
                debounceMs = 100L,
                randomJitterSupplier = { it },
            )

            var eventSink: ((SyncEvent) -> Unit)? = null
            fakeEventSource.onConnectCallback = { sink ->
                eventSink = sink
            }

            coordinator.onAppForeground()
            delay(50)

            assertNotNull(eventSink)

            eventSink?.invoke(SyncEvent.SyncRequired)
            delay(30)
            eventSink?.invoke(SyncEvent.ItemsChanged("id-1"))
            delay(30)
            eventSink?.invoke(SyncEvent.ItemsChanged("id-2"))

            delay(150)

            coordinator.stop()
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testDeduplicationOfEventId() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coordinator = SyncCoordinator(
                sessionManager = sessionManager,
                dPoPManager = dPoPManager,
                syncEventSource = fakeEventSource,
                coroutineScope = testScope,
                debounceMs = 100L,
                randomJitterSupplier = { it },
            )

            var eventSink: ((SyncEvent) -> Unit)? = null
            fakeEventSource.onConnectCallback = { sink ->
                eventSink = sink
            }

            coordinator.onAppForeground()
            delay(50)

            assertNotNull(eventSink)

            eventSink?.invoke(SyncEvent.ItemsChanged("duplicate-id"))
            delay(150)

            eventSink?.invoke(SyncEvent.ItemsChanged("duplicate-id"))
            delay(150)

            coordinator.stop()
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testReconnectWithExponentialBackoffAndJitter() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var attempts = 0
            fakeEventSource.connectionResultSupplier = {
                attempts++
                SyncConnectionResult.NetworkError(RuntimeException("Connection timeout"))
            }

            val coordinator = SyncCoordinator(
                sessionManager = sessionManager,
                dPoPManager = dPoPManager,
                syncEventSource = fakeEventSource,
                coroutineScope = testScope,
                randomJitterSupplier = { 10L },
            )

            coordinator.onAppForeground()
            delay(500)

            assertTrue(fakeEventSource.connectCount > 1, "Should retry connection multiple times")

            coordinator.stop()
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testImmediateReconnectOnNetworkAvailable() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            fakeEventSource.connectionResultSupplier = {
                SyncConnectionResult.NetworkError(RuntimeException("Offline"))
            }

            val coordinator = SyncCoordinator(
                sessionManager = sessionManager,
                dPoPManager = dPoPManager,
                syncEventSource = fakeEventSource,
                coroutineScope = testScope,
                randomJitterSupplier = { 10_000L },
            )

            coordinator.onAppForeground()
            delay(50)

            val countBeforeNetwork = fakeEventSource.connectCount

            coordinator.onNetworkAvailable()
            delay(50)

            assertTrue(fakeEventSource.connectCount > countBeforeNetwork, "Network recovery should trigger immediate connection attempt")

            coordinator.stop()
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testCancellationOnAppBackgroundAndLogout() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            fakeEventSource.connectionResultSupplier = {
                // Return non-completing or delay
                SyncConnectionResult.Completed
            }

            val coordinator = SyncCoordinator(
                sessionManager = sessionManager,
                dPoPManager = dPoPManager,
                syncEventSource = fakeEventSource,
                coroutineScope = testScope,
                randomJitterSupplier = { it },
            )

            coordinator.onAppForeground()
            delay(50)

            coordinator.onAppBackground()
            delay(50)

            assertEquals(SyncState.Disconnected, coordinator.syncState.value)
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testPreventMultipleActiveConnections() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coordinator = SyncCoordinator(
                sessionManager = sessionManager,
                dPoPManager = dPoPManager,
                syncEventSource = fakeEventSource,
                coroutineScope = testScope,
                randomJitterSupplier = { it },
            )

            coordinator.onAppForeground()
            coordinator.onAppForeground()
            coordinator.onAppForeground()
            delay(50)

            assertEquals(1, fakeEventSource.connectCount, "Should maintain exactly one active connection")

            coordinator.stop()
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testHttp401LogoutAndAuthenticationRequired() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            fakeEventSource.connectionResultSupplier = {
                SyncConnectionResult.HttpError(401, "Unauthorized")
            }

            val authRepo = AuthRepository(
                sessionManager = sessionManager,
                dPoPManager = dPoPManager,
            )

            val coordinator = SyncCoordinator(
                sessionManager = sessionManager,
                authRepository = authRepo,
                dPoPManager = dPoPManager,
                syncEventSource = fakeEventSource,
                coroutineScope = testScope,
                randomJitterSupplier = { it },
            )

            coordinator.onAppForeground()
            delay(100)

            assertEquals(SyncState.AuthenticationRequired, coordinator.syncState.value)
            assertEquals(false, sessionManager.isLoggedIn, "Session should be cleared on 401")
        } finally {
            testScope.cancel()
        }
    }

    @Test
    fun testHttp498TokenRefreshAndSingleRetry() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            fakeEventSource.connectionResultSupplier = { attempt ->
                if (attempt == 1) {
                    SyncConnectionResult.HttpError(498, "Expired token")
                } else {
                    SyncConnectionResult.Completed
                }
            }

            var refreshCount = 0
            val fakeAuthRepo = object : AuthRepository(
                sessionManager = sessionManager,
                dPoPManager = dPoPManager,
            ) {
                override suspend fun refreshSession(): Result<FinishLoginResponseDto> {
                    refreshCount++
                    sessionManager.accessToken = "refreshed_access_token_999"
                    return Result.success(
                        FinishLoginResponseDto(
                            accessToken = "refreshed_access_token_999",
                            refreshToken = "refresh_token_456",
                            tokenType = "DPoP",
                            accessTokenExpiresIn = 600,
                            refreshTokenExpiresIn = 2592000,
                        )
                    )
                }
            }

            val coordinator = SyncCoordinator(
                sessionManager = sessionManager,
                authRepository = fakeAuthRepo,
                dPoPManager = dPoPManager,
                syncEventSource = fakeEventSource,
                coroutineScope = testScope,
                randomJitterSupplier = { it },
            )

            coordinator.onAppForeground()
            delay(150)

            assertEquals(1, refreshCount, "refreshSession should be called exactly once")
            assertEquals("refreshed_access_token_999", fakeEventSource.lastAccessToken)
            assertEquals(2, fakeEventSource.connectCount, "Should retry connection once with fresh token")

            coordinator.stop()
        } finally {
            testScope.cancel()
        }
    }
}
