package pl.quicktask.app.auth.crypto

actual fun secureRandomBytes(size: Int): ByteArray {
    require(size in 0..65536)
    val bytes = js("new Uint8Array(size)")
    js("globalThis.crypto.getRandomValues(bytes)")
    return ByteArray(size) { (bytes[it] as Int).toByte() }
}
