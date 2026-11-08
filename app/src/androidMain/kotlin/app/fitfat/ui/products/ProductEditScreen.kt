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
import app.fitfat.data.namedPortions
import app.fitfat.ui.common.Categories
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.components.GramRuler
import app.fitfat.ui.components.PieceStepper
import app.fitfat.ui.components.PillToggle
import app.fitfat.ui.components.VerticalDrum
import app.fitfat.ui.theme.MacroColors
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val shorts = mapOf("grain" to "Крупы", "meat" to "Мясо, рыба", "dairy" to "Молочное", "fruit" to "Фрукты", "veg" to "Овощи", "sauce" to "Сладкое", "drink" to "Напитки")

/**
 * Новый продукт / правка (макет Product-edit, 4.3). Закрыть · заголовок · корзина (с подтверждением).
 * Название, категория сеткой 3 × 2 (выбранная — рамка `onSurface`, скругление 22), «На 100 г / На 1 шт» и вес штуки,
 * ккал считаются из БЖУ, Б / Ж / У барабанами, обычная порция, другие названия, «Сохранить».
 * Штрихкод — сканером или вручную, «Найти» берёт название и КБЖУ из Open Food Facts. Фото — плиткой у названия (камера или галерея). «Заполнить» (ассистент) — позже.
 */
