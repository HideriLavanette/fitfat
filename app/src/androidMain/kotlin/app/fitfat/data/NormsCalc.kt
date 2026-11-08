package app.fitfat.data

import app.fitfat.time.*
import app.fitfat.time.Fmt
import kotlinx.datetime.LocalDate
import app.fitfat.time.ChronoUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Методика норм (решение автора 08.11.2026, вторая редакция; заменяет формулы макетов первой настройки и «Цели и нормы»).
 *
 * 1. Сухая масса = вес × (1 − % жира). Базовый обмен — Кэтч — Макардл: 370 + 21,6 × сухая масса.
 * 2. Калории обычного дня — без множителя активности (активность не спрашиваем: люди её переоценивают,
 *    а нагрузки бывают не каждый день): похудение и рекомпозиция — базовый обмен; поддержание — базовый обмен × 1,2;
 *    набор — поддержание + 250 ккал за счёт углеводов (жиры при наборе не растут — иначе просто жиронабор).
 * 3. Белок: тренируется — 2,2 г на кг веса (похудение, рекомпозиция), 2,0 (набор, поддержание);
 *    не тренируется — на кг сухой массы: похудение 2,2 · рекомпозиция 2,0 · набор 1,8 · поддержание 1,6.
 *    Жиры: мужчинам 0,9, женщинам 1,2 г на кг сухой массы (границы 0,8–1,2). Остальное — углеводы.
 * 4. День тренировки — +90 г углеводов, только при интенсивных занятиях (зал, бег, плавание, единоборства,
 *    командные, велосипед); низкоинтенсивные (йога) не добавляют — они помогают худеть.
 * 5. Не худеет: тренд веса за 14 дней упал меньше чем на 0,2 кг — предложить −150 ккал. Белок не трогаем;
 *    срезаем сначала жиры, но не ниже 0,8 г на кг сухой массы, остальное — углеводы, не ниже 50 г.
 */

enum class Sex { Male, Female }

enum class Goal(val label: String, val kcalDelta: Int) {
    Lose("Похудеть", 0),
    Recomp("Рекомпозиция", 0),
    Keep("Поддерживать", 0),
    Gain("Набрать", 250),
}

/** Сохранено для старых профилей; в расчёте не участвует (активность не спрашиваем). */
enum class Activity(val label: String, val factor: Double) {
    Sedentary("Сидячая", 1.2),
    Light("Лёгкая", 1.375),
    Moderate("Средняя", 1.55),
    High("Высокая", 1.725),
}

/** [trains] — есть интенсивные тренировки (зал или интенсивный спорт). */
data class Body(val sex: Sex, val weightKg: Double, val bodyFatPct: Double, val goal: Goal, val trains: Boolean)

object NormsCalc {
    const val PROTEIN_MIN = 1.6
    const val PROTEIN_MAX = 2.2
    const val FAT_MIN = 0.8
    const val FAT_MAX = 1.2
    /** Поддержание: базовый обмен × бытовое движение. */
    const val KEEP_FACTOR = 1.2
    /** Добавка углеводов в день интенсивной тренировки, г. */
    const val TRAINING_CARBS = 90.0
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

    /** Жиры на кг сухой массы по умолчанию: мужчинам 0,9, женщинам 1,2 (гормоны и цикл). */
    fun defaultFatPerLeanKg(sex: Sex) = if (sex == Sex.Female) FAT_MAX else 0.9

    /** Белок по умолчанию, г: тренируется — от общего веса (2,2 / 2,0), иначе — от сухой массы (1,6–2,2 по цели). */
    fun defaultProtein(b: Body): Double = if (b.trains) {
        b.weightKg * if (b.goal == Goal.Lose || b.goal == Goal.Recomp) 2.2 else 2.0
    } else {
        lean(b) * when (b.goal) { Goal.Lose -> 2.2; Goal.Recomp -> 2.0; Goal.Gain -> 1.8; Goal.Keep -> 1.6 }
    }

    /** Калории обычного дня, от которых раскладываются БЖУ. */
    fun kcalBase(b: Body): Double = when (b.goal) {
        Goal.Lose, Goal.Recomp -> bmr(b)
        Goal.Keep, Goal.Gain -> bmr(b) * KEEP_FACTOR + b.goal.kcalDelta
    }

    /**
     * Нормы обычного дня: белок, жиры (от сухой массы), углеводы — остаток (не меньше нуля; если белок и жиры
     * уже больше калорий — калории растут до них). Граммы целые, калории считаются из граммов.
     */
    fun norms(b: Body, protein: Double = defaultProtein(b), fatPerLeanKg: Double = defaultFatPerLeanKg(b.sex)): Macros {
        val p = protein.roundToInt().toDouble()
        val f = (lean(b) * fatPerLeanKg.coerceIn(FAT_MIN, FAT_MAX)).roundToInt().toDouble()
        val u = max(0.0, floor((kcalBase(b) - p * 4 - f * 9) / 4))
        return Macros(p * 4 + f * 9 + u * 4, p, f, u)
    }

    /** День тренировки: обычный день + 90 г углеводов (интенсивные тренировки), иначе как обычный. */
    fun trainingDay(b: Body, normal: Macros): Macros =
        if (b.trains) normal.copy(carbs = normal.carbs + TRAINING_CARBS, kcal = normal.kcal + TRAINING_CARBS * 4) else normal

    /**
     * Вес стоит: с начала норм (или прошлого среза) прошло не меньше 14 дней, за последние 14 дней
     * есть хотя бы 6 взвешиваний, и тренд упал меньше чем на 0,2 кг. [trend] — сглаженный вес по датам.
     */
    fun isPlateau(trend: List<Pair<LocalDate, Double>>, normsSince: LocalDate, today: LocalDate): Boolean {
        if (ChronoUnit.DAYS.between(normsSince, today) < PLATEAU_DAYS) return false
        val from = today.minusDays(PLATEAU_DAYS.toLong())
        val window = trend.filter { !it.first.isBefore(from) && !it.first.isAfter(today) }.sortedBy { it.first }
        if (window.size < 6) return false
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
        val left = kcal - fatCut * 9
        val carbCut = min(max(0.0, current.carbs - CARBS_FLOOR), (left / 4).roundToInt().toDouble())
        if (fatCut <= 0 && carbCut <= 0) return null
        val f = current.fat - fatCut
        val u = current.carbs - carbCut
        return Macros(current.protein * 4 + f * 9 + u * 4, current.protein, f, u)
    }
}
