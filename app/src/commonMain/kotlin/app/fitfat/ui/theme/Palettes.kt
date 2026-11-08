package app.fitfat.ui.theme

import app.fitfat.time.Math
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import app.fitfat.data.AppPalette
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Тёмный «Графит» (08.11.2026, после проверки на телефоне): фон почти чёрный `#0B0D0F`, карточки `#161A1D` —
 * на них видны дорожки полосок БЖУ; `#2A3035` с образца — вариант поверхности, а не фон.
 *
 * Схемы цветов приложения (макет Settings, «Цвет приложения»). «Графит» — своя палитра автора (08.11.2026):
 * тёмно-графитовые поверхности, сине-серый и светлый серо-голубой, хвойно-аквамариновый акцент. «Розовый» — прежняя схема
 * из Theme.kt. Остальные — по алгоритму Material You (tonal spot) от цвета кнопки из макета: тона считаются
 * в CIELAB (L* = тон, как в HCT), оттенок — от цвета образца.
 */
internal fun schemeFor(p: AppPalette, dark: Boolean): ColorScheme = when (p) {
    AppPalette.Graphite -> if (dark) GraphiteDark else GraphiteLight
    AppPalette.Pink -> if (dark) PinkDark else PinkLight
    else -> tonalSpot(Color(p.swatch), dark)
}

// ——— «Графит»: #2A3035, #5F7685, #958F92, #B8C5CF; акцент — хвойно-аквамариновый #2A9A83 (вместо красного #E8403A, 08.11.2026) ———

private val GraphiteDark = darkColorScheme(
    primary = Color(0xFF2A9A83), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5F7685), onPrimaryContainer = Color(0xFFEEF3F7),
    inversePrimary = Color(0xFF1E7A67),
    secondary = Color(0xFFB8C5CF), onSecondary = Color(0xFF1F2B33),
    secondaryContainer = Color(0xFF475864), onSecondaryContainer = Color(0xFFE3ECF2),
    tertiary = Color(0xFFD3CBCE), onTertiary = Color(0xFF332E30),
    tertiaryContainer = Color(0xFF5A5457), onTertiaryContainer = Color(0xFFEFE8EA),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B0D0F), onBackground = Color(0xFFE6EBEF),
    surface = Color(0xFF0B0D0F), onSurface = Color(0xFFE6EBEF),
    surfaceVariant = Color(0xFF2A3136), onSurfaceVariant = Color(0xFFB8C5CF),
    surfaceTint = Color(0xFFB8C5CF),
    inverseSurface = Color(0xFFE6EBEF), inverseOnSurface = Color(0xFF2A3035),
    outline = Color(0xFF958F92), outlineVariant = Color(0xFF353D43),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2E353A), surfaceDim = Color(0xFF0B0D0F),
    surfaceContainerLowest = Color(0xFF070809), surfaceContainerLow = Color(0xFF111416),
    surfaceContainer = Color(0xFF161A1D), surfaceContainerHigh = Color(0xFF1E2327),
    surfaceContainerHighest = Color(0xFF272D32),
)

private val GraphiteLight = lightColorScheme(
    primary = Color(0xFF1E7A67), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE5EC), onPrimaryContainer = Color(0xFF2E3E49),
    inversePrimary = Color(0xFF7FD3BF),
    secondary = Color(0xFF5F7685), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3DEE6), onSecondaryContainer = Color(0xFF2E3E49),
    tertiary = Color(0xFF6E686B), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8E1E3), onTertiaryContainer = Color(0xFF3F3A3C),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF6F8F9), onBackground = Color(0xFF1C2125),
    surface = Color(0xFFF6F8F9), onSurface = Color(0xFF1C2125),
    surfaceVariant = Color(0xFFDDE4E9), onSurfaceVariant = Color(0xFF44535E),
    surfaceTint = Color(0xFF5F7685),
    inverseSurface = Color(0xFF2A3035), inverseOnSurface = Color(0xFFEEF2F5),
    outline = Color(0xFF747C82), outlineVariant = Color(0xFFC4CCD2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6F8F9), surfaceDim = Color(0xFFD5DBDF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFEFF2F4),
    surfaceContainer = Color(0xFFE8ECEF), surfaceContainerHigh = Color(0xFFE1E6EA),
    surfaceContainerHighest = Color(0xFFD9DFE4),
)

// ——— Tonal spot по образцу ———

