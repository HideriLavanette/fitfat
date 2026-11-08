package app.fitfat.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.DayType
import app.fitfat.data.FoodEntry
import app.fitfat.data.Macros
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.components.FfSheet
import app.fitfat.ui.components.WavyRing
import app.fitfat.ui.theme.MacroColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * День недели в отчёте: дата, тип, съедено, лимит (норма типа дня со срезом компенсации), записи;
 * [cut] — на сколько ккал снижен лимит за переборы дней [cutFrom]; [over] — перебор дня, [forgiven] — сколько из него списано.
 */
data class WeekDay(
    val date: LocalDate, val type: DayType, val eaten: Macros, val limit: Macros, val entries: List<FoodEntry>,
    val cut: Double = 0.0, val cutFrom: List<LocalDate> = emptyList(), val over: Double = 0.0, val forgiven: Double = 0.0,
)

private val ru = Locale("ru")
private val months = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "нояб", "дек")
private val dows = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private fun short(d: LocalDate) = "${d.dayOfMonth} ${months[d.monthValue - 1]}"

/**
 * «Неделя» (макет Report, 5.1): итоги за 6 прошедших дней — баланс перебор / недобор и вес за неделю со спарклайном,
 * средние Б / Ж / У относительно нормы (отметка нормы — на 75 % полосы) и чего не хватает; ниже лента карточек дней
 * (сегодня справа, крупнее, `primaryContainer`), тап — шторка дня. Лимит дня — норма его типа со срезом компенсации
 * переборов («−N к лимиту» на карточке, пояснение в шторке и под лентой). [weights] — вес по дням недели (может не быть).
 */
