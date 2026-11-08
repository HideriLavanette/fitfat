package app.fitfat.ui.today

import app.fitfat.ui.components.fullScreenDialogProperties
import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fitfat.ui.common.MockIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

private val monthsNom = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
private val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/**
 * Календарь месяца (макет Today, pickOpen): окно 350 dp сверху (отступ 90), скругление 28, `surfaceContainerHigh`,
 * поля 18, скрим 50 %. Ячейки 40: выбранный день — `primary`, сегодня — обводка `primary`, будущее недоступно,
 * точка 4 dp `outline` — в этот день есть записи. Внизу «Закрыть» (текстом) / «Сегодня» (залитая `primary`, как действие в остальных окнах).
 */
@Composable
fun MonthCalendar(
    selected: LocalDate,
    daysWithEntries: (YearMonth) -> Flow<List<Long>>,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { app.fitfat.data.DayClock.today() }
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val marked by remember(month) { daysWithEntries(month) }.collectAsState(initial = emptyList())
    val colors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss, properties = fullScreenDialogProperties()) {
        // Скрим рисуем сами (50 %), системное затемнение выключаем
        app.fitfat.ui.components.DialogNoDim()
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, shadowElevation = 12.dp,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 90.dp, start = 20.dp, end = 20.dp).width(350.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${monthsNom[month.monthValue - 1]} ${month.year}", fontSize = 18.sp, fontWeight = FontWeight(650), modifier = Modifier.weight(1f))
                        IconButton(onClick = { month = month.minusMonths(1) }, modifier = Modifier.size(40.dp)) {
                            Icon(MockIcons.ChevronLeft, "Предыдущий месяц", Modifier.size(20.dp), tint = colors.onSurface)
                        }
                        val canNext = month < YearMonth.from(today)
                        IconButton(onClick = { if (canNext) month = month.plusMonths(1) }, enabled = canNext, modifier = Modifier.size(40.dp)) {
                            Icon(MockIcons.ChevronRight, "Следующий месяц", Modifier.size(20.dp), tint = if (canNext) colors.onSurface else colors.surfaceContainerHighest)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        weekDays.forEach { Text(it, fontSize = 11.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    }
                    val lead = month.atDay(1).dayOfWeek.value - 1
                    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
                    cells.chunked(7).forEach { week ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            week.forEach { d -> Box(Modifier.weight(1f)) { if (d != null) DayCell(d, d == selected, d == today, d.isAfter(today), d.toEpochDay() in marked, onPick) } }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Закрывающее — слева (правило приложения)
                        Surface(onClick = onDismiss, shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.weight(1f).height(48.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("Закрыть", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.primary) }
                        }
                        Surface(onClick = { onPick(today) }, shape = RoundedCornerShape(24.dp), color = colors.primary, modifier = Modifier.weight(1f).height(48.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("Сегодня", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, sel: Boolean, isToday: Boolean, future: Boolean, hasEntries: Boolean, onPick: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(20.dp))
            .background(if (sel) colors.primary else Color.Transparent)
            .let { if (isToday && !sel) it.border(1.dp, colors.primary, RoundedCornerShape(20.dp)) else it }
            .clickable(enabled = !future) { onPick(d) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(
            d.dayOfMonth.toString(), fontSize = 14.sp,
            fontWeight = if (sel || isToday) FontWeight.Bold else FontWeight.Medium,
            color = when { sel -> colors.onPrimary; future -> colors.outlineVariant; else -> colors.onSurface },
        )
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (hasEntries && !future && !sel) colors.outline else Color.Transparent))
    }
}
