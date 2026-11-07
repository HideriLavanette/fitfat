package app.fitfat.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.fitfat.data.Product
import app.fitfat.data.portion
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import app.fitfat.ui.common.macroLine
import app.fitfat.ui.components.GramRuler
import app.fitfat.ui.components.tick
import app.fitfat.ui.components.bump
import app.fitfat.ui.theme.MacroColors
import kotlin.math.roundToInt

/**
 * Текст в карточке «Что съели» (макет Add-food): 18 sp, строка 26. Распознанное слово под курсором (или первое
 * распознанное) подсвечено `secondaryContainer`, над ним меню: продукт · стрелка «Другой вариант».
 * Слова, которых нет в базе, — оранжевый пунктир. Без веса после выбора открывается окно порции (как правка продукта).
 */
@Composable
fun RecognizeField(
    text: String,
    onText: (String) -> Unit,
    recognized: List<Recognized>,
    onAccept: (Recognized, Product) -> Unit,
    onVariants: (Recognized) -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    popupsEnabled: Boolean = true,
    placeholder: Boolean = true,
) {
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    // Текст поменяли снаружи (подтверждённый фрагмент стёрт) — курсор в конец
    LaunchedEffect(text) { if (value.text != text) value = TextFieldValue(text, TextRange(text.length)) }

    var focused by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) } // левый верх текста в окне

    val synced = value.text == text
    val cursor = value.selection.end
    val known = recognized.filter { it.candidates.isNotEmpty() }
    val active = when {
        !synced -> null
        else -> known.firstOrNull { focused && cursor in it.fragment.start..it.fragment.end } ?: known.firstOrNull()
    }
    val unknown = recognized.filter { it.candidates.isEmpty() && it.fragment.query.isNotBlank() }

    val colors = MaterialTheme.colorScheme
    val highlight = colors.secondaryContainer
    val dotted = MacroColors.unknown
    val density = LocalDensity.current

    Box(modifier) {
        if (placeholder && text.isEmpty()) {
            Text("Например: гречка 200, курица 150, огурец", fontSize = 18.sp, lineHeight = 26.sp, color = colors.onSurfaceVariant)
        }
        BasicTextField(
            value = value,
            onValueChange = { value = it; if (it.text != text) onText(it.text) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 26.dp)
                .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
                .onGloballyPositioned { origin = it.positionInWindow() }
                .drawBehind {
                    val l = layout ?: return@drawBehind
                    if (!synced) return@drawBehind
                    val pad = with(density) { 3.dp.toPx() }
                    active?.let { a ->
                        wordRects(l, a.fragment.start, a.fragment.end).forEach { r ->
                            drawRoundRect(highlight, Offset(r.left - pad, r.top), Size(r.width + 2 * pad, r.height), CornerRadius(with(density) { 6.dp.toPx() }))
                        }
                    }
                    val stroke = with(density) { 2.dp.toPx() }
                    val off = with(density) { 5.dp.toPx() }
                    val dash = PathEffect.dashPathEffect(floatArrayOf(stroke, stroke * 1.5f))
                    unknown.forEach { u ->
                        wordRects(l, u.fragment.start, u.fragment.end).forEach { r ->
                            val y = l.getLineBaseline(l.getLineForOffset(u.fragment.start).coerceAtMost(l.lineCount - 1)) + off
                            drawLine(dotted, Offset(r.left, y), Offset(r.right, y), stroke, pathEffect = dash)
                        }
                    }
                },
            textStyle = TextStyle(color = colors.onSurface, fontSize = 18.sp, lineHeight = 26.sp),
            cursorBrush = SolidColor(colors.primary),
            onTextLayout = { layout = it },
        )
    }

    val l = layout
    if (popupsEnabled && active != null && l != null && active.fragment.end <= l.layoutInput.text.length) {
        val word = wordRects(l, active.fragment.start, active.fragment.end).first()
        val gap = with(density) { 10.dp.roundToPx() }
        Popup(
            popupPositionProvider = AboveWord(word.translate(origin), gap),
            properties = PopupProperties(focusable = false), // клавиатура остаётся открытой
        ) {
            SuggestionMenu(active, onAccept = { onAccept(active, it) }, onVariants = { onVariants(active) })
        }
    }
}

/** Прямоугольники фрагмента по строкам (слово может переноситься). */
private fun wordRects(l: TextLayoutResult, start: Int, end: Int): List<Rect> {
    val len = l.layoutInput.text.length
    if (start >= len || end <= start) return listOf(l.getCursorRect(start.coerceIn(0, len)))
    val e = end.coerceAtMost(len)
    val first = l.getLineForOffset(start); val last = l.getLineForOffset(e - 1)
    return (first..last).map { line ->
        val s = if (line == first) start else l.getLineStart(line)
        val en = if (line == last) e else l.getLineEnd(line, visibleEnd = true)
        val a = l.getBoundingBox(s); val b = l.getBoundingBox((en - 1).coerceAtLeast(s))
        Rect(a.left, l.getLineTop(line), b.right, l.getLineBottom(line))
    }
}

private val menuShape = RoundedCornerShape(18.dp)

/** Меню над словом: плашка 32 + название 14 / 650 + КБЖУ на 100 г 11 sp; разделитель; стрелка 51 dp. */
@Composable
private fun SuggestionMenu(r: Recognized, onAccept: (Product) -> Unit, onVariants: () -> Unit) {
    val best = r.candidates.first().product
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.shadow(12.dp, menuShape).clip(menuShape).background(colors.surfaceContainerHighest).heightIn(min = 52.dp).widthIn(max = 358.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(onClick = { onAccept(best) }, color = Color.Transparent, modifier = Modifier.weight(1f, fill = false).heightIn(min = 52.dp)) {
            Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryIcon(best.category, 32.dp)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(best.name, fontSize = 14.sp, fontWeight = FontWeight(650), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(perLine(best), fontSize = 11.sp, color = colors.onSurfaceVariant)
                }
            }
        }
        Box(Modifier.width(1.dp).height(36.dp).background(colors.outlineVariant))
        Surface(onClick = onVariants, color = Color.Transparent, modifier = Modifier.width(51.dp).height(52.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(MockIcons.ChevronRight, "Другой вариант", Modifier.size(20.dp), tint = colors.onSurface) }
        }
    }
}

/** «110 ккал · Б 4,2 · Ж 1,1 · У 21,3 на 100 г» (или «на 1 шт»). */
@Composable
fun perLine(p: Product): AnnotatedString =
    macroLine(p.protein, p.fat, p.carbs, prefix = "${fmt0(p.kcal)} ккал · ", tail = if (p.perPiece) " на 1 шт" else " на 100 г")

/** Ставит меню над словом от его левого края (−4 dp); если сверху не помещается — под словом. */
private class AboveWord(private val word: Rect, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (word.left - gap * 0.4f).roundToInt()
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = word.top.roundToInt() - gap - popupContentSize.height
        val y = if (above >= 0) above else word.bottom.roundToInt() + gap
        return IntOffset(x, y)
    }

    override fun equals(other: Any?) = other is AboveWord && other.word == word && other.gap == gap
    override fun hashCode() = word.hashCode() * 31 + gap
}
