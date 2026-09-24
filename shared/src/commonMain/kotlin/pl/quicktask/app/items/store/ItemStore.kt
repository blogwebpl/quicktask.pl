package pl.quicktask.app.items.store

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.CompletedInTwoMinutesItem
import pl.quicktask.app.nextactions.model.NextAction
import pl.quicktask.app.scheduled.model.ScheduledTask
import pl.quicktask.app.trash.model.TrashItem
import pl.quicktask.app.projects.model.DecryptedProjectTask
import pl.quicktask.app.projects.model.ProjectsResult

/** Server snapshots and local overlays are updated together with a single atomic state. */
enum class TrashOperationKind { Restore, PermanentDelete }

class ItemStore {
    class TrashOperation internal constructor(val id: Long, val itemId: String?, val kind: TrashOperationKind?)

    class PendingOperation internal constructor(val id: Long, val itemId: String, val realId: String? = null)
    private data class Change(val operation: PendingOperation, val item: InboxItem?)
    private data class State(
        val completed: List<CompletedInTwoMinutesItem> = emptyList(),
        val completedLoaded: Boolean = false,
        val inbox: List<InboxItem> = emptyList(),
        val nextActions: List<NextAction> = emptyList(),
        val scheduledTasks: List<ScheduledTask> = emptyList(),
        val projects: List<pl.quicktask.app.projects.model.ProjectWithTasks> = emptyList(),
        val unassignedTasks: List<DecryptedProjectTask> = emptyList(),
        val trash: List<TrashItem> = emptyList(),
        val pending: Map<String, Change> = emptyMap(),
        val trashPending: Map<String, TrashOperation> = emptyMap(),
        val emptyTrash: TrashOperation? = null,
        val nextId: Long = 0,
        val generation: Long = 0,
        val inboxValid: Boolean = false,
        val inboxSnapshotVersion: Long = 0,
        val nextActionsValid: Boolean = false,
        val scheduledValid: Boolean = false,
        val projectsValid: Boolean = false,
        val trashValid: Boolean = false,
    ) {
        fun visibleTrash(): List<TrashItem> = if (emptyTrash != null) emptyList()
            else trash.filterNot { it.itemId in trashPending }

        fun visibleInbox(): List<InboxItem> {
            val added = pending.values.mapNotNull { it.item }.filter { pendingItem ->
                val change = pending[pendingItem.itemId]
                val realId = change?.operation?.realId
                inbox.none { inboxItem ->
                    inboxItem.itemId == pendingItem.itemId ||
                    (realId != null && inboxItem.itemId == realId) ||
                    (pendingItem.itemId.startsWith("temp_") &&
                     inboxItem.title == pendingItem.title &&
                     inboxItem.note == pendingItem.note)
                }
            }
            return added + inbox.mapNotNull { item ->
                val change = pending[item.itemId]
                if (change == null) item else change.item
            }
        }
    }
    private val state = MutableStateFlow(State())
    val completedItemsFlow: StateFlow<List<CompletedInTwoMinutesItem>> = ProjectedStateFlow(state) { it.completed }
    val hasLoadedCompletedItems get() = state.value.completedLoaded

    internal fun cacheCompleted(items: List<CompletedInTwoMinutesItem>, expectedGeneration: Long = generation): Boolean {
        while (true) {
            val before = state.value
            if (before.generation != expectedGeneration) return false
            if (state.compareAndSet(before, before.copy(completed = items, completedLoaded = true))) return true
        }
    }

