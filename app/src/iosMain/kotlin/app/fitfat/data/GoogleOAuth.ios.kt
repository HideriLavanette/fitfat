package app.fitfat.data

import app.fitfat.BuildValues
import app.fitfat.platform.Keychain
import app.fitfat.platform.Prefs
import app.fitfat.platform.keyWindow
import app.fitfat.time.nowMillis
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.http.parameters
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSURL
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64

/**
 * Вход Google на iPhone без SDK: OAuth 2.0 для установленных приложений (код авторизации + PKCE) в системном окне
 * браузера `ASWebAuthenticationSession`. Клиент — OAuth-клиент типа «iOS» из Google Cloud ([BuildValues.GOOGLE_IOS_CLIENT_ID]),
 * секрета у него нет. Токен обновления хранится в связке ключей ([Keychain]); по нему тихо берутся свежие токен входа
 * и токен доступа — окно больше не показывается, пока человек не выйдет.
 */
internal object GoogleOAuth {
    const val SIGN_IN_SCOPES = "openid email profile"

    /** Ответ Google: токен входа (JWT), токен доступа и выданные права. */
    class Tokens(val idToken: String?, val accessToken: String?, val scopes: Set<String>, val expiresAt: Long)

    sealed interface Outcome {
        class Ok(val tokens: Tokens) : Outcome
        data object Cancelled : Outcome
        class Error(val message: String) : Outcome
    }

    val clientId: String get() = BuildValues.GOOGLE_IOS_CLIENT_ID
    val configured: Boolean get() = clientId.isNotBlank()

    /** Для клиентов iOS Google принимает только такой адрес возврата: идентификатор клиента задом наперёд. */
    private val scheme: String get() = "com.googleusercontent.apps." + clientId.removeSuffix(".apps.googleusercontent.com")
    private val redirect: String get() = "$scheme:/oauth2redirect"

    private const val KEY_REFRESH = "refresh_token"
    /** Запасное место, если связка ключей недоступна (сборка без подписи): настройки приложения. */
    private val fallback by lazy { Prefs("google_oauth") }
    private val lock = Mutex()
    private var cached: Tokens? = null

    private var refreshToken: String?
        get() = Keychain.get(KEY_REFRESH) ?: fallback.getString(KEY_REFRESH, null)
        set(v) {
            val inKeychain = Keychain.put(KEY_REFRESH, v)
            fallback.edit { if (v == null || inKeychain) remove(KEY_REFRESH) else putString(KEY_REFRESH, v) }
        }

    /** Окно входа Google. [scopes] — права через пробел; [loginHint] — почта, чтобы сразу выбрать тот же аккаунт. */
    suspend fun authorize(scopes: String, loginHint: String? = null): Outcome {
        if (!configured) return Outcome.Error("Вход ещё не настроен в этой сборке — нет идентификатора клиента Google для iOS.")
        val verifier = b64url(randomBytes(32))
        val state = b64url(randomBytes(16))
        val url = buildString {
            append("https://accounts.google.com/o/oauth2/v2/auth?response_type=code")
            append("&client_id=").append(clientId.encodeURLParameter())
            append("&redirect_uri=").append(redirect.encodeURLParameter())
            append("&scope=").append(scopes.encodeURLParameter())
            append("&code_challenge=").append(b64url(sha256(verifier.encodeToByteArray())))
            append("&code_challenge_method=S256&include_granted_scopes=true")
            append("&state=").append(state)
            if (!loginHint.isNullOrBlank()) append("&login_hint=").append(loginHint.encodeURLParameter())
        }
        val callback = when (val r = WebAuth.open(url, scheme)) {
            is WebAuth.Result.Ok -> r.callback
            WebAuth.Result.Cancelled -> return Outcome.Cancelled
            is WebAuth.Result.Error -> return Outcome.Error("Не получилось открыть окно входа: ${r.message}")
        }
        val query = callback.substringAfter('?', "").substringBefore('#').split('&').mapNotNull {
            val i = it.indexOf('='); if (i <= 0) null else it.substring(0, i) to it.substring(i + 1).decodeURLQueryComponent()
        }.toMap()
        query["error"]?.let { return if (it == "access_denied") Outcome.Cancelled else Outcome.Error("Google отказал во входе: $it") }
        val code = query["code"]
        if (code == null || query["state"] != state) return Outcome.Error("Google вернул неожиданный ответ. Попробуйте ещё раз.")
        return runCatching {
            val j = token(parameters {
                append("grant_type", "authorization_code"); append("code", code); append("code_verifier", verifier)
                append("client_id", clientId); append("redirect_uri", redirect)
            }) ?: return Outcome.Error("Google не выдал токен. Попробуйте ещё раз.")
            j.optString("refresh_token").takeIf { it.isNotBlank() }?.let { refreshToken = it }
            Outcome.Ok(remember(j))
        }.getOrElse { Outcome.Error("Нет связи с Google. Проверьте интернет и попробуйте ещё раз.") }
    }

