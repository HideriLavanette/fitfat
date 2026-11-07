package app.fitfat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.fitfat.ui.theme.MacroColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Волнистое кольцо остатка калорий (макет Today: 256 dp, R 118, линия 8, 14 волн, амплитуда 3,2).
 * Волна — съеденное, ровный трек — остаток с зазорами 0,11 рад, красная волна 11 dp — перебор,
 * её длина растёт с размером перебора (100 % перебора = полный круг).
 */
@Composable
fun WavyRing(fraction: Float, color: Color, track: Color, modifier: Modifier = Modifier) {
    val over = MacroColors.over
    Canvas(modifier) {
        val k = size.minDimension / 256f          // масштаб от макета
        val r = 118f * k
        val amp = 3.2f * k
        val c = center
        val start = -PI.toFloat() / 2f
        val frac = min(fraction, 1f).coerceAtLeast(0f)
        val end = start + frac * 2f * PI.toFloat()
        val gap = 0.11f

        fun wave(from: Float, to: Float, n: Int): Path {
            val p = Path()
            for (i in 0..n) {
                val t = from + (to - from) * i / n
                val rr = r + amp * sin((t - start) * 14f)
                val x = c.x + rr * cos(t); val y = c.y + rr * sin(t)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }

        // Трек: от конца волны до начала круга, ровная дуга
        val ts = end + gap
        val te = start + 2f * PI.toFloat() - gap
        if (te > ts) {
            val p = Path()
            for (i in 0..80) {
                val t = ts + (te - ts) * i / 80
                val x = c.x + r * cos(t); val y = c.y + r * sin(t)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, track, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        }
        if (frac > 0f) drawPath(wave(start, end, 260), color, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val overFrac = min(fraction - 1f, 1f)
        if (overFrac > 0f) {
            drawPath(wave(start, start + overFrac * 2f * PI.toFloat(), 120), over, style = Stroke(11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Волнистая полоска макроса (макет Today: 310×14, линия 4, волна 2,6 / период 9).
 * Ровный трек остатка с зазором 8, точка-ограничитель r 2 на конце, перебор — красная волна 6.
 */
@Composable
fun WavyBar(value: Double, goal: Double, color: Color, track: Color, modifier: Modifier = Modifier) {
    val over = MacroColors.over
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val u = 1.dp.toPx()
        val cy = size.height / 2f
        val x0 = 3f * u
        val wd = size.width - 6f * u                      // в макете 304 из 310
        val fr = if (goal > 0) min(value / goal, 1.0).toFloat() else 0f
        val xe = x0 + wd * fr
        fun wave(to: Float, n: Int): Path {
            val p = Path()
            for (i in 0..n) {
                val x = x0 + (to - x0) * i / n
                val y = cy + 2.6f * u * sin(x / (9f * u))
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        if (xe + 8f * u < x0 + wd - 6f * u) {
            drawLine(track, Offset(xe + 8f * u, cy), Offset(x0 + wd - 6f * u, cy), strokeWidth = 4f * u, cap = StrokeCap.Round)
        }
        if (fr > 0f) drawPath(wave(xe, 120), color, style = Stroke(4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 2f * u, Offset(size.width - 5f * u, cy))
        if (goal > 0 && value > goal) {
            val xo = x0 + wd * min((value - goal) / goal, 1.0).toFloat()
            drawPath(wave(xo, 60), over, style = Stroke(6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
