package app.fitfat.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitfat.data.DayType
import app.fitfat.data.FoodEntry
import app.fitfat.data.Meal
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.category
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import app.fitfat.ui.common.fmtF1
import app.fitfat.ui.components.AddFab
import app.fitfat.ui.components.PillToggle
import app.fitfat.ui.components.WavyBar
import app.fitfat.ui.components.WavyRing
import app.fitfat.ui.components.showUndo
import app.fitfat.ui.theme.MacroColors
import app.fitfat.ui.weight.formatKg
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

private val ru = Locale("ru")
private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)
private fun hhmm(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

/**
 * Главный экран (макет `Today.dc.html`, 2.1–2.3). Отступы корня 12 / 16, промежуток 16;
 * отрицательные отступы макета (−4, −8, −6) учтены в верхних отступах элементов.
 */
@Composable
fun TodayScreen(vm: TodayViewModel, weightKg: Double?, onWeight: () -> Unit, onAdd: (LocalDate) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val deleted by vm.lastDeleted.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Pair<FoodEntry, Boolean>?>(null) } // запись, режим повтора
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        val e = deleted ?: return@LaunchedEffect
        if (snack.showUndo("«${e.name}» удалён")) vm.undoDelete() else vm.clearUndo()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            // Снизу: 120 по макету + 80 под FAB, чтобы последнюю запись можно было прокрутить выше него
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 200.dp),
        ) {
            item(key = "head") { Header(ui.date, weightKg, onSelect = vm::setDate, onPick = { picking = true }, onWeight = onWeight, dayType = ui.dayType) }
            item(key = "types") {
                DayTypes(ui, onPick = vm::setDayType, modifier = Modifier.padding(top = 12.dp))
            }
            if (ui.dayType != DayType.Normal) item(key = "note") { DayNote(ui, Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)) }
            item(key = "card") { ProgressCard(ui, Modifier.padding(top = 16.dp)) }
            item(key = "diary") {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Дневник", fontSize = 20.sp, fontWeight = FontWeight(650), modifier = Modifier.weight(1f).padding(start = 8.dp))
                    PillToggle(
                        options = listOf("Приёмы", "Время"),
                        selected = if (ui.groupByTime) 1 else 0,
                        onSelect = { vm.setGroupByTime(it == 1) },
                        height = 36.dp, fontSize = 13.sp, fill = false,
                    )
                }
            }
            if (ui.entries.isEmpty()) item(key = "empty") { EmptyDay(Modifier.padding(top = 10.dp)) }
            // Группы: новые сверху. Записи уже отсортированы по времени по убыванию.
            val byMeal = !ui.groupByTime
            val groups = ui.entries.groupBy { if (byMeal) Meal.of(it.meal).label else hhmm(it.minute) }
            groups.entries.forEachIndexed { gi, (title, list) ->
                item(key = "g_$title") {
                    MealSection(
                        title = title,
                        sub = if (byMeal) "" else "  ·  " + Meal.of(list.first().meal).label,
                        list = list, showTime = byMeal,
                        onTap = { editing = it to false },
                        onRepeat = { editing = it to true },
                        onDelete = vm::delete,
                        modifier = Modifier.padding(top = if (gi == 0) 10.dp else 16.dp),
                    )
                }
            }
        }

        AddFab(
            onClick = { onAdd(ui.date) },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 104.dp),
        )
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 196.dp))
    }

    editing?.let { (e, repeatMode) ->
        EntrySheet(
            entry = e, repeatMode = repeatMode, date = ui.date,
            onDismiss = { editing = null },
            onSave = { g, pcs -> if (repeatMode) vm.repeat(e, g, pcs) else vm.changePortion(e, g, pcs); editing = null },
            onDelete = { vm.delete(e); editing = null },
            onSwitchToRepeat = { editing = e to true },
        )
    }

    if (picking) {
        MonthCalendar(
            selected = ui.date,
            daysWithEntries = { vm.daysWithEntries(it) },
            onPick = { vm.setDate(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

/** Капсула даты (пейджер — решение автора) + чип веса справа. Высота строки 60, промежуток 10. */
@Composable
private fun Header(date: LocalDate, weightKg: Double?, onSelect: (LocalDate) -> Unit, onPick: () -> Unit, onWeight: () -> Unit, dayType: DayType) {
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DateCapsule(date, dayType, onSelect, onPick, Modifier.weight(1f))
        if (weightKg != null) {
            Surface(onClick = onWeight, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.height(40.dp)) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text("${formatKg(weightKg)} кг", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

private fun typeColor(t: DayType, secondary: Color, training: Color, cycle: Color) = when (t) {
    DayType.Normal -> secondary
    DayType.Training -> training
    DayType.Cycle -> cycle
}

/**
 * Капсула даты: 56 dp, скругление 28, `surfaceContainer`. Дни — страницы пейджера: последняя — сегодня,
 * левее — прошлое; свайп вправо — назад, влево — вперёд, не дальше сегодня. Тап — календарь.
 */
@Composable
private fun DateCapsule(date: LocalDate, dayType: DayType, onSelect: (LocalDate) -> Unit, onPick: () -> Unit, modifier: Modifier) {
    val today = remember { LocalDate.now() }
    fun pageOf(d: LocalDate) = (DAY_PAGES - 1 - ChronoUnit.DAYS.between(d, today)).toInt().coerceIn(0, DAY_PAGES - 1)
    fun dateOf(page: Int) = today.minusDays((DAY_PAGES - 1 - page).toLong())
    val pager = rememberPagerState(initialPage = pageOf(date)) { DAY_PAGES }
    val currentDate by rememberUpdatedState(date)
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { p -> if (dateOf(p) != currentDate) select(dateOf(p)) }
    }
    LaunchedEffect(date) {
        val target = pageOf(date)
        if (pager.currentPage != target) {
            if (abs(pager.currentPage - target) <= 7) pager.animateScrollToPage(target) else pager.scrollToPage(target)
        }
    }
    val colors = MaterialTheme.colorScheme
    val tColor = typeColor(dayType, colors.secondary, MacroColors.training, MacroColors.cycle)

    Surface(onClick = onPick, shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer, modifier = modifier.height(56.dp)) {
        Box(contentAlignment = Alignment.Center) {
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                val d = dateOf(page)
                val title = when (d) {
                    today -> "Сегодня"; today.minusDays(1) -> "Вчера"; today.minusDays(2) -> "Позавчера"
                    else -> d.format(DateTimeFormatter.ofPattern("d MMMM", ru))
                }
                val typed = d == date && dayType != DayType.Normal
                Column(Modifier.fillMaxSize().padding(horizontal = 38.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(title, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight(650), maxLines = 1)
                    Text(
                        d.format(longDate) + if (typed) " · " + dayType.label.lowercase() else "",
                        fontSize = 13.sp, maxLines = 1,
                        fontWeight = if (typed) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (typed) tColor else colors.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(MockIcons.ChevronLeft, null, Modifier.size(16.dp), tint = colors.outline)
                Spacer(Modifier.weight(1f))
                val hasNext = pager.currentPage < DAY_PAGES - 1
                Icon(MockIcons.ChevronRight, null, Modifier.size(16.dp), tint = if (hasNext) colors.onSurface else colors.surfaceContainerHighest)
            }
        }
    }
}

/** Сколько дней назад можно пролистать капсулой (~10 лет). */
private const val DAY_PAGES = 3653

/**
 * Тип дня: кнопки 40 dp. Выбранная залита цветом типа (скругление 20), текст `onSecondary`, иконка `onPrimary`;
 * остальные — `surfaceContainerHigh` (скругление 12), иконка в цвете типа.
 */
@Composable
private fun DayTypes(ui: TodayUi, onPick: (DayType) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val types = if (ui.showCycle) DayType.entries else DayType.entries.filter { it != DayType.Cycle }
    Row(modifier.semantics { contentDescription = "Тип дня" }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { t ->
            val sel = t == ui.dayType
            val c = typeColor(t, colors.secondary, MacroColors.training, MacroColors.cycle)
            val icon = when (t) { DayType.Normal -> MockIcons.Sun; DayType.Training -> MockIcons.Dumbbell; DayType.Cycle -> MockIcons.Drop }
            Row(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(if (sel) 20.dp else 12.dp))
                    .background(if (sel) c else colors.surfaceContainerHigh)
                    .selectable(selected = sel, role = Role.RadioButton, onClick = { onPick(t) })
                    .padding(start = 10.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = if (sel) colors.onPrimary else c)
                Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (sel) colors.onSecondary else colors.onSurface)
            }
        }
    }
}

@Composable
private fun DayNote(ui: TodayUi, modifier: Modifier) {
    val text = when (ui.dayType) {
        DayType.Training -> "День тренировки: норма углеводов +${fmt0(ui.norms.carbs - ui.baseNorms.carbs)} г, итого ${fmt0(ui.norms.kcal)} ккал"
        DayType.Cycle -> "Дни цикла: +${fmt0(ui.norms.carbs - ui.baseNorms.carbs)} г углеводов и +${fmt0(ui.norms.kcal - ui.baseNorms.kcal)} ккал к норме"
        DayType.Normal -> return
    }
    val c = if (ui.dayType == DayType.Training) MacroColors.training else MacroColors.cycle
    Text(text, fontSize = 12.sp, color = c, modifier = modifier)
}

/** Карточка прогресса: скругление 32, `surfaceContainer`, поля 20, промежуток 20; кольцо 256. */
@Composable
private fun ProgressCard(ui: TodayUi, modifier: Modifier) {
    val n = ui.norms; val e = ui.eaten
    val over = e.kcal > n.kcal
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(32.dp), color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                Modifier.size(256.dp).semantics { contentDescription = "Съедено ${fmt0(e.kcal)} из ${fmt0(n.kcal)} ккал" },
                contentAlignment = Alignment.Center,
            ) {
                WavyRing(
                    fraction = if (n.kcal > 0) (e.kcal / n.kcal).toFloat() else 0f,
                    color = colors.primary, track = colors.secondaryContainer,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (over) "+" + fmt0(e.kcal - n.kcal) else fmt0(n.kcal - e.kcal),
                        fontSize = 52.sp, lineHeight = 52.sp, fontWeight = FontWeight(750), letterSpacing = (-1.5).sp,
                        color = if (over) colors.error else colors.onSurface,
                    )
                    Text(if (over) "ккал сверх нормы" else "ккал осталось", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Text("${fmt0(e.kcal)} из ${fmt0(n.kcal)}", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MacroBar("Белки", e.protein, n.protein, MacroColors.protein, MacroColors.proteinContainer)
                MacroBar("Жиры", e.fat, n.fat, MacroColors.fat, MacroColors.fatContainer)
                MacroBar("Углеводы", e.carbs, n.carbs, MacroColors.carbs, MacroColors.carbsContainer)
            }
        }
    }
}

@Composable
private fun MacroBar(label: String, v: Double, goal: Double, color: Color, track: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).alignByBaseline())
            Text(fmt0(v), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.alignByBaseline())
            Text(" / ${fmt0(goal)} г", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
        }
        WavyBar(v, goal, color, track)
    }
}

/** Пустой день: карточка 28, поля 36 / 24, иконка 72 в `secondaryContainer`. */
@Composable
private fun EmptyDay(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(category("grain").icon, null, Modifier.size(34.dp), tint = colors.onSecondaryContainer)
            }
            Text("Пока ничего не записано", fontSize = 18.sp, fontWeight = FontWeight(650))
            Text(
                "Нажмите «+» и напишите, что съели, — например «овсянка 250, кофе с молоком».",
                fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Группа дневника: заголовок 17 / 650 + ккал справа; записи — в одном блоке 24 `surfaceContainerLow`. */
@Composable
private fun MealSection(
    title: String, sub: String, list: List<FoodEntry>, showTime: Boolean,
    onTap: (FoodEntry) -> Unit, onRepeat: (FoodEntry) -> Unit, onDelete: (FoodEntry) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f).alignByBaseline(), verticalAlignment = Alignment.Bottom) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight(650), modifier = Modifier.alignByBaseline())
                if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant, modifier = Modifier.alignByBaseline())
            }
            Text("${fmt0(list.sumOf { it.kcal })} ккал", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.alignByBaseline())
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerLow)) {
            list.forEach { e ->
                androidx.compose.runtime.key(e.id) {
                    EntryRow(e, showTime, onTap = { onTap(e) }, onRepeat = { onRepeat(e) }, onDelete = { onDelete(e) })
                }
            }
        }
    }
}

