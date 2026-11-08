package app.fitfat.ui.assistant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.Access
import app.fitfat.data.GoogleAccount
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Цена подписки (DESIGN.md, «Подписка и доступ»). Покупка через Google Play — когда будет платёжный профиль. */
const val SUBSCRIPTION_PRICE = "$4,99"

private data class Feature(val title: String, val text: String)

private val FEATURES = listOf(
    Feature("Понимает, как вы пишете", "Напишите как есть — ассистент сам разберёт фразу на продукты и граммы."),
    Feature("Считает блюда без рецепта", "Манты из кафе или домашний плов — оценит вес и КБЖУ и предупредит, что цифры примерные."),
    Feature("Отвечает с цифрами", "Видит ваш дневник, вес и нормы — объяснит, почему вес стоит, и разберёт прошлый день."),
    Feature("Подскажет, чем добрать", "Предложит продукты из вашей базы под остаток белка и калорий — в дневник одной кнопкой."),
)

/**
 * Вкладка «Ассистент» без доступа (решение автора 09.11.2026): спокойный рассказ, без навязывания. Карточки возможностей
 * сменяют друг друга сами (6 с) с живой мини-демонстрацией; точки снизу — нажать, чтобы перейти. Ниже — цена,
 * «Попробовать 7 дней бесплатно» (без карты, привязано к телефону) и «Подписка — $4,99 в месяц».
 */
@Composable
fun AssistantIntro() {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val access by Access.state.collectAsState()
    val account by GoogleAccount.get().account.collectAsState()
    var page by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { Access.refresh() }
    LaunchedEffect(page) { delay(6_000); page = (page + 1) % FEATURES.size }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(MockIcons.NavAssistant, null, Modifier.size(20.dp), tint = colors.onSecondaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text("Ассистент", fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight(650))
                Text("питание без ручного ввода", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
        }

        // Возможности: карточка с демонстрацией
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainer).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AnimatedContent(
                page, label = "feature",
                transitionSpec = { (slideInHorizontally(tween(380)) { it / 4 } + fadeIn(tween(380))) togetherWith (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(240))) },
            ) { i ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerLow).padding(14.dp), contentAlignment = Alignment.TopCenter) {
                        when (i) {
                            0 -> DemoParse()
                            1 -> DemoEstimate()
                            2 -> DemoChat()
                            else -> DemoSuggest()
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(FEATURES[i].title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(FEATURES[i].text, fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                FEATURES.indices.forEach { k ->
                    val on = k == page
                    Box(
                        Modifier.height(8.dp).width(if (on) 22.dp else 8.dp).clip(CircleShape)
                            .background(if (on) colors.primary else colors.surfaceContainerHighest).clickable { page = k },
                    )
                }
            }
        }

        // Цена и кнопки
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                Text(SUBSCRIPTION_PRICE, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(" в месяц", fontSize = 15.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp))
            }
            Text("Трекер остаётся бесплатным. Подписка — только на ассистента.", fontSize = 13.sp, lineHeight = 18.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            if (!access.trialUsed) {
                Surface(
                    onClick = {
                        if (busy) return@Surface
                        busy = true
                        scope.launch { message = Access.startTrial(); busy = false }
                    },
                    shape = RoundedCornerShape(28.dp), color = colors.primary, modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (busy) "Включаю…" else "Попробовать 7 дней бесплатно", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = colors.onPrimary)
                    }
                }
                Text(
                    "Без карты и оплаты. Через 7 дней ассистент отключится сам." + if (account == null) " Для работы нужен вход через Google в «Профиле»." else "",
                    fontSize = 12.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Пробная неделя на этом телефоне уже была.", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Surface(
                onClick = { message = "Оплата через Google Play появится в одном из следующих обновлений." },
                shape = RoundedCornerShape(28.dp), color = if (access.trialUsed) colors.primary else colors.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "Подписка — $SUBSCRIPTION_PRICE в месяц", fontSize = 16.sp, fontWeight = FontWeight.Medium,
                        color = if (access.trialUsed) colors.onPrimary else colors.onSurface,
                    )
                }
            }
        }
    }

    message?.let { m ->
        AlertDialog(onDismissRequest = { message = null }, text = { Text(m) }, confirmButton = { TextButton(onClick = { message = null }) { Text("Понятно") } })
    }
}

