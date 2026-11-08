package app.fitfat.ui.add

import android.content.Context
import androidx.core.content.edit
import app.fitfat.data.Macros
import app.fitfat.data.Meal
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Черновик «Добавить» (DESIGN.md, «Добавление еды»): строка, выбранные продукты, день, время и приём.
 * Переживает уход в другое приложение, выгрузку процесса и обновление сборки. Хранится одной строкой JSON
 * в SharedPreferences (несколько сотен байт) и стирается, как только записи добавлены в дневник или всё убрано.
 * Продукты — по id: удалённый из базы при восстановлении просто пропускается. Старше суток — не восстанавливается.
 */
class AddDraft private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("add_draft", Context.MODE_PRIVATE)

    data class Item(val productId: Long, val grams: Double, val pieces: Double?, val override: Macros?)

    data class State(
        val text: String, val items: List<Item>, val date: LocalDate, val time: LocalTime, val meal: Meal,
        val mealTouched: Boolean, val timeTouched: Boolean,
    ) {
        val isEmpty: Boolean get() = text.isBlank() && items.isEmpty()
    }

    fun save(s: State) {
        if (s.isEmpty) { clear(); return }
        val j = JSONObject()
            .put("v", 1).put("at", System.currentTimeMillis())
            .put("text", s.text).put("date", s.date.toEpochDay()).put("time", s.time.toSecondOfDay()).put("meal", s.meal.name)
            .put("mealTouched", s.mealTouched).put("timeTouched", s.timeTouched)
            .put("items", JSONArray().apply {
                s.items.forEach { i ->
                    put(JSONObject().put("id", i.productId).put("g", i.grams).put("pcs", i.pieces ?: JSONObject.NULL)
                        .put("ov", i.override?.let { JSONArray().put(it.kcal).put(it.protein).put(it.fat).put(it.carbs) } ?: JSONObject.NULL))
                }
            })
        prefs.edit { putString(KEY, j.toString()) }
    }

    fun load(): State? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val j = JSONObject(raw)
            if (System.currentTimeMillis() - j.optLong("at") > MAX_AGE_MS) return null
            val a = j.optJSONArray("items") ?: JSONArray()
            State(
                text = j.optString("text"),
                items = (0 until a.length()).map { k ->
                    val o = a.getJSONObject(k)
                    val ov = o.optJSONArray("ov")
                    Item(
                        o.getLong("id"), o.getDouble("g"),
                        if (o.isNull("pcs")) null else o.getDouble("pcs"),
                        ov?.let { Macros(it.getDouble(0), it.getDouble(1), it.getDouble(2), it.getDouble(3)) },
                    )
                },
                date = LocalDate.ofEpochDay(j.getLong("date")),
                time = LocalTime.ofSecondOfDay(j.getLong("time")),
                meal = Meal.of(j.optString("meal")),
                mealTouched = j.optBoolean("mealTouched"), timeTouched = j.optBoolean("timeTouched"),
            ).takeIf { !it.isEmpty }
        }.getOrNull()
    }

    fun clear() = prefs.edit { remove(KEY) }

    companion object {
        private const val KEY = "draft"
        private const val MAX_AGE_MS = 24 * 60 * 60_000L
        @Volatile private var instance: AddDraft? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: AddDraft(context.applicationContext).also { instance = it } }
    }
}
