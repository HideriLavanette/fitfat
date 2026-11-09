package app.fitfat.data

import app.fitfat.time.*
import kotlinx.datetime.LocalDate

/**
 * Правки дневника из чата ассистента (DESIGN.md, «Ассистент — правки дневника», 0.6, 09.11.2026): ассистент предлагает,
 * человек применяет кнопкой и может вернуть как было. Только записи сегодня и вчера — их ассистент видит в сводке.
 */
object DiaryEdits {
    /** Правка, привязанная к записи из базы: [before] — как сейчас, [after] — что станет (null — запись уберётся). */
    data class Planned(val edit: Assistant.Edit, val before: FoodEntry, val after: FoodEntry?)

    /** Время по умолчанию, когда запись переносят в другой приём: 8:00, 13:00, 19:00. */
    private fun minuteOf(m: Meal) = when (m) { Meal.Breakfast -> 8 * 60; Meal.Lunch -> 13 * 60; Meal.Dinner -> 19 * 60 }

    /** Новая порция: КБЖУ — пропорционально записи (как правка граммов в дневнике). Штучные — граммы через вес штуки. */
    private fun scaled(e: FoodEntry, grams: Double?, pieces: Double?): FoodEntry {
        if (grams == null && pieces == null) return e
        val g = grams ?: if (pieces != null && e.pieces != null && e.pieces > 0) e.grams * pieces / e.pieces else e.grams
        val p = if (e.pieces == null) null else pieces ?: if (grams != null && e.grams > 0) e.pieces * grams / e.grams else e.pieces
        val k = if (e.pieces != null && p != null && e.pieces > 0) p / e.pieces else if (e.grams > 0) g / e.grams else 1.0
        return e.copy(grams = g, pieces = p, kcal = e.kcal * k, protein = e.protein * k, fat = e.fat * k, carbs = e.carbs * k)
    }

    /** Правки ассистента → что станет с записями. Записи, которых нет среди [entries] (не сегодня и не вчера), отбрасываются. */
    fun plan(edits: List<Assistant.Edit>, entries: List<FoodEntry>, today: LocalDate): List<Planned> {
        val byId = entries.associateBy { it.id }
        return edits.mapNotNull { ed ->
            val e = byId[ed.entryId] ?: return@mapNotNull null
            when (ed.op) {
                Assistant.Edit.Op.Remove -> Planned(ed, e, null)
                Assistant.Edit.Op.Change -> {
                    var a = scaled(e, ed.grams, ed.pieces)
                    if (ed.meal != null && ed.meal.name != e.meal) a = a.copy(meal = ed.meal.name, minute = minuteOf(ed.meal))
                    if (a == e) null else Planned(ed, e, a)
                }
                Assistant.Edit.Op.Copy -> {
                    val meal = ed.meal ?: Meal.of(e.meal)
                    val minute = if (meal.name == e.meal) e.minute else minuteOf(meal)
                    Planned(ed, e, scaled(e, ed.grams, ed.pieces).copy(id = 0, epochDay = today.toEpochDay(), meal = meal.name, minute = minute))
                }
            }
        }
    }

    /** Применить; результат — как вернуть всё обратно (в обратном порядке). */
    suspend fun apply(food: FoodRepository, list: List<Planned>): suspend () -> Unit {
        val undo = mutableListOf<suspend () -> Unit>()
        list.forEach { p ->
            when (p.edit.op) {
                Assistant.Edit.Op.Remove -> { food.delete(p.before); undo += { food.restore(p.before) } }
                Assistant.Edit.Op.Change -> { food.updateEntry(p.after!!); undo += { food.updateEntry(p.before) } }
                Assistant.Edit.Op.Copy -> {
                    val id = food.restore(p.after!!)
                    undo += { food.delete(p.after.copy(id = id)) }
                }
            }
        }
        return { undo.asReversed().forEach { it() } }
    }
}
