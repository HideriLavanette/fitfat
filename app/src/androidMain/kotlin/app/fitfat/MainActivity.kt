package app.fitfat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.fitfat.ui.AppRoot
import app.fitfat.ui.theme.FitfatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Открыли из уведомления: шторка веса или «Неделя»
        if (savedInstanceState == null) app.fitfat.notify.Reminders.open.value = intent?.getStringExtra("open")
        setContent {
            // Тема из настроек: как в системе / светлая / тёмная, цвета системы (Android 12+)
            val mode by app.fitfat.data.AppSettings.theme.collectAsState()
            val dynamic by app.fitfat.data.AppSettings.dynamic.collectAsState()
            val palette by app.fitfat.data.AppSettings.palette.collectAsState()
            val dark = when (mode) {
                app.fitfat.data.ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
                app.fitfat.data.ThemeMode.Light -> false
                app.fitfat.data.ThemeMode.Dark -> true
            }
            FitfatTheme(darkTheme = dark, dynamicColor = dynamic, palette = palette) {
                AppRoot()
            }
        }
    }

    override fun onStart() { super.onStart(); app.fitfat.platform.AndroidApp.activity = this }
    override fun onStop() { if (app.fitfat.platform.AndroidApp.activity === this) app.fitfat.platform.AndroidApp.activity = null; super.onStop() }

    /** Нажали на уведомление, когда приложение уже открыто. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("open")?.let { app.fitfat.notify.Reminders.open.value = it }
    }
}
