package app.fitfat.ui.profile

import app.fitfat.time.*
import app.fitfat.time.Fmt
import app.fitfat.data.Hints
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.BuildConfig
import app.fitfat.FitfatApp
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
    val ctx = LocalContext.current
    val app = ctx.applicationContext as FitfatApp
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

    // Уведомления на Android 13+ — с разрешения; просим, когда включают напоминание
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) message = "Без разрешения на уведомления напоминания не придут. Его можно дать в настройках телефона."
    }
    fun needPermission() { if (Build.VERSION.SDK_INT >= 33 && !Reminders.canPost(ctx)) ask.launch(Manifest.permission.POST_NOTIFICATIONS) }
    fun saveRemind(s: Schedule) { AppSettings.setRemind(s); Reminders.schedule(ctx); if (s.on) needPermission() }
    fun saveReport(s: Schedule) { AppSettings.setReport(s); Reminders.schedule(ctx); if (s.on) needPermission() }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Backup.exportJson(app.database).toByteArray()) } }.isSuccess
            }
            message = if (ok) "Данные сохранены в файл." else "Не получилось сохранить файл."
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                // BOM — чтобы Excel узнал UTF-8
                runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(("﻿" + Backup.exportCsv(app.database)).toByteArray()) } }.isSuccess
            }
            message = if (ok) "Дневник сохранён в CSV." else "Не получилось сохранить файл."
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull() }
            if (text == null || Backup.peek(text) == null) message = "Это не файл FitFat. Нужен JSON, сохранённый «Экспортом»."
            else importText = text
        }
    }

    Column(
        Modifier.fillMaxSize().background(colors.surface).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header("Настройки", onBack)

        Group("Оформление") {
            if (Build.VERSION.SDK_INT >= 31) {
                SwitchRow(MockIcons.Sun, "Цвета системы", "Цвета приложения из обоев телефона", dynamic) { AppSettings.setDynamic(it) }
            }
            // Тема и цвет приложения — когда цвета не берутся из обоев (макет: блок «manual»)
            androidx.compose.animation.AnimatedVisibility(!dynamic || Build.VERSION.SDK_INT < 31) {
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
            val hintsCtx = androidx.compose.ui.platform.LocalContext.current
            NavRow(MockIcons.Bulb, "Показать подсказки заново", "Палец на барабанах и подтягивание строк со свайпом") {
                Hints.get(hintsCtx).reset(); message = "Подсказки снова появятся на своих экранах."
            }
        }

        Group("Дневник") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Дневник по умолчанию", fontSize = 15.sp)
                PillToggle(listOf("Приёмы", "Время"), if (groupByTime) 1 else 0, { onGroupByTime(it == 1) }, Modifier.fillMaxWidth(), height = 40.dp)
            }
            NavRow(MockIcons.Clock, "Новый день начинается в ${DayClock.label(dayStart)}", "До этого часа продолжается текущий день") { editDayStart = true }
        }

        Group("Данные") {
            NavRow(MockIcons.ChevronRight, "Экспорт в JSON", "Всё: вес, продукты, блюда, дневник, тренировки") { exportJson.launch("fitfat-${LocalDate.now()}.json") }
            NavRow(MockIcons.ChevronRight, "Экспорт дневника в CSV", "Для таблиц: дата, продукт, граммы, КБЖУ") { exportCsv.launch("fitfat-diary-${LocalDate.now()}.csv") }
            NavRow(MockIcons.Repeat, "Импорт данных", "Из файла JSON — заменит текущие данные") { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        }

        Group("Обновления") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 14.sp, color = colors.onSurfaceVariant)
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
                        val ok = withContext(Dispatchers.IO) { runCatching { Backup.importJson(app.database, text) }.isSuccess }
                        message = if (ok) "Данные загружены." else "Файл повреждён — данные не изменились."
                    }
                }) { Text("Заменить", color = colors.error) }
            },
        )
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
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (sub != null) Text(sub, fontSize = 13.sp, color = colors.onSurfaceVariant)
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
