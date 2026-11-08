package app.fitfat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.fitfat.platform.AndroidApp
import app.fitfat.ui.FitfatAppUi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Открыли из уведомления: шторка веса или «Неделя»
        if (savedInstanceState == null) app.fitfat.notify.Reminders.open.value = intent?.getStringExtra("open")
        setContent { FitfatAppUi() }
    }

    override fun onStart() { super.onStart(); AndroidApp.activity = this }
    override fun onStop() { if (AndroidApp.activity === this) AndroidApp.activity = null; super.onStop() }

    /** Нажали на уведомление, когда приложение уже открыто. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("open")?.let { app.fitfat.notify.Reminders.open.value = it }
    }
}