    internal fun applyCompleted(item: CompletedInTwoMinutesItem, targetItemId: String) = state.update {
        it.copy(
            generation = it.generation + 1,
            completed = (it.completed.filterNot { old -> old.itemId == targetItemId } + item)
                .sortedByDescending { entry -> entry.processedAt },
            inbox = it.inbox.filterNot { old -> old.itemId == targetItemId },
            nextActions = it.nextActions.filterNot { old -> old.itemId == targetItemId },
            scheduledTasks = it.scheduledTasks.filterNot { old -> old.itemId == targetItemId },
            trash = it.trash.filterNot { old -> old.itemId == targetItemId },
        )
    }
    val itemsFlow: StateFlow<List<InboxItem>> = ProjectedStateFlow(state) { it.visibleInbox() }
    val inboxSnapshotVersionFlow: StateFlow<Long> = ProjectedStateFlow(state) { it.inboxSnapshotVersion }
    val nextActionsFlow: StateFlow<List<NextAction>> = ProjectedStateFlow(state) { it.nextActions }
    val scheduledTasksFlow: StateFlow<List<ScheduledTask>> = ProjectedStateFlow(state) { it.scheduledTasks }
    val projectsFlow: StateFlow<List<pl.quicktask.app.projects.model.ProjectWithTasks>> = ProjectedStateFlow(state) { it.projects }
    val projectsOverviewFlow: StateFlow<ProjectsResult> = ProjectedStateFlow(state) {
        ProjectsResult(it.projects, it.unassignedTasks)
    }
    val trashItemsFlow: StateFlow<List<TrashItem>> = ProjectedStateFlow(state) { it.visibleTrash() }
    val trashOperationsFlow: StateFlow<Map<String, TrashOperationKind>> = ProjectedStateFlow(state) {
        it.trashPending.mapValues { entry -> requireNotNull(entry.value.kind) }
    }
    val emptyingTrashFlow: StateFlow<Boolean> = ProjectedStateFlow(state) { it.emptyTrash != null }

    fun beginTrashOperation(itemId: String?, kind: TrashOperationKind?): TrashOperation? {
        while (true) {
            val before = state.value
            if (before.emptyTrash != null) return null
            if (itemId == null && before.trashPending.isNotEmpty()) return null
            if (itemId != null && (itemId in before.trashPending || itemId in before.pending)) return null
            require((itemId == null) == (kind == null))
            val operation = TrashOperation(before.nextId, itemId, kind)
            val after = before.copy(
                nextId = before.nextId + 1,
                trashPending = if (itemId != null) before.trashPending + (itemId to operation) else before.trashPending,
                emptyTrash = if (itemId == null) operation else before.emptyTrash,
                generation = before.generation + 1,
                trashValid = false,
            )
            if (state.compareAndSet(before, after)) return operation
        }
    }

    fun finishTrashOperation(operation: TrashOperation) = state.update {
        if (operation.itemId == null) {
            if (it.emptyTrash == operation) it.copy(emptyTrash = null) else it
        } else if (it.trashPending[operation.itemId] == operation) {
            it.copy(trashPending = it.trashPending - operation.itemId)
        } else it
    }

    val isCacheValid get() = state.value.inboxValid
    val isNextActionsCacheValid get() = state.value.nextActionsValid
    val isScheduledCacheValid get() = state.value.scheduledValid
    val isProjectsCacheValid get() = state.value.projectsValid
    val isTrashCacheValid get() = state.value.trashValid
    internal val generation get() = state.value.generation

    fun invalidateCache() = state.update {
        it.copy(generation = it.generation + 1, inboxValid = false, nextActionsValid = false, scheduledValid = false, projectsValid = false, trashValid = false)
    }

