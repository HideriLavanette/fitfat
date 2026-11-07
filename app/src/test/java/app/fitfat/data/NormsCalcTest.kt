package app.fitfat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NormsCalcTest {
    private val man = Body(Sex.Male, 90.0, 20.0, Activity.Light, Goal.Lose) // сухая масса 72 кг

    @Test fun leanAndBmr() {
        assertEquals(72.0, NormsCalc.lean(man), 1e-9)
        assertEquals(1925.2, NormsCalc.bmr(man), 1e-9)
    }

    @Test fun loseWithLowActivity_isBmr() {
        val n = NormsCalc.norms(man)
        assertEquals(158.0, n.protein, 0.0) // 2,2 × 72
        assertEquals(72.0, n.fat, 0.0)      // 1,0 × 72
        assertEquals(161.0, n.carbs, 0.0)   // (1925,2 − 632 − 648) / 4
        assertEquals(1924.0, n.kcal, 0.0)
    }

    @Test fun loseWithModerateActivity_usesTdeeMinusDeficit() {
        val n = NormsCalc.norms(man.copy(activity = Activity.Moderate))
        assertEquals(351.0, n.carbs, 0.0) // (1925,2 × 1,55 − 300 − 632 − 648) / 4
    }

    @Test fun woman_fat12_protein22() {
        val w = Body(Sex.Female, 65.0, 28.0, Activity.Sedentary, Goal.Lose) // сухая 46,8
        val n = NormsCalc.norms(w)
        assertEquals(103.0, n.protein, 0.0)
        assertEquals(56.0, n.fat, 0.0)
        assertEquals(116.0, n.carbs, 0.0)
    }

    @Test fun proteinByGoal_andClamped() {
        assertEquals(115.0, NormsCalc.norms(man.copy(goal = Goal.Keep)).protein, 0.0)  // 1,6
        assertEquals(158.0, NormsCalc.norms(man, proteinPerLeanKg = 3.0).protein, 0.0) // не выше 2,2
        assertEquals(58.0, NormsCalc.norms(man, fatPerLeanKg = 0.5).fat, 0.0)          // не ниже 0,8
    }

    @Test fun carbsNeverNegative() {
        val tiny = Body(Sex.Male, 50.0, 40.0, Activity.Sedentary, Goal.Lose)
        val n = NormsCalc.norms(tiny, proteinPerLeanKg = 2.2, fatPerLeanKg = 1.2)
        assertTrue(n.carbs >= 0)
    }

    @Test fun cut_fatFirstToFloor_thenCarbs() {
        val start = NormsCalc.norms(man) // 158 / 72 / 161
        val a = NormsCalc.cut(start, 72.0)!!
        assertEquals(158.0, a.protein, 0.0)
        assertEquals(58.0, a.fat, 0.0)    // до 0,8 × 72
        assertEquals(155.0, a.carbs, 0.0) // остаток 24 ккал — 6 г углеводов
        assertEquals(150.0, start.kcal - a.kcal, 0.0)
        val b = NormsCalc.cut(a, 72.0)!!
        assertEquals(58.0, b.fat, 0.0)    // жиры уже на нижней границе
        assertEquals(117.0, b.carbs, 0.0)
    }

    @Test fun cut_nothingLeft_returnsNull() {
        assertNull(NormsCalc.cut(Macros(232 + 522 + 200.0, 58.0, 58.0, 50.0), 72.0))
    }

    @Test fun plateau() {
        val today = LocalDate.of(2026, 11, 30)
        fun series(dropPerDay: Double) = (0..20).map { today.minusDays(20L - it) to 90.0 - dropPerDay * it }
        val since = today.minusDays(20)
        assertTrue(NormsCalc.isPlateau(series(0.005), since, today))   // −0,07 кг за 14 дней
        assertFalse(NormsCalc.isPlateau(series(0.03), since, today))   // −0,42 кг
        assertFalse(NormsCalc.isPlateau(series(0.0), today.minusDays(10), today)) // рано
        assertFalse(NormsCalc.isPlateau(series(0.0).filterIndexed { i, _ -> i % 4 == 0 }, since, today)) // мало взвешиваний
    }
}
