package pl.quicktask.todo.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.quicktask.todo.auth.AuthRepository
import pl.quicktask.todo.auth.DPoPManager
import pl.quicktask.todo.auth.KeyStore
import pl.quicktask.todo.auth.SessionManager
import pl.quicktask.todo.auth.sharedDPoPManager
import pl.quicktask.todo.auth.sharedAuthRepository
import pl.quicktask.todo.inbox.InboxRepository
import pl.quicktask.todo.inbox.sharedInboxRepository
import pl.quicktask.todo.network.ApiConfig
import kotlin.math.min
import kotlin.random.Random

val sharedSyncCoordinator: SyncCoordinator by lazy { SyncCoordinator() }

class SyncCoordinator(
    private val sessionManager: SessionManager = SessionManager(),
    private val authRepository: AuthRepository = sharedAuthRepository,
    private val dPoPManager: DPoPManager = sharedDPoPManager,
    private val inboxRepository: InboxRepository = sharedInboxRepository,
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
            KeyStore.userKeys.collect { keys ->
                if (keys != null && isAppInForeground && sessionManager.isLoggedIn) {
                    inboxRepository.invalidateCache()
                    coroutineScope.launch {
                        inboxRepository.getItems(forceFetch = true)
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
            inboxRepository.invalidateCache()
            coroutineScope.launch {
                inboxRepository.getItems(forceFetch = true)
            }
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
            KeyStore.getSnapshot() != null &&
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

    private suspend fun runSyncLoop() {
        var currentBackoffMs = 1000L
        var attempt = 0

        while (coroutineScope.isActive && canRunSync()) {
            _syncState.value = SyncState.Connecting

            val token = sessionManager.accessToken
            if (token.isNullOrBlank()) {
                _syncState.value = SyncState.AuthenticationRequired
                break
            }

            val syncUrl = "${baseUrl.trimEnd('/')}/sync/events"
            val dpopProof = try {
                dPoPManager.generateDPoPProof("GET", syncUrl, token)
            } catch (e: Exception) {
                _syncState.value = SyncState.Disconnected
                break
            }

            var activeResult = syncEventSource.connectAndListen(
                url = syncUrl,
                accessToken = token,
                dpopProof = dpopProof,
                onEvent = { event ->
                    if (_syncState.value != SyncState.Connected) {
                        _syncState.value = SyncState.Connected
                        currentBackoffMs = 1000L
                        attempt = 0
                    }
                    handleIncomingEvent(event)
                },
            )

            if (!canRunSync()) break

            if (activeResult is SyncConnectionResult.HttpError && activeResult.statusCode == 498) {
                val refreshed = authRepository.refreshSession()
                if (refreshed.isSuccess) {
                    val newToken = sessionManager.accessToken
                    if (!newToken.isNullOrBlank()) {
                        val newDpop = dPoPManager.generateDPoPProof("GET", syncUrl, newToken)
                        activeResult = syncEventSource.connectAndListen(
                            url = syncUrl,
                            accessToken = newToken,
                            dpopProof = newDpop,
                            onEvent = { event ->
                                if (_syncState.value != SyncState.Connected) {
                                    _syncState.value = SyncState.Connected
                                    currentBackoffMs = 1000L
                                    attempt = 0
                                }
                                handleIncomingEvent(event)
                            },
                        )
                    } else {
                        _syncState.value = SyncState.AuthenticationRequired
                        authRepository.logout()
                        break
                    }
                } else {
                    _syncState.value = SyncState.AuthenticationRequired
                    authRepository.logout()
                    break
                }
            }

            if (!canRunSync()) break

            when (activeResult) {
                is SyncConnectionResult.Completed -> {
                    attempt++
                    val delayWithJitter = randomJitterSupplier(currentBackoffMs)
                    _syncState.value = SyncState.Retrying(attempt, delayWithJitter)
                    currentBackoffMs = min(currentBackoffMs * 2, 30_000L)
                    try {
                        delay(delayWithJitter)
                    } catch (_: Exception) {
                        break
                    }
                }
                is SyncConnectionResult.HttpError -> {
                    if (activeResult.statusCode == 401) {
                        _syncState.value = SyncState.AuthenticationRequired
                        authRepository.logout()
                        break
                    } else {
                        attempt++
                        val delayWithJitter = randomJitterSupplier(currentBackoffMs)
                        _syncState.value = SyncState.Retrying(attempt, delayWithJitter)
                        currentBackoffMs = min(currentBackoffMs * 2, 30_000L)
                        try {
                            delay(delayWithJitter)
                        } catch (_: Exception) {
                            break
                        }
                    }
                }
                is SyncConnectionResult.NetworkError -> {
                    attempt++
                    val delayWithJitter = randomJitterSupplier(currentBackoffMs)
                    _syncState.value = SyncState.Retrying(attempt, delayWithJitter)
                    currentBackoffMs = min(currentBackoffMs * 2, 30_000L)
                    try {
                        delay(delayWithJitter)
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }

        if (!canRunSync() && _syncState.value != SyncState.AuthenticationRequired) {
            _syncState.value = SyncState.Disconnected
        }
    }

    private fun handleIncomingEvent(event: SyncEvent) {
        when (event) {
            is SyncEvent.SyncRequired -> {
                scheduleDebouncedRefresh()
            }
            is SyncEvent.ItemsChanged -> {
                if (!deduplicator.isDuplicate(event.eventId)) {
                    scheduleDebouncedRefresh()
                }
            }
        }
    }

    private fun scheduleDebouncedRefresh() {
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(debounceMs)
            inboxRepository.invalidateCache()
            inboxRepository.getItems(forceFetch = true)
        }
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
            _syncState.value = SyncState.Disconnected
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
