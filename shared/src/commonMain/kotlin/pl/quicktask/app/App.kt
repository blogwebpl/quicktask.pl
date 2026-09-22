package pl.quicktask.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.quicktask.app.auth.presentation.AuthViewModel
import pl.quicktask.app.auth.presentation.LoginScreen
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.di.sharedAppModule
import pl.quicktask.app.main.presentation.MainLayout
import pl.quicktask.app.ui.theme.AppTheme
import pl.quicktask.app.ui.theme.ThemeManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import pl.quicktask.app.auth.session.SecretStorageStatus
import pl.quicktask.app.auth.session.SecretStorageAvailability

import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.auth.session.browserSessions
import pl.quicktask.app.auth.session.rememberBrowserUnlock
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.disable_remember_unlock
import todo.shared.generated.resources.session_memory_only_warning

@Composable
fun App(
    module: AppModule = sharedAppModule,
    authViewModel: AuthViewModel = viewModel { AuthViewModel(module.auth, module.logger) },
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by ThemeManager.themeMode.collectAsStateWithLifecycle()
    val storage by SecretStorageStatus.availability.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(uiState.isLoggedIn) {
        if (pl.quicktask.app.auth.session.browserSessions && uiState.isLoggedIn) {
            while (true) {
                kotlinx.coroutines.delay(500)
                if (module.sessionManager.changedInAnotherTab) {
                    module.sessionManager.clearSession()
                    module.keyStore.set(null)
                    authViewModel.invalidateLocalSession()
                    break
                }
            }
        }
    }

    AppTheme(themeMode = themeMode) {
      Column {
        if (storage == SecretStorageAvailability.MEMORY_ONLY) {
            Text(stringResource(Res.string.session_memory_only_warning))
        }
        if (uiState.isLoggedIn) {
            if (browserSessions) {
                TextButton(onClick = { rememberBrowserUnlock(false) }) {
                    Text(stringResource(Res.string.disable_remember_unlock))
                }
            }
            MainLayout(
                module = module,
                onLogout = { authViewModel.logout() },
            )
        } else if (uiState.isInitializing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LoginScreen(viewModel = authViewModel)
        }
      }
    }
}
