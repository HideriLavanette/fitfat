package app.fitfat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CompensationTest {
    private val norm = Macros(2150.0, 140.0, 70.0, 240.0).let { it.copy(kcal = it.protein * 4 + it.fat * 9 + it.carbs * 4) }
    private val cap = norm.kcal * Compensation.SHARE
    private val mon = LocalDate.of(2026, 11, 2) // понедельник

    private fun week(vararg eaten: Double?) = eaten.mapIndexed { i, k -> Compensation.Day(mon.plusDays(i.toLong()), k, norm) }

    @Test fun overSpreadEvenlyOverRestOfWeek() {
        // Перебор понедельника 120 ккал — по 20 на вторник…воскресенье
        val r = Compensation.calc(week(norm.kcal + 120), cap)
        for (i in 1..6) assertEquals(20.0, r.cut(mon.plusDays(i.toLong())), 1e-9)
        assertEquals(listOf(mon), r.cuts[mon.plusDays(3)]!!.from)
        assertEquals(0.0, r.cut(mon), 0.0)
        assertTrue(r.forgiven.isEmpty())
    }

    @Test fun cutLimitedTo10Percent_restForgiven() {
        // Суббота +600: на воскресенье только 10 % нормы, остальное списано
        val r = Compensation.calc(week(null, null, null, null, null, norm.kcal + 600), cap)
        assertEquals(cap, r.cut(mon.plusDays(6)), 1e-9)
        assertEquals(600 - cap, r.forgiven[mon.plusDays(5)]!!, 1e-9)
    }

    @Test fun sundayOverForgiven_nextWeekUntouched() {
        val r = Compensation.calc(week(null, null, null, null, null, null, norm.kcal + 300), cap)
        assertEquals(300.0, r.forgiven[mon.plusDays(6)]!!, 1e-9)
        assertEquals(0.0, r.cut(mon.plusDays(7)), 0.0)
    }

    @Test fun overCountedAgainstCutLimit() {
        // Пн +60 → по 10 на вт…вс; во вторник съели ровно обычную норму — это перебор 10 сверх сниженного лимита
        val r = Compensation.calc(week(norm.kcal + 60, norm.kcal), cap)
        assertEquals(10.0, r.over[mon.plusDays(1)]!!, 1e-9)
        assertEquals(12.0, r.cut(mon.plusDays(2)), 1e-9) // 10 + 10 / 5
    }

    @Test fun applyCutsFatAndCarbs_proteinKept() {
        val l = Compensation.apply(norm, 200.0)
        assertEquals(norm.protein, l.protein, 0.0)
        assertEquals(norm.kcal - 200, l.protein * 4 + l.fat * 9 + l.carbs * 4, 1e-9)
        assertEquals(norm.kcal - 200, l.kcal, 1e-9)
        assertTrue(l.fat < norm.fat && l.carbs < norm.carbs)
    }

    @Test fun daysWithoutRecordsDoNothing() {
        val r = Compensation.calc(week(null, null, null), cap)
        assertTrue(r.cuts.isEmpty() && r.over.isEmpty())
    }
}
