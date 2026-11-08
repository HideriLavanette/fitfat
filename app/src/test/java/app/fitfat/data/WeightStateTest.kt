package app.fitfat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightStateTest {
    @Test fun cooked() {
        assertEquals(WEIGHT_COOKED, WeightState.guess("Гречка варёная"))
        assertEquals(WEIGHT_COOKED, WeightState.guess("Картофель отварной"))
        assertEquals(WEIGHT_COOKED, WeightState.guess("Куриная грудка гриль"))
        assertEquals(WEIGHT_COOKED, WeightState.guess("Овсянка на воде"))
    }

    @Test fun dryAndRaw() {
        assertEquals(WEIGHT_DRY, WeightState.guess("Рис сухой"))
        assertEquals(WEIGHT_DRY, WeightState.guess("Овсяные хлопья"))
        assertEquals(WEIGHT_RAW, WeightState.guess("Говядина сырая"))
    }

    @Test fun cheeseIsNotRaw_breadIsNothing() {
        // «Сыр твёрдый» — не «сырой»
        assertNull(WeightState.guess("Сыр твёрдый"))
        assertNull(WeightState.guess("Хлеб пшеничный"))
        assertNull(WeightState.guess("Сухарики"))
        assertNull(WeightState.guess("Яйцо варёное"))
    }
}
