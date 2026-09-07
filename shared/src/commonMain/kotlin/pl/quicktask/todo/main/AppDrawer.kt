package pl.quicktask.todo.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.app_name
import todo.shared.generated.resources.logout_button

private data class DrawerItemData(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badge: Int? = null,
)

private data class DrawerSectionData(
    val title: String,
    val items: List<DrawerItemData>,
)

private val drawerSections = listOf(
    DrawerSectionData(
        title = "CAPTURE",
        items = listOf(
            DrawerItemData("inbox", "Inbox", Icons.Outlined.Email),
        ),
    ),
    DrawerSectionData(
        title = "ACTIONS",
        items = listOf(
            DrawerItemData("next", "Next Actions", Icons.Outlined.PlayArrow),
            DrawerItemData("scheduled", "Scheduled", Icons.Outlined.DateRange),
            DrawerItemData("waiting", "Waiting", Icons.Outlined.HourglassEmpty),
        ),
    ),
    DrawerSectionData(
        title = "PROJECTS",
        items = listOf(
            DrawerItemData("projects", "All Projects", Icons.AutoMirrored.Outlined.List),
        ),
    ),
    DrawerSectionData(
        title = "LIBRARY",
        items = listOf(
            DrawerItemData("someday", "Someday/Maybe", Icons.Outlined.Star),
            DrawerItemData("reference", "Reference", Icons.Outlined.Book),
        ),
    ),
    DrawerSectionData(
        title = "REVIEW",
        items = listOf(
            DrawerItemData("review", "Weekly Review", Icons.Outlined.Coffee),
        ),
    ),
    DrawerSectionData(
        title = "HISTORY",
        items = listOf(
            DrawerItemData("completed", "Completed", Icons.Outlined.CheckCircle),
            DrawerItemData("trash", "Trash", Icons.Outlined.Delete),
        ),
    ),
)

@Composable
fun AppDrawer(
    currentRoute: String = "inbox",
    onNavigate: (String) -> Unit = {},
    onLogout: () -> Unit = {},
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
            )

            drawerSections.forEach { section ->
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                )

                section.items.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        badge = item.badge?.let { { Text(it.toString()) } },
                        selected = currentRoute == item.id,
                        onClick = { onNavigate(item.id) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            NavigationDrawerItem(
                label = { Text(stringResource(Res.string.logout_button)) },
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                selected = false,
                onClick = onLogout,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
