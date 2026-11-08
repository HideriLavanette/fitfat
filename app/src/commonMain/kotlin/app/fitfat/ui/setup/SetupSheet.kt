package app.fitfat.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fitfat.data.Macros
import app.fitfat.data.Profile
import app.fitfat.ui.components.FfSheet

/**
 * Быстрая правка одного шага настройки в шторке (решение автора 08.11.2026): тап по пункту «Тело и цель» открывает
 * сразу нужный шаг — 1 о себе, 2 процент жира, 3 спорт, 4 цель, 5 нормы и белок — без перелистывания остальных.
 * «Отмена» слева, «Сохранить» справа; сохранение — как у правки по шагам.
 */
@Composable
fun SetupSheet(step: Int, initial: Profile, edit: SetupEdit, onDismiss: () -> Unit, onSave: (SetupResult) -> Unit) {
    var p by remember { mutableStateOf(initial) }
    val custom = remember { mutableStateMapOf<String, Macros>().apply { putAll(edit.custom) } }
    var cuts by remember { mutableIntStateOf(edit.cuts) }
    FfSheet(onDismiss = onDismiss) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            when (step) {
                1 -> AboutStep(p) { p = it }
                2 -> FatStep(p) { p = it }
                3 -> SportStep(p) { p = it }
                4 -> GoalStep(p, edit) { p = it }
                else -> NormsStep(p, custom, cuts, true, onUncut = { cuts = 0 }) { p = it }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp)) { Text("Отмена") }
            Button(onClick = { onSave(SetupResult(p, custom.toMap(), cuts)) }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Сохранить") }
        }
    }
}
