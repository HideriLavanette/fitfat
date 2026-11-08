package app.fitfat.ui.add

import app.fitfat.data.Macros
import app.fitfat.data.Meal
import app.fitfat.data.isNull
import app.fitfat.data.optArray
import app.fitfat.data.optString
import app.fitfat.platform.Prefs
import app.fitfat.time.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Черновик «Добавить» (DESIGN.md, «Добавление еды»): строка, выбранные продукты, день, время и приём.
 * Переживает уход в другое приложение, выгрузку процесса и обновление сборки. Хранится одной строкой JSON
 * в [Prefs] `add_draft` (несколько сотен байт) и стирается, как только записи добавлены в дневник или всё убрано.
 * Продукты — по id: удалённый из базы при восстановлении просто пропускается. Старше суток — не восстанавливается.
 */
class AddDraft private constructor() {
    private val prefs = Prefs("add_draft")

    data class Item(val productId: Long, val grams: Double, val pieces: Double?, val override: Macros?)

    data class State(
        val text: String, val items: List<Item>, val date: LocalDate, val time: LocalTime, val meal: Meal,
        val mealTouched: Boolean, val timeTouched: Boolean,
    ) {
        val isEmpty: Boolean get() = text.isBlank() && items.isEmpty()
    }

    fun save(s: State) {
        if (s.isEmpty) { clear(); return }
        val j = buildJsonObject {
            put("v", 1); put("at", nowMillis())
            put("text", s.text); put("date", s.date.toEpochDay()); put("time", s.time.toSecondOfDay()); put("meal", s.meal.name)
            put("mealTouched", s.mealTouched); put("timeTouched", s.timeTouched)
            putJsonArray("items") {
                s.items.forEach { i ->
                    addJsonObject {
                        put("id", i.productId); put("g", i.grams); put("pcs", i.pieces)
                        val ov = i.override
                        if (ov != null) putJsonArray("ov") { add(ov.kcal); add(ov.protein); add(ov.fat); add(ov.carbs) } else put("ov", JsonNull)
                    }
                }
            }
        }
        prefs.edit { putString(KEY, j.toString()) }
    }

    fun load(): State? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val j = Json.parseToJsonElement(raw).jsonObject
            if (nowMillis() - (j["at"]?.jsonPrimitive?.longOrNull ?: 0L) > MAX_AGE_MS) return null
            val a = j.optArray("items") ?: JsonArray(emptyList())
            State(
                text = j.optString("text"),
                items = a.map { el ->
                    val o = el.jsonObject
                    val ov = o.optArray("ov")
                    Item(
                        o["id"]!!.jsonPrimitive.long, o["g"]!!.jsonPrimitive.double,
                        if (o.isNull("pcs")) null else o["pcs"]!!.jsonPrimitive.double,
                        ov?.let { Macros(it[0].jsonPrimitive.double, it[1].jsonPrimitive.double, it[2].jsonPrimitive.double, it[3].jsonPrimitive.double) },
                    )
                },
                date = LocalDate.ofEpochDay(j["date"]!!.jsonPrimitive.long),
                time = LocalTime.ofSecondOfDay(j["time"]!!.jsonPrimitive.long),
                meal = Meal.of(j.optString("meal")),
                mealTouched = j["mealTouched"]?.jsonPrimitive?.booleanOrNull ?: false,
                timeTouched = j["timeTouched"]?.jsonPrimitive?.booleanOrNull ?: false,
            ).takeIf { !it.isEmpty }
        }.getOrNull()
    }

    fun clear() = prefs.edit { remove(KEY) }

    companion object {
        private const val KEY = "draft"
        private const val MAX_AGE_MS = 24 * 60 * 60_000L
        private val instance: AddDraft by lazy { AddDraft() }
        fun get(): AddDraft = instance
    }
}
