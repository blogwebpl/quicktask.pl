package pl.quicktask.todo.main

import androidx.compose.runtime.Composable

@Composable
fun MainContent(
    currentScreen: Screen = Screen.INBOX,
    onOpenDrawer: () -> Unit = {},
) {
    GenericTaskScreen(
        title = currentScreen.title,
        onOpenDrawer = onOpenDrawer,
    )
}
