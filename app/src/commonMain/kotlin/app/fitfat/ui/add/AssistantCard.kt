package app.fitfat.ui.add

import app.fitfat.ui.components.FfAlert

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fitfat.data.Assistant
import app.fitfat.data.Macros
import app.fitfat.ui.common.CategoryIcon
import app.fitfat.ui.common.MockIcons
import app.fitfat.ui.common.fmt0
import app.fitfat.ui.common.fmt1
import app.fitfat.ui.common.macroLine

/** Строка похожа на вопрос или просьбу к ассистенту, а не на список еды. */
fun looksLikeRequest(text: String): Boolean {
    val t = text.trim().lowercase()
    return '?' in t || REQUEST.containsMatchIn(t)
}

private val REQUEST = Regex("""^(сколько|что|чем|как|почему|зачем|можно|посоветуй|подскажи|предложи|удали|убери|перенеси|измени|исправь|замени|оцени|посчитай)\b""")

/** Кнопка под «Что съели»: «Спросить ассистента» / «Ассистент думает…». 48, скругление 16, `secondaryContainer`. */
@Composable
fun AssistantBar(busy: Boolean, onAsk: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onAsk, enabled = !busy, shape = RoundedCornerShape(16.dp), color = colors.secondaryContainer,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedContent(busy, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "aiIcon") { b ->
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (b) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.onSecondaryContainer)
                    else Icon(MockIcons.NavAssistant, null, Modifier.size(20.dp), tint = colors.onSecondaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(if (busy) "Ассистент думает…" else "Спросить ассистента", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onSecondaryContainer)
                if (!busy) Text("Оценит блюдо без рецепта или ответит на вопрос", fontSize = 12.sp, color = colors.onSecondaryContainer.copy(alpha = 0.8f))
            }
        }
    }
}

