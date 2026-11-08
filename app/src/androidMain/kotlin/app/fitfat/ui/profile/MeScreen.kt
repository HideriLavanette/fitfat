package app.fitfat.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.DayType
import app.fitfat.data.Goal
import app.fitfat.data.Macros
import app.fitfat.data.NormsCalc
import app.fitfat.data.NormsPlan
import app.fitfat.data.Profile
import app.fitfat.data.Sex
import app.fitfat.data.trainingLabel
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmtF1
import app.fitfat.ui.components.FfSheet
import app.fitfat.ui.theme.MacroColors

/**
 * «Тело и цель» (0.5, решение автора 08.11.2026 вместо отдельных «Параметров тела» и «Цели и норм»): всё, из чего
 * считаются нормы, в одном месте и только для чтения — тело с расчётом, спорт, цель, белок и нормы по типам дня.
 * Тап по пункту — шторка с нужным шагом ([onSheet]: 1 о себе, 2 жир, 3 спорт, 4 цель, 5 нормы и белок).
 * «Все шаги заново» внизу открывает шаги первой настройки с барабанами, заполненные текущими значениями ([onEdit] — с какого
 * шага). Тап по цели — сразу шторка «Ваша цель» с каруселью и подробностями.
 */
@Composable
fun MeScreen(
    profile: Profile, weight: Double, norms: (DayType) -> Macros, custom: Set<String>, cuts: Int, eta: String?,
    onBack: () -> Unit, onEdit: (step: Int) -> Unit, onSheet: (step: Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val b = profile.body(weight)
    val lean = NormsCalc.lean(b)
    Box(Modifier.fillMaxSize().background(colors.surface)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header("Тело и цель", onBack)

            Section("Тело") {
                Line("Пол", if (profile.sex == Sex.Female) "Женский" else "Мужской") { onSheet(1) }
                Line("Возраст", "${profile.age} ${years(profile.age)}") { onSheet(1) }
                Line("Рост", "${profile.heightCm} см") { onSheet(1) }
                Line("Вес", "${fmtF1(weight)} кг") { onSheet(1) }
                Line("Процент жира", "${profile.bodyFatPct} %") { onSheet(2) }
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cell("Сухая масса", "${fmtF1(lean)} кг", Modifier.weight(1f))
                    Cell("Жировая масса", "${fmtF1(weight - lean)} кг", Modifier.weight(1f))
                    Cell("Обмен в покое", "${fmt0(NormsCalc.bmr(b))} ккал", Modifier.weight(1f))
                }
                Note("Обмен в покое считается по сухой массе — так точнее, чем по весу и росту. Вес берётся из последнего взвешивания.")
            }

            Section("Спорт") {
                val kinds = buildList { if (profile.gym) add("Силовые"); addAll(profile.sports.map { trainingLabel(it) }) }
                Line("Занятия", if (kinds.isEmpty()) "нет" else kinds.joinToString(", ")) { onSheet(3) }
                Line("День тренировки", if (profile.trains) "+${fmt0(NormsCalc.TRAINING_CARBS)} г углеводов" else "без добавки") { onSheet(3) }
            }

            Section("Цель") {
                Line("Цель", app.fitfat.ui.setup.goalTitle(profile.goal), onClick = { onSheet(4) })
                if ((profile.goal == Goal.Lose || profile.goal == Goal.Gain) && profile.targetKg != null) {
                    Line("Целевой вес", "${fmtF1(profile.targetKg)} кг") { onSheet(4) }
                    if (eta != null) Note(eta)
                }
                val pk = NormsPlan.proteinPerKg(profile, weight)
                Line("Белок", "${fmtF1(pk)} г на кг · ${fmt0(pk * weight)} г") { onSheet(5) }
            }

            Section("Нормы на день") {
                val types = buildList {
                    add(DayType.Normal)
                    if (profile.trains) add(DayType.Training)
                    if (profile.sex == Sex.Female) add(DayType.Cycle)
                }
                types.forEach { t ->
                    val id = when (t) { DayType.Normal -> "n"; DayType.Training -> "t"; DayType.Cycle -> "c" }
                    NormLine(t, norms(t), id in custom) { onSheet(5) }
                }
                if (cuts > 0) Note("Срезано ${fmt0(cuts * NormsCalc.CUT_KCAL)} ккал из-за застоя веса. Вернуть можно в «Изменить» на шаге норм.")
            }
        }
        Surface(
            onClick = { onEdit(1) }, shape = RoundedCornerShape(28.dp), color = colors.primary,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).fillMaxWidth().height(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text("Все шаги заново", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onPrimary) }
        }
    }
}

internal fun years(n: Int) = if (n % 10 == 1 && n % 100 != 11) "год" else if (n % 10 in 2..4 && n % 100 !in 12..14) "года" else "лет"

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) { content() }
    }
}

/** Строка «название — значение»; с [onClick] — нажимается целиком и показывает шеврон. */
@Composable
private fun Line(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
        if (onClick != null) Icon(MockIcons.ChevronRight, null, Modifier.padding(start = 4.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Note(text: String) =
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))

@Composable
private fun Cell(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Строка нормы: значок типа дня, название (своя — с пометкой), ккал и Б / Ж / У. */
@Composable
private fun NormLine(t: DayType, m: Macros, own: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (ink, bg, icon: ImageVector) = when (t) {
        DayType.Training -> Triple(MacroColors.training, MacroColors.trainingContainer, MockIcons.Dumbbell)
        DayType.Cycle -> Triple(MacroColors.cycle, MacroColors.cycleContainer, MockIcons.Drop)
        DayType.Normal -> Triple(colors.onSurfaceVariant, colors.surfaceContainerHighest, MockIcons.NavToday)
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val name = when (t) { DayType.Normal -> "Обычный день"; DayType.Training -> "День тренировки"; DayType.Cycle -> "Дни цикла" }
            Text(name + if (own) " · своя" else "", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = MacroColors.protein, fontWeight = FontWeight.Bold)) { append("Б ") }; append(fmt0(m.protein) + "  ")
                withStyle(SpanStyle(color = MacroColors.fat, fontWeight = FontWeight.Bold)) { append("Ж ") }; append(fmt0(m.fat) + "  ")
                withStyle(SpanStyle(color = MacroColors.carbs, fontWeight = FontWeight.Bold)) { append("У ") }; append(fmt0(m.carbs))
            }, fontSize = 13.sp, color = colors.onSurface)
        }
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)) { append(fmt0(m.kcal)) }
            withStyle(SpanStyle(fontSize = 12.sp, color = colors.onSurfaceVariant)) { append(" ккал") }
        })
    }
}
