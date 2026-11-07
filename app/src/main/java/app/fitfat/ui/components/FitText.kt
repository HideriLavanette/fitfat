package app.fitfat.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Однострочный текст, который уменьшается под ширину (не ниже [minScale] от [fontSize]),
 * чтобы при крупном системном шрифте подпись не обрезалась и не переносилась.
 */
@Composable
fun FitText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    minScale: Float = 0.7f,
) {
    val measurer = rememberTextMeasurer()
    val base = LocalTextStyle.current.merge(TextStyle(fontSize = fontSize, fontWeight = fontWeight))
    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val scale = remember(text, base, maxW) {
            fun width(k: Float) = measurer.measure(
                text, base.copy(fontSize = fontSize * k), maxLines = 1, softWrap = false, constraints = Constraints(),
            ).size.width
            if (maxW == Constraints.Infinity) return@remember 1f
            // Ширина не строго пропорциональна размеру (хинтинг), поэтому уменьшаем по шагам, пока не влезет
            var k = 1f
            // Шаг всегда вниз, но не ниже minScale (раньше при k − 0,01 < minScale диапазон был пустым — вылет)
            while (k > minScale && width(k) > maxW) k = maxOf(minScale, minOf(k * maxW / width(k) * 0.99f, k - 0.01f))
            k
        }
        val size = fontSize * scale
        Text(
            text, color = color, fontSize = size, lineHeight = (size.value * 1.3f).sp,
            fontWeight = fontWeight, textAlign = textAlign, maxLines = 1, softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
