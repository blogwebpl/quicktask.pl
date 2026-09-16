package pl.quicktask.app.main

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
import pl.quicktask.app.inbox.sharedInboxRepository

@Composable
fun MainLayout(
    onLogout: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.INBOX) }

    val inboxItems by sharedInboxRepository.itemsFlow.collectAsStateWithLifecycle(emptyList())
    val trashItems by sharedInboxRepository.trashItemsFlow.collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(Unit) {
        sharedInboxRepository.getItems()
        sharedInboxRepository.getTrashItems()
    }

    val itemCounts = remember(inboxItems, trashItems) {
        mapOf(
            Screen.INBOX to inboxItems.size,
            Screen.TRASH to trashItems.size,
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                currentRoute = currentScreen.id,
                itemCounts = itemCounts,
                onNavigate = handleNavigate,
                onLogout = handleLogout,
            )
        },
        content = {
            MainContent(
                currentScreen = currentScreen,
                onOpenDrawer = handleOpenDrawer,
            )
        },
    )
}
