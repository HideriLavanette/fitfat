package app.fitfat.platform

import platform.UIKit.UIDevice

actual object Device {
    actual val id: String by lazy { UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "ios-unknown-device" }
    actual val info: String get() = "${UIDevice.currentDevice.model}, ${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}"
}

/** На iPhone падения пока не перехватываются (docs/IOS.md) — отчёты только через «Сообщить о проблеме». */
actual object CrashStore {
    actual fun install() {}
    actual fun take(): String? = null
}
