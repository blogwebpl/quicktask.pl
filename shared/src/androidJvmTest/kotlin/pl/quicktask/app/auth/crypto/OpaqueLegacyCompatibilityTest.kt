package pl.quicktask.app.auth.crypto

import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import dev.whyoleg.cryptography.algorithms.AES

class OpaqueLegacyCompatibilityTest {
    private val fixture = Json.parseToJsonElement(javaClass.getResource("/opaque-legacy.json")!!.readText()).jsonObject
    private fun value(name: String) = fixture.getValue(name).jsonPrimitive.content
    private fun bridge(operation: String, request: String = "", state: String = "", record: String = ""): JsonObject {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "scripts/opaque-compatibility.cjs").exists() }
        val process = ProcessBuilder("node", File(root, "scripts/opaque-compatibility.cjs").path).start()
        process.outputStream.bufferedWriter().use { writer ->
            writer.write(buildJsonObject { put("operation", operation); put("request", request); put("state", state); put("record", record) }.toString())
        }
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); error("OPAQUE bridge timed out") }
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), "Synthetic OPAQUE bridge failed")
        return Json.parseToJsonElement(output).jsonObject
    }

    @Test fun existingAccountRetainsExportKeyAndDecryptsOldData() = runTest {
        assertTrue(bridge("verify-js").getValue("success").jsonPrimitive.boolean)
        val manager = createOpaqueManager()
        val start = manager.startLogin(value("password"))
        val response = bridge("start", start.startLoginRequest)
        val finish = manager.finishLogin(value("password"), start.clientLoginState,
            response.getValue("loginResponse").jsonPrimitive.content, value("email"), value("origin"))
        assertEquals(value("exportKey"), finish.exportKey)
        assertTrue(bridge("finish", finish.finishLoginRequest, response.getValue("serverLoginState").jsonPrimitive.content)
            .getValue("success").jsonPrimitive.boolean)
        val pair = restoreUserKeyPair(finish.exportKey, UserKeyMaterialDto(value("publicKey"), value("encryptedPrivateKey"), value("privateKeyNonce")))
        assertContentEquals(value("privateKey").fromBase64(), pair.privateKeyPkcs8)
        val key = getCryptographyProvider().get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, value("fileKey").fromBase64())
        assertEquals(value("plaintext"), decryptBuffer(key, value("ciphertext").fromBase64()).decodeToString())
        assertFails { manager.finishLogin(value("password"), start.clientLoginState, response.getValue("loginResponse").jsonPrimitive.content, value("email"), value("origin")) }
    }

    @Test fun wrongPasswordAndOriginAreRejected() = runTest {
        for ((password, origin) in listOf("wrong password" to value("origin"), value("password") to "https://wrong.example.test")) {
            val manager = createOpaqueManager()
            val start = manager.startLogin(password)
            val response = bridge("start", start.startLoginRequest)
            assertFails { manager.finishLogin(password, start.clientLoginState, response.getValue("loginResponse").jsonPrimitive.content, value("email"), origin) }
        }
    }

    @Test fun newPasswordRegistrationRetainsExistingE2eKey() = runTest {
        val manager = createOpaqueManager()
        val password = "Synthetic changed password!"
        val start = manager.startRegistration(password)
        val response = bridge("registration", start.registrationRequest)
        val registered = manager.finishRegistration(password, start.clientRegistrationState, response.getValue("registrationResponse").jsonPrimitive.content, value("email"), value("origin"))
        val wrapped = wrapPrivateKey(registered.exportKey, value("privateKey").fromBase64())
        val login = manager.startLogin(password)
        val challenge = bridge("start", login.startLoginRequest, record = registered.registrationRecord)
        val finish = manager.finishLogin(password, login.clientLoginState, challenge.getValue("loginResponse").jsonPrimitive.content, value("email"), value("origin"))
        assertEquals(registered.exportKey, finish.exportKey)
        assertTrue(bridge("finish", finish.finishLoginRequest, challenge.getValue("serverLoginState").jsonPrimitive.content).getValue("success").jsonPrimitive.boolean)
        val pair = restoreUserKeyPair(finish.exportKey, UserKeyMaterialDto(value("publicKey"), wrapped.ciphertext, wrapped.nonce))
        assertContentEquals(value("privateKey").fromBase64(), pair.privateKeyPkcs8)
    }
}
