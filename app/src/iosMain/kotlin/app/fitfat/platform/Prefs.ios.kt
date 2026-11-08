package app.fitfat.platform

import platform.Foundation.NSUserDefaults

/** `NSUserDefaults` в своём наборе `app.fitfat.<name>` — по набору на каждое прежнее имя `SharedPreferences`. */
actual class Prefs actual constructor(name: String) {
    private val suite = "app.fitfat.$name"
    private val d = NSUserDefaults(suiteName = suite)

    actual fun getInt(key: String, def: Int): Int = if (contains(key)) d.integerForKey(key).toInt() else def
    actual fun getLong(key: String, def: Long): Long = if (contains(key)) d.integerForKey(key) else def
    actual fun getFloat(key: String, def: Float): Float = if (contains(key)) d.floatForKey(key) else def
    actual fun getBoolean(key: String, def: Boolean): Boolean = if (contains(key)) d.boolForKey(key) else def
    actual fun getString(key: String, def: String?): String? = d.stringForKey(key) ?: def
    actual fun getStringSet(key: String, def: Set<String>?): Set<String>? =
        d.arrayForKey(key)?.filterIsInstance<String>()?.toSet() ?: def
    actual fun contains(key: String): Boolean = d.objectForKey(key) != null

    actual fun edit(block: PrefsEditor.() -> Unit) {
        object : PrefsEditor {
            override fun putInt(key: String, v: Int) { d.setInteger(v.toLong(), key) }
            override fun putLong(key: String, v: Long) { d.setInteger(v, key) }
            override fun putFloat(key: String, v: Float) { d.setFloat(v, key) }
            override fun putBoolean(key: String, v: Boolean) { d.setBool(v, key) }
            override fun putString(key: String, v: String?) { if (v == null) d.removeObjectForKey(key) else d.setObject(v, key) }
            override fun putStringSet(key: String, v: Set<String>?) { if (v == null) d.removeObjectForKey(key) else d.setObject(v.toList(), key) }
            override fun remove(key: String) { d.removeObjectForKey(key) }
            override fun clear() { d.removePersistentDomainForName(suite) }
        }.block()
        d.synchronize()
    }
}
