package app.fitfat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
 * под выбранным — плашка 44 dp цвета [band]. Палец вверх — значение растёт.
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
    /** Тап по барабану — ввести точное число с клавиатуры (до 0,1). */
    typeable: Boolean = false,
    /** Палец-подсказка «проведите вверх» (первый барабан в группе; DESIGN.md, «Подсказки и жесты»). */
    hint: Boolean = false,
) {
    val v by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val latest = remember { doubleArrayOf(value) }
    latest[0] = v
    fun next(x: Double, dir: Int) = if (keepFraction) r1(x + dir * step) else r1(Math.round((x + dir * step) / step) * step)
    val drag = rememberStepDrag(ROW, atLimit = { dir -> next(latest[0], dir).let { it > max + 1e-9 || it < min - 1e-9 } }) { dir ->
        val nv = next(latest[0], dir)
        if (nv > max + 1e-9 || nv < min - 1e-9) false else { latest[0] = nv; change(nv); true }
    }
    fun fmt(x: Double) = if (decimals == 0) x.roundToInt().toString() else "%.${decimals}f".format(java.util.Locale("ru"), x)
    val colors = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf(false) }

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
                .semantics { contentDescription = label ?: "Барабан"; stateDescription = fmt(value) }
                .then(if (typeable) Modifier.pointerInput(Unit) { detectTapGestures(onTap = { editing = true }) } else Modifier)
                .stepDrag(drag, horizontal = false),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(band))
            for (k in -4..4) {
                val item = r1(value + k * step)
                if (item < min - 1e-9 || item > max + 1e-9) continue
                val y = k * ROW + drag.offset
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
            // Ввод точного числа поверх выбранного: десятичная клавиатура, «Готово» или уход фокуса — применить
            if (editing) {
                val start = if (value % 1.0 == 0.0) value.roundToInt().toString() else "%.1f".format(java.util.Locale("ru"), value)
                var text by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
                val fr = remember { FocusRequester() }
                var focused by remember { mutableStateOf(false) }
                fun commit() {
                    if (!editing) return
                    text.text.replace(',', '.').toDoubleOrNull()?.let { change(r1(it.coerceIn(min, max))) }
                    editing = false
                }
                BasicTextField(
                    text, { t -> text = t.copy(text = t.text.filter { it.isDigit() || it == ',' || it == '.' }.take(6)) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(band)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .focusRequester(fr)
                        .onFocusChanged { if (focused && !it.isFocused) commit(); focused = it.isFocused }
                        .semantics { contentDescription = (label ?: "Значение") + ", введите число" },
                )
                LaunchedEffect(Unit) { fr.requestFocus() }
            }
        }
    }
}
