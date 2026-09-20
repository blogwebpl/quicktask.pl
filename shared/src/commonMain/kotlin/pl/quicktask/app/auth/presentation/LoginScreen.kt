package pl.quicktask.app.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Row
import pl.quicktask.app.auth.session.browserSessions
import pl.quicktask.app.auth.session.rememberBrowserUnlock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.di.sharedAppModule
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.email
import todo.shared.generated.resources.login_button
import todo.shared.generated.resources.password

import todo.shared.generated.resources.remember_unlock_checkbox

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("admin@example.com") }
    var password by remember { mutableStateOf("admin") }
    var rememberUnlock by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(Res.string.email)) },
                enabled = !uiState.isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(Res.string.password)) },
                visualTransformation = PasswordVisualTransformation(),
                enabled = !uiState.isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (browserSessions) rememberBrowserUnlock(rememberUnlock); viewModel.login(email, password) },
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            val displayedError = when {
                uiState.errorMessage != null -> uiState.errorMessage
                uiState.errorMessageRes != null -> stringResource(uiState.errorMessageRes!!)
                else -> null
            }

            displayedError?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (browserSessions) {
                Row {
                    Checkbox(rememberUnlock, { rememberUnlock = it })
                    Text(stringResource(Res.string.remember_unlock_checkbox))
                }
            }
            val onLoginClick = remember(viewModel, email, password, rememberUnlock) {
                { if (browserSessions) rememberBrowserUnlock(rememberUnlock); viewModel.login(email, password) }
            }

            Button(
                onClick = onLoginClick,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(Res.string.login_button))
                }
            }
        }
    }
}

@Preview
@Composable
fun LoginScreenPreview() {
    val module = sharedAppModule
    LoginScreen(viewModel = viewModel { AuthViewModel(module.auth, module.logger) })
}
