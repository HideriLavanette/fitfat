package app.fitfat.ui.setup

import app.fitfat.ui.components.FfAlert

import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import app.fitfat.ui.components.PopDrumChip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.DayClock
import app.fitfat.data.PlanMode
import app.fitfat.data.ShiftInner
import app.fitfat.data.TRAINING_TYPES
import app.fitfat.data.TrainingLog
import app.fitfat.data.TrainingPlan
import app.fitfat.data.trainingLabel
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.components.FfSheet
import app.fitfat.ui.components.GramRuler
import app.fitfat.ui.components.PillToggle
import app.fitfat.ui.components.tick
import app.fitfat.ui.theme.MacroColors
import kotlinx.datetime.LocalDate
import app.fitfat.time.DateTimeFormatter

private val dayLong = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val dateShort = DateTimeFormatter.ofPattern("d MMMM yyyy")
private val dn = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val months = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "нояб", "дек")

private fun word(n: Int, a: String, b: String, c: String) =
    if (n % 10 == 1 && n % 100 != 11) a else if (n % 10 in 2..4 && n % 100 !in 12..14) b else c

/**
 * «Тренировки» (макет Training, 5.8): чем занимаетесь (чипы), расписание — «Дни недели / Через N дней / Вахта»,
 * дата начала, предпросмотр ближайших 4 недель и «≈ N тренировок в месяц»; история и «Записать тренировку».
 * Виды занятий — те же, что в первой настройке (силовые = зал). Расписание и виды сохраняются сразу.
 * В дни по расписанию главный экран сам ставит тип дня «Тренировка» (своя отметка дня главнее).
 * Вёрстка (09.11.2026, правка автора поверх макета — «больше воздуха, чёткая иерархия»): разделы через 32, заголовки
 * разделов 18 / 600, подписи 13 `onSurfaceVariant`; в карточке расписания поля 20 и блоки через 24; календарь с шапкой
 * дней недели, дни отдыха — без заливки, залиты только тренировки.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    types: Set<String>, onTypes: (Set<String>) -> Unit,
    plan: TrainingPlan?, onPlan: (TrainingPlan) -> Unit,
    log: List<TrainingLog>, onLog: (String, Int) -> Unit, onDelete: (TrainingLog) -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val today = DayClock.today()
    // Расписания ещё нет — дни не выбраны: превью и кружки совпадают, первый тап создаёт расписание
    val p = plan ?: TrainingPlan(days = emptySet(), start = today)
    var logging by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TrainingLog?>(null) }
    var picking by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(colors.surface)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(onClick = onBack, shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(MockIcons.ChevronLeft, "Назад", Modifier.size(24.dp), tint = colors.onSurface) }
                }
                Text("Тренировки", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            // Чем занимаетесь
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("Чем занимаетесь")
                // Маленькие карточки-теги с иконкой вида (09.11.2026, правка автора): выбранные — цветом тренировки
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TRAINING_TYPES.forEach { (id, label, _) ->
                        val sel = id in types
                        val bg by animateColorAsState(if (sel) MacroColors.trainingContainer else colors.surfaceContainer, label = "tt")
                        val ink by animateColorAsState(if (sel) MacroColors.training else colors.onSurfaceVariant, label = "ti")
                        Surface(
                            onClick = { onTypes(if (sel) types - id else types + id) }, shape = RoundedCornerShape(14.dp), color = bg,
                            border = BorderStroke(1.dp, if (sel) MacroColors.training.copy(alpha = 0.5f) else Color.Transparent),
                            modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = label + if (sel) ", выбрано" else "" },
                        ) {
                            Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) MacroColors.training.copy(alpha = 0.16f) else colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                                    Icon(trainingIcon(id), null, Modifier.size(17.dp), tint = ink)
                                }
                                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (sel) MacroColors.training else colors.onSurface)
                            }
                        }
                    }
                }
            }
            // Расписание
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val cells = (0 until 28).map { monday.plusDays(it.toLong()) }
            val count = cells.count { plan != null && p.kind(it) == TrainingPlan.Kind.Train }
            val perMonth = Math.round(count / 28.0 * 30).toInt()
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionTitle("Расписание")
                    Text(
                        if (plan == null) "Не задано" else "≈ $perMonth ${word(perMonth, "тренировка", "тренировки", "тренировок")} в месяц",
                        fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                PillToggle(PlanMode.entries.map { it.label }, p.mode.ordinal, onSelect = { onPlan(p.copy(mode = PlanMode.entries[it])) }, modifier = Modifier.fillMaxWidth(), height = 44.dp, fontSize = 13.sp)
                AnimatedVisibility(p.mode == PlanMode.Interval, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stepper("Дней тренировок подряд", null, p.trainDays, 1, 6) { onPlan(p.copy(trainDays = it)) }
                        Stepper("Затем дней отдыха", null, p.restDays, 1, 7) { onPlan(p.copy(restDays = it)) }
                        Text("${p.trainDays} ${word(p.trainDays, "день", "дня", "дней")} тренировок, ${p.restDays} ${word(p.restDays, "день", "дня", "дней")} отдыха — и по кругу", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                }
                AnimatedVisibility(p.mode == PlanMode.Shift, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stepper("Активный период", "дней, когда тренируетесь", p.onDays, 1, 60) { onPlan(p.copy(onDays = it)) }
                        Stepper("Без тренировок", "вахта, отпуск, перерыв", p.offDays, 1, 60) { onPlan(p.copy(offDays = it)) }
                        Text("В активный период тренируюсь", fontSize = 13.sp, color = colors.onSurfaceVariant)
                        PillToggle(ShiftInner.entries.map { it.label }, p.inner.ordinal, onSelect = { onPlan(p.copy(inner = ShiftInner.entries[it])) }, modifier = Modifier.fillMaxWidth(), height = 36.dp, fontSize = 12.sp)
                    }
                }
                AnimatedVisibility(p.mode == PlanMode.Week || (p.mode == PlanMode.Shift && p.inner == ShiftInner.Week), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Дни недели", fontSize = 13.sp, color = colors.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            dn.forEachIndexed { i, label ->
                                val sel = i in p.days
                                val bg by animateColorAsState(if (sel) MacroColors.trainingContainer else colors.surfaceContainerHigh, label = "dw")
                                Box(
                                    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(bg)
                                        .clickable { onPlan(p.copy(days = if (sel) p.days - i else p.days + i)) }
                                        .semantics { contentDescription = label + if (sel) ", тренировка" else "" },
                                    contentAlignment = Alignment.Center,
                                ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (sel) MacroColors.training else colors.onSurfaceVariant) }
                            }
                        }
                    }
                }
                AnimatedVisibility(p.mode != PlanMode.Week) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (p.mode == PlanMode.Shift) "Активный период начинается" else "Первая тренировка", fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Surface(onClick = { picking = true }, shape = RoundedCornerShape(14.dp), color = colors.surfaceContainerHigh, modifier = Modifier.height(44.dp)) {
                            Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(p.start.format(dateShort), fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                        }
                    }
                }
                // Ближайшие 4 недели
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ближайшие 4 недели", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dn.forEach { Text(it, fontSize = 11.sp, color = colors.outline, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
                    }
                    cells.chunked(7).forEach { week ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            week.forEach { d ->
                                val k = if (plan == null) TrainingPlan.Kind.Rest else p.kind(d)
                                val isToday = d == today
                                val shape = RoundedCornerShape(12.dp)
                                val m = Modifier.weight(1f).height(36.dp).clip(shape)
                                // Залиты только тренировки; отдых — просто число, перерыв — пунктирно-бледный контур, сегодня — обводка
                                Box(
                                    when (k) {
                                        TrainingPlan.Kind.Train -> m.background(MacroColors.trainingContainer).border(if (isToday) 1.5.dp else 0.dp, MacroColors.training, shape)
                                        TrainingPlan.Kind.Off -> m.border(1.dp, colors.outlineVariant, shape)
                                        TrainingPlan.Kind.Rest -> m.border(1.5.dp, if (isToday) colors.primary else Color.Transparent, shape)
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (d.dayOfMonth == 1) "1 ${months[d.monthValue - 1]}" else d.dayOfMonth.toString(), fontSize = 12.sp, maxLines = 1,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = when (k) { TrainingPlan.Kind.Train -> MacroColors.training; TrainingPlan.Kind.Off -> colors.outline; else -> colors.onSurfaceVariant },
                                    )
                                }
                            }
                        }
                    }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(Modifier.background(MacroColors.trainingContainer), "тренировка")
                        if (p.mode == PlanMode.Shift) Legend(Modifier.border(1.dp, colors.outlineVariant, RoundedCornerShape(4.dp)), "перерыв")
                        Legend(Modifier.border(1.5.dp, colors.primary, RoundedCornerShape(4.dp)), "сегодня")
                    }
                }
            }
                Text(
                    "В дни тренировок главный экран сам предложит тип дня «Тренировка» с повышенной нормой углеводов.",
                    fontSize = 12.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            // История
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionTitle("История")
                if (log.isEmpty()) {
                    Text("Пока пусто — записывайте тренировки кнопкой ниже.", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                } else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerLow).padding(vertical = 4.dp)) {
                    log.take(20).forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { deleting = l }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MacroColors.trainingContainer), contentAlignment = Alignment.Center) {
                                Icon(MockIcons.Dumbbell, null, Modifier.size(18.dp), tint = MacroColors.training)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(trainingLabel(l.type), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(LocalDate.ofEpochDay(l.epochDay).format(dayLong), fontSize = 12.sp, color = colors.onSurfaceVariant)
                            }
                            Text("${l.minutes} мин", fontSize = 14.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Surface(
            onClick = { logging = true }, shape = RoundedCornerShape(28.dp), color = colors.primary,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).fillMaxWidth().height(56.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(MockIcons.Plus, null, Modifier.size(20.dp), tint = colors.onPrimary)
                Text("Записать тренировку", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onPrimary)
            }
        }
    }
    if (logging) LogSheet(types, onDismiss = { logging = false }) { t, m -> onLog(t, m); logging = false }
    deleting?.let { l ->
        FfAlert(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить запись?") },
            text = { Text("${trainingLabel(l.type)}, ${LocalDate.ofEpochDay(l.epochDay).format(dayLong)} · ${l.minutes} мин") },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } },
            confirmButton = { TextButton(onClick = { onDelete(l); deleting = null }) { Text("Удалить", color = colors.error) } },
        )
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = p.start.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Отмена") } },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPlan(p.copy(start = LocalDate.ofEpochDay(it.floorDiv(86_400_000L)))) }
                    picking = false
                }) { Text("Готово") }
            },
        ) { DatePicker(state) }
    }
}

/** Иконка вида занятий. */
private fun trainingIcon(id: String) = when (id) {
    "strength" -> MockIcons.Dumbbell
    "cardio" -> MockIcons.Cardio
    "run" -> MockIcons.Run
    "swim" -> MockIcons.Swim
    "fight" -> MockIcons.Fight
    "team" -> MockIcons.Team
    "bike" -> MockIcons.Bike
    "yoga" -> MockIcons.Yoga
    else -> MockIcons.Dumbbell
}

