package app.fitfat.data

import app.fitfat.BuildValues
import app.fitfat.platform.AppLog
import app.fitfat.platform.CrashStore
import app.fitfat.platform.Device
import app.fitfat.platform.Platform
import app.fitfat.platform.Prefs
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Отчёты разработчику (DESIGN.md, «Отчёты о сбоях»): падения — сами при следующем запуске (если не выключено в Настройках),
 * сообщения о проблеме — из Настроек. Уходят на наш сервер (тот же, что у ассистента) и хранятся там 90 дней.
 * Внутри — текст ошибки, журнал переходов ([AppLog]), версия приложения и модель телефона. Без дневника, имени и почты.
 */
object Reports {
    private val prefs = Prefs("settings")
    private val _crashes = MutableStateFlow(prefs.getBoolean("crash_reports", true))
    /** Отправлять отчёты о сбоях. По умолчанию включено. */
    val crashes: StateFlow<Boolean> = _crashes.asStateFlow()
    fun setCrashes(on: Boolean) { _crashes.value = on; prefs.edit { putBoolean("crash_reports", on) } }

    val available: Boolean get() = BuildValues.ASSISTANT_URL.isNotBlank()

    private val app get() = "${Platform.versionName} (${Platform.versionCode})"

    private suspend fun send(kind: String, text: String, log: String?): Boolean = runCatching {
        if (!available) return false
        val body = buildJsonObject {
            put("kind", kind); put("text", text.take(16_000)); put("app", app); put("device", Device.info)
            if (log != null) put("log", JsonPrimitive(log.take(12_000)))
        }
        Http.client.post("${BuildValues.ASSISTANT_URL}/report") {
            header("X-Device", Device.id)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }.status.isSuccess()
    }.getOrDefault(false)

    /** При запуске: было падение — отправить (или стереть, если отчёты выключены). Не отправилось — вернуть на следующий раз нельзя: файл уже прочитан, повторяем только в этом запуске. */
    suspend fun sendPendingCrash() {
        val crash = CrashStore.take() ?: return
        if (!_crashes.value) return
        repeat(3) { attempt ->
            if (send("crash", crash, null)) return
            kotlinx.coroutines.delay(10_000L * (attempt + 1))
        }
    }

    /** «Сообщить о проблеме»: текст человека и, если разрешил, журнал. */
    suspend fun feedback(text: String, withLog: Boolean): Boolean {
        AppLog.i("feedback sent")
        return send("feedback", text, if (withLog) AppLog.dump() else null)
    }
}
