package app.fitfat.data

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Фото продуктов и блюд: лежат в каталоге `photos` приложения, в базе — только имя файла.
 * При добавлении уменьшаются до 1280 px по длинной стороне (JPEG 85). Выбор и съёмка — платформенные
 * ([app.fitfat.ui.components.rememberPhotoPicker]); показ сохранённых работает везде.
 */
expect object Photos {
    /** Картинка для показа; null — файла нет. Читать не в главном потоке. */
    suspend fun load(name: String): ImageBitmap?
    fun cached(name: String): ImageBitmap?
    /** Удаляет файлы, на которые не ссылается ни один продукт. */
    fun cleanup(used: Set<String>)
}
