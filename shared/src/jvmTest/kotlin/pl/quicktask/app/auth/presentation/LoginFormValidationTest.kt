package pl.quicktask.app.auth.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pl.quicktask.app.auth.session.MemorySettings
import pl.quicktask.app.auth.session.RememberedLoginEmail

class LoginFormValidationTest {
    @Test
    fun loginAndRegistrationRequireValidEmailAndTwelveCharacterPassword() {
        val form = LoginFormState(RememberedLoginEmail(MemorySettings()))
        val uiState = AuthUiState()
        form.password = "123456789012"

        listOf("", "missing-at.example.com", "a@", "a@example", "a@.example.com",
            "a..b@example.com", "a@example..com", "a b@example.com")
            .forEach { email ->
                form.updateEmail(email)
                assertFalse(canSubmitLoginForm(uiState, form), "Accepted $email")
            }

        form.updateEmail(" user+tag@sub.example.com ")
        assertTrue(canSubmitLoginForm(uiState, form))
        form.password = "12345678901"
        assertFalse(canSubmitLoginForm(uiState, form))

        form.switchMode(true)
        assertFalse(canSubmitLoginForm(uiState, form))
        assertTrue(isValidAuthEmail(form.email))
        form.password = "123456789012"
        assertTrue(canSubmitLoginForm(uiState, form))
    }

    @Test
    fun verificationAndUnlockKeepTheirOwnRequirements() {
        val form = LoginFormState(RememberedLoginEmail(MemorySettings()))
        form.verificationCode = "123456"
        assertTrue(canSubmitLoginForm(AuthUiState(registrationId = "registration"), form))
        form.verificationCode = ""
        assertFalse(canSubmitLoginForm(AuthUiState(registrationId = "registration"), form))

        form.password = "existing"
        assertTrue(canSubmitLoginForm(AuthUiState(oauthNeedsUnlock = true), form))
    }
}
