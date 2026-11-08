@file:OptIn(ExperimentalTime::class)

package app.fitfat.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Даты в общем коде — kotlinx.datetime. Здесь расширения с именами из java.time, к которым привык код приложения
 * (plusDays, toEpochDay, isAfter, YearMonth.atDay …), чтобы экраны и логика читались как раньше.
 */

private val zone: TimeZone get() = TimeZone.currentSystemDefault()

fun nowInstant(): Instant = Clock.System.now()

/** Миллисекунды от эпохи — вместо `System.currentTimeMillis()`. */
fun nowMillis(): Long = nowInstant().toEpochMilliseconds()

fun LocalDateTime.Companion.now(): LocalDateTime = nowInstant().toLocalDateTime(zone)
fun LocalDate.Companion.now(): LocalDate = LocalDateTime.now().date
fun LocalTime.Companion.now(): LocalTime = LocalDateTime.now().time

fun LocalDate.Companion.ofEpochDay(day: Long): LocalDate = LocalDate.fromEpochDays(day)
fun LocalDate.Companion.of(year: Int, month: Int, day: Int): LocalDate = LocalDate(year, month, day)

/** «Никогда»: день далеко в будущем — вместо `LocalDate.MAX`. */
val LocalDate.Companion.MAX: LocalDate get() = LocalDate(99999, 12, 31)

fun LocalDate.toEpochDay(): Long = toEpochDays()

fun LocalDate.plusDays(n: Long): LocalDate = plus(n, DateTimeUnit.DAY)
fun LocalDate.plusDays(n: Int): LocalDate = plus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Long): LocalDate = minus(n, DateTimeUnit.DAY)
fun LocalDate.minusDays(n: Int): LocalDate = minus(n, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(n: Long): LocalDate = plus(n * 7, DateTimeUnit.DAY)
fun LocalDate.minusWeeks(n: Long): LocalDate = minus(n * 7, DateTimeUnit.DAY)
fun LocalDate.plusMonths(n: Long): LocalDate = plus(n, DateTimeUnit.MONTH)
fun LocalDate.minusMonths(n: Long): LocalDate = minus(n, DateTimeUnit.MONTH)
fun LocalDate.plusMonths(n: Int): LocalDate = plus(n, DateTimeUnit.MONTH)
fun LocalDate.minusMonths(n: Int): LocalDate = minus(n, DateTimeUnit.MONTH)

fun LocalDate.isAfter(o: LocalDate): Boolean = this > o
fun LocalDate.isBefore(o: LocalDate): Boolean = this < o

/** Номер месяца 1–12 — вместо `monthValue`. */
val LocalDate.monthValue: Int get() = month.number

/** Понедельник = 1 … воскресенье = 7 — вместо `DayOfWeek.value`. */
val DayOfWeek.value: Int get() = isoDayNumber

/** День этой же недели (пн — вс) — вместо `date.with(DayOfWeek.MONDAY)`. */
fun LocalDate.with(dow: DayOfWeek): LocalDate = plusDays((dow.isoDayNumber - dayOfWeek.isoDayNumber).toLong())

fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime = LocalDateTime(this, LocalTime(hour, minute))
fun LocalDate.atTime(t: LocalTime): LocalDateTime = LocalDateTime(this, t)
fun LocalDate.atStartOfDay(): LocalDateTime = LocalDateTime(this, LocalTime(0, 0))
fun LocalDate.lengthOfMonth(): Int = yearMonth.lastDay.day
fun LocalDate.withDayOfMonth(d: Int): LocalDate = LocalDate(year, month, d)

/** Полных дней от [from] до [to] (может быть отрицательным) — вместо `ChronoUnit.DAYS.between`. */
fun daysBetween(from: LocalDate, to: LocalDate): Long = from.daysUntil(to).toLong()

/** Как в java.time: `ChronoUnit.DAYS.between(a, b)`. */
object ChronoUnit {
    object DAYS {
        fun between(from: LocalDate, to: LocalDate): Long = daysBetween(from, to)
    }
}

fun LocalDateTime.toLocalDate(): LocalDate = date
fun LocalDateTime.toLocalTime(): LocalTime = time
fun LocalDateTime.isAfter(o: LocalDateTime): Boolean = this > o
fun LocalDateTime.isBefore(o: LocalDateTime): Boolean = this < o

/** Минус [n] минут без учёта часовых поясов — как `java.time.LocalDateTime.minusMinutes`. */
fun LocalDateTime.minusMinutes(n: Long): LocalDateTime {
    val total = hour * 60L + minute - n
    val dayShift = Math.floorDiv(total, 1440L)
    val m = Math.floorMod(total, 1440L).toInt()
    return LocalDateTime(date.plusDays(dayShift), LocalTime(m / 60, m % 60, second, nanosecond))
}

fun LocalDateTime.toEpochMilli(): Long = toInstant(zone).toEpochMilliseconds()

/** Местные дата и время из миллисекунд эпохи. */
fun LocalDateTime.Companion.ofEpochMilli(ms: Long): LocalDateTime = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone)

