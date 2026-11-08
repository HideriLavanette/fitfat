package app.fitfat.ui.components

/**
 * Спрятать клавиатуру быстрее системной анимации (Android 11+ ведёт высоту сам, ≈150 мс); иначе — обычное скрытие.
 * [done] — когда клавиатура ушла.
 */
expect fun hideImeFast(done: () -> Unit = {})
