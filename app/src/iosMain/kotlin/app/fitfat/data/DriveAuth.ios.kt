package app.fitfat.data

/**
 * Доступ к папке приложения в Google Диске на iPhone — тем же OAuth, что и вход ([GoogleOAuth]): право `drive.appdata`
 * запрашивается отдельным окном согласия при первом сохранении, дальше токен доступа берётся тихо.
 */
actual class DriveAuth actual constructor() {
    actual suspend fun accessToken(email: String?, interactive: Boolean): String? {
        GoogleOAuth.refresh()?.let { if (DRIVE_APPDATA_SCOPE in it.scopes) return it.accessToken }
        if (!interactive) return null
        val r = GoogleOAuth.authorize("${GoogleOAuth.SIGN_IN_SCOPES} $DRIVE_APPDATA_SCOPE", loginHint = email) as? GoogleOAuth.Outcome.Ok ?: return null
        return r.tokens.accessToken?.takeIf { DRIVE_APPDATA_SCOPE in r.tokens.scopes }
    }
}
