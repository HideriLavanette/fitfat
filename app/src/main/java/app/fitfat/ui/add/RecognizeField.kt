package app.fitfat.ui.add

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.fitfat.data.Product
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import app.fitfat.ui.theme.MacroColors
import kotlin.math.roundToInt

/**
 * Поле «Что съели» с подсказкой над словом, как меню выделения текста.
 * Подсказка показывается для фрагмента под курсором; остальные распознанные фрагменты подчёркнуты —
 * тап по ним переносит курсор и открывает подсказку.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognizeField(
    text: String,
    onText: (String) -> Unit,
    recognized: List<Recognized>,
    onConfirm: (Recognized, Product) -> Unit,
    onCreate: (Recognized) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    // Текст поменяли снаружи (подтверждённый фрагмент стёрт) — курсор в конец
    LaunchedEffect(text) { if (value.text != text) value = TextFieldValue(text, TextRange(text.length)) }

    var focused by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) } // левый верх текста в окне

    val cursor = value.selection.end
    val active = if (focused && value.selection.collapsed && value.text == text)
        recognized.firstOrNull { cursor in it.fragment.start..it.fragment.end } else null

    val colors = MaterialTheme.colorScheme
    val underline = remember(recognized, active, text) { UnderlineKnown(recognized.filter { it !== active && it.candidates.isNotEmpty() }, text.length) }

    val interaction = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = { value = it; if (it.text != text) onText(it.text) },
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        interactionSource = interaction,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        minLines = 2,
        visualTransformation = underline,
        onTextLayout = { layout = it },
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value.text,
                innerTextField = { Box(Modifier.onGloballyPositioned { origin = it.positionInWindow() }) { inner() } },
                enabled = true, singleLine = false,
                visualTransformation = VisualTransformation.None,
                interactionSource = interaction,
                label = { Text("Что съели") },
                placeholder = { Text("гречка 200, грудка 150, 2 яйца") },
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true, isError = false, interactionSource = interaction,
                        shape = RoundedCornerShape(16.dp),
                    )
                },
            )
        },
    )

    val l = layout
    if (active != null && l != null && active.fragment.end <= l.layoutInput.text.length) {
        val f = active.fragment
        val a = l.getBoundingBox(f.start)
        val b = l.getBoundingBox((f.end - 1).coerceAtLeast(f.start))
        val word = if (a.top == b.top) Rect(a.left, a.top, b.right, a.bottom) else a
        val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
        Popup(
            popupPositionProvider = AboveWord(word.translate(origin), gap),
            properties = PopupProperties(focusable = false), // клавиатура остаётся открытой
        ) {
            SuggestionMenu(active, onConfirm = { onConfirm(active, it) }, onCreate = { onCreate(active) })
        }
    }
}

/** Меню над словом: лучший кандидат, стрелка — остальные варианты; нераспознанное — только «Создать продукт». */
@Composable
private fun SuggestionMenu(r: Recognized, onConfirm: (Product) -> Unit, onCreate: () -> Unit) {
    var all by remember(r.fragment.query) { mutableStateOf(false) }
    val best = r.candidates.firstOrNull()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 8.dp, tonalElevation = 2.dp,
        modifier = Modifier.widthIn(max = 340.dp),
    ) {
        when {
            best == null -> Surface(onClick = onCreate, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Text("  Создать продукт", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            !all -> Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductItem(best.product, Modifier.weight(1f, fill = false)) { onConfirm(best.product) }
                if (r.candidates.size > 1) {
                    VerticalDivider(Modifier.padding(vertical = 12.dp))
                    Surface(onClick = { all = true }, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Другие варианты",
                            Modifier.padding(horizontal = 10.dp).height(56.dp).width(28.dp),
                        )
                    }
                }
            }
            else -> Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                Surface(onClick = { all = false }, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Варианты", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ExpandLess, "Свернуть", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                r.candidates.forEach { c -> ProductItem(c.product, Modifier.fillMaxWidth()) { onConfirm(c.product) } }
                HorizontalDivider()
                Surface(onClick = onCreate, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary)
                        Text("  Создать продукт", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductItem(p: Product, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier) {
        Row(
            Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryIcon(p.category, 36.dp)
            Column {
                Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    macroLine(p.protein, p.fat, p.carbs, if (p.perPiece) " · ${fmt0(p.kcal)} ккал на 1 шт" else " · ${fmt0(p.kcal)} ккал на 100 г"),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
    }
}

/** «Б 12,6 · Ж 3,3 · У 68»: буквы жирные в цвете макроса, всё в одну строку. */
@Composable
fun macroLine(p: Double, f: Double, c: Double, tail: String = ""): AnnotatedString {
    val pc = MacroColors.protein; val fc = MacroColors.fat; val cc = MacroColors.carbs
    return buildAnnotatedString {
        fun part(letter: String, color: androidx.compose.ui.graphics.Color, v: Double) {
            pushStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)); append(letter); pop()
            append(" " + fmt1(v))
        }
        part("Б", pc, p); append(" · "); part("Ж", fc, f); append(" · "); part("У", cc, c)
        append(tail)
    }
}

/** Подчёркивает распознанные фрагменты, кроме того, над которым открыта подсказка. */
private class UnderlineKnown(private val items: List<Recognized>, private val length: Int) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.length != length) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text)
            items.forEach { addStyle(SpanStyle(textDecoration = TextDecoration.Underline), it.fragment.start, it.fragment.end) }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is UnderlineKnown && other.items == items && other.length == length
    override fun hashCode() = items.hashCode() * 31 + length
}

/** Ставит меню над словом по центру; если сверху не помещается — под словом. */
private class AboveWord(private val word: Rect, private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (word.center.x - popupContentSize.width / 2f).roundToInt()
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = word.top.roundToInt() - gap - popupContentSize.height
        val y = if (above >= 0) above else word.bottom.roundToInt() + gap
        return IntOffset(x, y)
    }

    override fun equals(other: Any?) = other is AboveWord && other.word == word && other.gap == gap
    override fun hashCode() = word.hashCode() * 31 + gap
}
