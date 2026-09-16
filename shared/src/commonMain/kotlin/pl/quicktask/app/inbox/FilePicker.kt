package pl.quicktask.app.inbox

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFilePicker(
    onFilePicked: (InputFile?) -> Unit,
): () -> Unit
