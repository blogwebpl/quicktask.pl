package pl.quicktask.app.inbox.presentation

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.support.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelStateTest {
    @Test
    fun refreshPreservesEditorStateAndListIsDrivenByStore(): Unit = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fixture = encryptedFixture()
        val client = mockClient(MockEngine {
            entered.complete(Unit)
            release.await()
            respond(testJson.encodeToString(listOf(fixture.inbox)), headers = jsonHeaders)
        })
        val viewModels = ViewModelStore()
        try {
            val module = fixture.module(client)
            val viewModel = InboxViewModel(module.inbox, module.store, module.lifecycle, module.completed)
            viewModels.put("inbox", viewModel)
            entered.await()
            viewModel.openAddDialog()
            viewModel.addSelectedFile(InputFile("selected.txt", "text/plain", byteArrayOf(1)))
            release.complete(Unit)
            val loaded = withTimeout(10000) { viewModel.uiState.first { !it.isLoading && it.items.isNotEmpty() } }
            assertTrue(loaded.showAddDialog)
            assertEquals("selected.txt", loaded.selectedFiles.single().fileName)
            assertEquals(module.store.itemsFlow.value, loaded.items)
            val changed = module.store.itemsFlow.value.single().copy(title = "sync update")
            module.store.applyInbox(changed, changed.itemId)
            val synced = withTimeout(10000) { viewModel.uiState.first { it.items.singleOrNull()?.title == "sync update" } }
            assertTrue(synced.showAddDialog)
            assertEquals(loaded.selectedFiles, synced.selectedFiles)
        } finally {
            val jobs = viewModels.keys().mapNotNull { viewModels[it]?.viewModelScope?.coroutineContext?.get(Job) }
            viewModels.clear()
            client.close()
            jobs.forEach { it.join() }
            Dispatchers.resetMain()
        }
    }
}
