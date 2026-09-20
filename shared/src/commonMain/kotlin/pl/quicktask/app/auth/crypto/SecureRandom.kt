package pl.quicktask.app.auth.crypto

/** Fails closed when the platform cryptographic random source is unavailable. */
expect fun secureRandomBytes(size: Int): ByteArray
