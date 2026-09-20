package pl.quicktask.app.items.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.quicktask.app.common.coroutineResult

class MissingSessionException : Exception("Missing active session")
class AttachmentLimitException(val limit: Int = MAX_ATTACHMENTS) : Exception("Attachment limit exceeded: $limit")
class ItemDecryptionException(cause: Throwable) : Exception("Item decryption failed", cause)
class FileIntegrityException : Exception("Ciphertext checksum mismatch")
class UserKeysLockedException : Exception("User keys are locked")
class UncertainItemWriteException(cause: Throwable) : Exception("The server write outcome is unknown", cause)

const val MAX_ATTACHMENTS = 10

internal suspend inline fun <T> itemResult(crossinline block: suspend () -> T): Result<T> =
    withContext(Dispatchers.Default) {
        coroutineResult { block() }
    }

internal suspend inline fun <T> decryptItem(crossinline block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: ItemDecryptionException) {
    throw e
} catch (e: Exception) {
    throw ItemDecryptionException(e)
}
