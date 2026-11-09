package app.fitfat.ui

import androidx.savedstate.read
import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import app.fitfat.ui.components.hideImeFast
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import app.fitfat.ui.components.Feedback
import kotlin.math.abs
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import kotlin.math.exp
import kotlin.math.sign
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.components.FloatingNavBar
import app.fitfat.ui.components.NavGuardZone
import app.fitfat.ui.components.NavItem
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fitfat.ui.profile.ProfileScreen
import app.fitfat.ui.add.AddFoodScreen
import app.fitfat.ui.add.AddFoodViewModel
import app.fitfat.ui.dishes.DishCardScreen
import app.fitfat.ui.dishes.DishesScreen
import app.fitfat.ui.products.ProductCardScreen
import app.fitfat.ui.products.ProductEditScreen
import app.fitfat.ui.products.ProductsScreen
import app.fitfat.ui.products.ProductsViewModel
import app.fitfat.ui.today.TodayScreen
import app.fitfat.ui.today.TodayViewModel
import app.fitfat.ui.weight.MorningWeightDialog
import app.fitfat.ui.weight.WeightEditSheet
import app.fitfat.ui.weight.WeightScreen
import app.fitfat.ui.weight.WeightViewModel
import app.fitfat.update.UpdateDialog
import kotlinx.datetime.LocalDate
import app.fitfat.update.UpdateViewModel

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Сегодня", MockIcons.NavToday),
    Weight("weight", "Вес", MockIcons.NavWeight),
    Assistant("assistant", "Ассистент", MockIcons.NavAssistant),
    Profile("profile", "Профиль", MockIcons.NavProfile),
}