    /** Свежие токены без окна — по сохранённому токену обновления; null — не входили, отозвано или нет сети. */
    suspend fun refresh(): Tokens? = lock.withLock {
        cached?.let { if (it.expiresAt - nowMillis() > 5 * 60_000L) return it }
        val rt = refreshToken ?: return null
        runCatching {
            val j = token(parameters { append("grant_type", "refresh_token"); append("refresh_token", rt); append("client_id", clientId) })
            j?.let(::remember)
        }.getOrNull()
    }

    /** Выйти: отозвать токен обновления у Google и забыть его. */
    suspend fun signOut() {
        val rt = refreshToken
        refreshToken = null
        cached = null
        if (rt != null) runCatching { Http.client.submitForm("https://oauth2.googleapis.com/revoke", parameters { append("token", rt) }) }
    }

    private fun remember(j: JsonObject): Tokens = Tokens(
        idToken = j.optString("id_token").takeIf { it.isNotBlank() },
        accessToken = j.optString("access_token").takeIf { it.isNotBlank() },
        scopes = j.optString("scope").split(' ').filter { it.isNotBlank() }.toSet(),
        expiresAt = nowMillis() + j.optLong("expires_in", 3600L) * 1000,
    ).also { cached = it }

    /** Запрос к конечной точке токенов; null — Google ответил ошибкой (код не тот, токен отозван). */
    private suspend fun token(form: io.ktor.http.Parameters): JsonObject? {
        val resp = Http.client.submitForm("https://oauth2.googleapis.com/token", form)
        val j = runCatching { Json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull() ?: return null
        if (resp.status.value != 200) {
            // Токен обновления отозван или просрочен — забыть, чтобы в следующий раз показать окно входа
            if (j.optString("error") == "invalid_grant") { refreshToken = null; cached = null }
            return null
        }
        return j
    }

    /** Полезная нагрузка токена входа: почта, имя, аватарка. */
    fun claims(idToken: String): JsonObject? = runCatching {
        val payload = idToken.split('.')[1].let { it + "=".repeat((4 - it.length % 4) % 4) }
        Json.parseToJsonElement(Base64.UrlSafe.decode(payload).decodeToString()).jsonObject
    }.getOrNull()

    fun credential(t: Tokens): GoogleCredential? {
        val id = t.idToken ?: return null
        val c = claims(id) ?: return null
        val email = c.optString("email").takeIf { it.isNotBlank() } ?: return null
        return GoogleCredential(id, email, c.optString("name").takeIf { it.isNotBlank() }, c.optString("picture").takeIf { it.isNotBlank() })
    }

    private fun b64url(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes).trimEnd('=')

    private fun randomBytes(n: Int): ByteArray {
        val out = ByteArray(n)
        out.usePinned { SecRandomCopyBytes(kSecRandomDefault, n.convert(), it.addressOf(0)) }
        return out
    }

    private fun sha256(input: ByteArray): ByteArray {
        val out = UByteArray(CC_SHA256_DIGEST_LENGTH)
        input.usePinned { i -> out.usePinned { o -> CC_SHA256(i.addressOf(0), input.size.convert(), o.addressOf(0)) } }
        return out.toByteArray()
    }
}

/** Системное окно браузера для входа: открывает адрес и ждёт возврата по своей схеме. */
private object WebAuth {
    sealed interface Result {
        class Ok(val callback: String) : Result
        data object Cancelled : Result
        class Error(val message: String) : Result
    }

    /** Окну нужно, поверх чего показаться, — окно приложения. */
    private class Anchor : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
        override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
            keyWindow() ?: UIWindow()
    }

    // Сессию и «якорь» нужно держать, пока окно открыто: иначе их соберёт сборщик и окно закроется
    private var session: ASWebAuthenticationSession? = null
    private var anchor: Anchor? = null

    suspend fun open(url: String, scheme: String): Result = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl == null) { cont.resume(Result.Error("неверный адрес")); return@suspendCancellableCoroutine }
            val s = ASWebAuthenticationSession(nsUrl, scheme) { callback, error ->
                session = null; anchor = null
                if (!cont.isActive) return@ASWebAuthenticationSession
                val back = callback?.absoluteString
                cont.resume(
                    when {
                        back != null -> Result.Ok(back)
                        // Код 1 — человек закрыл окно (ASWebAuthenticationSessionErrorCodeCanceledLogin)
                        error == null || error.code == 1L -> Result.Cancelled
                        else -> Result.Error(error.localizedDescription)
                    },
                )
            }
            val a = Anchor()
            s.presentationContextProvider = a
            s.prefersEphemeralWebBrowserSession = false
            session = s; anchor = a
            cont.invokeOnCancellation { s.cancel(); session = null; anchor = null }
            if (!s.start()) { session = null; anchor = null; if (cont.isActive) cont.resume(Result.Error("окно не открылось")) }
        }
    }
}
