package app.fitfat.platform

import android.content.Intent

actual fun restartApp() {
    val ctx = AndroidApp.context
    val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) ?: return
    ctx.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