@Composable
fun AppRoot(
    updates: UpdateViewModel = viewModel { UpdateViewModel() },
    weight: WeightViewModel = viewModel { WeightViewModel() },
    today: TodayViewModel = viewModel { TodayViewModel() },
    add: AddFoodViewModel = viewModel { AddFoodViewModel() },
    productsVm: ProductsViewModel = viewModel { ProductsViewModel() },
    assistantVm: app.fitfat.ui.assistant.AssistantViewModel = viewModel { app.fitfat.ui.assistant.AssistantViewModel() },
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    // Журнал переходов — для отчётов о сбоях
    LaunchedEffect(current) { current?.let { app.fitfat.platform.AppLog.i("screen $it") } }

    // Тихая проверка обновлений при запуске — не чаще раза в 12 часов
    LaunchedEffect(Unit) { updates.autoCheck() }
    // Доступ к ассистенту — при запуске и сразу после входа или выхода: иначе после входа оставался прежний ответ
    val signedIn by app.fitfat.data.GoogleAccount.get().account.collectAsState()
    LaunchedEffect(signedIn?.email) { app.fitfat.data.Access.refresh() }
    // Прошлый запуск упал — отчёт разработчику
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(3_000); app.fitfat.data.Reports.sendPendingCrash() }
    // Копия в Google Диск раз в день — тихо, если вошли и доступ к Диску уже дан
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(4_000); runCatching { app.fitfat.data.CloudBackup.autoIfDue() } }

    val haze = remember { HazeState() }
    // Первая настройка: при первом запуске — сразу; потом — из «Профиля» («Пересчитать нормы»)
    val fitApp = app.fitfat.data.AppGraph.get()
    val profileStore = remember { fitApp.profile }
    var setupOpen by remember { mutableStateOf(!profileStore.done) }
    // Настройку открыли «Изменить» из «Тело и цель»: правка с текущими значениями, а не расчёт с нуля
    var setupEdit by remember { mutableStateOf(false) }
    var setupStep by remember { mutableStateOf(1) }
    var setupFirstRun by remember { mutableStateOf(!profileStore.done) }
    val profileVer by profileStore.version.collectAsStateWithLifecycle()
    val profileNow = remember(profileVer) { profileStore.load() }
    /** Записать нормы трёх типов дня из профиля (вес — последний из дневника веса), своих значений и срезов. */
    fun applyNorms(p: app.fitfat.data.Profile, kg: Double? = null) {
        val w = kg ?: weight.ui.value.last?.kg ?: p.weightKg
        val plan = app.fitfat.data.NormsPlan.withCustom(app.fitfat.data.NormsPlan.calc(p, w, profileStore.cuts), profileStore.custom)
        fitApp.norms.setNorms(plan.normal, plan.training, plan.cycle, p.sex == app.fitfat.data.Sex.Female)
    }
    val wuiTop by weight.ui.collectAsStateWithLifecycle()
    // Правка профиля из «Тело и цель» (по шагам или шторкой): вес поменяли — сегодняшнее взвешивание;
    // нормы поменялись — две недели до проверки застоя заново
    fun saveEdit(p0: app.fitfat.data.Profile, w0: Double?, r: app.fitfat.ui.setup.SetupResult) {
        val kg = r.profile.weightKg
        if (w0 == null || kotlin.math.abs(kg - w0) >= 0.05) weight.save(LocalDate.now(), kg)
        val before = app.fitfat.data.NormsPlan.withCustom(app.fitfat.data.NormsPlan.calc(p0, w0 ?: kg, profileStore.cuts), profileStore.custom)
        val after = app.fitfat.data.NormsPlan.withCustom(app.fitfat.data.NormsPlan.calc(r.profile, kg, r.cuts), r.custom)
        profileStore.save(r.profile)
        profileStore.custom = r.custom
        profileStore.cuts = r.cuts
        if (before != after) profileStore.normsSince = app.fitfat.data.DayClock.today()
        applyNorms(r.profile, kg)
    }
    // Неделя (5.1): 7 дней по сегодня; лимит дня — норма его типа со срезом компенсации переборов.
    // Записи грузим с понедельника недели первого дня — от него считается компенсация
    val dayTypesVer by fitApp.norms.dayTypesVersion.collectAsStateWithLifecycle()
    val todayTop = app.fitfat.data.DayClock.today()
    val weekFood by remember(todayTop) { fitApp.food.range(app.fitfat.data.Compensation.weekStart(todayTop.minusDays(6)), todayTop) }.collectAsStateWithLifecycle(emptyList())
    val weekDays = remember(weekFood, dayTypesVer, profileVer, todayTop) {
        val comp = app.fitfat.data.Compensation.of(fitApp.norms, weekFood, todayTop.minusDays(6), todayTop, todayTop, profileStore.load()?.goal)
        (0..6).map { i ->
            val d = todayTop.minusDays(6L - i)
            val es = weekFood.filter { it.epochDay == d.toEpochDay() }
            val t = fitApp.norms.dayType(d)
            app.fitfat.ui.week.WeekDay(d, t, es.fold(app.fitfat.data.Macros()) { a, e -> a + app.fitfat.data.Macros(e.kcal, e.protein, e.fat, e.carbs) }, app.fitfat.data.Compensation.apply(fitApp.norms.norms(t), comp.cut(d)), es.sortedBy { app.fitfat.data.DayClock.order(it.minute, app.fitfat.data.DayClock.start.value) },
                comp.cut(d), comp.cuts[d]?.from.orEmpty(), comp.over[d] ?: 0.0, comp.forgiven[d] ?: 0.0)
        }
    }
    // Дата, для которой открыта шторка веса с главного экрана
    var weighing by remember { mutableStateOf<LocalDate?>(null) }
    fun goTab(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    // Свайп между вкладками: сдвиг экрана (dp), направление (+1 — следующая), прогресс до порога, порог пройден
    val tabSwipe = remember { Animatable(0f) }
    var swipeDir by remember { mutableIntStateOf(0) }
    var swipeTarget by remember { mutableIntStateOf(-1) }
    var swipeProgress by remember { mutableFloatStateOf(0f) }
    var swipeArmed by remember { mutableStateOf(false) }
    val morph = remember { Animatable(0f) }     // 0 — кнопка, 1 — весь экран
    val addBlur = remember { Animatable(0f) }   // размытие «Добавить», dp: тает дольше разворота рамки
    var morphFrom by remember { mutableStateOf<Rect?>(null) }
    // Где была кнопка «+», из которой открыли «Добавить», — туда же экран и сворачивается
    var lastFab by remember { mutableStateOf<Rect?>(null) }
    var closingAdd by remember { mutableStateOf(false) }
    // Закрытый «Добавить» после сворачивания ещё доживает свой переход — его не показываем, иначе он вспыхивает
    var hiddenAdd by remember { mutableStateOf<String?>(null) }
    // «В дневник» из продуктов и карточек: если «Добавить» уже открыт ниже — возвращаемся в него с окном порции,
    // иначе открываем новый на сегодня
    var pendingProduct by remember { mutableStateOf(0L) }
    fun addToDiary(id: Long) {
        val back = runCatching { nav.getBackStackEntry(ADD_ROUTE) }.getOrNull()
        if (back != null) { pendingProduct = id; nav.popBackStack(ADD_ROUTE, inclusive = false) }
        else nav.navigate("add/${app.fitfat.data.DayClock.today().toEpochDay()}?product=$id")
    }
    /** Закрыть «Добавить» зеркально открытию: размытие нарастает, рамка сжимается обратно в кнопку «+». */
    fun closeAdd() {
        val r = lastFab
        // В кнопку сворачивается только «Добавить», открытый с главного; открытый из карточки продукта — обычное «назад»
        val overToday = nav.previousBackStackEntry?.destination?.route == Tab.Today.route
        if (r == null || !overToday || morph.isRunning || closingAdd) { nav.popBackStack(); return }
        closingAdd = true; morphFrom = r
        // Клавиатура уходит сразу и быстрее системной анимации, иначе она висит над главным
        hideImeFast { focus.clearFocus(force = true) }
        val closing = nav.currentBackStackEntry?.id
        scope.launch { addBlur.snapTo(0f); addBlur.animateTo(24f, tween(CLOSE_MS, easing = FastOutSlowInEasing)) }
        scope.launch {
            morph.snapTo(1f)
            nav.popBackStack()
            // Сразу и быстро с места, мягкая посадка в кнопку — без паузы в начале
            morph.animateTo(0f, tween(CLOSE_MS, easing = FastOutSlowInEasing))
            hiddenAdd = closing
            morphFrom = null; closingAdd = false; lastFab = null
            // Размытие не должно пережить закрытие: иначе следующий «Добавить» без разворота (из карточки, «назад») остаётся мутным
            addBlur.snapTo(0f)
        }
    }
    // Слой вкладок гаснет, когда уходим на другой экран, и проявляется при возврате — как раньше гас сам экран вкладки.
    // Под разворачивающейся кнопкой «+» вкладка остаётся видна; при сворачивании «Добавить» — сразу на месте.
    val tabLayerAlpha = remember { Animatable(1f) }
    val onTab = Tab.entries.any { it.route == current }
    LaunchedEffect(onTab, closingAdd, morphFrom != null) {
        when {
            onTab && closingAdd -> tabLayerAlpha.snapTo(1f)
            onTab -> tabLayerAlpha.animateTo(1f, tween(210, delayMillis = 90))
            morphFrom != null -> tabLayerAlpha.snapTo(1f)
            else -> tabLayerAlpha.animateTo(0f, tween(90))
        }
    }
    // Содержимое вкладок. Собираются один раз и дальше только показываются / прячутся (см. TabLayer)
    val tabContent: @Composable (Tab) -> Unit = { t ->
        when (t) {
            Tab.Today -> {
                // Вес стоит 2 недели — предложить срезать 150 ккал (похудение и рекомпозиция)
                val plateau = remember(profileVer, wuiTop.all) {
                    val pr = profileNow ?: return@remember null
                    val day = app.fitfat.data.DayClock.today()
                    if (!profileStore.done || (pr.goal != app.fitfat.data.Goal.Lose && pr.goal != app.fitfat.data.Goal.Recomp)) return@remember null
                    if (profileStore.plateauSnooze?.let { !day.isAfter(it) } == true) return@remember null
                    val tr = wuiTop.all.map { it.date to it.trend }
                    if (!app.fitfat.data.NormsCalc.isPlateau(tr, profileStore.normsSince, day)) return@remember null
                    val w = wuiTop.last?.kg ?: pr.weightKg
                    // «До» — ровно те нормы, что сейчас на главном; «после» — они же минус срез
                    val before = fitApp.norms.norms(app.fitfat.data.DayType.Normal)
                    val after = app.fitfat.data.NormsCalc.cut(before, app.fitfat.data.NormsCalc.lean(pr.body(w))) ?: return@remember null
                    val from = tr.lastOrNull { !it.first.isAfter(day.minusDays(14)) }?.second ?: tr.first().second
                    app.fitfat.ui.today.PlateauOffer(tr.last().second - from, before, after)
                }
                TodayScreen(
                    today, weightFor = { d -> wuiTop.byDay[d.toEpochDay()]?.kg },
                    plateau = plateau,
                    onPlateauCut = {
                        val pr = profileNow
                        if (pr != null && plateau != null) {
                            // Тот же срез жиров и углеводов — во все типы дня (углеводы не ниже 50 г); свои нормы тоже срезаются
                            val df = plateau.before.fat - plateau.after.fat
                            val du = plateau.before.carbs - plateau.after.carbs
                            fun cut(m: app.fitfat.data.Macros): app.fitfat.data.Macros {
                                val f = m.fat - df; val u = (m.carbs - du).coerceAtLeast(minOf(m.carbs, app.fitfat.data.NormsCalc.CARBS_FLOOR))
                                return app.fitfat.data.Macros(m.protein * 4 + f * 9 + u * 4, m.protein, f, u)
                            }
                            val ns = fitApp.norms
                            ns.setNorms(plateau.after, cut(ns.norms(app.fitfat.data.DayType.Training)), cut(ns.norms(app.fitfat.data.DayType.Cycle)), pr.sex == app.fitfat.data.Sex.Female)
                            val own = profileStore.custom
                            if (own.isNotEmpty()) profileStore.custom = own.mapValues { (_, m) -> cut(m) }
                            profileStore.cuts = profileStore.cuts + 1
                            profileStore.normsSince = app.fitfat.data.DayClock.today()
                        }
                    },
                    onPlateauLater = { profileStore.plateauSnooze = app.fitfat.data.DayClock.today().plusDays(3) },
                    onWeight = { d -> weighing = d }, // чип веса — шторка с кругом для выбранного дня
                    onProduct = { id -> nav.navigate("product/$id") },
                    onBase = { nav.navigate("products") },
                    onWeek = { nav.navigate("week") },
                    onAdd = { d, from ->
                        // Кнопка «+» разворачивается на весь экран, потом под ней открывается «Добавить»
                        // «Добавить» открывается сразу и проявляется внутри растущей из кнопки рамки
                        if (from != null && !morph.isRunning) {
                            morphFrom = from; lastFab = from
                            scope.launch {
                                addBlur.snapTo(24f)
                                addBlur.animateTo(0f, tween(BLUR_MS, easing = FastOutSlowInEasing))
                            }
                            scope.launch {
                                morph.snapTo(0f)
                                morph.animateTo(1f, tween(MORPH_MS, easing = FastOutSlowInEasing))
                                morphFrom = null
                            }
                        }
                        nav.navigate("add/${d.toEpochDay()}")
                    },
                )
            }
            Tab.Weight -> WeightScreen(
                weight, onOpenDay = { d -> today.setDate(d); goTab(Tab.Today) },
                // Карточка цели — только у «Похудеть» и «Набрать» с целевым весом
                target = profileNow?.takeIf { it.goal == app.fitfat.data.Goal.Lose || it.goal == app.fitfat.data.Goal.Gain }?.targetKg,
                onGoal = { nav.navigate("me") },
            ) {
                app.fitfat.ui.week.WeekCard(weekDays) { nav.navigate("week") }
            }
            Tab.Assistant -> {
                // Без доступа — рассказ, цена и пробная неделя; с доступом — чат (DESIGN.md, «Подписка и доступ»)
                val access by app.fitfat.data.Access.state.collectAsState()
                if (access.has) app.fitfat.ui.assistant.AssistantChatScreen(assistantVm, onReport = { nav.navigate("week") })
                else app.fitfat.ui.assistant.AssistantIntro()
            }
            Tab.Profile -> {
                // Без профиля «Тело и цель» не из чего показывать — сначала первая настройка
                fun needSetup() = (profileNow == null).also { if (it) { setupFirstRun = false; setupEdit = false; setupOpen = true } }
                val typesVer = dayTypesVer
                ProfileScreen(
                    profileNow, wuiTop.last?.kg, norms = { t -> typesVer.let { fitApp.norms.norms(t) } }, todayType = fitApp.norms.dayType(todayTop),
                    onMe = { if (!needSetup()) nav.navigate("me") },
                    onTraining = { nav.navigate("training") },
                    onProducts = { nav.navigate("products") },
                    onSettings = { nav.navigate("settings") },
                    onAssistant = { goTab(Tab.Assistant) },
                    onSubscription = { nav.navigate("subscription") },
                )
            }
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize()) {
        // Всё содержимое — источник для блюра навбара и зоны под ним
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
        TabLayer(
            current = Tab.entries.indexOfFirst { it.route == current },
            layerAlpha = { tabLayerAlpha.value },
            modifier = Modifier.fillMaxSize()
                // Свайп по экрану вкладки — соседняя вкладка (если жест не забрал сам экран: пейджер, свайп записи, линейки).
                // Пока ведут — экран идёт за пальцем с сопротивлением, с края выезжает подсказка соседней вкладки.
                .graphicsLayer { translationX = tabSwipe.value * density }
                .pointerInput(current) {
                    val i = Tab.entries.indexOfFirst { it.route == current }
                    if (i < 0) return@pointerInput
                    val need = SWIPE_NEED.dp.toPx()
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dx = 0f; var dy = 0f; var blocked = down.isConsumed; var active = false
                        swipeArmed = false
                        while (true) {
                            // Сначала видим событие раньше экрана (Initial): если свайп уже наш — забираем его,
                            // чтобы список вкладки не перехватил жест из-за вертикального дрейфа пальца
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val d = ch.position - ch.previousPosition
                            dx += d.x; dy += d.y
                            if (active) ch.consume()
                            else {
                                // Пока свайп не наш — даём экрану обработать и смотрим, не забрал ли он жест
                                val evMain = awaitPointerEvent(PointerEventPass.Main)
                                if (evMain.changes.firstOrNull { it.id == down.id }?.isConsumed == true) blocked = true
                                if (blocked) continue
                                if (abs(dx) > slop && abs(dx) > 2 * abs(dy)) active = true else continue
                            }
                            val dir = if (dx < 0) 1 else -1
                            val has = (i + dir) in Tab.entries.indices
                            swipeDir = if (has) dir else 0
                            swipeTarget = if (has) i + dir else -1
                            val a = abs(dx) / density
                            // Есть куда — тянется до 48 dp; крайняя вкладка — лишь пружинит до 16 dp
                            val shown = sign(dx) * (if (has) 48f else 16f) * (1f - exp(-a / (if (has) 90f else 60f)))
                            scope.launch { tabSwipe.snapTo(shown) }
                            swipeProgress = (abs(dx) / need).coerceAtMost(1f)
                            val armed = has && abs(dx) > need
                            if (armed != swipeArmed) { swipeArmed = armed; if (armed) Feedback.tickSilent() }
                        }
                        // Направление закреплено при старте — вертикальный дрейф к концу длинного свайпа не мешает
                        val go = active && swipeArmed
                        swipeArmed = false; swipeDir = 0
                        scope.launch { tabSwipe.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
                        if (go && swipeTarget >= 0) goTab(Tab.entries[swipeTarget])
                    }
                },
            content = tabContent,
        )
        NavHost(
            navController = nav, startDestination = Tab.Today.route,
            modifier = Modifier.fillMaxSize(),
            // Вкладки: сдвиг на 1/8 ширины в сторону перехода + проявление; «Добавить» — проявление поверх развернувшейся кнопки
            enterTransition = {
                val dir = tabDir(initialState.destination.route, targetState.destination.route)
                // Разворот кнопки «+»: экран сам проявляется в рамке, отдельный переход не нужен
                if (morphFrom != null && targetState.destination.route?.startsWith("add") == true) return@NavHost fadeIn(tween(1))
                // Вглубь (продукты, блюда, карточки) — «сквозное затухание»: старый гаснет за 90 мс, новый проявляется после него,
                // экраны не накладываются друг на друга
                if (dir == 0) fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(300, delayMillis = 90, easing = FastOutSlowInEasing), initialScale = 0.96f)
                else fadeIn(tween(260, delayMillis = 40)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 8 * dir }
            },
            exitTransition = {
                val dir = tabDir(initialState.destination.route, targetState.destination.route)
                // Главный остаётся под растущей рамкой до конца разворота
                if (morphFrom != null && targetState.destination.route?.startsWith("add") == true) return@NavHost fadeOut(tween(1, delayMillis = MORPH_MS))
                if (dir == 0) fadeOut(tween(90)) else fadeOut(tween(180)) + slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 8 * dir }
            },
            // Закрытие «Добавить» в кнопку: главный сразу под ним, «Добавить» держится до конца сворачивания
            popEnterTransition = { if (closingAdd) fadeIn(tween(1)) else fadeIn(tween(210, delayMillis = 90)) },
            popExitTransition = { if (closingAdd) fadeOut(tween(1, delayMillis = CLOSE_MS + 40)) else fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.96f) },
        ) {
            // Вкладки живут в слое под навигацией (TabLayer) — здесь только места в стеке
            Tab.entries.forEach { t -> composable(t.route) { } }
            composable(ADD_ROUTE) { entry ->
                val day = entry.arguments?.read { getStringOrNull("day") }?.toLongOrNull() ?: app.fitfat.data.DayClock.today().toEpochDay()
                val product = entry.arguments?.read { getStringOrNull("product") }?.toLongOrNull() ?: 0L
                // Свой непрозрачный фон: под рамкой ещё виден главный экран, он не должен просвечивать
                // Системное «назад» тоже сворачивает экран в кнопку
                androidx.compose.ui.backhandler.BackHandler(enabled = lastFab != null && !closingAdd) { closeAdd() }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (entry.id == hiddenAdd) 0f else 1f }.morphClip(morphFrom) { morph.value }.background(MaterialTheme.colorScheme.surface)) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    alpha = morphContentAlpha(morphFrom, morph.value)
                    // Экран выходит из размытия: 24 dp → 0 за BLUR_MS (Android 12+, раньше — только проявление)
                    // Только во время разворота и сворачивания
                    val b = if (morphFrom != null || closingAdd || addBlur.isRunning) addBlur.value * density else 0f
                    renderEffect = if (b > 0.5f && app.fitfat.platform.Platform.supportsBlur) androidx.compose.ui.graphics.BlurEffect(b, b, androidx.compose.ui.graphics.TileMode.Decal) else null
                }) {
                AddFoodScreen(
                    add, LocalDate.ofEpochDay(day), onClose = { closeAdd() },
                    onAllProducts = { nav.navigate("products") }, productId = product,
                    pendingProduct = pendingProduct, onPendingTaken = { pendingProduct = 0L },
                    onDishes = { nav.navigate("dishes") }, session = entry.id,
                    onAssistantInfo = { focus.clearFocus(force = true); nav.popBackStack(); goTab(Tab.Assistant) },
                    onOpenProduct = { id -> focus.clearFocus(force = true); nav.navigate("product/$id") },
                    onBarcode = { code, onFound -> productsVm.resolveBarcode(code, onFound, onNew = { nav.navigate("productEdit/0?toAdd=true") }) },
                    onCreateDish = { picked ->
                        productsVm.draft = picked.map { app.fitfat.data.DishIngredient(dishId = 0, productId = it.product.id, grams = it.grams) }
                        productsVm.draftMeta = null
                        nav.navigate("dish/0")
                    },
                )
                }
                }
            }
            // База продуктов (0.4)
            composable("products") {
                ProductsScreen(
                    productsVm, onBack = { nav.popBackStack() },
                    onOpen = { id -> nav.navigate("product/$id") }, onNew = { productsVm.prefill = null; nav.navigate("productEdit/0") },
                    onNewFromScan = { nav.navigate("productEdit/0") },
                    onAddToDiary = { id -> addToDiary(id) },
                    onNewDish = { productsVm.draft = emptyList(); nav.navigate("dish/0") },
                )
            }
            // Неделя (0.5, макет 5.1)
            composable("week") {
                val from = todayTop.minusDays(6)
                val ws = wuiTop.all.filter { !it.date.isBefore(from) && !it.date.isAfter(todayTop) }.map { it.date to it.kg }
                app.fitfat.ui.week.WeekScreen(weekDays, ws, onBack = { nav.popBackStack() })
            }
            // Настройки приложения (0.5, макет 5.5)
            composable("subscription") {
                app.fitfat.ui.profile.SubscriptionScreen(onBack = { nav.popBackStack() }, onAssistant = { nav.popBackStack(); goTab(Tab.Assistant) })
            }
            composable("settings") {
                val tui by today.ui.collectAsStateWithLifecycle()
                app.fitfat.ui.profile.SettingsScreen(updates, tui.groupByTime, today::setGroupByTime, onBack = { nav.popBackStack() })
            }
            // Тело и цель (0.5): тело, спорт, цель и нормы только для чтения; «Изменить» — шаги настройки
            composable("me") {
                var sheet by remember { mutableStateOf<Int?>(null) }
                val p0 = profileNow ?: app.fitfat.data.Profile()
                val w = wuiTop.last?.kg ?: p0.weightKg
                val typesVer = dayTypesVer
                app.fitfat.ui.profile.MeScreen(
                    p0, w, norms = { t -> typesVer.let { fitApp.norms.norms(t) } }, custom = profileStore.custom.keys, cuts = profileStore.cuts,
                    eta = p0.targetKg?.let { app.fitfat.ui.setup.targetEta(it, w, wuiTop.all.map { e -> e.date to e.trend }) },
                    onBack = { nav.popBackStack() },
                    onEdit = { step -> setupFirstRun = false; setupEdit = true; setupStep = step; setupOpen = true },
                    onSheet = { sheet = it },
                )
                sheet?.let { step ->
                    app.fitfat.ui.setup.SetupSheet(
                        step, p0.copy(weightKg = w),
                        app.fitfat.ui.setup.SetupEdit(profileStore.custom, profileStore.cuts, wuiTop.all.map { it.date to it.trend }),
                        onDismiss = { sheet = null },
                    ) { r -> saveEdit(p0, w, r); sheet = null }
                }
            }
            // Тренировки (0.5, макет 5.8)
            composable("training") {
                val log by remember { fitApp.trainings.observeAll() }.collectAsStateWithLifecycle(emptyList())
                val plan by app.fitfat.data.TrainingSchedule.plan.collectAsStateWithLifecycle()
                val pr = profileNow
                val types = if (pr == null) emptySet() else (if (pr.gym) setOf("strength") else emptySet()) + pr.sports
                app.fitfat.ui.setup.TrainingScreen(
                    types = types,
                    onTypes = { set ->
                        // «Силовые» — это зал в профиле, остальное — виды спорта; от них зависят день тренировки и белок
                        if (pr != null) {
                            val np = pr.copy(gym = "strength" in set, sports = set - "strength")
                            profileStore.save(np)
                            applyNorms(np)
                        }
                    },
                    plan = plan,
                    onPlan = { p -> app.fitfat.data.TrainingSchedule.save(p, app.fitfat.data.DayClock.today()); fitApp.norms.touch() },
                    log = log,
                    onLog = { type, min ->
                        val d = app.fitfat.data.DayClock.today()
                        scope.launch { fitApp.trainings.insert(app.fitfat.data.TrainingLog(epochDay = d.toEpochDay(), type = type, minutes = min)) }
                        // Интенсивная тренировка — сегодня день тренировки (цикл не перебиваем)
                        val intensive = app.fitfat.data.TRAINING_TYPES.firstOrNull { it.first == type }?.third == true
                        // Профиль «не тренируется», а записали интенсивную — значит тренируется: добавляем вид в профиль, у дня тренировки появится добавка углеводов
                        if (intensive && pr != null && !pr.trains) {
                            val np = if (type == "strength") pr.copy(gym = true) else pr.copy(sports = pr.sports + type)
                            profileStore.save(np)
                            applyNorms(np)
                        }
                        if (intensive && fitApp.norms.dayType(d) == app.fitfat.data.DayType.Normal) fitApp.norms.setDayType(d, app.fitfat.data.DayType.Training)
                    },
                    onDelete = { t -> scope.launch { fitApp.trainings.delete(t.id) } },
                    onBack = { nav.popBackStack() },
                )
            }
            // Блюда (0.4, шаг 2)
            composable("dishes") {
                DishesScreen(
                    productsVm, onBack = { nav.popBackStack() },
                    onCreate = { productsVm.draft = emptyList(); productsVm.draftMeta = null; nav.navigate("dish/0") },
                    // Черновик уже собран ассистентом («Из описания») — открыть как есть
                    onCreateDraft = { nav.navigate("dish/0") },
                    onOpenCard = { id -> nav.navigate("dish/$id") },
                    // Порция блюда уходит в «Что съели» на экране добавления
                    onAdd = { dish, g, ov -> add.add(dish, g, null, ov); nav.popBackStack() },
                )
            }
            composable("dish/{id}") { entry ->
                val id = entry.arguments?.read { getStringOrNull("id") }?.toLongOrNull() ?: 0L
                DishCardScreen(
                    productsVm, id, onBack = { nav.popBackStack() },
                    onDeleted = { nav.popBackStack() },
                    onAddToDiary = { did -> addToDiary(did) },
                )
            }
            composable("product/{id}") { entry ->
                val id = entry.arguments?.read { getStringOrNull("id") }?.toLongOrNull() ?: 0L
                // Блюдо открывается своей карточкой
                if (productsVm.products.value.firstOrNull { it.id == id }?.isDish == true) {
                    DishCardScreen(
                        productsVm, id, onBack = { nav.popBackStack() }, onDeleted = { nav.popBackStack() },
                        onAddToDiary = { did -> addToDiary(did) },
                    )
                    return@composable
                }
                ProductCardScreen(
                    productsVm, id, onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("productEdit/$id") },
                    onAddToDiary = { addToDiary(id) },
                )
            }
            composable("productEdit/{id}?toAdd={toAdd}") { entry ->
                val id = entry.arguments?.read { getStringOrNull("id") }?.toLongOrNull() ?: 0L
                // Новый продукт после скана на «Добавить» — сохранили и сразу в «Что съели»
                val toAdd = entry.arguments?.read { getStringOrNull("toAdd") } == "true"
                ProductEditScreen(
                    productsVm, id, onClose = { nav.popBackStack() },
                    // Новый — сразу его карточка вместо формы; правка — назад к карточке
                    onSaved = { newId ->
                        when {
                            toAdd -> scope.launch {
                                productsVm.product(newId).first { it != null }?.let { p -> add.add(p, null, null) }
                                nav.popBackStack()
                            }
                            id == 0L -> nav.navigate("product/$newId") { popUpTo(entry.destination.id) { inclusive = true } }
                            else -> nav.popBackStack()
                        }
                    },
                    onDeleted = { nav.popBackStack("products", inclusive = false).let { ok -> if (!ok) nav.popBackStack(nav.graph.startDestinationId, false) } },
                )
            }
        }
        }
        // Навбар — только на вкладках. Пока кнопка «+» разворачивается в «Добавить» и обратно, он принадлежит
        // главному: виден вокруг рамки и вырезан по ней, поэтому уходит под растущую рамку и проступает из-под сжимающейся
        val morphing = morphFrom != null && current?.startsWith("add") == true
        if (Tab.entries.any { it.route == current } || morphing) {
            val cut = morphFrom
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (morphing) 1f else tabLayerAlpha.value }.drawWithContent {
                if (cut == null) drawContent()
                else {
                    val r = lerpRect(cut, size.width, size.height, morph.value)
                    val rad = 20.dp.toPx() * (1 - morph.value)
                    val hole = androidx.compose.ui.graphics.Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(r, androidx.compose.ui.geometry.CornerRadius(rad)))
                    }
                    clipPath(hole, androidx.compose.ui.graphics.ClipOp.Difference) { this@drawWithContent.drawContent() }
                }
            }) {
                // Под строкой состояния — плавное затемнение цветом фона: прокрученный текст не лезет под часы и значки
                val bg = androidx.compose.material3.MaterialTheme.colorScheme.surface
                androidx.compose.foundation.layout.Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(bg))
                    Box(Modifier.fillMaxWidth().height(14.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(bg, bg.copy(alpha = 0f)))))
                }
                NavGuardZone(haze, Modifier.align(Alignment.BottomCenter))
                FloatingNavBar(
                    items = Tab.entries.map { NavItem(it.label, it.icon) },
                    selected = Tab.entries.indexOfFirst { it.route == current }.let { if (it < 0) Tab.Today.ordinal else it },
                    onSelect = { i -> goTab(Tab.entries[i]) },
                    haze = haze,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        // Разворот кнопки «+»: прямоугольник растёт из кнопки до экрана, цвет — из акцентного в фон
        // Подсказка свайпа: с того края, откуда придёт соседняя вкладка
        val hintShow by animateFloatAsState(if (swipeDir != 0) swipeProgress else 0f, spring(stiffness = 900f), label = "hint")
        // Сторона и вкладка запоминаются: пока подсказка гаснет после отпускания, она уходит туда же, откуда вышла
        var hintDir by remember { mutableIntStateOf(1) }
        var hintTab by remember { mutableIntStateOf(-1) }
        if (swipeDir != 0) { hintDir = swipeDir; hintTab = swipeTarget }
        if (hintShow > 0.01f && hintTab >= 0) {
            val next = Tab.entries[hintTab]
            val right = hintDir > 0
            SwipeHint(next.label, next.icon, right, hintShow, swipeArmed, Modifier.align(if (right) Alignment.CenterEnd else Alignment.CenterStart))
        }
        // Значение анимации читается только при отрисовке — без перестройки всего экрана на каждом кадре
        morphFrom?.let { from -> FabMorph(from) { morph.value } }
        AddWarmUp()
    }
    }

    // Первая настройка поверх всего приложения
    androidx.compose.animation.AnimatedVisibility(
        setupOpen,
        enter = fadeIn(tween(260)) + androidx.compose.animation.slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 12 },
        exit = fadeOut(tween(200)),
    ) {
        val p0 = remember(setupOpen) { profileStore.load() }
        val w0 = wuiTop.last?.kg ?: p0?.weightKg
        val edit = setupEdit && p0 != null
        app.fitfat.ui.setup.SetupScreen(
            // При правке вес — последнее взвешивание
            initial = if (edit && w0 != null) p0!!.copy(weightKg = w0) else p0,
            onClose = if (setupFirstRun) null else ({ setupOpen = false }),
            startStep = if (edit) setupStep else 1,
            edit = if (edit) app.fitfat.ui.setup.SetupEdit(profileStore.custom, profileStore.cuts, wuiTop.all.map { it.date to it.trend }) else null,
        ) { r ->
            if (edit) {
                saveEdit(p0!!, w0, r)
                setupOpen = false
                return@SetupScreen
            }
            // Новый расчёт с нуля: срезы обнуляются, две недели до проверки застоя — заново
            profileStore.save(r.profile)
            profileStore.custom = r.custom
            profileStore.cuts = 0
            profileStore.normsSince = app.fitfat.data.DayClock.today()
            applyNorms(r.profile)
            setupOpen = false
        }
    }

    if (app.fitfat.platform.Platform.supportsSelfUpdate) UpdateDialog(updates)

    // Утренний вопрос о весе: один раз в день, пока не ответили или не пропустили
    // Нажали на уведомление: взвешивание — шторка веса на сегодня, отчёт — «Неделя»
    val openFromNotice by app.fitfat.notify.Reminders.open.collectAsStateWithLifecycle()
    LaunchedEffect(openFromNotice, setupOpen) {
        val o = openFromNotice ?: return@LaunchedEffect
        if (setupOpen) return@LaunchedEffect
        app.fitfat.notify.Reminders.open.value = null
        when (o) {
            app.fitfat.notify.Reminders.WEIGHT -> weighing = LocalDate.now()
            app.fitfat.notify.Reminders.WEEK -> if (current != "week") nav.navigate("week")
        }
    }
    // Разрешение на уведомления — один раз, после первой настройки: напоминания включены по умолчанию
    val askNotices = app.fitfat.ui.components.rememberNotificationPermission()
    LaunchedEffect(setupOpen) {
        if (setupOpen || app.fitfat.notify.Reminders.canPost()) return@LaunchedEffect
        val sp = app.fitfat.platform.Prefs("settings")
        if (sp.getBoolean("notices_asked", false)) return@LaunchedEffect
        sp.edit { putBoolean("notices_asked", true) }
        askNotices()
    }
    val ask by weight.askToday.collectAsStateWithLifecycle()
    val wui by weight.ui.collectAsStateWithLifecycle()
    val dialHint by weight.hint.collectAsStateWithLifecycle()
    if (ask && !setupOpen) {
        val today = LocalDate.now()
        MorningWeightDialog(weight, wui, dialHint, onSave = { weight.save(today, it) }, onSkip = weight::skipToday)
    }
    weighing?.let { d ->
        WeightEditSheet(
            date = d, ui = wui, hint = dialHint, initialType = weight.dayType(d), onLearned = weight::learned,
            onDismiss = { weighing = null },
            onSave = { kg, note, type -> weight.saveDay(d, kg, note, type); weighing = null },
            onDelete = { weight.delete(d); weighing = null },
        )
    }
}

