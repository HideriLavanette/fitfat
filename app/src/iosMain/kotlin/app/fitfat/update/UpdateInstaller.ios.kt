package app.fitfat.update

import app.fitfat.BuildValues
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS не даёт приложению установить само себя. Пока приложение ставится через AltStore (docs/IOS.md), обновление —
 * это переход в AltStore: он сам скачивает IPA из релиза, подписывает Apple ID владельца и ставит поверх.
 * Нужен AltServer на компьютере в той же сети Wi‑Fi. AltStore не установлен — открывается страница релиза.
 */
actual class UpdateInstaller actual constructor() {
    actual val installsItself: Boolean = false

    actual fun download(update: AppUpdate): Flow<DownloadEvent> = emptyFlow()
    actual fun canInstall(): Boolean = true
    actual fun openInstallPermission() {}

    /** [file] — адрес IPA в релизе GitHub. */
    actual fun install(file: String) {
        val app = UIApplication.sharedApplication
        val releases = NSURL.URLWithString("https://github.com/${BuildValues.UPDATE_REPO}/releases/latest")
        val altStore = NSURL.URLWithString("altstore://install?url=" + file.encodeURLParameter())
        if (altStore == null) { releases?.let { app.openURL(it, emptyMap<Any?, Any?>(), null) }; return }
        app.openURL(altStore, emptyMap<Any?, Any?>()) { opened ->
            if (!opened) releases?.let { app.openURL(it, emptyMap<Any?, Any?>(), null) }
        }
    }
}
