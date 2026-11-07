package app.fitfat.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.fitfat.data.Macros
import app.fitfat.data.Meal
import app.fitfat.data.Product
import app.fitfat.data.portion
import app.fitfat.ui.common.Categories
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import app.fitfat.ui.components.FfSheet
import app.fitfat.ui.components.GramRuler
import app.fitfat.ui.components.PieceStepper
import app.fitfat.ui.components.PillToggle
import app.fitfat.ui.components.VerticalDrum
import app.fitfat.ui.theme.MacroColors
import app.fitfat.ui.today.MonthCalendar
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ru = Locale("ru")

/**
 * Окно макетов поверх экрана: скрим 50 % (свой, системное затемнение выключено), карточка сверху с отступом [top].
 * [start] — отступ слева; null — по центру.
 */
@Composable
fun FfDialog(onDismiss: () -> Unit, top: Dp, width: Dp, start: Dp? = null, content: @Composable BoxScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            Box(
                Modifier.align(if (start == null) Alignment.TopCenter else Alignment.TopStart).statusBarsPadding().imePadding()
                    .padding(top = top, start = start ?: 0.dp).width(width)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                content = content,
            )
        }
    }
}

/** Карточка окна: поля 20, скругление 28, `surfaceContainerHigh`, тень. */
@Composable
private fun DialogCard(gap: Dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 12.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(gap), content = content)
    }
}

@Composable
private fun DialogHeader(p: Product, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CategoryIcon(p.category, 48.dp, radius = 16.dp, iconSize = 24.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.name, fontSize = 18.sp, fontWeight = FontWeight(650))
            Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowScope.DialogButtons(left: String, leftColor: Color, onLeft: () -> Unit, right: String, onRight: () -> Unit, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onLeft, shape = RoundedCornerShape(26.dp), color = Color.Transparent, modifier = Modifier.weight(1f).height(52.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(left, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = leftColor) }
    }
    Surface(
        onClick = onRight, enabled = enabled, shape = RoundedCornerShape(26.dp),
        color = if (enabled) colors.primary else colors.surfaceContainerHighest, modifier = Modifier.weight(1.4f).height(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(right, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) colors.onPrimary else colors.outline) }
    }
}

/**
 * Порция для частого или недавнего продукта (макет Add-empty): окно 342, сверху 210.
 * Граммы 40 / 750 и ккал 22 / 700, линейка (штуки — «−/+»), чипы Б / Ж / У, «Отмена» / «Добавить».
 */
@Composable
fun PortionDialog(p: Product, grams: Double, pieces: Double?, onDismiss: () -> Unit, onAdd: (Double, Double?) -> Unit) {
    var g by remember { mutableDoubleStateOf(grams) }
    var n by remember { mutableDoubleStateOf(pieces ?: p.defaultGrams.coerceAtLeast(1.0)) }
    val gramsNow = if (p.perPiece) (p.pieceGrams ?: 0.0) * n else g
    val m = p.portion(gramsNow, if (p.perPiece) n else null)
    val colors = MaterialTheme.colorScheme
    FfDialog(onDismiss, top = 210.dp, width = 342.dp) {
        DialogCard(16.dp) {
            DialogHeader(p, perLine(p))
            if (p.perPiece) {
                PieceStepper(n, { n = it }, p.pieceGrams ?: 0.0)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) { KcalText(m.kcal) }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(fmt0(g), fontSize = 40.sp, fontWeight = FontWeight(750), letterSpacing = (-1).sp)
                    Text(" г", fontSize = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp).weight(1f))
                    KcalText(m.kcal)
                }
                GramRuler(g.roundToInt(), { g = it.toDouble() })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MacroChip(fmt1(m.protein), MacroColors.protein, MacroColors.proteinContainer, "Белки")
                MacroChip(fmt1(m.fat), MacroColors.fat, MacroColors.fatContainer, "Жиры")
                MacroChip(fmt1(m.carbs), MacroColors.carbs, MacroColors.carbsContainer, "Углеводы")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButtons("Отмена", colors.primary, onDismiss, "Добавить", { onAdd(gramsNow, if (p.perPiece) n else null) })
            }
        }
    }
}

