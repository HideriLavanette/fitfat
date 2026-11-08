package app.fitfat.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.WEIGHT_COOKED
import app.fitfat.data.WeightState
import app.fitfat.ui.components.PillToggle

/**
 * Сухой / сырой / готовый вес (DESIGN.md, «Сухой и готовый вес»): плашка-метка 24 и пояснение 12 sp
 * на `surfaceContainerLow`, скругление 18. У готового метка — `tertiaryContainer`: напоминает, что точнее считать до готовки.
 */
@Composable
fun WeightNote(label: String, hint: String, warn: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val bg = if (warn) colors.tertiaryContainer else colors.secondaryContainer
        val ink = if (warn) colors.onTertiaryContainer else colors.onSecondaryContainer
        // Высота по тексту (не меньше 24): при крупном шрифте в настройках телефона буквы не упираются в край
        Box(Modifier.heightIn(min = 24.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink)
        }
        Text(hint, fontSize = 12.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant)
    }
}

/** Пояснение в карточке продукта; без отметки — ничего. */
@Composable
fun WeightNote(state: String?, modifier: Modifier = Modifier) {
    val label = WeightState.label(state) ?: return
    WeightNote(label, WeightState.hint(state) ?: return, warn = state == WEIGHT_COOKED, modifier)
}

/** Выбор в правке продукта: «Не важно · Сухой · Сырой · Готовый» и пояснение к выбранному. */
@Composable
fun WeightStatePicker(state: String?, onChange: (String?) -> Unit) {
    val options = listOf(null) + WeightState.all
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("КБЖУ указаны на вес", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PillToggle(
            options.map { WeightState.word(it)?.replaceFirstChar(Char::uppercase) ?: "Не важно" },
            options.indexOf(state).coerceAtLeast(0), onSelect = { onChange(options[it]) }, height = 40.dp, fontSize = 13.sp,
        )
        Text(
            WeightState.hint(state) ?: "Для хлеба, молочного, фруктов и готовых покупных продуктов — вес как есть.",
            fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
