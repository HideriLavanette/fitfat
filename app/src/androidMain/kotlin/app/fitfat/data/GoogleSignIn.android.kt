package app.fitfat.data

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import app.fitfat.platform.AndroidApp
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Credential Manager, «Sign in with Google». Системному окну нужна активити — берём текущую ([AndroidApp.activity]). */
actual class GoogleSignIn actual constructor() {
    private val context get() = AndroidApp.activity ?: AndroidApp.context

    actual suspend fun signIn(clientId: String): SignInOutcome {
        val request = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build()
        return try {
            val cred = CredentialManager.create(context).getCredential(context, request).credential
            if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return SignInOutcome.Error("Google вернул неожиданный ответ. Попробуйте ещё раз.")
            }
            val g = GoogleIdTokenCredential.createFrom(cred.data)
            SignInOutcome.Ok(GoogleCredential(g.idToken, g.id, g.displayName, g.profilePictureUri?.toString()))
        } catch (e: GetCredentialCancellationException) {
            SignInOutcome.Cancelled
        } catch (e: NoCredentialException) {
            SignInOutcome.Error("На телефоне нет аккаунта Google. Добавьте его в настройках Android.")
        } catch (e: GetCredentialException) {
            SignInOutcome.Error("Не получилось войти: ${e.message ?: e.type}")
        } catch (e: Exception) {
            SignInOutcome.Error("Не получилось войти: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    actual suspend fun silent(clientId: String): GoogleCredential? {
        val option = GetGoogleIdOption.Builder().setServerClientId(clientId).setFilterByAuthorizedAccounts(true).setAutoSelectEnabled(true).build()
        return runCatching {
            val cred = CredentialManager.create(context).getCredential(context, GetCredentialRequest.Builder().addCredentialOption(option).build()).credential
            if (cred !is CustomCredential || cred.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null
            GoogleIdTokenCredential.createFrom(cred.data).let { GoogleCredential(it.idToken, it.id, it.displayName, it.profilePictureUri?.toString()) }
        }.getOrNull()
    }

    actual suspend fun clear() {
        CredentialManager.create(AndroidApp.context).clearCredentialState(ClearCredentialStateRequest())
    }
}
