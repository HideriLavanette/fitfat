package app.fitfat.ui.components

import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ROW = 40f  // шаг значений по вертикали, dp
private const val R = 92f    // радиус барабана, dp

/** Значение с округлением до 0,1, чтобы шаги 0,5 не копили ошибку. */
private fun r1(v: Double) = (v * 10).roundToInt() / 10.0

/**
 * Вертикальный барабан из макетов (Claude-estimate: Б/Ж/У). Высота 180, скругление 22,
 * под выбранным — плашка 44 dp цвета [band]. Меньшие числа снизу, большие сверху (0.6.5): лента идёт за пальцем,
 * палец вниз — значение растёт.
 * Ближе к центру число плавно растёт (20→26 sp), жирнеет (400→600), светлеет до `onPrimaryContainer`;
 * к краям сжимается по вертикали и гаснет.
 */
@Composable
fun VerticalDrum(
    value: Double,
    onValueChange: (Double) -> Unit,
    min: Double,
    max: Double,
    step: Double,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    band: Color = MaterialTheme.colorScheme.secondaryContainer,
    decimals: Int = 0,
    height: Int = 180,
    /** Кручение не округляет до шага: 25,3 → 26,3 (БЖУ с дробной частью). */
    keepFraction: Boolean = false,
    /**
     * Тап по барабану переключает шаг (0.6.7): десятые (10,0 · 10,1 · 10,2…) ↔ обычный [step]. Дробная часть,
     * набранная в десятых, при возврате к целым остаётся: 10,1 → 11,1.
     */
    typeable: Boolean = false,
    /** Палец-подсказка «проведите вверх» (первый барабан в группе; DESIGN.md, «Подсказки и жесты»). */
    hint: Boolean = false,
) {
    val v by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val latest = remember { doubleArrayOf(value) }
    latest[0] = v
    var fine by remember { mutableStateOf(false) }
    val st = if (fine) 0.1 else step
    val keep = keepFraction || fine
    val dec = if (fine) 1 else decimals
    val stepNow by rememberUpdatedState(st)
    val keepNow by rememberUpdatedState(keep)
    fun next(x: Double, dir: Int) = if (keepNow) r1(x + dir * stepNow) else r1(Math.round((x + dir * stepNow) / stepNow) * stepNow)
    // Шаг ленты инвертирован: большие числа сверху, палец вниз подтягивает их к центру
    val drag = rememberStepDrag(ROW, atLimit = { d -> val dir = -d; next(latest[0], dir).let { it > max + 1e-9 || it < min - 1e-9 } }) { d ->
        val dir = -d
        val nv = next(latest[0], dir)
        if (nv > max + 1e-9 || nv < min - 1e-9) false else { latest[0] = nv; change(nv); true }
    }
    fun fmt(x: Double) = if (dec == 0) x.roundToInt().toString() else Fmt.fixed(x, dec)
    val colors = MaterialTheme.colorScheme

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = labelColor, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .fingerHint(app.fitfat.data.Hints.DRUM_COLUMN, FingerGesture.Vertical, hint)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surfaceContainer)
                .semantics { contentDescription = label ?: "Барабан"; stateDescription = fmt(value) + if (fine) ", шаг 0,1" else "" }
                .then(if (typeable) Modifier.pointerInput(Unit) { detectTapGestures(onTap = { fine = !fine; tick() }) } else Modifier)
                .stepDrag(drag, horizontal = false),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(band))
            for (k in -4..4) {
                val item = r1(value + k * st)
                if (item < min - 1e-9 || item > max + 1e-9) continue
                val y = -k * ROW + drag.offset
                val th = y / R
                if (abs(th) > 1.35f) continue
                val c = cos(th)
                val wv = max(0f, 1f - abs(y) / ROW) // 1 — точно в центре
                Text(
                    fmt(item),
                    fontSize = ((20f + 6f * wv) * (0.75f + 0.25f * c)).sp,
                    fontWeight = FontWeight((400 + 200 * wv).roundToInt()),
                    color = lerp(colors.onSurfaceVariant, colors.onPrimaryContainer, wv),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (R * sin(th)).dp)
                        .graphicsLayer {
                            alpha = max(0.15f, c.toDouble().pow(2.4).toFloat())
                            scaleY = 0.6f + 0.4f * c
                        },
                )
            }
        }
    }
}
