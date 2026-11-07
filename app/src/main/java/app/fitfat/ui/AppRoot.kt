package app.fitfat.ui

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.components.FloatingNavBar
import app.fitfat.ui.components.NavGuardZone
import app.fitfat.ui.components.NavItem
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
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
import app.fitfat.ui.screens.PlaceholderScreen
import app.fitfat.ui.screens.ProfileScreen
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
import java.time.LocalDate
import app.fitfat.update.UpdateViewModel

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Сегодня", MockIcons.NavToday),
    Weight("weight", "Вес", MockIcons.NavWeight),
    Assistant("assistant", "Ассистент", MockIcons.NavAssistant),
    Profile("profile", "Профиль", MockIcons.NavProfile),
}

@Composable
fun AppRoot(
    updates: UpdateViewModel = viewModel(),
    weight: WeightViewModel = viewModel(),
    today: TodayViewModel = viewModel(),
    add: AddFoodViewModel = viewModel(),
    productsVm: ProductsViewModel = viewModel(),
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    // Тихая проверка обновлений при запуске — не чаще раза в 12 часов
    LaunchedEffect(Unit) { updates.autoCheck() }

    val haze = remember { HazeState() }
    val wuiTop by weight.ui.collectAsStateWithLifecycle()
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
    val view = androidx.compose.ui.platform.LocalView.current
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
        else nav.navigate("add/${LocalDate.now().toEpochDay()}?product=$id")
    }
    /** Закрыть «Добавить» зеркально открытию: размытие нарастает, рамка сжимается обратно в кнопку «+». */
    fun closeAdd() {
        val r = lastFab
        // В кнопку сворачивается только «Добавить», открытый с главного; открытый из карточки продукта — обычное «назад»
        val overToday = nav.previousBackStackEntry?.destination?.route == Tab.Today.route
        if (r == null || !overToday || morph.isRunning || closingAdd) { nav.popBackStack(); return }
        closingAdd = true; morphFrom = r
        // Клавиатура уходит сразу и быстрее системной анимации, иначе она висит над главным
        hideImeFast(view) { focus.clearFocus(force = true) }
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
                TodayScreen(
                    today, weightFor = { d -> wuiTop.byDay[d.toEpochDay()]?.kg },
                    onWeight = { d -> weighing = d }, // чип веса — шторка с кругом для выбранного дня
                    onProduct = { id -> nav.navigate("product/$id") },
                    onBase = { nav.navigate("products") },
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
            Tab.Weight -> WeightScreen(weight, onOpenDay = { d -> today.setDate(d); goTab(Tab.Today) })
            Tab.Assistant -> PlaceholderScreen("Ассистент", "Советы и недельный отчёт")
            Tab.Profile -> ProfileScreen(updates, onProducts = { nav.navigate("products") }, onDishes = { nav.navigate("dishes") })
        }
    }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize()) {
        // Всё содержимое — источник для блюра навбара и зоны под ним
        Box(Modifier.fillMaxSize().haze(haze)) {
        TabLayer(
            current = Tab.entries.indexOfFirst { it.route == current },
            layerAlpha = tabLayerAlpha.value,
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
                            if (armed != swipeArmed) { swipeArmed = armed; if (armed) Feedback.tickSilent(view.context) }
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
                val day = entry.arguments?.getString("day")?.toLongOrNull() ?: LocalDate.now().toEpochDay()
                val product = entry.arguments?.getString("product")?.toLongOrNull() ?: 0L
                // Свой непрозрачный фон: под рамкой ещё виден главный экран, он не должен просвечивать
                // Системное «назад» тоже сворачивает экран в кнопку
                androidx.activity.compose.BackHandler(enabled = lastFab != null && !closingAdd) { closeAdd() }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (entry.id == hiddenAdd) 0f else 1f }.morphClip(morphFrom, morph.value).background(MaterialTheme.colorScheme.surface)) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    alpha = morphContentAlpha(morphFrom, morph.value)
                    // Экран выходит из размытия: 24 dp → 0 за BLUR_MS (Android 12+, раньше — только проявление)
                    // Только во время разворота и сворачивания
                    val b = if (morphFrom != null || closingAdd || addBlur.isRunning) addBlur.value * density else 0f
                    renderEffect = if (b > 0.5f && android.os.Build.VERSION.SDK_INT >= 31) androidx.compose.ui.graphics.BlurEffect(b, b, androidx.compose.ui.graphics.TileMode.Decal) else null
                }) {
                AddFoodScreen(
                    add, LocalDate.ofEpochDay(day), onClose = { closeAdd() },
                    onAllProducts = { nav.navigate("products") }, productId = product,
                    pendingProduct = pendingProduct, onPendingTaken = { pendingProduct = 0L },
                    onDishes = { nav.navigate("dishes") }, session = entry.id,
                    onBarcode = { code, onFound -> productsVm.resolveBarcode(code, onFound, onNew = { nav.navigate("productEdit/0?toAdd=true") }) },
                    onCreateDish = { picked ->
                        productsVm.draft = picked.map { app.fitfat.data.DishIngredient(dishId = 0, productId = it.product.id, grams = it.grams) }
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
                )
            }
            // Блюда (0.4, шаг 2)
            composable("dishes") {
                DishesScreen(
                    productsVm, onBack = { nav.popBackStack() },
                    onCreate = { productsVm.draft = emptyList(); nav.navigate("dish/0") },
                    onOpenCard = { id -> nav.navigate("dish/$id") },
                    // Порция блюда уходит в «Что съели» на экране добавления
                    onAdd = { dish, g, ov -> add.add(dish, g, null, ov); nav.popBackStack() },
                )
            }
            composable("dish/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                DishCardScreen(
                    productsVm, id, onBack = { nav.popBackStack() },
                    onDeleted = { nav.popBackStack() },
                    onAddToDiary = { did -> addToDiary(did) },
                )
            }
            composable("product/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
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
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                // Новый продукт после скана на «Добавить» — сохранили и сразу в «Что съели»
                val toAdd = entry.arguments?.getString("toAdd") == "true"
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
        morphFrom?.let { from -> FabMorph(from, morph.value) }
    }
    }

    UpdateDialog(updates)

    // Утренний вопрос о весе: один раз в день, пока не ответили или не пропустили
    val ask by weight.askToday.collectAsStateWithLifecycle()
    val wui by weight.ui.collectAsStateWithLifecycle()
    val dialHint by weight.hint.collectAsStateWithLifecycle()
    if (ask) {
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
private fun Modifier.morphClip(from: Rect?, t: Float): Modifier = if (from == null || t >= 1f) this else this.then(
    Modifier.graphicsLayer {
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
private fun FabMorph(from: Rect, t: Float) {
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize()) {
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
private fun TabLayer(current: Int, layerAlpha: Float, modifier: Modifier, content: @Composable (Tab) -> Unit) {
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
    val layerShown = layerAlpha > 0.001f
    Box(modifier.graphicsLayer { this.alpha = layerAlpha }) {
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
