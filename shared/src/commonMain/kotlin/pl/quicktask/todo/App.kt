package pl.quicktask.todo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.quicktask.todo.auth.AuthViewModel
import pl.quicktask.todo.auth.LoginScreen
import pl.quicktask.todo.main.MainLayout

@Composable
@Preview
fun App(
    authViewModel: AuthViewModel = viewModel(),
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

    MaterialTheme {
        if (uiState.isLoggedIn) {
            MainLayout()
        } else {
            LoginScreen(viewModel = authViewModel)
        }
    }
}
