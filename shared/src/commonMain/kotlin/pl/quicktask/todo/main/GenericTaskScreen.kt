package pl.quicktask.todo.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.ic_menu
import todo.shared.generated.resources.menu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericTaskScreen(
    title: String,
    onOpenDrawer: () -> Unit = {},
    showFab: Boolean = true,
    onFabClick: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_menu),
                            contentDescription = stringResource(Res.string.menu),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick = onFabClick,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Dodaj",
                    )
                }
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
