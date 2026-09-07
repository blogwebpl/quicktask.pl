package pl.quicktask.todo.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
)

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(isLoggedIn = repository.isLoggedIn))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        _uiState.value = AuthUiState(isLoading = true)

        viewModelScope.launch {
            val result = repository.login(email, password)
            result.onSuccess { response ->
                println("Zalogowano pomyślnie. Access Token: ${response.accessToken}")
                _uiState.value = AuthUiState(isLoggedIn = true)
            }.onFailure { error ->
                println("Błąd logowania: ${error.message}")
                val message = error.message ?: ""
                val userFriendlyMessage = if (message.contains("401") || message.contains("Unauthorized")) {
                    "Nieprawidłowy email lub hasło"
                } else {
                    message.ifBlank { "Wystąpił błąd logowania" }
                }
                _uiState.value = AuthUiState(errorMessage = userFriendlyMessage)
            }
        }
    }

    fun logout() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            repository.logout()
            _uiState.value = AuthUiState(isLoggedIn = false)
        }
    }
}