/** Направление между вкладками: +1 — вправо по навбару, −1 — влево, 0 — не вкладки. */
private fun tabDir(from: String?, to: String?): Int {
    val a = Tab.entries.indexOfFirst { it.route == from }
    val b = Tab.entries.indexOfFirst { it.route == to }
    return if (a < 0 || b < 0 || a == b) 0 else if (b > a) 1 else -1
}

private const val MORPH_MS = 420

private const val ADD_ROUTE = "add/{day}?product={product}"

private fun lerpRect(from: Rect, w: Float, h: Float, t: Float) =
    Rect(from.left * (1 - t), from.top * (1 - t), from.right + (w - from.right) * t, from.bottom + (h - from.bottom) * t)

/**
 * Экран «Добавить» во время разворота кнопки «+»: виден только внутри растущей рамки (скругление 20 → 0)
 * (фон сразу непрозрачный — главный экран не просвечивает).
 */
private fun Modifier.morphClip(from: Rect?, progress: () -> Float): Modifier = if (from == null) this else this.then(
    Modifier.graphicsLayer {
        val t = progress()
        if (t >= 1f) { clip = false; return@graphicsLayer }
        val r = lerpRect(from, size.width, size.height, t)
        val rad = 20.dp.toPx() * (1 - t)
        clip = true
        shape = object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
                androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(r, androidx.compose.ui.geometry.CornerRadius(rad)))
        }
    },
)

