package app.fitfat

import androidx.compose.ui.window.ComposeUIViewController
import app.fitfat.data.AppGraph
import app.fitfat.data.Profile
import app.fitfat.platform.Demo
import app.fitfat.platform.Prefs
import app.fitfat.time.now
import app.fitfat.time.toEpochDay
import kotlinx.datetime.LocalDate
import app.fitfat.notify.ReminderScheduler
import app.fitfat.notify.Reminders
import app.fitfat.ui.FitfatAppUi
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

/** Точка входа для Swift (`iosApp/iosApp/ContentView.swift`): настройки, база, уведомления и Compose-интерфейс. */
fun MainViewController(): UIViewController {
    AppGraph.start()
    ReminderScheduler.install()
    Reminders.schedule()
    // Для скриншотов в CI (переменные через SIMCTL_CHILD_*): FITFAT_DEMO=profile[,skip][,settings][,alert] — готовый профиль вместо первой
    // настройки (и пропущенное утреннее взвешивание); FITFAT_OPEN=week|weight — открыть «Неделю» или шторку веса, как по уведомлению
    val env = NSProcessInfo.processInfo.environment
    (env["FITFAT_DEMO"] as? String)?.takeIf { it.isNotBlank() }?.let { demo ->
        val g = AppGraph.get()
        if (!g.profile.done) {
            g.profile.save(Profile())
            Prefs("settings").edit { putBoolean("notices_asked", true) }
        }
        // Календарный день, как в WeightViewModel (утреннее окно сверяет с LocalDate.now(), а не с днём дневника)
        if ("skip" in demo) Prefs("weight").edit { putLong("skipped_day", LocalDate.now().toEpochDay()) }
        if ("settings" in demo) Demo.screen = "settings"
        if ("alert" in demo) Demo.alert = true
    }
    (env["FITFAT_OPEN"] as? String)?.takeIf { it.isNotBlank() }?.let { Reminders.open.value = it }
    return ComposeUIViewController { FitfatAppUi() }
}
