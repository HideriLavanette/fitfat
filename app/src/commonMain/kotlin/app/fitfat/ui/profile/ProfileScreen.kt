package app.fitfat.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.DayType
import app.fitfat.data.Macros
import app.fitfat.data.Profile
import app.fitfat.data.Sex
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmtF1
import app.fitfat.ui.components.FfSheet
import app.fitfat.ui.theme.MacroColors

/**
 * «Профиль» (макет Profile, 5.6): короткая сводка тела, «Нормы на день» карточками по типам дня (тап — шторка
 * «чем отличается от обычного дня» с переходом в «Тело и цель») и меню разделов. Тело, цель и нормы — один экран
 * «Тело и цель» по тапу на сводку (решение 08.11.2026). Карточки Google-аккаунта нет: синхронизации пока нет (DESIGN.md).
 */
@Composable
fun ProfileScreen(
    profile: Profile?, weight: Double?, norms: (DayType) -> Macros, todayType: DayType,
    onMe: () -> Unit, onTraining: () -> Unit, onProducts: () -> Unit, onSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf<DayType?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Профиль", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))

        // Аккаунт Google — первым, как в макете
        AccountCard()

        // Сводка тела и цели — тап открывает «Тело и цель»
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerLow).clickable(onClick = onMe).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(MockIcons.NavProfile, null, Modifier.size(24.dp), tint = colors.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (profile == null) {
                    Text("Профиль не заполнен", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Рост, вес и процент жира — для расчёта норм", fontSize = 13.sp, color = colors.onSurfaceVariant)
                } else {
                    val w = weight ?: profile.weightKg
                    Text("${if (profile.sex == Sex.Female) "Женщина" else "Мужчина"}, ${profile.age} ${years(profile.age)}", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("${profile.heightCm} см · ${fmtF1(w)} кг · жир ${profile.bodyFatPct} % · ${app.fitfat.ui.setup.goalTitle(profile.goal).lowercase()}", fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
            Icon(MockIcons.ChevronRight, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
        }

        // Нормы на день по типам
        if (profile != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Нормы на день", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
                val types = buildList {
                    add(DayType.Normal)
                    if (profile.trains || todayType == DayType.Training) add(DayType.Training)
                    if (profile.sex == Sex.Female) add(DayType.Cycle)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { t -> NormTile(t, norms(t), t == todayType, Modifier.weight(1f)) { open = t } }
                }
            }
        }

        Group("Разделы") {
            NavRow(MockIcons.Dumbbell, "Тренировки", "Виды, расписание, история", onTraining)
            NavRow(MockIcons.Search, "Продукты и блюда", "Поиск, карточки, правка, штрихкоды", onProducts)
        }
        Group("Ещё") {
            NavRow(MockIcons.Sun, "Настройки приложения", "Тема, напоминания, данные, обновления", onSettings)
        }
        Spacer(Modifier.height(120.dp)) // место под плавающий навбар
    }
    open?.let { t -> NormSheet(t, norms(DayType.Normal), norms(t), onGoals = { open = null; onMe() }) { open = null } }
}

private fun typeIcon(t: DayType): ImageVector = when (t) { DayType.Training -> MockIcons.Dumbbell; DayType.Cycle -> MockIcons.Drop; DayType.Normal -> MockIcons.NavToday }

@Composable
private fun typeColors(t: DayType): Pair<Color, Color> = when (t) {
    DayType.Training -> MacroColors.training to MacroColors.trainingContainer
    DayType.Cycle -> MacroColors.cycle to MacroColors.cycleContainer
    DayType.Normal -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceContainerHighest
}

private fun typeTitle(t: DayType) = when (t) { DayType.Normal -> "Обычный"; DayType.Training -> "Тренировка"; DayType.Cycle -> "Цикл" }

/** Плитка нормы: значок в цвете типа, название, ккал, Б / Ж / У; сегодняшний тип — с рамкой. */
@Composable
private fun NormTile(t: DayType, m: Macros, today: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (ink, bg) = typeColors(t)
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(colors.surfaceContainer)
            .then(if (today) Modifier.background(colors.primary.copy(alpha = 0.06f)) else Modifier)
            .clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(typeIcon(t), null, Modifier.size(18.dp), tint = ink)
        }
        Text(typeTitle(t) + if (today) " · сегодня" else "", fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)) { append(fmt0(m.kcal)) }
            withStyle(SpanStyle(fontSize = 12.sp, color = colors.onSurfaceVariant)) { append(" ккал") }
        })
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = MacroColors.protein, fontWeight = FontWeight.Bold)) { append("Б ") }; append(fmt0(m.protein) + "  ")
            withStyle(SpanStyle(color = MacroColors.fat, fontWeight = FontWeight.Bold)) { append("Ж ") }; append(fmt0(m.fat) + "  ")
            withStyle(SpanStyle(color = MacroColors.carbs, fontWeight = FontWeight.Bold)) { append("У ") }; append(fmt0(m.carbs))
        }, fontSize = 12.sp, maxLines = 1)
    }
}

