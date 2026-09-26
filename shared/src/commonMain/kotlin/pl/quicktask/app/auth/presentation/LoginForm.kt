package pl.quicktask.app.auth.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.auth.model.OAuthProvidersDto
import pl.quicktask.app.auth.session.browserSessions
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.app_name
import todo.shared.generated.resources.auth_hide_password
import todo.shared.generated.resources.auth_login_first_title
import todo.shared.generated.resources.auth_login_title
import todo.shared.generated.resources.auth_register_title
import todo.shared.generated.resources.auth_show_password
import todo.shared.generated.resources.auth_unlock_subtitle
import todo.shared.generated.resources.auth_unlock_title
import todo.shared.generated.resources.auth_verification_subtitle
import todo.shared.generated.resources.auth_verification_title
import todo.shared.generated.resources.auth_verified_email
import todo.shared.generated.resources.email
import todo.shared.generated.resources.ic_visibility
import todo.shared.generated.resources.ic_visibility_off
import todo.shared.generated.resources.login_switch
import todo.shared.generated.resources.password
import todo.shared.generated.resources.password_min_length
import todo.shared.generated.resources.register_switch
import todo.shared.generated.resources.remember_email_checkbox
import todo.shared.generated.resources.remember_unlock_checkbox
import todo.shared.generated.resources.verification_code
import todo.shared.generated.resources.verification_hint

@Composable
internal fun LoginForm(
    viewModel: AuthViewModel,
    uiState: AuthUiState,
    oauthProviders: OAuthProvidersDto,
    form: LoginFormState,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LoginFormContent(viewModel, uiState, oauthProviders, form, maxWidth < 480.dp)
    }
}

@Composable
private fun LoginFormContent(
    viewModel: AuthViewModel,
    uiState: AuthUiState,
    oauthProviders: OAuthProvidersDto,
    form: LoginFormState,
    compact: Boolean,
) {
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val passwordTransformation = remember { PasswordVisualTransformation() }
    val submitKeyboardActions = KeyboardActions(onDone = { submitLoginForm(viewModel, uiState, form) })

    LaunchedEffect(form.email.isBlank(), form.registering, uiState.registrationId,
        uiState.oauthNeedsUnlock, uiState.isLoading, uiState.oauthEmail) {
        if (form.email.isBlank() && uiState.registrationId == null &&
            !uiState.oauthNeedsUnlock && !uiState.isLoading && uiState.oauthEmail == null) {
            emailFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(uiState.oauthEmail) {
        if (!uiState.oauthEmail.isNullOrBlank() && uiState.registrationId == null && !uiState.isLoading) {
            passwordFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (compact) 16.dp else 32.dp,
                vertical = if (compact) 24.dp else 48.dp,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(if (compact) 20.dp else 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Text(
                            text = stringResource(Res.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    val isSpecialStep = uiState.registrationId != null || uiState.oauthNeedsUnlock
                    if (!isSpecialStep) {
                        AuthModeSelector(
                            registering = form.registering,
                            enabled = !uiState.isLoading,
                            onSelect = { registering ->
                                if (form.registering != registering) {
                                    viewModel.cancelRegistration()
                                    form.switchMode(registering)
                                }
                            },
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    Text(
                        text = stringResource(when {
                            uiState.registrationId != null -> Res.string.auth_verification_title
                            uiState.oauthNeedsUnlock -> Res.string.auth_unlock_title
                            form.registering -> Res.string.auth_register_title
                            form.showWelcomeBack -> Res.string.auth_login_title
                            else -> Res.string.auth_login_first_title
                        }),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    when {
                        uiState.registrationId != null -> Text(
                            text = stringResource(Res.string.auth_verification_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        uiState.oauthNeedsUnlock -> Text(
                            text = stringResource(Res.string.auth_unlock_subtitle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (uiState.oauthEmail != null && uiState.oauthEmail == form.email.trim().lowercase()) {
                        AuthNotice(text = stringResource(Res.string.auth_verified_email), error = false)
                    }

                    if (uiState.registrationId != null) {
                        AuthNotice(text = stringResource(Res.string.verification_hint), error = false)
                        OutlinedTextField(
                            value = form.verificationCode,
                            onValueChange = { form.verificationCode = it },
                            label = { Text(stringResource(Res.string.verification_code)) },
                            enabled = !uiState.isLoading,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = submitKeyboardActions,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )
                    } else {
                        val showEmailError = !uiState.oauthNeedsUnlock &&
                            form.email.isNotBlank() && !isValidAuthEmail(form.email)
                        OutlinedTextField(
                            value = form.email,
                            onValueChange = form::updateEmail,
                            label = { Text(stringResource(Res.string.email)) },
                            enabled = !uiState.isLoading && !uiState.oauthNeedsUnlock,
                            isError = showEmailError,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(emailFocusRequester),
                            shape = MaterialTheme.shapes.medium,
                        )

                        OutlinedTextField(
                            value = form.password,
                            onValueChange = { form.password = it },
                            label = { Text(stringResource(Res.string.password)) },
                            visualTransformation = if (form.passwordVisible) VisualTransformation.None else passwordTransformation,
                            trailingIcon = {
                                IconButton(
                                    onClick = { form.passwordVisible = !form.passwordVisible },
                                    enabled = !uiState.isLoading,
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (form.passwordVisible) Res.drawable.ic_visibility_off else Res.drawable.ic_visibility,
                                        ),
                                        contentDescription = stringResource(
                                            if (form.passwordVisible) Res.string.auth_hide_password else Res.string.auth_show_password,
                                        ),
                                    )
                                }
                            },
                            enabled = !uiState.isLoading,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = submitKeyboardActions,
                            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester),
                            shape = MaterialTheme.shapes.medium,
                        )

                        if (!form.registering && !uiState.oauthNeedsUnlock) {
                            AuthCheckbox(
                                checked = form.rememberEmail,
                                enabled = !uiState.isLoading,
                                onCheckedChange = form::updateRememberEmail,
                                label = stringResource(Res.string.remember_email_checkbox),
                            )
                        }

                        if (!uiState.oauthNeedsUnlock &&
                            (form.registering || form.password.isNotEmpty() && form.password.length < MIN_AUTH_PASSWORD_LENGTH)) {
                            Text(
                                text = stringResource(Res.string.password_min_length),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (browserSessions) {
                            AuthCheckbox(
                                checked = form.rememberUnlock,
                                enabled = !uiState.isLoading,
                                onCheckedChange = { form.rememberUnlock = it },
                                label = stringResource(Res.string.remember_unlock_checkbox),
                            )
                        }
                    }

                    val displayedError = uiState.errorMessage
                        ?: uiState.errorMessageRes?.let { stringResource(it) }
                    if (displayedError != null) AuthNotice(text = displayedError, error = true)

                    LoginFormActions(viewModel, uiState, oauthProviders, form)
                }
            }
        }
    }
}

@Composable
private fun AuthModeSelector(
    registering: Boolean,
    enabled: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .selectableGroup()
            .padding(4.dp),
    ) {
        listOf(false to Res.string.login_switch, true to Res.string.register_switch).forEach { (isRegister, label) ->
            val selected = registering == isRegister
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(AuthActionHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainer)
                    .selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { onSelect(isRegister) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun AuthCheckbox(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(
                value = checked,
                enabled = enabled,
                onValueChange = onCheckedChange,
                role = Role.Checkbox,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AuthNotice(text: String, error: Boolean) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = text,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}
