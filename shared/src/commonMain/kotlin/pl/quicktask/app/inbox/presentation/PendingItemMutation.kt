package pl.quicktask.app.inbox.presentation

import kotlinx.coroutines.CancellationException
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.UncertainItemWriteException
import pl.quicktask.app.items.store.ItemStore

/** Keep the overlay until the authoritative list has been reconciled. */
internal suspend fun runPendingItemMutation(
    store: ItemStore,
    operation: ItemStore.PendingOperation,
    mutate: suspend () -> Result<Unit>,
    refresh: suspend () -> Result<List<InboxItem>>,
    onMutationError: (Throwable) -> Unit,
    onRefreshError: (Throwable) -> Unit,
) {
    try {
        val result = mutate()
        val error = result.exceptionOrNull()
        if (error is CancellationException) throw error
        if (error == null || error is UncertainItemWriteException) {
            refresh().onFailure {
                if (it is CancellationException) throw it
                store.invalidateCache()
                onRefreshError(it)
            }
        }
        if (error != null) onMutationError(error)
    } catch (error: CancellationException) {
        store.invalidateCache()
        throw error
    } catch (error: Exception) {
        store.invalidateCache()
        onMutationError(error)
    } finally {
        store.finishOperation(operation)
    }
}