/** Сколько тает размытие «Добавить» после нажатия на «+», мс (разворот рамки — [MORPH_MS]). */
private const val BLUR_MS = 560

/** Сворачивание «Добавить» обратно в кнопку «+», мс: рамка и размытие вместе, быстрый старт. */
private const val CLOSE_MS = 380

/** Содержимое «Добавить» проявляется в рамке, пока розовая заливка гаснет; фон рамки непрозрачен сразу. */
private fun morphContentAlpha(from: Rect?, t: Float) = if (from == null) 1f else ((t - 0.15f) / 0.55f).coerceIn(0f, 1f)

/** Кнопка «+», растущая до экрана: рамка и скругление 20 → 0; розовый гаснет, пока сквозь него проявляется «Добавить». */
@Composable
private fun FabMorph(from: Rect, progress: () -> Float) {
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize()) {
        val t = progress()
        val r = lerpRect(from, size.width, size.height, t)
        val a = (1f - (t - 0.1f) / 0.6f).coerceIn(0f, 1f)
        drawRoundRect(colors.primary.copy(alpha = a), topLeft = r.topLeft, size = r.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx() * (1 - t)))
        // Плюс гаснет первым
        val pa = (1f - t * 3f).coerceIn(0f, 1f)
        if (pa > 0f) {
            val c = r.center; val half = 16.dp.toPx() * 0.6f; val sw = 2.2f * 32.dp.toPx() / 24f
            drawLine(colors.onPrimary.copy(alpha = pa), androidx.compose.ui.geometry.Offset(c.x - half, c.y), androidx.compose.ui.geometry.Offset(c.x + half, c.y), sw, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(colors.onPrimary.copy(alpha = pa), androidx.compose.ui.geometry.Offset(c.x, c.y - half), androidx.compose.ui.geometry.Offset(c.x, c.y + half), sw, androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** Сколько надо протянуть (dp), чтобы свайп переключил вкладку. */
private const val SWIPE_NEED = 90f

/**
 * Подсказка свайпа между вкладками: таблетка у края с иконкой, названием и стрелкой.
 * Выезжает по мере протягивания; порог пройден — становится акцентной (`primary`), можно отпускать.
 */
@Composable
private fun SwipeHint(label: String, icon: ImageVector, right: Boolean, t: Float, armed: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (armed) colors.primary else colors.surfaceContainerHighest, tween(160), label = "hbg")
    val ink by animateColorAsState(if (armed) colors.onPrimary else colors.onSurface, tween(160), label = "hink")
    val shape = if (right) RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp) else RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    Row(
        modifier
            .graphicsLayer {
                alpha = t.coerceIn(0f, 1f)
                translationX = (if (right) 1 else -1) * (1f - t) * 60.dp.toPx()
                val k = if (armed) 1.04f else 1f; scaleX = k; scaleY = k
            }
            .shadow(8.dp, shape).clip(shape).background(bg)
            .height(48.dp)
            .padding(start = if (right) 14.dp else 10.dp, end = if (right) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!right) Icon(MockIcons.ChevronLeft, null, Modifier.size(18.dp), tint = ink)
        Icon(icon, null, Modifier.size(20.dp), tint = ink)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
        if (right) Icon(MockIcons.ChevronRight, null, Modifier.size(18.dp), tint = ink)
    }
}


/**
 * Слой вкладок под навигацией: экраны вкладок собираются один раз и дальше только показываются.
 * Раньше навигация разбирала экран при уходе и собирала заново при возврате — это и были рывки
 * при переключении (сотни мс на первый кадр). Скрытая вкладка не размещается — не рисуется,
 * не ловит касания и не попадает в доступность; бесконечные анимации на ней стоят.
 * Переход между вкладками — как раньше: сдвиг на 1/8 ширины по направлению + проявление.
 * [current] — открытая вкладка (−1 — сверху другой экран), [layerAlpha] — прозрачность всего слоя (гаснет под другими экранами).
 */
@Composable
private fun TabLayer(current: Int, layerAlpha: () -> Float, modifier: Modifier, content: @Composable (Tab) -> Unit) {
    val n = Tab.entries.size
    val alpha = remember { List(n) { Animatable(if (it == 0) 1f else 0f) } }
    val shift = remember { List(n) { Animatable(0f) } } // доля ширины
    var active by remember { mutableIntStateOf(0) }
    val visited = remember { mutableStateListOf(0) }
    val target = if (current >= 0) current else -1
    // Вкладка, на которую ушли (или вернулись): переход с сдвигом в сторону навбара
    LaunchedEffect(target) {
        if (target < 0 || target == active) return@LaunchedEffect
        val from = active
        val dir = if (target > from) 1 else -1
        if (target !in visited) visited += target
        active = target
        launch { shift[target].snapTo(dir / 8f); shift[target].animateTo(0f, tween(320, easing = FastOutSlowInEasing)) }
        launch { alpha[target].animateTo(1f, tween(260, delayMillis = 40)) }
        launch { shift[from].animateTo(-dir / 8f, tween(320, easing = FastOutSlowInEasing)) }
        launch { alpha[from].animateTo(0f, tween(180)); shift[from].snapTo(0f) }
    }
    // Остальные вкладки собираем заранее, когда приложение уже успокоилось, — первый переход тоже без рывка
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        Tab.entries.indices.forEach { if (it !in visited) visited += it }
    }
    // Сверху другой экран и вкладку под ним уже не видно — слой не рисуем совсем
    // Прозрачность читается при отрисовке, в композицию попадает только «виден / не виден»
    val layerShown by remember { derivedStateOf { layerAlpha() > 0.001f } }
    Box(modifier.graphicsLayer { this.alpha = layerAlpha() }) {
        visited.forEach { i ->
            androidx.compose.runtime.key(i) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (i == active) 1f else 0f)
                        .layout { m, c ->
                            val p = m.measure(c)
                            layout(p.width, p.height) {
                                if (layerShown && alpha[i].value > 0.001f) p.placeWithLayer(0, 0) {
                                    this.alpha = alpha[i].value
                                    translationX = shift[i].value * size.width
                                }
                            }
                        },
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        app.fitfat.ui.components.LocalAnimationsActive provides (layerShown && i == active),
                    ) { content(Tab.entries[i]) }
                }
            }
        }
    }
}

/**
 * Прогрев «Добавить»: через пару секунд после запуска, пока приложение стоит без дела, невидимо собирает поле ввода
 * и прочие тяжёлые при первом показе части — первое нажатие на «+» не спотыкается о загрузку классов (0.6, решение 09.11.2026).
 */
@Composable
private fun AddWarmUp() {
    var phase by remember { mutableIntStateOf(0) } // 0 — ждём, 1 — собрано, 2 — убрано
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2500)
        phase = 1
        kotlinx.coroutines.delay(300)
        phase = 2
    }
    if (phase != 1) return
    Box(Modifier.size(1.dp).graphicsLayer { alpha = 0f }) {
        var v by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("гречка 200")) }
        androidx.compose.foundation.text.BasicTextField(
            v, { v = it },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        )
        androidx.compose.animation.AnimatedVisibility(true) { Text("·") }
    }
}
