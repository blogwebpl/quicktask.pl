package pl.quicktask.todo.auth

import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA512
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlin.random.Random

data class OpaqueRegisterStartResult(
    val registrationRequest: String,
    val clientRegistrationState: String,
)

data class OpaqueRegisterFinishResult(
    val registrationRecord: String,
    val exportKey: String,
)

data class OpaqueStartResult(
    val startLoginRequest: String,
    val clientLoginState: String,
)

data class OpaqueFinishResult(
    val finishLoginRequest: String,
    val exportKey: String,
)

interface OpaqueManager {
    suspend fun startRegistration(password: String): OpaqueRegisterStartResult
    suspend fun finishRegistration(
        password: String,
        clientRegistrationState: String,
        registrationResponse: String,
        email: String,
        serverOrigin: String,
    ): OpaqueRegisterFinishResult

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

    override suspend fun startRegistration(password: String): OpaqueRegisterStartResult {
        val blindFactor = ByteArray(32)
        Random.nextBytes(blindFactor)

        val sha512 = provider.get(SHA512)
        val passwordHash = sha512.hasher().hash(password.encodeToByteArray())

        val blindedBytes = ByteArray(32)
        for (i in 0 until 32) {
            blindedBytes[i] = (passwordHash[i].toInt() xor blindFactor[i].toInt()).toByte()
        }

        return OpaqueRegisterStartResult(
            registrationRequest = blindedBytes.toBase64Url(),
            clientRegistrationState = blindFactor.toBase64Url(),
        )
    }

    override suspend fun finishRegistration(
        password: String,
        clientRegistrationState: String,
        registrationResponse: String,
        email: String,
        serverOrigin: String,
    ): OpaqueRegisterFinishResult {
        val blindFactor = clientRegistrationState.fromBase64Url()
        val serverResponseBytes = registrationResponse.fromBase64Url()

        val hmac256 = provider.get(HMAC).keyDecoder(SHA256)
        val secretKey = hmac256.decodeFromByteArray(HMAC.Key.Format.RAW, blindFactor)
        val oprfOutput = secretKey.signatureGenerator().generateSignature(serverResponseBytes)

        val contextInfo = "$email|$serverOrigin".encodeToByteArray()
        val exportKeyBytes = ByteArray(64)
        val recordBytes = ByteArray(32)
        for (i in 0 until 64) {
            val oprfByte = if (i < oprfOutput.size) oprfOutput[i].toInt() else 0
            val ctxByte = if (i < contextInfo.size) contextInfo[i].toInt() else 0
            exportKeyBytes[i] = (oprfByte xor ctxByte).toByte()
            if (i < 32) {
                recordBytes[i] = (serverResponseBytes.getOrElse(i) { 0 }.toInt() xor oprfByte).toByte()
            }
        }

        return OpaqueRegisterFinishResult(
            registrationRecord = recordBytes.toBase64Url(),
            exportKey = exportKeyBytes.toBase64Url(),
        )
    }

    override suspend fun startLogin(password: String): OpaqueStartResult {
        val blindFactor = ByteArray(32)
        Random.nextBytes(blindFactor)

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

        val hmac256 = provider.get(HMAC).keyDecoder(SHA256)
        val secretKey = hmac256.decodeFromByteArray(HMAC.Key.Format.RAW, blindFactor)
        val oprfOutput = secretKey.signatureGenerator().generateSignature(serverResponseBytes)

        val contextInfo = "$email|$serverOrigin".encodeToByteArray()
        val exportKeyBytes = ByteArray(64)
        for (i in 0 until 64) {
            val oprfByte = if (i < oprfOutput.size) oprfOutput[i].toInt() else 0
            val ctxByte = if (i < contextInfo.size) contextInfo[i].toInt() else 0
            exportKeyBytes[i] = (oprfByte xor ctxByte).toByte()
        }

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
