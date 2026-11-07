package app.fitfat.ui.weight

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.DayType
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.components.FfDialog
import app.fitfat.ui.components.FfSheet
import app.fitfat.ui.theme.MacroColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

private val ru = Locale("ru")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)
private val dateFmt = DateTimeFormatter.ofPattern("d MMMM", ru)

private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

/**
 * Утренний вес (макет Main, 1.1): окно 358 сверху 132, `surfaceContainerHigh`, скругление 28, поля 24 / 24 / 20.
 * Дата, «Вес натощак», круг, карточка «За неделю» со спарклайном, «Пропустить» / «Сохранить».
 */
@Composable
fun MorningWeightDialog(vm: WeightViewModel, ui: WeightUi, hint: Boolean, onSave: (Double) -> Unit, onSkip: () -> Unit) {
    val today = LocalDate.now()
    var kg by remember { mutableDoubleStateOf(ui.last?.kg ?: 70.0) }
    val yesterday = ui.before(today)
    val colors = MaterialTheme.colorScheme
    FfDialog(onSkip, top = 132.dp, width = 358.dp) {
        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, shadowElevation = 12.dp) {
            Column(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(cap(today.format(dayFmt)), fontSize = 13.sp, color = colors.onSurfaceVariant)
                    Text("Вес натощак", fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight(650))
                }
                WeightDial(
                    kg, { kg = it },
                    caption = yesterday?.let { "вчера ${formatKg(it.kg)} · ${signedKg(kg - it.kg)} кг" } ?: "крутите круг",
                    hint = hint, onTouched = vm::learned,
                )
                WeekCard(ui, today, kg)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Buttons("Пропустить", onSkip, "Сохранить") { onSave(kg) }
                }
            }
        }
    }
}

/**
 * «За неделю»: изменение от первого взвешивания за 7 дней (≤ 0 — `primary`, рост — оранжевый) и спарклайн
 * 186 × 44: точки прошлых дней, последняя — текущее значение круга. Подписи — дни недели.
 */
