package app.fitfat.data

import app.fitfat.BuildValues
import app.fitfat.platform.Platform
import app.fitfat.platform.Prefs
import app.fitfat.time.Math
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Ассистент ввода еды (DESIGN.md, «Ассистент»): строка из «Добавить» → блюда с примерными КБЖУ, уточнение или ответ.
 * Ходит только на наш сервер-посредник ([BuildValues.ASSISTANT_URL]) с токеном входа Google. Отправляет строку,
 * остаток ккал дня и похожие продукты из базы — дневник целиком не уходит.
 */
object Assistant {
    /** Ассистент есть в этой сборке: адрес сервера задан, а платформа умеет вход Google. */
    val available: Boolean get() = BuildValues.ASSISTANT_URL.isNotBlank() && Platform.supportsGoogleSignIn

    /** Блюдо из ответа. КБЖУ — на 100 г; [existing] — название продукта из базы, если это он. */
    data class Item(
        val name: String, val existing: String?, val category: String,
        val grams: Double, val pieces: Double?, val per100: Macros, val confidence: String,
        /** На какой вес КБЖУ: [WEIGHT_DRY] / [WEIGHT_RAW] / [WEIGHT_COOKED] или null. */
        val weightState: String? = null,
        /** КБЖУ взяты с этикетки на фото и правдоподобны — продукт сохраняется без пометки «оценка» ([SOURCE_LABEL]). */
        val fromLabel: Boolean = false,
    ) {
        val macros: Macros get() = per100 * (grams / 100.0)
        val source: String get() = if (fromLabel) SOURCE_LABEL else SOURCE_AI
    }

    sealed interface Reply {
        data class Items(val items: List<Item>, val text: String, val note: String) : Reply
        data class Question(val text: String, val options: List<String>) : Reply
        data class Answer(val text: String) : Reply
        /** Правка уже записанного («убери вчерашние фетучини») — карточка «Изменения в дневнике». */
        data class Edits(val text: String, val edits: List<Edit>) : Reply
        data class Error(val message: String) : Reply
    }

    private val prefs by lazy { Prefs("assistant") }
    /** v2 (09.11.2026): в «Добавить» теперь уходят и записи дневника за сегодня и вчера — согласие спрашиваем заново. */
    fun consented() = prefs.getBoolean("consent_v2", false)
    fun giveConsent() = prefs.edit { putBoolean("consent_v2", true) }
    /** Согласие для вкладки «Ассистент»: туда уходит больше — сводка дневника и веса. */
    fun chatConsented() = prefs.getBoolean("consent_chat", false)
    fun giveChatConsent() = prefs.edit { putBoolean("consent_chat", true) }

    /** Продукты из базы, похожие на слова строки, — подсказка ассистенту, чтобы брал их, а не выдумывал. */
    fun similar(text: String, products: List<Product>, limit: Int = 20): List<Product> =
        Recognizer.split(text).flatMap { Recognizer.candidates(it, products, 5) }.map { it.product }.distinctBy { it.id }.take(limit)

    /** Токен входа Google или текст ошибки для экрана. */
    private suspend fun token(): Pair<String?, String?> {
        if (!available) return null to (if (Platform.supportsGoogleSignIn) "Ассистент не настроен в этой сборке." else "Ассистент на iPhone появится в следующих версиях — ему нужен вход через Google.")
        val google = GoogleAccount.get()
        if (google.account.value == null) return null to "Чтобы пользоваться ассистентом, войдите через Google в «Профиле»."
        val t = google.idToken() ?: return null to "Не получилось подтвердить вход Google. Проверьте интернет или выйдите и снова войдите в «Профиле»."
        return t to null
    }

    /** POST на сервер: ответ JSON или текст ошибки. */
    private suspend fun call(path: String, token: String, body: JsonObject): Pair<JsonObject?, String?> =
        runCatching { post("${BuildValues.ASSISTANT_URL}$path", token, body) }
            .getOrElse { null to "Нет связи с ассистентом. Проверьте интернет и попробуйте ещё раз." }

