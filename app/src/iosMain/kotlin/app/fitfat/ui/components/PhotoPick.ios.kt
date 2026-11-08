package app.fitfat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Выбора фото на iPhone пока нет ([app.fitfat.platform.Platform.supportsPhotoPick] = false): плитка без фото скрыта. */
@Composable
actual fun rememberPhotoPicker(onPhoto: (String) -> Unit): PhotoPicker = remember { PhotoPicker(false, {}, {}) }
