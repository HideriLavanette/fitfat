package app.fitfat.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Фото продуктов и блюд: лежат в каталоге `photos` приложения, в базе — только имя файла.
 * При добавлении уменьшаются до 1080 px по длинной стороне (JPEG 80, ~100–200 КБ; 09.11.2026 — было 1280 / 85). Выбор и съёмка — платформенные
 * ([app.fitfat.ui.components.rememberPhotoPicker]); показ сохранённых работает везде.
 */
expect object Photos {
    /** Картинка для показа; null — файла нет. Читать не в главном потоке. */
    suspend fun load(name: String): ImageBitmap?
    fun cached(name: String): ImageBitmap?
    /** Фото для оценки ассистентом: 1024 px, JPEG 75 (~100–200 КБ). Само фото остаётся на телефоне; null — нет файла. */
    suspend fun forAssistant(name: String): ByteArray?
    /** Удаляет файлы, на которые не ссылается ни один продукт. */
    fun cleanup(used: Set<String>)
}
