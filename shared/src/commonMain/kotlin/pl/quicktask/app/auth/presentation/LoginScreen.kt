package pl.quicktask.app.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import pl.quicktask.app.auth.session.browserCall
import pl.quicktask.app.auth.session.browserSessions
import pl.quicktask.app.auth.session.rememberBrowserUnlock
import pl.quicktask.app.di.sharedAppModule
import pl.quicktask.app.network.config.ApiConfig
import pl.quicktask.app.ui.theme.AppTheme
import pl.quicktask.app.ui.theme.ThemeMode
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.app_name
import todo.shared.generated.resources.auth_change_email
import todo.shared.generated.resources.auth_email_example
import todo.shared.generated.resources.auth_have_account
import todo.shared.generated.resources.auth_hide_password
import todo.shared.generated.resources.auth_login_subtitle
import todo.shared.generated.resources.auth_login_title
import todo.shared.generated.resources.auth_no_account
import todo.shared.generated.resources.auth_register_subtitle
import todo.shared.generated.resources.auth_register_title
import todo.shared.generated.resources.auth_show_password
import todo.shared.generated.resources.auth_verification_subtitle
import todo.shared.generated.resources.auth_verification_title
import todo.shared.generated.resources.auth_google_button
import todo.shared.generated.resources.auth_apple_button
import todo.shared.generated.resources.auth_verified_email
import todo.shared.generated.resources.auth_unlock_title
import todo.shared.generated.resources.auth_unlock_subtitle
import todo.shared.generated.resources.auth_unlock_button
import todo.shared.generated.resources.auth_switch_account
import todo.shared.generated.resources.email
import todo.shared.generated.resources.login_button
import todo.shared.generated.resources.password
import todo.shared.generated.resources.password_min_length
import todo.shared.generated.resources.remember_unlock_checkbox
import todo.shared.generated.resources.register_button
import todo.shared.generated.resources.register_switch
import todo.shared.generated.resources.login_switch
import todo.shared.generated.resources.verification_code
import todo.shared.generated.resources.verification_hint
import todo.shared.generated.resources.verify_button

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val oauthProviders by viewModel.oauthProviders.collectAsStateWithLifecycle()
    val deepLinkTicket by OAuthDeepLinks.ticket.collectAsStateWithLifecycle()
    val deepLinkError by OAuthDeepLinks.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registering by remember { mutableStateOf(false) }
    var verificationCode by remember { mutableStateOf("") }
    var rememberUnlock by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

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
            email = verifiedEmail
            registering = false
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(Res.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))

                if (uiState.oauthEmail != null && uiState.oauthEmail == email.trim().lowercase()) {
                    Text(
                        text = stringResource(Res.string.auth_verified_email),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = stringResource(when {
                        uiState.registrationId != null -> Res.string.auth_verification_title
                        uiState.oauthNeedsUnlock -> Res.string.auth_unlock_title
                        registering -> Res.string.auth_register_title
                        else -> Res.string.auth_login_title
                    }),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(when {
                        uiState.registrationId != null -> Res.string.auth_verification_subtitle
                        uiState.oauthNeedsUnlock -> Res.string.auth_unlock_subtitle
                        registering -> Res.string.auth_register_subtitle
                        else -> Res.string.auth_login_subtitle
                    }),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (uiState.registrationId != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(Res.string.verification_hint),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { verificationCode = it },
                        label = { Text(stringResource(Res.string.verification_code)) },
                        enabled = !uiState.isLoading,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (verificationCode.isNotBlank() && !uiState.isLoading) {
                                viewModel.verifyRegistration(verificationCode, email, password)
                            }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(Res.string.email)) },
                        placeholder = { Text(stringResource(Res.string.auth_email_example)) },
                        enabled = !uiState.isLoading && !uiState.oauthNeedsUnlock,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(Res.string.password)) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(stringResource(if (passwordVisible) Res.string.auth_hide_password else Res.string.auth_show_password))
                            }
                        },
                        supportingText = if (registering) {
                            { Text(stringResource(Res.string.password_min_length)) }
                        } else null,
                        enabled = !uiState.isLoading,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!uiState.isLoading && password.isNotBlank() &&
                                    (uiState.oauthNeedsUnlock || email.isNotBlank())) {
                                    if (uiState.oauthNeedsUnlock) {
                                        if (browserSessions) rememberBrowserUnlock(rememberUnlock)
                                        viewModel.unlockOAuthKeys(password)
                                    } else if (registering) viewModel.register(email.trim(), password)
                                    else {
                                        if (browserSessions) rememberBrowserUnlock(rememberUnlock)
                                        viewModel.login(email.trim(), password)
                                    }
                                }
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    )
                }

                val displayedError = when {
                    uiState.errorMessage != null -> uiState.errorMessage
                    uiState.errorMessageRes != null -> stringResource(uiState.errorMessageRes!!)
                    else -> null
                }

                if (displayedError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = displayedError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (browserSessions && !registering && uiState.registrationId == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .toggleable(
                                value = rememberUnlock,
                                enabled = !uiState.isLoading,
                                onValueChange = { rememberUnlock = it },
                                role = Role.Checkbox
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = rememberUnlock,
                            enabled = !uiState.isLoading,
                            onCheckedChange = null // Handled by Row toggleable
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(Res.string.remember_unlock_checkbox),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                val onLoginClick = remember(viewModel, email, password, rememberUnlock, registering, verificationCode,
                    uiState.registrationId, uiState.oauthNeedsUnlock) {
                    {
                        when {
                            uiState.registrationId != null -> viewModel.verifyRegistration(verificationCode, email, password)
                            uiState.oauthNeedsUnlock -> {
                                if (browserSessions) rememberBrowserUnlock(rememberUnlock)
                                viewModel.unlockOAuthKeys(password)
                            }
                            registering -> viewModel.register(email.trim(), password)
                            else -> {
                                if (browserSessions) rememberBrowserUnlock(rememberUnlock)
                                viewModel.login(email.trim(), password)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onLoginClick,
                    enabled = !uiState.isLoading &&
                        (if (uiState.registrationId != null) verificationCode.isNotBlank()
                         else if (uiState.oauthNeedsUnlock) password.isNotBlank()
                         else email.isNotBlank() && password.isNotBlank()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(when {
                                uiState.registrationId != null -> Res.string.verify_button
                                uiState.oauthNeedsUnlock -> Res.string.auth_unlock_button
                                registering -> Res.string.register_button
                                else -> Res.string.login_button
                            }),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                if (oauthPlatform != "desktop" && uiState.registrationId == null && !uiState.oauthNeedsUnlock &&
                    (oauthProviders.google || oauthProviders.apple)) {
                    if (oauthProviders.google) {
                        OutlinedButton(
                            onClick = {
                                if (browserSessions) scope.launch { browserCall("oauthStart", "provider" to "google") }
                                else uriHandler.openUri("${ApiConfig.BASE_URL}/auth/google/start?platform=$oauthPlatform")
                            },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text(stringResource(Res.string.auth_google_button)) }
                    }
                    if (oauthProviders.apple) {
                        OutlinedButton(
                            onClick = {
                                if (browserSessions) scope.launch { browserCall("oauthStart", "provider" to "apple") }
                                else uriHandler.openUri("${ApiConfig.BASE_URL}/auth/apple/start?platform=$oauthPlatform")
                            },
                            enabled = !uiState.isLoading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text(stringResource(Res.string.auth_apple_button)) }
                    }
                }
                if (uiState.registrationId != null) {
                    TextButton(
                        onClick = {
                            viewModel.cancelRegistration()
                            verificationCode = ""
                        },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(Res.string.auth_change_email))
                    }
                } else if (uiState.oauthNeedsUnlock) {
                    TextButton(
                        onClick = { viewModel.logout() },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text(stringResource(Res.string.auth_switch_account)) }
                } else {
                    Row(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(if (registering) Res.string.auth_have_account else Res.string.auth_no_account),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                viewModel.cancelRegistration()
                                registering = !registering
                                password = ""
                                passwordVisible = false
                            },
                            enabled = !uiState.isLoading,
                        ) {
                            Text(stringResource(if (registering) Res.string.login_switch else Res.string.register_switch))
                        }
                    }
                }
            }
        }
    }
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
