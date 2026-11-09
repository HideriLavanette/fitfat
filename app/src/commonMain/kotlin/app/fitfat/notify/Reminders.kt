package app.fitfat.notify

import app.fitfat.data.Schedule
import app.fitfat.time.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDateTime

/**
 * Напоминание о весе и недельный отчёт (макет Settings). Расписание — в [app.fitfat.data.AppSettings];
 * как именно будить — платформа ([ReminderScheduler]): Android — неточные будильники `AlarmManager`,
 * iOS — локальные уведомления `UNUserNotificationCenter`.
 */
object Reminders {
    const val WEIGHT = "weight"
    const val WEEK = "week"
    /** Предложение срезать калории ([app.fitfat.data.PaceCheck]) — открывает «Сегодня» с карточкой. Только Android. */
    const val PACE = "pace"

    /** Что открыть по нажатию на уведомление: [WEIGHT] — шторка веса, [WEEK] — «Неделя». Читает AppRoot. */
    val open = MutableStateFlow<String?>(null)

    /** Перепланировать оба напоминания по текущим настройкам. */
    fun schedule() = ReminderScheduler.schedule()

    /** Можно ли показывать уведомления (разрешение дано или не нужно). */
    fun canPost(): Boolean = ReminderScheduler.canPost()

    /** Ближайшее срабатывание после [now]: выбранный день недели (0 = Пн) и время; null — выключено или дни не выбраны. */
    fun next(s: Schedule, now: LocalDateTime): LocalDateTime? {
        if (!s.on || s.days.isEmpty()) return null
        for (i in 0..7) {
            val d = now.toLocalDate().plusDays(i.toLong())
            val t = d.atTime(s.minute / 60, s.minute % 60)
            if ((d.dayOfWeek.value - 1) in s.days && t.isAfter(now)) return t
        }
        return null
    }

    /** Тексты уведомлений — одни на обеих платформах. */
    const val WEIGHT_TITLE = "Время взвеситься"
    const val WEIGHT_TEXT = "Натощак, после туалета — так тренд веса точнее."
    const val WEEK_TITLE = "Недельный отчёт готов"
    const val WEEK_TEXT = "Баланс калорий, вес и средние БЖУ за 7 дней."
}

expect object ReminderScheduler {
    fun schedule()
    fun canPost(): Boolean
}