fun LocalTime.Companion.of(hour: Int, minute: Int): LocalTime = LocalTime(hour, minute)
fun LocalTime.Companion.ofSecondOfDay(s: Long): LocalTime = LocalTime.fromSecondOfDay(s.toInt())

fun YearMonth.Companion.from(d: LocalDate): YearMonth = d.yearMonth
fun YearMonth.atDay(d: Int): LocalDate = LocalDate(year, month, d)
val YearMonth.monthValue: Int get() = month.number
fun YearMonth.atEndOfMonth(): LocalDate = lastDay
fun YearMonth.lengthOfMonth(): Int = lastDay.day
fun YearMonth.plusMonths(n: Long): YearMonth = plus(n, DateTimeUnit.MONTH)
fun YearMonth.minusMonths(n: Long): YearMonth = minus(n, DateTimeUnit.MONTH)
fun YearMonth.plusMonths(n: Int): YearMonth = plus(n, DateTimeUnit.MONTH)
fun YearMonth.minusMonths(n: Int): YearMonth = minus(n, DateTimeUnit.MONTH)

/** Вместо `java.lang.Math`, которого нет вне JVM: `import app.fitfat.time.Math`. */
object Math {
    fun floorDiv(a: Long, b: Long): Long = a.floorDiv(b)
    fun floorMod(a: Long, b: Long): Long = a.mod(b)
    fun floorMod(a: Int, b: Int): Int = a.mod(b)
    /** Как `java.lang.Math.round(double)`: к ближайшему, половина — вверх. */
    fun round(v: Double): Long = kotlin.math.floor(v + 0.5).toLong()
    fun round(v: Float): Int = kotlin.math.floor(v + 0.5f).toInt()
    fun floor(v: Double): Double = kotlin.math.floor(v)
    fun ceil(v: Double): Double = kotlin.math.ceil(v)
    fun abs(v: Double): Double = kotlin.math.abs(v)
    fun abs(v: Int): Int = kotlin.math.abs(v)
    fun pow(a: Double, b: Double): Double = a.pow(b)
    fun toDegrees(rad: Double): Double = rad * 180.0 / kotlin.math.PI
    fun toRadians(deg: Double): Double = deg * kotlin.math.PI / 180.0
    fun max(a: Double, b: Double): Double = kotlin.math.max(a, b)
    fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)
}

/** Русские названия: месяцы в родительном падеже («9 ноября»), дни недели. */
object RuDates {
    private val monthsGen = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    private val monthsNom = listOf("январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")
    private val daysFull = listOf("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье")
    private val daysShort = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    /** «ноября» — родительный падеж, как `MMMM` в java.time для ru. */
    fun monthGenitive(m: Month): String = monthsGen[m.number - 1]
    /** «Ноябрь» — именительный, с заглавной (заголовок календаря). */
    fun monthName(m: Month): String = monthsNom[m.number - 1].replaceFirstChar { it.uppercase() }
    /** «понедельник» — как `DayOfWeek.getDisplayName(FULL, ru)`. */
    fun dayFull(d: DayOfWeek): String = daysFull[d.isoDayNumber - 1]
    /** «пн» — как `DayOfWeek.getDisplayName(SHORT, ru)`. */
    fun dayShort(d: DayOfWeek): String = daysShort[d.isoDayNumber - 1]
}

/**
 * Простой форматтер дат по шаблону java.time для русского: `d` — день, `MMMM` — месяц в родительном падеже,
 * `LLLL` — месяц в именительном, `yyyy` — год, `EEEE` — день недели полностью, `EEE` — коротко. Остальное — как есть.
 */
class DateFmt(private val pattern: String) {
    fun format(d: LocalDate): String {
        val sb = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            var n = 1
            while (i + n < pattern.length && pattern[i + n] == c) n++
            when (c) {
                'd' -> sb.append(if (n >= 2) d.day.toString().padStart(2, '0') else d.day.toString())
                'M' -> sb.append(when (n) { 4 -> RuDates.monthGenitive(d.month); 3 -> RuDates.monthGenitive(d.month).take(3); 2 -> d.month.number.toString().padStart(2, '0'); else -> d.month.number.toString() })
                'L' -> sb.append(if (n >= 4) RuDates.monthName(d.month).lowercase() else d.month.number.toString())
                'y' -> sb.append(if (n == 2) (d.year % 100).toString().padStart(2, '0') else d.year.toString())
                'E' -> sb.append(if (n >= 4) RuDates.dayFull(d.dayOfWeek) else RuDates.dayShort(d.dayOfWeek))
                else -> repeat(n) { sb.append(c) }
            }
            i += n
        }
        return sb.toString()
    }
}

/** Как `java.time.format.DateTimeFormatter.ofPattern(p, Locale("ru"))`. */
object DateTimeFormatter {
    fun ofPattern(pattern: String): DateFmt = DateFmt(pattern)
}

fun LocalDate.format(f: DateFmt): String = f.format(this)