    /** [photo] — JPEG еды для оценки по фото (не сохраняется на сервере). */
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    /** [diary] — записи сегодня и вчера с id ([AssistantContext.diary]) — для правок словами. */
    suspend fun ask(text: String, leftKcal: Double?, known: List<Product>, photo: ByteArray? = null, diary: JsonArray? = null): Reply {
        val (token, authErr) = token()
        if (token == null) return Reply.Error(authErr!!)
        val body = buildJsonObject {
            put("text", text)
            if (photo != null) put("photo", kotlin.io.encoding.Base64.encode(photo))
            leftKcal?.let { put("leftKcal", Math.round(it)) }
            diary?.let { put("diary", it) }
            putJsonArray("known") {
                known.forEach { p ->
                    addJsonObject {
                        put("name", p.name); put("kcal", p.kcal); put("protein", p.protein); put("fat", p.fat); put("carbs", p.carbs)
                        put("perPiece", p.perPiece); put("pieceGrams", p.pieceGrams)
                    }
                }
            }
        }
        val (j, err) = call("/estimate", token, body)
        return if (j == null) Reply.Error(err ?: "Ассистент недоступен.") else parse(j)
    }

    /** Ответ во вкладке «Ассистент» (макет 5.2): текст, продукты-предложения с «+», вывод после них. */
    /**
     * Правка дневника из чата: [Op.Remove] — убрать запись, [Op.Change] — новая порция и/или приём,
     * [Op.Copy] — повторить запись на сегодня. null — «не меняется». Применяет человек кнопкой.
     */
    data class Edit(val op: Op, val entryId: Long, val grams: Double?, val pieces: Double?, val meal: Meal?) {
        enum class Op { Remove, Change, Copy }
    }

    sealed interface ChatReply {
        data class Ok(val text: String, val items: List<Item>, val note: String, val edits: List<Edit> = emptyList()) : ChatReply
        data class Error(val message: String) : ChatReply
    }

    /**
     * Сообщение во вкладке «Ассистент». [history] — переписка (role «user»/«assistant», текст), последняя — от человека;
     * [context] — сводка данных ([AssistantContext]).
     */
    suspend fun chat(history: List<Pair<String, String>>, context: JsonObject): ChatReply {
        val (token, authErr) = token()
        if (token == null) return ChatReply.Error(authErr!!)
        val body = buildJsonObject {
            putJsonArray("messages") { history.forEach { (r, t) -> addJsonObject { put("role", r); put("text", t) } } }
            put("context", context)
        }
        val (j, err) = call("/chat", token, body)
        if (j == null) return ChatReply.Error(err ?: "Ассистент недоступен.")
        val text = j.optString("reply").trim()
        val items = parseItems(j.optArray("items") ?: JsonArray(emptyList()))
        val edits = parseEdits(j.optArray("edits") ?: JsonArray(emptyList()))
        if (text.isEmpty() && items.isEmpty() && edits.isEmpty()) return ChatReply.Error("Ассистент вернул пустой ответ — попробуйте ещё раз.")
        return ChatReply.Ok(text, items, j.optString("note").trim(), edits)
    }

    /** Правки из ответа: неизвестная операция, пустой id или бессмысленная порция — отбрасываются. */
    fun parseEdits(a: JsonArray): List<Edit> =
        a.indices.mapNotNull { i ->
            val o = a.optObject(i) ?: return@mapNotNull null
            val op = when (o.optString("op")) { "remove" -> Edit.Op.Remove; "change" -> Edit.Op.Change; "copy" -> Edit.Op.Copy; else -> return@mapNotNull null }
            val id = o.optDouble("entry_id").takeIf { !it.isNaN() && it > 0 }?.toLong() ?: return@mapNotNull null
            val grams = if (o.isNull("grams")) null else o.optDouble("grams").takeIf { !it.isNaN() && it > 0 }?.coerceAtMost(5000.0)
            val pieces = if (o.isNull("pieces")) null else o.optDouble("pieces").takeIf { !it.isNaN() && it > 0 }?.coerceAtMost(100.0)
            val meal = if (o.isNull("meal")) null else Meal.entries.firstOrNull { it.label == o.optString("meal") }
            // «Поменять» без новой порции и приёма — нечего менять
            if (op == Edit.Op.Change && grams == null && pieces == null && meal == null) return@mapNotNull null
            Edit(op, id, grams, pieces, meal)
        }.distinctBy { it.op to it.entryId }

