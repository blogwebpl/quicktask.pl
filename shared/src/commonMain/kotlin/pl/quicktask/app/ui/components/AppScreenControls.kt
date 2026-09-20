package pl.quicktask.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.ic_menu
import todo.shared.generated.resources.menu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppTopBar(
    title: String,
    onOpenDrawer: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
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
        actions = actions,
    )
}

@Composable
internal fun AppAddButton(onClick: () -> Unit, contentDescription: String?) {
    FloatingActionButton(onClick = onClick, shape = CircleShape) {
        Icon(imageVector = Icons.Default.Add, contentDescription = contentDescription)
    }
}