    internal fun cacheInbox(
        items: List<InboxItem>,
        expectedGeneration: Long = generation,
        completedOperation: PendingOperation? = null,
    ): Boolean {
        while (true) {
            val before = state.value
            if (before.generation != expectedGeneration) return false
            val pending = if (completedOperation != null &&
                before.pending[completedOperation.itemId]?.operation == completedOperation
            ) before.pending - completedOperation.itemId else before.pending
            if (state.compareAndSet(before, before.copy(
                    inbox = items,
                    inboxValid = true,
                    inboxSnapshotVersion = before.inboxSnapshotVersion + 1,
                    pending = pending,
                ))) return true
        }
    }
    internal fun cacheNextActions(items: List<NextAction>, expectedGeneration: Long = generation): Boolean {
        while (true) {
            val before = state.value
            if (before.generation != expectedGeneration) return false
            if (state.compareAndSet(before, before.copy(nextActions = items, nextActionsValid = true))) return true
        }
    }
    internal fun cacheScheduledTasks(items: List<ScheduledTask>, expectedGeneration: Long = generation): Boolean {
        while (true) {
            val before = state.value
            if (before.generation != expectedGeneration) return false
            if (state.compareAndSet(before, before.copy(scheduledTasks = items, scheduledValid = true))) return true
        }
    }
    internal fun cacheProjects(items: List<pl.quicktask.app.projects.model.ProjectWithTasks>, expectedGeneration: Long = generation, unassignedTasks: List<DecryptedProjectTask> = emptyList()): Boolean {
        while (true) {
            val before = state.value
            if (before.generation != expectedGeneration) return false
            if (state.compareAndSet(before, before.copy(projects = items, unassignedTasks = unassignedTasks, projectsValid = true))) return true
        }
    }
    internal fun cacheTrash(items: List<TrashItem>, expectedGeneration: Long = generation): Boolean {
        while (true) {
            val before = state.value
            if (before.generation != expectedGeneration) return false
            if (state.compareAndSet(before, before.copy(trash = items, trashValid = true))) return true
        }
    }

    /** Null is returned when another local operation already owns this item. */
    fun beginOperation(itemId: String, item: InboxItem?): PendingOperation? {
        while (true) {
            val before = state.value
            if (itemId in before.pending || itemId in before.trashPending) return null
            val operation = PendingOperation(before.nextId, itemId)
            val change = Change(operation, item?.copy(isPending = true))
            val after = before.copy(nextId = before.nextId + 1, pending = before.pending + (itemId to change))
            if (state.compareAndSet(before, after)) return operation
        }
    }
    fun finishOperation(operation: PendingOperation) = state.update {
        if (it.pending[operation.itemId]?.operation != operation) it
        else it.copy(pending = it.pending - operation.itemId)
    }
    fun bindRealId(tempId: String, realId: String) = state.update { s ->
        val change = s.pending[tempId] ?: return@update s
        val updatedOp = PendingOperation(change.operation.id, change.operation.itemId, realId)
        val updatedChange = change.copy(operation = updatedOp)
        s.copy(pending = s.pending + (tempId to updatedChange))
    }
    fun hasPendingOperation(itemId: String): Boolean = itemId in state.value.pending

