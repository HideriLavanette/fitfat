package app.fitfat.data

import app.fitfat.time.*
import kotlinx.datetime.LocalDate
import kotlin.math.max

/**
 * Предложение среза (0.6.4): нормы обычного дня до и после, изменение тренда за 14 дней.
 * [slow] — вес снижается, но медленнее половины плана; иначе — стоит (или растёт). [planKg] — сколько по плану за 14 дней.
 */
data class PaceOffer(val dropKg: Double, val before: Macros, val after: Macros, val slow: Boolean = false, val planKg: Double = 0.0)

/**
 * Проверка темпа (0.6.4, решение автора 09.11.2026): нормы похудения и рекомпозиции стартуют с запасом
 * ([NormsCalc.START_BUFFER] сверх базового обмена). Через 2 недели на нормах смотрим тренд: при похудении он должен
 * идти не медленнее половины плана ([NormsCalc.LOSE_PACE] веса в неделю), при рекомпозиции — хотя бы не стоять.
 * Иначе предлагаем срезать 150 ккал — карточкой на главном и уведомлением (Android). Одно и то же для обоих мест.
 */
object PaceCheck {
    /** Предложение среза или null. [trend] — сглаженный вес по датам, [normal] — нормы обычного дня сейчас. */
    fun offer(
        p: Profile, trend: List<Pair<LocalDate, Double>>, normal: Macros,
        since: LocalDate, snooze: LocalDate?, today: LocalDate,
    ): PaceOffer? {
        if (p.goal != Goal.Lose && p.goal != Goal.Recomp) return null
        if (snooze != null && !today.isAfter(snooze)) return null
        if (trend.isEmpty()) return null
        val w = trend.last().second
        val minDrop = NormsCalc.minDrop14(p.goal, w)
        if (!NormsCalc.isPlateau(trend, since, today, minDrop)) return null
        val after = NormsCalc.cut(normal, NormsCalc.lean(p.body(w))) ?: return null
        val from = trend.lastOrNull { !it.first.isAfter(today.minusDays(NormsCalc.PLATEAU_DAYS.toLong())) }?.second ?: trend.first().second
        val change = w - from
        return PaceOffer(change, normal, after, slow = -change >= NormsCalc.PLATEAU_MIN_DROP, planKg = NormsCalc.planDrop14(p.goal, w))
    }

    /** Заголовок и текст уведомления — те же слова, что на карточке. */
    fun title(o: PaceOffer) = if (o.slow) "Худеете медленнее плана" else "Вес стоит 2 недели"

    fun text(o: PaceOffer): String {
        val k = Fmt.f1(kotlin.math.abs(o.dropKg))
        val trend = "Тренд за 14 дней: ${if (o.dropKg > 0) "+" else "−"}$k кг" +
            (if (o.slow) ", по плану — около −${Fmt.f1(o.planKg)} кг" else "") + "."
        return "$trend Предлагаю срезать ${kotlin.math.round(max(0.0, o.before.kcal - o.after.kcal)).toInt()} ккал, белок не трогаем."
    }

    private val prefs by lazy { app.fitfat.platform.Prefs("pace") }

    /** Уведомлять о предложении один раз на каждый период норм (до среза или пересчёта). */
    fun shouldNotify(since: LocalDate): Boolean = prefs.getLong("notified", -1) != since.toEpochDay()
    fun markNotified(since: LocalDate) = prefs.edit { putLong("notified", since.toEpochDay()) }
}
