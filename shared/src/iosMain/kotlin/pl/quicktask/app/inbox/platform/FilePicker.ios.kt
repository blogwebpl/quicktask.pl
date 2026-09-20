package pl.quicktask.app.inbox.platform

import androidx.compose.runtime.Composable
import pl.quicktask.app.items.model.InputFile

@Composable
actual fun rememberFilePicker(
    onFilePicked: (InputFile?) -> Unit,
): () -> Unit = unsupportedFilePicker(onFilePicked)
