package app.fitfat.platform

import android.content.Context
import android.content.SharedPreferences

actual class Prefs actual constructor(name: String) {
    private val p: SharedPreferences = AndroidApp.context.getSharedPreferences(name, Context.MODE_PRIVATE)

    actual fun getInt(key: String, def: Int): Int = p.getInt(key, def)
    actual fun getLong(key: String, def: Long): Long = p.getLong(key, def)
    actual fun getFloat(key: String, def: Float): Float = p.getFloat(key, def)
    actual fun getBoolean(key: String, def: Boolean): Boolean = p.getBoolean(key, def)
    actual fun getString(key: String, def: String?): String? = p.getString(key, def)
    actual fun getStringSet(key: String, def: Set<String>?): Set<String>? = p.getStringSet(key, def)?.toSet()
    actual fun contains(key: String): Boolean = p.contains(key)
    actual fun all(): Map<String, Any?> = p.all.toMap()

    actual fun edit(block: PrefsEditor.() -> Unit) {
        val e = p.edit()
        object : PrefsEditor {
            override fun putInt(key: String, v: Int) { e.putInt(key, v) }
            override fun putLong(key: String, v: Long) { e.putLong(key, v) }
            override fun putFloat(key: String, v: Float) { e.putFloat(key, v) }
            override fun putBoolean(key: String, v: Boolean) { e.putBoolean(key, v) }
            override fun putString(key: String, v: String?) { e.putString(key, v) }
            override fun putStringSet(key: String, v: Set<String>?) { e.putStringSet(key, v) }
            override fun remove(key: String) { e.remove(key) }
            override fun clear() { e.clear() }
        }.block()
        e.apply()
    }
}
