package app.fitfat

import androidx.compose.ui.window.ComposeUIViewController
import app.fitfat.data.AppGraph
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
    // Для скриншотов в CI: SIMCTL_CHILD_FITFAT_OPEN=week|weight открывает «Неделю» или шторку веса, как нажатие на уведомление
    (NSProcessInfo.processInfo.environment["FITFAT_OPEN"] as? String)?.takeIf { it.isNotBlank() }?.let { Reminders.open.value = it }
    return ComposeUIViewController { FitfatAppUi() }
}
