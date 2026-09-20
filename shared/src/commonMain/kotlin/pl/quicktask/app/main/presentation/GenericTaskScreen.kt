package pl.quicktask.app.main.presentation

import pl.quicktask.app.ui.components.AppTopBar
import pl.quicktask.app.ui.components.AppAddButton

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.action_add

@Composable
fun GenericTaskScreen(
    title: String,
    onOpenDrawer: () -> Unit = {},
    showFab: Boolean = true,
    onFabClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = title,
                onOpenDrawer = onOpenDrawer,
            )
        },
        floatingActionButton = {
            if (showFab) {
                AppAddButton(onClick = onFabClick, contentDescription = stringResource(Res.string.action_add))
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        )
    }
}
