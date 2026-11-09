package app.fitfat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// «Розовый»: схемы сгенерированы алгоритмом Material You (tonal spot) от seed #E8749F
internal val PinkLight = lightColorScheme(
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

internal val PinkDark = darkColorScheme(
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
    /** Ячейка дня цикла в календаре веса: фон, рамка, число. */
    val cycleContainer: Color,
    val cycleBorder: Color,
    val cycleInk: Color,
    /** Пунктир под словом, которого нет в базе. */
    val unknown: Color,
    /** Оценка ассистента (ИИ): жёлтая плашка и значок предупреждения — цифры примерные. */
    val ai: Color,
    val aiContainer: Color,
)

// Значения из DESIGN.md (тёмная тема)
private val DarkMacros = MacroPalette(
    protein = Color(0xFFD3C4F5), proteinContainer = Color(0xFF332B45),
    fat = Color(0xFFE6D09A), fatContainer = Color(0xFF3A3122),
    carbs = Color(0xFFEEBD9E), carbsContainer = Color(0xFF3D2A21),
    over = Color(0xFFFF5C63),
    training = Color(0xFF8AB4FF), trainingContainer = Color(0xFF1F2A40),
    cycle = Color(0xFFFF8A8A),
    cycleContainer = Color(0xFF4A2228), cycleBorder = Color(0xFF8C3B45), cycleInk = Color(0xFFFFB3B3),
    unknown = Color(0xFFFFB86B),
    ai = Color(0xFFFFD54F),
    aiContainer = Color(0xFF3D3415),
)

// Светлая тема (09.11.2026, правка автора: прежние тона 40 были тусклыми и «грязными»): чистые насыщенные фиолетовый,
// золотой и оранжевый — для полос и букв; дорожки и плашки — светлые того же оттенка. Текст на плашке — контраст около 2,7–4,7 (буквы и числа жирные).
private val LightMacros = MacroPalette(
    protein = Color(0xFF6D4AE0), proteinContainer = Color(0xFFEDE7FF),
    fat = Color(0xFFC99400), fatContainer = Color(0xFFFFF2CC),
    carbs = Color(0xFFDD5A26), carbsContainer = Color(0xFFFFE6DA),
    over = Color(0xFFD93A42),
    training = Color(0xFF2F5EA8), trainingContainer = Color(0xFFD9E3FF),
    cycle = Color(0xFFC23B3B),
    cycleContainer = Color(0xFFFFDAD9), cycleBorder = Color(0xFFC23B3B), cycleInk = Color(0xFF8C1D2B),
    unknown = Color(0xFFA35A00),
    ai = Color(0xFF7A5C00),
    aiContainer = Color(0xFFFFF0B8),
)

private val LocalMacroPalette = staticCompositionLocalOf { DarkMacros }

/** Светлая ли сейчас тема — по палитре макросов (её выбирает [FitfatTheme] вместе со схемой цветов). */
@Composable @ReadOnlyComposable
fun isLightTheme(): Boolean = LocalMacroPalette.current === LightMacros

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
    val cycleContainer: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.cycleContainer
    val cycleBorder: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.cycleBorder
    val cycleInk: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.cycleInk
    val unknown: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.unknown
    val ai: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.ai
    val aiContainer: Color @Composable @ReadOnlyComposable get() = LocalMacroPalette.current.aiContainer
}

@Composable
fun FitfatTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    palette: app.fitfat.data.AppPalette = app.fitfat.data.AppPalette.Graphite,
    content: @Composable () -> Unit,
) {
    val scheme = (if (dynamicColor) platformDynamicScheme(darkTheme) else null) ?: schemeFor(palette, darkTheme)
    // Значки строки состояния и навигации — по теме приложения, а не телефона (иначе тёмные на тёмном)
    SystemBarsStyle(darkTheme)
    CompositionLocalProvider(LocalMacroPalette provides if (darkTheme) DarkMacros else LightMacros) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