    /** Ингредиент рецепта: вес — как кладут (сырой / сухой), КБЖУ — на 100 г в этом виде; [estimated] — вес предположил ассистент. */
    data class RecipeIngredient(
        val name: String, val existing: String?, val category: String, val grams: Double,
        val per100: Macros, val weightState: String?, val estimated: Boolean,
    )

    sealed interface RecipeReply {
        /** [cookedGrams] — вес готового, если его назвали; [steps] — шаги, если их описали. */
        data class Ok(
            val name: String, val cookedGrams: Double?, val steps: String,
            val ingredients: List<RecipeIngredient>, val message: String, val note: String,
        ) : RecipeReply
        data class Error(val message: String) : RecipeReply
    }

    /** Рецепт из описания своими словами (DESIGN.md, «Блюдо из описания»): уходит описание и похожие продукты из базы. */
    suspend fun recipe(text: String, known: List<Product>): RecipeReply {
        val (token, authErr) = token()
        if (token == null) return RecipeReply.Error(authErr!!)
        val body = buildJsonObject {
            put("text", text)
            putJsonArray("known") {
                known.forEach { p ->
                    addJsonObject {
                        put("name", p.name); put("kcal", p.kcal); put("protein", p.protein); put("fat", p.fat); put("carbs", p.carbs)
                        put("perPiece", p.perPiece); put("pieceGrams", p.pieceGrams)
                    }
                }
            }
        }
        val (j, err) = call("/recipe", token, body)
        return if (j == null) RecipeReply.Error(err ?: "Ассистент недоступен.") else parseRecipe(j)
    }

    /** Ответ /recipe → [RecipeReply]. Ингредиенты без названия или веса отбрасываются, калории — из БЖУ. Открыто для тестов. */
    fun parseRecipe(j: JsonObject): RecipeReply {
        val message = j.optString("message").trim()
        if (!j.optBoolean("ok")) return RecipeReply.Error(message.ifBlank { "Опишите блюдо: из чего оно и сколько чего положили." })
        val a = j.optArray("ingredients") ?: JsonArray(emptyList())
        val ings = a.indices.mapNotNull { i ->
            val o = a.optObject(i) ?: return@mapNotNull null
            val name = o.optString("name").trim()
            val grams = o.optDouble("grams")
            if (name.isEmpty() || grams.isNaN() || grams <= 0) return@mapNotNull null
            val p = o.optDouble("protein", 0.0).coerceAtLeast(0.0)
            val f = o.optDouble("fat", 0.0).coerceAtLeast(0.0)
            val u = o.optDouble("carbs", 0.0).coerceAtLeast(0.0)
            RecipeIngredient(
                name = name.replaceFirstChar { it.uppercase() },
                existing = if (o.isNull("existing")) null else o.optString("existing").takeIf { it.isNotBlank() },
                category = o.optString("category").takeIf { it in CATEGORIES } ?: "grain",
                grams = Math.round(grams.coerceAtMost(20_000.0)).toDouble(),
                per100 = Macros(p * 4 + f * 9 + u * 4, p, f, u),
                weightState = o.optString("weight_state").takeIf { it in WeightState.all },
                estimated = o.optBoolean("estimated"),
            )
        }
        if (ings.isEmpty()) return RecipeReply.Error(message.ifBlank { "Не нашёл в описании ингредиентов — напишите, из чего блюдо и сколько чего положили." })
        val cooked = if (j.isNull("cooked_grams")) null else j.optDouble("cooked_grams").takeIf { !it.isNaN() && it > 0 }
        return RecipeReply.Ok(
            name = j.optString("name").trim().replaceFirstChar { it.uppercase() }.ifBlank { "Блюдо" },
            cookedGrams = cooked?.let { Math.round(it.coerceAtMost(50_000.0)).toDouble() },
            steps = j.optString("steps").trim(), ingredients = ings, message = message, note = j.optString("note").trim(),
        )
    }

