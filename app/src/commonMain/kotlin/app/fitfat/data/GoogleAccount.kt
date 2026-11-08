package app.fitfat.data

import app.fitfat.BuildValues
import app.fitfat.platform.Prefs
import app.fitfat.time.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64

/** Аккаунт, в который вошли: почта, имя и адрес аватарки Google (могут не прийти). */
data class Account(val email: String, val name: String?, val photo: String? = null)

/** Что вернула платформа при входе: токен входа (JWT, живёт час), почта, имя, аватарка. */
data class GoogleCredential(val idToken: String, val email: String, val name: String?, val photo: String? = null)

/** Итог окна входа на платформе. */
sealed interface SignInOutcome {
    data class Ok(val credential: GoogleCredential) : SignInOutcome
    data object Cancelled : SignInOutcome
    data class Error(val message: String) : SignInOutcome
}

/** Вход Google на платформе: Android — Credential Manager, iOS — пока нет ([app.fitfat.platform.Platform.supportsGoogleSignIn]). */
expect class GoogleSignIn() {
    /** Окно выбора аккаунта. [clientId] — веб-клиент OAuth. */
    suspend fun signIn(clientId: String): SignInOutcome
    /** Тихо обновить токен тем же аккаунтом, без окна; null — не вышло. */
    suspend fun silent(clientId: String): GoogleCredential?
    /** Попросить систему больше не подставлять аккаунт автоматически. */
    suspend fun clear()
}

/**
 * Вход через Google. Приложение получает имя, почту и аватарку — ни пароля, ни доступа к Gmail или платежам.
 * На диске — почта, имя и адрес аватарки для карточки профиля. Токен входа (живёт час) держим в памяти: им ассистент
 * подтверждает серверу, кто спрашивает (DESIGN.md, «Ассистент»).
 */
class GoogleAccount private constructor() {
    private val prefs = Prefs("account")
    private val signer = GoogleSignIn()
    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account?> = _account.asStateFlow()

    private var token: String? = null
    private var tokenExp = 0L

    private fun keep(g: GoogleCredential) {
        token = g.idToken
        tokenExp = runCatching {
            val payload = g.idToken.split('.')[1].let { it + "=".repeat((4 - it.length % 4) % 4) }
            Json.parseToJsonElement(Base64.UrlSafe.decode(payload).decodeToString()).jsonObject["exp"]!!.jsonPrimitive.long * 1000
        }.getOrDefault(nowMillis() + 50 * 60_000L)
    }

    /**
     * Свежий токен входа для сервера ассистента или null (не вошли, нет сети, Google отказал).
     * Просроченный обновляется без окна выбора — тем же аккаунтом, что уже подключён.
     */
    suspend fun idToken(): String? {
        if (_account.value == null) return null
        token?.let { if (tokenExp - nowMillis() > 5 * 60_000L) return it }
        val clientId = BuildValues.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() } ?: return null
        return signer.silent(clientId)?.also(::keep)?.also(::remember)?.idToken
    }

    /** Аватарка или имя поменялись (или вошли до того, как приложение их запоминало) — обновить карточку. */
    private fun remember(c: GoogleCredential) {
        val cur = _account.value ?: return
        val next = cur.copy(name = c.name?.takeIf { it.isNotBlank() } ?: cur.name, photo = c.photo ?: cur.photo)
        if (next == cur) return
        prefs.edit { putString("name", next.name); putString("photo", next.photo) }
        _account.value = next
    }

    /** Вошли раньше, а аватарки ещё нет — тихо спросить её у Google (без окна). */
    suspend fun refreshProfile() {
        val acc = _account.value ?: return
        if (acc.photo != null) return
        val clientId = BuildValues.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() } ?: return
        signer.silent(clientId)?.also(::keep)?.also(::remember)
    }

    private fun load(): Account? = prefs.getString("email", null)?.let { Account(it, prefs.getString("name", null), prefs.getString("photo", null)) }

    /** Итог входа: аккаунт, отмена пользователем или текст ошибки для экрана. */
    sealed interface Result {
        data class Ok(val account: Account) : Result
        data object Cancelled : Result
        data class Error(val message: String) : Result
    }

    /** Открывает выбор аккаунта Google. */
    suspend fun signIn(): Result {
        val clientId = BuildValues.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) return Result.Error("Вход ещё не настроен в этой сборке — нет идентификатора клиента Google.")
        return when (val r = signer.signIn(clientId)) {
            is SignInOutcome.Ok -> {
                val acc = Account(r.credential.email, r.credential.name?.takeIf { it.isNotBlank() }, r.credential.photo)
                prefs.edit { putString("email", acc.email); putString("name", acc.name); putString("photo", acc.photo) }
                keep(r.credential)
                _account.value = acc
                Result.Ok(acc)
            }
            SignInOutcome.Cancelled -> Result.Cancelled
            is SignInOutcome.Error -> Result.Error(r.message)
        }
    }

    /** Выйти: забываем аккаунт и просим систему не подставлять его автоматически. */
    suspend fun signOut() {
        prefs.edit { clear() }
        _account.value = null
        token = null; tokenExp = 0
        runCatching { signer.clear() }
    }

    companion object {
        private val instance: GoogleAccount by lazy { GoogleAccount() }
        fun get(): GoogleAccount = instance
    }
}
