package app.fitfat.platform

/**
 * Что умеет платформа (docs/IOS.md, «Чего на iOS пока нет»). По флагам экраны прячут кнопки,
 * чтобы не оставлять кнопок без действия.
 */
expect object Platform {
    val isAndroid: Boolean
    /** Версия приложения для «Настроек» и окна обновления. */
    val versionName: String
    val versionCode: Int
    /** Цвета системы (Material You) — Android 12+. */
    val supportsDynamicColor: Boolean
    /** Самообновление из GitHub Releases — только Android. */
    val supportsSelfUpdate: Boolean
    /** Сканер штрихкодов. */
    val supportsBarcodeScan: Boolean
    /** Вход через Google (и ассистент, которому он нужен). */
    val supportsGoogleSignIn: Boolean
    /** Фото продукта с камеры и из галереи. */
    val supportsPhotoPick: Boolean
    /** Размытие `RenderEffect` в анимации «Добавить» (Android 12+, iOS всегда). */
    val supportsBlur: Boolean
    /** Экспорт и импорт файлов (JSON/CSV). */
    val supportsFiles: Boolean
}

/** Монотонное время, мс — вместо `SystemClock.uptimeMillis()`. */
expect fun uptimeMillis(): Long
