package pl.quicktask.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.nextactions.model.NextActionContext
import pl.quicktask.app.nextactions.model.NextActionContextDto
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.action_add_chip
import todo.shared.generated.resources.contexts_title
import todo.shared.generated.resources.no_contexts

@Composable
internal fun ContextRowItem(
    contextName: String,
    lat: Double?,
    lon: Double?,
    radius: Double?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val hasGps = lat != null && lon != null
            if (hasGps) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                val displayName = if (contextName.startsWith("@")) contextName else "@$contextName"
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (hasGps) {
                    val radiusText = if (radius != null) " • R=${radius.toInt()}m" else ""
                    Text(
                        text = "GPS: $lat, $lon$radiusText",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class ContextDisplayItem(
    val name: String,
    val lat: Double?,
    val lon: Double?,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContextFieldSection(
    availableContexts: List<NextActionContext>,
    selectedContextIds: List<String>,
    newContexts: List<NewContextInput>,
    onOpenContextPicker: () -> Unit,
    initialContexts: List<NextActionContextDto> = emptyList(),
) {
    val selectedContextItems = buildList<ContextDisplayItem> {
        selectedContextIds.forEach { ctxId ->
            val foundAvailable = availableContexts.find { it.contextId == ctxId }
            val foundInitial = initialContexts.find { it.contextId == ctxId }
            val ctxName = foundAvailable?.name ?: foundInitial?.name
            val lat = foundAvailable?.lat ?: foundInitial?.lat
            val lon = foundAvailable?.lon ?: foundInitial?.lon
            if (ctxName != null && none { it.name == ctxName }) {
                add(ContextDisplayItem(ctxName, lat, lon))
            }
        }
        newContexts.forEach { newCtx ->
            if (none { it.name == newCtx.name }) {
                add(ContextDisplayItem(newCtx.name, newCtx.lat, newCtx.lon))
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(Res.string.contexts_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (selectedContextItems.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenContextPicker),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(Res.string.no_contexts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenContextPicker),
            ) {
                selectedContextItems.forEach { item ->
                    val name = item.name
                    val lat = item.lat
                    val lon = item.lon
                    val displayName = if (name.startsWith("@")) name else "@$name"
                    val hasGps = lat != null && lon != null
                    AssistChip(
                        onClick = onOpenContextPicker,
                        label = { Text(displayName) },
                        leadingIcon = if (hasGps) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }

                AssistChip(
                    onClick = onOpenContextPicker,
                    label = { Text(stringResource(Res.string.action_add_chip)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}
