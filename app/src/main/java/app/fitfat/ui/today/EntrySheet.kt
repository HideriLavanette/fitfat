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
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import app.fitfat.data.FoodEntry
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

/** Шторка записи: правка порции либо повтор. Порция — барабан: штуки шагом 0,5 или граммы шагом 5. */
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
                Drum(
                    count = PIECE_MAX_STEPS, index = pieceIndex(pieces),
                    onIndexChange = { pieces = pieceOf(it) },
                    label = { "${fmt1(pieceOf(it))} шт" },
                    description = "Порция",
                )
                Text("≈ ${fmt0(perPieceGrams * pieces)} г", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Drum(
                    count = GRAM_MAX / GRAM_STEP + 1, index = gramIndex(grams),
                    onIndexChange = { grams = (it * GRAM_STEP).toDouble() },
                    label = { "${it * GRAM_STEP} г" },
                    description = "Порция",
                )
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
