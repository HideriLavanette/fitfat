package app.fitfat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fitfat.BuildConfig
import app.fitfat.update.UpdateViewModel

@Composable
fun PlaceholderScreen(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Экран переносится из макета на следующем этапе.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Начало дня: часы 0–12 и минуты шагом 15 барабанами; «Отмена» слева, «Готово» справа. */
@Composable
internal fun DayStartSheet(start: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var h by remember { mutableStateOf((start / 60).toDouble()) }
    var m by remember { mutableStateOf((start % 60 / 15 * 15).toDouble()) }
    val total = (h.toInt() * 60 + m.toInt()).coerceAtMost(12 * 60)
    app.fitfat.ui.components.FfSheet(onDismiss = onDismiss) {
        Text("Начало дня", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Text(
            "До ${app.fitfat.data.DayClock.label(total)} продолжается текущий день: перекус в 2 часа ночи запишется в него. В ${app.fitfat.data.DayClock.label(total)} «Сегодня» переключается на новую дату. Вес всегда записывается на календарный день.",
            fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.fitfat.ui.components.VerticalDrum(h, { h = it; if (it >= 12.0) m = 0.0 }, 0.0, 12.0, 1.0, Modifier.weight(1f), label = "Часы")
            app.fitfat.ui.components.VerticalDrum(m, { m = if (h >= 12.0) 0.0 else it }, 0.0, 45.0, 15.0, Modifier.weight(1f), label = "Минуты")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("Отмена") }
            Button(onClick = { onSave(total) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Готово") }
        }
    }
}
