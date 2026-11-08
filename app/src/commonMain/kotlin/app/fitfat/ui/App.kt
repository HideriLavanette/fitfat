package app.fitfat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.fitfat.data.AppSettings
import app.fitfat.data.ThemeMode
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
        AppRoot()
    }
}
