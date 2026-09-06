package pl.quicktask.todo.main

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun MainLayout(
    onLogout: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                onLogout = {
                    scope.launch { drawerState.close() }
                    onLogout()
                },
            )
        },
        content = {
            MainContent(
                onOpenDrawer = {
                    scope.launch {
                        drawerState.open()
                    }
                },
                onLogout = onLogout,
            )
        },
    )
}
