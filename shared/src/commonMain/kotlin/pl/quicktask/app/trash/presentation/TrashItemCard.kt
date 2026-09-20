package pl.quicktask.app.trash.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.trash.model.TrashItem
import todo.shared.generated.resources.*
@Composable
internal fun TrashItemCard(
    item: TrashItem,
    isOperating: Boolean,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPending = item.isPendingConfirmation

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (isPending) 0.65f else 1.0f),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = stringResource(item.originListNameRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                if (item.attachments.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_attach_file),
                            contentDescription = stringResource(Res.string.trash_attachments_description),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.attachments.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onRestore,
                    enabled = !isOperating && !isPending,
                ) {
                    Text(stringResource(Res.string.action_restore))
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDeletePermanently,
                    enabled = !isOperating && !isPending,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_delete),
                        contentDescription = stringResource(Res.string.trash_delete_permanently_description),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
