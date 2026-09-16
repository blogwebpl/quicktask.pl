package pl.quicktask.app.inbox

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFilePicker(
    onFilePicked: (InputFile?) -> Unit,
): () -> Unit = { onFilePicked(null) }
