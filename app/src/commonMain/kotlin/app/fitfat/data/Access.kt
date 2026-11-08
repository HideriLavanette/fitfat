package app.fitfat.data

import app.fitfat.BuildValues
import app.fitfat.platform.Device
import app.fitfat.platform.Prefs
import app.fitfat.time.nowMillis
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Доступ к ассистенту (DESIGN.md, «Подписка и доступ»): полный бесплатный (почта в списке на сервере), пробная неделя
 * (по кнопке, без карты, привязана к телефону — не к аккаунту) или нет доступа. Подписка через Google Play — позже.
 * Решает сервер; здесь — последний известный ответ (сохраняется, чтобы экраны не мигали без сети).
 */
object Access {
    enum class Status { Free, Trial, None }
    data class State(val status: Status, val trialUsed: Boolean, val trialEnds: Long) {
        /** Ассистент доступен сейчас. */
        val has: Boolean get() = status == Status.Free || (status == Status.Trial && trialEnds > nowMillis())
    }

    private val prefs = Prefs("access")
    private val _state = MutableStateFlow(
        State(
            Status.entries.firstOrNull { it.name == prefs.getString("status", null) } ?: Status.None,
            prefs.getBoolean("trialUsed", false), prefs.getLong("trialEnds", 0L),
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    /** Предпочтение для будущей покупки: автопродление (по умолчанию выключено — решение автора 09.11.2026). */
    private val _autoRenew = MutableStateFlow(prefs.getBoolean("autoRenew", false))
    val autoRenew: StateFlow<Boolean> = _autoRenew.asStateFlow()
    fun setAutoRenew(on: Boolean) { _autoRenew.value = on; prefs.edit { putBoolean("autoRenew", on) } }

    private fun keep(s: State) {
        _state.value = s
        prefs.edit { putString("status", s.status.name); putBoolean("trialUsed", s.trialUsed); putLong("trialEnds", s.trialEnds) }
    }

    private fun parse(text: String): State? = runCatching {
        val j = Json.parseToJsonElement(text).jsonObject
        val st = when (j.optString("status")) { "free" -> Status.Free; "trial" -> Status.Trial; else -> Status.None }
        State(st, j.optBoolean("trialUsed"), j.optLong("trialEnds"))
    }.getOrNull()

    private suspend fun call(path: String): Pair<State?, String?> = runCatching {
        if (BuildValues.ASSISTANT_URL.isBlank()) return null to "Ассистент не настроен в этой сборке."
        val token = if (GoogleAccount.get().account.value != null) GoogleAccount.get().idToken() else null
        val resp = Http.client.post("${BuildValues.ASSISTANT_URL}$path") {
            header("X-Device", Device.id)
            if (token != null) header("Authorization", "Bearer $token")
        }
        val text = resp.bodyAsText()
        val s = parse(text)
        if (s != null) keep(s)
        val err = runCatching { Json.parseToJsonElement(text).jsonObject.optString("error") }.getOrNull()?.takeIf { it.isNotBlank() }
        s to err
    }.getOrElse { null to "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз." }

    /** Обновить с сервера (при открытии вкладки «Ассистент», «Профиля», после входа). */
    suspend fun refresh() { call("/access") }

    /** Начать пробную неделю. Ошибка — текст для экрана. */
    suspend fun startTrial(): String? {
        val (s, err) = call("/trial")
        return if (s?.has == true) null else err ?: "Не получилось включить пробную неделю."
    }
}
