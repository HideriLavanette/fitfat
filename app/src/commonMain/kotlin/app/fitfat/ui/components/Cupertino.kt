package app.fitfat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.fitfat.platform.Platform
import kotlinx.coroutines.launch

/*
 * Адаптивный слой для iOS (решение автора 09.11.2026): экраны общие, но детали, по которым приложение
 * читается как Android, на iPhone рисуются в духе iOS — нажатие затемняет, а не пускает круги ripple;
 * алерт — карточка по центру с кнопками в ряд через тонкие линии; тумблер — как в Настройках iOS;
 * стекло с бликом по краю у капсулы навбара. На Android всё остаётся Material 3 по макетам.
 */

/** Стиль iOS: на Android — false, детали Material 3 не меняются. */
val isCupertino: Boolean get() = !Platform.isAndroid

/** Нажатие в духе iOS: содержимое чуть темнеет, пока палец на экране, без кругов ripple. */
object FadeIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = FadeNode(interactionSource)
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -0x5A1D
}

private class FadeNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val alpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect {
                when (it) {
                    is PressInteraction.Press -> launch { alpha.animateTo(0.10f, tween(60)) }
                    is PressInteraction.Release, is PressInteraction.Cancel -> launch { alpha.animateTo(0f, tween(220)) }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = alpha.value
        if (a > 0.002f) drawRect(Color.Black.copy(alpha = a))
    }
}

/** Стеклянная кромка: светлый блик сверху, тоньше снизу — как у панелей iOS. */
fun Modifier.glassBorder(shape: Shape): Modifier = border(
    1.dp,
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.16f))),
    shape,
)

/**
 * Алерт: на Android — `AlertDialog` Material 3 как раньше; на iOS — карточка 280 по центру, заголовок 17 и текст 13
 * по центру, кнопки в ряд через тонкие линии («Отмена» слева, действие справа — правило приложения).
 */
@Composable
fun FfAlert(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    if (!isCupertino) {
        AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier, dismissButton = dismissButton, icon = icon, title = title, text = text)
        return
    }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier.width(280.dp).shadow(24.dp, shape).clip(shape).background(colors.surfaceContainerHigh).glassBorder(shape),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                icon?.let { CompositionLocalProvider(LocalContentColor provides colors.primary) { it() } }
                title?.let { ProvideTextStyle(TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = colors.onSurface)) { it() } }
                text?.let { ProvideTextStyle(TextStyle(fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, color = colors.onSurfaceVariant)) { it() } }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                dismissButton?.let {
                    Box(Modifier.weight(1f).padding(vertical = 4.dp), contentAlignment = Alignment.Center) { it() }
                    VerticalDivider(Modifier.fillMaxHeight(), color = colors.outlineVariant.copy(alpha = 0.5f))
                }
                Box(Modifier.weight(1f).padding(vertical = 4.dp), contentAlignment = Alignment.Center) { confirmButton() }
            }
        }
    }
}

/** Тумблер: на Android — `Switch` Material 3; на iOS — 51×31 с белым кружком, дорожка `primary` / `surfaceVariant`. */
@Composable
fun FfSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (!isCupertino) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
        return
    }
    val colors = MaterialTheme.colorScheme
    val track by animateColorAsState(if (checked) colors.primary else colors.surfaceVariant, tween(200), label = "track")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, spring(dampingRatio = 0.85f, stiffness = 900f), label = "thumb")
    Box(
        modifier
            .size(51.dp, 31.dp)
            .clip(RoundedCornerShape(15.5.dp))
            .background(track)
            .toggleable(
                value = checked, enabled = enabled && onCheckedChange != null, role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onValueChange = { onCheckedChange?.invoke(it) },
            )
            .padding(2.dp),
    ) {
        Box(Modifier.offset(x = x).size(27.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}
