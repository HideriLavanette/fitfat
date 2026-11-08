package app.fitfat.ui.weight

import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.ui.components.bump
import app.fitfat.ui.components.tick
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** 12° поворота = 0,1 кг, полный оборот = 3 кг. Шесть крупных меток по 0,5 кг, между ними точки. */
private const val DEG_PER_STEP = 12f

fun formatKg(kg: Double) = Fmt.f1(kg)

/** «+0,3», «−0,2», «±0,0». */
fun signedKg(d: Double): String {
    val r = (d * 10).roundToInt()
    return (if (r > 0) "+" else if (r < 0) "−" else "±") + formatKg(abs(r) / 10.0)
}

/** Палец подсказки из макета (вьюбокс 24). */
private val fingerPath = PathParser().parsePathString(
    "M8.5 13V4.8a1.6 1.6 0 0 1 3.2 0V11l.1-1.2a1.6 1.6 0 0 1 3.2.2v1l.1-.4a1.6 1.6 0 0 1 3.1.5v.6a1.6 1.6 0 0 1 3.1.5V16a6 6 0 0 1-6 6h-1.4a6 6 0 0 1-4.7-2.3l-3.3-4.2a1.6 1.6 0 0 1 2.4-2.1Z",
).toPath()

/**
 * Круг веса (макеты Main / Weight-edit): 280 dp, дорожка 18 `surfaceContainerHighest`, шкала крутится вместе со значением,
 * «печенька» `primaryContainer`, число 64 / 750 + «кг» 20 / 500, подпись 14. Пока [hint] — палец показывает,
 * как крутить (дуга −40° → 50°, цикл 2,6 с); первое касание вызывает [onTouched].
 */
