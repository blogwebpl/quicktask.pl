package pl.quicktask.app.auth.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import pl.quicktask.app.auth.session.browserCall
import pl.quicktask.app.auth.session.browserSessions
import pl.quicktask.app.auth.session.RememberedLoginEmail
import pl.quicktask.app.di.sharedAppModule
import pl.quicktask.app.ui.theme.AppTheme
import pl.quicktask.app.ui.theme.ThemeMode

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val oauthProviders by viewModel.oauthProviders.collectAsStateWithLifecycle()
    val deepLinkTicket by OAuthDeepLinks.ticket.collectAsStateWithLifecycle()
    val deepLinkError by OAuthDeepLinks.error.collectAsStateWithLifecycle()
    val form = remember { LoginFormState() }

    LaunchedEffect(Unit) {
        if (browserSessions) {
            val result = browserCall("oauthTicket")
            val ticket = result["ticket"]?.jsonPrimitive?.contentOrNull
            if (!ticket.isNullOrBlank()) viewModel.completeOAuthCallback(ticket)
            if (result["error"]?.jsonPrimitive?.booleanOrNull == true) viewModel.oauthFailed()
        }
    }
    LaunchedEffect(uiState.oauthEmail) {
        uiState.oauthEmail?.let { verifiedEmail ->
            form.updateEmail(verifiedEmail)
            form.switchMode(false)
        }
    }
    LaunchedEffect(deepLinkTicket) {
        deepLinkTicket?.let { ticket ->
            OAuthDeepLinks.clear()
            viewModel.completeOAuthCallback(ticket)
        }
    }
    LaunchedEffect(deepLinkError) {
        if (deepLinkError) {
            OAuthDeepLinks.clearError()
            viewModel.oauthFailed()
        }
    }

    LoginForm(viewModel, uiState, oauthProviders, form)
}

internal class LoginFormState(private val rememberedLoginEmail: RememberedLoginEmail = RememberedLoginEmail()) {
    private val initialEmail = rememberedLoginEmail.load()
    private val hadRememberedEmail = initialEmail.isNotBlank()
    var email by mutableStateOf(initialEmail)
        private set
    var rememberEmail by mutableStateOf(hadRememberedEmail)
        private set
    val showWelcomeBack: Boolean
        get() = hadRememberedEmail && rememberEmail && email.isNotBlank()

    fun updateEmail(value: String) {
        email = value
        if (rememberEmail && !registering) rememberedLoginEmail.save(value)
    }

    fun updateRememberEmail(enabled: Boolean) {
        rememberEmail = enabled
        if (enabled) rememberedLoginEmail.save(email) else rememberedLoginEmail.clear()
    }
    var password by mutableStateOf("")
    var registering by mutableStateOf(false)
        private set
    fun switchMode(toRegistration: Boolean) {
        if (registering == toRegistration) return
        registering = toRegistration
        password = ""
        passwordVisible = false
        if (!toRegistration && rememberEmail) rememberedLoginEmail.save(email)
    }
    var verificationCode by mutableStateOf("")
    var rememberUnlock by mutableStateOf(false)
    var passwordVisible by mutableStateOf(false)
}

@Preview(name = "Login - light")
@Composable
fun LoginScreenPreview() {
    val module = sharedAppModule
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LoginScreen(viewModel = viewModel { AuthViewModel(module.auth, module.logger) })
    }
}

@Preview(name = "Login - dark")
@Composable
fun LoginScreenDarkPreview() {
    val module = sharedAppModule
    AppTheme(themeMode = ThemeMode.DARK) {
        LoginScreen(viewModel = viewModel { AuthViewModel(module.auth, module.logger) })
    }
}
