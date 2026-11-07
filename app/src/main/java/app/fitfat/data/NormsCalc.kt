package app.fitfat.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Методика норм (решение 08.11.2026, заменяет расчёт из макетов первой настройки и «Цели и нормы»).
 *
 * 1. Сухая масса = вес × (1 − % жира). Базовый обмен — Кэтч — Макардл: 370 + 21,6 × сухая масса.
 * 2. Калории: худеет и активность сидячая или лёгкая — ровно базовый обмен; иначе базовый обмен × активность
 *    ± поправка цели (худеет −300, рекомпозиция −230, набор +250, поддержание 0).
 * 3. Из калорий сначала белок 1,6–2,2 г на кг сухой массы (по цели), потом жиры 0,8–1,2 г на кг сухой массы
 *    (мужчинам 1,0, женщинам 1,2 — гормоны и цикл), всё остальное — углеводы.
 * 4. Не худеет: тренд веса за 14 дней упал меньше чем на 0,2 кг — предложить −150 ккал. Белок не трогаем;
 *    срезаем сначала жиры, но не ниже 0,8 г на кг сухой массы, остальное — углеводы, не ниже 50 г.
 *    После среза снова ждём 14 дней.
 */

enum class Sex { Male, Female }

enum class Goal(val label: String, val proteinPerLeanKg: Double, val kcalDelta: Int) {
    Lose("Похудеть", 2.2, -300),
    Recomp("Рекомпозиция", 2.0, -230),
    Keep("Поддерживать", 1.6, 0),
    Gain("Набрать", 1.8, 250),
}

enum class Activity(val label: String, val factor: Double, val low: Boolean) {
    Sedentary("Сидячая", 1.2, true),
    Light("Лёгкая", 1.375, true),
    Moderate("Средняя", 1.55, false),
    High("Высокая", 1.725, false),
}

data class Body(val sex: Sex, val weightKg: Double, val bodyFatPct: Double, val activity: Activity, val goal: Goal)

object NormsCalc {
    const val PROTEIN_MIN = 1.6
    const val PROTEIN_MAX = 2.2
    const val FAT_MIN = 0.8
    const val FAT_MAX = 1.2
    /** Ниже углеводы при срезе не опускаем, г. */
    const val CARBS_FLOOR = 50.0
    /** Шаг среза при застое, ккал. */
    const val CUT_KCAL = 150.0
    /** Сколько дней ждать перед выводом «вес стоит». */
    const val PLATEAU_DAYS = 14
    /** Тренд за [PLATEAU_DAYS] упал меньше — значит стоит, кг. */
    const val PLATEAU_MIN_DROP = 0.2

    fun lean(b: Body) = b.weightKg * (1 - b.bodyFatPct / 100)

    /** Базовый обмен (Кэтч — Макардл), ккал. */
    fun bmr(b: Body) = 370 + 21.6 * lean(b)

    /** Жиры на кг сухой массы по умолчанию: мужчинам 1,0, женщинам 1,2. */
    fun defaultFatPerLeanKg(sex: Sex) = if (sex == Sex.Female) FAT_MAX else 1.0

    /** Калории, от которых раскладываются БЖУ: при похудении с низкой активностью — базовый обмен. */
    fun kcalBase(b: Body): Double {
        val bmr = bmr(b)
        if (b.goal == Goal.Lose && b.activity.low) return bmr
        return bmr * b.activity.factor + b.goal.kcalDelta
    }

    /**
     * Нормы: белок и жиры — от сухой массы, углеводы — остаток (не меньше нуля; если белок и жиры уже
     * больше калорий — калории растут до них). Граммы целые, калории считаются из граммов.
     */
    fun norms(
        b: Body,
        proteinPerLeanKg: Double = b.goal.proteinPerLeanKg,
        fatPerLeanKg: Double = defaultFatPerLeanKg(b.sex),
    ): Macros {
        val lean = lean(b)
        val p = (lean * proteinPerLeanKg.coerceIn(PROTEIN_MIN, PROTEIN_MAX)).roundToInt().toDouble()
        val f = (lean * fatPerLeanKg.coerceIn(FAT_MIN, FAT_MAX)).roundToInt().toDouble()
        val u = max(0.0, floor((kcalBase(b) - p * 4 - f * 9) / 4))
        return Macros(p * 4 + f * 9 + u * 4, p, f, u)
    }

    /**
     * Вес стоит: с начала норм (или прошлого среза) прошло не меньше 14 дней, за последние 14 дней
     * есть хотя бы 6 взвешиваний, и тренд упал меньше чем на 0,2 кг. [trend] — сглаженный вес по датам.
     */
    fun isPlateau(trend: List<Pair<LocalDate, Double>>, normsSince: LocalDate, today: LocalDate): Boolean {
        if (ChronoUnit.DAYS.between(normsSince, today) < PLATEAU_DAYS) return false
        val from = today.minusDays(PLATEAU_DAYS.toLong())
        val window = trend.filter { !it.first.isBefore(from) && !it.first.isAfter(today) }.sortedBy { it.first }
        if (window.size < 6) return false
        // Начало окна — последняя точка не позже его начала, иначе первая в окне
        val start = trend.lastOrNull { !it.first.isAfter(from) }?.second ?: window.first().second
        return start - window.last().second < PLATEAU_MIN_DROP
    }

    /**
     * Срез при застое: −[kcal] ккал. Белок не трогаем; сначала жиры до 0,8 г на кг сухой массы,
     * остаток — углеводы, не ниже 50 г. null — срезать нечего (всё уже на нижних границах).
     */
    fun cut(current: Macros, leanKg: Double, kcal: Double = CUT_KCAL): Macros? {
        val fatFloor = (leanKg * FAT_MIN).roundToInt().toDouble()
        val fatCut = min(max(0.0, current.fat - fatFloor), floor(kcal / 9))
        var left = kcal - fatCut * 9
        val carbCut = min(max(0.0, current.carbs - CARBS_FLOOR), (left / 4).roundToInt().toDouble())
        left -= carbCut * 4
        if (fatCut <= 0 && carbCut <= 0) return null
        val f = current.fat - fatCut
        val u = current.carbs - carbCut
        return Macros(current.protein * 4 + f * 9 + u * 4, current.protein, f, u)
    }
}
