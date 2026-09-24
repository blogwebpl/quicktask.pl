package pl.quicktask.app.auth.presentation

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pl.quicktask.app.auth.data.AuthOperations
import pl.quicktask.app.auth.model.FinishLoginResponseDto
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_user_keys_locked
import todo.shared.generated.resources.password_min_length
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @Test
    fun savedSessionWithRestoredKeysOpensAppWithoutLoggingInAgain() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = SavedSessionAuthOperations()
        val viewModels = ViewModelStore()
        try {
            val viewModel = AuthViewModel(repository)
            viewModels.put("auth", viewModel)

            runCurrent()
            assertEquals(AuthUiState(isLoading = true, isInitializing = true), viewModel.uiState.value)
            assertEquals(1, repository.restoreCalls)
            assertEquals(0, repository.logoutCalls)

            repository.restoredKeys.complete(true)
            runCurrent()

            assertEquals(AuthUiState(isLoggedIn = true), viewModel.uiState.value)
            assertTrue(repository.isLoggedIn)
            assertEquals(0, repository.logoutCalls)
            assertEquals(0, repository.loginCalls)
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun savedSessionWithoutRestorableKeysLogsOutAndRequestsUnlock() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = SavedSessionAuthOperations()
        val viewModels = ViewModelStore()
        try {
            val viewModel = AuthViewModel(repository)
            viewModels.put("auth", viewModel)

            runCurrent()
            assertEquals(AuthUiState(isLoading = true, isInitializing = true), viewModel.uiState.value)
            assertEquals(1, repository.restoreCalls)
            assertEquals(0, repository.logoutCalls)

            repository.restoredKeys.complete(false)
            runCurrent()

            assertEquals(
                AuthUiState(errorMessageRes = Res.string.error_user_keys_locked),
                viewModel.uiState.value,
            )
            assertEquals(1, repository.logoutCalls)
            assertFalse(repository.isLoggedIn)
            assertEquals(0, repository.loginCalls)
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun validRegistrationShowsServerResult() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = RegistrationAuthOperations()
        val viewModels = ViewModelStore()
        try {
            val viewModel = AuthViewModel(repository)
            viewModels.put("auth", viewModel)

            viewModel.register("new@example.com", "long-password")
            assertTrue(viewModel.uiState.value.isLoading)

            runCurrent()

            assertEquals(1, repository.registerCalls)
            assertEquals("new@example.com", repository.registeredEmail)
            assertEquals(AuthUiState(registrationId = "registration-id"), viewModel.uiState.value)
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun shortRegistrationPasswordDoesNotCallServer() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = RegistrationAuthOperations()
        val viewModels = ViewModelStore()
        try {
            val viewModel = AuthViewModel(repository)
            viewModels.put("auth", viewModel)

            viewModel.register("new@example.com", "short")
            runCurrent()

            assertEquals(0, repository.registerCalls)
            assertEquals(
                AuthUiState(errorMessageRes = Res.string.password_min_length),
                viewModel.uiState.value,
            )
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }
}

private class RegistrationAuthOperations : AuthOperations {
    override val isLoggedIn = false
    var registerCalls = 0
        private set
    var registeredEmail: String? = null
        private set

    override suspend fun register(email: String, password: String): Result<String> {
        registerCalls++
        registeredEmail = email
        return Result.success("registration-id")
    }

    override suspend fun tryRestoreCachedKeys(): Boolean = false

    override suspend fun logout() = Unit

    override suspend fun login(email: String, password: String): Result<FinishLoginResponseDto> =
        error("Login is not expected in a registration test")
}

private class SavedSessionAuthOperations : AuthOperations {
    override var isLoggedIn = true
        private set
    val restoredKeys = CompletableDeferred<Boolean>()
    var restoreCalls = 0
        private set
    var logoutCalls = 0
        private set
    var loginCalls = 0
        private set

    override suspend fun tryRestoreCachedKeys(): Boolean {
        restoreCalls++
        return restoredKeys.await()
    }

    override suspend fun logout() {
        logoutCalls++
        isLoggedIn = false
    }

    override suspend fun login(email: String, password: String): Result<FinishLoginResponseDto> {
        loginCalls++
        error("Restoring a saved session must not require password login")
    }
}
