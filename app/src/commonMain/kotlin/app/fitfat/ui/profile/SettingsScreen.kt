package app.fitfat.ui.profile

import app.fitfat.data.GoogleAccount
import app.fitfat.data.Reports
import app.fitfat.data.CloudBackup
import app.fitfat.data.cloudWhen
import app.fitfat.platform.restartApp
import app.fitfat.ui.components.rememberTextFileOpener

import app.fitfat.ui.components.rememberTextFileSaver

import app.fitfat.ui.components.rememberNotificationPermission

import app.fitfat.data.AppGraph

import app.fitfat.platform.Platform
import app.fitfat.platform.ioDispatcher

import app.fitfat.time.*
import app.fitfat.time.Fmt
import app.fitfat.data.Hints
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.AppPalette
import app.fitfat.data.AppSettings
import app.fitfat.data.Backup
import app.fitfat.data.DayClock
import app.fitfat.data.Schedule
import app.fitfat.data.ThemeMode
import app.fitfat.notify.Reminders
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.components.FfSheet
import app.fitfat.ui.components.PillToggle
import app.fitfat.ui.components.VerticalDrum
import app.fitfat.ui.screens.DayStartSheet
import app.fitfat.update.UpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/**
 * «Настройки приложения» (макет Settings, 5.5): оформление, уведомления, колёса, дневник, данные, обновления.
 * Шесть цветов приложения из макета — позже (DESIGN.md), сейчас тема и цвета системы.
 */
