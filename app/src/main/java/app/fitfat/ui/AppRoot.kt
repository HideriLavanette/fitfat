package app.fitfat.ui

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
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize()) {
        // Всё содержимое — источник для блюра навбара и зоны под ним
        NavHost(navController = nav, startDestination = Tab.Today.route, modifier = Modifier.fillMaxSize().haze(haze)) {
            composable(Tab.Today.route) {
                TodayScreen(
                    today, weightFor = { d -> wuiTop.byDay[d.toEpochDay()]?.kg },
                    onWeight = { d -> weighing = d }, // чип веса — шторка с кругом для выбранного дня
                    onAdd = { d -> nav.navigate("add/${d.toEpochDay()}") },
                )
            }
            composable("add/{day}") { entry ->
                val day = entry.arguments?.getString("day")?.toLongOrNull() ?: LocalDate.now().toEpochDay()
                AddFoodScreen(add, LocalDate.ofEpochDay(day), onClose = { nav.popBackStack() })
            }
            composable(Tab.Weight.route) { WeightScreen(weight, onOpenDay = { d -> today.setDate(d); goTab(Tab.Today) }) }
            composable(Tab.Assistant.route) { PlaceholderScreen("Ассистент", "Советы и недельный отчёт") }
            composable(Tab.Profile.route) { ProfileScreen(updates) }
        }
        if (current?.startsWith("add") != true) {
            NavGuardZone(haze, Modifier.align(Alignment.BottomCenter))
            FloatingNavBar(
                items = Tab.entries.map { NavItem(it.label, it.icon) },
                selected = Tab.entries.indexOfFirst { it.route == current },
                onSelect = { i -> goTab(Tab.entries[i]) },
                haze = haze,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
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
