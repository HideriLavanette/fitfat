package app.fitfat.ui.setup

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.ui.components.tick
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Уровень жира на подробной шкале (09.11.2026, просьба автора: «более детальная и показательная шкала»):
 * 11 уровней у мужчин и 10 у женщин вместо 6 — по 3–5 % на уровень, с приметами, которые можно проверить в зеркале.
 * [ref] — значение для силуэта в ленте сравнения и для тапа по нему.
 */
internal class FatLevel(val from: Int, val to: Int, val title: String, val text: String) {
    val ref: Int get() = if (to >= 50) from + 4 else (from + to + 1) / 2
    val range: String get() = if (to >= 50) "$from%+" else "$from–$to%"
}

internal val maleLevels = listOf(
    FatLevel(3, 5, "Предел сушки", "Тонкая кожа, видны вены и волокна мышц. Так выглядят только на соревнованиях, и недолго."),
    FatLevel(6, 8, "Соревновательный", "Пресс и косые мышцы резко очерчены, вены на руках и животе. Держать долго тяжело."),
    FatLevel(9, 11, "Атлетичный", "Все кубики пресса видны при любом свете, плечи и руки рельефные."),
    FatLevel(12, 14, "Подтянутый", "Пресс виден, но нижние кубики сглажены; лёгкий рельеф рук и плеч."),
    FatLevel(15, 17, "Спортивный", "Верхние кубики видны при хорошем свете, живот плоский, боков нет."),
    FatLevel(18, 20, "Обычный", "Пресса не видно, живот плоский или чуть мягкий, намечаются бока."),
    FatLevel(21, 24, "Мягкий", "Живот мягкий и выступает сидя, бока заметны, складка над ремнём."),
    FatLevel(25, 29, "Выше среднего", "Живот округлый и выступает стоя, бока и складка на пояснице."),
    FatLevel(30, 34, "Полный", "Живот заметно выступает, грудь мягкая, жир на спине."),
    FatLevel(35, 39, "Высокий", "Живот нависает над ремнём, жир на руках, бёдрах и шее."),
    FatLevel(40, 50, "Очень высокий", "Живот большой и нависающий, фигура заметна в любой одежде."),
)

internal val femaleLevels = listOf(
    FatLevel(3, 11, "Предел сушки", "Уровень соревнований: видны вены и волокна мышц. Для здоровья опасно, цикл почти всегда сбивается."),
    FatLevel(12, 15, "Соревновательная", "Резкий рельеф пресса, рук и ног. Держать долго трудно, цикл может сбиваться."),
    FatLevel(16, 19, "Атлетичная", "Пресс виден, плечи и бёдра рельефные, вены на руках."),
    FatLevel(20, 23, "Подтянутая", "Контур пресса и рук, чёткая талия, упругие бёдра."),
    FatLevel(24, 27, "Спортивная", "Живот плоский, пресса почти не видно, мягкий изгиб бёдер."),
    FatLevel(28, 31, "Обычная", "Мягкие формы, живот плоский или чуть мягкий, бёдра округлые."),
    FatLevel(32, 35, "Мягкая", "Живот мягкий и выступает сидя, руки и бёдра без рельефа."),
    FatLevel(36, 39, "Выше среднего", "Округлый живот, полные бёдра и руки, складки на боках."),
    FatLevel(40, 44, "Полная", "Живот выступает стоя, жир на руках, спине и бёдрах."),
    FatLevel(45, 50, "Высокая", "Живот и бёдра крупные, фигура заметна в любой одежде."),
)

internal fun List<FatLevel>.levelOf(bf: Int) = firstOrNull { bf in it.from..it.to } ?: last()

/**
 * Линейка 3–50 %: деление на каждый процент, длинное и подпись — на каждые 5; под делениями — полоса уровней,
 * текущий уровень выделен. Тянуть или тапнуть — точное значение, щелчок на каждом проценте.
 */
