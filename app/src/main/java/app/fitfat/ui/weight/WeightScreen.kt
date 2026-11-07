package app.fitfat.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val rowFmt = DateTimeFormatter.ofPattern("d MMMM, EE", Locale("ru"))

@Composable
fun WeightScreen(vm: WeightViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<WeightPoint?>(null) }
    var adding by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text("Вес", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                val last = ui.last
                if (last == null) {
                    Text("Пока нет записей", style = MaterialTheme.typography.headlineSmall)
                    Text("Взвешивайтесь утром натощак — приложение само спросит вес.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formatKg(last.kg), fontSize = 48.sp, fontWeight = FontWeight.Bold)
                        Text(" кг", fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    val wc = ui.weekChange
                    Text(
                        "тренд ${formatKg(last.trend)} кг" + (wc?.let { " · " + (if (it < 0) "−" else "+") + formatKg(abs(it)) + " за неделю" } ?: ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Period.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = ui.period == p,
                        onClick = { vm.setPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                        icon = {},
                    ) { Text(p.label, maxLines = 1, fontSize = 12.sp) }
                }
            }
        }
        item { WeightChart(ui.visible, ui.period) }
        item {
            Surface(
                onClick = { adding = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Записать вес",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        val rows = ui.all.asReversed()
        items(rows, key = { it.date.toEpochDay() }) { p ->
            val idx = ui.all.indexOf(p)
            val prev = ui.all.getOrNull(idx - 1)
            WeightRow(p, prev?.let { p.kg - it.kg }) { editing = p }
        }
    }

    editing?.let { p ->
        val prev = ui.all.lastOrNull { it.date.isBefore(p.date) }?.kg
        WeightDialog(
            date = p.date, initialKg = p.kg, previous = prev, title = "Изменить вес",
            onSave = { vm.save(p.date, it); editing = null },
            onDismiss = { editing = null }, dismissLabel = "Отмена",
            onDelete = { vm.delete(p.date); editing = null },
        )
    }
    if (adding) {
        val today = LocalDate.now()
        val last = ui.last?.kg ?: 70.0
        WeightDialog(
            date = today, initialKg = ui.today?.kg ?: last, previous = ui.all.lastOrNull { it.date.isBefore(today) }?.kg,
            title = "Вес натощак",
            onSave = { vm.save(today, it); adding = false },
            onDismiss = { adding = false }, dismissLabel = "Отмена",
        )
    }
}

@Composable
private fun WeightRow(p: WeightPoint, delta: Double?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            val label = when (p.date) {
                LocalDate.now() -> "Сегодня"
                LocalDate.now().minusDays(1) -> "Вчера"
                else -> p.date.format(rowFmt)
            }
            Text(label, modifier = Modifier.weight(1f))
            Text("${formatKg(p.kg)} кг", fontWeight = FontWeight.SemiBold)
            Text(
                delta?.let { (if (it < 0) "−" else "+") + formatKg(abs(it)) } ?: "",
                modifier = Modifier.padding(start = 12.dp),
                color = if ((delta ?: 0.0) <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun WeightChart(points: List<WeightPoint>, period: Period) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        if (points.size < 2) {
            Text(
                "График появится после двух взвешиваний",
                modifier = Modifier.padding(24.dp),
                color = colors.onSurfaceVariant,
            )
            return@Surface
        }
        Canvas(Modifier.fillMaxWidth().height(200.dp).padding(16.dp)) {
            val from = LocalDate.now().minusDays(period.days - 1).toEpochDay()
            val span = (period.days - 1).coerceAtLeast(1).toFloat()
            val all = points.flatMap { listOf(it.kg, it.trend) }
            val lo = all.min() - 0.2
            val hi = all.max() + 0.2
            fun x(d: LocalDate) = (d.toEpochDay() - from) / span * size.width
            fun y(v: Double) = ((hi - v) / (hi - lo) * size.height).toFloat()
            // сетка
            for (k in 0..2) {
                val yy = size.height * k / 2f
                drawLine(colors.outlineVariant, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
            }
            // точки взвешиваний
            val dot = if (period.days > 100) 2.dp.toPx() else 3.dp.toPx()
            points.forEach { drawCircle(colors.outline, dot, Offset(x(it.date), y(it.kg))) }
            // линия тренда
            val path = Path()
            points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(p.date), y(p.trend)) else path.lineTo(x(p.date), y(p.trend)) }
            drawPath(path, colors.primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            val last = points.last()
            drawCircle(colors.primary, 5.dp.toPx(), Offset(x(last.date), y(last.trend)))
        }
    }
}
