package app.fitfat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.ui.common.MockIcons
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Переключатель-«таблетка» из макетов. Выбранный сегмент — `secondary` / `onSecondary`, полностью скруглён.
 * Слитный вариант (зазор 2): невыбранные — `surfaceContainerHighest`, внешние углы полные, внутренние 8.
 * Раздельный ([gapped], зазор 8, «Приём пищи» в «Когда поели»): невыбранные прозрачные со скруглением 12.
 * [fill] — сегменты делят ширину поровну (периоды веса, шторки); иначе — по содержимому (дневник).
 */
@Composable
fun PillToggle(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    fontSize: TextUnit = 14.sp,
    fill: Boolean = true,
    gapped: Boolean = false,
    /** Иконки сегментов (тип дня). */
    icons: List<androidx.compose.ui.graphics.vector.ImageVector>? = null,
    /** Цвет выбранного сегмента по индексу (тренировка — голубой); по умолчанию `secondary`. */
    selectedColors: List<Color>? = null,
    /** Цвет иконки невыбранного сегмента по индексу. */
    iconTints: List<Color>? = null,
) {
    val colors = MaterialTheme.colorScheme
    val full = height / 2
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (gapped) 8.dp else 2.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            // Плавно: цвет и скругление выбранного сегмента
            val inner by androidx.compose.animation.core.animateDpAsState(if (sel) full else if (gapped) 12.dp else 8.dp, androidx.compose.animation.core.tween(220), label = "ri")
            val shape: Shape = when {
                gapped || sel -> RoundedCornerShape(inner) // выбранный плавно скругляется полностью
                options.size == 1 -> RoundedCornerShape(full)
                i == 0 -> RoundedCornerShape(topStart = full, bottomStart = full, topEnd = inner, bottomEnd = inner)
                i == options.lastIndex -> RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = full, bottomEnd = full)
                else -> RoundedCornerShape(inner)
            }
            val selBg = selectedColors?.getOrNull(i) ?: colors.secondary
            val bg by androidx.compose.animation.animateColorAsState(if (sel) selBg else if (gapped) Color.Transparent else colors.surfaceContainerHighest, androidx.compose.animation.core.tween(220), label = "bg")
            val ink by androidx.compose.animation.animateColorAsState(if (sel) colors.onSecondary else colors.onSurface, androidx.compose.animation.core.tween(220), label = "ink")
            Row(
                (if (fill) Modifier.weight(1f) else Modifier)
                    .height(height)
                    .clip(shape)
                    .background(bg)
                    .selectable(selected = sel, role = Role.RadioButton, onClick = { if (!sel) Feedback.light(); onSelect(i) })
                    .padding(horizontal = if (fill) 8.dp else 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icons?.getOrNull(i)?.let {
                    val tint by androidx.compose.animation.animateColorAsState(if (sel) colors.onSecondary else iconTints?.getOrNull(i) ?: colors.onSurface, androidx.compose.animation.core.tween(220), label = "tint")
                    Icon(it, null, Modifier.size(18.dp), tint = tint)
                }
                // При крупном шрифте подпись чуть ужимается, а не обрезается
                if (fill) FitText(label, fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = ink, minScale = 0.75f)
                else Text(label, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, color = ink)
            }
        }
    }
}

/**
 * Шторка из макетов: скругление сверху 28, ручка 32×4 (`onSurfaceVariant` 40 %), скрим 50 % без блюра.
 * Поля 10 / 20 / 24, промежуток [gap]. Фон по умолчанию — `surfaceContainerLow`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FfSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    gap: Dp = 14.dp,
    horizontalPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = color,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier.padding(top = 10.dp).width(32.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
        },
        // Высокая шторка останавливается под строкой состояния, а не заезжает под неё
        modifier = modifier.statusBarsPadding(),
    ) {
        Column(
            Modifier.fillMaxWidth().trackTouches().navigationBarsPadding().padding(start = horizontalPadding, end = horizontalPadding, top = gap, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
            content = content,
        )
    }
}

/**
 * Snackbar отмены: висит 5 секунд (стандартный короткий — около 4). Вид — стандартный M3:
 * `inverseSurface`, скругление 4, кнопка `inversePrimary`, как в макете.
 * Возвращает true, если нажали «Отмена».
 */
suspend fun SnackbarHostState.showUndo(message: String, action: String = "Отмена"): Boolean =
    withTimeoutOrNull(5_000) {
        showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed
    } ?: false

/** Большая кнопка «+» на главном: 80×80, скругление 20, акцентная `primary`, иконка 32. */
@Composable
fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier, contentDescription: String = "Добавить еду") {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp,
        modifier = modifier.size(80.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(MockIcons.PlusBold, contentDescription, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
        }
    }
}

/**
 * Штуки: «−» · «2 шт · ~110 г» · «+» (макет Add-food, правка штучного продукта).
 * Кнопки 52×52, скругление 16, `surfaceContainerLow`; шаг 1 шт, минимум 1.
 */
@Composable
fun PieceStepper(pieces: Double, onChange: (Double) -> Unit, gramsPerPiece: Double, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    @Composable
    fun btn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
        Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLow, modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, desc, tint = colors.onSurface, modifier = Modifier.size(22.dp)) }
        }
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        btn(MockIcons.Minus, "Меньше") { if (pieces > 1.0) { onChange(pieces - 1); tick() } else bump() }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text("${app.fitfat.ui.common.fmt1(pieces)} шт", fontSize = 30.sp, fontWeight = FontWeight(750))
            Text(" · ~${app.fitfat.ui.common.fmt0(gramsPerPiece * pieces)} г", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        btn(MockIcons.Plus, "Больше") { onChange(pieces + 1); tick() }
    }
}

/** Крупные граммы над линейкой: «200 г» (32 sp, 750; «г» 16 sp `onSurfaceVariant`). */
@Composable
fun GramsReadout(grams: Int, modifier: Modifier = Modifier, size: TextUnit = 32.sp) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        Text(app.fitfat.ui.common.fmt0(grams.toDouble()), fontSize = size, fontWeight = FontWeight(750))
        Text(" г", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
    }
}
