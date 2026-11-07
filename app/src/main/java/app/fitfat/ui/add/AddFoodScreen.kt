package app.fitfat.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fitfat.data.Meal
import app.fitfat.data.Product
import app.fitfat.data.gramsFor
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFoodScreen(vm: AddFoodViewModel, date: LocalDate, onClose: () -> Unit) {
    LaunchedEffect(date) { vm.start(date) }
    val products by vm.products.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val recognized = remember(vm.text, products) { vm.recognized() }
    var editing by remember { mutableStateOf<Picked?>(null) }
    var creating by remember { mutableStateOf<Recognized?>(null) }
    var asking by remember { mutableStateOf<Pair<Recognized, Product>?>(null) }
    var creatingBlank by remember { mutableStateOf(false) }
    var timePicking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Закрыть") }
            Text("Добавить", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            AssistChip(
                onClick = { timePicking = true },
                label = { Text("%02d:%02d · %s".format(vm.time.hour, vm.time.minute, vm.meal.label)) },
                leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(vm.picked, key = { it.key }) { item -> PickedRow(item, onTap = { editing = item }, onRemove = { vm.remove(item) }) }
            item {
                RecognizeField(
                    text = vm.text,
                    onText = vm::onText,
                    recognized = recognized,
                    onConfirm = { r, p ->
                        // Вес не написан — сначала спросим граммы или штуки
                        if (r.fragment.grams == null && r.fragment.pieces == null) asking = r to p
                        else vm.confirm(r, p, r.fragment.grams, r.fragment.pieces)
                    },
                    onCreate = { creating = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { creatingBlank = true }) { Icon(Icons.Outlined.Add, null); Text(" Новый продукт") }
                }
            }
            if (recent.isNotEmpty()) {
                item { Text("Недавние", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { p ->
                            AssistChip(
                                onClick = { vm.addRecent(p) },
                                label = { Text(p.name + if (p.perPiece) " · ${fmt1(p.defaultGrams)} шт" else " · ${fmt0(p.defaultGrams)} г") },
                                leadingIcon = { CategoryIcon(p.category, 24.dp) },
                            )
                        }
                    }
                }
            }
        }
        val total = vm.picked.fold(app.fitfat.data.Macros()) { a, it -> a + it.macros }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vm.picked.isNotEmpty()) {
                    Row {
                        Text("+${fmt0(total.kcal)} ккал", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Б ${fmt1(total.protein)} · Ж ${fmt1(total.fat)} · У ${fmt1(total.carbs)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = { vm.save(onClose) },
                    enabled = vm.picked.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp),
                ) { Text(if (vm.picked.isEmpty()) "Подтвердите продукты" else "Добавить ${vm.picked.size} ${plural(vm.picked.size)}") }
            }
        }
    }

    editing?.let { item ->
        PortionDialog(item, onDismiss = { editing = null }, onSave = { g, pcs -> vm.update(item, g, pcs); editing = null })
    }
    asking?.let { (r, p) ->
        // Порция по умолчанию у продукта: штуки для штучных, граммы для остальных
        val pcs = if (p.perPiece) p.defaultGrams.coerceAtLeast(0.5) else null
        val draft = Picked(0, p, if (p.perPiece) p.gramsFor(pcs!!) else p.defaultGrams, pcs)
        PortionDialog(draft, onDismiss = { asking = null }, onSave = { g, pc -> vm.confirm(r, p, g, pc); asking = null })
    }
    creating?.let { r ->
        CreateProductDialog(initialName = r.fragment.query.replaceFirstChar { it.uppercase() }, onDismiss = { creating = null }) { p -> vm.createProduct(p, r) { creating = null } }
    }
    if (creatingBlank) {
        CreateProductDialog(initialName = "", onDismiss = { creatingBlank = false }) { p -> vm.createProduct(p, null) { creatingBlank = false } }
    }
    if (timePicking) {
        val st = rememberTimePickerState(vm.time.hour, vm.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timePicking = false },
            title = { Text("Когда поели") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(st)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Meal.entries.forEachIndexed { i, m ->
                            SegmentedButton(selected = vm.meal == m, onClick = { vm.selectMeal(m) }, shape = SegmentedButtonDefaults.itemShape(i, Meal.entries.size)) { Text(m.label) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.changeTime(LocalTime.of(st.hour, st.minute)); timePicking = false }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = { timePicking = false }) { Text("Отмена") } },
        )
    }
}

private fun plural(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "продукт"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "продукта"
    else -> "продуктов"
}

@Composable
private fun PickedRow(item: Picked, onTap: () -> Unit, onRemove: () -> Unit) {
    val m = item.macros
    Surface(onClick = onTap, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryIcon(item.product.category, 40.dp)
            Column(Modifier.weight(1f)) {
                Text(item.product.name, fontWeight = FontWeight.SemiBold)
                Text(item.pieces?.let { "${fmt1(it)} шт · ≈${fmt0(item.grams)} г" } ?: "${fmt0(item.grams)} г", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${fmt0(m.kcal)} ккал", fontWeight = FontWeight.SemiBold)
                Text("Б ${fmt1(m.protein)} · Ж ${fmt1(m.fat)} · У ${fmt1(m.carbs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Убрать") }
        }
    }
}
