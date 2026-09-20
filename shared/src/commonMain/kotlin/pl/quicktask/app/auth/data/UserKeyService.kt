package pl.quicktask.app.auth.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.quicktask.app.auth.crypto.KeyStore
import pl.quicktask.app.auth.crypto.UserKeyMaterialDto
import pl.quicktask.app.auth.crypto.restoreUserKeyPair
import pl.quicktask.app.auth.session.KeyCache
import pl.quicktask.app.auth.session.SessionManager

class UserKeyService(
    private val session: SessionManager,
    private val store: KeyStore,
    private val cache: KeyCache,
    private val fetch: suspend (String) -> UserKeyMaterialDto,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun recover(exportKey: String, accessToken: String) = withContext(dispatcher) {
        val email = session.userEmail ?: error("Missing session email")
        val pair = restoreUserKeyPair(exportKey, fetch(accessToken))
        cache.cacheUserKeys(email, pair)
        store.set(pair)
    }

    suspend fun restoreCached(): Boolean = withContext(dispatcher) {
        val email = session.userEmail ?: return@withContext false
        val pair = cache.loadCachedUserKeys(email) ?: return@withContext false
        store.set(pair)
        true
    }

    suspend fun clear() {
        store.set(null)
        cache.clearCachedUserKeys()
    }
}
