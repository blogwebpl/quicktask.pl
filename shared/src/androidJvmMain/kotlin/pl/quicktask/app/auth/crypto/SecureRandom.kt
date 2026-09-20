package pl.quicktask.app.auth.crypto

private val secureRandom = java.security.SecureRandom()
actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)
