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
import pl.quicktask.app.auth.session.browserSessions
import pl.quicktask.app.common.AppLogger
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.common.LogLevel
import pl.quicktask.app.common.NoOpAppLogger
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_user_keys_locked
import todo.shared.generated.resources.password_min_length

data class AuthUiState(
    val isLoading: Boolean = false,
    val isInitializing: Boolean = false,
    val errorMessageRes: StringResource? = null,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val registrationId: String? = null,
)

class AuthViewModel(
    private val repository: AuthOperations,
    private val logger: AppLogger = NoOpAppLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isLoading = repository.isLoggedIn,
            isInitializing = repository.isLoggedIn,
            isLoggedIn = false,
        ),
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
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

    fun register(email: String, password: String) {
        if (_uiState.value.isLoading) return
        AppLoggerManager.logFunction("AuthViewModel", "register", "email=${email.trim()}")
        if (password.length < 12) {
            AppLoggerManager.logStateChange(
                "AuthViewModel",
                "Rejestracja zatrzymana przez walidację klienta",
                "passwordLength=${password.length}, minimum=12",
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
