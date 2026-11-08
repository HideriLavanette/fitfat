package app.fitfat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DayClockTest {
    private val s = 7 * 60

    @Test fun beforeStart_isPreviousDay() {
        assertEquals(LocalDate.of(2026, 11, 8), DayClock.dayOf(LocalDateTime.of(2026, 11, 9, 2, 30), s))
        assertEquals(LocalDate.of(2026, 11, 8), DayClock.dayOf(LocalDateTime.of(2026, 11, 9, 6, 59), s))
        assertEquals(LocalDate.of(2026, 11, 9), DayClock.dayOf(LocalDateTime.of(2026, 11, 9, 7, 0), s))
        assertEquals(LocalDate.of(2026, 11, 9), DayClock.dayOf(LocalDateTime.of(2026, 11, 9, 23, 59), s))
    }

    @Test fun midnightStart_isCalendar() {
        assertEquals(LocalDate.of(2026, 11, 9), DayClock.dayOf(LocalDateTime.of(2026, 11, 9, 0, 0), 0))
    }

    @Test fun order_nightAfterEvening() {
        assertTrue(DayClock.order(2 * 60, s) > DayClock.order(23 * 60, s))
        assertTrue(DayClock.order(8 * 60, s) < DayClock.order(13 * 60, s))
        assertEquals(0, DayClock.order(7 * 60, s))
    }

    @Test fun night() {
        assertTrue(DayClock.isNight(LocalTime.of(3, 0), s))
        assertFalse(DayClock.isNight(LocalTime.of(7, 0), s))
    }

    @Test fun nightMeal_isDinner() {
        // Meal.byTime читает текущее начало дня — по умолчанию 07:00
        assertEquals(Meal.Dinner, Meal.byTime(LocalTime.of(2, 0)))
        assertEquals(Meal.Breakfast, Meal.byTime(LocalTime.of(8, 0)))
    }
}

class PortionsTest {
    @Test fun roundTrip() {
        val list = listOf(NamedPortion("тарелка", 300.0), NamedPortion("1 штука", 120.0))
        val p = Product(name = "x", category = "grain", kcal = 0.0, protein = 0.0, fat = 0.0, carbs = 0.0, portions = encodePortions(list))
        assertEquals(list, p.namedPortions)
    }
    @Test fun skipsBad() {
        val p = Product(name = "x", category = "grain", kcal = 0.0, protein = 0.0, fat = 0.0, carbs = 0.0, portions = "\tбез имени\nтарелка\t0\nкружка\t250")
        assertEquals(listOf(NamedPortion("кружка", 250.0)), p.namedPortions)
    }
}
