package app.fitfat.data

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import app.fitfat.platform.AndroidApp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Google Identity AuthorizationClient: при первом обращении — системное окно согласия на доступ к папке приложения в Диске. */
actual class DriveAuth actual constructor() {
    actual suspend fun accessToken(email: String?, interactive: Boolean): String? = runCatching {
        val activity = AndroidApp.activity as? ComponentActivity ?: return null
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .apply { if (email != null) setAccount(android.accounts.Account(email, "com.google")) }
            .build()
        val client = Identity.getAuthorizationClient(activity)
        val result = client.authorize(request).await()
        if (!result.hasResolution()) return result.accessToken
        if (!interactive) return null
        val pending = result.pendingIntent ?: return null
        val data = launch(activity, IntentSenderRequest.Builder(pending.intentSender).build()) ?: return null
        client.getAuthorizationResultFromIntent(data).accessToken
    }.getOrNull()

    /** Окно согласия; null — закрыли или отказали. */
    private suspend fun launch(activity: ComponentActivity, request: IntentSenderRequest): Intent? = suspendCancellableCoroutine { cont ->
        var unregister: (() -> Unit)? = null
        val launcher = activity.activityResultRegistry.register("drive-auth-${System.nanoTime()}", ActivityResultContracts.StartIntentSenderForResult()) { r ->
            unregister?.invoke()
            if (cont.isActive) cont.resume(if (r.resultCode == Activity.RESULT_OK) r.data else null)
        }
        unregister = { launcher.unregister() }
        cont.invokeOnCancellation { launcher.unregister() }
        launcher.launch(request)
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
