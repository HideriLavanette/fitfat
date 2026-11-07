package app.fitfat.ui.add

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitfat.data.Macros
import app.fitfat.data.Product
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import java.time.LocalDate

/**
 * Добавление еды (макеты Add-empty 3.1 и Add-food 3.2). Поля 12 / 16 / 20.
 * Пусто — «Часто в <приём>» и «Недавние» с переходом к порции; есть продукты — итог «+N ккал / останется»
 * и короткие «Недавние» с «+». Кнопки «штрихкод», «блюдо», «Посмотреть все» ведут на экраны 0.4 и появятся с ними.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFoodScreen(vm: AddFoodViewModel, date: LocalDate, onClose: () -> Unit) {
    LaunchedEffect(date) { vm.start(date) }
    val products by vm.products.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val frequent by vm.frequent.collectAsStateWithLifecycle()
    val left by vm.leftKcal.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val recognized = remember(vm.text, products) { vm.recognized() }

    var focused by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<PickState?>(null) }
    var editing by remember { mutableStateOf<Picked?>(null) }
    var portion by remember { mutableStateOf<FrequentItem?>(null) }
    var variants by remember { mutableStateOf<Recognized?>(null) }
    var creating by remember { mutableStateOf<Recognized?>(null) }
    var whenOpen by remember { mutableStateOf(false) }
    // Фрагмент, для которого выбирали порцию, исчез из текста — закрываем выбор
    LaunchedEffect(vm.text) { pick?.let { if (!vm.text.contains(it.r.fragment.raw)) pick = null } }

    fun accept(r: Recognized, p: Product) {
        // Вес написан — сразу в список, иначе «Сколько съели»
        if (r.fragment.grams != null || r.fragment.pieces != null) vm.confirm(r, p, r.fragment.grams, r.fragment.pieces)
        else pick = PickState(r, p)
    }

    val colors = MaterialTheme.colorScheme
    val hasItems = vm.picked.isNotEmpty()
    val unknown = recognized.firstOrNull { it.candidates.isEmpty() && it.fragment.query.isNotBlank() }
    val modal = editing != null || portion != null || variants != null || creating != null || whenOpen

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (hasItems) 12.dp else 14.dp),
    ) {
        // Шапка: закрыть · «Добавить» · чип «Сейчас · Обед»
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(onClick = onClose, shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Close, "Закрыть", Modifier.size(24.dp), tint = colors.onSurface) }
            }
            Text("Добавить", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Surface(onClick = { whenOpen = true }, shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh, modifier = Modifier.height(40.dp)) {
                Row(Modifier.padding(start = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(MockIcons.Clock, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                    Text(whenLabel(vm.date, vm.time, vm.meal, vm.timeTouched), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Composer(focused, hasItems, onTap = { focus.requestFocus() }) {
            vm.picked.forEach { item -> PickedRow(item) { editing = item } }
            RecognizeField(
                text = vm.text, onText = vm::onText, recognized = recognized, pick = pick,
                onAccept = ::accept,
                onVariants = { variants = it },
                onPickDone = {
                    pick?.let { s ->
                        val p = s.product
                        vm.confirm(s.r, p, if (p.perPiece) null else s.grams, if (p.perPiece) s.pieces else null)
                    }
                    pick = null
                },
                onFocus = { focused = it },
                popupsEnabled = !modal,
                placeholder = !hasItems,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .then(if (hasItems) Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = 2.dp) else Modifier)
                    .heightIn(min = if (hasItems) 28.dp else 72.dp),
            )
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (hasItems) 12.dp else 14.dp)) {
            unknown?.let { u -> UnknownBanner(u.fragment.query) { creating = u } }

            if (hasItems) {
                val total = vm.picked.fold(Macros()) { a, it -> a + it.macros }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("+${fmt0(total.kcal)} ккал", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Б ${fmt1(total.protein)} · Ж ${fmt1(total.fat)} · У ${fmt1(total.carbs)}", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("останется", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        Text("${fmt0(left - total.kcal)} ккал", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else if (frequent.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Часто ${mealIn(vm.meal)}", Modifier.padding(horizontal = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        frequent.forEach { fr ->
                            Surface(onClick = { portion = fr }, shape = RoundedCornerShape(8.dp), color = colors.surfaceContainerHigh, modifier = Modifier.height(32.dp)) {
                                Row(Modifier.padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CategoryIcon(fr.product.category, 24.dp, radius = 12.dp, iconSize = 16.dp)
                                    Text("${fr.product.name} ${amount(fr.product, fr.grams, fr.pieces)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            if (recent.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(if (hasItems) 4.dp else 6.dp)) {
                    Row(Modifier.height(36.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) { SectionTitle("Недавние") }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(if (hasItems) 22.dp else 24.dp)).background(colors.surfaceContainerLow)) {
                        recent.forEach { p ->
                            if (hasItems) RecentCompact(p) { vm.add(p, null, null) }
                            else RecentRow(p) { portion = FrequentItem(p, p.defaultGrams, if (p.perPiece) p.defaultGrams else null) }
                        }
                    }
                }
            }
        }

        val n = vm.picked.size
        Surface(
            onClick = { vm.save(onClose) }, enabled = hasItems, shape = RoundedCornerShape(28.dp),
            color = if (hasItems) colors.primary else colors.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (hasItems) "Добавить $n ${plural(n)}" else "Добавить",
                    fontSize = if (hasItems) 16.sp else 17.sp, fontWeight = if (hasItems) FontWeight.Medium else FontWeight.SemiBold,
                    letterSpacing = if (hasItems) 0.15.sp else 0.sp,
                    color = if (hasItems) colors.onPrimary else colors.outline,
                )
            }
        }
    }

    editing?.let { item ->
        EditPickedDialog(
            item, onDismiss = { editing = null },
            onRemove = { vm.remove(item); editing = null },
            onSave = { g, pcs, ov -> vm.update(item, g, pcs, ov); editing = null },
        )
    }
    portion?.let { fr ->
        PortionDialog(fr.product, fr.grams, fr.pieces, onDismiss = { portion = null }) { g, pcs -> vm.add(fr.product, g, pcs); portion = null }
    }
    variants?.let { r ->
        VariantsDialog(
            r, onDismiss = { variants = null },
            onPick = { p -> vm.remember(r, p); variants = null; accept(r, p) },
            onCreate = { variants = null; creating = r },
        )
    }
    creating?.let { r ->
        NewProductSheet(r, onDismiss = { creating = null }) { p, g, pcs -> vm.createProduct(p, r, g, pcs) { creating = null } }
    }
    if (whenOpen) WhenSheet(vm, onDismiss = { whenOpen = false })
}

/**
 * Карточка «Что съели»: скругление 24, без фокуса — `surfaceContainer`, в фокусе — `surfaceContainerHigh`,
 * масштаб 1,03 с пружиной и тенью. Поля 14 (пусто) / 12 (есть продукты).
 */
