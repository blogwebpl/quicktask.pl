package pl.quicktask.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.quicktask.app.auth.AuthViewModel
import pl.quicktask.app.auth.LoginScreen
import pl.quicktask.app.main.MainLayout
import pl.quicktask.app.ui.theme.AppTheme
import pl.quicktask.app.ui.theme.ThemeManager

@Composable
@Preview
fun App(
    authViewModel: AuthViewModel = viewModel { AuthViewModel() },
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by ThemeManager.themeMode.collectAsStateWithLifecycle()

    AppTheme(themeMode = themeMode) {
        if (uiState.isLoggedIn) {
            MainLayout(
                onLogout = { authViewModel.logout() },
            )
        } else {
            LoginScreen(viewModel = authViewModel)
        }
    }
}
