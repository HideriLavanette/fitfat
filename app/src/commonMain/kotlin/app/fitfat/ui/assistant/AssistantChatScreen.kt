package app.fitfat.ui.assistant

import app.fitfat.ui.components.FfAlert

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.Assistant
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0

private val QUICK = listOf("Что на ужин?", "Почему вес стоит?", "Разбери вчера")

/**
 * Вкладка «Ассистент» (макет 5.2): шапка — круг 40 с искрой, «Ассистент» 20 / 650, «видит ваш дневник, вес и цели» 12,
 * справа «Отчёт» (36, `surfaceContainerHighest`). Сообщения человека справа (`primaryContainer`, 22/22/6/22, до 280),
 * ответы слева (`surfaceContainer`, 22/22/22/6, до 320) с продуктами-предложениями и «+». Над полем — быстрые вопросы
 * (32, скругление 8, `surfaceContainerHigh`), поле 56 со скруглением 28 и круглой «Отправить» 44 (`primary`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantChatScreen(vm: AssistantViewModel, onReport: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var input by remember { mutableStateOf("") }
    var consentFor by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    fun send(text: String) {
        if (text.isBlank() || vm.busy) return
        if (!Assistant.chatConsented()) { consentFor = text; return }
        vm.send(text); input = ""
    }
    // Новое сообщение или «думаю» — к низу переписки
    LaunchedEffect(vm.messages.size, vm.busy) { val n = vm.messages.size + (if (vm.busy) 1 else 0); if (n > 0) list.animateScrollToItem(n) }

    val ime = app.fitfat.ui.components.imeVisible()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (ime) 12.dp else 128.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Шапка
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(MockIcons.NavAssistant, null, Modifier.size(20.dp), tint = colors.onSecondaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text("Ассистент", fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight(650))
                Text("видит ваш дневник, вес и цели", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            Surface(onClick = onReport, shape = RoundedCornerShape(18.dp), color = colors.surfaceContainerHighest, modifier = Modifier.height(36.dp)) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("Отчёт", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        // Переписка
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item(key = "hello") {
                AssistantBubble {
                    Text("Спросите про еду, вес или тренировки — я вижу ваш дневник, вес и цели и отвечу с цифрами.", fontSize = 15.sp, lineHeight = 21.sp)
                }
            }
            items(vm.messages, key = { it.id }) { m ->
                if (m.fromUser) UserBubble(m.text)
                else AssistantBubble {
                    if (m.error) {
                        Text(m.text, fontSize = 15.sp, lineHeight = 21.sp, color = colors.error)
                        Surface(onClick = { vm.retry() }, shape = RoundedCornerShape(18.dp), color = colors.surfaceContainerHighest, modifier = Modifier.height(36.dp)) {
                            Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("Ещё раз", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    } else {
                        if (m.text.isNotBlank()) Text(m.text, fontSize = 15.sp, lineHeight = 21.sp)
                        if (m.items.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            m.items.forEachIndexed { i, it -> SuggestionRow(it, added = (m.id to i) in vm.added) { vm.addToDiary(m, i) {} } }
                        }
                        if (m.note.isNotBlank()) Text(m.note, fontSize = 14.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
            if (vm.busy) item(key = "busy") {
                AssistantBubble {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.onSurfaceVariant)
                        Text("Думаю…", fontSize = 15.sp, color = colors.onSurfaceVariant)
                    }
                }
            }
        }

        // Быстрые вопросы
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK.forEach { q ->
                Surface(onClick = { send(q) }, enabled = !vm.busy, shape = RoundedCornerShape(8.dp), color = colors.surfaceContainerHigh, modifier = Modifier.height(32.dp)) {
                    Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text(q, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        // Поле
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainerHighest).padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                input, { input = it },
                modifier = Modifier.weight(1f).semantics { contentDescription = "Сообщение ассистенту" },
                textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp, lineHeight = 22.sp), cursorBrush = SolidColor(colors.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(input) }),
                decorationBox = { inner -> Box { if (input.isEmpty()) Text("Спросите что-нибудь", fontSize = 16.sp, color = colors.onSurfaceVariant); inner() } },
            )
            val can = input.isNotBlank() && !vm.busy
            Surface(onClick = { send(input) }, enabled = can, shape = RoundedCornerShape(22.dp), color = if (can) colors.primary else colors.surfaceContainerHigh, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(MockIcons.ArrowUp, "Отправить", Modifier.size(20.dp), tint = if (can) colors.onPrimary else colors.outline) }
            }
        }
    }

    consentFor?.let { pending ->
        FfAlert(
            onDismissRequest = { consentFor = null },
            title = { Text("Ассистент") },
            text = {
                Text(
                    "Чтобы отвечать с цифрами, ассистент получает сводку: пол, возраст, рост, цель и целевой вес; нормы и что вы съели сегодня и вчера; " +
                        "средние за неделю; вес за 3 недели; частые продукты из базы. Имя, почта и заметки не отправляются. " +
                        "Сводка и ваши сообщения уходят через сервер FitFat в Claude (Anthropic).",
                    fontSize = 14.sp, lineHeight = 20.sp,
                )
            },
            // Закрывающее — слева
            dismissButton = { TextButton(onClick = { consentFor = null }) { Text("Не сейчас") } },
            confirmButton = { TextButton(onClick = { Assistant.giveChatConsent(); consentFor = null; vm.send(pending); input = "" }) { Text("Понятно, продолжить") } },
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text, fontSize = 15.sp, lineHeight = 21.sp, color = colors.onPrimaryContainer,
            modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp)).background(colors.primaryContainer).padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AssistantBubble(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(true, enter = fadeIn()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Column(
                Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)).background(colors.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) { content() }
        }
    }
}

/** Предложение: плашка 36 / 12, «Творог 5%, 240 г» 600 и «41 г белка · 290 ккал» 12; «+» 40 / 12 — в дневник, после — галочка. */
@Composable
private fun SuggestionRow(item: Assistant.Item, added: Boolean, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val m = item.macros
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerLow).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryIcon(item.category, 36.dp, radius = 12.dp, iconSize = 18.dp)
        Column(Modifier.weight(1f)) {
            Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("${fmt0(m.protein)} г белка · ${fmt0(m.kcal)} ккал", fontSize = 12.sp, color = colors.onSurfaceVariant)
        }
        Surface(
            onClick = onAdd, enabled = !added, shape = RoundedCornerShape(12.dp),
            color = if (added) colors.secondaryContainer else colors.surfaceContainerHighest, modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (added) MockIcons.Check else MockIcons.Plus, if (added) "В дневнике" else "Добавить ${item.name} в дневник", Modifier.size(18.dp),
                    tint = if (added) colors.onSecondaryContainer else colors.onSurface)
            }
        }
    }
}
