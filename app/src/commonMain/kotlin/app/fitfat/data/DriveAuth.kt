package app.fitfat.data

/**
 * Доступ к скрытой папке приложения в Google Диске пользователя (scope `drive.appdata`): приложение видит только
 * свои файлы там, не остальной Диск. Android — Google Identity AuthorizationClient, iOS — пока нет.
 */
expect class DriveAuth() {
    /**
     * Токен доступа к Диску для аккаунта [email]. [interactive] — можно показать окно согласия;
     * false — только если согласие уже дано (тихая автокопия). null — не дали доступ, нет сети или платформа не умеет.
     */
    suspend fun accessToken(email: String?, interactive: Boolean): String?
}

const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
