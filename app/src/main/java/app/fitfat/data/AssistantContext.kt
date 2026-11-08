package app.fitfat.data

import app.fitfat.FitfatApp
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Сводка для вкладки «Ассистент» (DESIGN.md, «Ассистент — чат»): что ассистент «видит». Без имени, почты и заметок:
 * профиль (пол, возраст, рост, цель, целевой вес), нормы и съеденное сегодня и вчера с продуктами, средние за 7 дней,
 * вес за 3 недели и его тренд, частые продукты базы с КБЖУ на 100 г.
 */
object AssistantContext {
    private fun r(v: Double, k: Int = 0): Double { val m = Math.pow(10.0, k.toDouble()); return Math.round(v * m) / m }
    private fun macros(m: Macros) = JSONObject().put("kcal", r(m.kcal)).put("protein", r(m.protein, 1)).put("fat", r(m.fat, 1)).put("carbs", r(m.carbs, 1))

    suspend fun build(app: FitfatApp): JSONObject {
        val today = DayClock.today()
        val profile = ProfileStore(app).load()
        val norms = app.norms
        val goal = profile?.goal
        val entries = app.food.range(Compensation.weekStart(today.minusDays(7)), today).first()
        val byDay = entries.groupBy { it.epochDay }
        val comp = Compensation.of(norms, entries, today.minusDays(7), today, today, goal)

        fun day(d: LocalDate): JSONObject {
            val list = byDay[d.toEpochDay()].orEmpty().sortedBy { it.minute }
            val type = norms.dayType(d)
            val limit = Compensation.apply(norms.norms(type), comp.cut(d))
            val eaten = list.fold(Macros()) { a, e -> a + e.macros }
            return JSONObject()
                .put("date", d.toString()).put("dayType", type.label)
                .put("norm", macros(limit)).put("eaten", macros(eaten))
                .put("left", macros(Macros(limit.kcal - eaten.kcal, limit.protein - eaten.protein, limit.fat - eaten.fat, limit.carbs - eaten.carbs)))
                .put("foods", JSONArray().apply {
                    list.forEach { e ->
                        put(JSONObject().put("meal", Meal.of(e.meal).label).put("name", e.name).put("grams", r(e.grams))
                            .put("kcal", r(e.kcal)).put("protein", r(e.protein, 1)).put("fat", r(e.fat, 1)).put("carbs", r(e.carbs, 1)))
                    }
                })
        }

        // Средние за 7 прошедших дней — только по дням с записями
        val past = (1..7).map { today.minusDays(it.toLong()) }
        val withFood = past.filter { !byDay[it.toEpochDay()].isNullOrEmpty() }
        val week = JSONObject().put("daysWithRecords", withFood.size)
        if (withFood.isNotEmpty()) {
            val sum = withFood.fold(Macros()) { a, d -> a + byDay[d.toEpochDay()]!!.fold(Macros()) { b, e -> b + e.macros } }
            val normSum = withFood.fold(Macros()) { a, d -> a + Compensation.apply(norms.norms(norms.dayType(d)), comp.cut(d)) }
            week.put("avgEaten", macros(sum * (1.0 / withFood.size))).put("avgNorm", macros(normSum * (1.0 / withFood.size)))
        }

        // Вес за 3 недели и тренд (наклон прямой, кг в неделю)
        val weights = app.weights.entries.first().filter { it.epochDay >= today.minusDays(21).toEpochDay() }
        val weight = JSONObject().put("records", JSONArray().apply {
            weights.forEach { put(JSONObject().put("date", LocalDate.ofEpochDay(it.epochDay).toString()).put("kg", it.kg)) }
        })
        if (weights.size >= 3) {
            val xs = weights.map { it.epochDay.toDouble() }; val ys = weights.map { it.kg }
            val mx = xs.average(); val my = ys.average()
            val slope = xs.zip(ys).sumOf { (x, y) -> (x - mx) * (y - my) } / xs.sumOf { (it - mx) * (it - mx) }.coerceAtLeast(1e-9)
            weight.put("trendKgPerWeek", r(slope * 7, 2))
        }

        val frequent = app.food.allProducts.first().filter { it.useCount > 0 }.sortedByDescending { it.useCount }.take(20)
        return JSONObject()
            .put("today", today.toString())
            .put("profile", JSONObject().apply {
                profile?.let { p ->
                    put("sex", if (p.sex == Sex.Female) "женский" else "мужской").put("age", p.age).put("heightCm", p.heightCm)
                    put("goal", p.goal.label).put("targetKg", p.targetKg ?: JSONObject.NULL).put("trains", p.trains)
                }
            })
            .put("todayDay", day(today))
            .put("yesterday", day(today.minusDays(1)))
            .put("last7days", week)
            .put("weight", weight)
            .put("frequentFoods", JSONArray().apply {
                frequent.forEach { p ->
                    put(JSONObject().put("name", p.name).put("per", if (p.perPiece) "1 шт" else "100 г")
                        .put("kcal", r(p.kcal)).put("protein", r(p.protein, 1)).put("fat", r(p.fat, 1)).put("carbs", r(p.carbs, 1))
                        .put("usualPortion", if (p.perPiece) "${p.defaultGrams.roundToInt()} шт" else "${p.defaultGrams.roundToInt()} г"))
                }
            })
    }
}
