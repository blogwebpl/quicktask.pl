package pl.quicktask.app.sync.domain

import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.quicktask.app.auth.crypto.DPoPManager
import pl.quicktask.app.auth.crypto.KeyStore
import pl.quicktask.app.auth.data.SessionRefresher
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.items.domain.ItemSyncOperations
import pl.quicktask.app.network.client.SESSION_EXPIRED_STATUS
import pl.quicktask.app.network.config.ApiConfig
import pl.quicktask.app.sync.model.*
import pl.quicktask.app.sync.platform.*

private const val INITIAL_BACKOFF_MS = 1_000L
private const val MAX_BACKOFF_MS = 30_000L

class SyncCoordinator(
    private val sessionManager: SessionManager,
    private val dPoPManager: DPoPManager,
    private val sessionRefresher: SessionRefresher,
    private val keyStore: KeyStore,
    private val logout: suspend () -> Unit,
    private val itemSyncService: ItemSyncOperations,
    private val syncEventSource: SyncEventSource = createSyncEventSource(),
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val debounceMs: Long = 200L,
    private val randomJitterSupplier: (Long) -> Long = { baseMs -> calculateJitter(baseMs) },
) {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Disconnected)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val deduplicator = EventDeduplicator(maxSize = 100)
    private val syncMutex = Mutex()
    private var syncJob: Job? = null
    private var debounceJob: Job? = null

    var isAppInForeground: Boolean = false
        private set

    init {
        coroutineScope.launch {
            keyStore.userKeys.collect { keys ->
                if (keys != null && isAppInForeground && sessionManager.isLoggedIn) {
                    coroutineScope.launch {
                        itemSyncService.refreshActiveViews()
                    }
                    startSyncInternal()
                } else if (keys == null) {
                    stopInternal()
                }
            }
        }
    }

    fun onAppForeground() {
        isAppInForeground = true
        if (canRunSync()) {
            scheduleDebouncedRefresh()
            startSyncInternal()
        }
    }

    fun onAppBackground() {
        isAppInForeground = false
        stopInternal()
    }

    fun stop() {
        stopInternal()
    }

    fun onNetworkAvailable() {
        if (canRunSync()) {
            scheduleDebouncedRefresh()
            val currentState = _syncState.value
            if (currentState is SyncState.Retrying || currentState is SyncState.Disconnected) {
                // Cancel current backoff delay / retry immediately
                cancelSyncJobOnly()
                startSyncInternal()
            }
        }
    }

    fun canRunSync(): Boolean {
        return sessionManager.isLoggedIn &&
            !sessionManager.accessToken.isNullOrBlank() &&
            keyStore.getSnapshot() != null &&
            isAppInForeground
    }

    private fun startSyncInternal() {
        coroutineScope.launch {
            syncMutex.withLock {
                if (syncJob?.isActive == true) {
                    return@withLock
                }
                if (!canRunSync()) {
                    return@withLock
                }

                syncJob = coroutineScope.launch {
                    runSyncLoop()
                }
            }
        }
    }

    private sealed interface ConnectionAttempt {
        data class Completed(val result: SyncConnectionResult) : ConnectionAttempt
        data object AuthenticationRequired : ConnectionAttempt
        data object ProofFailed : ConnectionAttempt
    }

    private suspend fun runSyncLoop() {
        var currentBackoffMs = INITIAL_BACKOFF_MS
        var attempt = 0
        val onEvent: (SyncEvent) -> Unit = { event ->
            if (_syncState.value != SyncState.Connected) {
                updateSyncState(SyncState.Connected)
                currentBackoffMs = INITIAL_BACKOFF_MS
                attempt = 0
            }
            handleIncomingEvent(event)
        }
        while (coroutineScope.isActive && canRunSync()) {
            updateSyncState(SyncState.Connecting)
            val connection = connectWithSessionRefresh(onEvent)
            if (!canRunSync()) break
            when (connection) {
                ConnectionAttempt.AuthenticationRequired -> break
                ConnectionAttempt.ProofFailed -> {
                    updateSyncState(SyncState.Disconnected)
                    break
                }
                is ConnectionAttempt.Completed -> {
                    if (connection.result is SyncConnectionResult.HttpError && connection.result.statusCode == 401) {
                        requireAuthentication()
                        break
                    }
                }
            }
            attempt++
            val delayWithJitter = randomJitterSupplier(currentBackoffMs)
            updateSyncState(SyncState.Retrying(attempt, delayWithJitter))
            currentBackoffMs = min(currentBackoffMs * 2, MAX_BACKOFF_MS)
            delay(delayWithJitter)
        }
        if (!canRunSync() && _syncState.value != SyncState.AuthenticationRequired) {
            updateSyncState(SyncState.Disconnected)
        }
    }

    private suspend fun connectWithSessionRefresh(onEvent: (SyncEvent) -> Unit): ConnectionAttempt {
        val token = sessionManager.accessToken
        if (token.isNullOrBlank()) {
            updateSyncState(SyncState.AuthenticationRequired)
            return ConnectionAttempt.AuthenticationRequired
        }
        val connection = connect(token, onEvent)
        if (!canRunSync()) return connection
        if (connection is ConnectionAttempt.Completed &&
            connection.result is SyncConnectionResult.HttpError &&
            connection.result.statusCode == SESSION_EXPIRED_STATUS
        ) return refreshSessionAndReconnect(onEvent)
        return connection
    }

    private suspend fun connect(token: String, onEvent: (SyncEvent) -> Unit): ConnectionAttempt {
        val syncUrl = "${baseUrl.trimEnd('/')}/sync/events"
        val proof = try {
            dPoPManager.generateDPoPProof("GET", syncUrl, token)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return ConnectionAttempt.ProofFailed
        }
        return ConnectionAttempt.Completed(syncEventSource.connectAndListen(
            url = syncUrl,
            accessToken = token,
            dpopProof = proof,
            onEvent = onEvent,
        ))
    }

    private suspend fun refreshSessionAndReconnect(onEvent: (SyncEvent) -> Unit): ConnectionAttempt {
        val refreshed = sessionRefresher.refreshSession()
        (refreshed.exceptionOrNull() as? CancellationException)?.let { throw it }
        val token = sessionManager.accessToken
        if (refreshed.isFailure || token.isNullOrBlank()) return requireAuthentication()
        return connect(token, onEvent)
    }

    private suspend fun requireAuthentication(): ConnectionAttempt {
        updateSyncState(SyncState.AuthenticationRequired)
        logout()
        return ConnectionAttempt.AuthenticationRequired
    }

    private fun handleIncomingEvent(event: SyncEvent) {
        AppLoggerManager.logRefresh("SyncCoordinator", "Otrzymano zdarzenie synch: ${event::class.simpleName}")
        when (event) {
            is SyncEvent.SyncRequired -> {
                scheduleDebouncedRefresh()
            }
            is SyncEvent.ItemsChanged -> {
                if (!deduplicator.isDuplicate(event.eventId)) {
                    val itemId = event.itemId
                    if (!itemId.isNullOrBlank()) {
                        coroutineScope.launch {
                            val result = itemSyncService.handleSyncStateForItem(itemId)
                            if (result.isFailure) {
                                scheduleDebouncedRefresh()
                            }
                        }
                    } else {
                        scheduleDebouncedRefresh()
                    }
                }
            }
        }
    }

    private fun scheduleDebouncedRefresh() {
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(debounceMs)
            AppLoggerManager.logRefresh("SyncCoordinator", "Wykonanie odświeżenia widoków (refreshActiveViews)")
            itemSyncService.refreshActiveViews()
        }
    }

    private fun updateSyncState(newState: SyncState) {
        _syncState.value = newState
        AppLoggerManager.logStateChange("SyncCoordinator", "Stan synch: ${newState::class.simpleName}")
    }

    private fun cancelSyncJobOnly() {
        syncJob?.cancel()
        syncJob = null
    }

    private fun stopInternal() {
        cancelSyncJobOnly()
        debounceJob?.cancel()
        debounceJob = null
        deduplicator.clear()
        if (_syncState.value != SyncState.AuthenticationRequired) {
            updateSyncState(SyncState.Disconnected)
        }
    }

    companion object {
        fun calculateJitter(baseMs: Long): Long {
            val minMs = (baseMs * 0.8).toLong()
            val maxMs = (baseMs * 1.2).toLong()
            return Random.nextLong(minMs, maxMs + 1)
        }
    }
}
