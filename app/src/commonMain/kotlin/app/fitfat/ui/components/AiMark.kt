package app.fitfat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.theme.MacroColors

/**
 * Метка оценки ассистента (DESIGN.md, «Оценки ИИ»): жёлтая плашка 18 со значком предупреждения — рядом с названием
 * в дневнике, «Что съели», списке продуктов и карточке ответа ассистента.
 */
@Composable
fun AiMark(modifier: Modifier = Modifier) {
    Box(
        modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(MacroColors.aiContainer)
            .semantics { contentDescription = "Оценка ассистента, цифры примерные" },
        contentAlignment = Alignment.Center,
    ) { Icon(MockIcons.Warn, null, Modifier.size(12.dp), tint = MacroColors.ai) }
}

/** Пояснение в карточке продукта: цифры примерные, весь рацион на ИИ строить не стоит. */
@Composable
fun AiNote(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MacroColors.aiContainer).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(MockIcons.Warn, null, Modifier.size(18.dp), tint = MacroColors.ai)
        Text(
            "Оценка ассистента — цифры примерные и могут заметно отличаться. Не стоит строить на ИИ весь рацион: " +
                "при возможности уточните КБЖУ по рецепту или этикетке.",
            fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = MacroColors.ai,
        )
    }
}
