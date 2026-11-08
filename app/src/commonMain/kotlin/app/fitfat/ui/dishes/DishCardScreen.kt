package app.fitfat.ui.dishes

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitfat.data.DishIngredient
import app.fitfat.data.Product
import app.fitfat.data.Recognizer
import app.fitfat.data.dishTotals
import app.fitfat.data.portion
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import app.fitfat.ui.components.FfSheet
import app.fitfat.ui.components.GramRuler
import app.fitfat.ui.components.PopDrumChip
import app.fitfat.ui.components.showUndo
import app.fitfat.ui.products.IconBtn
import app.fitfat.ui.products.ProductsViewModel
import app.fitfat.ui.theme.MacroColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val fracs = listOf(2 to "½", 3 to "⅓", 4 to "¼", 6 to "⅙", 0 to "Свой")

/**
 * Карточка блюда (макет Dish-card, 4.4). Сохраняется сама (через полсекунды после правки), как только есть
 * название и хотя бы один ингредиент. Название; «Блюдо · N ингредиентов · готовый вес»; порция долями блюда
 * (½ ⅓ ¼ ⅙) или своим весом; КБЖУ на 100 г; состав — вес чипом с барабаном, смахнуть влево — удалить (с отменой),
 * «+ Ингредиент»; вес готового линейкой; рецепт. Внизу «Добавить порцию в дневник». Фото — плиткой у названия, штрихкод — если блюдо покупное.
 */
