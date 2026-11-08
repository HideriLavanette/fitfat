package app.fitfat.ui.components

import app.fitfat.platform.uptimeMillis
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import app.fitfat.data.Hints
import kotlinx.coroutines.delay
import kotlin.math.pow

/** Какой жест показывает палец. */
enum class FingerGesture {
    /** Видимый барабан: провести вверх. */
    Vertical,
    /** Чип с барабаном: нажать, затем удерживать и вести вверх. */
    TapHold,
}

/** Палец из подсказки круга веса (вьюбокс 24). */
private val fingerPath = PathParser().parsePathString(
    "M8.5 13V4.8a1.6 1.6 0 0 1 3.2 0V11l.1-1.2a1.6 1.6 0 0 1 3.2.2v1l.1-.4a1.6 1.6 0 0 1 3.1.5v.6a1.6 1.6 0 0 1 3.1.5V16a6 6 0 0 1-6 6h-1.4a6 6 0 0 1-4.7-2.3l-3.3-4.2a1.6 1.6 0 0 1 2.4-2.1Z",
).toPath()

private fun ease(x: Float) = if (x < 0.5f) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2

/**
 * Касание элемента считается его освоением ([Hints.used] по [key]) — у всех экземпляров, не только у показывающего палец.
 * Палец рисуется поверх элемента, когда [show] и жест ещё не освоен, или освоен, но на экране давно ничего не трогали.
 * Как у круга веса: появляется, показывает движение, гаснет; цикл 2,6 с.
 */
fun Modifier.fingerHint(key: String, gesture: FingerGesture, show: Boolean = false): Modifier = composed {
    val hints = remember { Hints.get() }
    val learned by hints.learned.collectAsState()
    val lastTouch by hints.lastTouch.collectAsState()
    val known = key in learned
    val shownAt = remember { uptimeMillis() }
    var now by remember { mutableLongStateOf(uptimeMillis()) }
    LaunchedEffect(show, known) { if (show && known) while (true) { delay(1000); now = uptimeMillis() } }
    val active = show && (!known || now - maxOf(lastTouch, shownAt) >= Hints.IDLE_MS)

    val track = Modifier.pointerInput(key) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.changes.any { it.pressed }) hints.used(key)
            }
        }
    }
    if (!active) return@composed track

    val colors = MaterialTheme.colorScheme
    val phase by rememberInfiniteTransition(label = "finger").animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    track.drawWithContent {
        drawContent()
        val u = density
        var fade = 0f; var dy = 0f; var press = 0f
        when (gesture) {
            FingerGesture.Vertical -> when {
                phase < 0.12f -> { fade = phase / 0.12f; dy = 22f }
                phase < 0.62f -> { fade = 1f; dy = 22f - 44f * ease((phase - 0.12f) / 0.5f) }
                phase < 0.78f -> { fade = 1 - (phase - 0.62f) / 0.16f; dy = -22f }
                else -> {}
            }
            FingerGesture.TapHold -> when {
                phase < 0.12f -> { fade = phase / 0.12f }
                phase < 0.3f -> { fade = 1f; press = ease((phase - 0.12f) / 0.18f) }
                phase < 0.62f -> { fade = 1f; press = 1f; dy = -16f * ease((phase - 0.3f) / 0.32f) }
                phase < 0.78f -> { fade = 1 - (phase - 0.62f) / 0.16f; press = 1f; dy = -16f }
                else -> {}
            }
        }
        if (fade <= 0f) return@drawWithContent
        val cx = size.width / 2f
        val cy = size.height / 2f + dy * u
        val r = 20f * u
        val sc = (0.9f + 0.1f * fade) * (1f - 0.12f * press)
        // Нажатие — расходящийся круг
        if (press > 0f && press < 1f) drawCircle(colors.primary.copy(alpha = 0.35f * (1 - press) * fade), r * (1f + press), Offset(cx, cy))
        scale(sc, Offset(cx, cy)) {
            drawCircle(colors.primary.copy(alpha = 0.3f * fade), r, Offset(cx, cy))
            drawCircle(colors.onPrimary.copy(alpha = fade), r - u, Offset(cx, cy), style = Stroke(2 * u))
            translate(cx - 8f * u, cy - 10f * u) {
                scale(36f * u / 24f, Offset.Zero) {
                    drawPath(fingerPath, colors.onPrimary.copy(alpha = fade))
                    drawPath(fingerPath, colors.onSurface.copy(alpha = fade), style = Stroke(1.4f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

/** Любое касание в окне — для повторной подсказки «застрял». Ставится на корень экрана и окон-диалогов. */
fun Modifier.trackTouches(): Modifier = composed {
    val hints = remember { Hints.get() }
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.changes.any { it.pressed }) hints.touch()
            }
        }
    }
}

/**
 * Строка со свайпом сама чуть тянется в сторону — видно, что её можно двигать: влево (и вправо, если [both]),
 * пружиной обратно, пауза 3 с. Пока [active]. [dx] — сдвиг строки в dp, тот же, что у свайпа.
 */
@Composable
fun SwipeNudge(dx: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, active: Boolean, both: Boolean) {
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        delay(900)
        while (true) {
            dx.animateTo(-34f, tween(420, easing = FastOutSlowInEasing))
            dx.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 260f))
            if (both) {
                delay(250)
                dx.animateTo(34f, tween(420, easing = FastOutSlowInEasing))
                dx.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 260f))
            }
            delay(3200)
        }
    }
}

/** Нужна ли строке подсказка-подтягивание: первая в списке и свайп ещё не пробовали. */
@Composable
fun nudgeWanted(key: String, first: Boolean): Boolean {
    if (!first) return false
    val learned by remember { Hints.get() }.learned.collectAsState()
    return key !in learned
}
