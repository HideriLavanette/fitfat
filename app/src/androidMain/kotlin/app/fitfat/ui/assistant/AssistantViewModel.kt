package app.fitfat.ui.assistant

import app.fitfat.time.*
import app.fitfat.time.Fmt
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fitfat.FitfatApp
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
)

/**
 * Вкладка «Ассистент» (макет 5.2): переписка живёт, пока открыто приложение. В запрос — последние сообщения
 * и свежая сводка данных ([AssistantContext]); «+» у предложения записывает его в дневник сейчас.
 */
class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val fa = app as FitfatApp
    val messages = mutableStateListOf<ChatMsg>()
    var busy by mutableStateOf(false)
        private set
    /** Предложения, уже добавленные в дневник: id сообщения → номера продуктов. */
    val added = mutableStateListOf<Pair<Long, Int>>()
    private var nextId = 1L

    fun send(activity: Context, text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        messages += ChatMsg(nextId++, true, t)
        busy = true
        viewModelScope.launch {
            val history = messages.filter { !it.error }.takeLast(12).map { m ->
                (if (m.fromUser) "user" else "assistant") to
                    (m.text + m.items.joinToString("") { "\n— ${it.name}" } + (if (m.note.isNotBlank()) "\n${m.note}" else "")).trim()
            }
            val ctx = runCatching { AssistantContext.build(fa) }.getOrNull()
            val reply = if (ctx == null) Assistant.ChatReply.Error("Не получилось собрать данные дневника.")
                else Assistant.chat(activity, history, ctx)
            messages += when (reply) {
                is Assistant.ChatReply.Ok -> ChatMsg(nextId++, false, reply.text, reply.items, reply.note)
                is Assistant.ChatReply.Error -> ChatMsg(nextId++, false, reply.message, error = true)
            }
            busy = false
        }
    }

    /** Повторить последний вопрос после ошибки. */
    fun retry(activity: Context) {
        val last = messages.lastOrNull { it.fromUser } ?: return
        messages.removeAll { it.error }
        messages.remove(last)
        send(activity, last.text)
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
