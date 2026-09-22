package pl.quicktask.app.items.domain

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import pl.quicktask.app.items.model.ItemSyncStateResponseDto
import pl.quicktask.app.items.support.*
import pl.quicktask.app.projects.model.*
import kotlin.test.*

class ProjectRefreshTest {
    @Test
    fun assigningMovingAndDetachingTaskRefreshesCachedProjectsAfterSave(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val task = fixture.projectTask()
        val first = fixture.project("project-1")
        val second = fixture.project("project-2")
        var overview = ProjectsResponseDto(listOf(first, second), listOf(task))
        val client = mockClient(MockEngine { request ->
            when {
                request.method == HttpMethod.Patch -> respond("{}", headers = jsonHeaders)
                request.url.encodedPath == "/inbox/projects" -> respond(testJson.encodeToString(overview), headers = jsonHeaders)
                request.url.encodedPath == "/inbox/trash" -> respond("[]", headers = jsonHeaders)
                else -> error("Unexpected request: ${request.url}")
            }
        })
        try {
            val module = fixture.module(client)
            module.projects.getProjects().getOrThrow()
            val item = fixture.mapper.syncNextAction(fixture.sync)
            for (projectId in listOf("project-1", "project-2", null)) {
                overview = ProjectsResponseDto(
                    listOf(first, second).map { it.copy(tasks = if (it.projectId == projectId) listOf(task) else emptyList()) },
                    if (projectId == null) listOf(task) else emptyList(),
                )
                module.nextActions.updateNextAction(
                    item = item, title = item.title, note = item.note, projectId = projectId,
                    dueAt = null, contextIds = emptyList(), newContextNames = emptyList(),
                    newContexts = emptyList(), tagIds = emptyList(), newTagNames = emptyList(),
                ).getOrThrow()
                val visible = module.store.projectsOverviewFlow.value
                assertEquals(overview.projects.map { it.tasks.map { task -> task.itemId } }, visible.projects.map { it.tasks.map { task -> task.itemId } })
                assertEquals(overview.unassignedTasks.map { it.itemId }, visible.unassignedTasks.map { it.itemId })
                assertEquals(visible, module.projects.getProjects().getOrThrow())
            }
        } finally { client.close() }
    }

    @Test
    fun remoteTaskChangeAndRemovalRefreshProjectMembership(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val task = fixture.projectTask()
        var snapshot = ItemSyncStateResponseDto("next-actions", item = fixture.sync.copy(scheduledAt = "2026-09-22T12:00:00Z"), projectId = "project-1")
        var overview = ProjectsResponseDto(listOf(fixture.project("project-1").copy(tasks = listOf(task))))
        val client = mockClient(MockEngine { request ->
            respond(
                when (request.url.encodedPath) {
                    "/inbox/item-1/sync-state" -> testJson.encodeToString(snapshot)
                    "/inbox/projects" -> testJson.encodeToString(overview)
                    else -> error("Unexpected request: ${request.url}")
                }, headers = jsonHeaders,
            )
        })
        try {
            val module = fixture.module(client)
            for (location in listOf("next-actions", "scheduled", "projects-task", "removed")) {
                snapshot = snapshot.copy(location = location, itemId = task.itemId)
                if (location == "removed") overview = overview.copy(projects = overview.projects.map { it.copy(tasks = emptyList()) })
                module.sync.handleSyncStateForItem(task.itemId).getOrThrow()
                assertEquals(if (location == "removed") emptyList() else listOf(task.itemId), module.store.projectsFlow.value.single().tasks.map { it.itemId })
            }
        } finally { client.close() }
    }

    @Test
    fun projectFetchRetriesWhenAssignmentChangesWhileResponseIsInFlight(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val client = mockClient(MockEngine {
            requests++
            val tasks = if (requests == 1) emptyList() else listOf(fixture.projectTask())
            if (requests == 1) {
                entered.complete(Unit)
                release.await()
            }
            respond(testJson.encodeToString(ProjectsResponseDto(listOf(fixture.project("project-1").copy(tasks = tasks)))), headers = jsonHeaders)
        })
        try {
            val module = fixture.module(client)
            val fetch = async { module.projects.getProjects(true).getOrThrow() }
            entered.await()
            module.store.invalidateCache()
            release.complete(Unit)
            assertEquals(listOf("item-1"), fetch.await().projects.single().tasks.map { it.itemId })
            assertEquals(2, requests)
            assertTrue(module.store.isProjectsCacheValid)
        } finally { client.close() }
    }

    private fun ItemFixture.projectTask() = ProjectTaskDto(
        itemId = inbox.itemId, taskId = inbox.itemId, encryptedTitle = inbox.encryptedTitle,
        encryptedNote = inbox.encryptedNote, encryptedItemKey = inbox.encryptedItemKey,
        createdAt = inbox.createdAt, updatedAt = inbox.updatedAt, gtdState = "NEXT",
    )

    private fun ItemFixture.project(id: String) = ProjectWithTasksDto(
        itemId = id, projectId = id, encryptedTitle = inbox.encryptedTitle,
        encryptedNote = inbox.encryptedNote, encryptedItemKey = inbox.encryptedItemKey,
        createdAt = inbox.createdAt, updatedAt = inbox.updatedAt,
    )
}
