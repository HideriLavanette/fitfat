package app.fitfat.ui.components

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/** Щелчок шага: вибрация + системный звук нажатия (звучит, если в системе включены звуки касания). */
fun View.tick() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    playSoundEffect(SoundEffectConstants.CLICK)
}

/** Упор в край диапазона: сильный отклик. */
fun View.bump() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    playSoundEffect(SoundEffectConstants.CLICK)
}

/**
 * Механика линеек и барабанов из макетов: палец двигает «ленту», каждые [spacing] dp — шаг значения
 * со щелчком; после отпускания — инерция (затухание 380 мс), затем доводка ленты к центру за 160 мс.
 * На краю диапазона лента не едет, отклик сильный (не чаще раза в 300 мс).
 *
 * [offset] — текущий сдвиг ленты в dp, его читает отрисовка. Направление: отрицательный сдвиг
 * (палец влево/вверх) — значение растёт.
 */
@Stable
class StepDrag internal constructor(
    private val spacing: Float,
    private val scope: CoroutineScope,
    private val view: View,
    private val step: (dir: Int) -> Boolean,
) {
    var offset by mutableFloatStateOf(0f)
        private set
    private var job: Job? = null
    private var lastBump = 0L

    private fun limit() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBump > 300) { lastBump = now; view.bump() }
    }

    /** Сдвиг на [d] dp. Возвращает false, если упёрлись в край. */
    fun push(d: Float): Boolean {
        var acc = offset + d
        while (acc <= -spacing) {
            if (!step(1)) { offset = 0f; limit(); return false }
            view.tick(); acc += spacing
        }
        while (acc >= spacing) {
            if (!step(-1)) { offset = 0f; limit(); return false }
            view.tick(); acc -= spacing
        }
        offset = acc
        return true
    }

    fun stop() { job?.cancel(); job = null }

    /** Отпустили палец со скоростью [v] dp/мс. */
    fun release(v0: Float) {
        stop()
        var v = v0.coerceIn(-3f, 3f).let { if (abs(it) < 0.35f) 0f else it }
        job = scope.launch {
            if (v != 0f) {
                var last = withFrameMillis { it }
                while (abs(v) >= 0.05f) {
                    val now = withFrameMillis { it }
                    val dt = min((now - last).toFloat(), 40f); last = now
                    if (!push(v * dt)) { v = 0f; break }
                    v *= exp(-dt / 380f)
                }
            }
            val from = offset
            val t0 = withFrameMillis { it }
            while (true) {
                val k = min(1f, (withFrameMillis { it } - t0) / 160f)
                offset = from * (1f - k).pow(3)
                if (k >= 1f) break
            }
        }
    }
}

@Composable
fun rememberStepDrag(spacing: Float, step: (dir: Int) -> Boolean): StepDrag {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val s by rememberUpdatedState(step)
    return remember(spacing) { StepDrag(spacing, scope, view) { s(it) } }
}

/**
 * Жест для [StepDrag]: горизонтальный ([horizontal] = true) или вертикальный.
 * Скорость считается по последним 90 мс, как в макете.
 */
fun Modifier.stepDrag(drag: StepDrag, horizontal: Boolean): Modifier = pointerInput(drag, horizontal) {
    val dpPx = density
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        drag.stop()
        val hist = ArrayDeque<Pair<Long, Float>>()
        var cum = 0f
        hist.addLast(SystemClock.uptimeMillis() to 0f)
        while (true) {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull() ?: break
            if (!ch.pressed) break
            val delta = ch.positionChange().let { if (horizontal) it.x else it.y } / dpPx
            if (delta != 0f) {
                ch.consume()
                cum += delta
                val now = SystemClock.uptimeMillis()
                hist.addLast(now to cum)
                while (hist.size > 2 && now - hist.first().first > 100) hist.removeFirst()
                drag.push(delta)
            }
        }
        val now = SystemClock.uptimeMillis()
        val r = hist.filter { now - it.first <= 90 }
        val v = if (r.size >= 2 && r.last().first - r.first().first >= 16)
            (r.last().second - r.first().second) / (r.last().first - r.first().first) else 0f
        drag.release(v)
    }
}
