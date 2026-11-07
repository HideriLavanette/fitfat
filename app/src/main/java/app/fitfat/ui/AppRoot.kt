package app.fitfat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
    val morph = remember { Animatable(0f) }     // 0 — кнопка, 1 — весь экран
    val morphFade = remember { Animatable(1f) } // проявление экрана «Добавить» из-под залитого фона
    var morphFrom by remember { mutableStateOf<Rect?>(null) }
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize()) {
        // Всё содержимое — источник для блюра навбара и зоны под ним
        NavHost(
            navController = nav, startDestination = Tab.Today.route, modifier = Modifier.fillMaxSize().haze(haze),
            // Вкладки: сдвиг на 1/8 ширины в сторону перехода + проявление; «Добавить» — проявление поверх развернувшейся кнопки
            enterTransition = {
                val dir = tabDir(initialState.destination.route, targetState.destination.route)
                if (dir == 0) fadeIn(tween(220)) else fadeIn(tween(260, delayMillis = 40)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 8 * dir }
            },
            exitTransition = {
                val dir = tabDir(initialState.destination.route, targetState.destination.route)
                if (dir == 0) fadeOut(tween(160)) else fadeOut(tween(180)) + slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 8 * dir }
            },
            popEnterTransition = { fadeIn(tween(260)) },
            popExitTransition = { fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 0.94f) },
        ) {
            composable(Tab.Today.route) {
                TodayScreen(
                    today, weightFor = { d -> wuiTop.byDay[d.toEpochDay()]?.kg },
                    onWeight = { d -> weighing = d }, // чип веса — шторка с кругом для выбранного дня
                    onProduct = { id -> nav.navigate("product/$id") },
                    onAdd = { d, from ->
                        // Кнопка «+» разворачивается на весь экран, потом под ней открывается «Добавить»
                        if (from == null || morph.isRunning) nav.navigate("add/${d.toEpochDay()}")
                        else {
                            morphFrom = from
                            scope.launch {
                                morph.snapTo(0f); morphFade.snapTo(1f)
                                morph.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
                                nav.navigate("add/${d.toEpochDay()}")
                                morphFade.animateTo(0f, tween(220, delayMillis = 60))
                                morphFrom = null
                            }
                        }
                    },
                )
            }
            composable("add/{day}?product={product}") { entry ->
                val day = entry.arguments?.getString("day")?.toLongOrNull() ?: LocalDate.now().toEpochDay()
                val product = entry.arguments?.getString("product")?.toLongOrNull() ?: 0L
                AddFoodScreen(
                    add, LocalDate.ofEpochDay(day), onClose = { nav.popBackStack() },
                    onAllProducts = { nav.navigate("products") }, productId = product,
                )
            }
            // База продуктов (0.4)
            composable("products") {
                ProductsScreen(
                    productsVm, onBack = { nav.popBackStack() },
                    onOpen = { id -> nav.navigate("product/$id") }, onNew = { nav.navigate("productEdit/0") },
                )
            }
            composable("product/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                ProductCardScreen(
                    productsVm, id, onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("productEdit/$id") },
                    onAddToDiary = { nav.navigate("add/${LocalDate.now().toEpochDay()}?product=$id") },
                )
            }
            composable("productEdit/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                ProductEditScreen(
                    productsVm, id, onClose = { nav.popBackStack() },
                    // Новый — сразу его карточка вместо формы; правка — назад к карточке
                    onSaved = { newId -> if (id == 0L) nav.navigate("product/$newId") { popUpTo("productEdit/0") { inclusive = true } } else nav.popBackStack() },
                    onDeleted = { nav.popBackStack("products", inclusive = false).let { ok -> if (!ok) nav.popBackStack(nav.graph.startDestinationId, false) } },
                )
            }
            composable(Tab.Weight.route) { WeightScreen(weight, onOpenDay = { d -> today.setDate(d); goTab(Tab.Today) }) }
            composable(Tab.Assistant.route) { PlaceholderScreen("Ассистент", "Советы и недельный отчёт") }
            composable(Tab.Profile.route) { ProfileScreen(updates) }
        }
        // Навбар — только на вкладках
        if (Tab.entries.any { it.route == current }) {
            NavGuardZone(haze, Modifier.align(Alignment.BottomCenter))
            FloatingNavBar(
                items = Tab.entries.map { NavItem(it.label, it.icon) },
                selected = Tab.entries.indexOfFirst { it.route == current },
                onSelect = { i -> goTab(Tab.entries[i]) },
                haze = haze,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        // Разворот кнопки «+»: прямоугольник растёт из кнопки до экрана, цвет — из акцентного в фон
        morphFrom?.let { from -> FabMorph(from, morph.value, morphFade.value) }
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

/** Кнопка «+», развёрнутая на весь экран: границы и скругление 20 → 0, цвет `primary` → `surface`. */
@Composable
private fun FabMorph(from: Rect, t: Float, alpha: Float) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = LocalDensity.current
        val w = with(d) { maxWidth.toPx() }; val h = with(d) { maxHeight.toPx() }
        val l = from.left * (1 - t); val tp = from.top * (1 - t)
        val r = from.right + (w - from.right) * t; val b = from.bottom + (h - from.bottom) * t
        val c = androidx.compose.ui.graphics.lerp(colors.primary, colors.surface, (t * 1.4f - 0.4f).coerceIn(0f, 1f))
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(l.roundToInt(), tp.roundToInt()) }
                .size(with(d) { (r - l).toDp() }, with(d) { (b - tp).toDp() })
                .graphicsLayer { this.alpha = alpha }
                .clip(RoundedCornerShape((20f * (1 - t)).dp))
                .background(c),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MockIcons.PlusBold, null, Modifier.size(32.dp).graphicsLayer { this.alpha = (1 - t * 2.5f).coerceIn(0f, 1f) }, tint = colors.onPrimary)
        }
    }
}