/** Заголовок раздела: 18 / 600, отступ 4 от края. */
@Composable
private fun SectionTitle(text: String) =
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp))

@Composable
private fun Legend(box: Modifier, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).then(box))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Число дней — чип с всплывающим барабаном (как чипы Б / Ж / У нормы, решение автора 08.11.2026 вместо «− / +»):
 * тап — барабан открыт до «Готово», удержание — крутите, пока держите; сбоку бабл со значением. Шаг 1, от [min] до [max].
 */
@Composable
private fun Stepper(label: String, sub: String?, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Row(Modifier.width(76.dp)) {
            PopDrumChip(
                value.toDouble(), { onChange(it.toInt().coerceIn(min, max)) }, "дн", colors.onSecondaryContainer, colors.secondaryContainer, label,
                stepFor = { 1.0 }, display = { it.toInt().toString() }, min = min.toDouble(), max = max.toDouble(),
            )
        }
    }
}

/** «Записать тренировку»: вид (из своих, иначе все), минуты линейкой. Пишется на сегодня. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogSheet(types: Set<String>, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val list = TRAINING_TYPES.filter { types.isEmpty() || it.first in types }
    var type by remember { mutableStateOf(list.first().first) }
    var minutes by remember { mutableStateOf(60) }
    FfSheet(onDismiss = onDismiss) {
        Text("Тренировка сегодня", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { (id, label, _) ->
                val sel = id == type
                Surface(onClick = { type = id }, shape = RoundedCornerShape(if (sel) 16.dp else 8.dp), color = if (sel) MacroColors.trainingContainer else colors.surfaceContainerHigh, modifier = Modifier.height(36.dp)) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (sel) MacroColors.training else colors.onSurface) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text("$minutes", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(" мин", fontSize = 15.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        GramRuler(minutes, { minutes = it.coerceAtLeast(5) }, max = 300)
        Text("День отметится как тренировка — с повышенной нормой углеводов.", fontSize = 12.sp, color = colors.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = onDismiss, shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerHighest, modifier = Modifier.weight(1f).height(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Отмена", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            }
            Surface(onClick = { onSave(type, minutes) }, shape = RoundedCornerShape(24.dp), color = colors.primary, modifier = Modifier.weight(1f).height(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Записать", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.onPrimary) }
            }
        }
    }
}
