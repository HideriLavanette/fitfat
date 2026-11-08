package app.fitfat.ui.components

/** Сканера на iPhone пока нет ([app.fitfat.platform.Platform.supportsBarcodeScan] = false), кнопки скрыты. */
actual fun scanBarcode(onCode: (String) -> Unit) {}
