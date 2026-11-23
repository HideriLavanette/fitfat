package app.fitfat.data

/**
 * Вход Google на iPhone — OAuth в системном окне браузера ([GoogleOAuth]). [clientId] (веб-клиент) здесь не нужен:
 * у iOS свой клиент OAuth. Пока он не задан в сборке, вход скрыт по [app.fitfat.platform.Platform.supportsGoogleSignIn].
 */
actual class GoogleSignIn actual constructor() {
    actual suspend fun signIn(clientId: String): SignInOutcome = when (val r = GoogleOAuth.authorize(GoogleOAuth.SIGN_IN_SCOPES)) {
        is GoogleOAuth.Outcome.Ok -> GoogleOAuth.credential(r.tokens)?.let { SignInOutcome.Ok(it) }
            ?: SignInOutcome.Error("Google вернул неожиданный ответ. Попробуйте ещё раз.")
        GoogleOAuth.Outcome.Cancelled -> SignInOutcome.Cancelled
        is GoogleOAuth.Outcome.Error -> SignInOutcome.Error(r.message)
    }

    actual suspend fun silent(clientId: String): GoogleCredential? = GoogleOAuth.refresh()?.let { GoogleOAuth.credential(it) }

    actual suspend fun clear() = GoogleOAuth.signOut()
}
