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
import pl.quicktask.app.auth.model.OAuthTicketResponseDto
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
    fun linkedOAuthSessionRequiresPasswordOnlyForKeyUnlock() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = LinkedOAuthAuthOperations()
        val viewModels = ViewModelStore()
        try {
            val viewModel = AuthViewModel(repository)
            viewModels.put("auth", viewModel)
            viewModel.completeOAuthCallback("one-time-ticket")
            runCurrent()
            assertEquals("alice@example.com", viewModel.uiState.value.oauthEmail)
            assertTrue(viewModel.uiState.value.oauthNeedsUnlock)
            assertFalse(viewModel.uiState.value.isLoggedIn)

            viewModel.unlockOAuthKeys("existing-password")
            runCurrent()
            assertEquals("existing-password", repository.unlockedWith)
            assertEquals(0, repository.passwordLoginCalls)
            assertEquals(AuthUiState(isLoggedIn = true), viewModel.uiState.value)
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun firstOAuthLoginLinksIdentityOnlyAfterPasswordLogin() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = FirstOAuthAuthOperations()
        val viewModels = ViewModelStore()
        try {
            val viewModel = AuthViewModel(repository)
            viewModels.put("auth", viewModel)
            viewModel.completeOAuthCallback("one-time-ticket")
            runCurrent()
            assertFalse(viewModel.uiState.value.oauthNeedsUnlock)
            assertEquals(0, repository.linkCalls)

            viewModel.login("alice@example.com", "existing-password")
            runCurrent()
            assertEquals(1, repository.passwordLoginCalls)
            assertEquals(1, repository.linkCalls)
            assertEquals(AuthUiState(isLoggedIn = true), viewModel.uiState.value)
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }

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

private class LinkedOAuthAuthOperations : AuthOperations {
    override val isLoggedIn = false
    var unlockedWith: String? = null
    var passwordLoginCalls = 0
    override suspend fun redeemOAuthTicket(ticket: String): Result<OAuthTicketResponseDto> =
        Result.success(OAuthTicketResponseDto(email = "alice@example.com", linked = true, accessToken = "access"))
    override suspend fun unlockOAuthKeys(password: String): Result<Unit> {
        unlockedWith = password
        return Result.success(Unit)
    }
    override suspend fun login(email: String, password: String): Result<FinishLoginResponseDto> {
        passwordLoginCalls++
        error("OAuth key unlock must not invoke password login")
    }
    override suspend fun tryRestoreCachedKeys(): Boolean = false
    override suspend fun logout() = Unit
}

private class FirstOAuthAuthOperations : AuthOperations {
    override val isLoggedIn = false
    var passwordLoginCalls = 0
    var linkCalls = 0
    override suspend fun redeemOAuthTicket(ticket: String): Result<OAuthTicketResponseDto> =
        Result.success(OAuthTicketResponseDto(email = "alice@example.com", linkTicket = "link-ticket"))
    override suspend fun login(email: String, password: String): Result<FinishLoginResponseDto> {
        passwordLoginCalls++
        return Result.success(FinishLoginResponseDto(
            accessToken = "access", tokenType = "DPoP", accessTokenExpiresIn = 600,
            refreshTokenExpiresIn = 3600,
        ))
    }
    override suspend fun linkOAuthIdentity(ticket: String): Result<Unit> {
        linkCalls++
        assertEquals("link-ticket", ticket)
        return Result.success(Unit)
    }
    override suspend fun tryRestoreCachedKeys(): Boolean = false
    override suspend fun logout() = Unit
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
