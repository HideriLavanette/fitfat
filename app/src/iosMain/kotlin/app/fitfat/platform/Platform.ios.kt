package app.fitfat.platform

import app.fitfat.BuildValues
import kotlinx.coroutines.IO
import platform.Foundation.NSProcessInfo

actual object Platform {
    actual val isAndroid: Boolean = false
    actual val versionName: String = BuildValues.VERSION_NAME
    actual val versionCode: Int = BuildValues.VERSION_CODE
    actual val supportsDynamicColor: Boolean = false
    actual val supportsSelfUpdate: Boolean = false
    // Пока нет на iPhone (docs/IOS.md): сканер — нужен AVFoundation, вход Google — SDK или OAuth, фото — PHPicker
    actual val supportsBarcodeScan: Boolean = false
    actual val supportsGoogleSignIn: Boolean = false
    actual val supportsPhotoPick: Boolean = false
    actual val supportsBlur: Boolean = true
    actual val supportsFiles: Boolean = true
}

actual fun uptimeMillis(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
