package app.fitfat.ui.products

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.Product
import app.fitfat.ui.common.Categories
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.components.GramRuler
import app.fitfat.ui.components.PieceStepper
import app.fitfat.ui.components.PillToggle
import app.fitfat.ui.components.VerticalDrum
import app.fitfat.ui.theme.MacroColors
import kotlin.math.roundToInt

private val shorts = mapOf("grain" to "Крупы", "meat" to "Мясо, рыба", "dairy" to "Молочное", "fruit" to "Фрукты", "veg" to "Овощи", "sauce" to "Сладкое")

/**
 * Новый продукт / правка (макет Product-edit, 4.3). Закрыть · заголовок · корзина (с подтверждением).
 * Название, категория сеткой 3 × 2 (выбранная — рамка `onSurface`, скругление 22), «На 100 г / На 1 шт» и вес штуки,
 * ккал считаются из БЖУ, Б / Ж / У барабанами, обычная порция, другие названия, «Сохранить».
 * Фото, штрихкод и «Заполнить» (ассистент) появятся в своих версиях.
 */
@Composable
fun ProductEditScreen(vm: ProductsViewModel, id: Long, onClose: () -> Unit, onSaved: (Long) -> Unit, onDeleted: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val existing = remember(id) { if (id > 0) vm.products.value.firstOrNull { it.id == id } else null }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var cat by remember { mutableStateOf(existing?.category ?: "grain") }
    var piece by remember { mutableStateOf(existing?.perPiece ?: false) }
    var pieceG by remember { mutableStateOf(existing?.pieceGrams?.let { fmt0(it) } ?: "") }
    var p by remember { mutableDoubleStateOf(existing?.protein ?: 0.0) }
    var f by remember { mutableDoubleStateOf(existing?.fat ?: 0.0) }
    var u by remember { mutableDoubleStateOf(existing?.carbs ?: 0.0) }
    var touched by remember { mutableStateOf(existing == null) } // БЖУ меняли — ккал пересчитываем
    var portion by remember { mutableDoubleStateOf(existing?.defaultGrams ?: 100.0) }
    var aliases by remember { mutableStateOf(existing?.let { aliases(it).joinToString(", ") } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(piece) { if (existing?.perPiece != piece) portion = if (piece) 1.0 else 100.0 }

    val calc = (p * 4 + f * 9 + u * 4)
    val kcal = if (touched) calc else existing?.kcal ?: calc
    val pg = pieceG.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && (!piece || (pg != null && pg > 0))

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconBtn(MockIcons.Close, "Закрыть", onClose)
            Text(if (existing == null) "Новый продукт" else "Изменить продукт", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (existing != null) IconBtn(MockIcons.Delete, "Удалить", { confirmDelete = true }, tint = colors.error)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Field("Название") {
                TextInput(name, { name = it }, "Например: Сырники домашние", 56.dp, "Название")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption("Категория")
                Categories.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            val sel = c.id == cat
                            val r by animateDpAsState(if (sel) 22.dp else 14.dp, label = "r")
                            val border by animateColorAsState(if (sel) colors.onSurface else Color.Transparent, label = "b")
                            Surface(
                                onClick = { cat = c.id }, shape = RoundedCornerShape(r), color = c.bg,
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp).border(2.dp, border, RoundedCornerShape(r)),
                            ) {
                                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(c.icon, null, Modifier.size(20.dp), tint = colors.onSurface)
                                    Text(shorts[c.id] ?: c.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (piece) "КБЖУ на 1 шт" else "КБЖУ на 100 г", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    PillToggle(listOf("На 100 г", "На 1 шт"), if (piece) 1 else 0, onSelect = { piece = it == 1 }, height = 36.dp, fontSize = 13.sp, fill = false)
                }
                if (piece) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainer).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Вес одной штуки", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        BasicTextField(
                            pieceG, { s -> pieceG = s.filter { it.isDigit() || it == ',' || it == '.' } }, singleLine = true,
                            textStyle = TextStyle(color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                            cursorBrush = SolidColor(colors.primary), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(64.dp).semantics { contentDescription = "Вес одной штуки, граммы" },
                        )
                        Text("г", fontSize = 14.sp, color = colors.onSurfaceVariant)
                    }
                }
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(fmt0(kcal), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (touched) "ккал · считается из БЖУ" else "ккал", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                val mx = if (piece) 150.0 else 100.0
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VerticalDrum(p, { p = it; touched = true }, 0.0, mx, 1.0, Modifier.weight(1f), label = "Белки, г", labelColor = MacroColors.protein, band = MacroColors.proteinContainer, decimals = if (p % 1.0 != 0.0) 1 else 0)
                    VerticalDrum(f, { f = it; touched = true }, 0.0, mx, 1.0, Modifier.weight(1f), label = "Жиры, г", labelColor = MacroColors.fat, band = MacroColors.fatContainer, decimals = if (f % 1.0 != 0.0) 1 else 0)
                    VerticalDrum(u, { u = it; touched = true }, 0.0, mx, 1.0, Modifier.weight(1f), label = "Углеводы, г", labelColor = MacroColors.carbs, band = MacroColors.carbsContainer, decimals = if (u % 1.0 != 0.0) 1 else 0)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption("Обычная порция")
                if (piece) PieceStepper(portion, { portion = it }, pg ?: 0.0)
                else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                        Text(fmt0(portion), fontSize = 24.sp, fontWeight = FontWeight(750))
                        Text(" г · ${fmt0(kcal * portion / 100)} ккал", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    GramRuler(portion.roundToInt(), { portion = it.toDouble() })
                }
            }
            Field("Другие названия") {
                TextInput(aliases, { aliases = it }, "через запятую: сырники, творожники", 48.dp, "Другие названия", 15.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = {
                val n = name.trim()
                val list = (listOf(n.lowercase()) + aliases.split(',').map { it.trim().lowercase() }).filter { it.isNotBlank() }.distinct()
                vm.save(
                    (existing ?: Product(name = n, category = cat, kcal = 0.0, protein = 0.0, fat = 0.0, carbs = 0.0)).copy(
                        name = n, category = cat, kcal = kcal, protein = p, fat = f, carbs = u,
                        perPiece = piece, pieceGrams = if (piece) pg else null, aliases = list.joinToString(","), defaultGrams = portion,
                    ),
                    onSaved,
                )
            },
            enabled = valid, shape = RoundedCornerShape(28.dp),
            color = if (valid) colors.primary else colors.surfaceContainerHighest, modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Сохранить", fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp, color = if (valid) colors.onPrimary else colors.outline)
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(MockIcons.Delete, null, tint = colors.error) },
            title = { Text("Удалить «${existing.name}»?") },
            text = { Text("Продукт исчезнет из вашей базы и перестанет распознаваться при вводе. Уже сделанные записи в дневнике останутся — их КБЖУ сохранены.") },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(existing.id, onDeleted) }) { Text("Удалить", color = colors.error) } },
        )
    }
}

@Composable
private fun Caption(text: String) = Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Caption(label); content() }
}

@Composable
private fun TextInput(value: String, onChange: (String) -> Unit, hint: String, height: Dp, desc: String, size: androidx.compose.ui.unit.TextUnit = 16.sp) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value, onChange, singleLine = true,
        textStyle = TextStyle(color = colors.onSurface, fontSize = size), cursorBrush = SolidColor(colors.primary),
        modifier = Modifier.fillMaxWidth().heightIn(min = height).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh)
            .semantics { contentDescription = desc },
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(hint, fontSize = size, color = colors.onSurfaceVariant)
                inner()
            }
        },
    )
}
