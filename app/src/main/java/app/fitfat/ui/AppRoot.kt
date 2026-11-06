package app.fitfat.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
import app.fitfat.ui.weight.WeightDialog
import app.fitfat.ui.weight.WeightScreen
import app.fitfat.ui.weight.WeightViewModel
import app.fitfat.update.UpdateDialog
import java.time.LocalDate
import app.fitfat.update.UpdateViewModel

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "Сегодня", Icons.Outlined.ViewList),
    Weight("weight", "Вес", Icons.Outlined.ShowChart),
    Assistant("assistant", "Ассистент", Icons.Outlined.AutoAwesome),
    Profile("profile", "Профиль", Icons.Outlined.Person),
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        NavHost(navController = nav, startDestination = Tab.Today.route) {
            composable(Tab.Today.route) { TodayScreen(today, onAdd = { d -> nav.navigate("add/${d.toEpochDay()}") }) }
            composable("add/{day}") { entry ->
                val day = entry.arguments?.getString("day")?.toLongOrNull() ?: LocalDate.now().toEpochDay()
                AddFoodScreen(add, LocalDate.ofEpochDay(day), onClose = { nav.popBackStack() })
            }
            composable(Tab.Weight.route) { WeightScreen(weight) }
            composable(Tab.Assistant.route) { PlaceholderScreen("Ассистент", "Советы и недельный отчёт") }
            composable(Tab.Profile.route) { ProfileScreen(updates) }
        }
        if (current?.startsWith("add") != true) FloatingNavBar(
            current = current,
            onSelect = { tab ->
                nav.navigate(tab.route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    UpdateDialog(updates)

    // Утренний вопрос о весе: один раз в день, пока не ответили или не пропустили
    val ask by weight.askToday.collectAsStateWithLifecycle()
    val wui by weight.ui.collectAsStateWithLifecycle()
    if (ask) {
        val today = LocalDate.now()
        WeightDialog(
            date = today,
            initialKg = wui.last?.kg ?: 70.0,
            previous = wui.all.lastOrNull { it.date.isBefore(today) }?.kg,
            title = "Вес натощак",
            onSave = { weight.save(today, it) },
            onDismiss = weight::skipToday,
        )
    }
}

/** Плавающий навбар: капсула 72 dp, индикатор 56×32 по спецификации M3. */
@Composable
private fun FloatingNavBar(current: String?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .fillMaxWidth()
            .height(72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { tab ->
                val selected = current == tab.route
                val indicator by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    label = "indicator",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.width(56.dp).height(32.dp).clip(CircleShape).background(indicator),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            tab.icon, contentDescription = null, modifier = Modifier.size(24.dp),
                            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
