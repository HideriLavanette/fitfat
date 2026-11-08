package app.fitfat.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Компенсация переборов (макет Report, DESIGN.md «Неделя»). Перебор прошедшего дня делится поровну на оставшиеся дни
 * той же календарной недели (пн — вс): лимит каждого снижается не больше чем на 10 % нормы обычного дня, только за счёт
 * жиров и углеводов в их пропорции, белок прежний. Что не влезло в 10 % и перебор воскресенья — списываются.
 * Неделя календарная, поэтому срез конкретного дня не зависит от того, с какого дня смотреть.
 */
object Compensation {
    const val SHARE = 0.10

    /** День для расчёта: [eatenKcal] — null, если записей нет или день не закончился (сегодня и позже). */
    data class Day(val date: LocalDate, val eatenKcal: Double?, val limit: Macros)

    /** Срез дня: на сколько ккал снижен лимит и за переборы каких дней. */
    data class Cut(val kcal: Double, val from: List<LocalDate>)

    /** [cuts] — срезы по дням; [over] — перебор дня сверх его лимита со срезом; [forgiven] — сколько из него списано. */
    data class Result(val cuts: Map<LocalDate, Cut>, val over: Map<LocalDate, Double>, val forgiven: Map<LocalDate, Double>) {
        fun cut(d: LocalDate) = cuts[d]?.kcal ?: 0.0
    }

    fun weekStart(d: LocalDate): LocalDate = d.with(DayOfWeek.MONDAY)

    /** Лимит со срезом: жиры и углеводы урезаются пропорционально их доле в калориях, ккал падают ровно на [cut]. */
    fun apply(limit: Macros, cut: Double): Macros {
        if (cut <= 0) return limit
        val fk = limit.fat * 9; val uk = limit.carbs * 4
        val share = if (fk + uk > 0) fk / (fk + uk) else 0.5
        return Macros(limit.kcal - cut, limit.protein, limit.fat - cut * share / 9, limit.carbs - cut * (1 - share) / 4)
    }

    /**
     * Срезы для [days] (идут подряд по датам; чтобы срез дня был полным, список начинается с понедельника его недели).
     * [cap] — 10 % нормы обычного дня.
     */
    fun calc(days: List<Day>, cap: Double): Result {
        val cuts = HashMap<LocalDate, Double>()
        val src = HashMap<LocalDate, MutableList<LocalDate>>()
        val over = HashMap<LocalDate, Double>()
        val forgiven = HashMap<LocalDate, Double>()
        for (d in days.sortedBy { it.date }) {
            val lim = apply(d.limit, minOf(cuts[d.date] ?: 0.0, cap))
            val eaten = d.eatenKcal ?: continue
            if (eaten <= lim.kcal) continue
            val o = eaten - lim.kcal
            over[d.date] = o
            val left = 7 - d.date.dayOfWeek.value
            if (left == 0) { forgiven[d.date] = o; continue }
            val per = o / left
            var lost = 0.0
            for (k in 1..left) {
                val j = d.date.plusDays(k.toLong())
                val add = minOf(per, cap - (cuts[j] ?: 0.0)).coerceAtLeast(0.0)
                if (add > 0) { cuts[j] = (cuts[j] ?: 0.0) + add; src.getOrPut(j) { mutableListOf() } += d.date }
                lost += per - add
            }
            if (lost > 0.5) forgiven[d.date] = lost
        }
        return Result(cuts.mapValues { (k, v) -> Cut(minOf(v, cap), src[k].orEmpty()) }, over, forgiven)
    }

    /** Компенсация выключена при наборе массы: там важнее добрать, чем не переесть. */
    fun enabled(goal: Goal?) = goal != Goal.Gain

    private val empty = Result(emptyMap(), emptyMap(), emptyMap())

    /**
     * Срезы для дней с [from] по [to] по записям [entries] (должны покрывать понедельник недели [from] … [to]).
     * Лимит дня — норма его типа; закончились только дни до [today].
     */
    fun of(norms: NormsStore, entries: List<FoodEntry>, from: LocalDate, to: LocalDate, today: LocalDate, goal: Goal?): Result {
        if (!enabled(goal)) return empty
        val byDay = entries.groupBy { it.epochDay }
        val days = generateSequence(weekStart(from)) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { d ->
            val es = byDay[d.toEpochDay()]
            Day(d, if (d.isBefore(today) && !es.isNullOrEmpty()) es.sumOf { it.kcal } else null, norms.norms(norms.dayType(d)))
        }.toList()
        return calc(days, norms.norms(DayType.Normal).kcal * SHARE)
    }
}
