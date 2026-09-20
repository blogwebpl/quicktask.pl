package pl.quicktask.app.di

import io.ktor.client.HttpClient
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import pl.quicktask.app.auth.crypto.DPoPManager
import pl.quicktask.app.auth.crypto.DefaultDPoPManager
import pl.quicktask.app.auth.crypto.KeyStore
import pl.quicktask.app.auth.crypto.OpaqueManager
import pl.quicktask.app.auth.crypto.createOpaqueManager
import pl.quicktask.app.auth.data.AuthApiClient
import pl.quicktask.app.auth.data.AuthRepository
import pl.quicktask.app.auth.data.SessionRefreshService
import pl.quicktask.app.auth.data.UserKeyService
import pl.quicktask.app.auth.session.KeyCache
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.common.AppLogger
import pl.quicktask.app.common.ConsoleAppLogger
import pl.quicktask.app.items.di.ItemModule
import pl.quicktask.app.settings.data.SettingsOperations
import pl.quicktask.app.items.domain.CachedUserKeysProvider
import pl.quicktask.app.network.client.sharedHttpClient
import pl.quicktask.app.network.config.ApiConfig
import pl.quicktask.app.sync.domain.SyncCoordinator

/** Application composition root; test instances do not depend on the shared module. */
class AppModule(
    httpClient: HttpClient = sharedHttpClient,
    val sessionManager: SessionManager = SessionManager(),
    val dPoPManager: DPoPManager = if (pl.quicktask.app.auth.session.browserSessions) pl.quicktask.app.auth.session.BrowserDPoPManager() else DefaultDPoPManager(),
    opaqueManager: OpaqueManager = createOpaqueManager(),
    keyCache: KeyCache = KeyCache(),
    baseUrl: String = ApiConfig.BASE_URL,
    val keyStore: KeyStore = KeyStore(),
    val logger: AppLogger = ConsoleAppLogger,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    clock: () -> Long = { getTimeMillis() },
) {
    private val authApi = AuthApiClient(httpClient, dPoPManager, baseUrl)
    val userKeys = UserKeyService(sessionManager, keyStore, keyCache, authApi::getUserKey, dispatcher)
    val sessionRefresh = SessionRefreshService(
        sessionManager, authApi::refresh,
        clearSession = {
            sessionManager.clearSession()
            dPoPManager.clearKeyPair()
            userKeys.clear()
        },
        clock = clock, dispatcher = dispatcher,
    )
    val auth = AuthRepository(
        authApi, dPoPManager, opaqueManager, sessionManager, userKeys, sessionRefresh, baseUrl, logger, dispatcher,
    )
    val items = ItemModule(httpClient, sessionManager, dPoPManager, baseUrl, sessionRefresh, CachedUserKeysProvider(userKeys, keyStore))
    val settings: SettingsOperations get() = items.settings
    val sync by lazy {
        SyncCoordinator(
            sessionManager = sessionManager,
            sessionRefresher = auth,
            logout = auth::logout,
            keyStore = keyStore,
            dPoPManager = dPoPManager,
            itemSyncService = items.sync,
            baseUrl = baseUrl,
        )
    }
}

val sharedAppModule: AppModule by lazy { AppModule() }