@Composable
private fun WeekCard(ui: WeightUi, today: LocalDate, current: Double) {
    val colors = MaterialTheme.colorScheme
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val vals: List<Double?> = days.mapIndexed { i, d -> if (i == 6) current else ui.byDay[d.toEpochDay()]?.kg }
    val first = vals.firstOrNull { it != null }
    val change = if (first != null) current - first else 0.0
    val primary = colors.primary
    val dotColor = colors.outline
    val border = colors.surfaceContainer
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("За неделю", fontSize = 12.sp, color = colors.onSurfaceVariant)
            Text("${signedKg(change)} кг", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (change <= 0.04) colors.primary else MacroColors.unknown)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Canvas(Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = "Вес за неделю" }) {
                val pts = vals.mapIndexedNotNull { i, v -> v?.let { i to it } }
                if (pts.isEmpty()) return@Canvas
                val lo = pts.minOf { it.second } - 0.1; val hi = pts.maxOf { it.second } + 0.1
                val k = size.height / 44f
                fun sx(i: Int) = 6 * k + i * (size.width - 12 * k) / 6f
                fun sy(v: Double) = (6 + (hi - v) / (hi - lo).coerceAtLeast(0.01) * 32).toFloat() * k
                val path = Path()
                pts.forEachIndexed { j, (i, v) -> if (j == 0) path.moveTo(sx(i), sy(v)) else path.lineTo(sx(i), sy(v)) }
                drawPath(path, primary, style = Stroke(2.5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
                pts.filter { it.first < 6 }.forEach { (i, v) -> drawCircle(dotColor, 2.5f * k, Offset(sx(i), sy(v))) }
                drawCircle(border, 5.5f * k, Offset(sx(6), sy(current)))
                drawCircle(primary, 4.5f * k, Offset(sx(6), sy(current)))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { Text(cap(it.dayOfWeek.getDisplayName(JTextStyle.SHORT, ru)), fontSize = 10.5.sp, color = colors.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun RowScope.Buttons(left: String, onLeft: () -> Unit, right: String, onRight: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onLeft, shape = RoundedCornerShape(28.dp), color = Color.Transparent, modifier = Modifier.weight(1f).height(56.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(left, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.primary) }
    }
    Surface(onClick = onRight, shape = RoundedCornerShape(28.dp), color = colors.primary, modifier = Modifier.weight(1.4f).height(56.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(right, fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp, color = colors.onPrimary) }
    }
}

/**
 * Правка записи веса (макет Weight-edit, 1.3): шторка `surfaceContainerLow`, поля 24. День недели и дата, корзина справа
 * (если запись есть, с подтверждением), круг «было X · ±d», отметки «Тренировка» / «Цикл» (тип дня), заметка,
 * «Отмена» / «Сохранить».
 */
@Composable
fun WeightEditSheet(
    date: LocalDate,
    ui: WeightUi,
    hint: Boolean,
    initialType: DayType,
    onLearned: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (kg: Double, note: String, type: DayType) -> Unit,
    onDelete: () -> Unit,
) {
    val entry = ui.byDay[date.toEpochDay()]
    val base = entry?.kg ?: ui.before(date)?.kg ?: ui.last?.kg ?: 70.0
    var kg by remember(date) { mutableDoubleStateOf(base) }
    var type by remember(date) { mutableStateOf(initialType) }
    var note by remember(date) { mutableStateOf(entry?.note ?: "") }
    var confirm by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    FfSheet(onDismiss = onDismiss, horizontalPadding = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cap(date.dayOfWeek.getDisplayName(JTextStyle.FULL, ru)), fontSize = 13.sp, color = colors.onSurfaceVariant)
                Text(date.format(dateFmt), fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
            }
            if (entry != null) {
                Surface(onClick = { confirm = true }, shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Delete, "Удалить запись", Modifier.size(24.dp), tint = colors.error) }
                }
            }
        }
        val caption = if (entry != null) "было ${formatKg(entry.kg)} · ${signedKg(kg - entry.kg)} кг"
        else ui.before(date)?.let { "раньше ${formatKg(it.kg)} · ${signedKg(kg - it.kg)} кг" } ?: "крутите круг"
        WeightDial(kg, { kg = it }, caption, Modifier.align(Alignment.CenterHorizontally), hint = hint, onTouched = onLearned)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarkToggle(
                "Тренировка", MockIcons.Dumbbell, type == DayType.Training, MacroColors.training, MacroColors.trainingContainer,
            ) { type = if (type == DayType.Training) DayType.Normal else DayType.Training }
            if (ui.female) {
                MarkToggle("Цикл", MockIcons.Drop, type == DayType.Cycle, MacroColors.cycle, MacroColors.cycleContainer) {
                    type = if (type == DayType.Cycle) DayType.Normal else DayType.Cycle
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Заметка", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
            BasicTextField(
                note, { note = it }, singleLine = true,
                textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh)
                    .semantics { contentDescription = "Заметка" },
                decorationBox = { inner ->
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
                        if (note.isEmpty()) Text("Например: после бани, мало спал", fontSize = 15.sp, color = colors.onSurfaceVariant)
                        inner()
                    }
                },
            )
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Buttons("Отмена", onDismiss, "Сохранить") { onSave(kg, note, type) }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Удалить запись?") },
            text = { Text("Вес за ${date.format(dateFmt)} пропадёт из графика и календаря.") },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Удалить", color = colors.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}

/** Отметка дня: 44, выкл. — `surfaceContainerHigh` скругление 12; вкл. — контейнер цвета отметки, рамка 2, скругление 22. */
@Composable
private fun RowScope.MarkToggle(label: String, icon: ImageVector, on: Boolean, ink: Color, container: Color, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (on) 22.dp else 12.dp),
        color = if (on) container else colors.surfaceContainerHigh,
        border = if (on) BorderStroke(2.dp, ink) else null,
        modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics { contentDescription = "$label, " + if (on) "отмечено" else "не отмечено" },
    ) {
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = ink)
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
