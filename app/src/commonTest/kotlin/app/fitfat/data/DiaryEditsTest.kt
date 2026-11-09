package app.fitfat.data

import app.fitfat.time.toEpochDay
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryEditsTest {
    private val today = LocalDate(2026, 11, 9)
    private val yesterday = LocalDate(2026, 11, 8)
    private val buckwheat = FoodEntry(id = 1, epochDay = yesterday.toEpochDay(), minute = 13 * 60 + 30, meal = "Lunch", productId = 10,
        name = "Гречка варёная", category = "grain", grams = 200.0, pieces = null, kcal = 220.0, protein = 8.0, fat = 2.0, carbs = 42.0)
    private val cutlet = FoodEntry(id = 2, epochDay = today.toEpochDay(), minute = 19 * 60, meal = "Dinner", productId = 11,
        name = "Котлета", category = "meat", grams = 80.0, pieces = 1.0, kcal = 200.0, protein = 14.0, fat = 14.0, carbs = 5.0)
    private val oats = FoodEntry(id = 3, epochDay = yesterday.toEpochDay(), minute = 8 * 60 + 15, meal = "Breakfast", productId = 12,
        name = "Овсянка", category = "grain", grams = 60.0, pieces = null, kcal = 210.0, protein = 7.0, fat = 4.0, carbs = 36.0)
    private val all = listOf(buckwheat, cutlet, oats)

    private fun edits(json: String) = Assistant.parseEdits(Json.parseToJsonElement(json).jsonArray)

    @Test fun parseDropsJunk() {
        val e = edits("""[
            {"op":"remove","entry_id":1,"grams":null,"pieces":null,"meal":null},
            {"op":"explode","entry_id":1,"grams":null,"pieces":null,"meal":null},
            {"op":"change","entry_id":2,"grams":null,"pieces":null,"meal":null},
            {"op":"change","entry_id":0,"grams":100,"pieces":null,"meal":null},
            {"op":"copy","entry_id":3,"grams":null,"pieces":null,"meal":"Завтрак"}
        ]""")
        assertEquals(listOf(Assistant.Edit.Op.Remove, Assistant.Edit.Op.Copy), e.map { it.op })
        assertEquals(Meal.Breakfast, e[1].meal)
    }

    @Test fun changeGramsScalesMacros() {
        val p = DiaryEdits.plan(edits("""[{"op":"change","entry_id":1,"grams":150,"pieces":null,"meal":null}]"""), all, today).single()
        assertEquals(150.0, p.after!!.grams)
        assertEquals(165.0, p.after!!.kcal, 0.01)
        assertEquals(buckwheat.minute, p.after!!.minute)
    }

    @Test fun changePiecesScalesGrams() {
        val p = DiaryEdits.plan(edits("""[{"op":"change","entry_id":2,"grams":null,"pieces":2,"meal":null}]"""), all, today).single()
        assertEquals(2.0, p.after!!.pieces)
        assertEquals(160.0, p.after!!.grams, 0.01)
        assertEquals(400.0, p.after!!.kcal, 0.01)
    }

    @Test fun moveMealSetsTypicalTime() {
        val p = DiaryEdits.plan(edits("""[{"op":"change","entry_id":1,"grams":null,"pieces":null,"meal":"Ужин"}]"""), all, today).single()
        assertEquals("Dinner", p.after!!.meal)
        assertEquals(19 * 60, p.after!!.minute)
        assertEquals(buckwheat.kcal, p.after!!.kcal)
    }

    @Test fun copyGoesToTodaySameTime() {
        val p = DiaryEdits.plan(edits("""[{"op":"copy","entry_id":3,"grams":null,"pieces":null,"meal":null}]"""), all, today).single()
        assertEquals(0L, p.after!!.id)
        assertEquals(today.toEpochDay(), p.after!!.epochDay)
        assertEquals(oats.minute, p.after!!.minute)
        assertEquals("Breakfast", p.after!!.meal)
    }

    @Test fun removeAndUnknownIds() {
        val list = DiaryEdits.plan(edits("""[
            {"op":"remove","entry_id":2,"grams":null,"pieces":null,"meal":null},
            {"op":"remove","entry_id":99,"grams":null,"pieces":null,"meal":null}
        ]"""), all, today)
        assertEquals(1, list.size)
        assertNull(list[0].after)
    }

    @Test fun estimateEditsReply() {
        val ok = Assistant.parse(Json.parseToJsonElement("""{"kind":"edits","reply":"Уберу фетучини","question":"","options":[],"items":[],"note":"",
            "edits":[{"op":"remove","entry_id":1,"grams":null,"pieces":null,"meal":null}]}""").jsonObject)
        assertTrue(ok is Assistant.Reply.Edits && ok.edits.single().entryId == 1L)
        // Правок не осталось (выдуманный id отброшен разбором) — честный ответ
        val none = Assistant.parse(Json.parseToJsonElement("""{"kind":"edits","reply":"","question":"","options":[],"items":[],"note":"",
            "edits":[{"op":"remove","entry_id":0,"grams":null,"pieces":null,"meal":null}]}""").jsonObject)
        assertTrue(none is Assistant.Reply.Answer)
    }

    @Test fun noOpChangeDropped() {
        val list = DiaryEdits.plan(edits("""[{"op":"change","entry_id":1,"grams":200,"pieces":null,"meal":"Обед"}]"""), all, today)
        assertTrue(list.isEmpty())
    }
}
