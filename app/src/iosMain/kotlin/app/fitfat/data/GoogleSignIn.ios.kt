package app.fitfat.data

/** На iPhone входа Google пока нет (docs/IOS.md): кнопка «Подключить» скрыта по [app.fitfat.platform.Platform.supportsGoogleSignIn]. */
actual class GoogleSignIn actual constructor() {
    actual suspend fun signIn(clientId: String): SignInOutcome =
        SignInOutcome.Error("Вход через Google на iPhone появится в следующих версиях.")

    actual suspend fun silent(clientId: String): GoogleCredential? = null

    actual suspend fun clear() {}
}
