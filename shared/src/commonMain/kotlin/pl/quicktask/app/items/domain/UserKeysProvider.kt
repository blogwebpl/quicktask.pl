package pl.quicktask.app.items.domain

import pl.quicktask.app.items.model.UserKeysLockedException

import kotlinx.coroutines.CancellationException
import pl.quicktask.app.auth.data.UserKeyService
import pl.quicktask.app.auth.crypto.KeyStore
import pl.quicktask.app.auth.crypto.UserKeyPair

fun interface UserKeysProvider {
    suspend fun getUserKeys(): UserKeyPair
}

class CachedUserKeysProvider(private val userKeys: UserKeyService, private val keyStore: KeyStore) : UserKeysProvider {
    override suspend fun getUserKeys(): UserKeyPair {
        keyStore.getSnapshot()?.let { return it }
        try {
            userKeys.restoreCached()
        } catch (e: CancellationException) {
            throw e
        }
        return keyStore.getSnapshot() ?: throw UserKeysLockedException()
    }
}