@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun DishCardScreen(vm: ProductsViewModel, id: Long, onBack: () -> Unit, onDeleted: () -> Unit, onAddToDiary: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val all by vm.products.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current

    var dishId by remember { mutableLongStateOf(id) }
    var loaded by remember { mutableStateOf(id == 0L) }
    var name by remember { mutableStateOf("") }
    var cooked by remember { mutableStateOf<Double?>(null) } // null — как сырой вес
    var portion by remember { mutableStateOf(0.0) }
    var frac by remember { mutableStateOf(4) }
    var recipe by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<DishIngredient>() }
    var picking by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<String?>(null) }
    var barcode by remember { mutableStateOf("") }
    var photoSheet by remember { mutableStateOf(false) }
    val picker = app.fitfat.ui.components.rememberPhotoPicker { photo = it }

    // Загрузка: блюдо из базы или черновик из «Создать блюдо»
    LaunchedEffect(id) {
        if (id > 0) {
            val p = vm.product(id).first { it != null }!!
            name = p.name; cooked = p.cookedGrams; portion = p.defaultGrams; recipe = p.recipe ?: ""; photo = p.photo; barcode = p.barcode ?: ""
            items.clear(); items.addAll(vm.ingredients(id).first())
            frac = fracs.firstOrNull { (n, _) -> n > 0 && p.cookedGrams != null && (p.cookedGrams / n).roundToInt() == p.defaultGrams.roundToInt() }?.first ?: 0
        } else {
            items.clear(); items.addAll(vm.draft); vm.draft = emptyList()
        }
        loaded = true
    }

    val raw = items.sumOf { it.grams }
    val cookedNow = (cooked ?: raw).coerceAtLeast(0.0)
    val pg = if (frac > 0 && cookedNow > 0) cookedNow / frac else portion
    val total = dishTotals(items, all)
    val k100 = if (cookedNow > 0) 100 / cookedNow else 0.0
    val valid = name.isNotBlank() && items.isNotEmpty() && cookedNow > 0

    // Автосохранение
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        snapshotFlow { listOf(name, cooked, portion, frac, recipe, photo, barcode, items.toList()) }.debounce(500).collect {
            // Всё считаем заново из текущего состояния — значения из кадра здесь устаревшие
            val rawNow = items.sumOf { it.grams }
            val c = cooked ?: rawNow
            if (name.isBlank() || items.isEmpty() || c <= 0) return@collect
            val pgNow = if (frac > 0) c / frac else portion
            val base = vm.products.value.firstOrNull { it.id == dishId } ?: Product(name = name, category = "dish", kcal = 0.0, protein = 0.0, fat = 0.0, carbs = 0.0)
            vm.saveDish(
                base.copy(name = name.trim(), category = "dish", cookedGrams = c, defaultGrams = pgNow.coerceAtLeast(1.0), recipe = recipe.ifBlank { null }, photo = photo, barcode = barcode.ifBlank { null },
                    aliases = (listOf(name.trim().lowercase()) + base.aliases.split(',').map { it.trim() }).filter { it.isNotBlank() }.distinct().joinToString(",")),
                items.toList(),
            ) { newId -> dishId = newId }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconBtn(MockIcons.ChevronLeft, "Назад", onBack)
                Text(if (dishId == 0L) "Новое блюдо" else "Блюдо", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (dishId > 0) IconBtn(MockIcons.Delete, "Удалить", { confirmDelete = true }, tint = colors.error)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Название
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Фото 84 × 84 (скругление 22), как в макете 4.4
                    app.fitfat.ui.components.PhotoTile(photo, 84.dp, 22.dp, picker.busy) { focus.clearFocus(); photoSheet = true }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Название — в рамке (видно, что это поле); пустое — серая подсказка и карандаш
                        BasicTextField(
                            name, { name = it }, singleLine = true,
                            textStyle = TextStyle(color = colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold), cursorBrush = SolidColor(colors.primary),
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Название блюда" },
                            decorationBox = { inner ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
                                        .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(Modifier.weight(1f)) {
                                        if (name.isEmpty()) Text("Название блюда", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant, maxLines = 1)
                                        inner()
                                    }
                                    if (name.isEmpty()) Icon(MockIcons.Edit, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                                }
                            },
                        )
                        Text("Блюдо · ${ingredientsWord(items.size)} · готовый вес ${fmt0(cookedNow)} г", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                }
                // Порция
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.primaryContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Порция · " + (fracs.firstOrNull { it.first == frac && frac > 0 }?.let { "1/${it.first} блюда" } ?: "свой вес"), fontSize = 15.sp, fontWeight = FontWeight(650), color = colors.onPrimaryContainer, modifier = Modifier.weight(1f))
                        Text("${fmt0(pg)} г · ${fmt0(total.kcal * k100 * pg / 100)} ккал", fontSize = 15.sp, color = colors.onPrimaryContainer)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fracs.forEach { (n, label) ->
                            val sel = n == frac
                            val bg by animateColorAsState(if (sel) colors.onPrimaryContainer else colors.onPrimaryContainer.copy(alpha = 0.14f), label = "fb")
                            Surface(onClick = { if (n == 0 && frac != 0) portion = pg; frac = n }, shape = RoundedCornerShape(if (sel) 20.dp else 12.dp), color = bg, modifier = Modifier.weight(1f).height(40.dp)) {
                                Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (sel) colors.primaryContainer else colors.onPrimaryContainer) }
                            }
                        }
                    }
                    if (frac == 0) GramRuler(portion.roundToInt(), { portion = it.toDouble() }, max = 3000)
                }
                // На 100 г
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("На 100 г", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    MiniChip(fmt0(total.kcal * k100), "ккал", colors.onSurface, colors.surfaceContainerHigh)
                    MiniChip(fmt1(total.protein * k100), "г", MacroColors.protein, MacroColors.proteinContainer)
                    MiniChip(fmt1(total.fat * k100), "г", MacroColors.fat, MacroColors.fatContainer)
                    MiniChip(fmt1(total.carbs * k100), "г", MacroColors.carbs, MacroColors.carbsContainer)
                }
                // Блюдо считается на готовый вес, ингредиенты — до готовки (DESIGN.md, «Сухой и готовый вес»)
                app.fitfat.ui.products.WeightNote(
                    "Готовое блюдо · ингредиенты до готовки",
                    "КБЖУ блюда — на 100 г готового. Ингредиенты вписывайте в сухом или сыром весе, до готовки: у варёного и жареного цифры сильно различаются, а по сухой массе расчёт точнее всего.",
                    warn = false,
                )
                // Состав
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.Bottom) {
                        Text("Состав", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("сырой вес ${fmt0(raw)} г", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerLow)) {
                        items.forEachIndexed { i, x ->
                            androidx.compose.runtime.key(x.productId, i) {
                                IngredientRow(x, all.firstOrNull { it.id == x.productId }, first = i == 0,
                                    onGrams = { g -> items[i] = x.copy(grams = g) },
                                    onDelete = {
                                        val removed = items.removeAt(i)
                                        scope.launch {
                                            if (snack.showUndo("«${all.firstOrNull { it.id == removed.productId }?.name ?: "Ингредиент"}» убран")) items.add(i.coerceAtMost(items.size), removed)
                                        }
                                    },
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { focus.clearFocus(); picking = true }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(MockIcons.Plus, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                            Text("Ингредиент", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                // Готовое блюдо
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Вес готового блюда", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                        Text(fmt0(cookedNow), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(" г", fontSize = 15.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    GramRuler(cookedNow.roundToInt(), { cooked = it.toDouble() }, max = 6000)
                    Text("Взвесьте казан без крышки и вычтите его вес — так КБЖУ на 100 г будут точными.", fontSize = 12.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant)
                }
                // Штрихкод — если блюдо покупное (макет 4.4): вручную или камерой
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Штрихкод · если блюдо покупное", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            barcode, { v -> barcode = v.filter { it.isDigit() }.take(14) }, singleLine = true,
                            textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp), cursorBrush = SolidColor(colors.primary),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh).padding(horizontal = 14.dp)
                                .semantics { contentDescription = "Штрихкод блюда" },
                            decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { if (barcode.isEmpty()) Text("не указан", fontSize = 16.sp, color = colors.onSurfaceVariant); inner() } },
                        )
                        Surface(onClick = { focus.clearFocus(); app.fitfat.ui.components.scanBarcode { barcode = it } }, shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerHighest, modifier = Modifier.size(56.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Barcode, "Сканировать штрихкод", Modifier.size(24.dp), tint = colors.onSurface) }
                        }
                    }
                }
                // Рецепт
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Рецепт", fontSize = 16.sp, fontWeight = FontWeight(650), modifier = Modifier.padding(horizontal = 8.dp))
                    RecipeBlock(recipe, writing, onStart = { writing = true }, onCancel = { writing = false }, onSave = { recipe = it; writing = false })
                }
            }
        }
        Surface(
            onClick = { if (dishId > 0) onAddToDiary(dishId) }, enabled = valid && dishId > 0, shape = RoundedCornerShape(28.dp),
            color = if (valid && dishId > 0) colors.primary else colors.surfaceContainerHighest,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).fillMaxWidth().height(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (valid) "Добавить порцию в дневник" else "Нужны название и ингредиенты",
                    fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp, color = if (valid && dishId > 0) colors.onPrimary else colors.outline,
                )
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp))
    }

    if (picking) IngredientPicker(all.filter { !it.isDish }, onDismiss = { picking = false }) { p ->
        picking = false
        val g = if (p.perPiece) (p.pieceGrams ?: 50.0) * p.defaultGrams.coerceAtLeast(1.0) else p.defaultGrams
        items.add(DishIngredient(dishId = dishId, productId = p.id, grams = g))
    }
    if (photoSheet) app.fitfat.ui.components.PhotoSheet(picker, photo != null, onDismiss = { photoSheet = false }, onRemove = { photo = null })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(MockIcons.Delete, null, tint = colors.error) },
            title = { Text("Удалить «$name»?") },
            text = { Text("Блюдо и его рецепт исчезнут из базы. Записи в дневнике останутся — их КБЖУ сохранены.") },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(dishId, onDeleted) }) { Text("Удалить", color = colors.error) } },
        )
    }
}

