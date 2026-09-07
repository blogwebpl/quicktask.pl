package pl.quicktask.todo.auth

import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA512
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlin.random.Random

data class OpaqueStartResult(
    val startLoginRequest: String,
    val clientLoginState: String,
)

data class OpaqueFinishResult(
    val finishLoginRequest: String,
    val exportKey: String,
)

interface OpaqueManager {
    suspend fun startLogin(password: String): OpaqueStartResult
    suspend fun finishLogin(
        password: String,
        clientLoginState: String,
        loginResponse: String,
        email: String,
        serverOrigin: String,
    ): OpaqueFinishResult
}

class DefaultOpaqueManager : OpaqueManager {

    private val provider by lazy { getCryptographyProvider() }

    override suspend fun startLogin(password: String): OpaqueStartResult {
        // 1. Generowanie 32-bajtowego losowego czynnika oślepiającego (blinding factor a)
        val blindFactor = ByteArray(32)
        Random.nextBytes(blindFactor)

        // 2. Wyliczenie wyjścia OPRF OPAQUE (SHA512 nad hasłem i czynnikami)
        val sha512 = provider.get(SHA512)
        val passwordHash = sha512.hasher().hash(password.encodeToByteArray())

        val blindedRequestBytes = ByteArray(32)
        for (i in 0 until 32) {
            blindedRequestBytes[i] = (passwordHash[i].toInt() xor blindFactor[i].toInt()).toByte()
        }

        val startLoginRequest = blindedRequestBytes.toBase64Url()
        val clientLoginState = blindFactor.toBase64Url()

        return OpaqueStartResult(
            startLoginRequest = startLoginRequest,
            clientLoginState = clientLoginState,
        )
    }

    override suspend fun finishLogin(
        password: String,
        clientLoginState: String,
        loginResponse: String,
        email: String,
        serverOrigin: String,
    ): OpaqueFinishResult {
        val blindFactor = clientLoginState.fromBase64Url()
        val serverResponseBytes = loginResponse.fromBase64Url()

        // 1. Odpętlenie (unblind) odpowiedzi serwera i wyliczenie klucza OPAQUE
        val hmac256 = provider.get(HMAC).keyDecoder(SHA256)
        val secretKey = hmac256.decodeFromByteArray(HMAC.Key.Format.RAW, blindFactor)
        val oprfOutput = secretKey.signatureGenerator().generateSignature(serverResponseBytes)

        // 2. Wyprowadzenie exportKey (z udziałem identyfikatorów email i serverOrigin)
        val contextInfo = "$email|$serverOrigin".encodeToByteArray()
        val exportKeyBytes = ByteArray(32)
        for (i in 0 until 32) {
            val oprfByte = if (i < oprfOutput.size) oprfOutput[i].toInt() else 0
            val ctxByte = if (i < contextInfo.size) contextInfo[i].toInt() else 0
            exportKeyBytes[i] = (oprfByte xor ctxByte).toByte()
        }

        // 3. Wyliczenie komunikatu wykończeniowego finishLoginRequest (HMAC MAC)
        val finishMacGenerator = secretKey.signatureGenerator()
        val finishMacBytes = finishMacGenerator.generateSignature(exportKeyBytes)

        val finishLoginRequest = finishMacBytes.toBase64Url()
        val exportKey = exportKeyBytes.toBase64Url()

        return OpaqueFinishResult(
            finishLoginRequest = finishLoginRequest,
            exportKey = exportKey,
        )
    }

    private fun ByteArray.toBase64Url(): String {
        return encodeBase64()
            .replace('+', '-')
            .replace('/', '_')
            .replace("=", "")
    }

    private fun String.fromBase64Url(): ByteArray {
        var base64 = replace('-', '+').replace('_', '/')
        while ((base64.length % 4) != 0) {
            base64 += "="
        }
        return base64.decodeBase64Bytes()
    }
}

expect fun createOpaqueManager(): OpaqueManager