@Composable
internal fun FatRuler(bf: Int, levels: List<FatLevel>, onChange: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val change by rememberUpdatedState(onChange)
    val cur by rememberUpdatedState(bf)
    val level = levels.levelOf(bf)
    val track = colors.surfaceContainerHighest
    val on = colors.secondaryContainer
    val minor = colors.outlineVariant
    val major = colors.onSurfaceVariant
    val pointer = colors.primary
    val labelColor = colors.onSurfaceVariant
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        val pad = 10.dp
        fun set(x: Float, width: Float, padPx: Float) {
            val t = ((x - padPx) / (width - 2 * padPx)).coerceIn(0f, 1f)
            val v = (3 + t * 47).roundToInt().coerceIn(3, 50)
            if (v != cur) { change(v); tick() }
        }
        Column {
            Canvas(
                Modifier.fillMaxWidth().height(44.dp)
                    .semantics { contentDescription = "Шкала процента жира"; stateDescription = "$bf%" }
                    .pointerInput(Unit) {
                        val p = pad.toPx()
                        detectTapGestures { set(it.x, size.width.toFloat(), p) }
                    }
                    .pointerInput(Unit) {
                        val p = pad.toPx()
                        detectHorizontalDragGestures(onDragStart = { set(it.x, size.width.toFloat(), p) }) { ch, _ ->
                            ch.consume(); set(ch.position.x, size.width.toFloat(), p)
                        }
                    },
            ) {
                val p = pad.toPx()
                val span = size.width - 2 * p
                fun x(v: Float) = p + (v - 3f) / 47f * span
                // Полоса уровней: сегменты с зазором, текущий — secondaryContainer
                val barTop = size.height - 12.dp.toPx(); val barH = 10.dp.toPx(); val gap = 2.dp.toPx()
                levels.forEach { l ->
                    val x0 = x(l.from - 0.5f).coerceAtLeast(p) + gap / 2
                    val x1 = x(l.to + 0.5f).coerceAtMost(size.width - p) - gap / 2
                    drawRoundRect(if (l === level) on else track, Offset(x0, barTop), Size(x1 - x0, barH), CornerRadius(barH / 2))
                }
                // Деления: каждый процент, каждые 5 — длиннее
                for (v in 3..50) {
                    val big = v % 5 == 0
                    val h = if (big) 14.dp.toPx() else 7.dp.toPx()
                    drawLine(if (big) major else minor, Offset(x(v.toFloat()), barTop - 4.dp.toPx() - h), Offset(x(v.toFloat()), barTop - 4.dp.toPx()), if (big) 1.5.dp.toPx() else 1.dp.toPx(), StrokeCap.Round)
                }
                // Указатель: линия через полосу и капля сверху
                val px = x(bf.toFloat())
                drawLine(pointer, Offset(px, 2.dp.toPx()), Offset(px, size.height), 3.dp.toPx(), StrokeCap.Round)
                drawCircle(pointer, 5.dp.toPx(), Offset(px, 5.dp.toPx()))
            }
            Box(Modifier.fillMaxWidth().height(16.dp)) {
                for (v in 5..50 step 5) {
                    val xDp = pad + (w - pad * 2) * ((v - 3f) / 47f)
                    Text("$v", fontSize = 11.sp, color = labelColor, modifier = Modifier.offset(x = xDp - 10.dp).width(20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

/**
 * Лента сравнения: силуэт каждого уровня (спереди) с его диапазоном. Текущий уровень выделен и держится в центре ленты;
 * тап по силуэту ставит середину уровня — дальше точнее линейкой или кольцом.
 */
@Composable
internal fun FatStrip(bf: Int, female: Boolean, levels: List<FatLevel>, onPick: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val level = levels.levelOf(bf)
    val idx = levels.indexOf(level)
    val state = rememberLazyListState()
    LaunchedEffect(idx) { state.animateScrollToItem(max(0, idx - 2)) }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        itemsIndexed(levels) { _, l ->
            val sel = l === level
            val bg by animateColorAsState(if (sel) colors.secondaryContainer else colors.surfaceContainer, label = "lvl")
            Surface(
                onClick = { if (!sel) { onPick(l.ref); tick() } },
                shape = RoundedCornerShape(14.dp), color = bg,
                modifier = Modifier.width(72.dp).semantics { contentDescription = "${l.title}, ${l.range}" },
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BodyFigure(l.ref, female, side = false, bg, Modifier.width(54.dp).height(86.dp))
                    Text(l.range, fontSize = 11.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (sel) colors.onSecondaryContainer else colors.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Силуэт при данном проценте жира (поле 100×160): спереди — плечи, руки, талия, бёдра и ноги, при низком проценте —
 * линии пресса; сбоку — выступ живота, грудь, ягодицы. [bg] — фон под фигурой: им рисуется зазор между рукой и телом.
 */
@Composable
internal fun BodyFigure(bf: Int, female: Boolean, side: Boolean, bg: Color, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val body = colors.primary.copy(alpha = 0.55f).compositeOver(bg)
    val line = colors.onPrimaryContainer.copy(alpha = 0.55f)
    val limb = colors.primary.copy(alpha = 0.78f).compositeOver(bg)
    Canvas(modifier) {
        val kx = size.width / 100f; val ky = size.height / 160f
        val k = minOf(kx, ky)
        fun o(x: Float, y: Float) = Offset(x * kx, y * ky)
        if (side) {
            // Профиль узкий — глубину тела рисуем крупнее, чтобы живот и ягодицы читались
            fun os(x: Float, y: Float) = o(50 + (x - 50) * 1.6f, y)
            drawSide(bf.toFloat(), female, body, limb, ::os, k)
        } else drawFront(bf, female, body, bg, line, ::o, k)
    }
}

private fun Color.compositeOver(under: Color): Color {
    val a = alpha
    return Color(red * a + under.red * (1 - a), green * a + under.green * (1 - a), blue * a + under.blue * (1 - a), 1f)
}

private fun Path.smooth(pts: List<Offset>) {
    // Сглаженная кривая через точки (середины отрезков — опорные точки квадратичных кривых)
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size - 1) {
        val m = Offset((pts[i].x + pts[i + 1].x) / 2, (pts[i].y + pts[i + 1].y) / 2)
        quadraticTo(pts[i].x, pts[i].y, m.x, m.y)
    }
    lineTo(pts.last().x, pts.last().y)
}

private fun DrawScope.drawFront(bfi: Int, female: Boolean, body: Color, bg: Color, line: Color, o: (Float, Float) -> Offset, k: Float) {
    val b = bfi.toFloat()
    val sh = if (female) 20f + b * 0.04f else 27f + b * 0.04f      // плечи
    val ch = if (female) 17f + b * 0.12f else 20f + b * 0.12f      // грудь
    val wa = if (female) 7f + b * 0.42f else 8f + b * 0.6f         // талия
    val hi = if (female) 15f + b * 0.42f else 14f + b * 0.32f       // бёдра
    val arm = (if (female) 5.5f + b * 0.12f else 7f + b * 0.12f)    // толщина руки
    val leg = (if (female) 8f + b * 0.2f else 8.5f + b * 0.15f)     // полуширина бедра у паха
    // Голова и шея
    drawOval(body, o(41f, 2f), Size(18 * (size.width / 100f), 22 * (size.height / 160f)))
    val neck = 4.5f + b * 0.06f
    drawRect(body, o(50 - neck, 20f), Size(2 * neck * (size.width / 100f), 12 * (size.height / 160f)))
    // Руки: под телом, с зазором фона, слегка в стороны
    fun armLine(s: Float) {
        val top = o(50 + s * (sh - arm * 0.35f), 36f)
        val bottom = o(50 + s * (maxOf(sh, wa + arm * 0.6f, hi * 0.92f) + 2f), 98f)
        drawLine(bg, top, bottom, (arm + 3f) * k, StrokeCap.Round)
        drawLine(body, top, bottom, arm * k, StrokeCap.Round)
    }
    // Ноги
    fun legShape(s: Float) = Path().apply {
        smooth(listOf(o(50 + s * 1.5f, 104f), o(50 + s * (hi * 0.98f), 106f), o(50 + s * (hi * 0.8f), 128f), o(50 + s * (5f + leg * 0.4f), 156f),
            o(50 + s * 3f, 156f), o(50 + s * 1.5f, 130f), o(50 + s * 1.5f, 104f)))
        close()
    }
    drawPath(legShape(-1f), body); drawPath(legShape(1f), body)
    // Торс
    val torso = Path().apply {
        smooth(listOf(o(50f, 30f), o(50 + sh, 33f), o(50 + sh + 1, 38f), o(50 + ch, 50f), o(50 + wa, 78f), o(50 + hi, 96f), o(50 + hi * 0.9f, 108f), o(50f, 108f)))
        lineTo(o(50f, 108f).x, o(50f, 108f).y)
        val left = listOf(o(50f, 108f), o(50 - hi * 0.9f, 108f), o(50 - hi, 96f), o(50 - wa, 78f), o(50 - ch, 50f), o(50 - sh - 1, 38f), o(50 - sh, 33f), o(50f, 30f))
        for (i in 1 until left.size - 1) {
            val m = Offset((left[i].x + left[i + 1].x) / 2, (left[i].y + left[i + 1].y) / 2)
            quadraticTo(left[i].x, left[i].y, m.x, m.y)
        }
        close()
    }
    armLine(-1f); armLine(1f)
    drawPath(torso, body)
    // Рельеф при низком проценте: пресс, у мужчин ещё грудные
    val lean = if (female) bfi <= 22 else bfi <= 14
    val w = 1.6f * k
    if (lean) {
        drawLine(line, o(50f, 60f), o(50f, 92f), w, StrokeCap.Round)
        listOf(66f, 75f, 84f).forEach { y -> drawLine(line, o(43f, y), o(57f, y), w, StrokeCap.Round) }
        if (!female) {
            drawLine(line, o(38f, 54f), o(48f, 56f), w, StrokeCap.Round)
            drawLine(line, o(62f, 54f), o(52f, 56f), w, StrokeCap.Round)
        }
    } else if (!female && bfi >= 25 || female && bfi >= 34) {
        // Складка над поясом — у высокого процента
        drawLine(line, o(50 - wa * 0.6f, 94f), o(50 + wa * 0.6f, 94f), w, StrokeCap.Round)
    }
}

private fun DrawScope.drawSide(b: Float, female: Boolean, body: Color, limb: Color, o: (Float, Float) -> Offset, k: Float) {
    // Профиль лицом вправо. Перед: грудь и живот; спина: лопатки, поясница, ягодицы.
    val chest = if (female) 13f + b * 0.12f else 13f + b * 0.1f
    val bust = if (female) 4f + b * 0.12f else 0f
    val belly = if (female) 7f + max(0f, b - 18f) * 0.55f else 8f + max(0f, b - 10f) * 0.6f
    val back = 10f + b * 0.05f
    val lumbar = 8f + b * 0.08f
    val glute = if (female) 13f + b * 0.22f else 11f + b * 0.15f
    val thigh = if (female) 7f + b * 0.18f else 7f + b * 0.15f
    drawCircle(body, 10f * k, Offset(o(52f, 13f).x, o(52f, 13f).y))
    drawRect(body, o(47f, 20f), Size(o(54f, 0f).x - o(47f, 0f).x, 12 * (size.height / 160f)))
    val pts = listOf(
        o(54f, 30f), o(50 + chest, 42f), o(50 + chest + bust, 52f), o(50 + chest * 0.9f, 62f),
        o(50 + belly, 82f), o(50 + belly * 0.8f, 98f), o(50 + thigh, 110f), o(50 + 5f + thigh * 0.3f, 132f), o(55f, 156f),
        o(43f, 156f), o(50 - 4f - thigh * 0.3f, 134f), o(50 - glute * 0.8f, 112f), o(50 - glute, 100f), o(50 - lumbar, 80f),
        o(50 - back, 50f), o(50 - back * 0.7f, 34f), o(46f, 30f),
    )
    drawPath(Path().apply { smooth(pts); close() }, body)
    // Рука вдоль тела — тоном темнее
    val armW = (if (female) 6f + b * 0.11f else 7.5f + b * 0.12f) * k
    drawLine(limb, o(48f, 38f), o(49f + belly * 0.15f, 96f), armW, StrokeCap.Round)
}
