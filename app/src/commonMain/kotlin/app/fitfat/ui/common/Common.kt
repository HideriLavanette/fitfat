package app.fitfat.ui.common

import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin


/** [bg] — фон плашки в тёмной теме (из макетов), [bgLight] — в светлой (тон ~90, 09.11.2026). Брать через [container]. */
data class Category(val id: String, val label: String, val icon: ImageVector, val bg: Color, val bgLight: Color)

/** Фон плашки категории для текущей темы: тёмный из макетов или светлый пастельный. */
val Category.container: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = if (app.fitfat.ui.theme.isLightTheme()) bgLight else bg

// Категории и их иконки — как в макетах (Product-edit, Today): фон категории + линейная иконка 1,8.
// Цвета фонов — фиксированная палитра категорий из макетов, не роли темы.
val Categories = listOf(
    Category("grain", "Крупы и выпечка", strokeIcon("M3 11h18a9 9 0 0 1-18 0Z M8 7c0-1.5 1.5-1.5 1.5-3 M13 7c0-1.5 1.5-1.5 1.5-3", 1.8f), Color(0xFF4A3D22), Color(0xFFF1E5C8)),
    Category("meat", "Мясо и рыба", strokeIcon("M14.5 3.5a5.5 5.5 0 0 1 3 9.5l-3.5 3.5-6-6 3.5-3.5a5.5 5.5 0 0 1 3-3.5Z M8 11l-3.5 3.5 M5.5 13.5a2 2 0 1 0 2.5 2.5", 1.8f), Color(0xFF4C2E26), Color(0xFFF6DDD4)),
    Category("dairy", "Молочное", strokeIcon("M7 3h10l-1.3 17.1a1 1 0 0 1-1 .9H9.3a1 1 0 0 1-1-.9Z M7.5 9h9", 1.8f), Color(0xFF263449), Color(0xFFD9E4F5)),
    Category("fruit", "Фрукты", strokeIcon("M12 7.5c-3.5-2.5-8-.5-8 4.5 0 4.5 3 9 6 9 1 0 1.5-.5 2-.5s1 .5 2 .5c3 0 6-4.5 6-9 0-5-4.5-7-8-4.5Z M12 7.5c0-2 1-4 3-4.5", 1.8f), Color(0xFF3A4224), Color(0xFFE4EBCB)),
    Category("veg", "Овощи", strokeIcon("M5 19C5 10 10 5 19 5c0 9-5 14-14 14Z M5 19l8-8", 1.8f), Color(0xFF24402F), Color(0xFFD3EADB)),
    Category("sauce", "Соусы и сладкое", strokeIcon("M5 9h14v3a7 7 0 0 1-14 0ZM9 5h6", 1.8f), Color(0xFF3B2A33), Color(0xFFF0DCE6)),
    // Напитки: стакан с трубочкой, фон в тон палитре категорий
    Category("drink", "Напитки", strokeIcon("M6 8h12l-1.4 12.1a1 1 0 0 1-1 .9H8.4a1 1 0 0 1-1-.9Z M6.5 12.5h11 M13 8l1.6-5H18", 1.8f), Color(0xFF1F3D44), Color(0xFFD0E8ED)),
)

/** Яйца есть в дневнике макета, но не в списке выбора категории. */
private val EggCategory = Category("egg", "Яйца", strokeIcon("M12 3c-4 0-7 6.5-7 11a7 7 0 0 0 14 0c0-4.5-3-11-7-11Z", 1.8f), Color(0xFF423A2C), Color(0xFFEEE4D2))

/** Блюдо — отдельная плашка из макетов (Dishes, Products). */
private val DishCategory = Category("dish", "Блюдо", strokeIcon("M4 10h16v6a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4ZM2 10h20M9 6c0-1 1-1 1-2M14 6c0-1 1-1 1-2", 1.8f), Color(0xFF5A3F48), Color(0xFFF3DCE4))

/** Старые id из базы 0.3.0 показываем ближайшей категорией макета. */
fun category(id: String): Category = when (id) {
    "egg" -> EggCategory
    "dish" -> DishCategory
    "sweet", "oil" -> Categories.first { it.id == "sauce" }
    else -> Categories.firstOrNull { it.id == id } ?: Categories.first()
}

/** Плашка категории: в макете 44 dp, скругление 14, иконка 22. Есть [photo] — вместо иконки фото продукта. */
@Composable
fun CategoryIcon(id: String, size: Dp = 44.dp, radius: Dp = size * (14f / 44f), iconSize: Dp = size * 0.5f, photo: String? = null) {
    val c = category(id)
    if (photo != null) {
        app.fitfat.ui.components.PhotoImage(photo, Modifier.size(size).clip(RoundedCornerShape(radius)).background(c.container))
        return
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(c.container), contentAlignment = Alignment.Center) {
        Icon(c.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(iconSize))
    }
}

/** Целое с разделением тысяч неразрывным пробелом: «1 820». */
fun fmt0(v: Double): String =
    Fmt.int(v)

/** Одна десятая всегда, как в строках дневника макета: «1,0», «12,6». */
fun fmtF1(v: Double): String = Fmt.f1(v)
fun fmt1(v: Double): String = if (v >= 100 || v == Math.floor(v)) Math.round(v).toString() else Fmt.f1(v)


/**
 * «Б 12,6 · Ж 3,3 · У 68»: буквы жирные в цвете макроса, числа — цветом текста вокруг.
 * [c] = null — без углеводов. [prefix] / [tail] — текст до и после.
 */
@Composable
fun macroLine(
    p: Double, f: Double, c: Double?,
    prefix: String = "", tail: String = "",
    fmt: (Double) -> String = ::fmt1,
): androidx.compose.ui.text.AnnotatedString {
    val pc = app.fitfat.ui.theme.MacroColors.protein
    val fc = app.fitfat.ui.theme.MacroColors.fat
    val cc = app.fitfat.ui.theme.MacroColors.carbs
    return androidx.compose.ui.text.buildAnnotatedString {
        append(prefix)
        fun part(letter: String, color: Color, v: Double) {
            pushStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)); append(letter); pop()
            append(" " + fmt(v))
        }
        part("Б", pc, p); append(" · "); part("Ж", fc, f)
        if (c != null) { append(" · "); part("У", cc, c) }
        append(tail)
    }
}
