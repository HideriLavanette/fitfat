package app.fitfat.ui.products

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import app.fitfat.ui.components.Feedback
import app.fitfat.ui.components.FfSheet
import app.fitfat.ui.components.GramRuler
import app.fitfat.ui.components.VerticalDrum
import app.fitfat.ui.components.showUndo
import app.fitfat.ui.theme.MacroColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitfat.data.Product
import app.fitfat.data.Recognizer
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.category
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.macroLine
import java.util.concurrent.TimeUnit

/** Теги фильтра (макет Products): id, подпись, иконка. */
private enum class Tag(val label: String) { Meat("Мясо и яйца"), Dairy("Молочное"), Grain("Крупы"), Plant("Овощи и фрукты"), Sweet("Сладкое и соусы"), Drink("Напитки"), Piece("Штучные") }

private fun Tag.icon(): ImageVector = when (this) {
    Tag.Meat -> category("meat").icon
    Tag.Dairy -> category("dairy").icon
    Tag.Grain -> category("grain").icon
    Tag.Plant -> category("veg").icon
    Tag.Sweet -> MockIcons.Sweet
    Tag.Drink -> category("drink").icon
    Tag.Piece -> MockIcons.Pieces
}

/** Цвет тега — фон его категории (как у плашек продуктов); у «Штучных» своего цвета нет. */
private fun Tag.bg(): Color? = when (this) {
    Tag.Meat -> category("meat").bg
    Tag.Dairy -> category("dairy").bg
    Tag.Grain -> category("grain").bg
    Tag.Plant -> category("veg").bg
    Tag.Sweet -> category("sauce").bg
    Tag.Drink -> category("drink").bg
    Tag.Piece -> null
}

private fun Tag.matches(p: Product) = when (this) {
    Tag.Meat -> p.category == "meat" || p.category == "egg"
    Tag.Dairy -> p.category == "dairy"
    Tag.Grain -> p.category == "grain"
    Tag.Plant -> p.category == "veg" || p.category == "fruit"
    Tag.Sweet -> p.category == "sauce" || p.category == "sweet" || p.category == "oil"
    Tag.Drink -> p.category == "drink"
    Tag.Piece -> p.perPiece
}

/** Период по последнему использованию: подпись, сколько дней назад максимум. */
private val periods = listOf("Любое время" to Long.MAX_VALUE, "Сегодня" to 0L, "За неделю" to 7L, "За месяц" to 30L)

private fun daysAgo(lastUsed: Long): Long? =
    if (lastUsed <= 0) null else TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastUsed).coerceAtLeast(0)

/** «сегодня», «вчера», «3 дн. назад», «неделю назад», «давно». */
fun usedAgo(lastUsed: Long): String? = daysAgo(lastUsed)?.let { d ->
    when { d == 0L -> "сегодня"; d == 1L -> "вчера"; d < 7 -> "$d дн. назад"; d < 14 -> "неделю назад"; else -> "давно" }
}

/**
 * Все продукты (макет Products, 4.1): поиск по названию и другим именам, период по последней записи,
 * теги категорий (несколько сразу), список «N из M». Кнопка «+ Продукт» — новый продукт.
 * «Все / Продукты / Блюда» — тип; сканер появится вместе со штрихкодами.
 */
