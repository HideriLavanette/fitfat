package app.fitfat.data

/** На iPhone входа Google пока нет (docs/IOS.md) — и копии в Диск тоже. */
actual class DriveAuth actual constructor() {
    actual suspend fun accessToken(email: String?, interactive: Boolean): String? = null
}
