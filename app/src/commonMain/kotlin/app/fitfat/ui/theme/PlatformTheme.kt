package app.fitfat.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Цвета системы (Material You, Android 12+); null — платформа не умеет, берём палитру приложения. */
@Composable
expect fun platformDynamicScheme(dark: Boolean): ColorScheme?

/** Значки строки состояния и навигации — по теме приложения, а не телефона (иначе тёмные на тёмном). */
@Composable
expect fun SystemBarsStyle(dark: Boolean)
