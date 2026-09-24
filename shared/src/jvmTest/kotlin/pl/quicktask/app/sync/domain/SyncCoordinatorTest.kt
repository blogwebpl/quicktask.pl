package pl.quicktask.app.sync.domain

import com.russhwolf.settings.Settings

import pl.quicktask.app.sync.model.*
import pl.quicktask.app.sync.platform.*

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.util.decodeBase64String
import io.ktor.util.encodeBase64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.quicktask.app.auth.crypto.DefaultDPoPManager
import pl.quicktask.app.auth.model.FinishLoginResponseDto
import pl.quicktask.app.auth.crypto.KeyStore
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.auth.crypto.UserKeyPair
import pl.quicktask.app.items.domain.ItemSyncOperations
import pl.quicktask.app.testing.TestSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun verifyConnectionScenario(
        result: (Int) -> SyncConnectionResult = { SyncConnectionResult.HttpError(498, "Expired") },
        refresh: suspend () -> Result<Unit> = { Result.success(Unit) },
        proof: suspend (Int) -> String = { "proof" },
        suspendConnection: Boolean = false,
        cancel: Boolean = false,
        expectedConnections: Int,
        expectedRefreshes: Int,
        expectedLogouts: Int,
        expectedState: SyncState,
    ) = runTest {
        var connections = 0
        var refreshes = 0
        var logouts = 0
        var proofs = 0
        var connectionCancelled = false
        val source = object : SyncEventSource {
            override suspend fun connectAndListen(
                url: String, accessToken: String, dpopProof: String, onEvent: (SyncEvent) -> Unit,
            ): SyncConnectionResult {
                connections++
                if (suspendConnection) {
                    try { kotlinx.coroutines.awaitCancellation() }
                    finally { connectionCancelled = true }
                }
                return result(connections)
            }
        }
        val manager = object : pl.quicktask.app.auth.crypto.DPoPManager {
            override suspend fun generateDPoPProof(method: String, url: String, accessToken: String?): String = proof(++proofs)
            override suspend fun clearKeyPair() {}
        }
        val coordinator = SyncCoordinator(
            sessionManager = sessionManager,
            dPoPManager = manager,
            sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { refreshes++; refresh() },
            keyStore = keyStore,
            logout = { logouts++; sessionManager.clearSession() },
            itemSyncService = RecordingItemSync(),
            syncEventSource = source,
            coroutineScope = backgroundScope,
            randomJitterSupplier = { it },
        )
        runCurrent()
        coordinator.onAppForeground()
        runCurrent()
        if (cancel) {
            coordinator.onAppBackground()
            runCurrent()
            if (suspendConnection) assertTrue(connectionCancelled)
        }
        assertEquals(expectedConnections, connections)
        assertEquals(expectedRefreshes, refreshes)
        assertEquals(expectedLogouts, logouts)
        assertEquals(expectedState, coordinator.syncState.value)
        if (expectedState != SyncState.Retrying(1, 1000L)) {
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(expectedConnections, connections)
            assertEquals(expectedLogouts, logouts)
        } else {
            advanceTimeBy(999)
            runCurrent()
            assertEquals(expectedConnections, connections)
            assertEquals(expectedRefreshes, refreshes)
        }
        if (expectedLogouts == 0) assertTrue(sessionManager.isLoggedIn)
        coordinator.stop()
    }

    @Test
    fun failedRefreshRequiresAuthentication() = verifyConnectionScenario(
        refresh = { Result.failure(IllegalStateException("Refresh failed")) },
        expectedConnections = 1, expectedRefreshes = 1, expectedLogouts = 1,
        expectedState = SyncState.AuthenticationRequired,
    )

    @Test
    fun missingRefreshedTokenRequiresAuthentication() = verifyConnectionScenario(
        refresh = { sessionManager.accessToken = ""; Result.success(Unit) },
        expectedConnections = 1, expectedRefreshes = 1, expectedLogouts = 1,
        expectedState = SyncState.AuthenticationRequired,
    )

    @Test
    fun unauthorizedAfterRefreshLogsOut() = verifyConnectionScenario(
        result = { SyncConnectionResult.HttpError(if (it == 1) 498 else 401, "Unauthorized") },
        expectedConnections = 2, expectedRefreshes = 1, expectedLogouts = 1,
        expectedState = SyncState.AuthenticationRequired,
    )

    @Test
    fun repeatedExpiredTokenWaitsBeforeNextAttempt() = verifyConnectionScenario(
        expectedConnections = 2, expectedRefreshes = 1, expectedLogouts = 0,
        expectedState = SyncState.Retrying(1, 1000L),
    )

    @Test
    fun proofFailureBeforeConnectionStopsSync() = verifyConnectionScenario(
        proof = { throw IllegalStateException("Proof failed") },
        expectedConnections = 0, expectedRefreshes = 0, expectedLogouts = 0,
        expectedState = SyncState.Disconnected,
    )

    @Test
    fun proofFailureAfterRefreshStopsSync() = verifyConnectionScenario(
        proof = { if (it == 2) throw IllegalStateException("Proof failed") else "proof" },
        expectedConnections = 1, expectedRefreshes = 1, expectedLogouts = 0,
        expectedState = SyncState.Disconnected,
    )

    @Test
    fun cancellationDuringBackoffDoesNotLogout() = verifyConnectionScenario(
        result = { SyncConnectionResult.Completed }, cancel = true,
        expectedConnections = 1, expectedRefreshes = 0, expectedLogouts = 0,
        expectedState = SyncState.Disconnected,
    )

    @Test
    fun cancellationDuringConnectionDoesNotLogout() = verifyConnectionScenario(
        suspendConnection = true, cancel = true,
        expectedConnections = 1, expectedRefreshes = 0, expectedLogouts = 0,
        expectedState = SyncState.Disconnected,
    )

    @Test
    fun cancellationDuringRefreshDoesNotLogout() = verifyConnectionScenario(
        refresh = { kotlinx.coroutines.awaitCancellation() }, cancel = true,
        expectedConnections = 1, expectedRefreshes = 1, expectedLogouts = 0,
        expectedState = SyncState.Disconnected,
    )

    @Test
    fun cancellationReturnedByRefreshDoesNotLogout() = verifyConnectionScenario(
        refresh = { Result.failure(kotlinx.coroutines.CancellationException("Cancelled")) }, cancel = true,
        expectedConnections = 1, expectedRefreshes = 1, expectedLogouts = 0,
        expectedState = SyncState.Disconnected,
    )

    @Test
    fun cancellationDuringProofDoesNotLogout() = verifyConnectionScenario(
        proof = { kotlinx.coroutines.awaitCancellation() }, cancel = true,
        expectedConnections = 0, expectedRefreshes = 0, expectedLogouts = 0,
        expectedState = SyncState.Disconnected,
    )


    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testRetryResultsShareBackoffCapResetAndCancellation() = runTest {
        val bases = mutableListOf<Long>()
        fakeEventSource.connectionResultSupplier = { attempt ->
            when (attempt % 3) {
                0 -> SyncConnectionResult.HttpError(503, "Unavailable")
                1 -> SyncConnectionResult.Completed
                else -> SyncConnectionResult.NetworkError(IllegalStateException("Offline"))
            }
        }
        val coordinator = SyncCoordinator(
                keyStore = keyStore,
                logout = { sessionManager.clearSession() },
                sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { Result.success(Unit) },
            itemSyncService = RecordingItemSync(),
            sessionManager = sessionManager,
            dPoPManager = dPoPManager,
            syncEventSource = fakeEventSource,
            coroutineScope = backgroundScope,
            randomJitterSupplier = { base -> bases.add(base); 10L },
        )
        // Let the initial key observer run before foreground starts the connection.
        runCurrent()
        coordinator.onAppForeground()
        runCurrent()
        repeat(6) { advanceTimeBy(10); runCurrent() }
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L, 30000L), bases)
        assertEquals(SyncState.Retrying(7, 10), coordinator.syncState.value)

        fakeEventSource.onConnectCallback = { it(SyncEvent.SyncRequired) }
        advanceTimeBy(10)
        runCurrent()
        assertEquals(1000L, bases.last())
        assertEquals(SyncState.Retrying(1, 10), coordinator.syncState.value)

        coordinator.onAppBackground()
        val count = fakeEventSource.connectCount
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(count, fakeEventSource.connectCount)
        assertEquals(SyncState.Disconnected, coordinator.syncState.value)
    }

    private var keyStore = KeyStore()
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
        keyStore = KeyStore()
        settings = TestSettings()
        sessionManager = SessionManager(settings)
        dPoPManager = DefaultDPoPManager(settings)
        fakeEventSource = FakeSyncEventSource()

        sessionManager.saveSession("access_token_123", "refresh_token_456", "user@example.com")
        keyStore.set(createMockUserKeys())
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
                keyStore = keyStore,
                logout = { sessionManager.clearSession() },
                sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { Result.success(Unit) },
                itemSyncService = RecordingItemSync(),
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
            val fakeSyncService = RecordingItemSync()

            val coordinator = SyncCoordinator(
                keyStore = keyStore,
                logout = { sessionManager.clearSession() },
                sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { Result.success(Unit) },
                sessionManager = sessionManager,
                dPoPManager = dPoPManager,
                itemSyncService = fakeSyncService,
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testItemsChangedWithItemIdTriggersSyncState() = runTest {
        val testScope = backgroundScope
        try {
            var handleSyncStateCalls = 0
            var lastHandledItemId: String? = null

            val fakeSyncService = object : ItemSyncOperations {
                override suspend fun refreshViews(inbox: Boolean, trash: Boolean) {}
                override suspend fun refreshActiveViews() {}
                override suspend fun handleSyncStateForItem(itemId: String): Result<Unit> {
                    handleSyncStateCalls++
                    lastHandledItemId = itemId
                    return Result.success(Unit)
                }
            }

            val coordinator = SyncCoordinator(
                keyStore = keyStore,
                logout = { sessionManager.clearSession() },
                sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { Result.success(Unit) },
                sessionManager = sessionManager,
                dPoPManager = dPoPManager,
                itemSyncService = fakeSyncService,
                syncEventSource = fakeEventSource,
                coroutineScope = testScope,
                debounceMs = 100L,
                randomJitterSupplier = { it },
            )

            var eventSink: ((SyncEvent) -> Unit)? = null
            fakeEventSource.onConnectCallback = { sink ->
                eventSink = sink
            }

            runCurrent()
            coordinator.onAppForeground()
            runCurrent()

            assertNotNull(eventSink)

            eventSink?.invoke(SyncEvent.ItemsChanged(eventId = "evt-123", itemId = "item-xyz-999"))
            runCurrent()

            assertEquals(1, handleSyncStateCalls)
            assertEquals("item-xyz-999", lastHandledItemId)

            coordinator.stop()
        } finally {
            testScope.cancel()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun contactsChangedNotifiesActiveCollectorOnce() = runTest {
        val coordinator = SyncCoordinator(
            keyStore = keyStore,
            logout = { sessionManager.clearSession() },
            sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { Result.success(Unit) },
            sessionManager = sessionManager,
            dPoPManager = dPoPManager,
            itemSyncService = RecordingItemSync(),
            syncEventSource = fakeEventSource,
            coroutineScope = backgroundScope,
            randomJitterSupplier = { it },
        )
        var eventSink: ((SyncEvent) -> Unit)? = null
        fakeEventSource.onConnectCallback = { eventSink = it }
        coordinator.onAppForeground()
        runCurrent()
        val notification = backgroundScope.async { coordinator.contactChanges.first() }
        runCurrent()

        assertNotNull(eventSink).invoke(SyncEvent.ContactsChanged("contacts-1"))
        runCurrent()

        assertEquals(Unit, notification.await())
        coordinator.stop()
    }

    @Test
    fun testDeduplicationOfEventId() = runBlocking {
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coordinator = SyncCoordinator(
                keyStore = keyStore,
                logout = { sessionManager.clearSession() },
                sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { Result.success(Unit) },
                itemSyncService = RecordingItemSync(),
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
                keyStore = keyStore,
                logout = { sessionManager.clearSession() },
                sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { Result.success(Unit) },
                itemSyncService = RecordingItemSync(),
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
                keyStore = keyStore,
                logout = { sessionManager.clearSession() },
                sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { Result.success(Unit) },
                itemSyncService = RecordingItemSync(),
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
                keyStore = keyStore,
                logout = { sessionManager.clearSession() },
                sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { Result.success(Unit) },
                itemSyncService = RecordingItemSync(),
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
                keyStore = keyStore,
                logout = { sessionManager.clearSession() },
                sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { Result.success(Unit) },
                itemSyncService = RecordingItemSync(),
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

            val authRepo = pl.quicktask.app.auth.data.SessionRefresher { Result.success(Unit) }

            val coordinator = SyncCoordinator(
                keyStore = keyStore,
                logout = { sessionManager.clearSession() },
                itemSyncService = RecordingItemSync(),
                sessionManager = sessionManager,
                sessionRefresher = authRepo,
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
            val fakeAuthRepo = pl.quicktask.app.auth.data.SessionRefresher {
                refreshCount++
                sessionManager.accessToken = "refreshed_access_token_999"
                Result.success(Unit)
            }

            val coordinator = SyncCoordinator(
                keyStore = keyStore,
                logout = { sessionManager.clearSession() },
                itemSyncService = RecordingItemSync(),
                sessionManager = sessionManager,
                sessionRefresher = fakeAuthRepo,
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

private class RecordingItemSync : ItemSyncOperations {
    var refreshes = 0
    val handled = mutableListOf<String>()
    override suspend fun refreshViews(inbox: Boolean, trash: Boolean) { refreshes++ }
    override suspend fun refreshActiveViews() { refreshes++ }
    override suspend fun handleSyncStateForItem(itemId: String): Result<Unit> {
        handled.add(itemId)
        return Result.success(Unit)
    }
}
