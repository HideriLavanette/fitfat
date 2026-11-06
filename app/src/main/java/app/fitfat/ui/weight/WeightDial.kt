package app.fitfat.ui.weight

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 12° поворота = 0,1 кг, полный оборот = 3 кг. Шесть крупных меток по 0,5 кг, между ними точки. */
private const val DEG_PER_STEP = 12f

fun formatKg(kg: Double) = String.format(Locale("ru"), "%.1f", kg)

@Composable
fun WeightDial(
    valueKg: Double,
    onValueChange: (Double) -> Unit,
    caption: String,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Последнее значение, которое колесо уже отдало наружу: между кадрами может прийти несколько шагов подряд
    val latest = remember { doubleArrayOf(valueKg) }
    latest[0] = valueKg
    val onChange by rememberUpdatedState(onValueChange)
    var residual by remember { mutableFloatStateOf(0f) } // доля шага, которую палец уже провернул
    val spin = remember { Animatable(0f) }
    val colors = MaterialTheme.colorScheme

    fun turn(deg: Float): Boolean {
        residual += deg
        var steps = 0
        while (residual >= DEG_PER_STEP) { residual -= DEG_PER_STEP; steps++ }
        while (residual <= -DEG_PER_STEP) { residual += DEG_PER_STEP; steps-- }
        if (steps == 0) return true
        val now = latest[0]
        val next = ((now * 10).roundToInt() + steps).coerceIn(0, 3000) / 10.0
        if (next == now) { residual = 0f; return false }
        latest[0] = next
        onChange(next)
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        return true
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Вес ${formatKg(valueKg)} килограмма. Вращайте круг" }
            .pointerInput(Unit) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                fun angleOf(p: Offset) = Math.toDegrees(atan2((p.y - center.y).toDouble(), (p.x - center.x).toDouble())).toFloat()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    scope.launch { spin.stop() }
                    var last = angleOf(down.position)
                    var total = 0f
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, Offset(0f, 0f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val a = angleOf(change.position)
                        var d = a - last
                        if (d > 180f) d -= 360f
                        if (d < -180f) d += 360f
                        last = a
                        total += d
                        tracker.addPosition(change.uptimeMillis, Offset(total, 0f))
                        turn(d)
                        change.consume()
                    }
                    // Инерция: угловая скорость в °/с затухает экспоненциально
                    val v = tracker.calculateVelocity().x.coerceIn(-2500f, 2500f)
                    scope.launch {
                        if (abs(v) > 60f) {
                            spin.snapTo(0f)
                            var prev = 0f
                            spin.animateDecay(v, exponentialDecay(frictionMultiplier = 2.2f)) {
                                val ok = turn(value - prev)
                                prev = value
                                if (!ok) scope.launch { spin.stop() }
                            }
                        }
                        // Доводка до ближайшей метки
                        val target = if (residual >= DEG_PER_STEP / 2) DEG_PER_STEP else if (residual <= -DEG_PER_STEP / 2) -DEG_PER_STEP else 0f
                        val start = residual
                        val settle = Animatable(0f)
                        var applied = 0f
                        settle.animateTo(target - start, spring(dampingRatio = 0.55f, stiffness = 600f)) {
                            turn(value - applied); applied = value
                        }
                        residual = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val r = this.size.minDimension / 2f
            val ring = r * 0.885f
            // Трек кольца
            drawCircle(colors.surfaceContainerHighest, radius = ring, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 18.dp.toPx()))
            // Шкала крутится вместе со значением
            val rot = (valueKg * 10).toFloat() * DEG_PER_STEP + residual
            rotate(rot) {
                for (i in 0 until 30) {
                    val a = (i * DEG_PER_STEP - 90f) * (PI / 180f).toFloat()
                    val c = Offset(center.x + ring * cos(a), center.y + ring * sin(a))
                    if (i % 5 == 0) {
                        val inner = Offset(center.x + (ring - 6.dp.toPx()) * cos(a), center.y + (ring - 6.dp.toPx()) * sin(a))
                        val outer = Offset(center.x + (ring + 6.dp.toPx()) * cos(a), center.y + (ring + 6.dp.toPx()) * sin(a))
                        drawLine(colors.onSurface, inner, outer, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    } else {
                        drawCircle(colors.outline, radius = 1.9.dp.toPx(), center = c)
                    }
                }
            }
            // Неподвижный указатель сверху
            val tip = Path().apply {
                moveTo(center.x, center.y - ring + 12.dp.toPx() + 8.dp.toPx())
                lineTo(center.x - 6.dp.toPx(), center.y - ring + 12.dp.toPx() - 2.dp.toPx())
                lineTo(center.x + 6.dp.toPx(), center.y - ring + 12.dp.toPx() - 2.dp.toPx())
                close()
            }
            drawPath(tip, colors.primary)
            // «Печенька» под числом
            val cookie = Path()
            val steps = 240
            for (k in 0..steps) {
                val t = k / steps.toFloat() * 2f * PI.toFloat()
                val rr = r * 0.66f + r * 0.043f * cos(9 * t)
                val p = Offset(center.x + rr * cos(t), center.y + rr * sin(t))
                if (k == 0) cookie.moveTo(p.x, p.y) else cookie.lineTo(p.x, p.y)
            }
            cookie.close()
            drawPath(cookie, colors.primaryContainer)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatKg(valueKg) + " кг",
                fontSize = 44.sp, fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer,
            )
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}