@Composable
fun ProductsScreen(vm: ProductsViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit, onNew: () -> Unit, onNewFromScan: () -> Unit = onNew, onAddToDiary: (Long) -> Unit = {}, onNewDish: () -> Unit = {}) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val all by vm.products.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableIntStateOf(0) }
    var tags by remember { mutableStateOf(setOf<Tag>()) }
    var type by rememberSaveable { mutableIntStateOf(0) } // 0 все, 1 продукты, 2 блюда
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    // Выбор нескольких: зажать строку; дальше тап добавляет / убирает
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val selecting = selected.isNotEmpty()
    var bulk by remember { mutableStateOf(Bulk.None) }
    var confirmDelete by remember { mutableStateOf<Set<Long>?>(null) }
    androidx.activity.compose.BackHandler(enabled = selecting) { selected = emptySet() }
    // Выбранные, которые пропали из базы, из выбора убираем
    LaunchedEffect(all) { val ids = all.map { it.id }.toSet(); if (!ids.containsAll(selected)) selected = selected intersect ids }
    fun undoable(msg: String, old: List<Product>) {
        selected = emptySet()
        if (old.isNotEmpty()) scope.launch { if (snack.showUndo(msg)) vm.restore(old) }
    }

    val query = Recognizer.normalize(q)
    val items = remember(all, query, period, tags, type) {
        all.filter { p ->
            (type == 0 || (type == 2) == p.isDish) && (query.isEmpty() || (listOf(p.name) + p.aliases.split(',')).any { Recognizer.normalize(it).contains(query) }) &&
                (period == 0 || (daysAgo(p.lastUsed)?.let { it <= periods[period].second } ?: false)) &&
                (tags.isEmpty() || tags.any { it.matches(p) })
        }.sortedWith(compareByDescending<Product> { it.lastUsed }.thenBy { it.name })
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Шапка: обычная или режим выбора (закрыть выбор — слева)
            androidx.compose.animation.AnimatedContent(
                selecting, label = "head",
                transitionSpec = { (fadeIn(tween(200)) + slideInVertically(tween(220)) { -it / 4 }).togetherWith(fadeOut(tween(150))) },
            ) { sel ->
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!sel) {
                        Surface(onClick = onBack, shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(MockIcons.ChevronLeft, "Назад", Modifier.size(24.dp), tint = colors.onSurface) }
                        }
                        Text("Все продукты", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${items.size} из ${all.size}", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                    } else {
                        Surface(onClick = { selected = emptySet() }, shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Close, "Отменить выбор", Modifier.size(24.dp), tint = colors.onSurface) }
                        }
                        androidx.compose.animation.AnimatedContent(selected.size, label = "n") { n ->
                            Text("Выбрано: $n", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.weight(1f))
                        val allIn = items.isNotEmpty() && items.all { it.id in selected }
                        Surface(onClick = { selected = if (allIn) selected - items.map { it.id }.toSet() else selected + items.map { it.id } }, shape = RoundedCornerShape(18.dp), color = Color.Transparent) {
                            Text(if (allIn) "Снять все" else "Выбрать все", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                        Surface(onClick = { confirmDelete = selected }, shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Delete, "Удалить выбранные", Modifier.size(22.dp), tint = colors.error) }
                        }
                    }
                }
            }
            // В режиме выбора вместо поиска — действия над выбранными
            if (selecting) {
                Row(Modifier.fillMaxWidth().height(52.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BulkChip(category("grain").icon, "Категория") { bulk = Bulk.Category }
                    BulkChip(MockIcons.Edit, "БЖУ") { bulk = Bulk.Macros }
                    BulkChip(MockIcons.Pieces, "Порция") { bulk = Bulk.Portion }
                }
            } else
            // Поиск
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(colors.surfaceContainer).padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(MockIcons.Search, null, Modifier.size(22.dp), tint = colors.onSurfaceVariant)
                BasicTextField(
                    q, { q = it }, singleLine = true,
                    textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Поиск" },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (q.isEmpty()) Text("Название или другое имя", fontSize = 16.sp, color = colors.onSurfaceVariant)
                            inner()
                        }
                    },
                )
                if (q.isNotEmpty()) {
                    Surface(onClick = { q = "" }, shape = RoundedCornerShape(16.dp), color = Color.Transparent, modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Close, "Очистить", Modifier.size(18.dp), tint = colors.onSurfaceVariant) }
                    }
                }
                // Скан: есть в базе — карточка, нет — новый продукт со штрихкодом
                Surface(
                    onClick = { app.fitfat.ui.components.scanBarcode(ctx) { code -> vm.resolveBarcode(code, onFound = { onOpen(it.id) }, onNew = onNewFromScan) } },
                    shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHighest, modifier = Modifier.size(40.dp),
                ) { Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Barcode, "Сканировать штрихкод", Modifier.size(20.dp), tint = colors.onSurface) } }
            }
            // Тип: все / продукты / блюда
            app.fitfat.ui.components.PillToggle(
                listOf("Все", "Продукты", "Блюда"), type, onSelect = { type = it },
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), height = 40.dp, fontSize = 13.sp,
            )
            // Период + теги в один ряд с прокруткой
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                val on = period != 0
                val pBg by animateColorAsState(if (on) colors.secondaryContainer else Color.Transparent, label = "pbg")
                Surface(
                    onClick = { period = (period + 1) % periods.size }, shape = RoundedCornerShape(12.dp), color = pBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (on) colors.primary else colors.outlineVariant),
                    modifier = Modifier.height(36.dp).semantics { contentDescription = "Период: ${periods[period].first}" },
                ) {
                    Row(Modifier.padding(start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(MockIcons.Calendar, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                        Text(periods[period].first, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                Tag.entries.forEach { t ->
                    val sel = t in tags
                    // Теги категорий — в цвете своей категории (палитра плашек); выбранный — рамка `onSurface` и скругление 16
                    val catBg = t.bg() ?: colors.surfaceContainerHigh
                    val bg by animateColorAsState(if (t.bg() == null && sel) colors.secondaryContainer else catBg, label = "tbg")
                    val border by animateColorAsState(if (sel) colors.onSurface else Color.Transparent, label = "tbr")
                    val r by androidx.compose.animation.core.animateDpAsState(if (sel) 16.dp else 8.dp, label = "trad")
                    Surface(
                        onClick = { tags = if (sel) tags - t else tags + t }, shape = RoundedCornerShape(r), color = bg,
                        border = androidx.compose.foundation.BorderStroke(2.dp, border),
                        modifier = Modifier.height(32.dp).semantics { contentDescription = t.label + if (sel) ", выбран" else "" },
                    ) {
                        Row(Modifier.padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (sel) MockIcons.Check else t.icon(), null, Modifier.size(16.dp), tint = colors.onSurface)
                            Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface, maxLines = 1)
                        }
                    }
                }
            }
            // Список
            // Фон — у самих строк (первая скруглена сверху, последняя снизу): контейнер кончается вместе со списком
            Box(Modifier.weight(1f).padding(horizontal = 16.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
                if (items.isEmpty()) {
                    Text(
                        if (all.isEmpty()) "База пуста" else "Ничего не нашлось — попробуйте убрать фильтры",
                        fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 32.dp),
                    )
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    itemsIndexed(items, key = { _, it -> it.id }) { i, p ->
                        val sel = p.id in selected
                        val shape = RoundedCornerShape(
                            topStart = if (i == 0) 24.dp else 0.dp, topEnd = if (i == 0) 24.dp else 0.dp,
                            bottomStart = if (i == items.lastIndex) 24.dp else 0.dp, bottomEnd = if (i == items.lastIndex) 24.dp else 0.dp,
                        )
                        Box(Modifier.clip(shape).background(colors.surfaceContainerLow)) {
                        ProductRow(
                            p, selecting, sel,
                            onClick = { if (selecting) selected = if (sel) selected - p.id else selected + p.id else onOpen(p.id) },
                            onLongClick = { selected = selected + p.id },
                            onAdd = { onAddToDiary(p.id) },
                            onDelete = { confirmDelete = setOf(p.id) },
                            first = i == 0,
                        )
                        }
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp))
        // Связанная пара кнопок «+ Блюдо | + Продукт»: снаружи скругление 16, на стыке 4, зазор 2 (группа кнопок M3)
        androidx.compose.animation.AnimatedVisibility(
            !selecting, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 24.dp),
            enter = fadeIn(tween(200)) + androidx.compose.animation.scaleIn(tween(200), initialScale = 0.9f),
            exit = fadeOut(tween(150)) + androidx.compose.animation.scaleOut(tween(150), targetScale = 0.9f),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                AddButton("Блюдо", RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 4.dp, bottomEnd = 4.dp), onNewDish)
                AddButton("Продукт", RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp), onNew)
            }
        }
    }

    val sel = all.filter { it.id in selected }
    when (bulk) {
        Bulk.None -> {}
        Bulk.Category -> BulkSheet("Категория", sel.size, sel.count { it.isDish }.takeIf { it > 0 }?.let { "Блюда не меняются" }, onDismiss = { bulk = Bulk.None }) {
            CategoryGrid(null) { c -> bulk = Bulk.None; vm.setCategoryMany(selected, c) { old -> undoable("Категория изменена: ${productsWord(old.size)}", old) } }
        }
        Bulk.Macros -> {
            val first = sel.firstOrNull { !it.isDish }
            var p by remember { mutableDoubleStateOf(first?.protein ?: 0.0) }
            var f by remember { mutableDoubleStateOf(first?.fat ?: 0.0) }
            var u by remember { mutableDoubleStateOf(first?.carbs ?: 0.0) }
            val notes = listOfNotNull(
                sel.count { it.isDish }.takeIf { it > 0 }?.let { "блюда считаются из состава и не меняются" },
                sel.count { it.perPiece && !it.isDish }.takeIf { it > 0 }?.let { "у штучных — на 1 шт" },
            ).joinToString("; ").ifEmpty { null }?.replaceFirstChar { it.uppercase() }
            BulkSheet("БЖУ на 100 г", sel.size, notes, onDismiss = { bulk = Bulk.None }) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(fmt0(p * 4 + f * 9 + u * 4), fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    Text("ккал · считается из БЖУ", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VerticalDrum(p, { p = it }, 0.0, 100.0, 1.0, Modifier.weight(1f), label = "Белки, г", labelColor = MacroColors.protein, band = MacroColors.proteinContainer, decimals = if (kotlin.math.abs(p - Math.round(p)) > 0.01) 1 else 0, keepFraction = true, typeable = true, hint = true)
                    VerticalDrum(f, { f = it }, 0.0, 100.0, 1.0, Modifier.weight(1f), label = "Жиры, г", labelColor = MacroColors.fat, band = MacroColors.fatContainer, decimals = if (kotlin.math.abs(f - Math.round(f)) > 0.01) 1 else 0, keepFraction = true, typeable = true)
                    VerticalDrum(u, { u = it }, 0.0, 100.0, 1.0, Modifier.weight(1f), label = "Углеводы, г", labelColor = MacroColors.carbs, band = MacroColors.carbsContainer, decimals = if (kotlin.math.abs(u - Math.round(u)) > 0.01) 1 else 0, keepFraction = true, typeable = true)
                }
                Text("Нажмите на число, чтобы ввести точно — например 25,3", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                ApplyButton(sel.count { !it.isDish }) { bulk = Bulk.None; vm.setMacrosMany(selected, p, f, u) { old -> undoable("БЖУ изменены: ${productsWord(old.size)}", old) } }
            }
        }
        Bulk.Portion -> {
            var g by remember { mutableDoubleStateOf(sel.firstOrNull { !it.perPiece }?.defaultGrams ?: 100.0) }
            BulkSheet("Обычная порция", sel.size, sel.count { it.perPiece }.takeIf { it > 0 }?.let { "У штучных порция в штуках — они не меняются" }, onDismiss = { bulk = Bulk.None }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    Text(fmt0(g), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(" г", fontSize = 15.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                GramRuler(g.roundToInt(), { g = it.toDouble() }, max = 3000)
                ApplyButton(sel.count { !it.perPiece }) { bulk = Bulk.None; vm.setPortionMany(selected, g) { old -> undoable("Порция изменена: ${productsWord(old.size)}", old) } }
            }
        }
    }
    confirmDelete?.let { ids ->
        val one = if (ids.size == 1) all.firstOrNull { it.id in ids } else null
        val what = when {
            one?.isDish == true -> "Блюдо и его рецепт исчезнут из базы."
            one != null -> "Продукт исчезнет из базы."
            else -> "Они исчезнут из базы."
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(MockIcons.Delete, null, tint = colors.error) },
            title = { Text(if (one != null) "Удалить «${one.name}»?" else "Удалить ${productsWord(ids.size)}?") },
            text = { Text("$what Записи в дневнике останутся — их КБЖУ сохранены.") },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = null; vm.deleteMany(ids) { selected = selected - ids } }) { Text("Удалить", color = colors.error) } },
        )
    }
}

