package app.fitfat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Шаг линейки граммов: до 100 г — 5, до 1 000 г — 10, дальше — 50. [dir] — направление шага. */
fun gramStep(v: Int, dir: Int): Int {
    val x = if (dir > 0) v else v - 1
    return if (x >= 1000) 50 else if (x >= 100) 10 else 5
}

/** Следующее значение линейки от [v] в сторону [dir], с выравниванием на сетку шага. */
fun gramNext(v: Int, dir: Int): Int {
    val s = gramStep(v, dir)
    return if (dir > 0) (floor(v.toDouble() / s) * s + s).toInt() else (ceil(v.toDouble() / s) * s - s).toInt()
}

private const val SP = 22f   // расстояние между рисками, dp
private const val R = 120f   // радиус «цилиндра» линейки, dp

/**
 * Горизонтальная линейка граммов (макет: «pot», высота 64, скругление 20).
 * Риски на цилиндре: каждая пятая длиннее, к краям ниже и бледнее; по центру — розовая черта.
 * Палец влево — больше граммов.
 */
@Composable
fun GramRuler(
    grams: Int,
    onGramsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 2000,
    label: String = "Вес порции, граммы",
) {
    val value by rememberUpdatedState(grams)
    val change by rememberUpdatedState(onGramsChange)
    // Значение, уже отданное наружу: за кадр может пройти несколько шагов подряд
    val latest = remember { intArrayOf(grams) }
    latest[0] = value
    val drag = rememberStepDrag(SP) { dir ->
        val nv = gramNext(latest[0], dir)
        if (nv > max || nv < min) false else { latest[0] = nv; change(nv); true }
    }
    val colors = MaterialTheme.colorScheme
    Canvas(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .semantics { contentDescription = label; stateDescription = "$grams г" }
            .stepDrag(drag, horizontal = true),
    ) {
        drawRect(colors.surfaceContainer)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val s = gramStep(grams, 1)
        val idx = (grams.toDouble() / s).roundToInt()
        for (k in -14..14) {
            val th = (k * SP + drag.offset) / R
            if (abs(th) > 1.45f) continue
            val c = cos(th)
            val x = cx + (R * sin(th)).dp.toPx()
            val major = ((idx + k) % 5 + 5) % 5 == 0
            val h = ((if (major) 30f else 18f) * (0.45f + 0.55f * c)).dp.toPx()
            val w = (if (major) 2.5f else 2f).dp.toPx()
            val a = c.toDouble().pow(2.2).toFloat() * (if (major) 0.95f else 0.6f)
            drawRoundRect(
                colors.onSurface.copy(alpha = a.coerceIn(0f, 1f)),
                topLeft = Offset(x - w / 2f, cy - h / 2f), size = Size(w, h),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        // Центральная метка
        val mw = 3.dp.toPx()
        drawRoundRect(
            colors.primary,
            topLeft = Offset(cx - mw / 2f, 10.dp.toPx()), size = Size(mw, size.height - 20.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}
