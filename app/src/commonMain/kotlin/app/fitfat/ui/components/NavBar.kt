package app.fitfat.ui.components

import app.fitfat.platform.uptimeMillis
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

data class NavItem(val label: String, val icon: ImageVector)

/**
 * Защитная зона под навбаром (макет: 120 dp): контент под ней размыт (12 dp) и затемнён градиентом
 * surface 0 → 55 % (на 45 %) → 85 %; маска плавно проявляет блюр сверху. Тапы зона забирает себе.
 * Высота растёт на системную панель навигации.
 */
@Composable
fun NavGuardZone(haze: HazeState, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme.surface
    Column(
        modifier
            .fillMaxWidth()
            .hazeEffect(haze) {
                blurRadius = 12.dp
                // Блюр считается в половинном разрешении — в 4 раза меньше пикселей, на радиусе 12 разницы не видно
                inputScale = HazeInputScale.Fixed(0.5f)
                tints = emptyList()
                // Haze требует фон: без него падает при первой отрисовке (IllegalArgumentException)
                backgroundColor = s
                mask = Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black)
            }
            .background(Brush.verticalGradient(0f to s.copy(alpha = 0f), 0.45f to s.copy(alpha = 0.55f), 1f to s.copy(alpha = 0.85f)))
            // В макете pointer-events: auto — тапы сквозь зону не проходят
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } },
    ) {
        Box(Modifier.fillMaxWidth().height(120.dp))
        Box(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** Бабл над навбаром: «точка невозврата» — на какой доле ширины вкладки от её центра бабл срывается к соседней. */
private const val NO_RETURN = 0.55f
/** Насколько бабл тянется за пальцем до срыва (доля ширины вкладки) и мягкость натяжения. */
private const val PULL = 0.13f
private const val PULL_SOFT = 0.22f

/**
 * Плавающий навбар (макет: отступы 16, высота 72, скругление 36, поля 8 / 6).
 * Фон — `surfaceContainerHigh` 72 % с блюром 24 того, что под ним, рамка `onSurface` 8 %, тень.
 * Индикатор 56×32 `secondaryContainer` один на всё меню и переезжает к вкладке; стоит ровно под иконкой
 * (позиция берётся от самой иконки, поэтому не съезжает при крупном шрифте).
 * Ведение пальцем по навбару: над ним бабл с вкладкой, на которую переключит; бабл тянется к пальцу,
 * а за точкой невозврата с щелчком перескакивает к соседней вкладке с отскоком. Отпустили — переход.
 */
@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    haze: HazeState,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(36.dp)
    val pos by animateFloatAsState(selected.coerceAtLeast(0).toFloat(), spring(dampingRatio = 0.72f, stiffness = 420f), label = "pos")
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var iconTop by remember { mutableFloatStateOf(0f) } // верх иконки внутри полей навбара, px

    // Бабл: позиция в «вкладках» (0…n-1), к какой вкладке привязан, виден ли
    val bubble = remember { Animatable(0f) }
    val bubbleShow = remember { Animatable(0f) }
    var snap by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val select by rememberUpdatedState(onSelect)
    val current by rememberUpdatedState(selected)

    Box(modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(72.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(shape)
                .hazeEffect(haze) {
                    // iOS: стекло прозрачнее и с более сильным размытием, как у системных панелей
                    blurRadius = if (isCupertino) 32.dp else 24.dp
                    inputScale = HazeInputScale.Fixed(0.5f)
                    tints = listOf(HazeTint(colors.surfaceContainerHigh.copy(alpha = if (isCupertino) 0.55f else 0.72f)))
                    backgroundColor = colors.surfaceContainerHigh
                }
                .then(if (isCupertino) Modifier.glassBorder(shape) else Modifier.border(1.dp, colors.onSurface.copy(alpha = 0.08f), shape))
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .pointerInput(items.size) {
                    val n = items.size
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val itemPx = size.width.toFloat() / n
                        val slop = viewConfiguration.touchSlop
                        var started = false
                        var lastSnapAt = 0L
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            if (!started && abs(ch.position.x - down.position.x) > slop) {
                                started = true; dragging = true
                                snap = current.coerceAtLeast(0)
                                scope.launch { bubble.snapTo(snap.toFloat()) }
                                scope.launch { bubbleShow.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 600f)) }
                                Feedback.light()
                            }
                            if (!started) continue
                            ch.consume() // это ведение, а не тап — вкладка под пальцем не нажмётся
                            val finger = (ch.position.x / itemPx - 0.5f).coerceIn(-0.4f, n - 0.6f)
                            val d = finger - snap
                            if (abs(d) > NO_RETURN) {
                                // Точка невозврата: прыжок к ближайшей вкладке с отскоком
                                snap = finger.roundToInt().coerceIn(0, n - 1)
                                lastSnapAt = uptimeMillis()
                                Feedback.tickSilent() // без звука
                                scope.launch { bubble.animateTo(snap.toFloat(), spring(dampingRatio = 0.68f, stiffness = 560f)) } // лёгкий отскок
                            } else if (uptimeMillis() - lastSnapAt > 220) {
                                // Натяжение: чем дальше палец, тем туже
                                val t = snap + sign(d) * PULL * (1f - exp(-abs(d) / PULL_SOFT))
                                scope.launch { bubble.animateTo(t, spring(dampingRatio = 0.9f, stiffness = 1400f)) }
                            }
                        }
                        if (started) {
                            dragging = false
                            if (snap != current) select(snap)
                            scope.launch { bubble.animateTo(snap.toFloat(), spring(dampingRatio = 0.6f)) }
                            scope.launch { bubbleShow.animateTo(0f, tween(160)) }
                        }
                    }
                },
        ) {
            val itemW = maxWidth / items.size
            if (selected >= 0) {
                val stretch = (abs(selected - pos)).coerceAtMost(1f) // 0 на месте, до 1 в пути
                val w = 56.dp + 28.dp * stretch
                Box(
                    Modifier
                        .offset { IntOffset((itemW * pos + (itemW - w) / 2).roundToPx(), iconTop.roundToInt()) }
                        .width(w).height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.secondaryContainer),
                )
            }
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, item ->
                    val sel = i == selected
                    // Насколько индикатор сейчас над этой вкладкой: цвета перетекают вместе с его движением
                    val near = (1f - abs(pos - i)).coerceIn(0f, 1f)
                    val iconC = lerp(colors.onSurfaceVariant, colors.onSecondaryContainer, near)
                    val textC = lerp(colors.onSurfaceVariant, colors.secondary, near)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .selectable(selected = sel, role = Role.Tab, indication = null, interactionSource = null, onClick = {
                                if (!sel) Feedback.light()
                                onSelect(i)
                            }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        Box(
                            Modifier.width(56.dp).height(32.dp)
                                .then(if (i == 0) Modifier.onPlaced { iconTop = it.positionInParent().y } else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(item.icon, null, Modifier.size(24.dp).graphicsLayer { val k = 1f + 0.08f * near; scaleX = k; scaleY = k }, tint = iconC)
                        }
                        Text(
                            item.label,
                            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
                            fontWeight = if (near > 0.5f) FontWeight.Bold else FontWeight.Medium,
                            color = textC,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // Бабл над навбаром — не обрезается его скруглением
        if (bubbleShow.value > 0.01f) {
            val i = snap.coerceIn(0, items.lastIndex)
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                val itemW = maxWidth / items.size
                var bw by remember { mutableFloatStateOf(0f) }
                Row(
                    Modifier
                        .offset { IntOffset(((itemW * (bubble.value + 0.5f)).toPx() - bw / 2).roundToInt(), (-60).dp.roundToPx()) }
                        .onPlaced { bw = it.size.width.toFloat() }
                        .graphicsLayer {
                            val t = bubbleShow.value
                            alpha = t.coerceIn(0f, 1f); scaleX = 0.7f + 0.3f * t; scaleY = 0.7f + 0.3f * t
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .shadow(10.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.inverseSurface)
                        .height(44.dp)
                        .padding(start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(items[i].icon, null, Modifier.size(20.dp), tint = colors.inverseOnSurface)
                    Text(items[i].label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.inverseOnSurface, maxLines = 1)
                }
            }
        }
    }
}
