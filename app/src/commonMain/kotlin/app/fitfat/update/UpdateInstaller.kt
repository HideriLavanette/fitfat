package app.fitfat.update

import kotlinx.coroutines.flow.Flow

sealed interface DownloadEvent {
    data class Progress(val fraction: Float) : DownloadEvent
    /** Путь к скачанному файлу. */
    data class Done(val file: String) : DownloadEvent
}

/**
 * Установка обновления из GitHub Releases. Android скачивает APK и отдаёт системному установщику.
 * iOS сам себя ставить не может: [install] открывает AltStore, который скачает и поставит IPA (docs/IOS.md).
 */
expect class UpdateInstaller() {
    /** Приложение само скачивает файл и ведёт установку (Android); false — установку делает другое приложение, [install] получает адрес файла. */
    val installsItself: Boolean
    /** Скачивает APK во временный каталог, отдаёт прогресс 0..1. Старые файлы удаляет. */
    fun download(update: AppUpdate): Flow<DownloadEvent>
    fun canInstall(): Boolean
    /** Открывает системный экран «Установка неизвестных приложений» для нашего приложения. */
    fun openInstallPermission()
    /** Android: передаёт скачанный APK системному установщику. iOS: открывает AltStore с адресом IPA. */
    fun install(file: String)
}
