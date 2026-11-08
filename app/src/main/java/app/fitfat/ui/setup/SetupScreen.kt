package app.fitfat.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.Activity
import app.fitfat.data.CYCLE_EXTRA_CARBS
import app.fitfat.data.Goal
import app.fitfat.data.Macros
import app.fitfat.data.NormsCalc
import app.fitfat.data.Profile
import app.fitfat.data.Sex
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.strokeIcon
import app.fitfat.ui.components.Feedback
import app.fitfat.ui.components.PillToggle
import app.fitfat.ui.components.PopDrumChip
import app.fitfat.ui.components.VerticalDrum
import app.fitfat.ui.components.tick
import app.fitfat.ui.theme.MacroColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Итог первой настройки: профиль и свои нормы по типам дня ("n", "t", "c"), если их поправили чипами. */
data class SetupResult(val profile: Profile, val custom: Map<String, Macros>, val cuts: Int = 0)

/**
 * Правка из «Тело и цель»: свои нормы и срезы, которые уже есть, и тренд веса для срока цели. В этом режиме на шаге
 * цели — целевой вес, на шаге норм — срез из-за застоя и белок на кг, последняя кнопка — «Сохранить».
 */
class SetupEdit(val custom: Map<String, Macros>, val cuts: Int, val trend: List<Pair<java.time.LocalDate, Double>>)

/**
 * Первая настройка (макеты 6.1–6.5, интерактив Onboarding): о себе → процент жира → спорт (без вопроса об активности) →
 * цель → нормы. Сверху «назад» слева и 4 полоски прогресса. Нормы считаются по методике `NormsCalc`
 * (решение 08.11.2026 вместо формулы макета), их можно поправить чипами Б / Ж / У.
 * [onClose] — закрыть (открыли из профиля); при первом запуске закрыть нельзя. [edit] — правка из «Тело и цель».
 */
@Composable
fun SetupScreen(initial: Profile?, onClose: (() -> Unit)?, edit: SetupEdit? = null, startStep: Int = 1, onDone: (SetupResult) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var step by remember { mutableIntStateOf(startStep) }
    var dir by remember { mutableIntStateOf(1) }
    var p by remember { mutableStateOf(initial ?: Profile()) }
    val custom = remember { mutableStateMapOf<String, Macros>().apply { edit?.let { putAll(it.custom) } } }
    var cuts by remember { mutableIntStateOf(edit?.cuts ?: 0) }
    fun go(to: Int) { dir = if (to > step) 1 else -1; step = to }
    // Правку цели открыли сразу с шага 4 — на нём «закрыть», а не «назад»
    val back = step > startStep || (step > 1 && onClose == null)
    BackHandler(enabled = step > 1 || onClose != null) { if (back) go(step - 1) else onClose?.invoke() }

    // Свой Surface: экран рисуется поверх приложения, тексту нужен цвет `onSurface` по умолчанию
    Surface(color = colors.surface, contentColor = colors.onSurface, modifier = Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Шапка: «назад» (или «закрыть» на первом шаге из профиля) и 4 полоски прогресса
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            val icon = if (back) MockIcons.ChevronLeft else if (onClose != null) MockIcons.Close else null
            if (icon != null) {
                Surface(
                    onClick = { if (back) go(step - 1) else onClose?.invoke() }, shape = RoundedCornerShape(24.dp), color = Color.Transparent,
                    modifier = Modifier.align(Alignment.CenterStart).offset(x = (-8).dp).size(48.dp),
                ) { Box(contentAlignment = Alignment.Center) { Icon(icon, if (back) "Назад" else "Закрыть", Modifier.size(24.dp), tint = colors.onSurface) } }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 56.dp).semantics { contentDescription = if (step < 5) "Шаг $step из 4" else "Готово" },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (i in 1..4) {
                    val c by animateColorAsState(if (i <= step) colors.primary else colors.surfaceContainerHighest, tween(260), label = "dot")
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                step, label = "step",
                transitionSpec = {
                    (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 6 * dir } + fadeIn(tween(220, delayMillis = 60)))
                        .togetherWith(slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 6 * dir } + fadeOut(tween(160)))
                },
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (s) {
                        1 -> AboutStep(p) { p = it }
                        2 -> FatStep(p) { p = it }
                        3 -> SportStep(p) { p = it }
                        4 -> GoalStep(p, edit) { p = it }
                        else -> NormsStep(p, custom, cuts, edit != null, onUncut = { cuts = 0 }) { p = it }
                    }
                }
            }
        }
        val label = when (step) { 4 -> "Рассчитать нормы"; 5 -> if (edit != null) "Сохранить" else "Начать"; else -> "Дальше" }
        Surface(
            onClick = {
                if (step < 5) go(step + 1) else onDone(SetupResult(p, custom.toMap(), cuts))
            },
            shape = RoundedCornerShape(28.dp), color = colors.primary, modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(label, label = "next") { Text(it, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onPrimary) }
            }
        }
    }
    }
}

