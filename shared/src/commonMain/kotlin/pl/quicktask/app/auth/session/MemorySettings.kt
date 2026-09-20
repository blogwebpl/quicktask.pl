package pl.quicktask.app.auth.session

import com.russhwolf.settings.Settings

class MemorySettings : Settings {
    private val state = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Any>>(emptyMap())
    private val map: Map<String, Any> get() = state.value
    private fun write(key: String, value: Any) {
        while (true) { val old = state.value; if (state.compareAndSet(old, old + (key to value))) return }
    }

    override val keys: Set<String> get() = map.keys
    override val size: Int get() = map.size
    override fun clear() { state.value = emptyMap() }
    override fun remove(key: String) { while (true) { val old = state.value; if (state.compareAndSet(old, old - key)) return } }
    override fun hasKey(key: String): Boolean = map.containsKey(key)
    override fun putInt(key: String, value: Int) = write(key, value)
    override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = map[key] as? Int
    override fun putLong(key: String, value: Long) = write(key, value)
    override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = map[key] as? Long
    override fun putString(key: String, value: String) = write(key, value)
    override fun getString(key: String, defaultValue: String): String = map[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = map[key] as? String
    override fun putFloat(key: String, value: Float) = write(key, value)
    override fun getFloat(key: String, defaultValue: Float): Float = map[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = map[key] as? Float
    override fun putDouble(key: String, value: Double) = write(key, value)
    override fun getDouble(key: String, defaultValue: Double): Double = map[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = map[key] as? Double
    override fun putBoolean(key: String, value: Boolean) = write(key, value)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = map[key] as? Boolean
}

