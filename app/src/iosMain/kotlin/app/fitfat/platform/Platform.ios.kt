package app.fitfat.platform

import app.fitfat.BuildValues
import kotlinx.coroutines.IO
import platform.Foundation.NSProcessInfo

actual object Platform {
    actual val isAndroid: Boolean = false
    actual val versionName: String = BuildValues.VERSION_NAME
    actual val versionCode: Int = BuildValues.VERSION_CODE
    actual val supportsDynamicColor: Boolean = false
    // Обновление через AltStore — только у сборок из релиза (у них свой номер); сборкам из ветки main предлагать релиз незачем
    actual val supportsSelfUpdate: Boolean = BuildValues.VERSION_CODE > 1
    // Пока нет на iPhone (docs/IOS.md): сканер — нужен AVFoundation, фото — PHPicker
    actual val supportsBarcodeScan: Boolean = false
    // Вход Google (OAuth в системном окне браузера) — когда в сборке задан клиент OAuth типа «iOS»: googleIosClientId
    actual val supportsGoogleSignIn: Boolean = BuildValues.GOOGLE_IOS_CLIENT_ID.isNotBlank()
    actual val supportsPhotoPick: Boolean = false
    actual val supportsBlur: Boolean = true
    actual val supportsFiles: Boolean = true
}

actual fun uptimeMillis(): Long = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