/**
 * Ответ ассистента (DESIGN.md, «Ассистент в „Добавить“»): блюда с весом и КБЖУ со значком «≈», пометка о неточности,
 * «Отмена» слева и «Добавить N» справа; уточнение — поле для ответа своими словами и варианты кнопками; ответ на вопрос — текст и «Понятно».
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AssistantCard(
    reply: Assistant.Reply, onDismiss: () -> Unit, onAdd: (List<Assistant.Item>) -> Unit, onOption: (String) -> Unit, onRetry: () -> Unit,
    edits: List<app.fitfat.data.DiaryEdits.Planned> = emptyList(),
    editsState: app.fitfat.ui.components.EditsState = app.fitfat.ui.components.EditsState.Pending,
    onApplyEdits: () -> Unit = {}, onRevertEdits: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    // Отвечают своими словами — клавиатура выезжает, докручиваем до всей карточки: поле, варианты и «Отмена» на виду
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    Column(
        Modifier.fillMaxWidth().bringIntoViewRequester(bring).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(MockIcons.NavAssistant, null, Modifier.size(18.dp), tint = colors.primary)
            Text("Ассистент", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.primary)
        }
        when (reply) {
            is Assistant.Reply.Items -> {
                if (reply.text.isNotBlank()) Text(reply.text, fontSize = 14.sp, lineHeight = 20.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { reply.items.forEach { EstimateRow(it) } }
                if (reply.items.size > 1) {
                    val total = reply.items.fold(Macros()) { a, it -> a + it.macros }
                    Text("Всего ≈ ${fmt0(total.kcal)} ккал", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
                }
                Text(
                    "Примерная оценка — реальные КБЖУ могут заметно отличаться." + (if (reply.note.isNotBlank()) " ${reply.note}" else ""),
                    fontSize = 12.sp, lineHeight = 16.sp, color = colors.onSurfaceVariant,
                )
                Buttons(cancel = "Отмена", onCancel = onDismiss, ok = "Добавить ${reply.items.size}", onOk = { onAdd(reply.items) })
            }
            is Assistant.Reply.Question -> {
                Text(reply.text, fontSize = 15.sp, lineHeight = 21.sp)
                AnswerField(bring, onSend = onOption)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reply.options.forEach { o ->
                        Surface(onClick = { onOption(o) }, shape = RoundedCornerShape(12.dp), color = colors.secondaryContainer, modifier = Modifier.heightIn(min = 40.dp)) {
                            Box(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                                Text(o, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSecondaryContainer)
                            }
                        }
                    }
                }
                Buttons(cancel = "Отмена", onCancel = onDismiss)
            }
            is Assistant.Reply.Answer -> {
                Text(reply.text, fontSize = 15.sp, lineHeight = 21.sp)
                Buttons(cancel = "Понятно", onCancel = onDismiss)
            }
            // Правка уже записанного: та же карточка, что в чате; «Отмена» закрывает ответ, после «Применить» — «Готово»
            is Assistant.Reply.Edits -> {
                if (reply.text.isNotBlank()) Text(reply.text, fontSize = 15.sp, lineHeight = 21.sp)
                app.fitfat.ui.components.DiaryEditsCard(edits, editsState, onApply = onApplyEdits, onCancel = onDismiss, onRevert = onRevertEdits)
                if (editsState != app.fitfat.ui.components.EditsState.Pending) Buttons(cancel = "Готово", onCancel = onDismiss)
            }
            is Assistant.Reply.Error -> {
                Text(reply.message, fontSize = 14.sp, lineHeight = 20.sp, color = colors.error)
                Buttons(cancel = "Закрыть", onCancel = onDismiss, ok = "Ещё раз", onOk = onRetry)
            }
        }
    }
}

/**
 * Ответ своими словами на вопрос ассистента («тесто, говяжий фарш, лук; 5 штук»): поле 48, скругление 14,
 * `surfaceContainerHighest`, справа круглая «Отправить» (`primary`), активна при непустом тексте.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AnswerField(bring: androidx.compose.foundation.relocation.BringIntoViewRequester, onSend: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val send = { if (text.isNotBlank()) { onSend(text.trim()); text = "" } }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHighest).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            text, { text = it },
            modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                .onFocusChanged { if (it.isFocused) scope.launch { kotlinx.coroutines.delay(350); bring.bringIntoView() } },
            textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp, lineHeight = 20.sp), cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("Ответить своими словами", fontSize = 15.sp, color = colors.onSurfaceVariant)
                    inner()
                }
            },
        )
        Surface(onClick = send, enabled = text.isNotBlank(), shape = RoundedCornerShape(20.dp), color = if (text.isNotBlank()) colors.primary else colors.surfaceContainerHigh, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(MockIcons.ChevronRight, "Отправить", Modifier.size(20.dp), tint = if (text.isNotBlank()) colors.onPrimary else colors.outline)
            }
        }
    }
}

/** Блюдо оценки: плашка 40, название и вес, справа «≈ N ккал» и БЖУ порции. */
@Composable
private fun EstimateRow(it: Assistant.Item) {
    val colors = MaterialTheme.colorScheme
    val m = it.macros
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHighest).padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CategoryIcon(it.category, 40.dp, radius = 12.dp, iconSize = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(it.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                if (it.existing == null) app.fitfat.ui.components.AiMark()
            }
            Text(
                (it.pieces?.let { p -> "${fmt1(p)} шт · ${fmt0(it.grams)} г" } ?: "${fmt0(it.grams)} г") +
                    if (it.existing != null) " · из вашей базы" else " · ≈ ${fmt0(it.per100.kcal)} ккал на 100 г",
                fontSize = 12.sp, color = colors.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("≈ ${fmt0(m.kcal)} ккал", fontSize = 15.sp, fontWeight = FontWeight(650))
            Text(macroLine(m.protein, m.fat, m.carbs), fontSize = 11.sp, color = colors.onSurfaceVariant)
        }
    }
}

/** Закрывающее — слева, действие — справа. */
@Composable
private fun Buttons(cancel: String, onCancel: () -> Unit, ok: String? = null, onOk: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onCancel, shape = RoundedCornerShape(20.dp), color = Color.Transparent, modifier = Modifier.height(40.dp)) {
            Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(cancel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.primary) }
        }
        Box(Modifier.weight(1f))
        if (ok != null) Surface(onClick = onOk, shape = RoundedCornerShape(20.dp), color = colors.primary, modifier = Modifier.height(40.dp)) {
            Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) { Text(ok, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onPrimary) }
        }
    }
}

/** Согласие перед первым запросом: что уходит и куда. */
@Composable
fun AssistantConsentDialog(onDismiss: () -> Unit, onAgree: () -> Unit) {
    FfAlert(
        onDismissRequest = onDismiss,
        title = { Text("Ассистент") },
        text = {
            Text(
                "Ассистент оценивает блюда, которых нет в базе, правит записи по вашей просьбе и отвечает на вопросы. Для этого строка, " +
                    "которую вы написали, остаток калорий на день, названия похожих продуктов из вашей базы и записи дневника за сегодня " +
                    "и вчера (название, порция, приём) уходят через сервер FitFat в Claude (Anthropic). Вес и параметры тела не отправляются." +
                    "\n\nОценки примерные: реальные КБЖУ могут заметно отличаться.",
                fontSize = 14.sp, lineHeight = 20.sp,
            )
        },
        // Закрывающее — слева
        dismissButton = { TextButton(onClick = onDismiss) { Text("Не сейчас") } },
        confirmButton = { TextButton(onClick = onAgree) { Text("Понятно, продолжить") } },
    )
}
