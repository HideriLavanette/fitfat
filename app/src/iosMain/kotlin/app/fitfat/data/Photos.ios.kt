package app.fitfat.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.fitfat.platform.documentDirectory
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/**
 * Показ сохранённых фото из `Documents/photos`. Съёмки и выбора из галереи на iPhone пока нет
 * ([app.fitfat.platform.Platform.supportsPhotoPick]), поэтому новых файлов здесь не появляется.
 */
actual object Photos {
    private val cache = HashMap<String, ImageBitmap>()
    private fun dir(): String = documentDirectory() + "/photos"

    /** На iPhone съёмки пока нет — и оценки по фото тоже. */
    actual suspend fun forAssistant(name: String): ByteArray? = null

    actual suspend fun load(name: String): ImageBitmap? {
        cache[name]?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                val data = NSData.dataWithContentsOfFile("${dir()}/$name") ?: return@runCatching null
                val n = data.length.toInt()
                if (n == 0) return@runCatching null
                val bytes = ByteArray(n)
                bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        }?.also { if (cache.size > 24) cache.clear(); cache[name] = it }
    }

    actual fun cached(name: String): ImageBitmap? = cache[name]

    actual fun cleanup(used: Set<String>) {
        val fm = NSFileManager.defaultManager
        val d = dir()
        val names = fm.contentsOfDirectoryAtPath(d, null)?.filterIsInstance<String>() ?: return
        names.filter { it !in used }.forEach { fm.removeItemAtPath("$d/$it", null) }
    }
}
