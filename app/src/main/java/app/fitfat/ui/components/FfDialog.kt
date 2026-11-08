package app.fitfat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Окно макетов поверх экрана: скрим 50 % (свой, системное затемнение выключено), карточка сверху с отступом [top].
 * [start] — отступ слева; null — по центру.
 */
@Composable
fun FfDialog(onDismiss: () -> Unit, top: Dp, width: Dp, start: Dp? = null, content: @Composable BoxScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        Box(
            Modifier.fillMaxSize().trackTouches().background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            Box(
                Modifier.align(if (start == null) Alignment.TopCenter else Alignment.TopStart).statusBarsPadding().imePadding()
                    .padding(top = top, start = start ?: 0.dp).width(width)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                content = content,
            )
        }
    }
}

