package pl.quicktask.app.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.auth.model.ApiException
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.items.presentation.itemErrorResource
import pl.quicktask.app.settings.data.SettingsOperations
import pl.quicktask.app.settings.model.UserSettings
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_fetch_settings
import todo.shared.generated.resources.error_save_settings
import todo.shared.generated.resources.timezone_max_length_error

data class SettingsUiState(
    val timeZone: String = "UTC",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessageRes: StringResource? = null,
    val errorMessage: String? = null,
)

class SettingsViewModel(
    private val repository: SettingsOperations,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        AppLoggerManager.logFunction("SettingsViewModel", "loadSettings")
        AppLoggerManager.logRefresh("SettingsViewModel", "Pobieranie ustawień użytkownika")
        _uiState.update { it.copy(isLoading = true, errorMessageRes = null, errorMessage = null, isSuccess = false) }
        viewModelScope.launch {
            try {
                repository.getUserSettings()
                    .onSuccess { settings ->
                        AppLoggerManager.logStateChange("SettingsViewModel", "Pobrano ustawienia", "timeZone=${settings.timeZone}")
                        _uiState.update {
                            it.copy(
                                timeZone = settings.timeZone,
                                isLoading = false,
                            )
                        }
                    }
                    .onFailure { error ->
                        AppLoggerManager.logStateChange("SettingsViewModel", "Błąd pobierania ustawień", error.message)
                        handleError(error, Res.string.error_fetch_settings)
                        _uiState.update { it.copy(isLoading = false) }
                    }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun updateTimeZoneInput(timeZone: String) {
        AppLoggerManager.logFunction("SettingsViewModel", "updateTimeZoneInput", "timeZone=$timeZone")
        _uiState.update {
            it.copy(
                timeZone = timeZone,
                isSuccess = false,
                errorMessageRes = null,
                errorMessage = null,
            )
        }
    }

    fun saveSettings(timeZone: String) {
        val trimmed = timeZone.trim()
        AppLoggerManager.logFunction("SettingsViewModel", "saveSettings", "timeZone=$trimmed")
        if (trimmed.length > 100) {
            _uiState.update {
                it.copy(errorMessageRes = Res.string.timezone_max_length_error, errorMessage = null)
            }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessageRes = null, errorMessage = null, isSuccess = false) }
        viewModelScope.launch {
            try {
                repository.updateUserSettings(UserSettings(timeZone = trimmed))
                    .onSuccess { updated ->
                        AppLoggerManager.logStateChange("SettingsViewModel", "Zapisano ustawienia", "timeZone=${updated.timeZone}")
                        _uiState.update {
                            it.copy(
                                timeZone = updated.timeZone,
                                isSaving = false,
                                isSuccess = true,
                            )
                        }
                    }
                    .onFailure { error ->
                        AppLoggerManager.logStateChange("SettingsViewModel", "Błąd zapisywania ustawień", error.message)
                        handleError(error, Res.string.error_save_settings)
                        _uiState.update { it.copy(isSaving = false) }
                    }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private fun handleError(error: Throwable, fallbackRes: StringResource) {
        if (error is CancellationException) throw error
        if ((error is ApiException) && (error.statusCode == 400) && !error.message.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = error.message, errorMessageRes = null) }
        } else {
            _uiState.update { it.copy(errorMessageRes = itemErrorResource(error, fallbackRes), errorMessage = null) }
        }
    }
}
