package app.fitfat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.time.toEpochDay
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0

/** Карточка правок: ждёт решения, применена (можно вернуть), отменена или возвращена. */
enum class EditsState { Pending, Applied, Cancelled, Reverted }

/**
 * Правки дневника из ответа ассистента — в чате и в «Добавить» (0.6, 09.11.2026): строки «что поменяется» с разницей ккал, «Отмена» слева и «Применить» справа.
 * Ничего не меняется без нажатия; после — «Применено» и «Вернуть».
 */
@Composable
fun DiaryEditsCard(
    list: List<app.fitfat.data.DiaryEdits.Planned>, state: EditsState,
    onApply: () -> Unit, onCancel: () -> Unit, onRevert: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val today = app.fitfat.data.DayClock.today().toEpochDay()
    fun portion(e: app.fitfat.data.FoodEntry) = e.pieces?.let { "${app.fitfat.ui.common.fmt1(it)} шт" } ?: "${fmt0(e.grams)} г"
    fun meal(e: app.fitfat.data.FoodEntry) = app.fitfat.data.Meal.of(e.meal).label.lowercase()
    fun day(e: app.fitfat.data.FoodEntry) = if (e.epochDay == today) "сегодня" else "вчера"
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerLow).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Изменения в дневнике", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurfaceVariant)
        list.forEach { p ->
            val b = p.before; val a = p.after
            val (icon, sub, delta) = when (p.edit.op) {
                app.fitfat.data.Assistant.Edit.Op.Remove -> Triple(MockIcons.Delete, "Убрать · ${portion(b)} · ${meal(b)} ${day(b)}", -b.kcal)
                app.fitfat.data.Assistant.Edit.Op.Change -> {
                    val parts = buildList {
                        if (portion(b) != portion(a!!)) add("${portion(b)} → ${portion(a)}")
                        if (a.meal != b.meal) add("${meal(b)} → ${meal(a)}") else add(meal(b))
                    }
                    Triple(MockIcons.Edit, parts.joinToString(" · ") + " ${day(b)}", a!!.kcal - b.kcal)
                }
                app.fitfat.data.Assistant.Edit.Op.Copy -> Triple(MockIcons.Repeat, "Повторить сегодня · ${portion(a!!)} · ${meal(a)}", a.kcal)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(16.dp), tint = colors.onSurface)
                }
                Column(Modifier.weight(1f)) {
                    Text(b.name, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(sub, fontSize = 12.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant)
                }
                val d = kotlin.math.round(delta).toLong()
                if (d != 0L) Text((if (d > 0) "+" else "−") + "${fmt0(kotlin.math.abs(delta))} ккал", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (d > 0) colors.onSurface else colors.onSurfaceVariant)
            }
        }
        when (state) {
            EditsState.Pending -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Закрывающее — слева
                Surface(onClick = onCancel, shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHighest, modifier = Modifier.weight(1f).height(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("Отмена", fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
                Surface(onClick = onApply, shape = RoundedCornerShape(20.dp), color = colors.primary, modifier = Modifier.weight(1f).height(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("Применить", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary) }
                }
            }
            EditsState.Applied -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(MockIcons.Check, null, Modifier.size(16.dp), tint = colors.primary)
                Text("Применено", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.primary, modifier = Modifier.weight(1f))
                Surface(onClick = onRevert, shape = RoundedCornerShape(18.dp), color = colors.surfaceContainerHighest, modifier = Modifier.height(36.dp)) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("Вернуть", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            EditsState.Cancelled -> Text("Не применено", fontSize = 13.sp, color = colors.onSurfaceVariant)
            EditsState.Reverted -> Text("Вернул как было", fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
    }
}
