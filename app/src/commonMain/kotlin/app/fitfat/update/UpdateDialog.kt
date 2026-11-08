package app.fitfat.update

import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun UpdateDialog(vm: UpdateViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        UpdateState.Idle -> Unit
        is UpdateState.Checking -> Unit // проверка идёт тихо; кнопка в профиле может показать индикатор

        UpdateState.UpToDate -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Обновлений нет") },
            text = { Text("У вас последняя версия — ${app.fitfat.platform.Platform.versionName}.") },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text("Хорошо") } },
        )

        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Не получилось") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = { vm.check(silent = false) }) { Text("Повторить") } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Закрыть") } },
        )

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = vm::dismiss,
            icon = { Icon(app.fitfat.ui.common.MockIcons.ArrowUp, contentDescription = null) },
            title = { Text("Доступна версия ${s.update.versionName}") },
            text = { ReleaseNotes(s.update) },
            confirmButton = { Button(onClick = { vm.download(s.update) }) { Text("Обновить · ${formatSize(s.update.sizeBytes)}") } },
            dismissButton = { TextButton(onClick = { vm.skip(s.update) }) { Text("Позже") } },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Загрузка ${s.update.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(s.progress * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Отмена") } },
        )

        is UpdateState.Ready -> {
            val allowed = vm.canInstall()
            AlertDialog(
                onDismissRequest = vm::dismiss,
                title = { Text("Готово к установке") },
                text = {
                    Text(
                        if (allowed) "Версия ${s.update.versionName} скачана. Данные приложения сохранятся."
                        else "Разрешите приложению устанавливать обновления — это нужно один раз. Затем вернитесь и нажмите «Установить»."
                    )
                },
                confirmButton = {
                    if (allowed) Button(onClick = { vm.install(s.file) }) { Text("Установить") }
                    else Button(onClick = vm::openInstallPermission) { Text("Разрешить") }
                },
                dismissButton = { TextButton(onClick = vm::dismiss) { Text("Позже") } },
            )
        }
    }
}

@Composable
private fun ReleaseNotes(update: AppUpdate) {
    Column(
        Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (update.prerelease) Text("Бета-версия", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Text(update.notes.ifBlank { "Без описания изменений." }, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes <= 0) "" else Fmt.f1(bytes / 1_048_576.0) + " МБ"
