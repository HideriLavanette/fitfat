package app.fitfat.ui.components

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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

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
            .hazeChild(haze) {
                blurRadius = 12.dp
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

/**
 * Плавающий навбар (макет: отступы 16, высота 72, скругление 36, поля 8 / 6).
 * Фон — `surfaceContainerHigh` 72 % с блюром 24 того, что под ним, рамка `onSurface` 8 %, тень.
 * Индикатор 56×32 `secondaryContainer`; подпись 12 sp, у выбранного жирная `secondary`.
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
    Row(
        modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .fillMaxWidth()
            .height(72.dp)
            .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(shape)
            .hazeChild(haze) {
                blurRadius = 24.dp
                tints = listOf(HazeTint(colors.surfaceContainerHigh.copy(alpha = 0.72f)))
                backgroundColor = colors.surfaceContainerHigh
            }
            .border(1.dp, colors.onSurface.copy(alpha = 0.08f), shape)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val sel = i == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(selected = sel, role = Role.Tab, onClick = { onSelect(i) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Box(
                    Modifier.width(56.dp).height(32.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (sel) colors.secondaryContainer else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, null, Modifier.size(24.dp), tint = if (sel) colors.onSecondaryContainer else colors.onSurfaceVariant)
                }
                Text(
                    item.label,
                    fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) colors.secondary else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
