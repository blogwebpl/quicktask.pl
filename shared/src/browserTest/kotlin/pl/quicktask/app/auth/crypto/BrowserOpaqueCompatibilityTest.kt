package pl.quicktask.app.auth.crypto

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class BrowserOpaqueCompatibilityTest {
    private val email = "compatibility@example.test"
    private val password = "Test-only OPAQUE compatibility 2026!"
    private val origin = "https://compatibility.example.test"
    private val setup = "ChFGVxA2MIU3apt0fEsog3TvA2I_-Dc6AZcQc5QV38iE_QWGbOFlVUhNRj7LDJE487wy54Y-vuSbv-8vyTzIMo4OHBgujjhbkAOe2Fvbt2rVabWTyfFCnr6aIY9G8YoFsA8G6m1C192BrvhwxoTbI3D5bPoIZpGO0vYdj_mVlCM"
    private val record = "WjQlQTuK9q7DwT5eboA5wFEqjZM43FzcGuHqv66NcnGgHc0Oj8LyVjoBZs16_7jQrVCdnGbZWJlDVHIDVDkcq8S3doI4RBfFrqEPojR3KLIXvCYS4YaBAk5U3LtytSfmlx1em5NDgBnExaeCDP7mvzsNZG6LsFVZvwogjJo4EckdXp-6_m20uny1k4h5BClcTkVOxmvvyKSKsivWuib--RuOCgQ5fe9aCx8i186WmetAaaHFTDEB65qD6SnoBAP-"
    private val exportKey = "HvM1WqD8Qg1LaIIVBrTfd04Ae5D6zh2bRHBDI-rz0cXeLaaHiS9W2MFHDZJGpUewsuWZAwjObvl0oOvav9Ocqg"
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
    private suspend fun server(method: String, extra: JsonObjectBuilder.() -> Unit): JsonObject =
        Json.parseToJsonElement(opaqueTestServer(method, buildJsonObject {
            put("serverSetup", setup); put("userIdentifier", email)
            put("identifiers", buildJsonObject { put("client", email); put("server", origin) })
            extra()
        }.toString())).jsonObject

    @Test fun legacyLoginKeepsExportKeyAndDecryptsExistingPrivateKey() = runTest {
        val manager = createOpaqueManager()
        val start = manager.startLogin(password)
        val response = server("startLogin") { put("registrationRecord", record); put("startLoginRequest", start.startLoginRequest) }
        val finish = manager.finishLogin(password, start.clientLoginState, response.text("loginResponse"), email, origin)
        assertEquals(exportKey, finish.exportKey)
        val accepted = server("finishLogin") { put("serverLoginState", response.text("serverLoginState")); put("finishLoginRequest", finish.finishLoginRequest) }
        assertTrue(accepted.text("sessionKey").isNotBlank())
        assertFails { manager.finishLogin(password, start.clientLoginState, response.text("loginResponse"), email, origin) }
        val pair = restoreUserKeyPair(finish.exportKey, UserKeyMaterialDto(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEVdQ1dU/0pj6u95/z46T4TUbctWSYhC6zwbvH7cSyLq1esGT3l/5QCDPtS0iS4+al33yx5tD0Nzs3/zVE3SzumQ==",
            "eBhpg+sgyDC+C9Q89/ePG32u/fAZuC7m9SCadcesLD8HtFt2veJO+UlbNwfOO+gITN1XDNkTK1zHp6Mn52alz+NUT1EILAqBcxCAgvJKMrLfe7i0KiCe4WjIlGH6eRTb8OMTnF5BTRs+y96rd2IJVTb1G9qH33kqDlkbV4NwdYJbuDg3vi5aazmyJqL7/qoazl35/+0tPqolpQ==", "2UYkxam7DUNMyJeP"))
        assertEquals("MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgpEsJB3uO+KFXRzqAv57QTBW4obAiEnW3RhVPC5XZcbShRANCAARV1DV1T/SmPq73n/PjpPhNRty1ZJiELrPBu8ftxLIurV6wZPeX/lAIM+1LSJLj5qXffLHm0PQ3Ozf/NUTdLO6Z", pair.privateKeyPkcs8!!.toBase64())
    }

    @Test fun registrationAndFullLoginAgree() = runTest {
        val manager = createOpaqueManager()
        val start = manager.startRegistration(password)
        val response = server("createRegistrationResponse") { put("registrationRequest", start.registrationRequest) }
        val registered = manager.finishRegistration(password, start.clientRegistrationState, response.text("registrationResponse"), email, origin)
        val login = manager.startLogin(password)
        val challenge = server("startLogin") { put("registrationRecord", registered.registrationRecord); put("startLoginRequest", login.startLoginRequest) }
        val finish = manager.finishLogin(password, login.clientLoginState, challenge.text("loginResponse"), email, origin)
        assertEquals(registered.exportKey, finish.exportKey)
        assertTrue(server("finishLogin") { put("serverLoginState", challenge.text("serverLoginState")); put("finishLoginRequest", finish.finishLoginRequest) }.text("sessionKey").isNotBlank())
    }

    @Test fun wrongPasswordOriginAndDamagedResponseAreRejected() = runTest {
        for ((secret, serverOrigin, damaged) in listOf(Triple("wrong password", origin, false), Triple(password, "https://wrong.example.test", false), Triple(password, origin, true))) {
            val manager = createOpaqueManager()
            val start = manager.startLogin(secret)
            val response = server("startLogin") { put("registrationRecord", record); put("startLoginRequest", start.startLoginRequest) }
            assertFails { manager.finishLogin(secret, start.clientLoginState, if (damaged) "invalid response" else response.text("loginResponse"), email, serverOrigin) }
            assertFails { manager.finishLogin(secret, start.clientLoginState, response.text("loginResponse"), email, origin) }
        }
    }
}
