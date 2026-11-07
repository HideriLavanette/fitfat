package app.fitfat.ui.products

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.items
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
private enum class Tag(val label: String) { Meat("Мясо и яйца"), Dairy("Молочное"), Grain("Крупы"), Plant("Овощи и фрукты"), Sweet("Сладкое и соусы"), Piece("Штучные") }

private fun Tag.icon(): ImageVector = when (this) {
    Tag.Meat -> category("meat").icon
    Tag.Dairy -> category("dairy").icon
    Tag.Grain -> category("grain").icon
    Tag.Plant -> category("veg").icon
    Tag.Sweet -> MockIcons.Sweet
    Tag.Piece -> MockIcons.Pieces
}

private fun Tag.matches(p: Product) = when (this) {
    Tag.Meat -> p.category == "meat" || p.category == "egg"
    Tag.Dairy -> p.category == "dairy"
    Tag.Grain -> p.category == "grain"
    Tag.Plant -> p.category == "veg" || p.category == "fruit"
    Tag.Sweet -> p.category == "sauce" || p.category == "sweet" || p.category == "oil"
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
 * Переключатель «Все / Продукты / Блюда» и сканер появятся вместе с блюдами и штрихкодами.
 */
@Composable
fun ProductsScreen(vm: ProductsViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit, onNew: () -> Unit) {
    val all by vm.products.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableIntStateOf(0) }
    var tags by remember { mutableStateOf(setOf<Tag>()) }
    val colors = MaterialTheme.colorScheme

    val query = Recognizer.normalize(q)
    val items = remember(all, query, period, tags) {
        all.filter { p ->
            (query.isEmpty() || (listOf(p.name) + p.aliases.split(',')).any { Recognizer.normalize(it).contains(query) }) &&
                (period == 0 || (daysAgo(p.lastUsed)?.let { it <= periods[period].second } ?: false)) &&
                (tags.isEmpty() || tags.any { it.matches(p) })
        }.sortedWith(compareByDescending<Product> { it.lastUsed }.thenBy { it.name })
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(onClick = onBack, shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(MockIcons.ChevronLeft, "Назад", Modifier.size(24.dp), tint = colors.onSurface) }
                }
                Text("Все продукты", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${items.size} из ${all.size}", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
            }
            // Поиск
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(colors.surfaceContainer).padding(start = 16.dp, end = 16.dp),
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
            }
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
                    val bg by animateColorAsState(if (sel) colors.secondaryContainer else colors.surfaceContainerHigh, label = "tbg")
                    Surface(
                        onClick = { tags = if (sel) tags - t else tags + t }, shape = RoundedCornerShape(8.dp), color = bg,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Row(Modifier.padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(t.icon(), null, Modifier.size(16.dp), tint = if (sel) colors.onSecondaryContainer else colors.onSurfaceVariant)
                            Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (sel) colors.onSecondaryContainer else colors.onSurface, maxLines = 1)
                        }
                    }
                }
            }
            // Список
            Box(Modifier.weight(1f).padding(horizontal = 16.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(colors.surfaceContainerLow)) {
                if (items.isEmpty()) {
                    Text(
                        if (all.isEmpty()) "База пуста" else "Ничего не нашлось — попробуйте убрать фильтры",
                        fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 32.dp),
                    )
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(items, key = { it.id }) { p -> ProductRow(p) { onOpen(p.id) } }
                }
            }
        }
        // «+ Продукт»
        Surface(
            onClick = onNew, shape = RoundedCornerShape(16.dp), color = colors.primaryContainer, shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = 24.dp).height(56.dp),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MockIcons.Plus, null, Modifier.size(24.dp), tint = colors.onPrimaryContainer)
                Text("Продукт", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer)
            }
        }
    }
}

/** Строка продукта: 62+, плашка 44, название 15 / 600, Б · Ж · У [· 1 шт ≈ 50 г] · когда; справа ккал и «ккал/100 г». */
@Composable
private fun ProductRow(p: Product, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tail = (if (p.perPiece && p.pieceGrams != null) " · 1 шт ≈ ${fmt0(p.pieceGrams)} г" else "") + (usedAgo(p.lastUsed)?.let { " · $it" } ?: "")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIcon(p.category, 44.dp, radius = 14.dp, iconSize = 22.dp)
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
