package pl.quicktask.app.inbox.platform

import androidx.compose.runtime.Composable
import pl.quicktask.app.items.model.InputFile

@Composable
expect fun rememberFilePicker(
    onFilePicked: (InputFile?) -> Unit,
): () -> Unit

internal fun unsupportedFilePicker(onFilePicked: (InputFile?) -> Unit): () -> Unit = {
    onFilePicked(null)
}