/** Нормы по методике: обычный день, день тренировки (+90 г углеводов при интенсивных тренировках), дни цикла (+25 г). */
private fun baseNorms(p: Profile, cuts: Int = 0): app.fitfat.data.DayNorms = app.fitfat.data.NormsPlan.calc(p, p.weightKg, cuts)

@Composable
private fun Title(text: String) = Text(text, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)

@Composable
private fun Caption(text: String) = Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)

// ——— Шаг 1: о себе ———

@Composable
internal fun AboutStep(p: Profile, onChange: (Profile) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Title("Расскажите о себе")
        Text("Нужно для стартовой оценки калорий. Потом приложение уточнит её по реальным данным.", fontSize = 15.sp, lineHeight = 21.sp, color = colors.onSurfaceVariant)
        PillToggle(
            listOf("Мужской", "Женский"), if (p.sex == Sex.Female) 1 else 0,
            onSelect = { i ->
                val sex = if (i == 1) Sex.Female else Sex.Male
                if (sex != p.sex) onChange(p.copy(sex = sex, bodyFatPct = if (sex == Sex.Female) 27 else 18))
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Пол" }, height = 52.dp, fontSize = 16.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerticalDrum(p.age.toDouble(), { onChange(p.copy(age = it.toInt())) }, 14.0, 90.0, 1.0, Modifier.weight(1f), label = "Возраст", height = 220, hint = true)
            VerticalDrum(p.heightCm.toDouble(), { onChange(p.copy(heightCm = it.toInt())) }, 120.0, 230.0, 1.0, Modifier.weight(1f), label = "Рост, см", height = 220)
            VerticalDrum(p.weightKg, { onChange(p.copy(weightKg = it)) }, 35.0, 250.0, 0.5, Modifier.weight(1f), label = "Вес, кг", decimals = 1, height = 220)
        }
        Text("Крутите барабаны вверх и вниз", fontSize = 12.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// ——— Шаг 2: процент жира ———

private class Band(val from: Int, val to: Int, val title: String, val text: String)

private val maleBands = listOf(
    Band(3, 8, "Соревновательная сушка", "Видны все мышцы и вены. Держать такой уровень долго тяжело."),
    Band(9, 12, "Атлетичный", "Чёткий пресс, рельеф плеч и рук."),
    Band(13, 17, "Спортивный", "Пресс виден при хорошем свете, живот плоский."),
    Band(18, 22, "Обычный", "Пресса не видно, живот мягкий, но не выступает."),
    Band(23, 27, "Выше среднего", "Живот округлый, появляются бока."),
    Band(28, 50, "Избыточный", "Жир на животе, боках и спине заметен даже в одежде."),
)
private val femaleBands = listOf(
    Band(3, 15, "Соревновательная форма", "Очень выражен рельеф. Держать долго трудно, может сбиваться цикл."),
    Band(16, 20, "Атлетичная", "Виден пресс, рельеф рук и ног."),
    Band(21, 25, "Спортивная", "Подтянутая фигура, лёгкий контур пресса, талия чёткая."),
    Band(26, 31, "Обычная", "Мягкие формы, пресса не видно, бёдра и руки без рельефа."),
    Band(32, 37, "Выше среднего", "Округлый живот, мягкие бёдра и руки."),
    Band(38, 50, "Избыточная", "Жир заметен на животе, бёдрах и руках даже в одежде."),
)

@Composable
internal fun FatStep(p: Profile, onChange: (Profile) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val female = p.sex == Sex.Female
    val bands = if (female) femaleBands else maleBands
    val bf = p.bodyFatPct
    val band = bands.firstOrNull { bf in it.from..it.to } ?: bands.last()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth()) { Title("Процент жира") }
        Text("Покрутите кольцо и сверьтесь с описанием — достаточно примерной оценки.", fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
        FatDial(bf) { onChange(p.copy(bodyFatPct = it)) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Silhouette(bf, female, Modifier.width(76.dp).height(122.dp).semantics { contentDescription = "Примерный силуэт при $bf% жира" })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${band.title} · ${band.from}–${if (band.to == 50) "50+" else band.to}%", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(band.text, fontSize = 13.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            bands.forEach { b ->
                val on = b === band
                val bg by animateColorAsState(if (on) colors.secondaryContainer else colors.surfaceContainer, label = "band")
                Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = Alignment.Center) {
                    Text("${b.from}" + if (b.to == 50) "+" else "", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (on) colors.onPrimaryContainer else colors.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Кольцо процента жира (240): дорожка 18 `surfaceContainerHighest`, 30 отметок через 12° (каждая пятая — штрих),
 * вращаются вместе со значением (12° на 1 %); в центре «печенька» `primaryContainer` и число 56.
 * Вращать пальцем по кругу, с инерцией и доводкой; щелчок на каждом проценте, упор на 3 и 50.
 */
@Composable
private fun FatDial(bf: Int, onChange: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val change by rememberUpdatedState(onChange)
    val cur = remember { intArrayOf(bf) }
    cur[0] = bf
    var acc by remember { mutableFloatStateOf(0f) } // доля шага в градусах
    var bumpAt by remember { mutableStateOf(0L) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun bump() { val now = System.currentTimeMillis(); if (now - bumpAt > 300) { bumpAt = now; Feedback.bump(view.context) } }
    fun turn(da: Float): Boolean {
        val v = cur[0]
        if ((v <= 3 && da < 0) || (v >= 50 && da > 0)) { acc = 0f; bump(); return false }
        var a = acc + da
        var steps = 0
        while (a >= 12f) { a -= 12f; steps++ }
        while (a <= -12f) { a += 12f; steps-- }
        acc = a
        if (steps != 0) {
            val nv = (v + steps).coerceIn(3, 50)
            if (nv == v) { acc = 0f; bump(); return false }
            cur[0] = nv; change(nv); view.tick()
            if (nv == 3 || nv == 50) acc = 0f
        }
        return true
    }
    suspend fun snap() {
        val from = acc
        val to = if (from >= 6f) 12f else if (from <= -6f) -12f else 0f
        val anim = Animatable(0f)
        var prev = 0f
        anim.animateTo(1f, tween(200)) {
            val k = 1f - (1f - value) * (1f - value) * (1f - value)
            val pos = (to - from) * k
            turn(pos - prev); prev = pos
        }
        acc = 0f
    }
    // Кольцо — 82 % ширины (не больше 340 dp); всё внутри масштабируется от макетных 240
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    val sizeDp = minOf(maxWidth * 0.82f, 340.dp)
    val k = sizeDp / 240.dp
    Box(
        Modifier.size(sizeDp)
            .semantics { contentDescription = "Процент жира"; stateDescription = "$bf%" }
            .pointerInput(Unit) {
                val cx = size.width / 2f; val cy = size.height / 2f
                fun angle(o: Offset) = Math.toDegrees(atan2((o.y - cy).toDouble(), (o.x - cx).toDouble())).toFloat()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    job?.cancel()
                    var last = angle(down.position)
                    var cum = 0f
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, Offset(0f, 0f))
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        ch.consume()
                        val a = angle(ch.position)
                        var da = a - last
                        if (da > 180f) da -= 360f
                        if (da < -180f) da += 360f
                        last = a; cum += da
                        tracker.addPosition(ch.uptimeMillis, Offset(cum, 0f))
                        turn(da)
                    }
                    // Инерция: угловая скорость затухает (τ 400 мс), потом доводка до деления
                    var v = (tracker.calculateVelocity().x / 1000f).coerceIn(-2.5f, 2.5f) // °/мс
                    if (abs(v) < 0.3f) v = 0f
                    job = scope.launch {
                        if (v != 0f) {
                            var t0 = -1L
                            while (true) {
                                val ok = androidx.compose.runtime.withFrameMillis { t ->
                                    val dt = if (t0 < 0) 16f else (t - t0).coerceAtMost(40).toFloat()
                                    t0 = t
                                    val r = turn(v * dt)
                                    v *= exp(-dt / 400f)
                                    r
                                }
                                if (!ok || abs(v) < 0.04f) break
                            }
                        }
                        snap()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val track = colors.surfaceContainerHighest
        val minor = colors.outline
        val major = colors.onSurface
        val cookie = colors.primaryContainer
        val pointer = colors.primary
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 240f
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(track, 106 * k, c, style = Stroke(18 * k))
            rotate(bf * 12f + acc, c) {
                for (i in 0 until 30) {
                    val a = ((i * 12f) - 90f) * (PI / 180).toFloat()
                    if (i % 5 == 0) drawLine(major, c + Offset(100 * k * cos(a), 100 * k * sin(a)), c + Offset(112 * k * cos(a), 112 * k * sin(a)), 3 * k, StrokeCap.Round)
                    else drawCircle(minor, 1.8f * k, c + Offset(106 * k * cos(a), 106 * k * sin(a)))
                }
            }
            // Указатель сверху (в макете под «печенькой»)
            drawPath(Path().apply { moveTo(120 * k, 89 * k); lineTo(114 * k, 79 * k); lineTo(126 * k, 79 * k); close() }, pointer)
            val path = Path()
            for (i in 0..240) {
                val t = i / 240f * 2 * PI.toFloat()
                val r = (80 + 5 * cos(9 * t)) * k
                val pt = c + Offset(r * cos(t), r * sin(t))
                if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            path.close()
            drawPath(path, cookie)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$bf", fontSize = 56.sp * k, lineHeight = 56.sp * k, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, color = colors.onPrimaryContainer)
            Text("%", fontSize = 22.sp * k, lineHeight = 22.sp * k, color = colors.onPrimaryContainer, modifier = Modifier.padding(bottom = 8.dp * k))
        }
    }
}
    }

/** Примерный силуэт (100×160): плечи, талия и бёдра зависят от процента жира; при низком — линии пресса. */
@Composable
private fun Silhouette(bf: Int, female: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val body = colors.secondaryContainer
    val absC = colors.onPrimaryContainer.copy(alpha = 0.7f)
    Canvas(modifier) {
        val kx = size.width / 100f; val ky = size.height / 160f
        fun o(x: Float, y: Float) = Offset(x * kx, y * ky)
        val b = bf.toFloat()
        val sh = if (female) 24f else 30f
        val ch = if (female) 20 + b * 0.2f else 25 + b * 0.15f
        val wa = if (female) 10 + b * 0.32f else 12 + b * 0.42f
        val hi = if (female) 20 + b * 0.36f else 17 + b * 0.3f
        drawOval(body, topLeft = o(38f, 6f), size = androidx.compose.ui.geometry.Size(24 * kx, 24 * ky))
        val p = Path().apply {
            moveTo(o(50 - sh, 40f).x, o(50 - sh, 40f).y)
            quadraticTo(o(50 - ch, 62f).x, o(50 - ch, 62f).y, o(50 - wa, 95f).x, o(50 - wa, 95f).y)
            quadraticTo(o(50 - hi - 2, 110f).x, o(50 - hi - 2, 110f).y, o(50 - hi, 125f).x, o(50 - hi, 125f).y)
            lineTo(o(50 - hi + 5, 156f).x, o(50 - hi + 5, 156f).y)
            lineTo(o(50 + hi - 5, 156f).x, o(50 + hi - 5, 156f).y)
            lineTo(o(50 + hi, 125f).x, o(50 + hi, 125f).y)
            quadraticTo(o(50 + hi + 2, 110f).x, o(50 + hi + 2, 110f).y, o(50 + wa, 95f).x, o(50 + wa, 95f).y)
            quadraticTo(o(50 + ch, 62f).x, o(50 + ch, 62f).y, o(50 + sh, 40f).x, o(50 + sh, 40f).y)
            close()
        }
        drawPath(p, body)
        val lean = if (female) bf <= 22 else bf <= 14
        if (lean) {
            val w = 2 * kx
            drawLine(absC, o(44f, 72f), o(56f, 72f), w, StrokeCap.Round)
            drawLine(absC, o(44f, 82f), o(56f, 82f), w, StrokeCap.Round)
            drawLine(absC, o(45f, 92f), o(55f, 92f), w, StrokeCap.Round)
            drawLine(absC, o(50f, 68f), o(50f, 96f), w, StrokeCap.Round)
        }
    }
}

// ——— Шаг 3: спорт и активность ———

private val sportsList = listOf("run" to "Бег", "swim" to "Плавание", "fight" to "Единоборства", "team" to "Командные", "yoga" to "Йога", "bike" to "Велосипед")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SportStep(p: Profile, onChange: (Profile) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Title("Спорт и тренировки")
        Caption("Ходите в зал?")
        PillToggle(listOf("Да", "Нет"), if (p.gym) 0 else 1, onSelect = { onChange(p.copy(gym = it == 0)) }, modifier = Modifier.fillMaxWidth(), height = 48.dp, fontSize = 15.sp)
        Caption("Секции и спорт")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sportsList.forEach { (id, label) ->
                val sel = id in p.sports
                val bg by animateColorAsState(if (sel) colors.secondaryContainer else colors.surfaceContainerHigh, label = "sp")
                Surface(
                    onClick = { onChange(p.copy(sports = if (sel) p.sports - id else p.sports + id)) },
                    shape = RoundedCornerShape(8.dp), color = bg, modifier = Modifier.height(32.dp),
                ) {
                    Row(Modifier.padding(start = if (sel) 8.dp else 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (sel) Icon(MockIcons.Check, null, Modifier.size(16.dp), tint = colors.onPrimaryContainer)
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (sel) colors.onPrimaryContainer else colors.onSurface)
                    }
                }
            }
        }
        // Активность в течение дня не спрашиваем (решение 08.11.2026): её переоценивают, а лёгкие нагрузки помогают худеть
        Text("Йога и прогулки калорий не добавляют — они помогают худеть. День тренировки с добавкой углеводов — для зала и интенсивного спорта.",
            fontSize = 13.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
    }
}

// ——— Шаг 4: цель ———

private class GoalCard(val goal: Goal, val label: String, val sub: String, val icon: String)

private const val LOOP_PAGES = 4000

private val goalCards = listOf(
    GoalCard(Goal.Recomp, "Рекомпозиция", "Минус жир и плюс мышцы почти при том же весе. Лучший выбор, если тренируетесь.", "M12 4v16M6 20h12M4 8h16M4 8l-2.5 6a2.5 2.5 0 0 0 5 0ZM20 8l-2.5 6a2.5 2.5 0 0 0 5 0Z"),
    GoalCard(Goal.Lose, "Похудение", "Снижаем вес дефицитом калорий. Без силовых вместе с жиром уходят и мышцы.", "M3 7l6 6 4-4 8 8M21 11v6h-6"),
    GoalCard(Goal.Gain, "Набор мышц", "Растим мышцы на небольшом профиците. Нужны регулярные силовые.", "M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11"),
    GoalCard(Goal.Keep, "Поддержание", "Держим вес и форму: едим по расходу.", "M5 9h14M5 15h14"),
)

/**
 * Цель — карусель карточек 284 × 280 (скругление 28): текущая — `surfaceContainerHigh`, масштаб 1; соседние — 0,9 и 45 %.
 * Под ней точки (текущая — полоска 22 `primary`). Без тренировок для похудения, рекомпозиции и набора — предупреждение.
 */
@Composable
internal fun GoalStep(p: Profile, edit: SetupEdit?, onChange: (Profile) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Title("Ваша цель")
        GoalPicker(p, bleedBy = 20) { np ->
            // При правке у похудения и набора своя цель по весу: по умолчанию −5 / +3 кг от текущего
            val w = np.weightKg
            val target = when {
                edit == null -> np.targetKg
                np.goal == Goal.Lose && (np.targetKg == null || np.targetKg >= w) -> ((w - 5) * 2).roundToInt() / 2.0
                np.goal == Goal.Gain && (np.targetKg == null || np.targetKg <= w) -> ((w + 3) * 2).roundToInt() / 2.0
                else -> np.targetKg
            }
            onChange(np.copy(targetKg = target))
        }
        if (edit != null) {
            androidx.compose.animation.AnimatedVisibility(p.goal == Goal.Lose || p.goal == Goal.Gain, enter = fadeIn() + androidx.compose.animation.expandVertically(), exit = fadeOut() + androidx.compose.animation.shrinkVertically()) {
                val w = p.weightKg
                TargetCard(p.targetKg ?: if (p.goal == Goal.Gain) w + 3 else w - 5, w, p.goal == Goal.Gain, edit.trend) { onChange(p.copy(targetKg = it)) }
            }
        }
    }
        GoalDetails(p, p.weightKg)
}

/** Карусель цели с точками и предупреждением (шаг 6.4 и «Цель и нормы» 5.3). [bleedBy] — поля экрана, dp. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GoalPicker(p: Profile, bleedBy: Int, onChange: (Profile) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // Бесконечная карусель: страниц много, цель — номер страницы по кругу; старт — в середине
    val n = goalCards.size
    val mid = LOOP_PAGES / 2 - (LOOP_PAGES / 2) % n
    val pager = rememberPagerState(initialPage = mid + goalCards.indexOfFirst { it.goal == p.goal }.coerceAtLeast(0)) { LOOP_PAGES }
    // Ближайшая страница с целью i — чтобы точки и тап листали коротким путём
    fun nearest(i: Int): Int { val cur = pager.currentPage; val d = ((i - cur % n) % n + n) % n; return if (d <= n / 2) cur + d else cur + d - n }
    val latest by rememberUpdatedState(p)
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage % n }.collect { i -> if (latest.goal != goalCards[i].goal) onChange(latest.copy(goal = goalCards[i].goal)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth()
                // Карусель от края до края экрана (поля экрана 20)
                .layout { m, c ->
                    val extra = (bleedBy * 2).dp.roundToPx()
                    val pl = m.measure(c.copy(maxWidth = c.maxWidth + extra, minWidth = c.minWidth + extra))
                    layout(c.maxWidth, pl.height) { pl.place(-extra / 2, 0) }
                },
        ) {
            val side = ((maxWidth - 284.dp) / 2).coerceAtLeast(0.dp)
            HorizontalPager(
                pager, pageSize = PageSize.Fixed(284.dp), pageSpacing = 16.dp, contentPadding = PaddingValues(horizontal = side),
                modifier = Modifier.fillMaxWidth().height(290.dp).semantics { contentDescription = "Цель" },
            ) { i ->
                val g = goalCards[i % n]
                val f = abs((pager.currentPage - i) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                val on = f < 0.5f
                Column(
                    Modifier.size(284.dp, 280.dp)
                        .graphicsLayer { val s = 1f - 0.1f * f; scaleX = s; scaleY = s; alpha = 1f - 0.55f * f }
                        .clip(RoundedCornerShape(28.dp)).background(lerp(colors.surfaceContainerHigh, colors.surfaceContainer, f))
                        .clickable { scope.launch { pager.animateScrollToPage(i) } }
                        .padding(horizontal = 22.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.padding(bottom = 4.dp).size(72.dp).clip(RoundedCornerShape(26.dp))
                            .background(if (on) colors.primaryContainer else colors.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) { Icon(remember(g) { strokeIcon(g.icon, 1.8f) }, null, Modifier.size(36.dp), tint = if (on) colors.onPrimaryContainer else colors.onSurfaceVariant) }
                    Text(g.label, fontSize = 22.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    Text(g.sub, fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 0.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
            goalCards.forEachIndexed { i, g ->
                val sel = i == pager.currentPage % n
                val w by animateDpAsState(if (sel) 22.dp else 8.dp, tween(200), label = "dotW")
                val c by animateColorAsState(if (sel) colors.primary else colors.outlineVariant, label = "dotC")
                Box(
                    Modifier.size(32.dp).clickable { scope.launch { pager.animateScrollToPage(nearest(i)) } }.semantics { contentDescription = g.label },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.width(w).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c)) }
            }
        }
        val warn = when {
            p.goal == Goal.Lose && !p.trains -> "Похудение без тренировок: вместе с жиром уходят и мышцы. После такого похудения можно чувствовать себя даже хуже — слабость, дряблость, быстрый откат веса. Добавьте хотя бы 2 силовые в неделю."
            (p.goal == Goal.Recomp || p.goal == Goal.Gain) && !p.trains -> "Без силовых тренировок мышцы не растут — изменения пойдут в основном в жир. Отметьте зал или спорт на прошлом шаге."
            else -> null
        }
        androidx.compose.animation.AnimatedVisibility(warn != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.errorContainer).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(remember { strokeIcon("M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17ZM12 11v5M12 8h.01") }, null, Modifier.size(20.dp), tint = colors.onErrorContainer)
                Text(warn ?: "", fontSize = 13.sp, lineHeight = 18.sp, color = colors.onErrorContainer, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ——— Шаг 5: нормы ———

@Composable
internal fun NormsStep(p: Profile, custom: MutableMap<String, Macros>, cuts: Int, edit: Boolean, onUncut: () -> Unit, onChange: (Profile) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val base = baseNorms(p, cuts)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title("Ваши нормы")
        Text(
            if (edit) "Нормы считаются сами. Если у вас свои цифры — нажмите на Б, Ж или У или удерживайте и ведите вверх-вниз."
            else "Стартовая оценка. Если у вас свои цифры — нажмите на Б, Ж или У или удерживайте и ведите вверх-вниз.",
            fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant,
        )
        androidx.compose.animation.AnimatedVisibility(cuts > 0) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHigh).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Срезано ${fmt0(cuts * app.fitfat.data.NormsCalc.CUT_KCAL)} ккал из-за застоя веса", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                Surface(onClick = onUncut, shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
                    Text("Вернуть", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        NormCards(p, base, custom)
        if (edit) {
            // Белок на кг веса — горизонтальный барабан 1,6–2,2
            val pk = app.fitfat.data.NormsPlan.proteinPerKg(p, p.weightKg)
            Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text("Белок для расчёта", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("${fmt0(pk * p.weightKg)} г в день", fontSize = 14.sp, color = colors.onSurfaceVariant)
            }
            ProteinDrum(pk) { onChange(p.copy(proteinPerKg = it)) }
            Text("На килограмм веса. Белок не урезается ни при срезе калорий, ни при смене типа дня.", fontSize = 12.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Карточки норм: обычный день, день тренировки (если тренируется), дни цикла (женщинам). */
@Composable
internal fun NormCards(p: Profile, base: app.fitfat.data.DayNorms, custom: MutableMap<String, Macros>) {
    val colors = MaterialTheme.colorScheme
    NormCard("n", "Обычный день", MockIcons.NavToday, colors.onSurfaceVariant, colors.surfaceContainerHighest, base.normal, custom)
    if (p.trains) NormCard("t", "День тренировки", MockIcons.Dumbbell, MacroColors.training, MacroColors.trainingContainer, base.training, custom)
    if (p.sex == Sex.Female) NormCard("c", "Дни цикла", MockIcons.Drop, MacroColors.cycle, MacroColors.cycleContainer, base.cycle, custom)
}

/**
 * Карточка нормы: скругление 22 `surfaceContainer`, поля 16; значок 32 в цвете типа дня, название; поправили —
 * «Своя · сбросить». Ккал 32 и чипы Б / Ж / У с барабаном (шаг 1 г); ккал считаются из своих граммов.
 */
@Composable
private fun NormCard(
    id: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, ink: Color, iconBg: Color,
    base: Macros, custom: MutableMap<String, Macros>,
) {
    val colors = MaterialTheme.colorScheme
    val own = custom[id]
    val v = own ?: base
    fun set(m: Macros) { custom[id] = m.copy(kcal = m.protein * 4 + m.fat * 9 + m.carbs * 4) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(colors.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = ink)
            }
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            androidx.compose.animation.AnimatedVisibility(own != null, enter = fadeIn(), exit = fadeOut()) {
                Surface(onClick = { custom.remove(id) }, shape = RoundedCornerShape(14.dp), color = colors.surfaceContainerHighest, modifier = Modifier.height(28.dp)) {
                    Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Своя · сбросить", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fmt0(v.kcal), fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
            Text(" ккал", fontSize = 15.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PopDrumChip(v.protein, { set(v.copy(protein = it)) }, "г", MacroColors.protein, MacroColors.proteinContainer, "Белки", bubbleRight = true, stepFor = { 1.0 }, display = { "Б ${fmt0(it)}" })
            PopDrumChip(v.fat, { set(v.copy(fat = it)) }, "г", MacroColors.fat, MacroColors.fatContainer, "Жиры", stepFor = { 1.0 }, display = { "Ж ${fmt0(it)}" })
            PopDrumChip(v.carbs, { set(v.copy(carbs = it)) }, "г", MacroColors.carbs, MacroColors.carbsContainer, "Углеводы", stepFor = { 1.0 }, display = { "У ${fmt0(it)}" })
        }
    }
}