@Composable
fun SettingsScreen(
    updates: UpdateViewModel,
    groupByTime: Boolean, onGroupByTime: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val app = AppGraph.get()
    val scope = rememberCoroutineScope()
    val theme by AppSettings.theme.collectAsState()
    val dynamic by AppSettings.dynamic.collectAsState()
    val palette by AppSettings.palette.collectAsState()
    val haptic by AppSettings.haptic.collectAsState()
    val remind by AppSettings.remind.collectAsState()
    val report by AppSettings.report.collectAsState()
    val dayStart by DayClock.start.collectAsState()
    var editing by remember { mutableStateOf<String?>(null) }
    var editDayStart by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var importText by remember { mutableStateOf<String?>(null) }
    var cloudBusy by remember { mutableStateOf(false) }
    /** Восстановить копию из Диска, сохранённую в это время (мс); null — окна нет. */
    var restoreFrom by remember { mutableStateOf<Long?>(null) }
    var restarting by remember { mutableStateOf(false) }
    var feedbackOpen by remember { mutableStateOf(false) }

    // Уведомления — с разрешения (Android 13+, iOS); просим, когда включают напоминание
    val needPermission = rememberNotificationPermission { message = "Без разрешения на уведомления напоминания не придут. Его можно дать в настройках телефона." }
    fun saveRemind(s: Schedule) { AppSettings.setRemind(s); Reminders.schedule(); if (s.on) needPermission() }
    fun saveReport(s: Schedule) { AppSettings.setReport(s); Reminders.schedule(); if (s.on) needPermission() }

    val exportJson = rememberTextFileSaver("application/json") { ok -> message = if (ok) "Данные сохранены в файл." else "Не получилось сохранить файл." }
    val exportCsv = rememberTextFileSaver("text/csv") { ok -> message = if (ok) "Дневник сохранён в CSV." else "Не получилось сохранить файл." }
    val importFile = rememberTextFileOpener { text ->
        if (text == null || Backup.peek(text) == null) message = "Это не файл FitFat. Нужен JSON, сохранённый «Экспортом»."
        else importText = text
    }

    Column(
        Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header("Настройки", onBack)

        Group("Оформление") {
            if (Platform.supportsDynamicColor) {
                SwitchRow(MockIcons.Sun, "Цвета системы", "Цвета приложения из обоев телефона", dynamic) { AppSettings.setDynamic(it) }
            }
            // Тема и цвет приложения — когда цвета не берутся из обоев (макет: блок «manual»)
            androidx.compose.animation.AnimatedVisibility(!dynamic || !Platform.supportsDynamicColor) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Тема", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PillToggle(ThemeMode.entries.map { it.label }, theme.ordinal, { AppSettings.setTheme(ThemeMode.entries[it]) }, Modifier.fillMaxWidth(), height = 40.dp)
                    Text("Цвет приложения", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PaletteRow(palette) { AppSettings.setPalette(it) }
                }
            }
        }

        Group("Уведомления") {
            SwitchRow(
                MockIcons.NavWeight, "Напоминание о весе", if (remind.on) AppSettings.remindText(remind).replaceFirstChar { it.uppercase() } else "Выключено",
                remind.on, onClick = { editing = "remind" },
            ) { saveRemind(remind.copy(on = it)) }
            SwitchRow(
                MockIcons.Calendar, "Недельный отчёт", if (report.on) AppSettings.reportText(report).replaceFirstChar { it.uppercase() } else "Выключено",
                report.on, onClick = { editing = "report" },
            ) { saveReport(report.copy(on = it)) }
        }

        Group("Ввод") {
            SwitchRow(MockIcons.Repeat, "Звук и вибрация колеса", "Щелчки при кручении барабанов и линеек", haptic) { AppSettings.setHaptic(it) }
            NavRow(MockIcons.Bulb, "Показать подсказки заново", "Палец на барабанах и подтягивание строк со свайпом") {
                Hints.get().reset(); message = "Подсказки снова появятся на своих экранах."
            }
        }

        Group("Дневник") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Дневник по умолчанию", fontSize = 15.sp)
                PillToggle(listOf("Приёмы", "Время"), if (groupByTime) 1 else 0, { onGroupByTime(it == 1) }, Modifier.fillMaxWidth(), height = 40.dp)
            }
            NavRow(MockIcons.Clock, "Новый день начинается в ${DayClock.label(dayStart)}", "До этого часа — прошлый день") { editDayStart = true }
        }

        // Копия в Google Диске пользователя (как у WhatsApp): без своего сервера, фото остаются на телефоне
        if (Platform.supportsGoogleSignIn) Group("Копия в Google Диске") {
            val account by GoogleAccount.get().account.collectAsState()
            val last by CloudBackup.lastSaved.collectAsState()
            val auto by CloudBackup.auto.collectAsState()
            if (account == null) {
                Text(
                    "Войдите через Google в «Профиле» — тогда дневник, вес, база продуктов и нормы будут сохраняться в ваш Google Диск, и их можно будет вернуть на новом телефоне.",
                    fontSize = 13.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                SwitchRow(MockIcons.Cloud, "Сохранять автоматически", "Раз в день, когда открываете приложение", auto) { CloudBackup.setAuto(it) }
                NavRow(MockIcons.Cloud, if (cloudBusy) "Сохраняю…" else "Сохранить сейчас",
                    if (last > 0) "Последняя копия: ${cloudWhen(last)}" else "С этого телефона ещё не сохраняли") {
                    if (cloudBusy) return@NavRow
                    cloudBusy = true
                    scope.launch {
                        message = when (val r = CloudBackup.save(interactive = true)) {
                            CloudBackup.Result.Ok -> "Копия сохранена в Google Диск."
                            is CloudBackup.Result.Error -> r.message
                        }
                        cloudBusy = false
                    }
                }
                NavRow(MockIcons.Repeat, "Восстановить из копии", "Заменит данные на телефоне") {
                    if (cloudBusy) return@NavRow
                    cloudBusy = true
                    scope.launch {
                        runCatching { CloudBackup.remote(interactive = true) }
                            .onSuccess { r -> if (r == null) message = "В Google Диске ещё нет копии." else restoreFrom = r.savedAt }
                            .onFailure { message = it.message ?: "Нет связи с Google Диском." }
                        cloudBusy = false
                    }
                }
            }
        }

        Group("Данные") {
            NavRow(MockIcons.ChevronRight, "Экспорт в JSON", "Все данные и настройки") { exportJson("fitfat-${LocalDate.now()}.json") { Backup.exportJson(app.database) } }
            NavRow(MockIcons.ChevronRight, "Экспорт дневника в CSV", "Дневник для таблиц") { exportCsv("fitfat-diary-${LocalDate.now()}.csv") { "\uFEFF" + Backup.exportCsv(app.database) } }
            NavRow(MockIcons.Repeat, "Импорт данных", "Заменит текущие данные") { importFile() }
        }

        // Отчёты разработчику (DESIGN.md, «Отчёты о сбоях»)
        if (Reports.available) Group("Помощь") {
            val crashOn by Reports.crashes.collectAsState()
            NavRow(MockIcons.Edit, "Сообщить о проблеме", "Письмо разработчику") { feedbackOpen = true }
            SwitchRow(MockIcons.Repeat, "Отправлять отчёты о сбоях", "Только текст ошибки и модель телефона", crashOn) { Reports.setCrashes(it) }
        }

        if (Platform.supportsSelfUpdate) Group("Обновления") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Версия ${Platform.versionName} (${Platform.versionCode})", fontSize = 14.sp, color = colors.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Бета-версии", fontSize = 15.sp)
                        Text("Получать тестовые сборки раньше остальных", fontSize = 13.sp, color = colors.onSurfaceVariant)
                    }
                    Switch(checked = updates.betaChannel, onCheckedChange = updates::setBeta)
                }
                Button(onClick = { updates.check(silent = false) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Проверить обновления") }
            }
        }
    }

    when (editing) {
        "remind" -> ScheduleSheet(
            "Напоминание о весе", remind, single = false,
            preview = { AppSettings.remindText(it) }, onDismiss = { editing = null },
        ) { saveRemind(it.copy(on = true)); editing = null }
        "report" -> ScheduleSheet(
            "Недельный отчёт", report, single = true,
            preview = { AppSettings.reportText(it) }, onDismiss = { editing = null },
        ) { saveReport(it.copy(on = true)); editing = null }
    }

    if (editDayStart) DayStartSheet(dayStart, onDismiss = { editDayStart = false }) { DayClock.setStart(it); editDayStart = false }

    importText?.let { text ->
        val n = Backup.peek(text).orEmpty()
        AlertDialog(
            onDismissRequest = { importText = null },
            title = { Text("Заменить данные?") },
            text = {
                Text(
                    "Из файла: ${n["food_entry"] ?: 0} записей дневника, ${n["product"] ?: 0} продуктов и блюд, ${n["weight"] ?: 0} взвешиваний, " +
                        "${n["training"] ?: 0} тренировок. Текущие данные заменятся, отменить это нельзя. Сначала можно сделать экспорт.",
                )
            },
            dismissButton = { TextButton(onClick = { importText = null }) { Text("Отмена") } },
            confirmButton = {
                TextButton(onClick = {
                    importText = null
                    scope.launch {
                        val ok = withContext(ioDispatcher) { runCatching { Backup.importJson(app.database, text) }.isSuccess }
                        if (ok) restarting = true else message = "Файл повреждён — данные не изменились."
                    }
                }) { Text("Заменить", color = colors.error) }
            },
        )
    }
    restoreFrom?.let { at ->
        AlertDialog(
            onDismissRequest = { restoreFrom = null },
            title = { Text("Восстановить копию?") },
            text = { Text("Копия из Google Диска от ${cloudWhen(at)} заменит дневник, вес, базу продуктов и нормы на этом телефоне. Отменить это нельзя. Фото продуктов останутся как есть.") },
            dismissButton = { TextButton(onClick = { restoreFrom = null }) { Text("Отмена") } },
            confirmButton = {
                TextButton(onClick = {
                    restoreFrom = null; cloudBusy = true
                    scope.launch {
                        when (val r = CloudBackup.restore(interactive = true)) {
                            CloudBackup.Result.Ok -> restarting = true
                            is CloudBackup.Result.Error -> message = r.message
                        }
                        cloudBusy = false
                    }
                }) { Text("Восстановить", color = colors.error) }
            },
        )
    }
    // После восстановления настройки и экраны читаются заново — проще всего перезапуском
    if (restarting) AlertDialog(
        onDismissRequest = {},
        text = { Text("Данные восстановлены. Приложение перезапустится, чтобы всё подхватить.") },
        confirmButton = { TextButton(onClick = { restartApp() }) { Text("Перезапустить") } },
    )
    if (feedbackOpen) FeedbackSheet(onDismiss = { feedbackOpen = false }) { ok ->
        feedbackOpen = false
        message = if (ok) "Спасибо! Сообщение отправлено разработчику." else "Не получилось отправить — проверьте интернет и попробуйте ещё раз."
    }
    message?.let { m ->
        AlertDialog(onDismissRequest = { message = null }, text = { Text(m) }, confirmButton = { TextButton(onClick = { message = null }) { Text("Понятно") } })
    }
}

