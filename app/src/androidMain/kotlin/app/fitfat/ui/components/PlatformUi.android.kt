package app.fitfat.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import app.fitfat.notify.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun DialogNoDim() {
    (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
}

@Composable
actual fun rememberNotificationPermission(onDenied: () -> Unit): () -> Unit {
    val denied by rememberUpdatedState(onDenied)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (!ok) denied() }
    return { if (Build.VERSION.SDK_INT >= 33 && !Reminders.canPost()) ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

@Composable
actual fun rememberTextFileSaver(mime: String, onDone: (Boolean) -> Unit): (name: String, content: suspend () -> String) -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val done by rememberUpdatedState(onDone)
    var pending: (suspend () -> String)? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        val content = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(content().toByteArray()) } }.isSuccess
            }
            done(ok)
        }
    }
    return { name, content -> pending = content; launcher.launch(name) }
}

@Composable
actual fun rememberTextFileOpener(onText: (String?) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val deliver by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull() }
            deliver(text)
        }
    }
    return { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
}

actual fun fullScreenDialogProperties(): androidx.compose.ui.window.DialogProperties =
    androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)

actual fun noFontPadding(): androidx.compose.ui.text.PlatformTextStyle? = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
