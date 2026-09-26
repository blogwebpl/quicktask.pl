package pl.quicktask.app.auth.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pl.quicktask.app.auth.session.MemorySettings
import pl.quicktask.app.auth.session.RememberedLoginEmail

class LoginFormStateTest {
    @Test
    fun checkboxStoresAndRemovesEmailWithoutAuthentication() {
        val preference = RememberedLoginEmail(MemorySettings())
        val firstVisit = LoginFormState(preference)

        firstVisit.updateEmail(" first@example.test ")
        assertEquals("", preference.load())
        assertFalse(firstVisit.showWelcomeBack)

        firstVisit.updateRememberEmail(true)
        assertEquals("first@example.test", preference.load())
        assertFalse(firstVisit.showWelcomeBack)

        firstVisit.updateEmail("second@example.test")
        assertEquals("second@example.test", preference.load())

        val nextVisit = LoginFormState(preference)
        assertEquals("second@example.test", nextVisit.email)
        assertTrue(nextVisit.rememberEmail)
        assertTrue(nextVisit.showWelcomeBack)

        nextVisit.updateRememberEmail(false)
        assertEquals("", preference.load())
        assertFalse(nextVisit.showWelcomeBack)
        assertEquals("second@example.test", nextVisit.email)
        assertEquals("", LoginFormState(preference).email)
    }

    @Test
    fun checkedEmptyAddressIsSavedAsSoonAsItIsEntered() {
        val preference = RememberedLoginEmail(MemorySettings())
        val form = LoginFormState(preference)
        form.updateRememberEmail(true)
        assertEquals("", preference.load())

        form.updateEmail("later@example.test")
        assertEquals("later@example.test", preference.load())

        form.updateEmail("")
        assertEquals("", preference.load())
    }

    @Test
    fun registrationDoesNotReplaceRememberedLoginAddress() {
        val preference = RememberedLoginEmail(MemorySettings())
        val form = LoginFormState(preference)
        form.updateEmail("login@example.test")
        form.updateRememberEmail(true)

        form.switchMode(true)
        form.updateEmail("registration@example.test")
        assertEquals("login@example.test", preference.load())

        form.switchMode(false)
        assertEquals("registration@example.test", preference.load())
    }
}
