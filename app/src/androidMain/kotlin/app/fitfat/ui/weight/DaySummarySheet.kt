package app.fitfat.ui.weight

import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.DayType
import app.fitfat.data.FoodEntry
import app.fitfat.data.Macros
import app.fitfat.data.Meal
import app.fitfat.data.macros
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import app.fitfat.ui.components.FfSheet
import app.fitfat.ui.components.WavyBar
import app.fitfat.ui.theme.MacroColors
import kotlinx.datetime.LocalDate
import app.fitfat.time.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("d MMMM")

/**
 * Сводка дня из календаря веса: дата и тип дня; вес, изменение к прошлому взвешиванию и заметка;
 * ккал из нормы и Б / Ж / У волнистыми полосками; еда по приёмам. Внизу «Изменить вес» (шторка правки)
 * и «Открыть день» (главный экран на эту дату).
 */
@Composable
fun DaySummarySheet(
    date: LocalDate,
    ui: WeightUi,
    vm: WeightViewModel,
    onDismiss: () -> Unit,
    onEditWeight: () -> Unit,
    onOpenDay: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val entries by remember(date) { vm.food(date) }.collectAsState(initial = emptyList())
    val type = remember(date, ui.marksVersion) { vm.dayType(date) }
    val norms = remember(date, ui.marksVersion) { vm.normsFor(date) }
    val eaten = entries.fold(Macros()) { a, e -> a + e.macros }
    val point = ui.byDay[date.toEpochDay()]
    val prev = ui.before(date)

    FfSheet(onDismiss = onDismiss) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Дата и тип дня
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(RuDates.dayFull(date.dayOfWeek).replaceFirstChar { it.uppercase() }, fontSize = 13.sp, color = colors.onSurfaceVariant)
                    Text(date.format(dateFmt), fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium)
                }
                TypeChip(type)
            }

            // Вес
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (point == null) {
                    Text("Вес не записан", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Можно добавить задним числом — «Записать вес».", fontSize = 13.sp, color = colors.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(formatKg(point.kg), fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight(750))
                            Text(" кг", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        prev?.let { p ->
                            val d = point.kg - p.kg
                            Text(
                                "${signedKg(d)} кг к ${p.date.format(dateFmt)}",
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = if (d <= 0.04) colors.primary else MacroColors.unknown,
                                modifier = Modifier.padding(bottom = 5.dp),
                            )
                        }
                    }
                    Text("тренд ${formatKg(point.trend)} кг", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    point.note?.let { Text("«$it»", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            }

            // КБЖУ
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt0(eaten.kcal), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (eaten.kcal > norms.kcal) MacroColors.over else colors.onSurface)
                    Text(" из ${fmt0(norms.kcal)} ккал", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }
                MacroRow("Белки", eaten.protein, norms.protein, MacroColors.protein, MacroColors.proteinContainer)
                MacroRow("Жиры", eaten.fat, norms.fat, MacroColors.fat, MacroColors.fatContainer)
                MacroRow("Углеводы", eaten.carbs, norms.carbs, MacroColors.carbs, MacroColors.carbsContainer)
            }

            // Еда
            if (entries.isEmpty()) {
                Text("Еды в этот день не записано", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            } else {
                Meal.entries.forEach { m ->
                    val list = entries.filter { Meal.of(it.meal) == m }.sortedBy { it.minute }
                    if (list.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.padding(horizontal = 4.dp)) {
                                Text(m.label, fontSize = 15.sp, fontWeight = FontWeight(650), color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text("${fmt0(list.sumOf { it.kcal })} ккал", fontSize = 13.sp, color = colors.onSurfaceVariant)
                            }
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer)) {
                                list.forEach { FoodRow(it) }
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = onEditWeight, shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHighest, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (point == null) "Записать вес" else "Изменить вес", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
            Surface(onClick = onOpenDay, shape = RoundedCornerShape(28.dp), color = colors.primary, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Открыть день", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onPrimary, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }
}

/** Тип дня: «Тренировка» / «Цикл» цветом отметки; обычный день — `secondaryContainer`. */
@Composable
private fun TypeChip(t: DayType) {
    val colors = MaterialTheme.colorScheme
    val (ink, bg, icon) = when (t) {
        DayType.Training -> Triple(MacroColors.training, MacroColors.trainingContainer, MockIcons.Dumbbell)
        DayType.Cycle -> Triple(MacroColors.cycle, MacroColors.cycleContainer, MockIcons.Drop)
        DayType.Normal -> Triple(colors.onSecondaryContainer, colors.secondaryContainer, MockIcons.Sun)
    }
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = ink)
        Text(t.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (t == DayType.Normal) ink else colors.onSurface)
    }
}

@Composable
private fun MacroRow(label: String, v: Double, goal: Double, color: Color, track: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.weight(1f))
            Text(fmt0(v), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(" / ${fmt0(goal)} г", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        WavyBar(v, goal, color, track)
    }
}

@Composable
private fun FoodRow(e: FoodEntry) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIcon(e.category, 36.dp, radius = 12.dp, iconSize = 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(e.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val amount = e.pieces?.let { "${fmt1(it)} шт" } ?: "${fmt0(e.grams)} г"
            Text("$amount · ${Fmt.hhmm(e.minute)}", fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Text("${fmt0(e.kcal)} ккал", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