// ---------- Мини-демонстрации ----------

/** Печатается фраза, потом под ней по одному появляются распознанные продукты. */
@Composable
private fun DemoParse() {
    val colors = MaterialTheme.colorScheme
    val phrase = "бургер из кафе и полпорции фри"
    var typed by remember { mutableIntStateOf(0) }
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(300)
        while (typed < phrase.length) { typed++; delay(55) }
        delay(350)
        repeat(2) { shown++; delay(450) }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(phrase.take(typed) + if (typed < phrase.length) "▍" else "", fontSize = 16.sp, color = colors.onSurface,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 12.dp))
        DemoRow(shown >= 1, "grain", "Бургер", "≈ 230 г · 560 ккал")
        DemoRow(shown >= 2, "veg", "Картофель фри", "≈ 60 г · 190 ккал")
    }
}

/** Карточка оценки блюда со значком «≈». */
@Composable
private fun DemoEstimate() {
    val colors = MaterialTheme.colorScheme
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { delay(300); step = 1; delay(600); step = 2; delay(600); step = 3 }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DemoRow(step >= 1, "grain", "Манты с говядиной", "5 шт · 647 г")
        AnimatedVisibility(step >= 2, enter = fadeIn() + slideInVertically { it / 2 }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                Text("≈ 1 476", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(" ккал", fontSize = 15.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        AnimatedVisibility(step >= 3, enter = fadeIn()) {
            Text("Примерная оценка — реальные КБЖУ могут отличаться.", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** Вопрос и ответ ассистента. */
@Composable
private fun DemoChat() {
    val colors = MaterialTheme.colorScheme
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { delay(300); step = 1; delay(900); step = 2 }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(step >= 1, Modifier.align(Alignment.End), enter = fadeIn() + slideInVertically { it / 2 }) {
            Text("Почему вес стоит?", fontSize = 14.sp, color = colors.onPrimaryContainer,
                modifier = Modifier.clip(RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp)).background(colors.primaryContainer).padding(horizontal = 14.dp, vertical = 10.dp))
        }
        AnimatedVisibility(step >= 2, enter = fadeIn() + slideInVertically { it / 2 }) {
            Text("За неделю в среднем 2 140 ккал при норме 1 830 — перебор ~310 в день. Уберите вечерний перекус, и вес пойдёт.",
                fontSize = 14.sp, lineHeight = 19.sp,
                modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)).background(colors.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

/** Предложения с «+», одно отмечается галочкой. */
@Composable
private fun DemoSuggest() {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { delay(300); step = 1; delay(450); step = 2; delay(900); step = 3 }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DemoRow(step >= 1, "dairy", "Творог 5%, 240 г", "41 г белка · 290 ккал", plus = true, done = step >= 3)
        DemoRow(step >= 2, "meat", "Тунец, 150 г", "32 г белка · 144 ккал", plus = true)
    }
}

@Composable
private fun DemoRow(visible: Boolean, category: String, title: String, sub: String, plus: Boolean = false, done: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(visible, enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHigh).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryIcon(category, 36.dp, radius = 12.dp, iconSize = 18.dp)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            if (plus) Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (done) colors.secondaryContainer else colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(if (done) MockIcons.Check else MockIcons.Plus, null, Modifier.size(16.dp), tint = if (done) colors.onSecondaryContainer else colors.onSurface) }
        }
    }
}

/** Подпись срока пробной недели: «до 16 нояб». */
fun trialUntil(ms: Long): String = app.fitfat.data.cloudWhen(ms).substringBefore(",").let { if (it == "сегодня" || it == "вчера") it else "до $it" }

@Suppress("unused") private val keep = Color.Unspecified