@Composable
internal fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(onClick = onBack, shape = RoundedCornerShape(24.dp), color = Color.Transparent, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(MockIcons.ChevronLeft, "Назад", Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface) }
        }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), content = content)
        }
    }
}

@Composable
private fun IconBox(icon: ImageVector) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Строка с переключателем; [onClick] — тап по тексту (настроить), иначе тап переключает. */
@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String, on: Boolean, onClick: (() -> Unit)? = null, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { if (onClick != null) onClick() else onToggle(!on) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBox(icon)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onToggle)
    }
}

@Composable
internal fun NavRow(icon: ImageVector, title: String, sub: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBox(icon)
        // Название и подпись — по строке, плотный интервал: при крупном шрифте строка не раздувается (09.11.2026)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 13.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(MockIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
    }
}

private val DOW = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/**
 * Шторка расписания: дни недели кружками (для отчёта — один день), пресеты «Каждый день / По будням / По выходным»,
 * время барабанами (минуты шагом 5), строка-превью. «Отмена» слева, «Сохранить» справа.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleSheet(title: String, initial: Schedule, single: Boolean, preview: (Schedule) -> String, onDismiss: () -> Unit, onSave: (Schedule) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var days by remember { mutableStateOf(initial.days) }
    var h by remember { mutableStateOf((initial.minute / 60).toDouble()) }
    var m by remember { mutableStateOf((initial.minute % 60 / 5 * 5).toDouble()) }
    val s = Schedule(true, days, h.toInt() * 60 + m.toInt())
    FfSheet(onDismiss = onDismiss) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DOW.forEachIndexed { i, d ->
                val sel = i in days
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(if (sel) colors.primary else colors.surfaceContainerHighest)
                        .clickable { days = if (single) setOf(i) else if (sel) days - i else days + i },
                    contentAlignment = Alignment.Center,
                ) { Text(d, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (sel) colors.onPrimary else colors.onSurface) }
            }
        }
        if (!single) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Каждый день" to (0..6).toSet(), "По будням" to (0..4).toSet(), "По выходным" to setOf(5, 6)).forEach { (label, set) ->
                    val sel = days == set
                    Box(
                        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(if (sel) colors.secondaryContainer else Color.Transparent)
                            .then(if (sel) Modifier else Modifier.background(colors.surfaceContainerHigh))
                            .clickable { days = set }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (sel) colors.onSecondaryContainer else colors.onSurface) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VerticalDrum(h, { h = it }, 0.0, 23.0, 1.0, Modifier.weight(1f), label = "Часы", height = 150, hint = true)
            VerticalDrum(m, { m = it }, 0.0, 55.0, 5.0, Modifier.weight(1f), label = "Минуты", height = 150)
        }
        Text(
            if (days.isEmpty()) "Выберите хотя бы один день" else "Придёт ${preview(s)}",
            fontSize = 14.sp, color = if (days.isEmpty()) colors.error else colors.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("Отмена") }
            Button(onClick = { onSave(s) }, enabled = days.isNotEmpty(), modifier = Modifier.weight(1f).height(48.dp)) { Text("Сохранить") }
        }
        Spacer(Modifier.width(0.dp))
    }
}

/**
 * Цвет приложения (макет Settings): круги 44, выбранный — скругление 14 с галочкой; «Графит» — образец из двух
 * половин (сине-серый и красный акцент). Цвета образцов — из [AppPalette].
 */
