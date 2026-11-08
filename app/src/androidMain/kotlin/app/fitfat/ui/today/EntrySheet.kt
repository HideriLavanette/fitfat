package app.fitfat.ui.today

import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.FoodEntry
import app.fitfat.data.Meal
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmtF1
import app.fitfat.ui.components.FfSheet
import app.fitfat.ui.components.GramRuler
import app.fitfat.ui.components.GramsReadout
import app.fitfat.ui.components.PieceStepper
import app.fitfat.ui.theme.MacroColors
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Шторка записи (макет 2.4): правка порции либо повтор. Граммы — линейка, штуки — «−/+».
 * Под порцией — чипы ккал · Б · Ж · У; в режиме правки — плитки «Удалить» / «Повторить».
 */
@Composable
fun EntrySheet(
    entry: FoodEntry,
    repeatMode: Boolean,
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (grams: Double, pieces: Double?) -> Unit,
    onDelete: () -> Unit,
    onSwitchToRepeat: () -> Unit,
    /** Карточка продукта; null — продукта уже нет в базе. */
    onCard: (() -> Unit)? = null,
) {
    val piece = entry.pieces != null
    var grams by remember(entry.id) { mutableDoubleStateOf(entry.grams) }
    var pieces by remember(entry.id) { mutableDoubleStateOf(entry.pieces ?: 1.0) }
    val k = if (piece) pieces / (entry.pieces ?: 1.0) else if (entry.grams > 0) grams / entry.grams else 1.0
    val perPieceGrams = if (piece && (entry.pieces ?: 0.0) > 0) entry.grams / entry.pieces!! else 0.0
    val colors = MaterialTheme.colorScheme
    val time = Fmt.hhmm(entry.minute / 60, entry.minute % 60)
    val sub = when {
        !repeatMode -> "${Meal.of(entry.meal).label} · $time · запись в дневнике"
        date == app.fitfat.data.DayClock.today() -> LocalTime.now().let { "Повтор · запишется сейчас, ${Fmt.hhmm(it.hour, it.minute)}" }
        else -> "Повтор · запишется на $time"
    }

    FfSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryIcon(entry.category, 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, fontSize = 19.sp, fontWeight = FontWeight(650))
                Text(sub, fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
        }
        if (piece) {
            PieceStepper(pieces, { pieces = it }, perPieceGrams)
        } else {
            GramsReadout(grams.toInt())
            GramRuler(grams.toInt(), { grams = it.toDouble() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MacroChip(fmt0(entry.kcal * k), "ккал", colors.onSurface, colors.surfaceContainerHigh, 1.4f, "Калории")
            MacroChip(fmtF1(entry.protein * k), "г", MacroColors.protein, MacroColors.proteinContainer, 1f, "Белки")
            MacroChip(fmtF1(entry.fat * k), "г", MacroColors.fat, MacroColors.fatContainer, 1f, "Жиры")
            MacroChip(fmtF1(entry.carbs * k), "г", MacroColors.carbs, MacroColors.carbsContainer, 1f, "Углеводы")
        }
        if (!repeatMode) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(MockIcons.Delete, "Удалить", colors.error, colors.error, onDelete)
                Tile(MockIcons.Repeat, "Повторить", colors.onSurface, colors.onSurfaceVariant, onSwitchToRepeat)
                if (onCard != null) Tile(MockIcons.Card, "Карточка", colors.onSurface, colors.onSurfaceVariant, onCard)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = onDismiss, shape = RoundedCornerShape(28.dp), color = Color.Transparent, modifier = Modifier.weight(1f).height(56.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Отмена", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.primary) }
            }
            Surface(
                onClick = { onSave(if (piece) perPieceGrams * pieces else grams, if (piece) pieces else null) },
                shape = RoundedCornerShape(28.dp), color = colors.primary, modifier = Modifier.weight(1.5f).height(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (repeatMode) "Добавить ещё раз" else "Сохранить", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary)
                }
            }
        }
    }
}

/** Чип значения: высота 36, скругление 12, число 15 / 600 + единица 11 / 500 с прозрачностью 75 %. */
@Composable
private fun RowScope.MacroChip(value: String, unit: String, ink: Color, bg: Color, weight: Float, label: String) {
    Row(
        Modifier.weight(weight).height(36.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label $value $unit" },
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
        Text(unit, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ink.copy(alpha = 0.75f), maxLines = 1)
    }
}

/** Плитка действия: 64 dp, скругление 18, `surfaceContainerLow`, иконка 20 над подписью 13 / 600. */
@Composable
private fun RowScope.Tile(icon: ImageVector, label: String, ink: Color, iconInk: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.weight(1f).height(64.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            Icon(icon, null, Modifier.size(20.dp), tint = iconInk)
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ink)
        }
    }
}
