package app.fitfat.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.fitfat.platform.AndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

actual class UpdateInstaller actual constructor() {
    private val context get() = AndroidApp.context

    actual fun download(update: AppUpdate): Flow<DownloadEvent> = flow {
        val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val target = File(dir, "fitfat-${update.versionCode}.apk")
        val part = File(dir, target.name + ".part")
        // GitHub отдаёт редирект https → https на хранилище, HttpURLConnection проходит его сам
        val conn = URL(update.apkUrl).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) throw IOException("Не удалось скачать: код ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
            var done = 0L
            var lastEmit = -1
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastEmit) { lastEmit = pct; emit(DownloadEvent.Progress(pct / 100f)) }
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("Файл скачался не полностью")
            if (!part.renameTo(target)) throw IOException("Не удалось сохранить файл")
            emit(DownloadEvent.Done(target.absolutePath))
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    actual fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    actual fun openInstallPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Android сам проверит, что подпись совпадает с установленной версией, — чужой APK поверх не встанет. */
    actual fun install(file: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", File(file))
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
