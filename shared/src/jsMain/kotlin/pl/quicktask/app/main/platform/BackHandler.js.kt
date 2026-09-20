package pl.quicktask.app.main.platform

import androidx.compose.runtime.Composable

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op w przeglądarce JS
}
