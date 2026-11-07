package app.fitfat.ui.common

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.abs
import kotlin.math.roundToInt

/** Щелчок шага: вибрация + системный звук нажатия (звучит, если в системе включены звуки касания). */
fun View.tick() {
    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    playSoundEffect(SoundEffectConstants.CLICK)
}

/**
 * Вертикальный барабан: инерция, доводка до значения, щелчок и вибрация на каждом шаге.
 * Числа плавно крупнеют, ярчают и становятся непрозрачными по мере приближения к центру.
 *
 * @param count число значений, [index] — выбранное, [label] — подпись значения по индексу.
 */
@Composable
fun Drum(
    count: Int,
    index: Int,
    onIndexChange: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 44.dp,
    visibleItems: Int = 5,
    description: String = "",
) {
    val view = LocalView.current
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index.coerceIn(0, count - 1))
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val half = visibleItems / 2
    val change by rememberUpdatedState(onIndexChange)

    // Индекс под центральной рамкой: первый видимый + доля прокрутки внутри него
    val centered by remember {
        derivedStateOf {
            (state.firstVisibleItemIndex + (state.firstVisibleItemScrollOffset / itemPx).roundToInt()).coerceIn(0, count - 1)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { centered }.distinctUntilChanged().drop(1).collect { i ->
            view.tick()
            change(i)
        }
    }
    // Значение поменяли снаружи — подкручиваем барабан, пока пользователь его не трогает
    LaunchedEffect(index) {
        if (!state.isScrollInProgress && index != centered) state.animateScrollToItem(index.coerceIn(0, count - 1))
    }

    val colors = MaterialTheme.colorScheme
    Box(
        modifier.height(itemHeight * visibleItems).semantics { contentDescription = description + " " + label(centered) },
        contentAlignment = Alignment.Center,
    ) {
        // Подложка под выбранным значением
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = colors.secondaryContainer,
            modifier = Modifier.fillMaxWidth().height(itemHeight),
        ) {}
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = itemHeight * half),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(count) { i ->
                // Расстояние до центра в шагах: 0 — по центру, 1 — соседний, дальше — гаснет
                val dist by remember(i) {
                    derivedStateOf {
                        val pos = state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx
                        abs(i - pos).coerceAtMost(half + 1f)
                    }
                }
                val t = (1f - dist).coerceIn(0f, 1f) // 1 — точно в центре
                Box(Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        label(i),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = lerp(colors.onSurfaceVariant, colors.onSecondaryContainer, t),
                        modifier = Modifier.graphicsLayer {
                            val s = 0.78f + 0.22f * t
                            scaleX = s; scaleY = s
                            alpha = (1f - dist / (half + 1f)).coerceIn(0.15f, 1f)
                        },
                    )
                }
            }
        }
    }
}

/** Шаги барабана граммов: 0…2000 г через 5 г. */
const val GRAM_STEP = 5
const val GRAM_MAX = 2000
fun gramIndex(g: Double) = (g / GRAM_STEP).roundToInt().coerceIn(0, GRAM_MAX / GRAM_STEP)

/** Шаги барабана штук: 0,5…20 шт через 0,5. */
const val PIECE_MAX_STEPS = 40
fun pieceIndex(p: Double) = ((p * 2).roundToInt() - 1).coerceIn(0, PIECE_MAX_STEPS - 1)
fun pieceOf(i: Int) = (i + 1) / 2.0
