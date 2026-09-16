package pl.quicktask.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_access_token_expired
import todo.shared.generated.resources.error_authentication_failed
import todo.shared.generated.resources.error_invalid_dpop_proof
import todo.shared.generated.resources.error_invalid_login_request
import todo.shared.generated.resources.error_login_failed
import todo.shared.generated.resources.error_too_many_requests

import todo.shared.generated.resources.error_user_keys_locked

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessageRes: StringResource? = null,
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
)

class AuthViewModel(
    private val repository: AuthRepository = sharedAuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isLoading = repository.isLoggedIn,
            isLoggedIn = false,
        ),
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        if (repository.isLoggedIn) {
            viewModelScope.launch {
                val restored = repository.tryRestoreCachedKeys()
                if (restored) {
                    _uiState.value = AuthUiState(isLoggedIn = true)
                } else {
                    repository.logout()
                    _uiState.value = AuthUiState(
                        isLoggedIn = false,
                        errorMessageRes = Res.string.error_user_keys_locked,
                    )
                }
            }
        }
    }

    fun login(email: String, password: String) {
        if (_uiState.value.isLoading) return
        _uiState.value = AuthUiState(isLoading = true)

        viewModelScope.launch {
            val result = repository.login(email, password)
            result.onSuccess {
                _uiState.value = AuthUiState(isLoggedIn = true)
            }.onFailure { error ->
                println("Błąd logowania: ${error.message}")
                val (errorRes, customMsg) = mapErrorToState(error)
                _uiState.value = AuthUiState(
                    errorMessageRes = errorRes,
                    errorMessage = customMsg,
                )
            }
        }
    }

    private fun mapErrorToState(error: Throwable): Pair<StringResource?, String?> {
        val apiException = error as? ApiException
        val code = apiException?.code

        when (code) {
            "AUTHENTICATION_FAILED" -> return Pair(Res.string.error_authentication_failed, null)
            "INVALID_DPOP_PROOF" -> return Pair(Res.string.error_invalid_dpop_proof, null)
            "INVALID_LOGIN_REQUEST" -> return Pair(Res.string.error_invalid_login_request, null)
            "ACCESS_TOKEN_EXPIRED" -> return Pair(Res.string.error_access_token_expired, null)
        }

        val statusCode = apiException?.statusCode ?: run {
            val msg = error.message ?: ""
            when {
                msg.contains("401") || msg.contains("Unauthorized") -> 401
                msg.contains("400") -> 400
                msg.contains("429") || msg.contains("Too many requests") -> 429
                else -> null
            }
        }

        return when (statusCode) {
            401 -> Pair(Res.string.error_authentication_failed, null)
            400 -> Pair(Res.string.error_invalid_login_request, null)
            429 -> Pair(Res.string.error_too_many_requests, null)
            else -> {
                val detail = error.message?.takeIf { it.isNotBlank() }
                if (detail != null) {
                    Pair(null, "Wystąpił błąd logowania: $detail")
                } else {
                    Pair(Res.string.error_login_failed, null)
                }
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
