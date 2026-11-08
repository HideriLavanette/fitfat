package app.fitfat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizerTest {
    private fun product(name: String, aliases: String = "") = Product(name = name, category = "meat", kcal = 0.0, protein = 0.0, fat = 0.0, carbs = 0.0, aliases = aliases)
    private fun fragment(text: String) = Recognizer.split(text).first()

    @Test fun dishWithIngredientWord_isPartialMatch() {
        // «Говядина варёная» нашлась по «говядиной», но «манты» она не покрывает — нужен ассистент
        assertEquals(listOf("манты"), Recognizer.uncovered(fragment("манты с говядиной 5шт 647гр"), product("Говядина варёная")))
    }

    @Test fun plainProduct_isFullMatch() {
        assertTrue(Recognizer.uncovered(fragment("гречка 200"), product("Гречка варёная")).isEmpty())
        assertTrue(Recognizer.uncovered(fragment("гречки 200"), product("Гречка варёная")).isEmpty())
    }

    @Test fun aliasesCount() {
        assertTrue(Recognizer.uncovered(fragment("грудка гриль 150"), product("Куриная грудка", "грудка гриль")).isEmpty())
    }
}