@Composable
fun WeekScreen(days: List<WeekDay>, weights: List<Pair<LocalDate, Double>>, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf<WeekDay?>(null) }
    val today = days.last()
    val past = days.dropLast(1)
    val withFood = past.filter { it.entries.isNotEmpty() }
    Column(
        Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(onClick = onBack, shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(MockIcons.ChevronLeft, "Назад", Modifier.size(24.dp), tint = colors.onSurface) }
            }
            Text("Неделя", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${short(days.first().date)} — ${short(today.date)}", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        }
        // Итоги недели
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainer).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val bal = withFood.sumOf { it.eaten.kcal - it.limit.kcal }
                val over = bal > 0
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (over) colors.errorContainer.copy(alpha = 0.35f) else colors.surfaceContainerHigh).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Баланс за ${withFood.size} ${dayWord(withFood.size)}", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    Text(if (withFood.isEmpty()) "—" else (if (over) "+" else "−") + fmt0(abs(bal)) + " ккал", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = if (over) colors.error else colors.onSurface)
                    Text(
                        if (withFood.isEmpty()) "нет записей" else (if (over) "перебор · " else "недобор · ") + fmt0(abs(bal) / withFood.size) + " в день",
                        fontSize = 12.sp, color = colors.onSurfaceVariant,
                    )
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerHigh).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Вес за неделю", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    if (weights.size >= 2) {
                        val d = weights.last().second - weights.first().second
                        Text((if (d < 0) "−" else "+") + "%.1f".format(ru, abs(d)) + " кг", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                        Sparkline(weights.map { it.second }, Modifier.fillMaxWidth().height(26.dp))
                    } else {
                        Text("—", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
                        Text("мало взвешиваний", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
            // В среднем в день
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("В среднем в день", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                val notes = mutableListOf<String>()
                listOf(Triple("Белки", MacroColors.protein, 0), Triple("Жиры", MacroColors.fat, 1), Triple("Углеводы", MacroColors.carbs, 2)).forEach { (label, c, k) ->
                    fun pick(m: Macros) = when (k) { 0 -> m.protein; 1 -> m.fat; else -> m.carbs }
                    val n = withFood.size.coerceAtLeast(1)
                    val avg = withFood.sumOf { pick(it.eaten) } / n
                    // Норма — средняя по прошедшим дням, даже без записей
                    val norm = past.sumOf { pick(it.limit) } / past.size.coerceAtLeast(1)
                    val lowP = k == 0 && withFood.isNotEmpty() && avg < norm - 3
                    val high = k != 0 && withFood.isNotEmpty() && avg > norm * 1.05
                    if (lowP) notes += "белка не хватает ~${fmt0(norm - avg)} г в день"
                    if (high) notes += "${if (k == 1) "жиров" else "углеводов"} больше нормы на ~${fmt0(avg - norm)} г"
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c, modifier = Modifier.width(72.dp))
                        BoxWithConstraints(Modifier.weight(1f).height(14.dp), contentAlignment = Alignment.CenterStart) {
                            val r = if (norm > 0) avg / norm else 0.0
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.surfaceContainerHighest))
                            Box(Modifier.width(maxWidth * min(1.0, r * 0.75).toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (lowP || high) colors.error else c))
                            // Отметка нормы — на 75 % полосы
                            Box(Modifier.padding(start = maxWidth * 0.75f).width(2.dp).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(colors.onSurface.copy(alpha = 0.6f)))
                        }
                        Text("${fmt0(avg)} / ${fmt0(norm)} г", fontSize = 13.sp, color = if (lowP || high) colors.error else colors.onSurface, modifier = Modifier.width(80.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
                if (notes.isNotEmpty()) Text("Обратите внимание: ${notes.joinToString(", ")}.", fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text("По дням", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("листайте влево — прошлые дни", fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        // Лента дней: сегодня справа; начинаем с конца
        LazyRow(
            Modifier.padding(horizontal = 0.dp).layoutBleed(16), reverseLayout = true,
            horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        ) {
            items(days.reversed(), key = { it.date.toEpochDay() }) { d -> DayCard(d, d == today) { open = d } }
        }
        Text(balanceNote(past), fontSize = 12.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
    open?.let { d -> DaySheet(d, d == today) { open = null } }
}

/** Подпись под лентой (макет): куда делся перебор недели и сколько списано. */
private fun balanceNote(past: List<WeekDay>): String {
    val over = past.sumOf { it.over }
    val forgiven = past.sumOf { it.forgiven }
    val base = "Баланс — по прошедшим дням с записями, сегодня не входит. Лимит дня — норма его типа (тренировка, цикл)"
    if (over < 1) return "$base. Переборов не было — лимиты не снижались."
    return "$base. Перебор за неделю ${fmt0(over)} ккал разнесён на следующие дни своей недели (пн — вс) — не больше 10 % нормы в день, только за счёт жиров и углеводов." +
        if (forgiven >= 1) " Остаток ${fmt0(forgiven)} ккал списан, чтобы не урезать себя слишком сильно." else ""
}

private val fromDay = DateTimeFormatter.ofPattern("EEEE", ru)

/** «Лимит снижен на 120 ккал за перебор (понедельник, вторник) — урезаны жиры и углеводы, белок прежний». */
private fun cutText(d: WeekDay) =
    "Лимит снижен на ${fmt0(d.cut)} ккал за перебор (${d.cutFrom.distinct().joinToString(", ") { it.format(fromDay) }}) — урезаны жиры и углеводы, белок прежний"

private fun dayWord(n: Int) = if (n % 10 == 1 && n % 100 != 11) "день" else if (n % 10 in 2..4 && n % 100 !in 12..14) "дня" else "дней"

/** Лента — от края до края экрана (поля 16). */
private fun Modifier.layoutBleed(dp: Int) = this.then(
    Modifier.layout { m, c ->
        val extra = (dp * 2).dp.roundToPx()
        val pl = m.measure(c.copy(maxWidth = c.maxWidth + extra, minWidth = c.minWidth + extra))
        layout(c.maxWidth, pl.height) { pl.place(-extra / 2, 0) }
    },
)

/** Спарклайн веса: линия 2 `primary`, последняя точка 3. */
@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier) {
    val c = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val lo = values.min() - 0.05; val hi = values.max() + 0.05
        fun x(i: Int) = 4f + i * (size.width - 8f) / max(1, values.size - 1)
        fun y(v: Double) = (3 + (hi - v) / (hi - lo) * (size.height - 6)).toFloat()
        val p = Path()
        values.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(i), y(v)) else p.lineTo(x(i), y(v)) }
        drawPath(p, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(c, 3.dp.toPx(), Offset(x(values.lastIndex), y(values.last())))
    }
}

/** Карточка дня: 136 (сегодня 168, `primaryContainer` с рамкой `primary`), кольцо с ккал, «из N», перебор / недобор, Б / Ж / У. */
@Composable
private fun DayCard(d: WeekDay, today: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val over = !today && d.eaten.kcal > d.limit.kcal
    val ink = if (today) colors.onPrimaryContainer else colors.onSurface
    val sub = if (today) colors.onPrimaryContainer else colors.onSurfaceVariant
    Column(
        Modifier.width(if (today) 168.dp else 136.dp).heightIn(min = 318.dp).clip(RoundedCornerShape(24.dp))
            .background(if (today) colors.primaryContainer else colors.surfaceContainer)
            .then(if (today) Modifier.border(2.dp, colors.primary, RoundedCornerShape(24.dp)) else Modifier)
            .clickable(onClick = onClick).padding(14.dp)
            .semantics { contentDescription = "${short(d.date)}: ${fmt0(d.eaten.kcal)} из ${fmt0(d.limit.kcal)} ккал" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (today) "Сегодня" else dows[d.date.dayOfWeek.value - 1], fontSize = if (today) 18.sp else 15.sp, fontWeight = if (today) FontWeight.Bold else FontWeight.SemiBold, color = ink, modifier = Modifier.weight(1f))
            if (d.type != DayType.Normal) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(if (d.type == DayType.Cycle) MacroColors.cycleContainer else MacroColors.trainingContainer), contentAlignment = Alignment.Center) {
                    Icon(if (d.type == DayType.Cycle) MockIcons.Drop else MockIcons.Dumbbell, null, Modifier.size(14.dp), tint = if (d.type == DayType.Cycle) MacroColors.cycle else MacroColors.training)
                }
            }
        }
        Text(short(d.date), fontSize = 12.sp, color = sub, modifier = Modifier.padding(top = 0.dp))
        val s = if (today) 124.dp else 104.dp
        Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp).size(s), contentAlignment = Alignment.Center) {
            WavyRing(
                fraction = if (d.limit.kcal > 0) min(1.0, d.eaten.kcal / d.limit.kcal).toFloat() else 0f,
                color = if (today) colors.onPrimaryContainer else colors.primary,
                track = if (today) colors.onPrimaryContainer.copy(alpha = 0.22f) else colors.secondaryContainer,
                modifier = Modifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmt0(d.eaten.kcal), fontSize = if (today) 24.sp else 20.sp, fontWeight = FontWeight.SemiBold, color = if (over) colors.error else ink)
                Text("ккал", fontSize = 11.sp, color = sub)
            }
        }
        Text("из ${fmt0(d.limit.kcal)} ккал", fontSize = 12.sp, color = sub)
        val diff = d.eaten.kcal - d.limit.kcal
        Text(
            when {
                today -> "осталось ${fmt0(max(0.0, -diff))}"
                d.entries.isEmpty() -> "нет записей"
                diff > 0 -> "перебор +${fmt0(diff)}"
                else -> "недобор −${fmt0(-diff)}"
            },
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (today) colors.onPrimaryContainer else if (over) colors.error else colors.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MacroLine("Б", MacroColors.protein, d.eaten.protein, d.limit.protein, sub)
            MacroLine("Ж", MacroColors.fat, d.eaten.fat, d.limit.fat, sub)
            MacroLine("У", MacroColors.carbs, d.eaten.carbs, d.limit.carbs, sub)
        }
        if (d.cut >= 1) Text("−${fmt0(d.cut)} к лимиту", fontSize = 11.sp, lineHeight = 14.sp, color = sub)
    }
}

