package app.fitfat.data

import app.fitfat.platform.AndroidApp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Фото продуктов и блюд: лежат в `files/photos` приложения, в базе — только имя файла.
 * При добавлении уменьшаются до 1280 px по длинной стороне (JPEG 85), поворот по EXIF учитывается.
 */
actual object Photos {
    private const val MAX_SIDE = 1280
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }

    /** Куда системная камера кладёт снимок (через FileProvider, без разрешения камеры). */
    fun cameraUri(ctx: Context): Uri {
        val f = File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "shot.jpg")
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".updates", f)
    }

    /** Копирует картинку из [uri] к себе, уменьшив. Возвращает имя файла или null, если не прочиталась. */
    suspend fun import(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return@runCatching null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return@runCatching null
            val rot = cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val k = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
            if (rot != 0f || k < 1f) {
                val m = Matrix().apply { if (k < 1f) postScale(k, k); postRotate(rot) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val name = "p_${System.currentTimeMillis()}.jpg"
            File(dir(ctx), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            cache.put(name, bmp.asImageBitmap())
            name
        }.getOrNull()
    }

    /** Картинка для показа; null — файла нет. Читать не в главном потоке. */
    actual suspend fun load(name: String): ImageBitmap? {
        val ctx = AndroidApp.context
        cache.get(name)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(File(dir(ctx), name).path)?.asImageBitmap() }.getOrNull()
                ?.also { cache.put(name, it) }
        }
    }

    actual fun cached(name: String): ImageBitmap? = cache.get(name)

    /** Удаляет файлы, на которые не ссылается ни один продукт. */
    actual fun cleanup(used: Set<String>) {
        dir(AndroidApp.context).listFiles()?.forEach { if (it.name !in used) it.delete() }
    }
}