@Composable
private fun PaletteRow(selected: AppPalette, onPick: (AppPalette) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AppPalette.entries.forEach { p ->
            val sel = p == selected
            val r by androidx.compose.animation.core.animateDpAsState(if (sel) 14.dp else 22.dp, label = "swatch")
            val base = Color(p.swatch)
            val fill = p.accent?.let { a ->
                androidx.compose.ui.graphics.Brush.linearGradient(0f to base, 0.5f to base, 0.5f to Color(a), 1f to Color(a))
            } ?: androidx.compose.ui.graphics.SolidColor(base)
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(r)).background(fill)
                    .clickable { onPick(p) }
                    .swatchSemantics(p.label, sel),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(sel, enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()) {
                    Icon(MockIcons.Check, null, Modifier.size(20.dp), tint = Color(p.ink))
                }
            }
        }
    }
}

private fun Modifier.swatchSemantics(label: String, sel: Boolean) = this.then(
    Modifier.semantics { contentDescription = label + if (sel) ", выбран" else "" },
)

/**
 * «Сообщить о проблеме»: что случилось (поле), «Приложить журнал» (последние переходы, без данных дневника) — включено,
 * «Отмена» слева, «Отправить» справа. Модель телефона и версия приложения добавляются сами.
 */
@Composable
private fun FeedbackSheet(onDismiss: () -> Unit, onSent: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var withLog by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    app.fitfat.ui.components.FfSheet(onDismiss = onDismiss) {
        Text("Сообщить о проблеме", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Опишите, что случилось и что вы делали перед этим. Модель телефона и версия приложения добавятся сами.", fontSize = 13.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant)
        androidx.compose.foundation.text.BasicTextField(
            text, { text = it.take(4000) },
            textStyle = androidx.compose.ui.text.TextStyle(color = colors.onSurface, fontSize = 16.sp, lineHeight = 22.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHighest).padding(14.dp),
            decorationBox = { inner -> Box { if (text.isEmpty()) Text("Например: после добавления блюда пропала запись", fontSize = 16.sp, color = colors.onSurfaceVariant); inner() } },
        )
        Row(Modifier.fillMaxWidth().clickable { withLog = !withLog }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Приложить журнал", fontSize = 15.sp)
                Text("Какие экраны открывали — без вашего дневника", fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
            Switch(checked = withLog, onCheckedChange = { withLog = it })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("Отмена") }
            Button(
                onClick = { sending = true; scope.launch { val ok = Reports.feedback(text.trim(), withLog); sending = false; onSent(ok) } },
                enabled = text.isNotBlank() && !sending, modifier = Modifier.weight(1f).height(48.dp),
            ) { Text(if (sending) "Отправляю…" else "Отправить") }
        }
    }
}