/** Тон [tone] (L*) оттенка [hue] с насыщенностью до [chroma]; насыщенность снижается, пока цвет не влезет в sRGB. */
private fun toneOf(hue: Double, chroma: Double, tone: Double): Color {
    if (tone >= 100) return Color.White
    if (tone <= 0) return Color.Black
    var c = chroma
    while (true) {
        val rgb = labToSrgb(tone, c * cos(hue), c * sin(hue))
        if (rgb != null || c <= 0.5) return rgb ?: labToSrgb(tone, 0.0, 0.0)!!
        c -= 1.0
    }
}

private fun labToSrgb(l: Double, a: Double, b: Double): Color? {
    val fy = (l + 16) / 116
    val fx = fy + a / 500
    val fz = fy - b / 200
    fun inv(t: Double) = if (t.pow(3) > 0.008856) t.pow(3) else (116 * t - 16) / 903.3
    val x = 0.95047 * inv(fx); val y = if (l > 8) fy.pow(3) else l / 903.3; val z = 1.08883 * inv(fz)
    val rl = 3.2406 * x - 1.5372 * y - 0.4986 * z
    val gl = -0.9689 * x + 1.8758 * y + 0.0415 * z
    val bl = 0.0557 * x - 0.2040 * y + 1.0570 * z
    fun gam(v: Double) = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055
    val r = gam(rl); val g = gam(gl); val bb = gam(bl)
    val eps = 0.002
    if (r < -eps || r > 1 + eps || g < -eps || g > 1 + eps || bb < -eps || bb > 1 + eps) return null
    return Color((r.coerceIn(0.0, 1.0) * 255).roundToInt(), (g.coerceIn(0.0, 1.0) * 255).roundToInt(), (bb.coerceIn(0.0, 1.0) * 255).roundToInt())
}

/** Оттенок цвета в CIELAB, радианы. */
private fun hueOf(c: Color): Double {
    fun lin(v: Float) = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    val r = lin(c.red); val g = lin(c.green); val b = lin(c.blue)
    val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
    val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
    val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
    fun f(t: Double) = if (t > 0.008856) cbrt(t) else (903.3 * t + 16) / 116
    val a = 500 * (f(x) - f(y)); val bb = 200 * (f(y) - f(z))
    return atan2(bb, a).let { if (sqrt(a * a + bb * bb) < 1e-6) 0.0 else it }
}

private fun tonalSpot(seed: Color, dark: Boolean): ColorScheme {
    val h = hueOf(seed)
    val th = h + Math.toRadians(60.0)
    fun p(t: Int) = toneOf(h, 36.0, t.toDouble())
    fun s(t: Int) = toneOf(h, 16.0, t.toDouble())
    fun t3(t: Int) = toneOf(th, 24.0, t.toDouble())
    fun n(t: Int) = toneOf(h, 4.0, t.toDouble())
    fun nv(t: Int) = toneOf(h, 8.0, t.toDouble())
    return if (dark) darkColorScheme(
        primary = p(80), onPrimary = p(20), primaryContainer = p(30), onPrimaryContainer = p(90), inversePrimary = p(40),
        secondary = s(80), onSecondary = s(20), secondaryContainer = s(30), onSecondaryContainer = s(90),
        tertiary = t3(80), onTertiary = t3(20), tertiaryContainer = t3(30), onTertiaryContainer = t3(90),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        background = n(6), onBackground = n(90), surface = n(6), onSurface = n(90),
        surfaceVariant = nv(30), onSurfaceVariant = nv(80), surfaceTint = p(80),
        inverseSurface = n(90), inverseOnSurface = n(20), outline = nv(60), outlineVariant = nv(30), scrim = Color.Black,
        surfaceBright = n(24), surfaceDim = n(6), surfaceContainerLowest = n(4), surfaceContainerLow = n(10),
        surfaceContainer = n(12), surfaceContainerHigh = n(17), surfaceContainerHighest = n(22),
    ) else lightColorScheme(
        primary = p(40), onPrimary = Color.White, primaryContainer = p(90), onPrimaryContainer = p(30), inversePrimary = p(80),
        secondary = s(40), onSecondary = Color.White, secondaryContainer = s(90), onSecondaryContainer = s(30),
        tertiary = t3(40), onTertiary = Color.White, tertiaryContainer = t3(90), onTertiaryContainer = t3(30),
        error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
        background = n(98), onBackground = n(10), surface = n(98), onSurface = n(10),
        surfaceVariant = nv(90), onSurfaceVariant = nv(30), surfaceTint = p(40),
        inverseSurface = n(20), inverseOnSurface = n(95), outline = nv(50), outlineVariant = nv(80), scrim = Color.Black,
        surfaceBright = n(98), surfaceDim = n(87), surfaceContainerLowest = Color.White, surfaceContainerLow = n(96),
        surfaceContainer = n(94), surfaceContainerHigh = n(92), surfaceContainerHighest = n(90),
    )
}
