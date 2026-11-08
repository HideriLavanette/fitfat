package app.fitfat.ui.components

/**
 * Сканер штрихкодов: свой экран камеры платформы. [onCode] — цифры штрихкода; отмена — ничего.
 * Есть ли сканер на платформе — [app.fitfat.platform.Platform.supportsBarcodeScan]; без него кнопку не показываем.
 */
expect fun scanBarcode(onCode: (String) -> Unit)
