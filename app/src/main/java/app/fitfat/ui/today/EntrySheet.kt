package app.fitfat.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.FoodEntry
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import kotlin.math.roundToInt

/** Шторка записи: правка порции либо повтор. Для штучных — шаг 1 шт, иначе граммы. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(
    entry: FoodEntry,
    repeatMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (grams: Double, pieces: Double?) -> Unit,
    onDelete: () -> Unit,
    onSwitchToRepeat: () -> Unit,
) {
    val piece = entry.pieces != null
    var grams by remember(entry.id) { mutableDoubleStateOf(entry.grams) }
    var pieces by remember(entry.id) { mutableDoubleStateOf(entry.pieces ?: 1.0) }
    val k = if (piece) pieces / (entry.pieces ?: 1.0) else if (entry.grams > 0) grams / entry.grams else 1.0
    val perPieceGrams = if (piece && (entry.pieces ?: 0.0) > 0) entry.grams / entry.pieces!! else 0.0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(entry.category, 48.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleLarge)
                    Text(if (repeatMode) "Повтор · запишется сейчас" else "Запись в дневнике", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (piece) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalIconButton(onClick = { pieces = (pieces - 1).coerceAtLeast(1.0) }) { Icon(Icons.Outlined.Remove, "Меньше") }
                    Text("${fmt1(pieces)} шт", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("≈ ${fmt0(perPieceGrams * pieces)} г", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalIconButton(onClick = { pieces += 1 }) { Icon(Icons.Outlined.Add, "Больше") }
                }
            } else {
                Text("${fmt0(grams)} г", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Slider(value = grams.toFloat(), onValueChange = { grams = ((it / 5f).roundToInt() * 5).toDouble() }, valueRange = 0f..maxOf(600f, (entry.grams * 2).toFloat()))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${fmt0(entry.kcal * k)} ккал", fontWeight = FontWeight.SemiBold)
                Text("Б ${fmt1(entry.protein * k)} · Ж ${fmt1(entry.fat * k)} · У ${fmt1(entry.carbs * k)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!repeatMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error); Text("  Удалить", color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = onSwitchToRepeat, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Repeat, null); Text("  Повторить") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(56.dp)) { Text("Отмена") }
                Button(
                    onClick = { onSave(if (piece) perPieceGrams * pieces else grams, if (piece) pieces else null) },
                    modifier = Modifier.weight(1.5f).height(56.dp), shape = RoundedCornerShape(28.dp),
                ) { Text(if (repeatMode) "Добавить ещё раз" else "Сохранить") }
            }
        }
    }
}
