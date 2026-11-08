package app.fitfat.update

import app.fitfat.data.Http
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

/** Сбой проверки обновлений с понятным текстом для окна. */
class UpdateException(message: String) : Exception(message)

/** CI кладёт в релиз файл вида fitfat-0.3.0-beta1-117.apk: имя версии и versionCode. */
private val APK_NAME = Regex("""fitfat-(.+)-(\d+)\.apk""")

class UpdateRepository(private val repo: String) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Самая свежая версия из последних релизов; бета учитывается только если включена. */
    suspend fun latest(includeBeta: Boolean): AppUpdate? {
        val resp = Http.client.get("https://api.github.com/repos/$repo/releases?per_page=15") {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            timeout { connectTimeoutMillis = 10_000; requestTimeoutMillis = 15_000 }
        }
        val code = resp.status.value
        if (code == 404) throw UpdateException("Репозиторий $repo не найден или закрыт")
        if (code != 200) throw UpdateException("GitHub ответил кодом $code")
        return json.decodeFromString<List<GhRelease>>(resp.bodyAsText())
            .asSequence()
            .filter { !it.draft && (includeBeta || !it.prerelease) }
            .mapNotNull { it.toUpdate() }
            .maxByOrNull { it.versionCode }
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
