package app.fitfat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Схемы сгенерированы алгоритмом Material You (tonal spot) от seed #E8749F
private val LightScheme = lightColorScheme(
    primary = Color(0xFF8B4A61),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E3),
    onPrimaryContainer = Color(0xFF6F3349),
    inversePrimary = Color(0xFFFFB0C9),
    secondary = Color(0xFF74565F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E3),
    onSecondaryContainer = Color(0xFF5A3F48),
    tertiary = Color(0xFF7D5635),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF623F20),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF22191C),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF2DDE2),
    onSurfaceVariant = Color(0xFF514347),
    surfaceTint = Color(0xFF8B4A61),
    inverseSurface = Color(0xFF372E30),
    inverseOnSurface = Color(0xFFFDEDF0),
    outline = Color(0xFF837377),
    outlineVariant = Color(0xFFD5C2C6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F8),
    surfaceDim = Color(0xFFE6D6D9),
    surfaceContainer = Color(0xFFFAEAED),
    surfaceContainerHigh = Color(0xFFF5E4E7),
    surfaceContainerHighest = Color(0xFFEFDFE1),
    surfaceContainerLow = Color(0xFFFFF0F2),
    surfaceContainerLowest = Color(0xFFFFFFFF)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB0C9),
    onPrimary = Color(0xFF541D33),
    primaryContainer = Color(0xFF6F3349),
    onPrimaryContainer = Color(0xFFFFD9E3),
    inversePrimary = Color(0xFF8B4A61),
    secondary = Color(0xFFE2BDC7),
    onSecondary = Color(0xFF422931),
    secondaryContainer = Color(0xFF5A3F48),
    onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFFEFBC94),
    onTertiary = Color(0xFF48290C),
    tertiaryContainer = Color(0xFF623F20),
    onTertiaryContainer = Color(0xFFFFDCC2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191113),
    onBackground = Color(0xFFEFDFE1),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFEFDFE1),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    surfaceTint = Color(0xFFFFB0C9),
    inverseSurface = Color(0xFFEFDFE1),
    inverseOnSurface = Color(0xFF372E30),
    outline = Color(0xFF9E8C90),
    outlineVariant = Color(0xFF514347),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF403739),
    surfaceDim = Color(0xFF191113),
    surfaceContainer = Color(0xFF261D20),
    surfaceContainerHigh = Color(0xFF31282A),
    surfaceContainerHighest = Color(0xFF3C3235),
    surfaceContainerLow = Color(0xFF22191C),
    surfaceContainerLowest = Color(0xFF140C0E)
)

/** Цвета макросов — свои семантические цвета, не системные роли. */
object MacroColors {
    val protein = Color(0xFFD3C4F5)
    val proteinContainer = Color(0xFF332B45)
    val fat = Color(0xFFE6D09A)
    val fatContainer = Color(0xFF3A3122)
    val carbs = Color(0xFFEEBD9E)
    val carbsContainer = Color(0xFF3D2A21)
    val over = Color(0xFFFF5C63)
    val training = Color(0xFF8AB4FF)
    val cycle = Color(0xFFFF8A8A)
}

@Composable
fun FitfatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
