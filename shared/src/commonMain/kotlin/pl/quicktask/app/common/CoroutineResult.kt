package pl.quicktask.app.common

import kotlinx.coroutines.CancellationException

/** Cancellation belongs to the coroutine lifecycle, not the operation's error result. */
internal suspend inline fun <T> coroutineResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    Result.failure(error)
}
