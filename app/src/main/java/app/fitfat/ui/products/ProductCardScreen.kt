package app.fitfat.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.Product
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import app.fitfat.ui.theme.MacroColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Встроенная база — первые строки таблицы (сид при создании БД). Всё, что новее, — продукты пользователя. */
private const val SEED_COUNT = 31

/**
 * Карточка продукта (макет Product-card, 4.2): иконка 76 и название 24 / 700, откуда продукт;
 * КБЖУ на 100 г или 1 шт — ккал 34, полоса долей энергии, Б / Ж / У с процентами; обычная порция;
 * слова, по которым он распознаётся (+ «Добавить»); сколько раз записывали; «Добавить в дневник».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductCardScreen(vm: ProductsViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit, onAddToDiary: () -> Unit) {
    val p by remember(id) { vm.product(id) }.collectAsState(initial = null)
    val colors = MaterialTheme.colorScheme
    var addingAlias by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconBtn(MockIcons.ChevronLeft, "Назад", onBack)
            Text("Продукт", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (p != null) IconBtn(MockIcons.Edit, "Изменить", onEdit)
        }
        val prod = p ?: return@Column
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryIcon(prod.category, 76.dp, radius = 26.dp, iconSize = 38.dp, photo = prod.photo)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(prod.name, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(MockIcons.Check, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                        Text(sourceLabel(prod), fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
            Nutrition(prod)
            Section("Порции") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerLow)) {
                    val portion = if (prod.perPiece) {
                        val n = prod.defaultGrams.coerceAtLeast(1.0)
                        "${fmt1(n)} шт" + (prod.pieceGrams?.let { " ≈ ${fmt0(it * n)} г" } ?: "") + " · ${fmt0(prod.kcal * n)} ккал"
                    } else "${fmt0(prod.defaultGrams)} г · ${fmt0(prod.kcal * prod.defaultGrams / 100)} ккал"
                    PortionRow("Обычная порция", portion)
                    if (prod.perPiece && prod.pieceGrams != null) PortionRow("1 шт", "${fmt0(prod.pieceGrams)} г · ${fmt0(prod.kcal)} ккал")
                }
            }
            Section("Распознаётся по словам") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    aliases(prod).forEach { a ->
                        Box(Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerHighest).padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(a, fontSize = 14.sp)
                        }
                    }
                    Surface(onClick = { addingAlias = true }, shape = RoundedCornerShape(10.dp), color = colors.surfaceContainerHigh, modifier = Modifier.heightIn(min = 32.dp)) {
                        Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(MockIcons.Plus, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                            Text("Добавить", fontSize = 14.sp)
                        }
                    }
                }
            }
            Text(usage(prod), fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Surface(onClick = onAddToDiary, shape = RoundedCornerShape(28.dp), color = colors.primary, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Icon(MockIcons.Plus, null, Modifier.size(22.dp), tint = colors.onPrimary)
                Text("Добавить в дневник", fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp, color = colors.onPrimary)
            }
        }
    }

    if (addingAlias) p?.let { prod ->
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingAlias = false },
            title = { Text("Другое название") },
            text = {
                BasicTextField(
                    text, { text = it }, singleLine = true,
                    textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp), cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHighest),
                    decorationBox = { inner ->
                        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) Text("Например: курица гриль", fontSize = 16.sp, color = colors.onSurfaceVariant)
                            inner()
                        }
                    },
                )
            },
            // Закрывающее — слева
            dismissButton = { TextButton(onClick = { addingAlias = false }) { Text("Отмена") } },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    val list = (prod.aliases.split(',').map { it.trim() } + text.trim().lowercase()).filter { it.isNotBlank() }.distinct()
                    vm.save(prod.copy(aliases = list.joinToString(","))) {}
                    addingAlias = false
                }) { Text("Добавить") }
            },
        )
    }
}

/** Откуда данные продукта. */
fun sourceLabel(p: Product) = when (p.source ?: if (p.id <= SEED_COUNT) app.fitfat.data.SOURCE_SEED else app.fitfat.data.SOURCE_MANUAL) {
    app.fitfat.data.SOURCE_SEED -> "Из встроенной базы"
    app.fitfat.data.SOURCE_OFF -> "Из Open Food Facts"
    else -> "Введён вручную"
} + (p.barcode?.let { " · штрихкод $it" } ?: "")

/** Слова распознавания без самого названия. */
fun aliases(p: Product) = p.aliases.split(',').map { it.trim() }.filter { it.isNotBlank() && !it.equals(p.name, ignoreCase = true) }.distinct()

private fun usage(p: Product): String {
    if (p.useCount <= 0 || p.lastUsed <= 0) return "Ещё не записывали"
    val n = p.useCount
    val times = if (n % 10 in 2..4 && n % 100 !in 12..14) "раза" else "раз"
    val when_ = usedAgo(p.lastUsed) ?: ""
    val time = SimpleDateFormat("HH:mm", Locale("ru")).format(Date(p.lastUsed))
    return "Записывали $n $times · последний раз $when_ в $time"
}

/** Пищевая ценность: на 100 г / 1 шт, ккал 34 / 750; полоса долей энергии Б / Ж / У; три колонки с процентами. */
@Composable
private fun Nutrition(p: Product) {
    val colors = MaterialTheme.colorScheme
    val eP = p.protein * 4; val eF = p.fat * 9; val eU = p.carbs * 4
    val sum = (eP + eF + eU).takeIf { it > 0 } ?: 1.0
    fun pct(e: Double) = (e / sum * 100).roundToInt()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (p.perPiece) "на 1 шт" else "на 100 г", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f).padding(bottom = 6.dp))
            Text(fmt0(p.kcal), fontSize = 34.sp, fontWeight = FontWeight(750))
            Text(" ккал", fontSize = 15.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(eP to MacroColors.protein, eF to MacroColors.fat, eU to MacroColors.carbs).filter { it.first > 0 }.forEach { (e, c) ->
                Box(Modifier.weight((e / sum).toFloat()).fillMaxSize().background(c))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Белки", p.protein, MacroColors.protein) to eP, Triple("Жиры", p.fat, MacroColors.fat) to eF, Triple("Углеводы", p.carbs, MacroColors.carbs) to eU)
                .forEach { (t, e) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(t.first, fontSize = 12.sp, color = t.third)
                        Text("${fmt1(t.second)} г", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(if (e > 0) "${pct(e)}% энергии" else "0%", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                }
        }
    }
}

@Composable
private fun PortionRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight(650), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        content()
    }
}

@Composable
fun IconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc, Modifier.size(22.dp), tint = tint) }
    }
}
