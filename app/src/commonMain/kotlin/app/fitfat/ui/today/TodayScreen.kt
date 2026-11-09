package app.fitfat.ui.today

import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import app.fitfat.ui.common.macroLine
import app.fitfat.ui.components.AddFab
import app.fitfat.ui.components.FitText
import app.fitfat.ui.components.PillToggle
import app.fitfat.ui.components.WavyBar
import app.fitfat.ui.components.WavyRing
import app.fitfat.ui.components.showUndo
import app.fitfat.ui.theme.MacroColors
import app.fitfat.ui.weight.formatKg
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import app.fitfat.time.DateTimeFormatter
import app.fitfat.time.ChronoUnit
import kotlin.math.abs
import app.fitfat.ui.components.Feedback
import kotlin.math.sign
import kotlin.math.exp
import kotlin.math.min

private val weekday = DateTimeFormatter.ofPattern("EEEE")
private val monthShort = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "нояб", "дек")
/** «суббота, 7 нояб» — месяц коротко, чтобы « · тренировка» влезала целиком. */
private fun subtitleDate(d: LocalDate) = "${d.format(weekday)}, ${d.dayOfMonth} ${monthShort[d.monthValue - 1]}"
/** Свайп строки: предел сдвига под сопротивлением, мягкость, порог срыва (dp). */
private const val SWIPE_RESIST = 56f
private const val SWIPE_SOFT = 70f
private const val SWIPE_SNAP = 110f

private fun hhmm(minute: Int) = Fmt.hhmm(minute / 60, minute % 60)

/** Разрыв между записями, после которого начинается новый блок в режиме «Время». */
private const val BLOCK_GAP_MIN = 10

/**
 * Блоки по времени: записи (по убыванию времени) идут в один блок, пока соседние отличаются не больше
 * чем на [BLOCK_GAP_MIN] минут. Заголовок блока — время самой ранней записи в нём.
 */
private fun timeBlocks(entries: List<FoodEntry>): List<List<FoodEntry>> {
    val blocks = mutableListOf<MutableList<FoodEntry>>()
    entries.forEach { e ->
        val cur = blocks.lastOrNull()
        if (cur != null && app.fitfat.data.DayClock.order(cur.last().minute) - app.fitfat.data.DayClock.order(e.minute) <= BLOCK_GAP_MIN) cur += e else blocks += mutableListOf(e)
    }
    return blocks
}

/**
 * Главный экран (макет `Today.dc.html`, 2.1–2.3). Отступы корня 12 / 16, промежуток 16;
 * отрицательные отступы макета (−4, −8, −6) учтены в верхних отступах элементов.
 */
