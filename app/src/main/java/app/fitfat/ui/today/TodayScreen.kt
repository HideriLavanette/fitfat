package app.fitfat.ui.today

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitfat.data.DayType
import app.fitfat.data.FoodEntry
import app.fitfat.data.Meal
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.WavyBar
import app.fitfat.ui.common.WavyRing
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import app.fitfat.ui.theme.MacroColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(vm: TodayViewModel, onAdd: (LocalDate) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val deleted by vm.lastDeleted.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Pair<FoodEntry, Boolean>?>(null) } // запись, режим повтора
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        val e = deleted ?: return@LaunchedEffect
        val r = snack.showSnackbar("«${e.name}» удалён", actionLabel = "Отмена", duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearUndo()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { DateCapsule(ui.date, onShift = vm::shiftDay, onPick = { picking = true }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayType.entries.forEach { t ->
                        FilterChip(
                            selected = ui.dayType == t,
                            onClick = { vm.setDayType(t) },
                            label = { Text(t.label) },
                            leadingIcon = { Icon(if (t == DayType.Training) Icons.Outlined.FitnessCenter else Icons.Outlined.WbSunny, null, Modifier.size(18.dp)) },
                            colors = if (t == DayType.Training) FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1F2A40), selectedLabelColor = MacroColors.training, selectedLeadingIconColor = MacroColors.training,
                            ) else FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }
            item { ProgressCard(ui) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Дневник", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(selected = !ui.groupByTime, onClick = { vm.setGroupByTime(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) { Text("Приёмы", fontSize = 13.sp) }
                        SegmentedButton(selected = ui.groupByTime, onClick = { vm.setGroupByTime(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) { Text("По времени", fontSize = 13.sp) }
                    }
                }
            }
            if (ui.entries.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Пока ничего не записано", style = MaterialTheme.typography.titleMedium)
                            Text("Нажмите «+» и напишите, что съели: «гречка 200, грудка 150»", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            // Группы: новые сверху. Записи уже отсортированы по времени по убыванию.
            val groups = ui.entries.groupBy { if (ui.groupByTime) "%02d:%02d".format(it.minute / 60, it.minute % 60) else Meal.of(it.meal).label }
            groups.forEach { (title, list) ->
                item(key = "h_$title") {
                    Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp)) {
                        Text(
                            title + if (ui.groupByTime) " · " + Meal.of(list.first().meal).label else "",
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                        )
                        Text("${fmt0(list.sumOf { it.kcal })} ккал", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(list, key = { it.id }) { e ->
                    EntryRow(e, showTime = !ui.groupByTime,
                        onTap = { editing = e to false },
                        onRepeat = { editing = e to true },
                        onDelete = { vm.delete(e) })
                }
            }
        }

        LargeFloatingActionButton(
            onClick = { onAdd(ui.date) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 120.dp),
        ) { Icon(Icons.Outlined.Add, contentDescription = "Добавить еду", modifier = Modifier.size(32.dp)) }

        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 210.dp))
    }

    editing?.let { (e, repeatMode) ->
        EntrySheet(
            entry = e, repeatMode = repeatMode,
            onDismiss = { editing = null },
            onSave = { g, pcs -> if (repeatMode) vm.repeat(e, g, pcs) else vm.changePortion(e, g, pcs); editing = null },
            onDelete = { vm.delete(e); editing = null },
            onSwitchToRepeat = { editing = e to true },
        )
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = ui.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("Открыть") }
            },
            dismissButton = { TextButton(onClick = { vm.setDate(LocalDate.now()); picking = false }) { Text("Сегодня") } },
        ) { DatePicker(state) }
    }
}

/** Капсула даты: свайп влево — вчера, вправо — вперёд (не дальше сегодня), тап — календарь. */
@Composable
private fun DateCapsule(date: LocalDate, onShift: (Long) -> Unit, onPick: () -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    val shown by animateFloatAsState(drag, label = "drag")
    val today = LocalDate.now()
    val title = when (date) { today -> "Сегодня"; today.minusDays(1) -> "Вчера"; today.minusDays(2) -> "Позавчера"; else -> date.format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))) }
    Surface(
        onClick = onPick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(60.dp).pointerInput(date) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (drag < -120f) onShift(-1) else if (drag > 120f && date.isBefore(today)) onShift(1)
                    drag = 0f
                },
                onDragCancel = { drag = 0f },
            ) { change, d -> change.consume(); drag = (drag + d).coerceIn(-300f, 300f) }
        },
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.outline)
            Column(Modifier.weight(1f).offset { IntOffset(shown.roundToInt() / 2, 0) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(date.format(longDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = if (date.isBefore(today)) MaterialTheme.colorScheme.outline else Color.Transparent)
        }
    }
}

@Composable
private fun ProgressCard(ui: TodayUi) {
    val n = ui.norms; val e = ui.eaten
    val over = e.kcal > n.kcal
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                WavyRing(
                    fraction = if (n.kcal > 0) (e.kcal / n.kcal).toFloat() else 0f,
                    color = MaterialTheme.colorScheme.primary, track = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (over) "+" + fmt0(e.kcal - n.kcal) else fmt0(n.kcal - e.kcal),
                        fontSize = 48.sp, fontWeight = FontWeight.Bold,
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(if (over) "ккал сверх нормы" else "ккал осталось", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${fmt0(e.kcal)} из ${fmt0(n.kcal)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            MacroBar("Белки", e.protein, n.protein, MacroColors.protein, MacroColors.proteinContainer)
            MacroBar("Жиры", e.fat, n.fat, MacroColors.fat, MacroColors.fatContainer)
            MacroBar("Углеводы", e.carbs, n.carbs, MacroColors.carbs, MacroColors.carbsContainer)
        }
    }
}

@Composable
private fun MacroBar(label: String, v: Double, goal: Double, color: Color, track: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${fmt0(v)} / ${fmt0(goal)} г", color = if (v > goal * 1.05) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        WavyBar(if (goal > 0) (v / goal).toFloat() else 0f, color, track)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryRow(e: FoodEntry, showTime: Boolean, onTap: () -> Unit, onRepeat: () -> Unit, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        when (it) {
            SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
            SwipeToDismissBoxValue.StartToEnd -> { onRepeat(); false } // строка возвращается на место
            SwipeToDismissBoxValue.Settled -> false
        }
    })
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val dir = state.dismissDirection
            val bg = when (dir) { SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer; SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer; else -> Color.Transparent }
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (dir == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start,
            ) {
                if (dir == SwipeToDismissBoxValue.StartToEnd) { Icon(Icons.Outlined.Repeat, null); Spacer(Modifier.size(8.dp)); Text("Повторить") }
                if (dir == SwipeToDismissBoxValue.EndToStart) { Text("Удалить"); Spacer(Modifier.size(8.dp)); Icon(Icons.Outlined.Delete, null) }
            }
        },
    ) {
        Surface(onClick = onTap, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryIcon(e.category)
                Column(Modifier.weight(1f)) {
                    Text(e.name, fontWeight = FontWeight.SemiBold)
                    Text("Б ${fmt1(e.protein)} · Ж ${fmt1(e.fat)} · У ${fmt1(e.carbs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmt0(e.kcal), fontWeight = FontWeight.SemiBold)
                    val amount = e.pieces?.let { "${fmt1(it)} шт" } ?: "${fmt0(e.grams)} г"
                    Text(amount + if (showTime) " · %02d:%02d".format(e.minute / 60, e.minute % 60) else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