    private suspend fun post(url: String, token: String, body: JsonObject): Pair<JsonObject?, String?> {
        val resp = Http.client.post(url) {
            header("Authorization", "Bearer $token")
            header("X-Device", app.fitfat.platform.Device.id)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
            timeout { connectTimeoutMillis = 15_000; requestTimeoutMillis = 90_000; socketTimeoutMillis = 90_000 }
        }
        val raw = resp.bodyAsText()
        val json = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
        if (resp.status.value == 402) Access.refresh()
        if (!resp.status.isSuccess()) return null to (json?.optString("error")?.takeIf { it.isNotBlank() } ?: "Ассистент недоступен (${resp.status.value}).")
        return if (json != null) json to null else null to "Ассистент вернул непонятный ответ — попробуйте ещё раз."
    }

    /** Ответ сервера → [Reply]. Открыто для тестов. */
    fun parse(j: JsonObject): Reply {
        val reply = j.optString("reply").trim()
        return when (j.optString("kind")) {
            "question" -> {
                val opts = j.optArray("options")?.let { a -> a.indices.map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
                Reply.Question(j.optString("question").ifBlank { reply }, opts)
            }
            "items" -> {
                val items = parseItems(j.optArray("items") ?: JsonArray(emptyList()))
                if (items.isEmpty()) Reply.Answer(reply.ifBlank { "Не нашёл в строке еды, которую можно записать." })
                else Reply.Items(items, reply, j.optString("note").trim())
            }
            "edits" -> {
                val edits = parseEdits(j.optArray("edits") ?: JsonArray(emptyList()))
                if (edits.isEmpty()) Reply.Answer(reply.ifBlank { "Не нашёл такую запись за сегодня и вчера." })
                else Reply.Edits(reply, edits)
            }
            else -> Reply.Answer(reply.ifBlank { "Не понял запрос — попробуйте написать иначе." })
        }
    }

    /** Блюда из ответа: без названия или веса — отбрасываются, калории — из БЖУ. */
    internal fun parseItemsForTest(a: JsonArray) = parseItems(a)

    private fun parseItems(a: JsonArray): List<Item> =
        a.indices.mapNotNull { i ->
            val o = a.optObject(i) ?: return@mapNotNull null
            val grams = o.optDouble("grams")
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
                pieces = if (o.isNull("pieces")) null else o.optDouble("pieces").takeIf { !it.isNaN() && it > 0 },
                // Калории — из БЖУ, как у всех продуктов приложения
                per100 = Macros(p * 4 + f * 9 + u * 4, p, f, u),
                confidence = o.optString("confidence").ifBlank { "medium" },
                weightState = o.optString("weight_state").takeIf { it in WeightState.all },
                fromLabel = o.optBoolean("from_label", false) && labelPlausible(o.optDouble("kcal"), p, f, u),
            )
        }

    /**
     * Вторая проверка цифр с этикетки (первая — у ассистента): БЖУ не больше 100 г на 100 г, а калории с этикетки
     * сходятся с БЖУ (4·Б + 9·Ж + 4·У) с точностью 20 % (не меньше 25 ккал — округления, клетчатка, полиолы).
     * Не сходится — значит, на этикетке ошибка или цифры прочитаны неверно, и продукт остаётся оценкой.
     */
    internal fun labelPlausible(kcal: Double, p: Double, f: Double, u: Double): Boolean {
        if (kcal.isNaN() || kcal < 0) return false
        if (p + f + u > 101) return false
        val calc = p * 4 + f * 9 + u * 4
        return kotlin.math.abs(kcal - calc) <= maxOf(25.0, calc * 0.2)
    }

    private val CATEGORIES = setOf("grain", "meat", "dairy", "fruit", "veg", "sauce", "drink")
}
