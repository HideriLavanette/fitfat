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

    /** Продукты из базы, похожие на слова строки, — подсказка ассистенту, чтобы брал их, а не выдумывал. */
    fun similar(text: String, products: List<Product>, limit: Int = 20): List<Product> =
        Recognizer.split(text).flatMap { Recognizer.candidates(it, products, 5) }.map { it.product }.distinctBy { it.id }.take(limit)

    /** [activity] — контекст активити: может понадобиться тихо обновить токен Google. */
    suspend fun ask(activity: Context, text: String, leftKcal: Double?, known: List<Product>): Reply {
        if (!available) return Reply.Error("Ассистент не настроен в этой сборке.")
        val google = GoogleAccount.get(activity)
        if (google.account.value == null) return Reply.Error("Чтобы пользоваться ассистентом, подключите аккаунт Google в «Профиле».")
        val token = google.idToken(activity) ?: return Reply.Error("Не получилось подтвердить вход Google. Проверьте интернет или переподключите аккаунт в «Профиле».")
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
        return withContext(Dispatchers.IO) {
            runCatching { post("${BuildConfig.ASSISTANT_URL}/estimate", token, body) }
                .getOrElse { Reply.Error("Нет связи с ассистентом. Проверьте интернет и попробуйте ещё раз.") }
        }
    }

    private fun post(url: String, token: String, body: JSONObject): Reply {
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
            if (code !in 200..299) return Reply.Error(json?.optString("error")?.takeIf { it.isNotBlank() } ?: "Ассистент недоступен ($code).")
            return json?.let(::parse) ?: Reply.Error("Ассистент вернул непонятный ответ — попробуйте ещё раз.")
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
                val a = j.optJSONArray("items") ?: JSONArray()
                val items = (0 until a.length()).mapNotNull { i ->
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
                if (items.isEmpty()) Reply.Answer(reply.ifBlank { "Не нашёл в строке еды, которую можно записать." })
                else Reply.Items(items, reply, j.optString("note").trim())
            }
            else -> Reply.Answer(reply.ifBlank { "Не понял запрос — попробуйте написать иначе." })
        }
    }

    private val CATEGORIES = setOf("grain", "meat", "dairy", "fruit", "veg", "sauce", "drink")
}