    // Legacy list mutations are server-state mutations; local operations use overlays above.
    fun addOptimisticItem(item: InboxItem) = state.update {
        it.copy(inbox = listOf(item) + it.inbox.filterNot { existing -> existing.itemId == item.itemId })
    }
    fun updateOptimisticItem(item: InboxItem) = state.update {
        it.copy(inbox = it.inbox.map { existing -> if (existing.itemId == item.itemId) item else existing })
    }
    fun removeOptimisticItem(itemId: String) = state.update {
        it.copy(inbox = it.inbox.filterNot { item -> item.itemId == itemId })
    }
    fun setOptimisticItems(items: List<InboxItem>) = state.update { it.copy(inbox = items) }
    internal fun setOptimisticTrash(items: List<TrashItem>) = state.update { it.copy(trash = items) }
    internal fun removeTrashItem(itemId: String) = state.update {
        it.copy(trash = it.trash.filterNot { item -> item.itemId == itemId })
    }
    internal fun applyInbox(item: InboxItem, targetItemId: String) = state.update {
        val index = it.inbox.indexOfFirst { old -> old.itemId == targetItemId }
        val updatedInbox = if (index != -1) {
            it.inbox.toMutableList().apply { set(index, item) }
        } else {
            listOf(item) + it.inbox
        }
        it.copy(
            generation = it.generation + 1,
            inbox = updatedInbox,
            completed = it.completed.filterNot { old -> old.itemId == targetItemId },
            nextActions = it.nextActions.filterNot { old -> old.itemId == targetItemId },
            scheduledTasks = it.scheduledTasks.filterNot { old -> old.itemId == targetItemId },
            trash = it.trash.filterNot { old -> old.itemId == targetItemId },
        )
    }
    internal fun applyNextAction(item: NextAction, targetItemId: String) = state.update {
        val index = it.nextActions.indexOfFirst { old -> old.itemId == targetItemId }
        val updatedNextActions = if (index != -1) {
            it.nextActions.toMutableList().apply { set(index, item) }
        } else {
            listOf(item) + it.nextActions
        }
        it.copy(
            generation = it.generation + 1,
            nextActions = updatedNextActions,
            completed = it.completed.filterNot { old -> old.itemId == targetItemId },
            inbox = it.inbox.filterNot { old -> old.itemId == targetItemId },
            scheduledTasks = it.scheduledTasks.filterNot { old -> old.itemId == targetItemId },
            trash = it.trash.filterNot { old -> old.itemId == targetItemId },
        )
    }
    internal fun applyScheduledTask(item: ScheduledTask, targetItemId: String) = state.update {
        val index = it.scheduledTasks.indexOfFirst { old -> old.itemId == targetItemId }
        val updatedScheduledTasks = if (index != -1) {
            it.scheduledTasks.toMutableList().apply { set(index, item) }
        } else {
            listOf(item) + it.scheduledTasks
        }
        it.copy(
            generation = it.generation + 1,
            scheduledTasks = updatedScheduledTasks,
            completed = it.completed.filterNot { old -> old.itemId == targetItemId },
            inbox = it.inbox.filterNot { old -> old.itemId == targetItemId },
            nextActions = it.nextActions.filterNot { old -> old.itemId == targetItemId },
            trash = it.trash.filterNot { old -> old.itemId == targetItemId },
        )
    }
    internal fun applyTrash(item: TrashItem, targetItemId: String) = state.update {
        val index = it.trash.indexOfFirst { old -> old.itemId == targetItemId }
        val updatedTrash = if (index != -1) {
            it.trash.toMutableList().apply { set(index, item) }
        } else {
            listOf(item) + it.trash
        }
        it.copy(
            generation = it.generation + 1,
            trash = updatedTrash,
            completed = it.completed.filterNot { old -> old.itemId == targetItemId },
            inbox = it.inbox.filterNot { old -> old.itemId == targetItemId },
            nextActions = it.nextActions.filterNot { old -> old.itemId == targetItemId },
            scheduledTasks = it.scheduledTasks.filterNot { old -> old.itemId == targetItemId },
        )
    }
    internal fun applyRemoval(itemId: String) = state.update {
        it.copy(
            generation = it.generation + 1,
            inbox = it.inbox.filterNot { item -> item.itemId == itemId },
            completed = it.completed.filterNot { item -> item.itemId == itemId },
            nextActions = it.nextActions.filterNot { item -> item.itemId == itemId },
            scheduledTasks = it.scheduledTasks.filterNot { item -> item.itemId == itemId },
            trash = it.trash.filterNot { item -> item.itemId == itemId },
        )
    }
}

/** Projection without a background scope: value and collection always use the same source. */
@OptIn(InternalCoroutinesApi::class, ExperimentalForInheritanceCoroutinesApi::class)
private class ProjectedStateFlow<S, T>(
    private val source: StateFlow<S>, private val project: (S) -> T,
) : StateFlow<T> {
    override val value get() = project(source.value)
    override val replayCache get() = listOf(value)
    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        source.map(project).distinctUntilChanged().collect(collector)
        error("StateFlow collection cannot complete")
    }
}
