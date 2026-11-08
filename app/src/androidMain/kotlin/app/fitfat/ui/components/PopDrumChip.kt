package app.fitfat.ui.components

import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.fitfat.ui.common.MockIcons
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ROW = 32f   // шаг строк барабана, dp
private const val R = 92f     // радиус «цилиндра», dp
private const val DRUM_H = 232
private const val BAND_TOP = 94 // плашка выбранного совпадает с чипом
private const val SIDE = 96     // место под бабл с каждой стороны

/**
 * Чип значения с всплывающим барабаном (макет Goals, чипы Б / Ж / У нормы).
 * Барабан — это сам чип, выросший в высоту до 232 (тот же цвет и ширина). Тап — остаётся открытым, внизу «✓ Готово»; удержание 300 мс — барабан на время
 * удержания: ведите вверх-вниз, отпустили — закрылся. Сбоку бабл с выбранным значением.
 * [value] — что показывает чип; [onTarget] получает число с барабана (шаг [step]) в пределах [min]..[max].
 */
@Composable
fun RowScope.PopDrumChip(
    value: Double,
    onTarget: (Double) -> Unit,
    unit: String,
    ink: Color,
    bg: Color,
    label: String,
    weight: Float = 1f,
    bubbleRight: Boolean = false,
    stepFor: (Double) -> Double = { if (it < 10) 0.5 else 1.0 },
    /** Как показывать значение в закрытом чипе. */
    display: (Double) -> String = { Fmt.f1(it) },
    min: Double = 0.0,
    max: Double = Double.MAX_VALUE,
    /** Палец-подсказка «нажмите или удерживайте и ведите» (первый чип в группе; DESIGN.md, «Подсказки и жесты»). */
    hint: Boolean = false,
) {
    val view = LocalView.current
    val density = LocalDensity.current.density
    var open by remember { mutableStateOf(false) }
    var sticky by remember { mutableStateOf(false) }
    var drumVal by remember { mutableDoubleStateOf(0.0) }
    var step by remember { mutableDoubleStateOf(1.0) }
    val target by rememberUpdatedState(onTarget)
    val current by rememberUpdatedState(value)
    val drag = rememberStepDrag(ROW, atLimit = { dir -> (((drumVal + dir * step) / step).roundToInt() * step).let { it < min - 1e-9 || it > max + 1e-9 } }) { dir ->
        val nv = ((drumVal + dir * step) / step).roundToInt() * step
        if (nv < min - 1e-9 || nv > max + 1e-9) false else { drumVal = nv; target(nv); true }
    }
    // Раскрытие из чипа: 0 — барабан размером и цветом чипа, 1 — полный барабан
    val expand = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var chipW by remember { mutableFloatStateOf(72f) }
    fun openDrum(stick: Boolean) {
        step = stepFor(current)
        drumVal = (current / step).roundToInt() * step
        sticky = stick; open = true
        scope.launch { expand.snapTo(0f); expand.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 520f)) }
    }
    fun closeDrum() {
        scope.launch { expand.animateTo(0f, tween(150)); open = false }
    }
    fun fmt(v: Double) = if (step < 1) Fmt.f1(v) else v.roundToInt().toString()

    Box(Modifier.weight(weight)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .fingerHint(app.fitfat.data.Hints.DRUM_CHIP, FingerGesture.TapHold, hint)
                .onSizeChanged { chipW = it.width / density }.clip(RoundedCornerShape(12.dp)).background(bg)
                .semantics(mergeDescendants = true) { contentDescription = "$label: ${display(value)} $unit. Нажмите, чтобы изменить" }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val up = withTimeoutOrNull(300) { waitForUpOrCancellation() }
                        if (up != null) { openDrum(stick = true); return@awaitEachGesture } // тап — до «Готово»
                        // Удержание: барабан открыт, пока палец на экране
                        openDrum(stick = false)
                        view.tick()
                        var moved = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            val dy = ch.positionChange().y / density
                            if (dy != 0f) { ch.consume(); if (abs(dy) > 0.5f) moved = true; drag.push(dy) }
                        }
                        drag.release(0f)
                        if (moved) closeDrum() else sticky = true
                    }
                }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End),
        ) {
            Text(display(value), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
            Text(unit, fontSize = 11.sp, color = ink.copy(alpha = 0.75f), maxLines = 1)
        }

        if (open) {
            val colors = MaterialTheme.colorScheme
            Popup(
                popupPositionProvider = remember(bubbleRight, density) { OverChip(bubbleRight, density) },
                onDismissRequest = { closeDrum() },
                properties = PopupProperties(focusable = sticky, clippingEnabled = false),
            ) {
                val t = expand.value.coerceIn(0f, 1.05f)
                val tc = t.coerceAtMost(1f)
                // Барабан строго над чипом и той же ширины; место под бабл — только с одной стороны
                Box(Modifier.width((chipW + SIDE).dp)) {
                    Column(
                        Modifier.align(if (bubbleRight) Alignment.TopStart else Alignment.TopEnd).width(chipW.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(DRUM_H.dp)
                                .semantics { contentDescription = label; stateDescription = "${fmt(drumVal)} $unit" }
                                .then(if (sticky) Modifier.stepDrag(drag, horizontal = false) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Сам чип растёт в высоту: тот же цвет и ширина, скругление 12 -> 36,
                            // соседние числа проявляются за его краями
                            val shape = RoundedCornerShape((12f + 24f * tc).dp)
                            Box(
                                Modifier.fillMaxWidth().height((44f + (DRUM_H - 44f) * t).dp)
                                    .shadow((12f * tc).dp, shape).clip(shape).background(bg),
                                contentAlignment = Alignment.Center,
                            ) {
                                // Плашка выбранного проявляется по мере раскрытия
                                Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp)).background(ink.copy(alpha = 0.14f * tc)))
                                for (k in -4..4) {
                                    val v = drumVal + k * step
                                    if (v < min - 1e-9 || v > max + 1e-9) continue
                                    val y = k * ROW + drag.offset
                                    val th = y / R
                                    if (abs(th) > 1.3f) continue
                                    val c = cos(th)
                                    val wv = max(0f, 1f - abs(y) / ROW)
                                    Text(
                                        fmt(v),
                                        fontSize = ((15f + 4f * wv) * (0.8f + 0.2f * c)).sp,
                                        fontWeight = FontWeight((400 + 300 * wv).roundToInt()),
                                        color = lerp(ink.copy(alpha = 0.6f), ink, wv),
                                        textAlign = TextAlign.Center, maxLines = 1,
                                        modifier = Modifier.fillMaxWidth().height(28.dp)
                                            .offset(y = (R * sin(th)).dp)
                                            .graphicsLayer { alpha = max(0.15f, c.toDouble().pow(2.2).toFloat()); scaleY = 0.6f + 0.4f * c },
                                    )
                                }
                            }
                        }
                        if (sticky) {
                            Surface(onClick = { closeDrum() }, shape = CircleShape, color = colors.primary, shadowElevation = 6.dp,
                                modifier = Modifier.size(48.dp).graphicsLayer { alpha = tc; scaleX = 0.6f + 0.4f * tc; scaleY = 0.6f + 0.4f * tc }) {
                                Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Check, "Готово", Modifier.size(22.dp), tint = colors.onPrimary) }
                            }
                        }
                    }
                    // Бабл с выбранным значением — сбоку от барабана, на уровне чипа
                    Box(
                        Modifier.align(if (bubbleRight) Alignment.TopEnd else Alignment.TopStart).padding(top = BAND_TOP.dp).width((SIDE - 10).dp).height(44.dp),
                        contentAlignment = if (bubbleRight) Alignment.CenterStart else Alignment.CenterEnd,
                    ) {
                        Box(
                            Modifier.graphicsLayer { alpha = tc; scaleX = 0.8f + 0.2f * tc; scaleY = 0.8f + 0.2f * tc }
                                .shadow(8.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(colors.inverseSurface)
                                .heightIn(min = 44.dp).padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${fmt(drumVal)} $unit", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.inverseOnSurface, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Центр барабана — над центром чипа, плашка выбранного — на уровне чипа; бабл слева или справа. */
private class OverChip(private val bubbleRight: Boolean, private val density: Float) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val half = (popupContentSize.width - (SIDE * density).roundToInt()) / 2 // половина ширины барабана = чипа
        val cx = anchorBounds.center.x
        val x = if (bubbleRight) cx - half else cx - popupContentSize.width + half
        return IntOffset(x, anchorBounds.top - (BAND_TOP * density).roundToInt())
    }
}
