package app.fitfat.update

import kotlinx.coroutines.flow.Flow

sealed interface DownloadEvent {
    data class Progress(val fraction: Float) : DownloadEvent
    /** Путь к скачанному файлу. */
    data class Done(val file: String) : DownloadEvent
}

/**
 * Скачивание и установка APK из GitHub Releases — только Android ([app.fitfat.platform.Platform.supportsSelfUpdate]).
 * На iOS обновления приходят через TestFlight / App Store, эти методы не вызываются.
 */
expect class UpdateInstaller() {
    /** Скачивает APK во временный каталог, отдаёт прогресс 0..1. Старые файлы удаляет. */
    fun download(update: AppUpdate): Flow<DownloadEvent>
    fun canInstall(): Boolean
    /** Открывает системный экран «Установка неизвестных приложений» для нашего приложения. */
    fun openInstallPermission()
    /** Передаёт APK системному установщику. */
    fun install(file: String)
}
