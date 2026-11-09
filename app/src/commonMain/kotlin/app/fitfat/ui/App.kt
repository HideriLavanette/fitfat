package app.fitfat.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.fitfat.data.AppSettings
import app.fitfat.data.ThemeMode
import androidx.lifecycle.viewmodel.compose.viewModel
import app.fitfat.platform.Demo
import app.fitfat.ui.components.FadeIndication
import app.fitfat.ui.components.FfAlert
import app.fitfat.ui.profile.SettingsScreen
import app.fitfat.update.UpdateViewModel
import app.fitfat.ui.components.isCupertino
import app.fitfat.ui.theme.FitfatTheme

/** Корень интерфейса для обеих платформ: тема из настроек (как в системе / светлая / тёмная, палитра, цвета системы) и [AppRoot]. */
@Composable
fun FitfatAppUi() {
    val mode by AppSettings.theme.collectAsState()
    val dynamic by AppSettings.dynamic.collectAsState()
    val palette by AppSettings.palette.collectAsState()
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    FitfatTheme(darkTheme = dark, dynamicColor = dynamic, palette = palette) {
        // iOS: нажатие затемняет, а не пускает круги ripple (Cupertino.kt)
        if (isCupertino) CompositionLocalProvider(LocalIndication provides FadeIndication) { Root() } else Root()
    }
}

/** Корень и, только в режиме скриншотов CI ([Demo]), экран «Настройки» или образец алерта поверх него. */
@Composable
private fun Root() {
    Box {
        AppRoot()
        if (Demo.screen == "settings") {
            SettingsScreen(viewModel { UpdateViewModel() }, groupByTime = false, onGroupByTime = {}, onBack = {})
        }
        if (Demo.alert) {
            FfAlert(
                onDismissRequest = {},
                title = { Text("Удалить запись?") },
                text = { Text("Вес за 8 ноября пропадёт из графика и календаря.") },
                dismissButton = { TextButton(onClick = {}) { Text("Отмена") } },
                confirmButton = { TextButton(onClick = {}) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            )
        }
    }
}
