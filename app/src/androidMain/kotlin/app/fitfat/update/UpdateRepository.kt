package app.fitfat.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

@Serializable
internal data class GhAsset(
    val name: String,
    @SerialName("browser_download_url") val url: String,
    val size: Long = 0,
)

@Serializable
internal data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val prerelease: Boolean,
)

/** CI кладёт в релиз файл вида fitfat-0.3.0-beta1-117.apk: имя версии и versionCode. */
private val APK_NAME = Regex("""fitfat-(.+)-(\d+)\.apk""")

class UpdateRepository(private val repo: String) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Самая свежая версия из последних релизов; бета учитывается только если включена. */
    suspend fun latest(includeBeta: Boolean): AppUpdate? = withContext(Dispatchers.IO) {
        val conn = URL("https://api.github.com/repos/$repo/releases?per_page=15").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            val code = conn.responseCode
            if (code == 404) throw IOException("Репозиторий $repo не найден или закрыт")
            if (code != 200) throw IOException("GitHub ответил кодом $code")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<List<GhRelease>>(text)
                .asSequence()
                .filter { !it.draft && (includeBeta || !it.prerelease) }
                .mapNotNull { it.toUpdate() }
                .maxByOrNull { it.versionCode }
        } finally {
            conn.disconnect()
        }
    }

    private fun GhRelease.toUpdate(): AppUpdate? {
        val asset = assets.firstOrNull { APK_NAME.matches(it.name) } ?: return null
        val m = APK_NAME.matchEntire(asset.name) ?: return null
        return AppUpdate(
            versionName = m.groupValues[1],
            versionCode = m.groupValues[2].toInt(),
            notes = body.orEmpty().trim(),
            apkUrl = asset.url,
            sizeBytes = asset.size,
            prerelease = prerelease,
        )
    }
}
