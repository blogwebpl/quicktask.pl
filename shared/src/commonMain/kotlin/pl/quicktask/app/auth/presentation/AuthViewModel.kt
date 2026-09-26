package pl.quicktask.app.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.auth.data.AuthOperations
import pl.quicktask.app.auth.model.ApiException
import pl.quicktask.app.auth.model.OAuthProvidersDto
import pl.quicktask.app.auth.session.browserSessions
import pl.quicktask.app.common.AppLogger
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.common.LogLevel
import pl.quicktask.app.common.NoOpAppLogger
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_user_keys_locked
import todo.shared.generated.resources.error_authentication_failed
import todo.shared.generated.resources.password_min_length

data class AuthUiState(
    val isLoading: Boolean = false,
    val isInitializing: Boolean = false,
    val errorMessageRes: StringResource? = null,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val registrationId: String? = null,
    val oauthEmail: String? = null,
    val oauthNeedsUnlock: Boolean = false,
)

class AuthViewModel(
    private val repository: AuthOperations,
    private val logger: AppLogger = NoOpAppLogger,
) : ViewModel() {
    private var pendingOAuthLinkTicket: String? = null

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isLoading = repository.isLoggedIn,
            isInitializing = repository.isLoggedIn,
            isLoggedIn = false,
        ),
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private val _oauthProviders = MutableStateFlow(OAuthProvidersDto())
    val oauthProviders: StateFlow<OAuthProvidersDto> = _oauthProviders.asStateFlow()

    init {
        if (oauthPlatform != "desktop") viewModelScope.launch {
            try { _oauthProviders.value = repository.oauthProviders() }
            catch (_: Exception) { /* Password login remains available if provider discovery fails. */ }
        }
        if (repository.isLoggedIn) {
            viewModelScope.launch {
                AppLoggerManager.logRefresh("AuthViewModel", "Sprawdzanie przywracania zbuforowanych kluczy")
                val restored = repository.tryRestoreCachedKeys()
                if (restored) {
                    AppLoggerManager.logStateChange("AuthViewModel", "Przywrócono klucze, uzytkownik zalogowany")
                    _uiState.update { AuthUiState(isLoggedIn = true, isInitializing = false) }
                } else {
                    if (!browserSessions) repository.logout()
                    AppLoggerManager.logStateChange("AuthViewModel", "Klucze zablokowane, wymagane ponowne logowanie")
                    _uiState.update {
                        AuthUiState(
                            isLoggedIn = false,
                            isInitializing = false,
                            errorMessageRes = Res.string.error_user_keys_locked,
                        )
                    }
                }
            }
        }
    }

    fun login(email: String, password: String) {
        if (_uiState.value.isLoading) return
        AppLoggerManager.logFunction("AuthViewModel", "login", "email=$email")
        _uiState.update { AuthUiState(isLoading = true) }

        viewModelScope.launch {
            val result = repository.login(email, password)
            result.onSuccess {
                pendingOAuthLinkTicket?.let { ticket ->
                    repository.linkOAuthIdentity(ticket)
                    pendingOAuthLinkTicket = null
                }
                AppLoggerManager.logStateChange("AuthViewModel", "Logowanie zakończone sukcesem")
                _uiState.update { AuthUiState(isLoggedIn = true) }
            }.onFailure { error ->
                AppLoggerManager.logStateChange("AuthViewModel", "Niepowodzenie logowania", error.message)
                logger.log(LogLevel.WARNING, "auth.login", (error as? ApiException)?.statusCode, (error as? ApiException)?.code)
                val (errorRes, customMsg) = mapAuthErrorToState(error)
                _uiState.update {
                    AuthUiState(
                        errorMessageRes = errorRes,
                        errorMessage = customMsg,
                    )
                }
            }
        }
    }

    fun completeOAuthCallback(ticket: String) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null, errorMessageRes = null) }
        viewModelScope.launch {
            repository.redeemOAuthTicket(ticket).onSuccess { result ->
                pendingOAuthLinkTicket = result.linkTicket
                _uiState.update { it.copy(isLoading = false, oauthEmail = result.email,
                    oauthNeedsUnlock = result.linked) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, errorMessageRes = Res.string.error_authentication_failed) }
            }
        }
    }

    fun unlockOAuthKeys(password: String) {
        if (_uiState.value.isLoading || !_uiState.value.oauthNeedsUnlock) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null, errorMessageRes = null) }
        viewModelScope.launch {
            repository.unlockOAuthKeys(password).onSuccess {
                _uiState.update { AuthUiState(isLoggedIn = true) }
            }.onFailure { error ->
                val (res, message) = mapAuthErrorToState(error)
                _uiState.update { it.copy(isLoading = false, errorMessageRes = res, errorMessage = message) }
            }
        }
    }

    fun oauthFailed() {
        _uiState.update { it.copy(errorMessageRes = Res.string.error_authentication_failed, errorMessage = null) }
    }

    fun register(email: String, password: String) {
        if (_uiState.value.isLoading) return
        AppLoggerManager.logFunction("AuthViewModel", "register", "email=${email.trim()}")
        if (password.length < MIN_AUTH_PASSWORD_LENGTH) {
            AppLoggerManager.logStateChange(
                "AuthViewModel",
                "Rejestracja zatrzymana przez walidację klienta",
                "passwordLength=${password.length}, minimum=$MIN_AUTH_PASSWORD_LENGTH",
            )
            _uiState.update { it.copy(errorMessageRes = Res.string.password_min_length, errorMessage = null) }
            return
        }
        _uiState.update { AuthUiState(isLoading = true) }
        viewModelScope.launch {
            repository.register(email, password).onSuccess { id ->
                AppLoggerManager.logStateChange("AuthViewModel", "Serwer przyjął rejestrację do weryfikacji")
                _uiState.update { AuthUiState(registrationId = id) }
            }.onFailure { error ->
                AppLoggerManager.logStateChange("AuthViewModel", "Niepowodzenie rejestracji", error.message)
                logger.log(LogLevel.WARNING, "auth.register", (error as? ApiException)?.statusCode, (error as? ApiException)?.code)
                val (errorRes, customMsg) = mapAuthErrorToState(error)
                _uiState.update { AuthUiState(errorMessageRes = errorRes, errorMessage = customMsg) }
            }
        }
    }

    fun verifyRegistration(code: String, email: String, password: String) {
        val id = _uiState.value.registrationId ?: return
        if (_uiState.value.isLoading) return
        AppLoggerManager.logFunction("AuthViewModel", "verifyRegistration")
        _uiState.update { it.copy(isLoading = true, errorMessage = null, errorMessageRes = null) }
        viewModelScope.launch {
            repository.verifyRegistration(id, code, email, password).onSuccess {
                pendingOAuthLinkTicket?.let { ticket ->
                    repository.linkOAuthIdentity(ticket)
                    pendingOAuthLinkTicket = null
                }
                AppLoggerManager.logStateChange("AuthViewModel", "Konto utworzone i użytkownik zalogowany")
                _uiState.update { AuthUiState(isLoggedIn = true) }
            }.onFailure { error ->
                AppLoggerManager.logStateChange("AuthViewModel", "Niepowodzenie potwierdzenia rejestracji", error.message)
                logger.log(LogLevel.WARNING, "auth.register.verify", (error as? ApiException)?.statusCode, (error as? ApiException)?.code)
                val (errorRes, customMsg) = mapAuthErrorToState(error)
                _uiState.update { AuthUiState(registrationId = id, errorMessageRes = errorRes, errorMessage = customMsg) }
            }
        }
    }

    fun cancelRegistration() {
        _uiState.update { AuthUiState() }
    }

    fun logout() {
        pendingOAuthLinkTicket = null
        AppLoggerManager.logFunction("AuthViewModel", "logout")
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            repository.logout()
            AppLoggerManager.logStateChange("AuthViewModel", "Wylogowano użytkownika")
            _uiState.update { AuthUiState(isLoggedIn = false) }
        }
    }

    fun invalidateLocalSession() {
        AppLoggerManager.logFunction("AuthViewModel", "invalidateLocalSession")
        AppLoggerManager.logStateChange("AuthViewModel", "Unieważniono sesję lokalną")
        _uiState.value = AuthUiState(isLoggedIn = false)
    }
}
