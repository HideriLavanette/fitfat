package app.fitfat

import androidx.compose.ui.window.ComposeUIViewController
import app.fitfat.data.AppGraph
import app.fitfat.notify.ReminderScheduler
import app.fitfat.notify.Reminders
import app.fitfat.ui.FitfatAppUi
import platform.UIKit.UIViewController

/** Точка входа для Swift (`iosApp/iosApp/ContentView.swift`): настройки, база, уведомления и Compose-интерфейс. */
fun MainViewController(): UIViewController {
    AppGraph.start()
    ReminderScheduler.install()
    Reminders.schedule()
    return ComposeUIViewController { FitfatAppUi() }
}
