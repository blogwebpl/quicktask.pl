package pl.quicktask.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pl.quicktask.app.nextactions.model.DecryptedProject
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.task_save_button
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectSelectionBottomSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun saveCreatesProjectFromSearchAndConfirmsItBeforeClosing(): Unit {
        val project = project("new-project", "Remont kuchni")
        val creation = CompletableDeferred<DecryptedProject?>()
        val requestedTitles = mutableListOf<String>()
        val events = mutableListOf<String>()
        val saveLabel = showPicker(
            onCreateProject = { title ->
                requestedTitles.add(title)
                creation.await()
            },
            events = events,
        )

        compose.onNode(hasSetTextAction()).performTextInput("  Remont kuchni  ")
        compose.onNodeWithText(saveLabel).performClick()

        compose.runOnIdle {
            assertEquals(listOf("Remont kuchni"), requestedTitles)
            assertTrue(events.isEmpty(), "The picker must wait for the project to be saved")
        }
        compose.onNode(hasSetTextAction()).assertExists()

        compose.runOnIdle { creation.complete(project) }

        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("confirm:new-project", "dismiss"), events)
            assertEquals(listOf("Remont kuchni"), requestedTitles)
        }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun failedSaveKeepsSearchOpenAndAllowsRetry(): Unit {
        val project = project("retried-project", "Remont kuchni")
        val requestedTitles = mutableListOf<String>()
        val events = mutableListOf<String>()
        val saveLabel = showPicker(
            onCreateProject = { title ->
                requestedTitles.add(title)
                if (requestedTitles.size == 1) null else project
            },
            events = events,
        )

        compose.onNode(hasSetTextAction()).performTextInput("Remont kuchni")
        compose.onNodeWithText(saveLabel).performClick()

        compose.onNodeWithText("Nie udało się utworzyć projektu. Spróbuj ponownie.").assertExists()
        compose.onNodeWithText("Remont kuchni").assertExists()
        compose.runOnIdle {
            assertEquals(listOf("Remont kuchni"), requestedTitles)
            assertTrue(events.isEmpty(), "A failed save must not confirm or close the picker")
        }

        compose.onNodeWithText(saveLabel).performClick()

        compose.runOnIdle {
            assertEquals(listOf("Remont kuchni", "Remont kuchni"), requestedTitles)
            assertEquals(listOf("confirm:retried-project", "dismiss"), events)
        }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    private fun showPicker(
        onCreateProject: suspend (String) -> DecryptedProject?,
        events: MutableList<String>,
    ): String {
        var isOpen by mutableStateOf(true)
        lateinit var saveLabel: String
        compose.setContent {
            MaterialTheme {
                saveLabel = stringResource(Res.string.task_save_button)
                if (isOpen) {
                    ProjectSelectionBottomSheet(
                        availableProjects = emptyList(),
                        selectedProjectId = null,
                        onDismiss = {
                            events.add("dismiss")
                            isOpen = false
                        },
                        onConfirm = { events.add("confirm:$it") },
                        onCreateProject = onCreateProject,
                    )
                }
            }
        }
        compose.waitForIdle()
        return saveLabel
    }

    private fun project(id: String, title: String): DecryptedProject = runBlocking {
        DecryptedProject(
            projectId = id,
            title = title,
            projectKey = CryptographyProvider.Default.get(AES.GCM).keyGenerator(AES.Key.Size.B256).generateKey(),
        )
    }
}
