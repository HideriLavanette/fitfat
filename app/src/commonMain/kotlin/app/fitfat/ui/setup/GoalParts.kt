package app.fitfat.ui.setup

import app.fitfat.ui.components.tick
import app.fitfat.ui.components.bump
import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.Goal
import app.fitfat.data.Macros
import app.fitfat.data.NormsCalc
import app.fitfat.data.NormsPlan
import app.fitfat.data.Profile
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.components.tick
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import app.fitfat.time.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private fun fmt1(v: Double) = Fmt.f1(v)

private val etaFmt = DateTimeFormatter.ofPattern("d MMMM yyyy")

/**
 * Целевой вес (08.11.2026, решение автора вместо «− / +»): число 32 сверху, под ним линейка с рисками, как вес порции
 * в редактировании продуктов ([GramRuler]): шаг 0,5 кг, каждая целая риска длиннее;
 * при похудении от 35 кг до текущего веса, при наборе от текущего до 250 кг. Срок — по реальному тренду веса за
 * последние 2 недели: «Цель ≈ 12 марта 2027 · 17 нед. при темпе −0,3 кг/нед».
 */
@Composable
internal fun TargetCard(target: Double, weight: Double, gain: Boolean, trend: List<Pair<LocalDate, Double>>, onChange: (Double) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val w = (weight * 2).roundToInt() / 2.0
    // Линейка считает в половинах килограмма
    val lo = if (gain) (w * 2).toInt() + 1 else 70
    val hi = if (gain) 500 else ((w * 2).toInt() - 1).coerceAtLeast(70)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Целевой вес", fontSize = 13.sp, color = colors.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmt1(target), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                Text(" кг", fontSize = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
        app.fitfat.ui.components.GramRuler(
            (target * 2).roundToInt().coerceIn(lo, hi), { onChange(it / 2.0) }, min = lo, max = hi, label = "Целевой вес",
            next = { v, d -> v + d }, stepOf = { 1 }, major = 2, state = { "${fmt1(it / 2.0)} кг" }, bg = colors.surfaceContainerHigh,
        )
        Text(targetEta(target, weight, trend), fontSize = 14.sp)
    }
}

private val proteinSteps = listOf(1.6, 1.7, 1.8, 1.9, 2.0, 2.1, 2.2)

/** Белок на кг — горизонтальный барабан 1,6–2,2 (макет Goals). */
@Composable
internal fun ProteinDrum(value: Double, onChange: (Double) -> Unit) =
    HorizontalDrum(proteinSteps, value, "Белок, грамм на килограмм веса", MaterialTheme.colorScheme.surfaceContainer, onChange)

/**
 * Горизонтальный барабан (макет Goals): высота 64, скругление 20, фон [bg]; под текущим — плашка 72 × 48
 * `secondaryContainer`; соседние мельче и бледнее, края гаснут в цвет фона. Листается и докручивается, тап — выбрать,
 * щелчок на каждом шаге.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HorizontalDrum(values: List<Double>, value: Double, description: String, bg: Color, onChange: (Double) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val start = values.indices.minByOrNull { abs(values[it] - value) } ?: 0
    val pager = rememberPagerState(initialPage = start) { values.size }
    val change by rememberUpdatedState(onChange)
    LaunchedEffect(pager) {
        var first = true
        snapshotFlow { pager.currentPage }.collect { i -> if (!first) { tick(); change(values[i]) }; first = false }
    }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(20.dp)).background(bg)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(72.dp).height(48.dp).clip(RoundedCornerShape(24.dp)).background(colors.secondaryContainer))
        val side = (maxWidth - 72.dp) / 2
        HorizontalPager(pager, pageSize = PageSize.Fixed(72.dp), contentPadding = PaddingValues(horizontal = side), modifier = Modifier.fillMaxSize()) { i ->
            val d = abs((pager.currentPage - i) + pager.currentPageOffsetFraction).coerceAtMost(3f)
            val on = d < 0.5f
            Box(
                Modifier.fillMaxSize().clickable { scope.launch { pager.animateScrollToPage(i) } }
                    .graphicsLayer { alpha = 1f - d * 0.22f; val k = 1f - d * 0.08f; scaleX = k; scaleY = k },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    fmt1(values[i]), fontSize = if (on) 22.sp else 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) colors.onSecondaryContainer else colors.onSurface, maxLines = 1,
                )
            }
        }
        // Края гаснут в цвет фона
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(72.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(bg, bg.copy(alpha = 0f)))))
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(72.dp).fillMaxHeight().background(Brush.horizontalGradient(listOf(bg.copy(alpha = 0f), bg))))
        }
    }
}

/** Прогноз цели по реальному тренду веса за последние 2 недели (общий для «Тело и цель» и вкладки «Вес»). */
internal sealed interface GoalForecast {
    /** Взвешиваний меньше чем на 2 недели. */
    data object NotEnough : GoalForecast
    /** Цель уже достигнута (вес по ту сторону целевого или в пределах 0,2 кг). */
    data object Reached : GoalForecast
    /** Вес стоит или идёт от цели. [perWeek] — кг в неделю. */
    data class NotMoving(val perWeek: Double) : GoalForecast
    data class Eta(val date: LocalDate, val weeks: Int, val perWeek: Double) : GoalForecast
}

/** [up] — цель выше начального веса (набор), иначе — похудение. */
internal fun goalForecast(target: Double, weight: Double, trend: List<Pair<LocalDate, Double>>, up: Boolean = target > weight): GoalForecast {
    val remain = target - weight
    if (abs(remain) < 0.2 || (up && remain < 0) || (!up && remain > 0)) return GoalForecast.Reached
    val today = app.fitfat.data.DayClock.today()
    val window = trend.filter { !it.first.isBefore(today.minusDays(14)) }
    if (window.size < 6 || ChronoUnit.DAYS.between(window.first().first, window.last().first) < 10) return GoalForecast.NotEnough
    val days = ChronoUnit.DAYS.between(window.first().first, window.last().first).toDouble()
    val perWeek = (window.last().second - window.first().second) / days * 7
    if (abs(perWeek) < 0.05 || perWeek * remain <= 0) return GoalForecast.NotMoving(perWeek)
    val weeks = remain / perWeek
    return GoalForecast.Eta(today.plusDays((weeks * 7).roundToInt().toLong()), weeks.roundToInt().coerceAtLeast(1), perWeek)
}

internal fun etaDate(d: LocalDate): String = d.format(etaFmt)
internal fun paceText(perWeek: Double) = "${if (perWeek > 0) "+" else "−"}${fmt1(abs(perWeek))} кг/нед"

/** Срок цели строкой для «Тело и цель»: «Цель ≈ 12 марта 2027 · 17 нед. при темпе −0,3 кг/нед». */
internal fun targetEta(target: Double, weight: Double, trend: List<Pair<LocalDate, Double>>): String =
    when (val f = goalForecast(target, weight, trend)) {
        GoalForecast.NotEnough -> "Срок появится, когда наберётся 2 недели взвешиваний"
        GoalForecast.Reached -> "Цель достигнута"
        is GoalForecast.NotMoving -> "Сейчас вес не движется к цели — срок не посчитать"
        is GoalForecast.Eta -> "Цель ≈ ${etaDate(f.date)} · ${f.weeks} нед. при темпе ${paceText(f.perWeek)}"
    }
