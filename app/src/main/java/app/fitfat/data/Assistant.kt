package app.fitfat.data

import android.content.Context
import androidx.core.content.edit
import app.fitfat.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ассистент ввода еды (DESIGN.md, «Ассистент»): строка из «Добавить» → блюда с примерными КБЖУ, уточнение или ответ.
 * Ходит только на наш сервер-посредник ([BuildConfig.ASSISTANT_URL]) с токеном входа Google. Отправляет строку,
 * остаток ккал дня и похожие продукты из базы — дневник целиком не уходит.
 */
object Assistant {
    /** Ассистент есть в этой сборке (адрес сервера задан). */
    val available: Boolean get() = BuildConfig.ASSISTANT_URL.isNotBlank()

    /** Блюдо из ответа. КБЖУ — на 100 г; [existing] — название продукта из базы, если это он. */
    data class Item(
        val name: String, val existing: String?, val category: String,
        val grams: Double, val pieces: Double?, val per100: Macros, val confidence: String,
    ) {
        val macros: Macros get() = per100 * (grams / 100.0)
    }

    sealed interface Reply {
        data class Items(val items: List<Item>, val text: String, val note: String) : Reply
        data class Question(val text: String, val options: List<String>) : Reply
        data class Answer(val text: String) : Reply
        data class Error(val message: String) : Reply
    }

    private fun prefs(c: Context) = c.getSharedPreferences("assistant", Context.MODE_PRIVATE)
    fun consented(c: Context) = prefs(c).getBoolean("consent", false)
    fun giveConsent(c: Context) = prefs(c).edit { putBoolean("consent", true) }
    /** Согласие для вкладки «Ассистент»: туда уходит больше — сводка дневника и веса. */
    fun chatConsented(c: Context) = prefs(c).getBoolean("consent_chat", false)
    fun giveChatConsent(c: Context) = prefs(c).edit { putBoolean("consent_chat", true) }

    /** Продукты из базы, похожие на слова строки, — подсказка ассистенту, чтобы брал их, а не выдумывал. */
    fun similar(text: String, products: List<Product>, limit: Int = 20): List<Product> =
        Recognizer.split(text).flatMap { Recognizer.candidates(it, products, 5) }.map { it.product }.distinctBy { it.id }.take(limit)

    /** Токен входа Google или текст ошибки для экрана. */
    private suspend fun token(activity: Context): Pair<String?, String?> {
        if (!available) return null to "Ассистент не настроен в этой сборке."
        val google = GoogleAccount.get(activity)
        if (google.account.value == null) return null to "Чтобы пользоваться ассистентом, подключите аккаунт Google в «Профиле»."
        val t = google.idToken(activity) ?: return null to "Не получилось подтвердить вход Google. Проверьте интернет или переподключите аккаунт в «Профиле»."
        return t to null
    }

    /** POST на сервер: ответ JSON или текст ошибки. */
    private suspend fun call(path: String, token: String, body: JSONObject): Pair<JSONObject?, String?> = withContext(Dispatchers.IO) {
        runCatching { post("${BuildConfig.ASSISTANT_URL}$path", token, body) }
            .getOrElse { null to "Нет связи с ассистентом. Проверьте интернет и попробуйте ещё раз." }
    }

    /** [activity] — контекст активити: может понадобиться тихо обновить токен Google. */
    suspend fun ask(activity: Context, text: String, leftKcal: Double?, known: List<Product>): Reply {
        val (token, authErr) = token(activity)
        if (token == null) return Reply.Error(authErr!!)
        val body = JSONObject().apply {
            put("text", text)
            leftKcal?.let { put("leftKcal", Math.round(it)) }
            put("known", JSONArray().apply {
                known.forEach { p ->
                    put(JSONObject().put("name", p.name).put("kcal", p.kcal).put("protein", p.protein).put("fat", p.fat).put("carbs", p.carbs)
                        .put("perPiece", p.perPiece).put("pieceGrams", p.pieceGrams ?: JSONObject.NULL))
                }
            })
        }
        val (j, err) = call("/estimate", token, body)
        return if (j == null) Reply.Error(err ?: "Ассистент недоступен.") else parse(j)
    }

