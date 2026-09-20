package pl.quicktask.app.inbox.presentation.dialogs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.inbox.presentation.TWO_MINUTE_DURATION_SECONDS
import pl.quicktask.app.items.model.InboxItem
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.timer_cancel
import todo.shared.generated.resources.timer_done
import todo.shared.generated.resources.timer_remaining
import todo.shared.generated.resources.timer_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TwoMinuteTimerDialog(
    item: InboxItem,
    remainingSeconds: Int,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val progress = (remainingSeconds.toFloat() / TWO_MINUTE_DURATION_SECONDS).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
    )

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeFormatted = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.timer_title)) },
                    navigationIcon = {
                        IconButton(onClick = onCancel, enabled = true) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.timer_cancel),
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Box(
                    modifier = Modifier.size(260.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 14.dp,
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.timer_remaining),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.timer_cancel),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Button(
                        onClick = onDone,
                        enabled = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                    ) {
                        Text(text = stringResource(Res.string.timer_done), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
