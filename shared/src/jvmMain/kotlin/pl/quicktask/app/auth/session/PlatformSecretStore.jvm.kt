package pl.quicktask.app.auth.session

import com.sun.jna.*
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences

internal fun platformSecretStore(): PlatformSecretStore = when {
    System.getProperty("os.name").startsWith("Windows") -> WindowsSecretStore()
    System.getProperty("os.name").startsWith("Mac") -> MacSecretStore()
    System.getProperty("os.name").startsWith("Linux") -> LinuxSecretStore()
    else -> error("No secure storage on this platform")
}

@Structure.FieldOrder("cbData", "pbData")
class DataBlob : Structure {
    @JvmField var cbData: Int = 0
    @JvmField var pbData: Pointer? = null
    constructor() : super()
    constructor(bytes: ByteArray) : this() { cbData = bytes.size; pbData = Memory(bytes.size.toLong()).also { it.write(0, bytes, 0, bytes.size) }; write() }
}
interface Crypt32Api : StdCallLibrary {
    fun CryptProtectData(input: DataBlob, description: WString?, entropy: Pointer?, reserved: Pointer?, prompt: Pointer?, flags: Int, output: DataBlob): Boolean
    fun CryptUnprotectData(input: DataBlob, description: Pointer?, entropy: Pointer?, reserved: Pointer?, prompt: Pointer?, flags: Int, output: DataBlob): Boolean
}
interface Kernel32Api : StdCallLibrary { fun LocalFree(pointer: Pointer): Pointer? }
internal class WindowsSecretStore(private val prefs: Preferences = Preferences.userRoot().node("pl/quicktask/app/secrets-v2")) : PlatformSecretStore {
    private val crypt = Native.load("Crypt32", Crypt32Api::class.java)
    private val kernel = Native.load("Kernel32", Kernel32Api::class.java)
    private fun transform(bytes: ByteArray, encrypt: Boolean): ByteArray {
        val input = DataBlob(bytes); val output = DataBlob()
        try {
            val ok = if (encrypt) crypt.CryptProtectData(input, null, null, null, null, 1, output)
                else crypt.CryptUnprotectData(input, null, null, null, null, 1, output)
            check(ok) { "OS secret storage unavailable" }
            output.read()
            return output.pbData!!.getByteArray(0, output.cbData)
        } finally { (input.pbData as? Memory)?.close(); output.pbData?.let { kernel.LocalFree(it) } }
    }
    override fun read(key: String): String? = prefs.get(key, null)?.let { transform(Base64.getDecoder().decode(it), false).toString(Charsets.UTF_8) }
    override fun write(key: String, value: String) { prefs.put(key, Base64.getEncoder().encodeToString(transform(value.toByteArray(), true))); prefs.flush() }
    override fun delete(key: String) { prefs.remove(key); prefs.flush() }
}

interface SecurityApi : Library {
    fun SecKeychainFindGenericPassword(chain: Pointer?, serviceLength: Int, service: ByteArray, accountLength: Int, account: ByteArray, length: IntByReference, data: PointerByReference, item: PointerByReference): Int
    fun SecKeychainAddGenericPassword(chain: Pointer?, serviceLength: Int, service: ByteArray, accountLength: Int, account: ByteArray, length: Int, data: ByteArray, item: PointerByReference?): Int
    fun SecKeychainItemModifyAttributesAndData(item: Pointer, attrs: Pointer?, length: Int, data: ByteArray): Int
    fun SecKeychainItemDelete(item: Pointer): Int
    fun SecKeychainItemFreeContent(attrs: Pointer?, data: Pointer): Int
}
interface CoreFoundationApi : Library { fun CFRelease(pointer: Pointer) }
internal class MacSecretStore : PlatformSecretStore {
    private val api = Native.load("/System/Library/Frameworks/Security.framework/Security", SecurityApi::class.java)
    private val cf = Native.load("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", CoreFoundationApi::class.java)
    private val service = "pl.quicktask.secrets-v2".toByteArray()
    private fun <T> find(key: String, operation: (Pointer?, String?) -> T): T {
        val account = key.toByteArray(); val size = IntByReference(); val data = PointerByReference(); val item = PointerByReference()
        val status = api.SecKeychainFindGenericPassword(null, service.size, service, account.size, account, size, data, item)
        if (status == -25300) return operation(null, null)
        check(status == 0) { "Keychain unavailable" }
        try { return operation(item.value, data.value.getByteArray(0, size.value).toString(Charsets.UTF_8)) }
        finally { api.SecKeychainItemFreeContent(null, data.value); cf.CFRelease(item.value) }
    }
    override fun read(key: String): String? = find(key) { _, value -> value }
    override fun write(key: String, value: String) = find(key) { item, _ ->
        val account = key.toByteArray(); val bytes = value.toByteArray()
        check((if (item == null) api.SecKeychainAddGenericPassword(null, service.size, service, account.size, account, bytes.size, bytes, null)
            else api.SecKeychainItemModifyAttributesAndData(item, null, bytes.size, bytes)) == 0) { "Keychain write failed" }
    }
    override fun delete(key: String) = find(key) { item, _ -> if (item != null) check(api.SecKeychainItemDelete(item) == 0) }
}

internal class LinuxSecretStore : PlatformSecretStore {
    private fun command(args: List<String>, input: String? = null): Pair<Int, String> {
        val process = ProcessBuilder(listOf("secret-tool") + args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        process.outputStream.bufferedWriter().use { if (input != null) it.write(input) }
        if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Secret Service timeout") }
        return process.exitValue() to process.inputStream.bufferedReader().readText().trimEnd('\n')
    }
    override fun read(key: String): String? {
        val (code, value) = command(listOf("lookup", "application", "pl.quicktask", "key", key))
        check(code == 0 || code == 1) { "Secret Service unavailable" }
        return value.takeIf { code == 0 }
    }
    override fun write(key: String, value: String) { check(command(listOf("store", "--label=QuickTask", "application", "pl.quicktask", "key", key), value).first == 0) }
    override fun delete(key: String) { val code = command(listOf("clear", "application", "pl.quicktask", "key", key)).first; check(code == 0 || code == 1) }
}