@Composable
private fun MacroLine(letter: String, c: Color, v: Double, lim: Double, ink: Color) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = c, fontWeight = FontWeight.Bold)) { append(letter) }
            append(" ${fmt0(v)} / ${fmt0(lim)}")
        },
        fontSize = 12.sp, color = ink,
    )
}

private val sheetDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)

/** Шторка дня: заголовок с типом, 4 ячейки «съедено / лимит» (перебор ккал, жиров, углеводов > 3 % — `error`), записи, «Закрыть». */
@Composable
private fun DaySheet(d: WeekDay, today: Boolean, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FfSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(d.date.format(sheetDate).replaceFirstChar { it.uppercase() }, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (d.type != DayType.Normal) {
                val cyc = d.type == DayType.Cycle
                Row(
                    Modifier.height(24.dp).clip(RoundedCornerShape(8.dp)).background(if (cyc) MacroColors.cycleContainer else MacroColors.trainingContainer).padding(start = 6.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(if (cyc) MockIcons.Drop else MockIcons.Dumbbell, null, Modifier.size(14.dp), tint = if (cyc) MacroColors.cycle else MacroColors.training)
                    Text(if (cyc) "Цикл" else "Тренировка", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (cyc) MacroColors.cycle else MacroColors.training)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Triple("Ккал", colors.onSurface, d.eaten.kcal to d.limit.kcal), Triple("Белки", MacroColors.protein, d.eaten.protein to d.limit.protein),
                Triple("Жиры", MacroColors.fat, d.eaten.fat to d.limit.fat), Triple("Углев.", MacroColors.carbs, d.eaten.carbs to d.limit.carbs),
            ).forEach { (label, c, vl) ->
                val (v, lim) = vl
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainer).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, fontSize = 11.sp, color = c)
                    Text(fmt0(v), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (label != "Белки" && v > lim * 1.03) colors.error else colors.onSurface)
                    Text("из ${fmt0(lim)}", fontSize = 11.sp, color = colors.onSurfaceVariant)
                }
            }
        }
        if (d.cut >= 1) Text(cutText(d), fontSize = 13.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (d.entries.isEmpty()) Text("Записей нет", fontSize = 14.sp, color = colors.onSurfaceVariant)
            d.entries.forEach { e ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(e.name, fontSize = 15.sp)
                        Text("${fmt0(e.grams)} г · %02d:%02d".format(e.minute / 60, e.minute % 60), fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                    Text(fmt0(e.kcal), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Surface(onClick = onDismiss, shape = RoundedCornerShape(26.dp), color = colors.surfaceContainerHighest, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("Закрыть", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        }
    }
}
