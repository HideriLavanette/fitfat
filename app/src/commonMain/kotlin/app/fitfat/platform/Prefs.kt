package app.fitfat.platform

/**
 * Простое хранилище «ключ — значение» по имени файла. На Android — те же `SharedPreferences`
 * с теми же именами и ключами, что и раньше (данные пользователя сохраняются); на iOS — `NSUserDefaults`.
 */
expect class Prefs(name: String) {
    fun getInt(key: String, def: Int): Int
    fun getLong(key: String, def: Long): Long
    fun getFloat(key: String, def: Float): Float
    fun getBoolean(key: String, def: Boolean): Boolean
    fun getString(key: String, def: String?): String?
    fun getStringSet(key: String, def: Set<String>?): Set<String>?
    fun contains(key: String): Boolean
    /** Несколько изменений разом. */
    fun edit(block: PrefsEditor.() -> Unit)
}

interface PrefsEditor {
    fun putInt(key: String, v: Int)
    fun putLong(key: String, v: Long)
    fun putFloat(key: String, v: Float)
    fun putBoolean(key: String, v: Boolean)
    fun putString(key: String, v: String?)
    fun putStringSet(key: String, v: Set<String>?)
    fun remove(key: String)
    fun clear()
}
