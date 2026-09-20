package pl.quicktask.app.main.presentation

import pl.quicktask.app.main.model.Screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.app_name
import todo.shared.generated.resources.ic_book
import todo.shared.generated.resources.ic_check_circle
import todo.shared.generated.resources.ic_coffee
import todo.shared.generated.resources.ic_date_range
import todo.shared.generated.resources.ic_delete
import todo.shared.generated.resources.ic_email
import todo.shared.generated.resources.ic_exit_to_app
import todo.shared.generated.resources.ic_hourglass_empty
import todo.shared.generated.resources.ic_list
import todo.shared.generated.resources.ic_play_arrow
import todo.shared.generated.resources.ic_settings
import todo.shared.generated.resources.ic_star
import todo.shared.generated.resources.ic_sun
import todo.shared.generated.resources.logout_button
import todo.shared.generated.resources.section_actions
import todo.shared.generated.resources.section_capture
import todo.shared.generated.resources.section_history
import todo.shared.generated.resources.section_library
import todo.shared.generated.resources.section_projects
import todo.shared.generated.resources.section_review
import todo.shared.generated.resources.settings

private data class DrawerItemData(
    val screen: Screen,
    val iconRes: DrawableResource,
    val badge: Int? = null,
)

private data class DrawerSectionData(
    val titleRes: StringResource,
    val items: List<DrawerItemData>,
)

private val drawerSections = listOf(
    DrawerSectionData(
        titleRes = Res.string.section_capture,
        items = listOf(
            DrawerItemData(Screen.INBOX, Res.drawable.ic_email),
        ),
    ),
    DrawerSectionData(
        titleRes = Res.string.section_actions,
        items = listOf(
            DrawerItemData(Screen.NOW, Res.drawable.ic_sun),
            DrawerItemData(Screen.NEXT_ACTIONS, Res.drawable.ic_play_arrow),
            DrawerItemData(Screen.SCHEDULED, Res.drawable.ic_date_range),
            DrawerItemData(Screen.WAITING, Res.drawable.ic_hourglass_empty),
        ),
    ),
    DrawerSectionData(
        titleRes = Res.string.section_projects,
        items = listOf(
            DrawerItemData(Screen.PROJECTS, Res.drawable.ic_list),
        ),
    ),
    DrawerSectionData(
        titleRes = Res.string.section_library,
        items = listOf(
            DrawerItemData(Screen.SOMEDAY, Res.drawable.ic_star),
            DrawerItemData(Screen.REFERENCE, Res.drawable.ic_book),
        ),
    ),
    DrawerSectionData(
        titleRes = Res.string.section_review,
        items = listOf(
            DrawerItemData(Screen.REVIEW, Res.drawable.ic_coffee),
        ),
    ),
    DrawerSectionData(
        titleRes = Res.string.section_history,
        items = listOf(
            DrawerItemData(Screen.COMPLETED, Res.drawable.ic_check_circle),
            DrawerItemData(Screen.TRASH, Res.drawable.ic_delete),
        ),
    ),
)

@Composable
fun AppDrawer(
    currentRoute: String = Screen.INBOX.id,
    itemCounts: Map<Screen, Int?> = emptyMap(),
    onNavigate: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val scrollState = rememberScrollState()

    ModalDrawerSheet {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_settings),
                        contentDescription = stringResource(Res.string.settings),
                    )
                }
            }

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(12.dp),
            ) {
                drawerSections.forEach { section ->
                    Text(
                        text = stringResource(section.titleRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                    )

                    section.items.forEach { item ->
                        val count = itemCounts[item.screen] ?: item.badge
                        val displayCount = count?.takeIf { it > 0 }
                        NavigationDrawerItem(
                            label = { Text(stringResource(item.screen.titleRes)) },
                            icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                            badge = displayCount?.let { cnt ->
                                { Text(cnt.toString()) }
                            },
                            selected = currentRoute == item.screen.id,
                            onClick = { onNavigate(item.screen.id) },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                NavigationDrawerItem(
                    label = { Text(stringResource(Res.string.logout_button)) },
                    icon = { Icon(painterResource(Res.drawable.ic_exit_to_app), contentDescription = null) },
                    selected = false,
                    onClick = onLogout,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
