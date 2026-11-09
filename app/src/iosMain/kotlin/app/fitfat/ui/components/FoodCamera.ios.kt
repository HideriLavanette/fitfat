package app.fitfat.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.fitfat.data.Product

/** На iPhone камеры пока нет (docs/IOS.md): кнопка скрыта по `Platform.supportsBarcodeScan`; на всякий случай — сразу закрыть. */
@Composable
actual fun FoodCamera(
    allowPhoto: Boolean,
    lookup: suspend (String) -> Product?,
    onProduct: (Product) -> Unit,
    onCreate: (String) -> Unit,
    onPhoto: (String) -> Unit,
    onClose: () -> Unit,
) {
    LaunchedEffect(Unit) { onClose() }
}