/** Шторка нормы типа дня: таблица «Обычно / этот день / разница», почему так, «Настроить» и «Понятно». */
@Composable
private fun NormSheet(t: DayType, normal: Macros, day: Macros, onGoals: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (ink, bg) = typeColors(t)
    FfSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
                Icon(typeIcon(t), null, Modifier.size(20.dp), tint = ink)
            }
            Text(if (t == DayType.Normal) "Обычный день" else if (t == DayType.Training) "День тренировки" else "Дни цикла", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer).padding(vertical = 6.dp)) {
            TableRow("", "Обычно", if (t == DayType.Normal) "Норма" else typeTitle(t), "Разница", head = true)
            fun d(a: Double, b: Double) = (b - a).let { if (kotlin.math.abs(it) < 0.5) "—" else (if (it > 0) "+" else "−") + fmt0(kotlin.math.abs(it)) }
            TableRow("Ккал", fmt0(normal.kcal), fmt0(day.kcal), d(normal.kcal, day.kcal))
            TableRow("Белки", fmt0(normal.protein), fmt0(day.protein), d(normal.protein, day.protein), MacroColors.protein)
            TableRow("Жиры", fmt0(normal.fat), fmt0(day.fat), d(normal.fat, day.fat), MacroColors.fat)
            TableRow("Углеводы", fmt0(normal.carbs), fmt0(day.carbs), d(normal.carbs, day.carbs), MacroColors.carbs)
        }
        Text(
            when (t) {
                DayType.Normal -> "База — обмен в покое по сухой массе. Белок и жиры — по сухой массе и цели, остальное — углеводы. Если вес стоит две недели, приложение предложит срезать 150 ккал."
                DayType.Training -> "В день интенсивной тренировки добавляются углеводы — топливо для мышц и восстановления. Белок и жиры не меняются. Дни тренировок берутся из расписания или отметки дня."
                DayType.Cycle -> "Во второй половине цикла обмен немного выше и тянет на сладкое — небольшая добавка углеводов помогает держать режим без срывов."
            },
            fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onGoals, modifier = Modifier.weight(1f).height(48.dp)) { Text("Настроить") }
            Button(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("Понятно") }
        }
    }
}

@Composable
private fun TableRow(label: String, a: String, b: String, c: String, ink: Color = MaterialTheme.colorScheme.onSurface, head: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val sub = colors.onSurfaceVariant
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ink, modifier = Modifier.width(84.dp))
        listOf(a, b, c).forEachIndexed { i, s ->
            Text(
                s, fontSize = if (head) 12.sp else 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                fontWeight = if (!head && i == 1) FontWeight.SemiBold else FontWeight.Normal,
                color = if (head) sub else if (i == 2 && s.startsWith("+")) colors.primary else if (i == 2) sub else colors.onSurface,
            )
        }
    }
}
