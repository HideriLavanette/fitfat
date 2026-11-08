package app.fitfat.time

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Числа в интерфейсе — вместо `String.format(Locale("ru"), …)`, которого нет вне JVM.
 * Десятичный разделитель — запятая, разряды — неразрывный пробел (1 820), как требует CLAUDE.md.
 */
object Fmt {
    /** Неразрывный пробел между разрядами. */
    const val NBSP = ' '

    /** Целое с разрядами: 1 820. */
    fun int(v: Long): String {
        val s = abs(v).toString()
        val sb = StringBuilder()
        s.forEachIndexed { i, c ->
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(NBSP)
            sb.append(c)
        }
        return (if (v < 0) "−" else "") + sb
    }

    fun int(v: Double): String = int(v.roundToLong())

    /** Одна десятичная через запятую: 25,3; −0,4. Округление — как в `%.1f` (к ближайшему). */
    fun f1(v: Double): String = fixed(v, 1)

    /** [decimals] знаков после запятой; 0 — целое без разделителя. */
    fun fixed(v: Double, decimals: Int): String {
        if (decimals <= 0) return v.roundToLong().toString()
        val k = 10.0.pow(decimals)
        val r = (abs(v) * k).roundToLong()
        val whole = r / k.toLong()
        val frac = (r % k.toLong()).toString().padStart(decimals, '0')
        val neg = v < 0 && r != 0L
        return (if (neg) "-" else "") + whole + "," + frac
    }

    /** Две цифры: 07. */
    fun two(n: Int): String = n.toString().padStart(2, '0')

    /** 07:30 из минут от полуночи. */
    fun hhmm(minute: Int): String = two(minute / 60) + ":" + two(minute % 60)

    fun hhmm(hour: Int, minute: Int): String = two(hour) + ":" + two(minute)
}
