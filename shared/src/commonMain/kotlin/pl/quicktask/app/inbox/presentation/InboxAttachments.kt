package pl.quicktask.app.inbox.presentation

import pl.quicktask.app.items.model.AttachmentMetadataState
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InputFile


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import todo.shared.generated.resources.attachment_metadata_decryption_failed
import todo.shared.generated.resources.attachment_unnamed
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.task_remove_file_description
import todo.shared.generated.resources.task_uploading_files

@Composable
internal fun ExistingAttachmentCard(
    attachment: DecryptedAttachment,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    AttachmentCardContent(
        text = when (attachment.metadataState) {
            AttachmentMetadataState.DECRYPTION_FAILED -> stringResource(Res.string.attachment_metadata_decryption_failed)
            AttachmentMetadataState.AVAILABLE -> attachment.name ?: stringResource(Res.string.attachment_unnamed)
        },
        enabled = enabled,
        onRemove = onRemove,
    )
}

@Composable
internal fun AttachmentCard(
    file: InputFile,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    AttachmentCardContent(
        text = "${file.fileName} (${formatFileSize(file.bytes.size.toLong())})",
        enabled = enabled,
        onRemove = onRemove,
    )
}

@Composable
private fun AttachmentCardContent(
    text: String,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.task_remove_file_description),
                )
            }
        }
    }
}

private fun formatFileSize(sizeInBytes: Long): String {
    return when {
        sizeInBytes < 1024 -> "$sizeInBytes B"
        sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
        else -> "${sizeInBytes / (1024 * 1024)} MB"
    }
}
