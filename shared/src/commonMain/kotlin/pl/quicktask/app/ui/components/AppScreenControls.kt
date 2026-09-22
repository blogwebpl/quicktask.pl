package pl.quicktask.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.main.model.Screen
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.app_name
import todo.shared.generated.resources.ic_menu
import todo.shared.generated.resources.menu

@Composable
internal fun AppTopBar(
    title: String,
    onOpenDrawer: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    appName: String? = stringResource(Res.string.app_name),
) {
    val allScreenTitles = Screen.entries.map { stringResource(it.titleRes) }

    TopAppBar(
        title = {
            AutoFitTopBarTitle(
                title = title,
                appName = appName,
                allScreenTitles = allScreenTitles,
            )
        },
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
internal fun AutoFitTopBarTitle(
    title: String,
    appName: String?,
    allScreenTitles: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val shortTitle = if ((appName != null) && title.startsWith("$appName - ")) {
        title.removePrefix("$appName - ")
    } else if ((appName != null) && title.startsWith(appName) && title.contains(" - ")) {
        title.substringAfter(" - ")
    } else {
        title
    }

    if (appName == null) {
        Text(
            text = title,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    } else {
        BoxWithConstraints(modifier = modifier) {
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val maxPx = with(density) { maxWidth.toPx() }

            val canFitAllTitles = remember(appName, allScreenTitles, maxPx, style) {
                allScreenTitles.all { screenTitle ->
                    val fullText = "$appName - $screenTitle"
                    val result = textMeasurer.measure(
                        text = AnnotatedString(fullText),
                        style = style,
                        maxLines = 1,
                    )
                    (result.size.width <= maxPx) && (!result.hasVisualOverflow)
                }
            }

            val fullTitle = if (title.startsWith(appName)) title else "$appName - $title"

            val displayTitle = if (canFitAllTitles) {
                fullTitle
            } else {
                shortTitle
            }

            Text(
                text = displayTitle,
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AppAddButton(onClick: () -> Unit, contentDescription: String?) {
    FloatingActionButton(onClick = onClick, shape = CircleShape) {
        Icon(imageVector = Icons.Default.Add, contentDescription = contentDescription)
    }
}
