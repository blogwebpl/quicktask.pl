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
    val nextActions by module.items.store.nextActionsFlow.collectAsStateWithLifecycle(emptyList())
    val scheduledTasks by module.items.store.scheduledTasksFlow.collectAsStateWithLifecycle(emptyList())
    val completedItems by module.items.store.completedItemsFlow.collectAsStateWithLifecycle(emptyList())
    val referenceItems by module.items.store.referencesFlow.collectAsStateWithLifecycle(emptyList())
    val projectsOverview by module.items.store.projectsOverviewFlow.collectAsStateWithLifecycle(
        pl.quicktask.app.projects.model.ProjectsResult(emptyList(), emptyList())
    )

    var nowCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(module) {
        module.items.inbox.getItems()
        module.items.trash.getTrashItems()
        module.items.projects.getProjects()
        module.items.nextActions.getNextActions()
        module.items.scheduled.getScheduledTasks()
        module.items.completed.getCompletedInTwoMinutes()
        module.items.references.getReferenceItems()
        module.items.now.getNowData().onSuccess { data ->
            nowCount = data.availableNextActions.size + data.scheduledToday.size + data.overdue.size + data.waitingForReview.size
        }
    }

    val itemCounts = remember(
        inboxItems, trashItems, projects, nextActions, scheduledTasks, completedItems, referenceItems, projectsOverview, nowCount
    ) {
        val assignedTasks = projectsOverview.projects.flatMap { it.tasks }
        val unassignedTasks = projectsOverview.unassignedTasks
        val allProjectTasks = assignedTasks + unassignedTasks

        val waitingCount = allProjectTasks.count { it.gtdState.equals("WAITING", ignoreCase = true) }
        val somedayCount = allProjectTasks.count { it.gtdState.equals("SOMEDAY", ignoreCase = true) }
        val reviewCount = allProjectTasks.count { it.gtdState.equals("REVIEW", ignoreCase = true) }

        mapOf(
            Screen.INBOX to inboxItems.size,
            Screen.TRASH to trashItems.size,
            Screen.PROJECTS to projects.size,
            Screen.NEXT_ACTIONS to nextActions.size,
            Screen.SCHEDULED to scheduledTasks.size,
            Screen.COMPLETED to completedItems.size,
            Screen.WAITING to waitingCount,
            Screen.SOMEDAY to somedayCount,
            Screen.REFERENCE to referenceItems.size,
            Screen.REVIEW to reviewCount,
        ).toMutableMap().apply {
            if (nowCount != null) {
                put(Screen.NOW, nowCount!!)
            }
        }
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
