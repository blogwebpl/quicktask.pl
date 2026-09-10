package pl.quicktask.todo.inbox

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFilePicker(
    onFilePicked: (InputFile?) -> Unit,
): () -> Unit
