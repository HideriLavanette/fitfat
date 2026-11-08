package app.fitfat.data

import app.fitfat.time.Fmt
import app.fitfat.time.now
import app.fitfat.time.ofEpochMilli
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

private val MONTHS = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "нояб", "дек")

/** «сегодня, 03:40», «вчера, 21:15», «7 нояб, 12:00» — когда сохранена копия. */
fun cloudWhen(ms: Long): String {
    val dt = LocalDateTime.ofEpochMilli(ms)
    val today = LocalDate.now()
    val day = when (dt.date) {
        today -> "сегодня"
        LocalDate.fromEpochDays(today.toEpochDays() - 1) -> "вчера"
        else -> "${dt.date.day} ${MONTHS[dt.date.month.ordinal]}"
    }
    return "$day, ${Fmt.hhmm(dt.hour, dt.minute)}"
}
