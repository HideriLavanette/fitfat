package app.fitfat.ui.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fitfat.data.Product
import app.fitfat.data.portion
import app.fitfat.ui.common.Categories
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.Drum
import app.fitfat.ui.common.GRAM_MAX
import app.fitfat.ui.common.GRAM_STEP
import app.fitfat.ui.common.PIECE_MAX_STEPS
import app.fitfat.ui.common.gramIndex
import app.fitfat.ui.common.pieceIndex
import app.fitfat.ui.common.pieceOf
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1

@Composable
fun PortionDialog(item: Picked, onDismiss: () -> Unit, onSave: (Double, Double?) -> Unit) {
    val p = item.product
    var grams by remember { mutableDoubleStateOf(item.grams) }
    var pieces by remember { mutableDoubleStateOf(item.pieces ?: 1.0) }
    val m = p.portion(grams, if (p.perPiece) pieces else null)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { CategoryIcon(p.category, 40.dp) },
        title = { Text(p.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (p.perPiece) {
                    Drum(
                        count = PIECE_MAX_STEPS, index = pieceIndex(pieces),
                        onIndexChange = { pieces = pieceOf(it) },
                        label = { "${fmt1(pieceOf(it))} шт" },
                        description = "Порция",
                    )
                } else {
                    Drum(
                        count = GRAM_MAX / GRAM_STEP + 1, index = gramIndex(grams),
                        onIndexChange = { grams = (it * GRAM_STEP).toDouble() },
                        label = { "${it * GRAM_STEP} г" },
                        description = "Порция",
                    )
                }
                Text("${fmt0(m.kcal)} ккал · Б ${fmt1(m.protein)} · Ж ${fmt1(m.fat)} · У ${fmt1(m.carbs)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(if (p.perPiece) (p.pieceGrams ?: 0.0) * pieces else grams, if (p.perPiece) pieces else null) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Создание продукта. Калории считаются из БЖУ, если их не указать. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateProductDialog(initialName: String, onDismiss: () -> Unit, onCreate: (Product) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var cat by remember { mutableStateOf("grain") }
    var perPiece by remember { mutableStateOf(false) }
    var pieceG by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var u by remember { mutableStateOf("") }
    fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
    val calc = (num(p) ?: 0.0) * 4 + (num(f) ?: 0.0) * 9 + (num(u) ?: 0.0) * 4
    val valid = name.isNotBlank() && (num(p) != null || num(f) != null || num(u) != null || num(kcal) != null) && (!perPiece || num(pieceG) != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый продукт") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Categories.forEach { c ->
                        FilterChip(selected = cat == c.id, onClick = { cat = c.id }, label = { Text(c.label) }, leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp)) })
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !perPiece, onClick = { perPiece = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("На 100 г") }
                    SegmentedButton(selected = perPiece, onClick = { perPiece = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("На 1 шт") }
                }
                if (perPiece) NumField("Вес 1 шт, г", pieceG) { pieceG = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Белки", p, Modifier.weight(1f)) { p = it }
                    NumField("Жиры", f, Modifier.weight(1f)) { f = it }
                    NumField("Углев.", u, Modifier.weight(1f)) { u = it }
                }
                NumField("Ккал (если пусто — по БЖУ: ${fmt0(calc)})", kcal) { kcal = it }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onCreate(Product(
                    name = name.trim(), category = cat,
                    kcal = num(kcal) ?: calc, protein = num(p) ?: 0.0, fat = num(f) ?: 0.0, carbs = num(u) ?: 0.0,
                    perPiece = perPiece, pieceGrams = if (perPiece) num(pieceG) else null,
                    aliases = name.trim().lowercase(), defaultGrams = if (perPiece) 1.0 else 100.0,
                ))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun NumField(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
