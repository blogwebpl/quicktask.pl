package pl.quicktask.app.main.presentation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.inbox.presentation.InboxScreen
import pl.quicktask.app.main.model.Screen
import pl.quicktask.app.nextactions.presentation.NextActionsScreen
import pl.quicktask.app.now.presentation.NowScreen
import pl.quicktask.app.references.presentation.ReferenceScreen
import pl.quicktask.app.settings.presentation.SettingsScreen
import pl.quicktask.app.trash.presentation.TrashScreen

@Composable
fun MainContent(
    module: AppModule,
    currentScreen: Screen = Screen.INBOX,
    onOpenDrawer: () -> Unit = {},
    onFabClick: () -> Unit = {},
) {
    when (currentScreen) {
        Screen.COMPLETED -> {
            pl.quicktask.app.completed.presentation.CompletedScreen(module, onOpenDrawer)
        }
        Screen.INBOX -> {
            InboxScreen(
                module = module,
                onOpenDrawer = onOpenDrawer,
            )
        }
        Screen.NOW -> {
            NowScreen(
                module = module,
                onOpenDrawer = onOpenDrawer,
            )
        }
        Screen.NEXT_ACTIONS -> {
            NextActionsScreen(
                module = module,
                onOpenDrawer = onOpenDrawer,
            )
        }
        Screen.SCHEDULED -> {
            pl.quicktask.app.scheduled.presentation.ScheduledScreen(
                module = module,
                onOpenDrawer = onOpenDrawer,
            )
        }
        Screen.WAITING -> {
            pl.quicktask.app.waiting.presentation.WaitingScreen(
                module = module,
                onOpenDrawer = onOpenDrawer,
            )
        }
        Screen.PROJECTS -> {
            pl.quicktask.app.projects.presentation.ProjectsScreen(
                module = module,
                onOpenDrawer = onOpenDrawer,
            )
        }
        Screen.TRASH -> {
            TrashScreen(
                module = module,
                onOpenDrawer = onOpenDrawer,
            )
        }
        Screen.REFERENCE -> {
            ReferenceScreen(module = module, onOpenDrawer = onOpenDrawer)
        }
        Screen.SETTINGS -> {
            SettingsScreen(
                module = module,
                onOpenDrawer = onOpenDrawer,
            )
        }
        else -> {
            val screenTitle = stringResource(currentScreen.titleRes)
            val showFab = (currentScreen != Screen.COMPLETED) && (currentScreen != Screen.TRASH)

            GenericTaskScreen(
                title = screenTitle,
                onOpenDrawer = onOpenDrawer,
                showFab = showFab,
                onFabClick = onFabClick,
            )
        }
    }
}
