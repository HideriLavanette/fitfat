package app.fitfat.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitfat.data.DayType
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.components.PillToggle
import app.fitfat.ui.components.tick
import app.fitfat.ui.theme.MacroColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val ru = Locale("ru")
private val shortMonths = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "нояб", "дек")
private val rangeFmt = DateTimeFormatter.ofPattern("d MMMM", ru)

/**
 * Вкладка «Вес» (макет Weight-history, 1.2). Поля 12 / 16, промежуток 16. Стрелки «Назад» нет — это вкладка навбара.
 * Текущий вес 48 / 750, тренд и изменение за период; переключатель периода; график с прогнозом
 * (удерживать и вести — смена периода); календарь 5 недель: вес, пропуски «—», отметки тренировки и цикла.
 * Тап по дню — сводка дня (вес, КБЖУ, тип, еда), из неё — правка веса (1.3) или главный экран на эту дату.
 */
@Composable
fun WeightScreen(vm: WeightViewModel, onOpenDay: (LocalDate) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val hint by vm.hint.collectAsStateWithLifecycle()
    var viewing by remember { mutableStateOf<LocalDate?>(null) } // сводка дня
    var editing by remember { mutableStateOf<LocalDate?>(null) }
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 200.dp), // выше защитной зоны навбара
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.height(56.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Вес", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val last = ui.last
            if (last == null) {
                Text("Пока нет записей", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text("Взвешивайтесь утром натощак — приложение само спросит вес.", fontSize = 14.sp, color = colors.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(formatKg(last.kg), fontSize = 48.sp, lineHeight = 48.sp, fontWeight = FontWeight(750), letterSpacing = (-1.5).sp)
                    Text("кг", fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(
                    "тренд ${formatKg(last.trend)} кг" + (ui.periodChange?.let { " · ${signedKg(it)} кг за период" } ?: ""),
                    fontSize = 14.sp, color = colors.onSurfaceVariant,
                )
            }
        }
        PillToggle(Period.entries.map { it.label }, ui.period.ordinal, onSelect = { vm.setPeriod(Period.entries[it]) }, modifier = Modifier.fillMaxWidth())
        WeightChart(ui) { vm.setPeriod(it) }
        Text(
            "Удерживайте график и ведите пальцем, чтобы сменить период",
            fontSize = 12.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().pullUp(6),
        )
        WeightCalendar(ui, vm::dayType) { viewing = it }
    }

    viewing?.let { d ->
        DaySummarySheet(
            d, ui, vm, onDismiss = { viewing = null },
            onEditWeight = { viewing = null; editing = d },
            onOpenDay = { viewing = null; onOpenDay(d) },
        )
    }
    editing?.let { d ->
        WeightEditSheet(
            date = d, ui = ui, hint = hint, initialType = vm.dayType(d), onLearned = vm::learned,
            onDismiss = { editing = null },
            onSave = { kg, note, type -> vm.saveDay(d, kg, note, type); editing = null },
            onDelete = { vm.delete(d); editing = null },
        )
    }
}

/** Отрицательный отступ сверху из макета (margin −6 dp). */
private fun Modifier.pullUp(dp: Int) = layout { m, c ->
    val p = m.measure(c)
    val up = (dp * density).roundToInt()
    layout(p.width, max(0, p.height - up)) { p.place(0, -up) }
}

/**
 * График: карточка 24 `surfaceContainer`, поля 16 / 12 / 12; область 190 (слева 36 под подписи, снизу 26).
 * Сетка, точки взвешиваний `outline`, тренд 3 `primary`, прогноз пунктиром на 20 % периода по наклону тренда за неделю,
 * последняя точка 5 с обводкой. Подписи: три значения слева, дата начала и «сегодня» снизу. Легенда под графиком.
 */
@Composable
private fun WeightChart(ui: WeightUi, onPeriod: (Period) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val view = LocalView.current
    val current by rememberUpdatedState(ui.period)
    val stepPx = with(LocalDensity.current) { 40.dp.toPx() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer)
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val pts = ui.visible
        Box(
            Modifier.fillMaxWidth().height(190.dp)
                .semantics { contentDescription = "График веса за период ${ui.period.label}" }
                .pointerInput(Unit) {
                    var acc = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = { acc = 0f; view.tick() },
                        onDrag = { ch, d ->
                            ch.consume()
                            acc += d.x
                            while (abs(acc) >= stepPx) {
                                val dir = if (acc > 0) 1 else -1
                                acc -= dir * stepPx
                                val i = (current.ordinal + dir).coerceIn(0, Period.entries.lastIndex)
                                if (i != current.ordinal) { onPeriod(Period.entries[i]); view.tick() }
                            }
                        },
                    )
                },
        ) {
            if (pts.size < 2) {
                Text("График появится после двух взвешиваний", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.Center))
            } else {
                val grid = colors.surfaceContainerHighest
                val dotC = colors.outline
                val primary = colors.primary
                val bg = colors.surfaceContainer
                val label = TextStyle(fontSize = 11.sp, color = colors.onSurfaceVariant)
                Canvas(Modifier.fillMaxSize()) {
                    val k = density
                    val l = 36 * k; val r = 8 * k; val t = 8 * k; val b = 26 * k
                    val today = LocalDate.now()
                    val n = ui.period.days.toInt()
                    val from = today.minusDays((n - 1).toLong())
                    val fDays = max(2, (n * 0.2).roundToInt())
                    val last = pts.last()
                    // Наклон тренда за последнюю неделю
                    val weekAgo = ui.all.lastOrNull { !it.date.isAfter(last.date.minusDays(7)) } ?: ui.all.first()
                    val span = ChronoUnit.DAYS.between(weekAgo.date, last.date).coerceAtLeast(1)
                    val slope = (last.trend - weekAgo.trend) / span
                    val lastIdx = ChronoUnit.DAYS.between(from, last.date).toInt()
                    val fEnd = last.trend + slope * (n - 1 + fDays - lastIdx)
                    val all = pts.flatMap { listOf(it.kg, it.trend) } + fEnd
                    val lo = all.min() - 0.2; val hi = all.max() + 0.2
                    fun x(j: Int) = l + j / (n - 1f + fDays) * (size.width - l - r)
                    fun y(v: Double) = (t + (hi - v) / (hi - lo) * (size.height - t - b)).toFloat()
                    fun j(d: LocalDate) = ChronoUnit.DAYS.between(from, d).toInt()

                    val ticks = listOf(hi - 0.2, (hi + lo) / 2, lo + 0.2)
                    ticks.forEach { v ->
                        drawLine(grid, Offset(l, y(v)), Offset(size.width - r, y(v)), strokeWidth = 1 * k)
                        val tl = measurer.measure(formatKg(v), label)
                        drawText(tl, topLeft = Offset(30 * k - tl.size.width, y(v) - tl.size.height / 2f))
                    }
                    // Прогноз
                    drawLine(
                        primary, Offset(x(lastIdx), y(last.trend)), Offset(x(n - 1 + fDays), y(fEnd)), strokeWidth = 2 * k,
                        cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * k, 5 * k)),
                    )
                    val rad = (if (n > 100) 1.3f else if (n > 40) 2f else 3.2f) * k
                    pts.forEach { drawCircle(dotC, rad, Offset(x(j(it.date)), y(it.kg))) }
                    val path = Path()
                    pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(j(p.date)), y(p.trend)) else path.lineTo(x(j(p.date)), y(p.trend)) }
                    drawPath(path, primary, style = Stroke(3 * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(bg, 6.5f * k, Offset(x(lastIdx), y(last.trend)))
                    drawCircle(primary, 5 * k, Offset(x(lastIdx), y(last.trend)))
                    // Подписи оси X
                    val start = measurer.measure("${from.dayOfMonth} ${shortMonths[from.monthValue - 1]}", label)
                    drawText(start, topLeft = Offset(l, size.height - 16 * k))
                    val end = measurer.measure("сегодня", label)
                    drawText(end, topLeft = Offset(x(n - 1) - 44 * k, size.height - 16 * k))
                }
            }
        }
        Row(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend("тренд") { Box(Modifier.width(14.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.primary)) }
            Legend("взвешивания") { Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.outline)) }
            Legend("прогноз") {
                Canvas(Modifier.width(14.dp).height(2.dp)) {
                    drawLine(colors.primary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
                }
            }
        }
    }
}