private enum class Bulk { None, Category, Macros, Portion }

/** Половинка пары «+ Блюдо | + Продукт»: высота 56, `primaryContainer`, тень 6. */
@Composable
private fun AddButton(label: String, shape: RoundedCornerShape, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = shape, color = colors.primaryContainer, shadowElevation = 6.dp, modifier = Modifier.height(56.dp)) {
        Row(Modifier.padding(start = 14.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(MockIcons.Plus, null, Modifier.size(22.dp), tint = colors.onPrimaryContainer)
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer)
        }
    }
}

/** «1 продукт», «3 продукта», «5 продуктов». */
fun productsWord(n: Int) = "$n " + when {
    n % 10 == 1 && n % 100 != 11 -> "продукт"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "продукта"
    else -> "продуктов"
}

/** Действие над выбранными: чип 40 dp, `surfaceContainerHigh`; удаление — `errorContainer`. */
@Composable
private fun BulkChip(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ink = if (danger) colors.onErrorContainer else colors.onSurface
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = if (danger) colors.errorContainer else colors.surfaceContainerHigh, modifier = Modifier.height(40.dp)) {
        Row(Modifier.padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = ink)
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
        }
    }
}

/** Шторка массовой правки: заголовок, «для N продуктов», пояснение, что не меняется. */
@Composable
private fun BulkSheet(title: String, n: Int, note: String?, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FfSheet(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            Text("Для $n " + if (n % 10 == 1 && n % 100 != 11) "продукта" else "продуктов", fontSize = 13.sp, color = colors.onSurfaceVariant)
            note?.let { Text(it, fontSize = 12.sp, color = colors.onSurfaceVariant) }
        }
        content()
    }
}

