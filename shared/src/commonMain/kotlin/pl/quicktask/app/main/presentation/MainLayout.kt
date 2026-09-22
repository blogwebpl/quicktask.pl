package pl.quicktask.app.main.presentation

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.main.model.Screen
import pl.quicktask.app.main.platform.BackHandler

@Composable
fun MainLayout(
    module: AppModule,
    onLogout: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.INBOX) }

    val inboxItems by module.items.store.itemsFlow.collectAsStateWithLifecycle(emptyList())
    val trashItems by module.items.store.trashItemsFlow.collectAsStateWithLifecycle(emptyList())
    val projects by module.items.store.projectsFlow.collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(module) {
        module.items.inbox.getItems()
        module.items.trash.getTrashItems()
        module.items.projects.getProjects()
    }

    val itemCounts = remember(inboxItems, trashItems, projects) {
        mapOf(
            Screen.INBOX to inboxItems.size,
            Screen.TRASH to trashItems.size,
            Screen.PROJECTS to projects.size,
        )
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    BackHandler(enabled = (!drawerState.isOpen && currentScreen != Screen.INBOX)) {
        currentScreen = Screen.INBOX
    }

    val handleNavigate: (String) -> Unit = remember {
        { routeId ->
            currentScreen = Screen.fromId(routeId)
            scope.launch {
                drawerState.close()
            }
        }
    }

    val handleLogout: () -> Unit = remember(onLogout) {
        {
            scope.launch {
                drawerState.close()
                onLogout()
            }
        }
    }

    val handleOpenDrawer: () -> Unit = remember {
        {
            scope.launch {
                drawerState.open()
            }
        }
    }

    val handleSettingsClick: () -> Unit = remember {
        {
            currentScreen = Screen.SETTINGS
            scope.launch {
                drawerState.close()
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                currentRoute = currentScreen.id,
                itemCounts = itemCounts,
                onNavigate = handleNavigate,
                onLogout = handleLogout,
                onSettingsClick = handleSettingsClick,
            )
        },
        content = {
            MainContent(
                module = module,
                currentScreen = currentScreen,
                onOpenDrawer = handleOpenDrawer,
            )
        },
    )
}
