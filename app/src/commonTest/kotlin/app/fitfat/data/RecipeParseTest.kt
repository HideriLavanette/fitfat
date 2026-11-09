package app.fitfat.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeParseTest {
    private fun parse(json: String) = Assistant.parseRecipe(Json.parseToJsonElement(json).jsonObject)

    @Test fun okRecipe() {
        val r = parse("""{"ok":true,"message":"Собрал борщ","name":"борщ","cooked_grams":3000,"steps":"Сварить бульон\nДобавить овощи",
            "ingredients":[
              {"name":"свёкла сырая","existing":null,"category":"veg","grams":300,"kcal":42,"protein":1.5,"fat":0.1,"carbs":8.8,"weight_state":"raw","estimated":false},
              {"name":"Говядина сырая","existing":"Говядина","category":"meat","grams":400.4,"kcal":187,"protein":18.9,"fat":12.4,"carbs":0,"weight_state":"raw","estimated":false},
              {"name":"Соль","existing":null,"category":"sauce","grams":0,"kcal":0,"protein":0,"fat":0,"carbs":0,"weight_state":"none","estimated":true}
            ],"note":""}""")
        assertIs<Assistant.RecipeReply.Ok>(r)
        assertEquals("Борщ", r.name)
        assertEquals(3000.0, r.cookedGrams)
        // Нулевой вес отброшен, название — с большой буквы, граммы — целые
        assertEquals(listOf("Свёкла сырая", "Говядина сырая"), r.ingredients.map { it.name })
        assertEquals(400.0, r.ingredients[1].grams)
        assertEquals("Говядина", r.ingredients[1].existing)
        assertEquals(WEIGHT_RAW, r.ingredients[0].weightState)
        // Калории — из БЖУ: 1,5·4 + 0,1·9 + 8,8·4
        assertEquals(42.1, r.ingredients[0].per100.kcal, 0.01)
        assertTrue(r.steps.contains("бульон"))
    }

    @Test fun noCookedWeight() {
        val r = parse("""{"ok":true,"message":"","name":"","cooked_grams":null,"steps":"",
            "ingredients":[{"name":"Рис сухой","existing":null,"category":"grain","grams":200,"kcal":344,"protein":6.7,"fat":0.7,"carbs":78.9,"weight_state":"dry","estimated":true}],"note":""}""")
        assertIs<Assistant.RecipeReply.Ok>(r)
        assertNull(r.cookedGrams)
        assertEquals("Блюдо", r.name)
        assertTrue(r.ingredients.single().estimated)
    }

    @Test fun notARecipe() {
        val r = parse("""{"ok":false,"message":"Опишите блюдо: из чего оно и сколько чего положили.","name":"","cooked_grams":null,"steps":"","ingredients":[],"note":""}""")
        assertIs<Assistant.RecipeReply.Error>(r)
        assertEquals("Опишите блюдо: из чего оно и сколько чего положили.", r.message)
    }

    @Test fun okWithoutIngredientsIsError() {
        val r = parse("""{"ok":true,"message":"","name":"Борщ","cooked_grams":null,"steps":"","ingredients":[],"note":""}""")
        assertIs<Assistant.RecipeReply.Error>(r)
    }
}
