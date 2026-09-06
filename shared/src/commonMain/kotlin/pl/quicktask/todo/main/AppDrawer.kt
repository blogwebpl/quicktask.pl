package pl.quicktask.todo.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.drawer_content
import todo.shared.generated.resources.logout_button

@Composable
fun AppDrawer(
    onLogout: () -> Unit = {},
) {
    ModalDrawerSheet {
        Text(
            text = stringResource(Res.string.drawer_content),
            modifier = Modifier.padding(16.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.logout_button)) },
            selected = false,
            onClick = onLogout,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
