package pl.quicktask.app.auth.crypto

@JsFun("(size) => Array.from(globalThis.crypto.getRandomValues(new Uint8Array(size))).join(',')")
private external fun randomBytesCsv(size: Int): String

actual fun secureRandomBytes(size: Int): ByteArray {
    require(size in 0..65536)
    if (size == 0) return ByteArray(0)
    return randomBytesCsv(size).split(',').map { it.toInt().toByte() }.toByteArray()
}
