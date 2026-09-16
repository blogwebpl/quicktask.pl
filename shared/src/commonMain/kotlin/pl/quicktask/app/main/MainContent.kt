package pl.quicktask.app.main

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.inbox.InboxScreen
import pl.quicktask.app.inbox.TrashScreen
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.app_name

@Composable
fun MainContent(
    currentScreen: Screen = Screen.INBOX,
    onOpenDrawer: () -> Unit = {},
    onFabClick: () -> Unit = {},
) {
    when (currentScreen) {
        Screen.INBOX -> {
            InboxScreen(
                onOpenDrawer = onOpenDrawer,
            )
        }
        Screen.TRASH -> {
            TrashScreen(
                onOpenDrawer = onOpenDrawer,
            )
        }
        else -> {
            val appName = stringResource(Res.string.app_name)
            val screenTitle = stringResource(currentScreen.titleRes)
            val showFab = (currentScreen != Screen.COMPLETED) && (currentScreen != Screen.TRASH)

            GenericTaskScreen(
                title = "$appName - $screenTitle",
                onOpenDrawer = onOpenDrawer,
                showFab = showFab,
                onFabClick = onFabClick,
            )
        }
    }
}
