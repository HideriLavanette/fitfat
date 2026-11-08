package app.fitfat.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.ui.common.MockIcons
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

private const val RESIST = 56f // dp: до «срыва» строка тянется с сопротивлением не дальше этого
private const val SOFT = 70f
private const val SNAP = 110f  // dp пальца: порог срабатывания

/**
 * Смахнуть влево — убрать (как строки дневника и списка продуктов): строка тянется с нарастающим сопротивлением,
 * на пороге — «срыв» с вибрацией, дальше идёт за пальцем; отпустили после срыва — [onRemove], раньше — пружиной назад.
 * Справа подложка `errorContainer` с [label] и корзиной. [shape] — скругление строки, [enabled] — свайп включён.
 */
@Composable
fun SwipeRemove(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Убрать",
    shape: Dp = 16.dp,
    enabled: Boolean = true,
    /** Ключ жеста в [app.fitfat.data.Hints]: первое смахивание считается освоением. */
    hintKey: String? = null,
    /** Строка сама чуть тянется влево, пока свайп не пробовали (первая в списке). */
    nudge: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    var released by remember { mutableStateOf(false) }
    val d = dx.value
    val hints = remember { app.fitfat.data.Hints.get(view.context) }
    SwipeNudge(dx, nudge, both = false)
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.matchParentSize().clip(RoundedCornerShape(shape)).background(if (d < 0) colors.errorContainer else Color.Transparent).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Row(Modifier.alpha(if (d < 0) (if (released) 1f else 0.55f * min(-d / RESIST, 1f)) else 0f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onErrorContainer)
                Spacer(Modifier.width(0.dp))
                Icon(MockIcons.Delete, null, Modifier.size(20.dp), tint = colors.onErrorContainer)
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .offset { IntOffset(with(density) { d.dp.roundToPx() }, 0) }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    var raw = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { raw = dx.value; released = false; hintKey?.let(hints::used) },
                        onDragEnd = {
                            val was = released
                            released = false
                            if (was && dx.value < 0) {
                                // Строка уезжает за край, потом убирается
                                scope.launch { dx.animateTo(-600f, spring(dampingRatio = 1f, stiffness = 900f)); onRemove(); dx.snapTo(0f) }
                            } else scope.launch { dx.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
                        },
                        onDragCancel = { released = false; scope.launch { dx.animateTo(0f) } },
                    ) { ch, amountPx ->
                        // Только влево: вправо строка почти не двигается
                        raw = (raw + amountPx / density.density).coerceIn(-320f, 24f)
                        if (raw > 0) { scope.launch { dx.snapTo(raw * 0.2f) }; return@detectHorizontalDragGestures }
                        ch.consume()
                        val a = abs(raw)
                        if (!released && a >= SNAP) { released = true; Feedback.tickSilent(view.context) }
                        else if (released && a < SNAP * 0.75f) { released = false; Feedback.light(view.context) }
                        val target = if (released) raw else -RESIST * (1f - exp(-a / SOFT))
                        scope.launch {
                            if (released) dx.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 1600f)) else dx.snapTo(target)
                        }
                    }
                },
        ) { content() }
    }
}