/** Ингредиент: плашка 36, название и его КБЖУ, вес — чип с барабаном. Смахнуть влево — убрать (с сопротивлением, как дневник). */
@Composable
private fun IngredientRow(x: DishIngredient, p: Product?, first: Boolean, onGrams: (Double) -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    app.fitfat.ui.components.SwipeRemove(
        onRemove = onDelete, label = "Удалить", hintKey = app.fitfat.data.Hints.SWIPE_DISH,
        nudge = app.fitfat.ui.components.nudgeWanted(app.fitfat.data.Hints.SWIPE_DISH, first),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerLow).heightIn(min = 60.dp).padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryIcon(p?.category ?: "grain", 36.dp, radius = 12.dp, iconSize = 18.dp, photo = p?.photo)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p?.name ?: "Удалённый продукт", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                    // В каком виде взвешен ингредиент; готовый в рецепте — подсвечен: обычно его надо взвешивать до готовки
                    app.fitfat.data.WeightState.word(p?.weightState)?.let { w ->
                        val cooked = p?.weightState == app.fitfat.data.WEIGHT_COOKED
                        Text(w, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (cooked) colors.onTertiaryContainer else colors.onSurfaceVariant,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (cooked) colors.tertiaryContainer else colors.surfaceContainerHigh).padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                if (p != null) {
                    val pcs = if (p.perPiece) p.pieceGrams?.takeIf { it > 0 }?.let { x.grams / it } ?: 0.0 else null
                    val m = p.portion(x.grams, pcs)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MiniChip(fmt0(m.kcal), "ккал", colors.onSurface, colors.surfaceContainerHigh)
                        MiniChip(fmt1(m.protein), "", MacroColors.protein, MacroColors.proteinContainer)
                        MiniChip(fmt1(m.fat), "", MacroColors.fat, MacroColors.fatContainer)
                        MiniChip(fmt1(m.carbs), "", MacroColors.carbs, MacroColors.carbsContainer)
                    }
                }
            }
            // Чип веса — по ширине числа (не уже 60), без пустого места слева
            Row(Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max).widthIn(min = 60.dp)) {
                PopDrumChip(x.grams, onGrams, "г", colors.onSurface, colors.surfaceContainerHighest, p?.name ?: "Вес", stepFor = { if (it < 100) 5.0 else 10.0 }, display = { fmt0(it) }, hint = first)
            }
        }
    }
}

