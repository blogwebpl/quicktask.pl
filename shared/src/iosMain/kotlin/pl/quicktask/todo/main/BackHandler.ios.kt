package pl.quicktask.todo.main

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op na platformie iOS
}
