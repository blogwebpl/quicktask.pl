package pl.quicktask.app.auth.crypto

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import pl.quicktask.app.auth.session.*
import kotlin.test.*

class BrowserSessionSecurityTest {
    @Test fun storedDpopKeyIsReusedAndLossRequiresLogin() = runTest {
        withBrowserSessionLock {
            browserCall("clear")
            val manager = BrowserDPoPManager()
            val first = manager.generateDPoPProof("POST", browserOrigin() + "/auth/browser/login/finish")
            val second = BrowserDPoPManager().generateDPoPProof("POST", browserOrigin() + "/auth/browser/refresh")
            fun header(proof: String) = Json.parseToJsonElement(proof.substringBefore('.').fromBase64Url().decodeToString()).jsonObject.getValue("jwk")
            assertEquals(header(first), header(second))
            assertNotEquals(first, second)
            browserCall("clear")
            assertFails { manager.generateDPoPProof("POST", browserOrigin() + "/auth/browser/refresh") }
        }
    }

    @Test fun unlockedKeyIsStoredOnlyWithConsentAndCanBeRevoked() = runTest {
        withBrowserSessionLock {
            val pair = generateUserKeyPair()
            val email = "consent@example.test"
            rememberBrowserUnlock(false)
            suspend fun cache() = browserCall("cache", "email" to email, "publicKey" to pair.publicKeySpki.toBase64(), "privateKey" to pair.privateKeyPkcs8!!.toBase64())
            cache()
            assertFalse(browserCall("load", "email" to email).containsKey("publicKey"))
            rememberBrowserUnlock(true)
            cache()
            assertEquals(pair.publicKeySpki.toBase64(), browserCall("load", "email" to email).getValue("publicKey").jsonPrimitive.content)
            assertFalse(browserCall("load", "email" to "other@example.test").containsKey("publicKey"))
            rememberBrowserUnlock(false)
            assertFalse(browserCall("load", "email" to email).containsKey("publicKey"))
            browserCall("clearCache")
        }
    }
}
