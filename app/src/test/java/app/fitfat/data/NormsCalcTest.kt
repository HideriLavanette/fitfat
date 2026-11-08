package app.fitfat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NormsCalcTest {
    // Мужчина 82 кг, 15 % жира, рекомпозиция, ходит в зал: сухая масса 69,7 кг, базовый обмен ≈ 1 876 ккал
    private val man = Body(Sex.Male, 82.0, 15.0, Goal.Recomp, trains = true)

    @Test fun leanAndBmr() {
        assertEquals(69.7, NormsCalc.lean(man), 1e-9)
        assertEquals(1875.52, NormsCalc.bmr(man), 1e-9)
    }

    @Test fun recompTrains_isBmr_protein22PerBodyKg() {
        val n = NormsCalc.norms(man)
        assertEquals(180.0, n.protein, 0.0) // 2,2 × 82
        assertEquals(63.0, n.fat, 0.0)      // 0,9 × 69,7
        assertEquals(147.0, n.carbs, 0.0)   // (1 875,5 − 720 − 567) / 4
        assertEquals(1875.0, n.kcal, 0.0)   // на уровне базового обмена
    }

    @Test fun trainingDay_plus90Carbs_onlyIfTrains() {
        val n = NormsCalc.norms(man)
        assertEquals(237.0, NormsCalc.trainingDay(man, n).carbs, 0.0)
        val yogaOnly = man.copy(trains = false)
        val m = NormsCalc.norms(yogaOnly)
        assertEquals(m, NormsCalc.trainingDay(yogaOnly, m))
    }

    @Test fun womanNoTraining_lose_leanProtein_fat12() {
        val w = Body(Sex.Female, 65.0, 28.0, Goal.Lose, trains = false) // сухая 46,8
        val n = NormsCalc.norms(w)
        assertEquals(103.0, n.protein, 0.0) // 2,2 × 46,8
        assertEquals(56.0, n.fat, 0.0)      // 1,2 × 46,8
        assertEquals(116.0, n.carbs, 0.0)
    }

    @Test fun gain_addsOnlyCarbs() {
        val keep = Body(Sex.Male, 90.0, 20.0, Goal.Keep, trains = true)
        val k = NormsCalc.norms(keep)
        val g = NormsCalc.norms(keep.copy(goal = Goal.Gain))
        assertEquals(180.0, k.protein, 0.0) // 2,0 × 90
        assertEquals(k.fat, g.fat, 0.0)     // жиры при наборе не растут
        assertEquals(k.protein, g.protein, 0.0)
        assertEquals(251.0, k.carbs, 0.0)   // (1 925,2 × 1,2 − 720 − 585) / 4
        assertEquals(313.0, g.carbs, 0.0)   // +250 ккал — углеводами
    }

    @Test fun fatClamped() {
        assertEquals(56.0, NormsCalc.norms(man, fatPerLeanKg = 0.5).fat, 0.0) // не ниже 0,8
    }

    @Test fun carbsNeverNegative() {
        val tiny = Body(Sex.Male, 50.0, 40.0, Goal.Lose, trains = true)
        assertTrue(NormsCalc.norms(tiny).carbs >= 0)
    }

    @Test fun cut_fatFirstToFloor_thenCarbs() {
        val start = NormsCalc.norms(man) // 180 / 63 / 147
        val a = NormsCalc.cut(start, 69.7)!!
        assertEquals(180.0, a.protein, 0.0)
        assertEquals(56.0, a.fat, 0.0)    // до 0,8 × 69,7
        assertEquals(125.0, a.carbs, 0.0) // остаток 87 ккал — 22 г углеводов
        val b = NormsCalc.cut(a, 69.7)!!
        assertEquals(56.0, b.fat, 0.0)
        assertEquals(87.0, b.carbs, 0.0)
    }

    @Test fun cut_nothingLeft_returnsNull() {
        assertNull(NormsCalc.cut(Macros(0.0, 180.0, 56.0, 50.0), 69.7))
    }

    @Test fun plateau() {
        val today = LocalDate.of(2026, 11, 30)
        fun series(dropPerDay: Double) = (0..20).map { today.minusDays(20L - it) to 90.0 - dropPerDay * it }
        val since = today.minusDays(20)
        assertTrue(NormsCalc.isPlateau(series(0.005), since, today))
        assertFalse(NormsCalc.isPlateau(series(0.03), since, today))
        assertFalse(NormsCalc.isPlateau(series(0.0), today.minusDays(10), today))
        assertFalse(NormsCalc.isPlateau(series(0.0).filterIndexed { i, _ -> i % 4 == 0 }, since, today))
    }
}

class NormsPlanTest {
    private val me = Profile(Sex.Male, 28, 180, 82.0, 15, gym = true, sports = emptySet(), goal = Goal.Recomp)

    @Test fun plan_matchesMethod_andCuts() {
        val n0 = NormsPlan.calc(me, 82.0, 0)
        assertEquals(180.0, n0.normal.protein, 0.0)
        assertEquals(147.0, n0.normal.carbs, 0.0)
        assertEquals(237.0, n0.training.carbs, 0.0) // +90
        assertEquals(172.0, n0.cycle.carbs, 0.0)    // +25
        val n1 = NormsPlan.calc(me, 82.0, 1)
        assertEquals(56.0, n1.normal.fat, 0.0)
        assertEquals(125.0, n1.normal.carbs, 0.0)
        assertEquals(215.0, n1.training.carbs, 0.0) // срез идёт и в день тренировки
    }

    @Test fun proteinPerKg_defaultAndChosen() {
        assertEquals(2.2, NormsPlan.proteinPerKg(me, 82.0), 1e-9)
        val chosen = me.copy(proteinPerKg = 2.0)
        assertEquals(164.0, NormsPlan.calc(chosen, 82.0, 0).normal.protein, 0.0)
    }

    @Test fun custom_overridesType() {
        val own = Macros(180 * 4 + 60 * 9 + 140 * 4.0, 180.0, 60.0, 140.0)
        val r = NormsPlan.withCustom(NormsPlan.calc(me, 82.0, 0), mapOf("n" to own))
        assertEquals(own, r.normal)
        assertEquals(237.0, r.training.carbs, 0.0)
    }
}
