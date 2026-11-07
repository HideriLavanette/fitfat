package app.fitfat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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

/** Палитра макросов и типов дня — свои семантические цвета, не системные роли. */
@Immutable
data class MacroPalette(
    val protein: Color,
    val proteinContainer: Color,
    val fat: Color,
    val fatContainer: Color,
    val carbs: Color,
    val carbsContainer: Color,
    val over: Color,
    val training: Color,
    val trainingContainer: Color,
    val cycle: Color,
    /** Пунктир под словом, которого нет в базе. */
    val unknown: Color,
)

// Значения из DESIGN.md (тёмная тема)
private val DarkMacros = MacroPalette(
    protein = Color(0xFFD3C4F5), proteinContainer = Color(0xFF332B45),
    fat = Color(0xFFE6D09A), fatContainer = Color(0xFF3A3122),
    carbs = Color(0xFFEEBD9E), carbsContainer = Color(0xFF3D2A21),
    over = Color(0xFFFF5C63),
    training = Color(0xFF8AB4FF), trainingContainer = Color(0xFF1F2A40),
    cycle = Color(0xFFFF8A8A),
    unknown = Color(0xFFFFB86B),
)

// Светлая тема: те же оттенки, тон 40 для линии и тон 90 для дорожки
private val LightMacros = MacroPalette(
    protein = Color(0xFF6B5A9C), proteinContainer = Color(0xFFEBE0FF),
    fat = Color(0xFF7A6421), fatContainer = Color(0xFFF7E8BF),
    carbs = Color(0xFF8E5333), carbsContainer = Color(0xFFFFDECB),
    over = Color(0xFFD93A42),
    training = Color(0xFF2F5EA8), trainingContainer = Color(0xFFD9E3FF),
    cycle = Color(0xFFC23B3B),
    unknown = Color(0xFFA35A00),
)

private val LocalMacroPalette = staticCompositionLocalOf { DarkMacros }

/** Доступ к палитре макросов текущей темы: `MacroColors.protein` и т. п. */
object MacroColors {
    val protein: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.protein
    val proteinContainer: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.proteinContainer
    val fat: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.fat
    val fatContainer: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.fatContainer
    val carbs: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.carbs
    val carbsContainer: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.carbsContainer
    val over: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.over
    val training: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.training
    val trainingContainer: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.trainingContainer
    val cycle: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.cycle
    val unknown: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.unknown
}

@Composable
fun FitfatTheme(
    // Всегда тёмная; выбор темы появится в настройках позже
    darkTheme: Boolean = true,
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
    // Значки строки состояния и навигации — по теме приложения, а не телефона (иначе тёмные на тёмном)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalMacroPalette provides if (darkTheme) DarkMacros else LightMacros) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
