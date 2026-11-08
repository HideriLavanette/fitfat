package app.fitfat.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Цветов системы на iOS нет — палитра приложения. */
@Composable
actual fun platformDynamicScheme(dark: Boolean): ColorScheme? = null

/** Стиль строки состояния задан в Info.plist (светлые значки на тёмном); отдельной настройки не нужно. */
@Composable
actual fun SystemBarsStyle(dark: Boolean) {}
