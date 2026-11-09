package app.fitfat.ui.dishes

import app.fitfat.ui.components.tick
import app.fitfat.ui.components.bump
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitfat.data.DishIngredient
import app.fitfat.data.Macros
import app.fitfat.data.Product
import app.fitfat.data.Recognizer
import app.fitfat.data.dishTotals
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.macroLine
import app.fitfat.ui.components.FfSheet
import app.fitfat.ui.components.GramRuler
import app.fitfat.ui.components.tick
import app.fitfat.ui.products.IconBtn
import app.fitfat.ui.products.ProductsViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** «1 ингредиент», «3 ингредиента», «5 ингредиентов». */
fun ingredientsWord(n: Int) = "$n " + when {
    n % 10 == 1 && n % 100 != 11 -> "ингредиент"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "ингредиента"
    else -> "ингредиентов"
}

/**
 * Мои блюда (макет Dishes, 3.5): поиск, карточки блюд (плашка 52, «N ингредиентов · Б · Ж · У» на 100 г, ккал/100 г),
 * «Создать блюдо». Тап — шторка порции: граммы линейкой, «Состав в этот раз» (меняется только эта запись),
 * «Отмена / Добавить», «Открыть карточку блюда».
 */
@Composable
fun DishesScreen(
    vm: ProductsViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    /** Открыть карточку нового блюда с готовым черновиком ([ProductsViewModel.draft], [ProductsViewModel.draftMeta]). */
    onCreateDraft: () -> Unit = onCreate,
    onOpenCard: (Long) -> Unit,
    onAdd: (dish: Product, grams: Double, override: Macros?) -> Unit,
) {
    val all by vm.products.collectAsStateWithLifecycle()
    val ingredients by vm.allIngredients.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<Product?>(null) }
    val colors = MaterialTheme.colorScheme
    // «Из описания» — только с доступом к ассистенту (DESIGN.md, «Подписка и доступ»)
    val access by app.fitfat.data.Access.state.collectAsStateWithLifecycle()
    val aiOn = app.fitfat.data.Assistant.available && access.has
    var fromText by remember { mutableStateOf(false) }
    val query = Recognizer.normalize(q)
    val dishes = all.filter { it.isDish && (query.isEmpty() || Recognizer.normalize(it.name).contains(query)) }
        .sortedWith(compareByDescending<Product> { it.lastUsed }.thenBy { it.name })

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconBtn(MockIcons.ChevronLeft, "Назад", onBack)
            Text("Мои блюда", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(colors.surfaceContainer).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(MockIcons.Search, null, Modifier.size(22.dp), tint = colors.onSurfaceVariant)
            BasicTextField(
                q, { q = it }, singleLine = true, textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp), cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Поиск блюда" },
                decorationBox = { inner -> Box { if (q.isEmpty()) Text("Поиск блюда", fontSize = 16.sp, color = colors.onSurfaceVariant); inner() } },
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (dishes.isEmpty()) item {
                Text(
                    if (all.none { it.isDish }) "Блюд пока нет. Соберите блюдо из продуктов — например, плов или сырники, — и записывайте его порциями."
                    else "Ничего не нашлось",
                    fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                )
            }
            items(dishes, key = { it.id }) { d ->
                val n = ingredients.count { it.dishId == d.id }
                Surface(onClick = { open = d }, shape = RoundedCornerShape(22.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val photo = d.photo
                        if (photo != null) app.fitfat.ui.components.PhotoImage(photo, Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(colors.secondaryContainer))
                        else Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
                            Icon(MockIcons.Dish, null, Modifier.size(24.dp), tint = colors.onSecondaryContainer)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(d.name, fontSize = 16.sp, fontWeight = FontWeight(650))
                            Text(macroLine(d.protein, d.fat, d.carbs, prefix = ingredientsWord(n) + " · "), fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(fmt0(d.kcal), fontSize = 16.sp, fontWeight = FontWeight(650))
                            Text("ккал/100 г", fontSize = 11.sp, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Рецепт своими словами — ассистент собирает карточку блюда (0.6, 09.11.2026)
            if (aiOn) Surface(onClick = { fromText = true }, shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, modifier = Modifier.weight(1f).height(56.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Icon(MockIcons.NavAssistant, null, Modifier.size(20.dp), tint = colors.onSurface)
                    Text("Из описания", fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp)
                }
            }
            Surface(onClick = onCreate, shape = RoundedCornerShape(28.dp), color = colors.primary, modifier = Modifier.weight(1f).height(56.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Icon(MockIcons.Plus, null, Modifier.size(22.dp), tint = colors.onPrimary)
                    Text(if (aiOn) "Создать" else "Создать блюдо", fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp, color = colors.onPrimary)
                }
            }
        }
    }

    if (fromText) RecipeFromTextSheet(vm, onDismiss = { fromText = false }, onReady = { fromText = false; onCreateDraft() })

    open?.let { d ->
        DishPortionSheet(
            d, ingredients.filter { it.dishId == d.id }, all,
            onDismiss = { open = null },
            onAdd = { g, ov -> open = null; onAdd(d, g, ov) },
            onOpenCard = { open = null; onOpenCard(d.id) },
        )
    }
}

/**
 * Шторка порции блюда: граммы 36 / 750 и ккал, линейка, «на 100 г», «Состав в этот раз» — ± к ингредиентам
 * только для этой записи (рецепт не меняется), «состав изменён» и «Сбросить». КБЖУ записи пересчитываются
 * из изменённого состава: готовый вес меняется пропорционально сырому.
 */
@Composable
fun DishPortionSheet(
    dish: Product,
    items: List<DishIngredient>,
    all: List<Product>,
    onDismiss: () -> Unit,
    onAdd: (grams: Double, override: Macros?) -> Unit,
    onOpenCard: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    var g by remember { mutableDoubleStateOf(dish.defaultGrams) }
    val grams = remember(items) { mutableStateListOf<Double>().apply { addAll(items.map { it.grams }) } }
    var expanded by remember { mutableStateOf(false) }
    val changed = items.indices.any { grams[it] != items[it].grams }
    val base = dishTotals(items, all)
    val cur = dishTotals(items.mapIndexed { i, x -> x.copy(grams = grams[i]) }, all)
    val baseRaw = items.sumOf { it.grams }.coerceAtLeast(1.0)
    val cooked = (dish.cookedGrams ?: baseRaw) * (grams.sum().coerceAtLeast(1.0) / baseRaw)
    val portion = if (changed) cur * (g / cooked) else Macros(dish.kcal, dish.protein, dish.fat, dish.carbs) * (g / 100)
    val per100 = if (changed) cur.kcal / cooked * 100 else dish.kcal

    FfSheet(onDismiss = onDismiss, gap = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(dish.name, fontSize = 20.sp, fontWeight = FontWeight(650), modifier = Modifier.weight(1f))
            AnimatedVisibility(changed) {
                Box(Modifier.clip(RoundedCornerShape(13.dp)).background(colors.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("состав изменён", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onSecondaryContainer)
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fmt0(g), fontSize = 36.sp, fontWeight = FontWeight(750))
            Text(" г", fontSize = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp).weight(1f))
            Text(fmt0(portion.kcal), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(" ккал", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        GramRuler(g.roundToInt(), { g = it.toDouble() }, max = 3000)
        Text("на 100 г: ${fmt0(per100)} ккал", fontSize = 12.sp, color = colors.onSurfaceVariant)
        if (items.isNotEmpty()) {
            Surface(onClick = { expanded = !expanded }, shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(MockIcons.Edit, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                    Text("Состав в этот раз", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    val nChanged = items.indices.count { grams[it] != items[it].grams }
                    Text(if (changed) "изменено: $nChanged" else ingredientsWord(items.size), fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerLow)) {
                    items.forEachIndexed { i, x ->
                        val p = all.firstOrNull { it.id == x.productId }
                        val diff = grams[i] != x.grams
                        val step = if (p?.perPiece == true) p.pieceGrams ?: 10.0 else if (x.grams < 100) 5.0 else 10.0
                        val ink by animateColorAsState(if (diff) colors.primary else colors.onSurface, label = "ink")
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (diff) colors.primary else Color.Transparent))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(p?.name ?: "Удалённый продукт", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (diff) "в рецепте ${fmt0(x.grams)} г" else "как в рецепте", fontSize = 11.sp, color = colors.onSurfaceVariant)
                            }
                            StepBtn(MockIcons.Minus, "${p?.name}: меньше") { if (grams[i] - step >= 0) { grams[i] = grams[i] - step; tick() } }
                            Text("${fmt0(grams[i])} г", fontSize = 15.sp, fontWeight = FontWeight(650), color = ink, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 64.dp))
                            StepBtn(MockIcons.Plus, "${p?.name}: больше") { grams[i] = grams[i] + step; tick() }
                        }
                    }
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Меняется только эта запись. Рецепт блюда остаётся прежним.", fontSize = 12.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (changed) Surface(onClick = { items.forEachIndexed { i, x -> grams[i] = x.grams } }, shape = RoundedCornerShape(16.dp), color = Color.Transparent) {
                            Text("Сбросить", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = onDismiss, shape = RoundedCornerShape(26.dp), color = Color.Transparent, modifier = Modifier.weight(1f).height(52.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Отмена", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.primary) }
            }
            Surface(onClick = { onAdd(g, if (changed) portion else null) }, shape = RoundedCornerShape(26.dp), color = colors.primary, modifier = Modifier.weight(1.4f).height(52.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Добавить", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary) }
            }
        }
        if (onOpenCard != null) {
            Surface(onClick = onOpenCard, color = Color.Transparent, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Открыть карточку блюда", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun StepBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface) }
    }
}

/**
 * «Блюдо из описания» (0.6, 09.11.2026, макета нет): рецепт своими словами → ассистент раскладывает его на ингредиенты
 * с весом и КБЖУ → открывается карточка нового блюда на проверку (ингредиенты-оценки — с жёлтой меткой).
 * Описание сохраняется, пока шторка открыта; ошибка — текстом над кнопками, описание не теряется.
 */
@Composable
private fun RecipeFromTextSheet(vm: ProductsViewModel, onDismiss: () -> Unit, onReady: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var consent by remember { mutableStateOf(false) }

    fun go() {
        if (text.isBlank() || busy) return
        if (!app.fitfat.data.Assistant.consented()) { consent = true; return }
        busy = true; error = null
        scope.launch {
            val known = app.fitfat.data.Assistant.similar(text, vm.products.value.filter { !it.isDish }, 30)
            when (val r = app.fitfat.data.Assistant.recipe(text.trim(), known)) {
                is app.fitfat.data.Assistant.RecipeReply.Ok -> vm.draftFromRecipe(r) { busy = false; onReady() }
                is app.fitfat.data.Assistant.RecipeReply.Error -> { busy = false; error = r.message }
            }
        }
    }

    FfSheet(onDismiss = { if (!busy) onDismiss() }) {
        Text("Блюдо из описания", fontSize = 22.sp, fontWeight = FontWeight(650))
        Text(
            "Напишите, из чего готовили и сколько чего положили. Ассистент соберёт карточку блюда — вы проверите и поправите.",
            fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant,
        )
        BasicTextField(
            text, { text = it; error = null },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHighest)
                .padding(16.dp).semantics { contentDescription = "Описание блюда" },
            textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp, lineHeight = 22.sp),
            cursorBrush = SolidColor(colors.primary),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text(
                        "Борщ: свёкла 300 г, говядина 400 г, картошка 3 шт, капуста 200 г, морковь, лук, 2 ст. л. масла, вода 2 л. Вышло 3 кг",
                        fontSize = 16.sp, lineHeight = 22.sp, color = colors.onSurfaceVariant,
                    )
                    inner()
                }
            },
        )
        error?.let { Text(it, fontSize = 14.sp, lineHeight = 20.sp, color = colors.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Закрывающее — слева
            Surface(onClick = onDismiss, enabled = !busy, shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, modifier = Modifier.weight(1f).height(52.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Отмена", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            }
            val can = text.isNotBlank() && !busy
            Surface(onClick = { go() }, enabled = can, shape = RoundedCornerShape(28.dp), color = if (can || busy) colors.primary else colors.surfaceContainerHigh, modifier = Modifier.weight(1f).height(52.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.onPrimary)
                    Text(if (busy) "Собираю…" else "Собрать", fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        color = if (can || busy) colors.onPrimary else colors.onSurfaceVariant)
                }
            }
        }
    }

    if (consent) {
        app.fitfat.ui.add.AssistantConsentDialog(onDismiss = { consent = false }) {
            app.fitfat.data.Assistant.giveConsent(); consent = false; go()
        }
    }
}
