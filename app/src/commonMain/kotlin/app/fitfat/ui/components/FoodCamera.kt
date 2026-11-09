package app.fitfat.ui.components

import androidx.compose.runtime.Composable
import app.fitfat.data.Product

/**
 * Камера «Добавить» (решения автора 09.11.2026): одна кнопка вместо «штрихкод» и «фото». Во весь экран, сверху — подсказка
 * в плашке. Нашёлся штрихкод — рамка вокруг него и снизу карточка: товар из базы ([lookup]) — нажать, [onProduct];
 * нет в базе — «Создать продукт», [onCreate] с цифрами. Если [allowPhoto] (есть доступ к ассистенту) — кнопка снимка:
 * [onPhoto] — имя уменьшенного файла в `photos`. Внизу затемнение, как под навбаром, и широкая «Из галереи».
 */
@Composable
expect fun FoodCamera(
    allowPhoto: Boolean,
    lookup: suspend (String) -> Product?,
    onProduct: (Product) -> Unit,
    onCreate: (String) -> Unit,
    onPhoto: (String) -> Unit,
    onClose: () -> Unit,
)
