package pl.quicktask.app.auth.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

expect val oauthPlatform: String

object OAuthDeepLinks {
    private const val PREFIX = "quicktask://auth/callback#oauth_ticket="
    private val _ticket = MutableStateFlow<String?>(null)
    val ticket = _ticket.asStateFlow()
    private val _error = MutableStateFlow(false)
    val error = _error.asStateFlow()

    fun receive(url: String) {
        if (url == "quicktask://auth/callback#oauth_error=1") {
            _error.value = true
            return
        }
        if (!url.startsWith(PREFIX)) return
        val value = url.removePrefix(PREFIX)
        if (value.matches(Regex("[A-Za-z0-9_-]{40,100}"))) _ticket.value = value
    }

    fun clear() { _ticket.value = null }
    fun clearError() { _error.value = false }
}

fun receiveOAuthDeepLink(url: String) = OAuthDeepLinks.receive(url)
