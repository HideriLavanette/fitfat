package app.fitfat.data

import app.fitfat.platform.Prefs
import app.fitfat.platform.ioDispatcher
import app.fitfat.time.nowMillis
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Копия данных в Google Диске пользователя (DESIGN.md, «Копия в Google Диске»), как у WhatsApp: один файл
 * `fitfat-backup.json` в скрытой папке приложения (`appDataFolder`) — его не видно в Диске и к остальным файлам
 * доступа нет. Своего сервера не нужно, место — из Диска пользователя. Внутри — тот же JSON, что «Экспорт» ([Backup]);
 * фото остаются на телефоне. Автокопия — не чаще раза в [AUTO_EVERY_MS], при запуске приложения, если вошли и уже дали доступ.
 */
object CloudBackup {
    private const val FILE = "fitfat-backup.json"
    private const val AUTO_EVERY_MS = 20 * 60 * 60_000L
    private val prefs = Prefs("cloud")
    private val auth = DriveAuth()
    private var token: Pair<String, Long>? = null

    private val _last = MutableStateFlow(prefs.getLong("last", 0L))
    /** Когда в последний раз сохранили копию с этого телефона (мс), 0 — ещё не сохраняли. */
    val lastSaved: StateFlow<Long> = _last.asStateFlow()

    private val _auto = MutableStateFlow(prefs.getBoolean("auto", true))
    val auto: StateFlow<Boolean> = _auto.asStateFlow()
    fun setAuto(on: Boolean) { _auto.value = on; prefs.edit { putBoolean("auto", on) } }

    /** Копия в Диске: id файла и когда сохранена. */
    data class Remote(val id: String, val savedAt: Long)

    sealed interface Result {
        data object Ok : Result
        data class Error(val message: String) : Result
    }

    private suspend fun token(interactive: Boolean): String? {
        token?.let { (t, exp) -> if (exp > nowMillis()) return t }
        val email = GoogleAccount.get().account.value?.email ?: return null
        val t = auth.accessToken(email, interactive) ?: return null
        token = t to nowMillis() + 45 * 60_000L
        return t
    }

    private fun noAccess(interactive: Boolean) =
        if (GoogleAccount.get().account.value == null) "Войдите через Google в «Профиле»."
        else if (interactive) "Нет доступа к Google Диску. Разрешите его в окне Google и попробуйте ещё раз."
        else "Нет доступа к Google Диску."

    /** Последняя копия в Диске; null — копии нет. Ошибка доступа — исключение с текстом для экрана. */
    suspend fun remote(interactive: Boolean): Remote? = withContext(ioDispatcher) {
        val t = token(interactive) ?: throw IllegalStateException(noAccess(interactive))
        val q = "name='$FILE'".encodeURLParameter()
        val resp = Http.client.get("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,modifiedTime)&orderBy=modifiedTime%20desc") {
            header("Authorization", "Bearer $t")
        }
        if (resp.status.value == 401) { token = null; throw IllegalStateException("Доступ к Диску устарел — попробуйте ещё раз.") }
        if (resp.status.value !in 200..299) throw IllegalStateException("Google Диск не ответил (${resp.status.value}).")
        val f = Json.parseToJsonElement(resp.bodyAsText()).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@withContext null
        Remote(f["id"]!!.jsonPrimitive.content, runCatching { Instant.parse(f["modifiedTime"]!!.jsonPrimitive.content).toEpochMilliseconds() }.getOrDefault(0L))
    }

    /** Сохранить копию: заменить файл в Диске или создать. */
    suspend fun save(interactive: Boolean): Result = runCatching {
        val existing = remote(interactive)
        val t = token(interactive) ?: return Result.Error(noAccess(interactive))
        val data = Backup.exportJson(AppGraph.get().database)
        val resp = withContext(ioDispatcher) {
            if (existing != null) {
                Http.client.patch("https://www.googleapis.com/upload/drive/v3/files/${existing.id}?uploadType=media") {
                    header("Authorization", "Bearer $t")
                    setBody(TextContent(data, ContentType.Application.Json))
                }
            } else {
                val b = "fitfat-${nowMillis()}"
                val body = "--$b\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                    "{\"name\":\"$FILE\",\"parents\":[\"appDataFolder\"]}\r\n" +
                    "--$b\r\nContent-Type: application/json\r\n\r\n$data\r\n--$b--"
                Http.client.post("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart") {
                    header("Authorization", "Bearer $t")
                    setBody(TextContent(body, ContentType.parse("multipart/related; boundary=$b")))
                }
            }
        }
        if (resp.status.value !in 200..299) return Result.Error("Не получилось сохранить в Google Диск (${resp.status.value}).")
        val now = nowMillis()
        prefs.edit { putLong("last", now) }
        _last.value = now
        Result.Ok
    }.getOrElse { Result.Error(it.message ?: "Нет связи с Google Диском.") }

    /** Заменить данные телефона копией из Диска. После — перезапуск приложения ([app.fitfat.platform.restartApp]). */
    suspend fun restore(interactive: Boolean): Result = runCatching {
        val r = remote(interactive) ?: return Result.Error("В Google Диске ещё нет копии.")
        val t = token(interactive) ?: return Result.Error(noAccess(interactive))
        val text = withContext(ioDispatcher) {
            val resp = Http.client.get("https://www.googleapis.com/drive/v3/files/${r.id}?alt=media") { header("Authorization", "Bearer $t") }
            if (resp.status.value !in 200..299) throw IllegalStateException("Не получилось скачать копию (${resp.status.value}).")
            resp.bodyAsText()
        }
        if (Backup.peek(text) == null) return Result.Error("Копия в Диске повреждена — данные не изменились.")
        withContext(ioDispatcher) { Backup.importJson(AppGraph.get().database, text) }
        Result.Ok
    }.getOrElse { Result.Error(it.message ?: "Нет связи с Google Диском.") }

    /** При запуске: вошли, автокопия включена, доступ уже дан и с прошлой копии прошло достаточно — сохранить тихо. */
    suspend fun autoIfDue() {
        if (!_auto.value || GoogleAccount.get().account.value == null) return
        if (nowMillis() - _last.value < AUTO_EVERY_MS) return
        save(interactive = false)
    }
}