@Composable
private fun KcalText(kcal: Double) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(fmt0(kcal), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(" ккал", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/** Чип макроса: 36, скругление 12, число 15 / 600 + «г» 11 / 500 с прозрачностью 75 %. */
@Composable
private fun RowScope.MacroChip(value: String, ink: Color, bg: Color, label: String) {
    Row(
        Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label $value г" },
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
        Text("г", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ink.copy(alpha = 0.75f))
    }
}

/**
 * Правка подтверждённого продукта (макет Add-food, окно 350, сверху 170): порция, четыре поля ккал / Б / Ж / У —
 * правка только для этой записи. «Убрать» / «Сохранить».
 */
@Composable
fun EditPickedDialog(item: Picked, onDismiss: () -> Unit, onRemove: () -> Unit, onSave: (Double, Double?, Macros?) -> Unit) {
    val p = item.product
    var g by remember { mutableDoubleStateOf(item.grams) }
    var n by remember { mutableDoubleStateOf(item.pieces ?: 1.0) }
    val o = item.override
    var k by remember { mutableStateOf(o?.let { fmt0(it.kcal) }) }
    var pr by remember { mutableStateOf(o?.let { fmt1(it.protein) }) }
    var f by remember { mutableStateOf(o?.let { fmt1(it.fat) }) }
    var u by remember { mutableStateOf(o?.let { fmt1(it.carbs) }) }
    val gramsNow = if (p.perPiece) (p.pieceGrams ?: 0.0) * n else g
    val calc = p.portion(gramsNow, if (p.perPiece) n else null)
    val edited = listOf(k, pr, f, u).any { it != null }
    val colors = MaterialTheme.colorScheme
    fun num(s: String?) = s?.replace(',', '.')?.replace(" ", "")?.toDoubleOrNull()

    FfDialog(onDismiss, top = 170.dp, width = 350.dp) {
        DialogCard(14.dp) {
            DialogHeader(p, perLine(p))
            if (p.perPiece) {
                PieceStepper(n, { n = it }, p.pieceGrams ?: 0.0)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                        Text(fmt0(g), fontSize = 30.sp, fontWeight = FontWeight(750))
                        Text(" г", fontSize = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    GramRuler(g.roundToInt(), { g = it.toDouble() })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumCell(k ?: fmt0(calc.kcal), { k = it }, "ккал", colors.onSurface, colors.surfaceContainerHigh, "Калории")
                NumCell(pr ?: fmt1(calc.protein), { pr = it }, "г", MacroColors.protein, MacroColors.proteinContainer, "Белки")
                NumCell(f ?: fmt1(calc.fat), { f = it }, "г", MacroColors.fat, MacroColors.fatContainer, "Жиры")
                NumCell(u ?: fmt1(calc.carbs), { u = it }, "г", MacroColors.carbs, MacroColors.carbsContainer, "Углеводы")
            }
            Text(
                if (edited) "БЖУ изменены вручную только для этой записи" else "Можно поправить БЖУ только для этой записи",
                fontSize = 12.sp, color = colors.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButtons("Убрать", colors.error, onRemove, "Сохранить", {
                    val ov = if (!edited) null else Macros(
                        num(k) ?: calc.kcal, num(pr) ?: calc.protein, num(f) ?: calc.fat, num(u) ?: calc.carbs,
                    )
                    onSave(gramsNow, if (p.perPiece) n else null, ov)
                })
            }
        }
    }
}

/** Поле числа: 44, скругление 12, число 16 / 600 вправо + единица 11 с прозрачностью 75 %. */
@Composable
private fun RowScope.NumCell(value: String, onChange: (String) -> Unit, unit: String, ink: Color, bg: Color, label: String) {
    Row(
        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicTextField(
            value, { s -> onChange(s.filter { it.isDigit() || it == ',' || it == '.' }) },
            singleLine = true,
            textStyle = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End),
            cursorBrush = SolidColor(ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
        )
        Text(unit, fontSize = 11.sp, color = ink.copy(alpha = 0.75f))
    }
}

private val shortMonths = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "нояб", "дек")

/** Подпись чипа времени: «Сейчас · Обед», «Вчера, 13:40 · Обед», «4 нояб, 13:40 · Ужин». */
fun whenLabel(date: LocalDate, time: LocalTime, meal: Meal, touched: Boolean): String {
    val today = LocalDate.now()
    if (!touched && date == today) return "Сейчас · ${meal.label}"
    val day = when (date) {
        today -> ""
        today.minusDays(1) -> "Вчера, "
        else -> "${date.dayOfMonth} ${shortMonths[date.monthValue - 1]}, "
    }
    return "$day%02d:%02d · ${meal.label}".format(time.hour, time.minute)
}

/**
 * «Когда поели» (макет Add-food): шторка `surface`, промежуток 16. День — слитный переключатель,
 * «Другой день» — поле даты с календарём; время — поле 56 и «Сейчас»; приём пищи — раздельный переключатель,
 * пока не выбран руками — «· подобран по времени». «Готово».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhenSheet(vm: AddFoodViewModel, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now()
    var other by remember { mutableStateOf(vm.date != today && vm.date != today.minusDays(1)) }
    var calendar by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(false) }
    val dayIndex = when {
        other -> 2
        vm.date == today -> 0
        vm.date == today.minusDays(1) -> 1
        else -> 2
    }

    FfSheet(onDismiss = onDismiss, color = colors.surface, gap = 16.dp) {
        Text("Когда поели", fontSize = 22.sp, fontWeight = FontWeight(650))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Caption("День")
            PillToggle(listOf("Сегодня", "Вчера", "Другой день"), dayIndex, onSelect = { i ->
                when (i) {
                    0 -> { other = false; vm.changeDate(today) }
                    1 -> { other = false; vm.changeDate(today.minusDays(1)) }
                    else -> { other = true; calendar = true }
                }
            }, modifier = Modifier.fillMaxWidth())
            if (other) {
                Text("Дата", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Field(48.dp, onClick = { calendar = true }) {
                    Text(vm.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", ru)), fontSize = 16.sp)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Caption("Время")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(56.dp, Modifier.weight(1f), onClick = { clock = true }) {
                    Text("%02d:%02d".format(vm.time.hour, vm.time.minute), fontSize = 24.sp, fontWeight = FontWeight(650))
                }
                Surface(onClick = { other = false; vm.now() }, shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerHighest, modifier = Modifier.height(56.dp)) {
                    Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) { Text("Сейчас", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                buildAnnotatedString {
                    append("Приём пищи")
                    if (!vm.mealTouched) { pushStyle(SpanStyle(fontWeight = FontWeight.Normal)); append(" · подобран по времени"); pop() }
                },
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant,
            )
            PillToggle(Meal.entries.map { it.label }, vm.meal.ordinal, onSelect = { vm.selectMeal(Meal.entries[it]) }, modifier = Modifier.fillMaxWidth(), gapped = true)
        }
        Surface(onClick = onDismiss, shape = RoundedCornerShape(28.dp), color = colors.primary, modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(56.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("Готово", fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp, color = colors.onPrimary) }
        }
    }

    if (calendar) {
        MonthCalendar(
            selected = vm.date, daysWithEntries = { flowOf(emptyList()) },
            onPick = { vm.changeDate(it); calendar = false }, onDismiss = { calendar = false },
        )
    }
    if (clock) {
        val st = rememberTimePickerState(vm.time.hour, vm.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { clock = false },
            title = { Text("Время") },
            text = { TimePicker(st) },
            confirmButton = { TextButton(onClick = { vm.changeTime(LocalTime.of(st.hour, st.minute)); clock = false }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = { clock = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Caption(text: String) = Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Field(height: Dp, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.height(height)) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { content() }
    }
}

/**
 * «Какую именно?» (макет Add-variants): окно 280 слева 16, сверху 196, `surfaceContainer`, скругление 16.
 * Текущий вариант — `tertiaryContainer` с галочкой. Выбор запоминается для этого слова.
 */
@Composable
fun VariantsDialog(r: Recognized, onDismiss: () -> Unit, onPick: (Product) -> Unit, onCreate: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val word = r.fragment.query
    FfDialog(onDismiss, top = 196.dp, width = 280.dp, start = 16.dp) {
        Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainer, shadowElevation = 10.dp) {
            Column(Modifier.padding(4.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Вы написали «${r.fragment.raw.trim().trimEnd(',')}»", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Text("Какую именно?", fontSize = 16.sp, fontWeight = FontWeight(650))
                }
                r.candidates.forEachIndexed { i, c ->
                    val sel = i == 0
                    val p = c.product
                    Surface(
                        onClick = { onPick(p) }, shape = RoundedCornerShape(12.dp),
                        color = if (sel) colors.tertiaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CategoryIcon(p.category, 36.dp, radius = 12.dp, iconSize = 18.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                val ink = if (sel) colors.onTertiaryContainer else colors.onSurface
                                Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink)
                                Text("${fmt0(p.kcal)} ккал · Б ${fmt1(p.protein)} · Ж ${fmt1(p.fat)} · ${usedLabel(p.useCount)}", fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                            if (sel) Icon(MockIcons.Check, "Выбрано", Modifier.size(20.dp), tint = colors.tertiary)
                        }
                    }
                }
                Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(colors.outlineVariant))
                Surface(onClick = onCreate, shape = RoundedCornerShape(14.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(MockIcons.Plus, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                        Text("Создать новый продукт", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Text(
                    "Выбор запомнится: в следующий раз «$word» сразу станет этим продуктом.",
                    fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp),
                )
            }
        }
    }
}


private fun usedLabel(n: Int): String = when {
    n <= 0 -> "база"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "выбирали $n раза"
    else -> "выбирали $n раз"
}

/**
 * «Новый продукт» (макет Claude-estimate): шторка `surfaceContainerLow`. Название, категория, Б / Ж / У барабанами
 * на 100 г или на 1 шт, ккал считаются из БЖУ. Внизу — сколько уйдёт в эту запись. «Сохранить и добавить».
 */
@Composable
fun NewProductSheet(from: Recognized?, onDismiss: () -> Unit, onSave: (Product, grams: Double?, pieces: Double?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val initial = from?.fragment?.query?.replaceFirstChar { it.uppercase() } ?: ""
    var name by remember { mutableStateOf(initial) }
    val cats = remember { Categories.sortedBy { if (it.id == "sauce") 0 else 1 } }
    var cat by remember { mutableStateOf(cats.first().id) }
    var piece by remember { mutableStateOf(from?.fragment?.pieces != null) }
    var p by remember { mutableDoubleStateOf(0.0) }
    var f by remember { mutableDoubleStateOf(0.0) }
    var u by remember { mutableDoubleStateOf(0.0) }
    var pieceG by remember { mutableStateOf("") }
    val kcal = (p * 4 + f * 9 + u * 4).roundToInt()
    val entryG = from?.fragment?.grams ?: 100.0
    val entryN = from?.fragment?.pieces ?: 1.0
    val entryK = if (piece) kcal * entryN else kcal * entryG / 100
    val pg = pieceG.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && (!piece || (pg != null && pg > 0))

    FfSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Новый продукт", fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (from != null) Text("из «${from.fragment.raw.trim().trimEnd(',')}»", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        BasicTextField(
            name, { name = it }, singleLine = true,
            textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp),
            cursorBrush = SolidColor(colors.primary),
            modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(4.dp)).background(colors.surface)
                .border(2.dp, colors.primary, RoundedCornerShape(4.dp)).semantics { contentDescription = "Название" },
            decorationBox = { inner -> Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) { inner() } },
        )
        // Категории прокручиваются от края до края шторки
        Row(
            Modifier.bleed(20.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cats.forEach { c ->
                val sel = c.id == cat
                Surface(
                    onClick = { cat = c.id }, shape = RoundedCornerShape(8.dp),
                    color = if (sel) c.bg else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (sel) colors.primary else colors.outlineVariant),
                    modifier = Modifier.height(32.dp),
                ) {
                    Row(Modifier.padding(start = 8.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(c.icon, null, Modifier.size(16.dp), tint = colors.onSurface)
                        Text(c.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(fmt0(kcal.toDouble()), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("ккал " + if (piece) "на 1 шт" else "на 100 г", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
            PillToggle(listOf("100 г", "1 шт"), if (piece) 1 else 0, onSelect = { piece = it == 1 }, height = 36.dp, fontSize = 13.sp, fill = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerticalDrum(p, { p = it }, 0.0, 100.0, 1.0, Modifier.weight(1f), label = "Белки, г", labelColor = MacroColors.protein, band = MacroColors.proteinContainer)
            VerticalDrum(f, { f = it }, 0.0, 100.0, 1.0, Modifier.weight(1f), label = "Жиры, г", labelColor = MacroColors.fat, band = MacroColors.fatContainer)
            VerticalDrum(u, { u = it }, 0.0, 100.0, 1.0, Modifier.weight(1f), label = "Углеводы, г", labelColor = MacroColors.carbs, band = MacroColors.carbsContainer)
        }
        if (piece) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Вес одной штуки", fontSize = 14.sp, modifier = Modifier.weight(1f))
                BasicTextField(
                    pieceG, { s -> pieceG = s.filter { it.isDigit() || it == ',' || it == '.' } }, singleLine = true,
                    textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp, textAlign = TextAlign.End),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(64.dp).height(40.dp).clip(RoundedCornerShape(4.dp)).background(colors.surface)
                        .border(1.dp, colors.outline, RoundedCornerShape(4.dp)).semantics { contentDescription = "Вес одной штуки, граммы" },
                    decorationBox = { inner -> Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) { inner() } },
                )
                Text("г", fontSize = 14.sp, color = colors.onSurfaceVariant)
            }
        }
        Row(Modifier.padding(horizontal = 4.dp)) {
            Text("В эту запись: " + if (piece) "${fmt1(entryN)} шт" else "${fmt0(entryG)} г", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${fmt0(entryK)} ккал", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = onDismiss, shape = RoundedCornerShape(28.dp), color = Color.Transparent, modifier = Modifier.weight(1f).height(56.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Отмена", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.primary) }
            }
            Surface(
                onClick = {
                    val n = name.trim()
                    val aliases = listOfNotNull(n.lowercase(), from?.fragment?.query).distinct().joinToString(",")
                    onSave(
                        Product(
                            name = n, category = cat, kcal = kcal.toDouble(), protein = p, fat = f, carbs = u,
                            perPiece = piece, pieceGrams = if (piece) pg else null, aliases = aliases,
                            defaultGrams = if (piece) 1.0 else 100.0,
                        ),
                        if (piece) null else entryG, if (piece) entryN else null,
                    )
                },
                enabled = valid, shape = RoundedCornerShape(28.dp),
                color = if (valid) colors.primary else colors.surfaceContainerHighest, modifier = Modifier.weight(1.5f).height(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Сохранить и добавить", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (valid) colors.onPrimary else colors.outline)
                }
            }
        }
    }
}

/** Расширяет элемент на [by] за поля родителя с обеих сторон. */
private fun Modifier.bleed(by: Dp) = layout { m, c ->
    val extra = (by * 2).roundToPx()
    val pl = m.measure(c.copy(minWidth = c.minWidth + extra, maxWidth = c.maxWidth + extra))
    layout(c.maxWidth, pl.height) { pl.place(-extra / 2, 0) }
}
