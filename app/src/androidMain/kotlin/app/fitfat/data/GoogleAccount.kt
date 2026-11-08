package app.fitfat.data

import android.content.Context
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import app.fitfat.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Подключённый аккаунт: почта, имя (может не быть). */
data class Account(val email: String, val name: String?)

/**
 * Вход через Google (Credential Manager, «Sign in with Google»). Приложение получает только имя и почту —
 * ни пароля, ни доступа к Gmail, Диску или платежам. На диске — только почта и имя для карточки профиля.
 * Токен входа (живёт час) держим в памяти: им ассистент подтверждает серверу, кто спрашивает (DESIGN.md, «Ассистент»).
 */
class GoogleAccount private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val _account = MutableStateFlow(load())
    val account: StateFlow<Account?> = _account.asStateFlow()

    @Volatile private var token: String? = null
    @Volatile private var tokenExp = 0L

    private fun keep(g: GoogleIdTokenCredential) {
        token = g.idToken
        tokenExp = runCatching {
            val payload = g.idToken.split('.')[1]
            org.json.JSONObject(String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING))).getLong("exp") * 1000
        }.getOrDefault(System.currentTimeMillis() + 50 * 60_000L)
    }

    /**
     * Свежий токен входа для сервера ассистента или null (не вошли, нет сети, Google отказал).
     * Просроченный обновляется без окна выбора — тем же аккаунтом, что уже подключён. [activity] — контекст активити.
     */
    suspend fun idToken(activity: Context): String? {
        if (_account.value == null) return null
        token?.let { if (tokenExp - System.currentTimeMillis() > 5 * 60_000L) return it }
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.takeIf { it.isNotBlank() } ?: return null
        val option = GetGoogleIdOption.Builder().setServerClientId(clientId).setFilterByAuthorizedAccounts(true).setAutoSelectEnabled(true).build()
        return runCatching {
            val cred = CredentialManager.create(activity).getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
            if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null
            GoogleIdTokenCredential.createFrom(cred.data).also(::keep).idToken
        }.getOrNull()
    }

    private fun load(): Account? = prefs.getString("email", null)?.let { Account(it, prefs.getString("name", null)) }

    /** Итог входа: аккаунт, отмена пользователем или текст ошибки для экрана. */
    sealed interface Result {
        data class Ok(val account: Account) : Result
        data object Cancelled : Result
        data class Error(val message: String) : Result
    }

    /** Открывает выбор аккаунта Google. [activity] — контекст активити (нужен системному окну). */
    suspend fun signIn(activity: Context): Result {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) return Result.Error("Вход ещё не настроен в этой сборке — нет идентификатора клиента Google.")
        val request = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build()
        return try {
            val cred = CredentialManager.create(activity).getCredential(activity, request).credential
            if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return Result.Error("Google вернул неожиданный ответ. Попробуйте ещё раз.")
            }
            val g = GoogleIdTokenCredential.createFrom(cred.data)
            val acc = Account(g.id, g.displayName?.takeIf { it.isNotBlank() })
            prefs.edit { putString("email", acc.email); putString("name", acc.name) }
            keep(g)
            _account.value = acc
            Result.Ok(acc)
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: NoCredentialException) {
            Result.Error("На телефоне нет аккаунта Google. Добавьте его в настройках Android.")
        } catch (e: GetCredentialException) {
            Result.Error("Не получилось войти: ${e.message ?: e.type}")
        } catch (e: Exception) {
            Result.Error("Не получилось войти: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Отключить: забываем аккаунт и просим систему не подставлять его автоматически. */
    suspend fun signOut(context: Context) {
        prefs.edit { clear() }
        _account.value = null
        token = null; tokenExp = 0
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    companion object {
        @Volatile private var instance: GoogleAccount? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: GoogleAccount(context.applicationContext).also { instance = it } }
    }
}