@Composable
private fun Legend(text: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mark()
        Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Календарь веса: карточка 24 `surfaceContainerLow`, поля 14 / 12 / 12; 5 недель, последняя — текущая.
 * Ячейка 54, скругление 12: число 11 (первое число месяца — «1 нояб»), вес 13 / 650 или «—», полоска тренировки 18 × 4.
 * Сегодня — рамка 2 `primary`, день цикла — свой фон и рамка, будущее пустое и не нажимается.
 */
@Composable
private fun WeightCalendar(ui: WeightUi, dayType: (LocalDate) -> DayType, onDay: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val today = LocalDate.now()
    var page by remember { mutableIntStateOf(0) } // 0 — последние 5 недель, 1 — ещё раньше
    val endOfWeek = today.plusDays((7 - today.dayOfWeek.value).toLong()).minusWeeks(5L * page)
    val start = endOfWeek.minusDays(34)
    // Отметки перечитываются при смене типа дня
    val marks = remember(start, ui.marksVersion) { (0..34).associate { start.plusDays(it.toLong()) to dayType(start.plusDays(it.toLong())) } }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerLow)
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${start.format(rangeFmt)} — ${endOfWeek.format(rangeFmt)}", fontSize = 16.sp, fontWeight = FontWeight(650), modifier = Modifier.weight(1f))
            IconButton(onClick = { page++ }, modifier = Modifier.size(40.dp)) {
                Icon(MockIcons.ChevronLeft, "Раньше", Modifier.size(20.dp), tint = colors.onSurface)
            }
            IconButton(onClick = { if (page > 0) page-- }, enabled = page > 0, modifier = Modifier.size(40.dp)) {
                Icon(MockIcons.ChevronRight, "Позже", Modifier.size(20.dp), tint = if (page > 0) colors.onSurface else colors.surfaceContainerHighest)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                Text(it, fontSize = 11.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        for (w in 0 until 5) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (dIdx in 0 until 7) {
                    val d = start.plusDays((w * 7 + dIdx).toLong())
                    DayCell(d, today, ui.byDay[d.toEpochDay()], marks[d] ?: DayType.Normal, tracked = ui.all.firstOrNull()?.date?.let { !d.isBefore(it) } ?: false, modifier = Modifier.weight(1f)) { onDay(d) }
                }
            }
        }
        Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend("тренировка") { Box(Modifier.width(14.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MacroColors.training)) }
            if (ui.female) {
                Legend("цикл") {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(MacroColors.cycleContainer).border(1.dp, MacroColors.cycle, RoundedCornerShape(4.dp)))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("—", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text("пропуск", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** [tracked] — день не раньше первого взвешивания: только тогда пустой день считается пропуском. */
@Composable
private fun DayCell(d: LocalDate, today: LocalDate, p: WeightPoint?, type: DayType, tracked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val future = d.isAfter(today)
    val isToday = d == today
    val cyc = type == DayType.Cycle && !future
    val train = type == DayType.Training && !future
    val first = d.dayOfMonth == 1
    val shape = RoundedCornerShape(12.dp)
    val w = when {
        future -> ""
        p != null -> formatKg(p.kg)
        isToday || !tracked -> ""
        else -> "—"
    }
    val aria = "${d.dayOfMonth} ${shortMonths[d.monthValue - 1]}: " + when {
        future || !tracked -> "нет данных"; p != null -> "$w кг"; isToday -> "не взвешивались"; else -> "пропуск"
    } + (if (train) ", тренировка" else "") + (if (cyc) ", цикл" else "")
    Column(
        modifier.heightIn(min = 54.dp).clip(shape)
            .background(if (cyc) MacroColors.cycleContainer else if (future) Color.Transparent else colors.surfaceContainer)
            .border(if (isToday) 2.dp else 1.dp, if (isToday) colors.primary else if (cyc) MacroColors.cycleBorder else Color.Transparent, shape)
            .clickable(enabled = !future, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = aria }
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            if (first) "1 ${shortMonths[d.monthValue - 1]}" else d.dayOfMonth.toString(),
            fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1,
            fontWeight = if (first || isToday) FontWeight.Bold else FontWeight.Medium,
            color = if (cyc) MacroColors.cycleInk else if (future) colors.outline else colors.onSurfaceVariant,
        )
        Text(w, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight(650), maxLines = 1, color = if (p == null) colors.outline else colors.onSurface)
        Spacer(Modifier.padding(top = 1.dp))
        Box(Modifier.width(18.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (train) MacroColors.training else Color.Transparent))
    }
}
