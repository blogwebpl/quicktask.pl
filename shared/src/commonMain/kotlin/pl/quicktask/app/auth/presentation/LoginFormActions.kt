package pl.quicktask.app.auth.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.auth.model.OAuthProvidersDto
import pl.quicktask.app.auth.session.browserCall
import pl.quicktask.app.auth.session.browserSessions
import pl.quicktask.app.auth.session.rememberBrowserUnlock
import pl.quicktask.app.network.config.ApiConfig
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.auth_apple_button
import todo.shared.generated.resources.auth_change_email
import todo.shared.generated.resources.auth_google_button
import todo.shared.generated.resources.auth_switch_account
import todo.shared.generated.resources.auth_unlock_button
import todo.shared.generated.resources.login_button
import todo.shared.generated.resources.register_button
import todo.shared.generated.resources.verify_button

@Composable
internal fun ColumnScope.LoginFormActions(
    viewModel: AuthViewModel,
    uiState: AuthUiState,
    oauthProviders: OAuthProvidersDto,
    form: LoginFormState,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val canSubmit = canSubmitLoginForm(uiState, form)
    val actionLabel = when {
        uiState.registrationId != null -> Res.string.verify_button
        uiState.oauthNeedsUnlock -> Res.string.auth_unlock_button
        form.registering -> Res.string.register_button
        else -> Res.string.login_button
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(
        onClick = { submitLoginForm(viewModel, uiState, form) },
        enabled = !uiState.isLoading && canSubmit,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = stringResource(actionLabel),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    if (oauthPlatform != "desktop" && uiState.registrationId == null && !uiState.oauthNeedsUnlock) {
        if (oauthProviders.google) {
            OutlinedButton(
                onClick = {
                    if (browserSessions) {
                        scope.launch { browserCall("oauthStart", "provider" to "google") }
                    } else {
                        uriHandler.openUri("${ApiConfig.BASE_URL}/auth/google/start?platform=$oauthPlatform")
                    }
                },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(Res.string.auth_google_button))
            }
        }
        if (oauthProviders.apple) {
            OutlinedButton(
                onClick = {
                    if (browserSessions) {
                        scope.launch { browserCall("oauthStart", "provider" to "apple") }
                    } else {
                        uriHandler.openUri("${ApiConfig.BASE_URL}/auth/apple/start?platform=$oauthPlatform")
                    }
                },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(Res.string.auth_apple_button))
            }
        }
    }

    when {
        uiState.registrationId != null -> {
            TextButton(
                onClick = {
                    viewModel.cancelRegistration()
                    form.verificationCode = ""
                },
                enabled = !uiState.isLoading,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(Res.string.auth_change_email))
            }
        }
        uiState.oauthNeedsUnlock -> {
            TextButton(
                onClick = viewModel::logout,
                enabled = !uiState.isLoading,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(Res.string.auth_switch_account))
            }
        }
    }
}

internal fun submitLoginForm(
    viewModel: AuthViewModel,
    uiState: AuthUiState,
    form: LoginFormState,
) {
    if (uiState.isLoading || !canSubmitLoginForm(uiState, form)) return

    when {
        uiState.registrationId != null -> {
            viewModel.verifyRegistration(form.verificationCode, form.email, form.password)
        }
        uiState.oauthNeedsUnlock -> {
            if (browserSessions) rememberBrowserUnlock(form.rememberUnlock)
            viewModel.unlockOAuthKeys(form.password)
        }
        form.registering -> viewModel.register(form.email.trim(), form.password)
        else -> {
            if (browserSessions) rememberBrowserUnlock(form.rememberUnlock)
            viewModel.login(form.email.trim(), form.password)
        }
    }
}

internal fun canSubmitLoginForm(uiState: AuthUiState, form: LoginFormState): Boolean = when {
    uiState.registrationId != null -> form.verificationCode.isNotBlank()
    uiState.oauthNeedsUnlock -> form.password.isNotBlank()
    else -> isValidAuthEmail(form.email) && form.password.length >= MIN_AUTH_PASSWORD_LENGTH
}

internal const val MIN_AUTH_PASSWORD_LENGTH = 12

private val authEmailPattern = Regex(
    """^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$"""
)

internal fun isValidAuthEmail(value: String): Boolean =
    value.trim().let { it.length <= 254 && authEmailPattern.matches(it) }
