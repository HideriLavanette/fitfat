package app.fitfat.data

import app.fitfat.time.minusDays
import app.fitfat.time.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaceAndLabelTest {
    private val today = LocalDate(2026, 11, 9)
    private val since = today.minusDays(20)
    private val normal = Macros(180.0 * 4 + 61 * 9 + 160 * 4, 180.0, 61.0, 160.0)

    /** Тренд за 21 день: ежедневно, от [start] равномерно на [perDay] в день. */
    private fun trend(start: Double, perDay: Double) = (0..20).map { i -> today.minusDays(20).plusDays(i.toLong()) to start + perDay * i }

    @Test fun startBufferOnLoseAndRecomp() {
        val b = Body(Sex.Male, 83.0, 18.0, Goal.Lose, true)
        assertEquals(NormsCalc.bmr(b) + 150, NormsCalc.kcalBase(b), 0.001)
        assertEquals(NormsCalc.bmr(b) + 150, NormsCalc.kcalBase(b.copy(goal = Goal.Recomp)), 0.001)
        assertEquals(NormsCalc.bmr(b) * 1.2, NormsCalc.kcalBase(b.copy(goal = Goal.Keep)), 0.001)
    }

    @Test fun loseOnPlanNoOffer() {
        // 83 кг, план 0,5 % в неделю ≈ 0,83 кг за 2 недели; идёт −0,06 кг в день ≈ −0,84 кг — всё по плану
        val p = Profile(goal = Goal.Lose, weightKg = 83.0)
        assertNull(PaceCheck.offer(p, trend(83.0, -0.06), normal, since, null, today))
    }

    @Test fun loseSlowOffersCut() {
        // −0,02 кг в день ≈ −0,28 кг за 2 недели — меньше половины плана (0,41 кг)
        val p = Profile(goal = Goal.Lose, weightKg = 83.0)
        val o = assertNotNull(PaceCheck.offer(p, trend(83.0, -0.02), normal, since, null, today))
        assertTrue(o.slow)
        assertEquals("Худеете медленнее плана", PaceCheck.title(o))
        assertTrue(o.after.kcal < o.before.kcal)
        assertEquals(o.before.protein, o.after.protein, 0.001)
    }

    @Test fun recompSlowIsFine() {
        // Рекомпозиция: −0,28 кг за 2 недели — вес не стоит, срез не нужен
        val p = Profile(goal = Goal.Recomp, weightKg = 83.0)
        assertNull(PaceCheck.offer(p, trend(83.0, -0.02), normal, since, null, today))
    }

    @Test fun standingOffersPlateau() {
        val p = Profile(goal = Goal.Recomp, weightKg = 83.0)
        val o = assertNotNull(PaceCheck.offer(p, trend(83.0, 0.0), normal, since, null, today))
        assertFalse(o.slow)
        assertEquals("Вес стоит 2 недели", PaceCheck.title(o))
    }

    @Test fun tooEarlyOrSnoozedNoOffer() {
        val p = Profile(goal = Goal.Lose, weightKg = 83.0)
        assertNull(PaceCheck.offer(p, trend(83.0, 0.0), normal, today.minusDays(10), null, today))
        assertNull(PaceCheck.offer(p, trend(83.0, 0.0), normal, since, today.plusDays(1), today))
        assertNull(PaceCheck.offer(p.copy(goal = Goal.Keep), trend(83.0, 0.0), normal, since, null, today))
    }

    @Test fun labelPlausible() {
        // Честный твёрдый сыр: Б 25, Ж 29, У 0, 361 ккал
        assertTrue(Assistant.labelPlausible(361.0, 25.0, 29.0, 0.0))
        // «Сыр Б 30, Ж 10, 350 ккал» — калории с БЖУ не сходятся
        assertFalse(Assistant.labelPlausible(350.0, 30.0, 10.0, 0.0))
        // БЖУ больше 100 г на 100 г
        assertFalse(Assistant.labelPlausible(800.0, 60.0, 50.0, 10.0))
        assertFalse(Assistant.labelPlausible(Double.NaN, 3.0, 2.5, 4.7))
    }

    @Test fun fromLabelParsedOnlyWhenPlausible() {
        fun item(json: String) = Assistant.parseItemsForTest(Json.parseToJsonElement("[$json]") as kotlinx.serialization.json.JsonArray).single()
        val ok = item("""{"name":"Сыр Российский","existing":null,"category":"dairy","grams":30,"pieces":null,"kcal":361,"protein":25,"fat":29,"carbs":0,"confidence":"high","weight_state":"none","from_label":true}""")
        assertTrue(ok.fromLabel); assertEquals(SOURCE_LABEL, ok.source)
        val bad = item("""{"name":"Сыр","existing":null,"category":"dairy","grams":30,"pieces":null,"kcal":350,"protein":30,"fat":10,"carbs":0,"confidence":"high","weight_state":"none","from_label":true}""")
        assertFalse(bad.fromLabel); assertEquals(SOURCE_AI, bad.source)
        val est = item("""{"name":"Плов","existing":null,"category":"grain","grams":300,"pieces":null,"kcal":180,"protein":6,"fat":8,"carbs":21,"confidence":"medium","weight_state":"cooked"}""")
        assertFalse(est.fromLabel)
    }
}
