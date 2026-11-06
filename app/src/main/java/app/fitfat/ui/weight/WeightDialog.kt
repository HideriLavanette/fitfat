package app.fitfat.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))

/**
 * Окно ввода веса с колесом. [previous] — вчерашнее значение для подсказки.
 * Для правки старой записи передаётся её дата и показывается кнопка «Удалить».
 */
@Composable
fun WeightDialog(
    date: LocalDate,
    initialKg: Double,
    previous: Double?,
    title: String,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Пропустить",
    onDelete: (() -> Unit)? = null,
) {
    var kg by remember { mutableDoubleStateOf(initialKg) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(date.format(dateFmt).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                }
                val caption = previous?.let {
                    val d = kg - it
                    val sign = if (d > 0.04) "+" else if (d < -0.04) "−" else "±"
                    "вчера ${formatKg(it)} · $sign${formatKg(kotlin.math.abs(d))}"
                } ?: "крутите кольцо"
                WeightDial(valueKg = kg, onValueChange = { kg = it }, caption = caption)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete, modifier = Modifier.height(56.dp)) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(56.dp)) { Text(dismissLabel) }
                    Button(onClick = { onSave(kg) }, modifier = Modifier.weight(1.4f).height(56.dp)) { Text("Сохранить") }
                }
            }
        }
    }
}