@Composable
private fun ApplyButton(n: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ok = n > 0
    Surface(
        onClick = onClick, enabled = ok, shape = RoundedCornerShape(28.dp), color = if (ok) colors.primary else colors.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (ok) "Применить" else "Нечего менять", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (ok) colors.onPrimary else colors.outline)
        }
    }
}

private const val SWIPE_RESIST = 56f // dp: до «срыва» строка тянется с сопротивлением не дальше этого
private const val SWIPE_SOFT = 70f
private const val SWIPE_SNAP = 110f  // dp пальца: порог срабатывания

/**
 * Строка продукта: 62+, плашка 44, название 15 / 600, Б · Ж · У [· 1 шт ≈ 50 г] · когда; справа ккал и «ккал/100 г».
 * Смахнуть вправо — в дневник, влево — удалить (с подтверждением): как строки дневника — сопротивление,
 * на пороге «срыв» с вибрацией (без звука), дальше строка идёт за пальцем. Зажать — выбор нескольких; в выборе свайпа нет.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductRow(p: Product, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onAdd: () -> Unit, onDelete: () -> Unit, first: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val dx = remember { Animatable(0f) }
    var released by remember { mutableStateOf(false) }
    val d = dx.value
    val hints = remember { app.fitfat.data.Hints.get(view.context) }
    // Первая строка сама тянется в обе стороны, пока свайп не пробовали
    app.fitfat.ui.components.SwipeNudge(dx, app.fitfat.ui.components.nudgeWanted(app.fitfat.data.Hints.SWIPE_PRODUCTS, first) && !selecting, both = true)
    val tail = (if (p.perPiece && p.pieceGrams != null) " · 1 шт ≈ ${fmt0(p.pieceGrams)} г" else "") + (usedAgo(p.lastUsed)?.let { " · $it" } ?: "")
    val bg by animateColorAsState(if (selected) colors.secondaryContainer else colors.surfaceContainerLow, label = "rowbg")
    Box(Modifier.fillMaxWidth().heightIn(min = 62.dp)) {
        // Подложка: слева «В дневник», справа «Удалить»
        Row(
            Modifier.matchParentSize().background(if (d > 0) colors.primaryContainer else if (d < 0) colors.errorContainer else Color.Transparent).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.alpha(if (d > 0) (if (released) 1f else 0.55f * min(d / SWIPE_RESIST, 1f)) else 0f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MockIcons.Plus, null, Modifier.size(22.dp), tint = colors.onPrimaryContainer)
                Text("В дневник", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.alpha(if (d < 0) (if (released) 1f else 0.55f * min(-d / SWIPE_RESIST, 1f)) else 0f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Удалить", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onErrorContainer)
                Icon(MockIcons.Delete, null, Modifier.size(22.dp), tint = colors.onErrorContainer)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 62.dp)
                .offset { IntOffset(with(density) { d.dp.roundToPx() }, 0) }
                .clip(RoundedCornerShape((20f * min(abs(d) / 24f, 1f)).dp))
                .background(bg)
                .pointerInput(p.id, selecting) {
                    if (selecting) return@pointerInput
                    var raw = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { raw = dx.value; released = false; hints.used(app.fitfat.data.Hints.SWIPE_PRODUCTS) },
                        onDragEnd = {
                            val v = dx.value
                            val was = released
                            released = false
                            if (was && v > 0) onAdd()
                            if (was && v < 0) onDelete()
                            scope.launch { dx.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f)) }
                        },
                        onDragCancel = { released = false; scope.launch { dx.animateTo(0f) } },
                    ) { ch, amountPx ->
                        ch.consume()
                        raw = (raw + amountPx / density.density).coerceIn(-320f, 320f)
                        val a = abs(raw)
                        if (!released && a >= SWIPE_SNAP) { released = true; Feedback.tickSilent(view.context) }
                        else if (released && a < SWIPE_SNAP * 0.75f) { released = false; Feedback.light(view.context) }
                        val target = if (released) raw else sign(raw) * SWIPE_RESIST * (1f - exp(-a / SWIPE_SOFT))
                        scope.launch {
                            if (released) dx.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 1600f)) else dx.snapTo(target)
                        }
                    }
                }
                .combinedClickable(onClick = onClick, onLongClick = { Feedback.tickSilent(view.context); onLongClick() })
                .semantics { contentDescription = "${p.name}. Смахните вправо — в дневник, влево — удалить; удерживайте, чтобы выбрать несколько" }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // В выборе плашка превращается в галочку
            androidx.compose.animation.Crossfade(selected, animationSpec = tween(160), label = "check") { s ->
                if (s) Box(Modifier.size(44.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                    Icon(MockIcons.Check, null, Modifier.size(22.dp), tint = colors.onPrimary)
                } else CategoryIcon(p.category, 44.dp, radius = 14.dp, iconSize = 22.dp, photo = p.photo)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(macroLine(p.protein, p.fat, p.carbs, tail = tail), fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt0(p.kcal), fontSize = 15.sp, fontWeight = FontWeight(650))
                Text(if (p.perPiece) "ккал/шт" else "ккал/100 г", fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}
