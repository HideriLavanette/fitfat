package app.fitfat.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import kotlin.math.abs

/**
 * Вкладка «Ассистент»: советы ассистента — в отдельной версии (DESIGN.md). Недельный отчёт — на вкладке «Вес» ([WeekCard]).
 */
@Composable
fun AssistantScreen() {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Ассистент", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
        Text("Советы по питанию появятся в следующих версиях.", fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

/** Вход в недельный отчёт («Неделя») с балансом за прошедшие дни — на вкладке «Вес». */
@Composable
fun WeekCard(days: List<WeekDay>, onWeek: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val past = days.dropLast(1).filter { it.entries.isNotEmpty() }
    val bal = past.sumOf { it.eaten.kcal - it.limit.kcal }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(colors.primaryContainer).clickable(onClick = onWeek).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(colors.onPrimaryContainer.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(MockIcons.Calendar, null, Modifier.size(24.dp), tint = colors.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Неделя", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer)
            Text(
                if (past.isEmpty()) "Отчёт за 7 дней: баланс, вес, средние БЖУ"
                else "Баланс за ${past.size} дн.: ${if (bal > 0) "+" else "−"}${fmt0(abs(bal))} ккал",
                fontSize = 14.sp, color = colors.onPrimaryContainer,
            )
        }
        Icon(MockIcons.ChevronRight, null, Modifier.size(20.dp), tint = colors.onPrimaryContainer)
    }
}
