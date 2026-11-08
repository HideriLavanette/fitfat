package app.fitfat.platform

import android.os.Build
import android.os.SystemClock
import app.fitfat.BuildConfig

actual object Platform {
    actual val isAndroid: Boolean = true
    actual val versionName: String = BuildConfig.VERSION_NAME
    actual val versionCode: Int = BuildConfig.VERSION_CODE
    actual val supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= 31
    actual val supportsSelfUpdate: Boolean = true
    actual val supportsBarcodeScan: Boolean = true
    actual val supportsGoogleSignIn: Boolean = true
    actual val supportsPhotoPick: Boolean = true
    actual val supportsBlur: Boolean = Build.VERSION.SDK_INT >= 31
    actual val supportsFiles: Boolean = true
}

actual fun uptimeMillis(): Long = SystemClock.uptimeMillis()

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