/**
 * Строка записи (макет): строка едет за пальцем (до ±170 dp). Вправо больше 90 — повтор (строка
 * пружиной возвращается), влево больше 90 — удаление с отменой. Под строкой: справа — `primaryContainer`
 * «Повторить», слева — `errorContainer` «Удалить», подпись проявляется к 90 dp.
 */
@Composable
private fun EntryRow(e: FoodEntry, showTime: Boolean, onTap: () -> Unit, onRepeat: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) } // dp
    val repeat by rememberUpdatedState(onRepeat)
    val delete by rememberUpdatedState(onDelete)
    val d = dx.value
    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        // Подложка
        Row(
            Modifier.matchParentSize().background(if (d > 0) colors.primaryContainer else if (d < 0) colors.errorContainer else Color.Transparent).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.alpha(if (d > 0) min(d / 90f, 1f) else 0f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MockIcons.Repeat, null, Modifier.size(22.dp), tint = colors.onPrimaryContainer)
                Text("Повторить", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.alpha(if (d < 0) min(-d / 90f, 1f) else 0f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Удалить", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onErrorContainer)
                Icon(MockIcons.Delete, null, Modifier.size(22.dp), tint = colors.onErrorContainer)
            }
        }
        // Сама строка
        val amount = (e.pieces?.let { "${fmt1(it)} шт, " } ?: "") + "${fmt0(e.grams)} г" + if (showTime) " · " + hhmm(e.minute) else ""
        Surface(
            onClick = onTap,
            color = colors.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .offset { IntOffset(with(density) { d.dp.roundToPx() }, 0) }
                .semantics { contentDescription = "${e.name}, ${fmt0(e.grams)} г. Нажмите, чтобы изменить; смахните вправо — повторить, влево — удалить" }
                .pointerInput(e.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val v = dx.value
                            scope.launch {
                                when {
                                    v > 90f -> { repeat(); dx.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
                                    v < -90f -> delete()
                                    else -> dx.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { dx.animateTo(0f) } },
                    ) { ch, amountPx ->
                        ch.consume()
                        scope.launch { dx.snapTo((dx.value + amountPx / density.density).coerceIn(-170f, 170f)) }
                    }
                },
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryIcon(e.category)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(e.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Б ${fmtF1(e.protein)} · Ж ${fmtF1(e.fat)} · У ${fmtF1(e.carbs)}", fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(fmt0(e.kcal), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(amount, fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}
