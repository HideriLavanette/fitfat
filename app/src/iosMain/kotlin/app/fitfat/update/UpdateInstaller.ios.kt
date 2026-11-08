package app.fitfat.update

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** На iOS самообновления нет: обновления через TestFlight / App Store. Раздел скрыт по [app.fitfat.platform.Platform.supportsSelfUpdate]. */
actual class UpdateInstaller actual constructor() {
    actual fun download(update: AppUpdate): Flow<DownloadEvent> = flow { throw UnsupportedOperationException("На iOS обновления приходят через App Store") }
    actual fun canInstall(): Boolean = false
    actual fun openInstallPermission() {}
    actual fun install(file: String) {}
}
