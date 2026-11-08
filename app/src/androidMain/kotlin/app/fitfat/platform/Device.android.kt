package app.fitfat.platform

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
import java.io.File

@SuppressLint("HardwareIds")
actual object Device {

    actual val id: String by lazy {
        Settings.Secure.getString(AndroidApp.context.contentResolver, Settings.Secure.ANDROID_ID)?.takeIf { it.length >= 8 } ?: fallback()
    }
    actual val info: String get() = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    /** Без ANDROID_ID (редкие прошивки) — свой случайный id в настройках приложения. */
    private fun fallback(): String {
        val p = Prefs("device")
        return p.getString("id", null) ?: java.util.UUID.randomUUID().toString().also { id -> p.edit { putString("id", id) } }
    }
}

actual object CrashStore {
    private val file get() = File(AndroidApp.context.filesDir, "crash_pending.txt")

    actual fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val trace = e.stackTraceToString().take(12_000)
                file.writeText("Поток: ${thread.name}\n$trace\n\nЖурнал:\n${AppLog.dump()}")
            }
            previous?.uncaughtException(thread, e)
        }
    }

    actual fun take(): String? = runCatching {
        val f = file
        if (!f.exists()) null else f.readText().also { f.delete() }
    }.getOrNull()
}
