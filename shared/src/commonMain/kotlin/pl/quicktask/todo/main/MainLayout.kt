package pl.quicktask.todo.main

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

@Composable
fun MainLayout(
    onLogout: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.INBOX) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                currentRoute = currentScreen.id,
                onNavigate = { routeId ->
                    currentScreen = Screen.fromId(routeId)
                    scope.launch {
                        drawerState.close()
                    }
                },
                onLogout = {
                    scope.launch {
                        drawerState.close()
                        onLogout()
                    }
                },
            )
        },
        content = {
            MainContent(
                currentScreen = currentScreen,
                onOpenDrawer = {
                    scope.launch {
                        drawerState.open()
                    }
                },
            )
        },
    )
}
