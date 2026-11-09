package app.fitfat.ui.assistant

import app.fitfat.platform.Prefs

import app.fitfat.data.AppGraph

import androidx.lifecycle.ViewModel

import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import app.fitfat.data.Assistant
import app.fitfat.data.AssistantContext
import app.fitfat.data.DayClock
import app.fitfat.data.Meal
import app.fitfat.data.Portion
import app.fitfat.data.Product
import app.fitfat.data.Recognizer
import app.fitfat.data.SOURCE_AI
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

/** Сообщение чата. У ответа ассистента — продукты-предложения и вывод; [error] — текст ошибки вместо ответа. */
data class ChatMsg(
    val id: Long, val fromUser: Boolean, val text: String,
    val items: List<Assistant.Item> = emptyList(), val note: String = "", val error: Boolean = false,
    /** Предложенные правки дневника — карточка «Применить» под ответом. */
    val edits: List<app.fitfat.data.DiaryEdits.Planned> = emptyList(),
)

/** Карточка правок: ждёт решения, применена (можно вернуть), отменена или возвращена. */
enum class EditsState { Pending, Applied, Cancelled, Reverted }

/**
 * Вкладка «Ассистент» (макет 5.2): переписка живёт, пока открыто приложение. В запрос — последние сообщения
 * и свежая сводка данных ([AssistantContext]); «+» у предложения записывает его в дневник сейчас.
 */
class AssistantViewModel : ViewModel() {
    private val fa = AppGraph.get()
    val messages = mutableStateListOf<ChatMsg>()
    var busy by mutableStateOf(false)
        private set
    /** Предложения, уже добавленные в дневник: id сообщения → номера продуктов. */
    val added = mutableStateListOf<Pair<Long, Int>>()
    private var nextId = 1L
    /** Состояние карточки правок по id сообщения и как вернуть применённое. */
    val editsState = androidx.compose.runtime.mutableStateMapOf<Long, EditsState>()
    private val undo = mutableMapOf<Long, suspend () -> Unit>()

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        messages += ChatMsg(nextId++, true, t)
        busy = true
        viewModelScope.launch {
            val history = messages.filter { !it.error }.takeLast(12).map { m ->
                (if (m.fromUser) "user" else "assistant") to
                    (m.text + m.items.joinToString("") { "\n— ${it.name}" } + (if (m.note.isNotBlank()) "\n${m.note}" else "") +
                        (if (m.edits.isEmpty()) "" else "\n[Правки дневника: " + when (editsState[m.id]) {
                            EditsState.Applied -> "применены"; EditsState.Cancelled -> "отменены"; EditsState.Reverted -> "применены и возвращены"; else -> "ещё не применены"
                        } + "]")).trim()
            }
            val ctx = runCatching { AssistantContext.build(fa) }.getOrNull()
            val reply = if (ctx == null) Assistant.ChatReply.Error("Не получилось собрать данные дневника.")
                else Assistant.chat(history, ctx)
            // Правки — к записям сегодня и вчера; несуществующие id отбрасываются
            val planned = if (reply is Assistant.ChatReply.Ok && reply.edits.isNotEmpty()) runCatching {
                val today = DayClock.today()
                app.fitfat.data.DiaryEdits.plan(reply.edits, fa.food.range(today.minusDays(1), today).first(), today)
            }.getOrDefault(emptyList()) else emptyList()
            messages += when (reply) {
                is Assistant.ChatReply.Ok -> ChatMsg(nextId++, false, reply.text, reply.items, reply.note, edits = planned).also {
                    if (planned.isNotEmpty()) editsState[it.id] = EditsState.Pending
                }
                is Assistant.ChatReply.Error -> ChatMsg(nextId++, false, reply.message, error = true)
            }
            busy = false
        }
    }

    /** «Применить» под правками: записи меняются в дневнике, «Вернуть» откатывает. */
    fun applyEdits(msg: ChatMsg) {
        if (editsState[msg.id] != EditsState.Pending) return
        editsState[msg.id] = EditsState.Applied
        viewModelScope.launch {
            runCatching { app.fitfat.data.DiaryEdits.apply(fa.food, msg.edits) }
                .onSuccess { undo[msg.id] = it }
                .onFailure { editsState[msg.id] = EditsState.Pending }
        }
    }

    fun cancelEdits(msg: ChatMsg) { if (editsState[msg.id] == EditsState.Pending) editsState[msg.id] = EditsState.Cancelled }

    fun revertEdits(msg: ChatMsg) {
        val u = undo.remove(msg.id) ?: return
        editsState[msg.id] = EditsState.Reverted
        viewModelScope.launch { runCatching { u() } }
    }

    /** Повторить последний вопрос после ошибки. */
    fun retry() {
        val last = messages.lastOrNull { it.fromUser } ?: return
        messages.removeAll { it.error }
        messages.remove(last)
        send(last.text)
    }

    /** «+» у предложения: в дневник на сегодня, сейчас, приём по времени. Нового продукта нет в базе — сохраняется с пометкой «оценка ассистента». */
    fun addToDiary(msg: ChatMsg, index: Int, onDone: (String) -> Unit) = viewModelScope.launch {
        val item = msg.items.getOrNull(index) ?: return@launch
        val all = fa.food.allProducts.first()
        val p = all.firstOrNull { it.name.equals(item.existing ?: item.name, ignoreCase = true) } ?: fa.food.createProduct(
            Product(
                name = item.name, category = item.category,
                kcal = item.per100.kcal, protein = item.per100.protein, fat = item.per100.fat, carbs = item.per100.carbs,
                defaultGrams = Math.round(item.grams / (item.pieces ?: 1.0)).toDouble().coerceAtLeast(1.0),
                aliases = Recognizer.normalize(item.name), source = SOURCE_AI,
            ),
        )
        val now = LocalTime.now()
        val pieces = if (p.perPiece) item.pieces ?: p.pieceGrams?.takeIf { it > 0 }?.let { item.grams / it } ?: 1.0 else item.pieces
        fa.food.add(DayClock.today(), now, Meal.byTime(now), listOf(p to Portion(item.grams, pieces, null)))
        added += msg.id to index
        onDone("«${p.name}» — в дневнике")
    }
}
