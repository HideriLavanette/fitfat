package app.fitfat.data

import app.fitfat.BuildValues
import app.fitfat.platform.Prefs
import app.fitfat.time.nowMillis
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 *
 * Чтобы системное окно «Вход в аккаунт» не всплывало при каждом запуске (0.6.7): по токену Google сервер ассистента
 * один раз выдаёт свой пропуск на 90 дней ([bearer]) — дальше в запросах идёт он, и Google не беспокоим.
 */
class GoogleAccount private constructor() {
    private val prefs = Prefs("account")
    private val signer = GoogleSignIn()
    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account?> = _account.asStateFlow()

    private var token: String? = null
    private var tokenExp = 0L
    /** Один тихий запрос к Google за раз: два экрана, спросившие токен одновременно, не покажут окно дважды. */
    private val googleLock = Mutex()
    private val sessionLock = Mutex()
    /** Сервер ещё не умеет выдавать пропуск (старая версия) — не спрашивать чаще раза в 10 минут. */
    private var sessionAskedAt = 0L

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
    suspend fun idToken(): String? = googleLock.withLock {
        if (_account.value == null) return null
        token?.let { if (tokenExp - nowMillis() > 5 * 60_000L) return it }
        val clientId = BuildValues.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() } ?: return null
        signer.silent(clientId)?.also(::keep)?.also(::remember)?.idToken
    }

    /**
     * Чем подтверждать себя серверу ассистента (заголовок Authorization): пропуск сервера, а пока его нет — токен Google
     * (по нему же пропуск и берём). null — не вошли или Google не ответил.
     */
    suspend fun bearer(): String? {
        if (_account.value == null) return null
        prefs.getString(KEY_SESSION, null)?.let { return it }
        val google = idToken() ?: return null
        return sessionLock.withLock { prefs.getString(KEY_SESSION, null) ?: openSession(google) ?: google }
    }

    private suspend fun openSession(google: String): String? = runCatching {
        if (BuildValues.ASSISTANT_URL.isBlank() || nowMillis() - sessionAskedAt < 10 * 60_000L) return null
        sessionAskedAt = nowMillis()
        val resp = Http.client.post("${BuildValues.ASSISTANT_URL}/session") { header("Authorization", "Bearer $google") }
        if (resp.status.value != 200) return null
        Json.parseToJsonElement(resp.bodyAsText()).jsonObject.optString("session").takeIf { isSession(it) }
            ?.also { s -> prefs.edit { putString(KEY_SESSION, s) }; sessionAskedAt = 0L }
    }.getOrNull()

    /** Сервер не принял пропуск [used] (истёк или отозван) — забыть его: следующий [bearer] возьмёт новый. */
    fun dropSession(used: String) {
        if (used == prefs.getString(KEY_SESSION, null)) prefs.edit { remove(KEY_SESSION) }
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
        // Один раз на вход: у аккаунта может не быть аватарки вовсе — не спрашивать её при каждом открытии «Профиля»
        if (acc.photo != null || prefs.getBoolean(KEY_PHOTO_ASKED, false)) return
        val clientId = BuildValues.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() } ?: return
        prefs.edit { putBoolean(KEY_PHOTO_ASKED, true) }
        googleLock.withLock { signer.silent(clientId)?.also(::keep)?.also(::remember) }
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
                // Пропуск сервера выдан прежнему аккаунту — новому не годится
                prefs.edit { putString("email", acc.email); putString("name", acc.name); putString("photo", acc.photo); remove(KEY_SESSION); remove(KEY_PHOTO_ASKED) }
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
        // Пропуск сервера отзываем — если сети нет, он сам истечёт
        prefs.getString(KEY_SESSION, null)?.let { s ->
            if (BuildValues.ASSISTANT_URL.isNotBlank()) runCatching { Http.client.post("${BuildValues.ASSISTANT_URL}/logout") { header("Authorization", "Bearer $s") } }
        }
        prefs.edit { clear() }
        _account.value = null
        token = null; tokenExp = 0
        runCatching { signer.clear() }
    }

    companion object {
        private const val KEY_SESSION = "session"
        private const val KEY_PHOTO_ASKED = "photoAsked"
        /** Пропуск сервера ассистента (а не токен Google). */
        fun isSession(t: String) = t.startsWith("ffs_")
        private val instance: GoogleAccount by lazy { GoogleAccount() }
        fun get(): GoogleAccount = instance
    }
}