@Composable
fun WeightDial(
    valueKg: Double,
    onValueChange: (Double) -> Unit,
    caption: String,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    hint: Boolean = false,
    onTouched: () -> Unit = {},
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // Последнее значение, которое колесо уже отдало наружу: между кадрами может прийти несколько шагов подряд
    val latest = remember { doubleArrayOf(valueKg) }
    latest[0] = valueKg
    val onChange by rememberUpdatedState(onValueChange)
    val touched by rememberUpdatedState(onTouched)
    val lastBump = remember { longArrayOf(0L) }
    var residual by remember { mutableFloatStateOf(0f) } // доля шага, которую палец уже провернул
    val spin = remember { Animatable(0f) }
    val colors = MaterialTheme.colorScheme

    // Подсказка: фаза 0..1 за 2,6 с
    val phase = if (hint) {
        val tr = rememberInfiniteTransition(label = "hint")
        tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "phase").value
    } else 0f
    val a0 = -40f; val a1 = 50f
    fun ease(x: Float) = if (x < 0.5f) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2
    var demoAng = a0; var fade = 0f
    if (hint) when {
        phase < 0.12f -> { fade = phase / 0.12f }
        phase < 0.62f -> { fade = 1f; demoAng = a0 + (a1 - a0) * ease((phase - 0.12f) / 0.5f) }
        phase < 0.78f -> { fade = 1 - (phase - 0.62f) / 0.16f; demoAng = a1 }
        else -> {}
    }
    val demoSteps = if (hint && phase >= 0.12f && phase < 0.78f) ((demoAng - a0) / DEG_PER_STEP).roundToInt() else 0
    val shown = ((valueKg * 10).roundToInt() + demoSteps) / 10.0

    fun turn(deg: Float): Boolean {
        // На краю (0 или 200 кг) круг стоит намертво, пока тянут — частый сильный отклик
        val v = latest[0]
        if ((v <= 0.0 && deg < 0f) || (v >= 200.0 && deg > 0f)) {
            residual = 0f
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastBump[0] > 110) { lastBump[0] = now; view.bump() }
            return false
        }
        residual += deg
        var steps = 0
        while (residual >= DEG_PER_STEP) { residual -= DEG_PER_STEP; steps++ }
        while (residual <= -DEG_PER_STEP) { residual += DEG_PER_STEP; steps-- }
        if (steps == 0) return true
        val now = latest[0]
        val next = ((now * 10).roundToInt() + steps).coerceIn(0, 2000) / 10.0
        if (next == now) { residual = 0f; return false }
        latest[0] = next
        onChange(next)
        view.tick() // вибрация + щелчок
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
                    touched()
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
                    // Чем быстрее смахнули, тем дальше крутится: скорость растёт быстрее линейной, трение слабое
                    val raw = tracker.calculateVelocity().x
                    val v = (raw * (1f + abs(raw) / 900f)).coerceIn(-12000f, 12000f)
                    scope.launch {
                        if (abs(raw) > 60f) {
                            spin.snapTo(0f)
                            var prev = 0f
                            spin.animateDecay(v, exponentialDecay(frictionMultiplier = 0.9f)) {
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
            val k = this.size.minDimension / 280f // единица макета
            val c = center
            val ring = 124 * k
            drawCircle(colors.surfaceContainerHighest, radius = ring, style = Stroke(width = 18 * k))
            val rot = (valueKg * 10).toFloat() * DEG_PER_STEP + residual + (if (hint) demoAng - a0 else 0f)
            rotate(rot) {
                for (i in 0 until 30) {
                    val a = (i * DEG_PER_STEP - 90f) * (PI / 180f).toFloat()
                    if (i % 5 == 0) {
                        drawLine(
                            colors.onSurface,
                            Offset(c.x + 118 * k * cos(a), c.y + 118 * k * sin(a)),
                            Offset(c.x + 130 * k * cos(a), c.y + 130 * k * sin(a)),
                            strokeWidth = 3 * k, cap = StrokeCap.Round,
                        )
                    } else {
                        drawCircle(colors.outline, radius = 1.9f * k, center = Offset(c.x + ring * cos(a), c.y + ring * sin(a)))
                    }
                }
            }
            // «Печенька» под числом
            val cookie = Path()
            for (s in 0..240) {
                val t = s / 240f * 2f * PI.toFloat()
                val rr = (92 + 6 * cos(9 * t)) * k
                val x = c.x + rr * cos(t); val y = c.y + rr * sin(t)
                if (s == 0) cookie.moveTo(x, y) else cookie.lineTo(x, y)
            }
            cookie.close()
            drawPath(cookie, colors.primaryContainer)

            if (hint && fade > 0f) {
                // След пальца по дорожке и сам палец
                val rad = { deg: Float -> (deg - 90f) * (PI / 180f).toFloat() }
                drawArc(
                    colors.primary.copy(alpha = 0.35f * fade),
                    startAngle = a0 - 90f, sweepAngle = demoAng - a0, useCenter = false,
                    topLeft = Offset(c.x - ring, c.y - ring), size = androidx.compose.ui.geometry.Size(2 * ring, 2 * ring),
                    style = Stroke(width = 18 * k, cap = StrokeCap.Round),
                )
                val fr = rad(demoAng)
                val fx = c.x + ring * cos(fr) - 22 * k
                val fy = c.y + ring * sin(fr) - 22 * k
                val sc = 0.9f + 0.1f * fade
                scale(sc, Offset(fx + 22 * k, fy + 22 * k)) {
                    drawCircle(colors.primary.copy(alpha = 0.3f * fade), 22 * k, Offset(fx + 22 * k, fy + 22 * k))
                    drawCircle(colors.onPrimary.copy(alpha = fade), 21 * k, Offset(fx + 22 * k, fy + 22 * k), style = Stroke(2 * k))
                    translate(fx + 14 * k, fy + 12 * k) {
                        scale(40 * k / 24f, Offset.Zero) {
                            drawPath(fingerPath, colors.onPrimary.copy(alpha = fade))
                            drawPath(fingerPath, colors.onSurface.copy(alpha = fade), style = Stroke(1.4f, cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatKg(shown), fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight(750), letterSpacing = (-2).sp, color = colors.onPrimaryContainer)
                Text(" кг", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = colors.onPrimaryContainer, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(caption, fontSize = 14.sp, color = colors.onPrimaryContainer, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