@Composable
fun ProductEditScreen(vm: ProductsViewModel, id: Long, onClose: () -> Unit, onSaved: (Long) -> Unit, onDeleted: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Новый продукт после скана приходит заготовкой (штрихкод и, может быть, данные Open Food Facts)
    val prefill = remember(id) { if (id == 0L) vm.prefill.also { vm.prefill = null } else null }
    val existing = remember(id) { if (id > 0) vm.products.value.firstOrNull { it.id == id } else null }
    val start = existing ?: prefill
    var name by remember { mutableStateOf(start?.name ?: "") }
    var cat by remember { mutableStateOf(start?.category ?: "grain") }
    var piece by remember { mutableStateOf(existing?.perPiece ?: false) }
    // Сухой / сырой / готовый вес: у нового — догадка по названию, пока не выбрали руками
    var weightState by remember { mutableStateOf(start?.weightState ?: start?.name?.let(app.fitfat.data.WeightState::guess)) }
    var weightTouched by remember { mutableStateOf(existing != null) }
    LaunchedEffect(name) { if (!weightTouched) weightState = app.fitfat.data.WeightState.guess(name) }
    var pieceG by remember { mutableStateOf(existing?.pieceGrams?.let { fmt0(it) } ?: "") }
    var p by remember { mutableDoubleStateOf(start?.protein ?: 0.0) }
    var f by remember { mutableDoubleStateOf(start?.fat ?: 0.0) }
    var u by remember { mutableDoubleStateOf(start?.carbs ?: 0.0) }
    var touched by remember { mutableStateOf(start == null) } // БЖУ меняли — ккал пересчитываем
    var portion by remember { mutableDoubleStateOf(existing?.defaultGrams ?: 100.0) }
    var aliases by remember { mutableStateOf(existing?.let { aliases(it).joinToString(", ") } ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var barcode by remember { mutableStateOf(start?.barcode ?: "") }
    var photo by remember { mutableStateOf(existing?.photo) }
    // Свои порции (макет 4.3: чипы «1 шт = 60 г» и «+ Порция»); -1 — новая
    var portions by remember { mutableStateOf(existing?.namedPortions ?: emptyList()) }
    var editingPortion by remember { mutableStateOf<Int?>(null) }
    var photoSheet by remember { mutableStateOf(false) }
    val picker = app.fitfat.ui.components.rememberPhotoPicker { photo = it }
    // Источник: данные из Open Food Facts, пока их не меняли руками
    var offData by remember { mutableStateOf(if (start?.source == app.fitfat.data.SOURCE_OFF) listOf(start.protein, start.fat, start.carbs) else null) }
    var offKcal by remember { mutableStateOf(if (start?.source == app.fitfat.data.SOURCE_OFF) start.kcal else null) }
    var looking by remember { mutableStateOf(false) }
    var lookNote by remember { mutableStateOf<String?>(if (prefill != null && prefill.source != app.fitfat.data.SOURCE_OFF) "В Open Food Facts этого штрихкода нет — заполните сами" else null) }
    LaunchedEffect(piece) { if (existing?.perPiece != piece) portion = if (piece) 1.0 else 100.0 }

    val calc = (p * 4 + f * 9 + u * 4)
    val fromOff = offData != null && offData == listOf(p, f, u)
    val kcal = if (fromOff) offKcal ?: calc else if (touched) calc else start?.kcal ?: calc
    val pg = pieceG.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && (!piece || (pg != null && pg > 0))

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconBtn(MockIcons.Close, "Закрыть", onClose)
            Text(if (existing == null) "Новый продукт" else "Изменить продукт", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (existing != null) IconBtn(MockIcons.Delete, "Удалить", { confirmDelete = true }, tint = colors.error)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Фото 96 × 96 (скругление 20) слева от названия, как в макете 4.3
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                app.fitfat.ui.components.PhotoTile(photo, 96.dp, 20.dp, picker.busy) { photoSheet = true }
                Box(Modifier.weight(1f)) {
                    Field("Название") {
                        TextInput(name, { name = it }, "Например: Сырники домашние", 56.dp, "Название")
                    }
                }
            }
            // Штрихкод: камерой или вручную; «Найти» — в Open Food Facts
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Caption("Штрихкод")
                    Text(" · необязательно", fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        TextInput(barcode, { v -> barcode = v.filter { it.isDigit() }.take(14); lookNote = null }, "4600000000000", 56.dp, "Штрихкод", keyboard = KeyboardType.Number)
                    }
                    val canLook = barcode.length >= 8 && !looking
                    if (barcode.length >= 8) {
                        Surface(
                            onClick = {
                                looking = true; lookNote = null
                                scope.launch {
                                    val off = vm.lookup(barcode)
                                    looking = false
                                    if (off == null) lookNote = "В Open Food Facts не нашлось — заполните сами"
                                    else {
                                        name = name.ifBlank { off.name }; p = off.protein; f = off.fat; u = off.carbs
                                        offData = listOf(off.protein, off.fat, off.carbs); offKcal = off.kcal; touched = false
                                        lookNote = "Данные из Open Food Facts — проверьте по упаковке"
                                    }
                                }
                            },
                            enabled = canLook, shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerHighest, modifier = Modifier.height(56.dp),
                        ) {
                            Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                Text(if (looking) "Ищу…" else "Найти", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Surface(onClick = { app.fitfat.ui.components.scanBarcode(ctx) { barcode = it; lookNote = null } }, shape = RoundedCornerShape(16.dp), color = colors.secondaryContainer, modifier = Modifier.size(56.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Barcode, "Сканировать штрихкод", Modifier.size(24.dp), tint = colors.onSecondaryContainer) }
                    }
                }
                lookNote?.let { Text(it, fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp)) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption("Категория")
                CategoryGrid(cat) { cat = it }
            }
            WeightStatePicker(weightState) { weightState = it; weightTouched = true }
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
                    VerticalDrum(p, { p = it; touched = true }, 0.0, mx, 1.0, Modifier.weight(1f), label = "Белки, г", labelColor = MacroColors.protein, band = MacroColors.proteinContainer, decimals = if (kotlin.math.abs(p - Math.round(p)) > 0.01) 1 else 0, keepFraction = true, typeable = true, hint = true)
                    VerticalDrum(f, { f = it; touched = true }, 0.0, mx, 1.0, Modifier.weight(1f), label = "Жиры, г", labelColor = MacroColors.fat, band = MacroColors.fatContainer, decimals = if (kotlin.math.abs(f - Math.round(f)) > 0.01) 1 else 0, keepFraction = true, typeable = true)
                    VerticalDrum(u, { u = it; touched = true }, 0.0, mx, 1.0, Modifier.weight(1f), label = "Углеводы, г", labelColor = MacroColors.carbs, band = MacroColors.carbsContainer, decimals = if (kotlin.math.abs(u - Math.round(u)) > 0.01) 1 else 0, keepFraction = true, typeable = true)
                }
                Text("Нажмите на число, чтобы ввести точно — например 25,3", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption("Порции")
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    portions.forEachIndexed { i, np ->
                        Surface(onClick = { editingPortion = i }, shape = RoundedCornerShape(8.dp), color = colors.surfaceContainerHighest, modifier = Modifier.heightIn(min = 32.dp)) {
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                Text("${np.name} = ${fmt0(np.grams)} г", fontSize = 14.sp)
                            }
                        }
                    }
                    Surface(onClick = { editingPortion = -1 }, shape = RoundedCornerShape(10.dp), color = colors.surfaceContainerHigh, modifier = Modifier.height(36.dp)) {
                        Row(Modifier.padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(MockIcons.Plus, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                            Text("Порция", fontSize = 14.sp)
                        }
                    }
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
                        barcode = barcode.ifBlank { null }, photo = photo, portions = app.fitfat.data.encodePortions(portions),
                        weightState = weightState,
                        // Встроенный остаётся встроенным, пока его КБЖУ не правили; данные Open Food Facts — пока совпадают
                        source = when {
                            fromOff -> app.fitfat.data.SOURCE_OFF
                            existing?.source == app.fitfat.data.SOURCE_SEED && !touched -> app.fitfat.data.SOURCE_SEED
                            existing?.source == app.fitfat.data.SOURCE_AI && !touched -> app.fitfat.data.SOURCE_AI
                            else -> app.fitfat.data.SOURCE_MANUAL
                        },
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

    editingPortion?.let { i ->
        PortionSheet(
            initial = portions.getOrNull(i), unitKcal = if (piece) null else kcal,
            onDismiss = { editingPortion = null },
            onDelete = if (i >= 0) ({ portions = portions.filterIndexed { j, _ -> j != i }; editingPortion = null }) else null,
            onSave = { np -> portions = if (i >= 0) portions.mapIndexed { j, x -> if (j == i) np else x } else portions + np; editingPortion = null },
        )
    }
    if (photoSheet) app.fitfat.ui.components.PhotoSheet(picker, photo != null, onDismiss = { photoSheet = false }, onRemove = { photo = null })
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
private fun TextInput(value: String, onChange: (String) -> Unit, hint: String, height: Dp, desc: String, size: androidx.compose.ui.unit.TextUnit = 16.sp, keyboard: KeyboardType = KeyboardType.Text) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value, onChange, singleLine = true,
        textStyle = TextStyle(color = colors.onSurface, fontSize = size), cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
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

/** Сетка категорий по 3 в ряд (выбранная — рамка `onSurface`, скругление 22); неполный ряд — той же ширины. */
@Composable
fun CategoryGrid(selected: String?, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Categories.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val sel = c.id == selected
                    val r by animateDpAsState(if (sel) 22.dp else 14.dp, label = "r")
                    val border by animateColorAsState(if (sel) colors.onSurface else Color.Transparent, label = "b")
                    Surface(
                        onClick = { onSelect(c.id) }, shape = RoundedCornerShape(r), color = c.bg,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp).border(2.dp, border, RoundedCornerShape(r)),
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                            Icon(c.icon, null, Modifier.size(20.dp), tint = colors.onSurface)
                            Text(shorts[c.id] ?: c.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Своя порция: название («тарелка», «1 штука») и вес линейкой. Слева «Удалить» (у существующей) или «Отмена»,
 * справа «Сохранить». [unitKcal] — ккал на 100 г, чтобы показать калорийность порции.
 */
@Composable
private fun PortionSheet(initial: app.fitfat.data.NamedPortion?, unitKcal: Double?, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (app.fitfat.data.NamedPortion) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var grams by remember { mutableDoubleStateOf(initial?.grams ?: 100.0) }
    val ok = name.isNotBlank() && grams > 0
    app.fitfat.ui.components.FfSheet(onDismiss = onDismiss) {
        Text(if (initial == null) "Новая порция" else "Порция", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        TextInput(name, { name = it }, "Например: тарелка, 1 штука, стакан", 56.dp, "Название порции")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(fmt0(grams), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(" г" + (unitKcal?.let { " · ${fmt0(it * grams / 100)} ккал" } ?: ""), fontSize = 15.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        GramRuler(grams.roundToInt(), { grams = it.toDouble() }, max = 2000)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onDelete != null) {
                Surface(onClick = onDelete, shape = RoundedCornerShape(24.dp), color = colors.errorContainer, modifier = Modifier.weight(1f).height(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("Удалить", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.onErrorContainer) }
                }
            } else {
                Surface(onClick = onDismiss, shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerHighest, modifier = Modifier.weight(1f).height(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("Отмена", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                }
            }
            Surface(
                onClick = { onSave(app.fitfat.data.NamedPortion(name.trim(), grams)) }, enabled = ok, shape = RoundedCornerShape(24.dp),
                color = if (ok) colors.primary else colors.surfaceContainerHighest, modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Text("Сохранить", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (ok) colors.onPrimary else colors.outline) }
            }
        }
    }
}