@Composable
fun TodayScreen(vm: TodayViewModel, weightFor: (LocalDate) -> Double?, onWeight: (LocalDate) -> Unit, onAdd: (LocalDate, androidx.compose.ui.geometry.Rect?) -> Unit, onProduct: (Long) -> Unit = {}, onBase: () -> Unit = {},
    plateau: PlateauOffer? = null, onPlateauCut: () -> Unit = {}, onPlateauLater: () -> Unit = {}, onWeek: () -> Unit = {}) {
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
            item(key = "head") { Header(ui.date, weightFor(ui.date), onSelect = vm::setDate, onPick = { picking = true }, onWeight = { onWeight(ui.date) }, dayType = ui.dayType, onBase = onBase) }
            item(key = "types") {
                DayTypes(ui, onPick = vm::setDayType, modifier = Modifier.padding(top = 12.dp))
            }
            item(key = "card") { ProgressCard(ui, Modifier.padding(top = 16.dp), onWeek) }
            // Карточка застоя — только на сегодня
            if (plateau != null && ui.date == app.fitfat.data.DayClock.today()) item(key = "plateau") {
                PlateauCard(plateau, onPlateauCut, onPlateauLater, Modifier.padding(top = 16.dp).animateItem())
            }
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
            // Записи дня — одним блоком: при смене дня старый уезжает, новый въезжает по направлению листания
            item(key = "entries") {
                androidx.compose.animation.AnimatedContent(
                    targetState = ui,
                    transitionSpec = {
                        val dir = if (targetState.date > initialState.date) 1 else -1
                        (androidx.compose.animation.slideInHorizontally(tween(DAY_MS)) { it / 3 * dir } + androidx.compose.animation.fadeIn(tween(DAY_MS)))
                            .togetherWith(androidx.compose.animation.slideOutHorizontally(tween(DAY_MS)) { -it / 3 * dir } + androidx.compose.animation.fadeOut(tween(DAY_MS * 2 / 3)))
                            .using(androidx.compose.animation.SizeTransform(clip = false))
                    },
                    contentKey = { it.date }, // внутри дня (правка, удаление) блок не уезжает
                    label = "day",
                ) { day ->
                    Column(Modifier.fillMaxWidth()) {
                        if (day.entries.isEmpty()) EmptyDay(Modifier.padding(top = 10.dp))
                        // Группы: новые сверху. Записи уже отсортированы по времени по убыванию.
                        val byMeal = !day.groupByTime
                        val groups: List<Pair<String, List<FoodEntry>>> =
                            if (byMeal) day.entries.groupBy { Meal.of(it.meal).label }.toList()
                            else timeBlocks(day.entries).map { hhmm(it.last().minute) to it }
                        groups.forEachIndexed { gi, (title, list) ->
                            androidx.compose.runtime.key(title, list.first().id) {
                                MealSection(
                                    title = title,
                                    sub = if (byMeal) "" else "  ·  " + Meal.of(list.first().meal).label,
                                    list = list, showTime = byMeal, nudgeId = if (gi == 0) list.first().id else -1,
                                    onTap = { editing = it to false },
                                    onRepeat = { editing = it to true },
                                    onDelete = vm::delete,
                                    modifier = Modifier.padding(top = if (gi == 0) 10.dp else 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        var fabRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
        AddFab(
            // Кнопка разворачивается в экран «Добавить» — нужны её границы
            onClick = { onAdd(ui.date, fabRect) },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 104.dp)
                .onGloballyPositioned { fabRect = it.boundsInRoot() },
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
            onCard = if (e.productId > 0) ({ editing = null; onProduct(e.productId) }) else null,
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
private fun Header(date: LocalDate, weightKg: Double?, onSelect: (LocalDate) -> Unit, onPick: () -> Unit, onWeight: () -> Unit, dayType: DayType, onBase: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DateCapsule(date, dayType, onSelect, onPick, Modifier.weight(1f))
        // Вес выбранного дня; тап — шторка с кругом для этой даты. Нет веса — «— кг».
        // База продуктов и блюд — круглая кнопка в шапке
        Surface(onClick = onBase, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Search, "Все продукты и блюда", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // При смене дня вес въезжает по направлению листания (вперёд — справа, назад — слева), ширина чипа плавно меняется
        Surface(onClick = onWeight, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.height(40.dp)) {
            Box(Modifier.padding(horizontal = 14.dp).animateContentSize(spring(dampingRatio = 0.8f, stiffness = 500f)), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedContent(
                    targetState = date to weightKg,
                    transitionSpec = {
                        val dir = if (targetState.first > initialState.first) 1 else -1
                        (androidx.compose.animation.slideInHorizontally(tween(260)) { it / 2 * dir } + androidx.compose.animation.fadeIn(tween(220)))
                            .togetherWith(androidx.compose.animation.slideOutHorizontally(tween(260)) { -it / 2 * dir } + androidx.compose.animation.fadeOut(tween(160)))
                            .using(androidx.compose.animation.SizeTransform(clip = true))
                    },
                    contentKey = { it.second }, // анимация только когда вес действительно другой
                    label = "weight",
                ) { (_, kg) ->
                    Text(
                        (kg?.let { formatKg(it) } ?: "—") + " кг", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (kg == null) 0.7f else 1f),
                    )
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
    val today = remember { app.fitfat.data.DayClock.today() }
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
                    else -> d.format(DateTimeFormatter.ofPattern("d MMMM"))
                }
                val typed = d == date && dayType != DayType.Normal
                Column(Modifier.fillMaxSize().padding(horizontal = 38.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(title, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight(650), maxLines = 1)
                    // Дата уже в заголовке («4 ноября») — в подписи только день недели, без повтора
                    val relative = d == today || d == today.minusDays(1) || d == today.minusDays(2)
                    DateSubtitle(if (relative) subtitleDate(d) else d.format(weekday), typed, if (d == date) dayType else DayType.Normal)
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

/**
 * Подпись даты в капсуле: «суббота, 7 нояб» и справа чип типа дня — закруглённый контейнер цвета типа со значком
 * (гантель — тренировка, капля — цикл; решение 08.11.2026 вместо слова). Чип выезжает и уезжает, сдвигая дату по центру.
 * Размер шрифта подбирается под строку с чипом, чтобы при крупном шрифте ничего не прыгало.
 */
@Composable
private fun DateSubtitle(base: String, typed: Boolean, type: DayType) {
    val colors = MaterialTheme.colorScheme
    // Чип помнит последний особый тип — уезжает со своим значком и цветом
    var last by remember { mutableStateOf(DayType.Training) }
    if (type != DayType.Normal) last = type
    val ink = if (last == DayType.Cycle) MacroColors.cycle else MacroColors.training
    val box = if (last == DayType.Cycle) MacroColors.cycleContainer else MacroColors.trainingContainer
    val icon = if (last == DayType.Cycle) MockIcons.Drop else MockIcons.Dumbbell
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val style = androidx.compose.material3.LocalTextStyle.current.merge(androidx.compose.ui.text.TextStyle(fontSize = 13.sp))
    val chipPx = with(LocalDensity.current) { 34.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxW = constraints.maxWidth
        val scale = remember(base, maxW, style) {
            val w = measurer.measure(base, style, maxLines = 1, softWrap = false).size.width + chipPx
            if (w <= maxW) 1f else (maxW * 0.98f / w).coerceAtLeast(0.7f)
        }
        val size = 13.sp * scale
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(base, fontSize = size, lineHeight = (size.value * 1.3f).sp, maxLines = 1, softWrap = false, color = colors.onSurfaceVariant)
            androidx.compose.animation.AnimatedVisibility(
                visible = typed,
                enter = androidx.compose.animation.expandHorizontally(tween(260), expandFrom = Alignment.Start) + androidx.compose.animation.fadeIn(tween(260)),
                exit = androidx.compose.animation.shrinkHorizontally(tween(260), shrinkTowards = Alignment.Start) + androidx.compose.animation.fadeOut(tween(200)),
            ) {
                val boxC by animateColorAsState(box, tween(260), label = "chipBg")
                val inkC by animateColorAsState(ink, tween(260), label = "chipInk")
                Box(Modifier.padding(start = 6.dp).height(18.dp).clip(RoundedCornerShape(9.dp)).background(boxC).padding(horizontal = 7.dp)
                    .semantics { contentDescription = if (last == DayType.Cycle) "Цикл" else "Тренировка" }, contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(13.dp), tint = inkC)
                }
            }
        }
    }
}

/** Смена дня: блок дневника уезжает и въезжает (мс). */
private const val DAY_MS = 300

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
    // Режимы одного дня — переключатель как «Приёмы / Время», выбранный сегмент цветом типа
    PillToggle(
        options = types.map { it.label },
        selected = types.indexOf(ui.dayType).coerceAtLeast(0),
        onSelect = { onPick(types[it]) },
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Тип дня" },
        icons = types.map { when (it) { DayType.Normal -> MockIcons.Sun; DayType.Training -> MockIcons.Dumbbell; DayType.Cycle -> MockIcons.Drop } },
        selectedColors = types.map { typeColor(it, colors.secondary, MacroColors.training, MacroColors.cycle) },
        iconTints = types.map { typeColor(it, colors.onSurfaceVariant, MacroColors.training, MacroColors.cycle) },
    )
}

private val cutDays = DateTimeFormatter.ofPattern("EEEE")

/** Плашка компенсации: скругление 14, `surfaceContainerHigh`, текст 12 `onSurfaceVariant`, шеврон; тап — «Неделя». */
@Composable
private fun CompensationNote(cut: Double, from: List<LocalDate>, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val days = from.distinct().joinToString(", ") { if (it == app.fitfat.data.DayClock.today().minusDays(1)) "вчера" else it.format(cutDays) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHigh).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Норма снижена на ${fmt0(cut)} ккал — компенсация переборов этой недели" + (if (days.isNotEmpty()) " ($days)" else "") + ". Белок не урезается.",
            fontSize = 12.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f),
        )
        Icon(MockIcons.ChevronRight, "Открыть неделю", Modifier.size(16.dp), tint = colors.onSurfaceVariant)
    }
}

/** Карточка прогресса: скругление 32, `surfaceContainer`, поля 20, промежуток 20; кольцо 256. */
@Composable
private fun ProgressCard(ui: TodayUi, modifier: Modifier, onWeek: () -> Unit = {}) {
    val e = ui.eaten
    val colors = MaterialTheme.colorScheme
    // Нормы и цвета меняются плавно: пружина подхватывает движение с текущего значения,
    // поэтому быстрые переключения «Обычный / Тренировка» не рвут анимацию
    val sp = spring<Float>(dampingRatio = 0.9f, stiffness = 260f)
    val nK by animateFloatAsState(ui.norms.kcal.toFloat(), sp, label = "nk")
    val nP by animateFloatAsState(ui.norms.protein.toFloat(), sp, label = "np")
    val nF by animateFloatAsState(ui.norms.fat.toFloat(), sp, label = "nf")
    val nU by animateFloatAsState(ui.norms.carbs.toFloat(), sp, label = "nu")
    val extraT = (ui.typeNorms.carbs - ui.baseNorms.carbs).coerceAtLeast(0.0).toFloat()
    val extra by animateFloatAsState(extraT, sp, label = "ex")
    // Прибавка жиров (дни цикла) — так же отдельной частью полосы
    val extraFT = (ui.typeNorms.fat - ui.baseNorms.fat).coerceAtLeast(0.0).toFloat()
    val extraF by animateFloatAsState(extraFT, sp, label = "exf")
    val fraction by animateFloatAsState(if (ui.norms.kcal > 0) (e.kcal / ui.norms.kcal).toFloat() else 0f, sp, label = "fr")
    // Съеденное тоже перетекает: при смене дня числа досчитываются, полосы доезжают
    val eK by animateFloatAsState(e.kcal.toFloat(), sp, label = "ek")
    val eP by animateFloatAsState(e.protein.toFloat(), sp, label = "ep")
    val eF by animateFloatAsState(e.fat.toFloat(), sp, label = "ef")
    val eU by animateFloatAsState(e.carbs.toFloat(), sp, label = "eu")
    val tween = androidx.compose.animation.core.tween<Color>(320)
    val ringC by animateColorAsState(when (ui.dayType) { DayType.Training -> MacroColors.training; DayType.Cycle -> MacroColors.cycle; DayType.Normal -> colors.primary }, tween, label = "rc")
    val ringT by animateColorAsState(when (ui.dayType) { DayType.Training -> MacroColors.trainingContainer; DayType.Cycle -> MacroColors.cycleContainer; DayType.Normal -> colors.secondaryContainer }, tween, label = "rt")
    // Цвет добавки помнит последний особый тип, чтобы при возврате к «Обычному» она уезжала своим цветом
    var lastSpecial by remember { mutableStateOf(DayType.Training) }
    if (ui.dayType != DayType.Normal) lastSpecial = ui.dayType
    val xc by animateColorAsState(if (lastSpecial == DayType.Cycle) MacroColors.cycle else MacroColors.training, tween, label = "xc")
    val xt by animateColorAsState(if (lastSpecial == DayType.Cycle) MacroColors.cycleContainer else MacroColors.trainingContainer, tween, label = "xt")

    val left = nK - eK
    val over = left < 0
    val kcalInk by animateColorAsState(if (over) colors.error else colors.onSurface, tween, label = "ki")
    Surface(shape = RoundedCornerShape(32.dp), color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                Modifier.size(256.dp).semantics { contentDescription = "Съедено ${fmt0(e.kcal)} из ${fmt0(ui.norms.kcal)} ккал" },
                contentAlignment = Alignment.Center,
            ) {
                WavyRing(fraction = fraction, color = ringC, track = ringT, modifier = Modifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (over) "+" + fmt0(-left.toDouble()) else fmt0(left.toDouble()),
                        fontSize = 52.sp, lineHeight = 52.sp, fontWeight = FontWeight(750), letterSpacing = (-1.5).sp,
                        color = kcalInk,
                    )
                    Text(if (over) "ккал сверх нормы" else "ккал осталось", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.offset(y = (-4).dp))
                    // «ккал осталось» относится к числу — к нему ближе, чем к «0 из 1 829»
                    Text("${fmt0(eK.toDouble())} из ${fmt0(nK.toDouble())}", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MacroBar("Белки", eP.toDouble(), nP.toDouble(), MacroColors.protein, MacroColors.proteinContainer)
                MacroBar("Жиры", eF.toDouble(), nF.toDouble(), MacroColors.fat, MacroColors.fatContainer, extraF.toDouble(), xc, xt)
                // Прибавка углеводов в день тренировки / цикла — отдельной частью полосы своим цветом
                MacroBar("Углеводы", eU.toDouble(), nU.toDouble(), MacroColors.carbs, MacroColors.carbsContainer, extra.toDouble(), xc, xt)
            }
            // Компенсация переборов (макет Today: плашка под полосками, ведёт в «Неделю»)
            AnimatedVisibility(ui.cut >= 1, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                CompensationNote(ui.cut, ui.cutFrom, onWeek)
            }
        }
    }
}

@Composable
private fun MacroBar(
    label: String, v: Double, goal: Double, color: Color, track: Color,
    extra: Double = 0.0, extraColor: Color = color, extraTrack: Color = track,
) {
    // «(+100)» проявляется и гаснет вместе с добавкой
    val alpha = (extra / 20.0).coerceIn(0.0, 1.0).toFloat()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).alignByBaseline())
            Text(fmt0(v), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.alignByBaseline())
            Text(" / ${fmt0(goal)} г", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
            if (alpha > 0.01f) Text(" (+${fmt0(extra)})", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = extraColor.copy(alpha = alpha), modifier = Modifier.alignByBaseline())
        }
        WavyBar(v, goal, color, track, extra = extra, extraColor = extraColor, extraTrack = extraTrack)
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
    /** Запись, которая подсказывает свайп подтягиванием (первая в дне), или -1. */
    nudgeId: Long = -1,
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
                    EntryRow(e, showTime, onTap = { onTap(e) }, onRepeat = { onRepeat(e) }, onDelete = { onDelete(e) }, first = e.id == nudgeId)
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
private fun EntryRow(e: FoodEntry, showTime: Boolean, onTap: () -> Unit, onRepeat: () -> Unit, onDelete: () -> Unit, first: Boolean = false) {
    val aiIds by remember { app.fitfat.data.AppGraph.get().food.aiIds }.collectAsState(initial = emptySet())
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) } // dp
    var released by remember { mutableStateOf(false) } // строка «сорвалась» и идёт за пальцем
    val repeat by rememberUpdatedState(onRepeat)
    val delete by rememberUpdatedState(onDelete)
    val d = dx.value
    val hints = remember { app.fitfat.data.Hints.get() }
    // Первая запись дня сама тянется в обе стороны, пока свайп не пробовали
    app.fitfat.ui.components.SwipeNudge(dx, app.fitfat.ui.components.nudgeWanted(app.fitfat.data.Hints.SWIPE_DIARY, first), both = true)
    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        // Подложка
        Row(
            Modifier.matchParentSize().background(if (d > 0) colors.primaryContainer else if (d < 0) colors.errorContainer else Color.Transparent).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.alpha(if (d > 0) (if (released) 1f else 0.55f * min(d / SWIPE_RESIST, 1f)) else 0f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MockIcons.Repeat, null, Modifier.size(22.dp), tint = colors.onPrimaryContainer)
                Text("Повторить", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.alpha(if (d < 0) (if (released) 1f else 0.55f * min(-d / SWIPE_RESIST, 1f)) else 0f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Удалить", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onErrorContainer)
                Icon(MockIcons.Delete, null, Modifier.size(22.dp), tint = colors.onErrorContainer)
            }
        }
        // Сама строка
        val amount = (e.pieces?.let { "${fmt1(it)} шт, " } ?: "") + "${fmt0(e.grams)} г" + if (showTime) " · " + hhmm(e.minute) else ""
        Surface(
            onClick = onTap,
            color = colors.surfaceContainerLow,
            // В покое — ровная часть блока; при сдвиге скругляется, чтобы углы не торчали на подложке
            shape = RoundedCornerShape((20f * min(abs(d) / 24f, 1f)).dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .offset { IntOffset(with(density) { d.dp.roundToPx() }, 0) }
                .semantics { contentDescription = "${e.name}, ${fmt0(e.grams)} г. Нажмите, чтобы изменить; смахните вправо — повторить, влево — удалить" }
                .pointerInput(e.id) {
                    // Как свайп уведомлений Android: сопротивление нарастает, на пороге «срыв» со щелчком,
                    // дальше строка догоняет палец и идёт за ним один в один. Вернули палец — снова прилипает.
                    var raw = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { raw = dx.value; released = false; hints.used(app.fitfat.data.Hints.SWIPE_DIARY) },
                        onDragEnd = {
                            val v = dx.value
                            val wasReleased = released
                            released = false
                            scope.launch {
                                when {
                                    wasReleased && v > 0 -> { repeat(); dx.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)) }
                                    wasReleased && v < 0 -> delete()
                                    else -> dx.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
                                }
                            }
                        },
                        onDragCancel = { released = false; scope.launch { dx.animateTo(0f) } },
                    ) { ch, amountPx ->
                        ch.consume()
                        raw = (raw + amountPx / density.density).coerceIn(-320f, 320f)
                        val a = abs(raw)
                        if (!released && a >= SWIPE_SNAP) { released = true; Feedback.tick() }
                        else if (released && a < SWIPE_SNAP * 0.75f) { released = false; Feedback.light() }
                        val target = if (released) raw else sign(raw) * SWIPE_RESIST * (1f - exp(-a / SWIPE_SOFT))
                        scope.launch {
                            if (released) dx.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 1600f))
                            else dx.snapTo(target)
                        }
                    }
                },
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CategoryIcon(e.category)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(e.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                        if (e.productId in aiIds) app.fitfat.ui.components.AiMark()
                    }
                    Text(macroLine(e.protein, e.fat, e.carbs, fmt = ::fmtF1), fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(fmt0(e.kcal), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(amount, fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** Предложение среза при застое: изменение тренда за 14 дней, нормы обычного дня до и после. */
data class PlateauOffer(val dropKg: Double, val before: app.fitfat.data.Macros, val after: app.fitfat.data.Macros)

/**
 * «Вес стоит 2 недели»: карточка 24 `surfaceContainerHigh` под кольцом. Что меняется — жиры и углеводы до → после,
 * белок не трогаем. «Позже» (слева, на 3 дня) и «Срезать 150 ккал» (справа, `primary`).
 */
@Composable
private fun PlateauCard(o: PlateauOffer, onCut: () -> Unit, onLater: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(MockIcons.NavWeight, null, Modifier.size(18.dp), tint = colors.onSecondaryContainer)
            }
            Text("Вес стоит 2 недели", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        val drop = Fmt.f1(kotlin.math.abs(o.dropKg))
        val parts = listOfNotNull(
            if (o.after.fat != o.before.fat) "жиры ${fmt0(o.before.fat)} → ${fmt0(o.after.fat)} г" else null,
            if (o.after.carbs != o.before.carbs) "углеводы ${fmt0(o.before.carbs)} → ${fmt0(o.after.carbs)} г" else null,
        ).joinToString(", ")
        Text(
            "Тренд за 14 дней: ${if (o.dropKg > 0) "+" else "−"}$drop кг. Предлагаю срезать ${fmt0(o.before.kcal - o.after.kcal)} ккал: $parts. Белок не трогаем.",
            fontSize = 13.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = onLater, shape = RoundedCornerShape(20.dp), color = Color.Transparent, modifier = Modifier.weight(1f).height(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Позже", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.primary) }
            }
            Surface(onClick = onCut, shape = RoundedCornerShape(20.dp), color = colors.primary, modifier = Modifier.weight(1.4f).height(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Срезать ${fmt0(o.before.kcal - o.after.kcal)} ккал", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary) }
            }
        }
    }
}