    /** Ответ во вкладке «Ассистент» (макет 5.2): текст, продукты-предложения с «+», вывод после них. */
    sealed interface ChatReply {
        data class Ok(val text: String, val items: List<Item>, val note: String) : ChatReply
        data class Error(val message: String) : ChatReply
    }

    /**
     * Сообщение во вкладке «Ассистент». [history] — переписка (role «user»/«assistant», текст), последняя — от человека;
     * [context] — сводка данных ([AssistantContext]).
     */
    suspend fun chat(activity: Context, history: List<Pair<String, String>>, context: JSONObject): ChatReply {
        val (token, authErr) = token(activity)
        if (token == null) return ChatReply.Error(authErr!!)
        val body = JSONObject()
            .put("messages", JSONArray().apply { history.forEach { (r, t) -> put(JSONObject().put("role", r).put("text", t)) } })
            .put("context", context)
        val (j, err) = call("/chat", token, body)
        if (j == null) return ChatReply.Error(err ?: "Ассистент недоступен.")
        val text = j.optString("reply").trim()
        val items = parseItems(j.optJSONArray("items") ?: JSONArray())
        if (text.isEmpty() && items.isEmpty()) return ChatReply.Error("Ассистент вернул пустой ответ — попробуйте ещё раз.")
        return ChatReply.Ok(text, items, j.optString("note").trim())
    }

    private fun post(url: String, token: String, body: JSONObject): Pair<JSONObject?, String?> {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15_000; readTimeout = 90_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val raw = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (code !in 200..299) return null to (json?.optString("error")?.takeIf { it.isNotBlank() } ?: "Ассистент недоступен ($code).")
            return if (json != null) json to null else null to "Ассистент вернул непонятный ответ — попробуйте ещё раз."
        } finally { c.disconnect() }
    }

    /** Ответ сервера → [Reply]. Открыто для тестов. */
    fun parse(j: JSONObject): Reply {
        val reply = j.optString("reply").trim()
        return when (j.optString("kind")) {
            "question" -> {
                val opts = j.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
                Reply.Question(j.optString("question").ifBlank { reply }, opts)
            }
            "items" -> {
                val items = parseItems(j.optJSONArray("items") ?: JSONArray())
                if (items.isEmpty()) Reply.Answer(reply.ifBlank { "Не нашёл в строке еды, которую можно записать." })
                else Reply.Items(items, reply, j.optString("note").trim())
            }
            else -> Reply.Answer(reply.ifBlank { "Не понял запрос — попробуйте написать иначе." })
        }
    }

    /** Блюда из ответа: без названия или веса — отбрасываются, калории — из БЖУ. */
    private fun parseItems(a: JSONArray): List<Item> =
                (0 until a.length()).mapNotNull { i ->
                    val o = a.optJSONObject(i) ?: return@mapNotNull null
                    val grams = o.optDouble("grams", Double.NaN)
                    val name = o.optString("name").trim()
                    if (name.isEmpty() || grams.isNaN() || grams <= 0) return@mapNotNull null
                    val p = o.optDouble("protein", 0.0).coerceAtLeast(0.0)
                    val f = o.optDouble("fat", 0.0).coerceAtLeast(0.0)
                    val u = o.optDouble("carbs", 0.0).coerceAtLeast(0.0)
                    Item(
                        name = name.replaceFirstChar { it.uppercase() },
                        existing = if (o.isNull("existing")) null else o.optString("existing").takeIf { it.isNotBlank() },
                        category = o.optString("category").takeIf { it in CATEGORIES } ?: "grain",
                        grams = grams.coerceAtMost(5000.0),
                        pieces = if (o.isNull("pieces")) null else o.optDouble("pieces", Double.NaN).takeIf { !it.isNaN() && it > 0 },
                        // Калории — из БЖУ, как у всех продуктов приложения
                        per100 = Macros(p * 4 + f * 9 + u * 4, p, f, u),
                        confidence = o.optString("confidence", "medium"),
                    )
                }

    private val CATEGORIES = setOf("grain", "meat", "dairy", "fruit", "veg", "sauce", "drink")
}
