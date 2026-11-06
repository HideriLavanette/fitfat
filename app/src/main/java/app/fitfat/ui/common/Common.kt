package app.fitfat.ui.common

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
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

val OverRed = Color(0xFFFF5C63)

data class Category(val id: String, val label: String, val icon: ImageVector, val bg: Color)

val Categories = listOf(
    Category("grain", "Крупы и хлеб", Icons.Outlined.RiceBowl, Color(0xFF4A3D22)),
    Category("meat", "Мясо и рыба", Icons.Outlined.LunchDining, Color(0xFF4C2E26)),
    Category("egg", "Яйца", Icons.Outlined.Egg, Color(0xFF423A2C)),
    Category("dairy", "Молочное", Icons.Outlined.LocalDrink, Color(0xFF263449)),
    Category("fruit", "Фрукты", Icons.Outlined.Spa, Color(0xFF3A4224)),
    Category("veg", "Овощи", Icons.Outlined.Eco, Color(0xFF24402F)),
    Category("oil", "Масла и орехи", Icons.Outlined.WaterDrop, Color(0xFF3A3122)),
    Category("sweet", "Сладкое", Icons.Outlined.Cookie, Color(0xFF3B2A33)),
)

fun category(id: String) = Categories.firstOrNull { it.id == id } ?: Categories.first()

@Composable
fun CategoryIcon(id: String, size: Dp = 44.dp) {
    val c = category(id)
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(c.bg), contentAlignment = Alignment.Center) {
        Icon(c.icon, contentDescription = null, tint = Color(0xFFEFDFE1), modifier = Modifier.size(size * 0.5f))
    }
}

fun fmt0(v: Double) = String.format(Locale("ru"), "%,d", Math.round(v)).replace('\u00A0', ' ')
fun fmt1(v: Double): String = if (v >= 100 || v == Math.floor(v)) Math.round(v).toString() else String.format(Locale("ru"), "%.1f", v)

/** Волнистое кольцо M3 Expressive: волна — съеденное, тихий трек — остаток, красная волна поверх — перебор. */
@Composable
fun WavyRing(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier, stroke: Dp = 8.dp) {
    Canvas(modifier) {
        val w = stroke.toPx()
        val r = size.minDimension / 2f - w * 1.6f
        val c = center
        val start = -PI.toFloat() / 2f
        val f = min(fraction, 1f)
        val end = start + f * 2f * PI.toFloat()
        val amp = 3.dp.toPx()
        fun wave(from: Float, to: Float): Path {
            val p = Path()
            val n = 200
            for (i in 0..n) {
                val t = from + (to - from) * i / n
                val rr = r + amp * sin((t - start) * 12f)
                val x = c.x + rr * cos(t); val y = c.y + rr * sin(t)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        if (f < 1f) {
            val gap = (w * 1.5f + 4.dp.toPx()) / r
            val ts = end + gap; val te = start + 2f * PI.toFloat() - gap
            if (te > ts) {
                val p = Path()
                val n = 80
                for (i in 0..n) {
                    val t = ts + (te - ts) * i / n
                    val x = c.x + r * cos(t); val y = c.y + r * sin(t)
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                drawPath(p, track, style = Stroke(width = w, cap = StrokeCap.Round))
            }
        }
        if (f > 0f) drawPath(wave(start, end), color, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val over = min(fraction - 1f, 1f)
        if (over > 0f) drawPath(wave(start, start + over * 2f * PI.toFloat()), OverRed, style = Stroke(width = w * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Волнистая полоска макроса с точкой-ограничителем и красным слоем перебора. */
@Composable
fun WavyBar(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val w = 4.dp.toPx()
        val cy = size.height / 2f
        val amp = 2.6.dp.toPx()
        val x0 = w / 2; val x1 = size.width - w / 2
        val xe = x0 + (x1 - x0) * min(fraction, 1f)
        fun wave(to: Float): Path {
            val p = Path(); val n = 120
            for (i in 0..n) {
                val x = x0 + (to - x0) * i / n
                val y = cy + amp * sin(x / 9.dp.toPx())
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        val gap = 8.dp.toPx()
        if (xe + gap < x1 - 6.dp.toPx()) {
            drawLine(track, Offset(xe + gap, cy), Offset(x1 - 6.dp.toPx(), cy), strokeWidth = w, cap = StrokeCap.Round)
            drawCircle(color, 2.dp.toPx(), Offset(x1, cy))
        }
        if (fraction > 0f) drawPath(wave(xe), color, style = Stroke(width = w, cap = StrokeCap.Round))
        val over = min(fraction - 1f, 1f)
        if (over > 0f) drawPath(wave(x0 + (x1 - x0) * over), OverRed, style = Stroke(width = w * 1.6f, cap = StrokeCap.Round))
    }
}