@Composable
private fun MiniChip(value: String, unit: String, ink: Color, bg: Color) {
    Row(Modifier.height(24.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
        if (unit.isNotEmpty()) Text(unit, fontSize = 9.sp, color = ink.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
private fun RecipeBlock(recipe: String, writing: Boolean, onStart: () -> Unit, onCancel: () -> Unit, onSave: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    when {
        writing -> {
            var draft by remember { mutableStateOf(recipe) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    draft, { draft = it }, textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp, lineHeight = 21.sp), cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerHigh).padding(14.dp)
                        .semantics { contentDescription = "Текст рецепта" },
                    decorationBox = { inner -> Box { if (draft.isEmpty()) Text("1. Обжарить мясо в казане…", fontSize = 15.sp, color = colors.onSurfaceVariant); inner() } },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onCancel) { Text("Отмена") }
                    Surface(onClick = { onSave(draft.trim()) }, shape = RoundedCornerShape(20.dp), color = colors.primary, modifier = Modifier.height(40.dp)) {
                        Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) { Text("Сохранить", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary) }
                    }
                }
            }
        }
        recipe.isBlank() -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Рецепт пока не записан", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            Surface(onClick = onStart, shape = RoundedCornerShape(18.dp), color = colors.surfaceContainerHighest, modifier = Modifier.heightIn(min = 36.dp)) {
                Row(Modifier.padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(MockIcons.Edit, null, Modifier.size(16.dp), tint = colors.onSurface)
                    Text("Записать рецепт", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        else -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(recipe, fontSize = 15.sp, lineHeight = 22.sp)
            Surface(onClick = onStart, color = Color.Transparent, modifier = Modifier.align(Alignment.End)) {
                Text("Изменить", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

/** Выбор продукта для состава: поиск и список. */
@Composable
private fun IngredientPicker(products: List<Product>, onDismiss: () -> Unit, onPick: (Product) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    val query = Recognizer.normalize(q)
    val list = products.filter { query.isEmpty() || (listOf(it.name) + it.aliases.split(',')).any { n -> Recognizer.normalize(n).contains(query) } }
        .sortedWith(compareByDescending<Product> { it.lastUsed }.thenBy { it.name }).take(40)
    FfSheet(onDismiss = onDismiss, gap = 10.dp) {
        Text("Ингредиент", fontSize = 20.sp, fontWeight = FontWeight(650))
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(MockIcons.Search, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
            BasicTextField(
                q, { q = it }, singleLine = true, textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp), cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Поиск продукта" },
                decorationBox = { inner -> Box { if (q.isEmpty()) Text("Поиск продукта", fontSize = 16.sp, color = colors.onSurfaceVariant); inner() } },
            )
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            list.forEach { p ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onPick(p) }.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryIcon(p.category, 40.dp, radius = 12.dp, iconSize = 20.dp, photo = p.photo)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${fmt0(p.kcal)} ккал " + (if (p.perPiece) "на 1 шт" else "на 100 г") + (app.fitfat.data.WeightState.word(p.weightState)?.let { " · $it вес" } ?: ""), fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