@Composable
private fun Composer(focused: Boolean, hasItems: Boolean, onTap: () -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "scale")
    val elev by animateDpAsState(if (focused) 14.dp else 1.dp, tween(400), label = "elev")
    val bg by animateColorAsState(if (focused) colors.surfaceContainerHigh else colors.surfaceContainer, tween(250), label = "bg")
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.3f) }
            .shadow(elev, shape).clip(shape).background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .padding(if (hasItems) 12.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (hasItems) 8.dp else 6.dp),
    ) {
        Text("Что съели", fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = if (hasItems) 2.dp else 0.dp))
        content()
    }
}

/** Подтверждённый продукт: 56+, поля 6 / 12 / 6 / 6, скругление 16, `surfaceContainerHighest`. */
@Composable
private fun PickedRow(item: Picked, onTap: () -> Unit) {
    val m = item.macros
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onTap, shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerHighest, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryIcon(item.product.category, 44.dp, radius = 14.dp, iconSize = 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(item.product.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(item.pieces?.let { "${fmt1(it)} шт · ~${fmt0(item.grams)} г" } ?: "${fmt0(item.grams)} г", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("${fmt0(m.kcal)} ккал", fontSize = 15.sp, fontWeight = FontWeight(650))
                Text("Б ${fmt1(m.protein)} · Ж ${fmt1(m.fat)} · У ${fmt1(m.carbs)}", fontSize = 11.sp, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** «соус терияки» нет в базе · «+ Создать продукт». Поля 10 / 10 / 10 / 16, скругление 18. */
@Composable
private fun UnknownBanner(word: String, onCreate: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer).padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            buildAnnotatedString {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append("«$word»"); pop()
                append(" нет в базе")
            },
            fontSize = 14.sp, modifier = Modifier.weight(1f),
        )
        Surface(onClick = onCreate, shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer, modifier = Modifier.height(40.dp)) {
            Row(Modifier.padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(MockIcons.Plus, null, Modifier.size(18.dp), tint = colors.onSurface)
                Text("Создать продукт", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, fontSize = 15.sp, fontWeight = FontWeight(650), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

/** Недавний продукт на пустом экране: 60, плашка 40 / 12, стрелка — к порции. */
@Composable
private fun RecentRow(p: Product, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(60.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIcon(p.category, 40.dp, radius = 12.dp, iconSize = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(recentSub(p), fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
        Icon(MockIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
    }
}

/** Недавний продукт, когда уже что-то выбрано: 56, плашка 36, «+» 40 — добавить обычную порцию. */
@Composable
private fun RecentCompact(p: Product, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIcon(p.category, 36.dp, radius = 12.dp, iconSize = 18.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(recentSub(p), fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Surface(onClick = onAdd, shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(MockIcons.Plus, "Добавить ${p.name}", Modifier.size(18.dp), tint = colors.onSurface) }
        }
    }
}

/** «250 г · 133 ккал» или «1 шт · 248 ккал» — обычная порция продукта. */
private fun recentSub(p: Product): String {
    val pcs = if (p.perPiece) p.defaultGrams.coerceAtLeast(1.0) else null
    val kcal = if (p.perPiece) p.kcal * pcs!! else p.kcal * p.defaultGrams / 100
    return "${amount(p, p.defaultGrams, pcs)} · ${fmt0(kcal)} ккал"
}

private fun amount(p: Product, grams: Double, pieces: Double?) =
    if (p.perPiece) "${fmt1(pieces ?: 1.0)} шт" else "${fmt0(grams)} г"

private fun mealIn(m: app.fitfat.data.Meal) = when (m) {
    app.fitfat.data.Meal.Breakfast -> "на завтрак"
    app.fitfat.data.Meal.Lunch -> "в обед"
    app.fitfat.data.Meal.Dinner -> "на ужин"
}

private fun plural(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "продукт"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "продукта"
    else -> "продуктов"
}
