package app.fitfat.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Сутки дневника: новый день начинается не в полночь, а в [startMinute] (по умолчанию 07:00, меняется в профиле).
 * Еда, записанная до этого часа, относится к предыдущему дню и стоит в нём последней, как поздний ужин.
 * Вес не сдвигается: утреннее взвешивание — всегда вес календарного дня.
 */
object DayClock {
    const val DEFAULT_START = 7 * 60
    private const val KEY = "day_start_min"
    private var prefs: android.content.SharedPreferences? = null
    private val _start = MutableStateFlow(DEFAULT_START)
    /** Начало дня, минуты от полуночи. */
    val start: StateFlow<Int> = _start.asStateFlow()
    val startMinute: Int get() = _start.value

    fun init(context: Context) {
        val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        _start.value = p.getInt(KEY, DEFAULT_START)
    }

    fun setStart(minutes: Int) {
        val m = minutes.coerceIn(0, 12 * 60)
        prefs?.edit { putInt(KEY, m) }
        _start.value = m
    }

    /** День дневника для момента [at]: до начала дня — ещё вчера. */
    fun dayOf(at: LocalDateTime, start: Int = startMinute): LocalDate = at.minusMinutes(start.toLong()).toLocalDate()

    /** Сегодня по дневнику. */
    fun today(): LocalDate = dayOf(LocalDateTime.now())

    /** Порядок внутри дня: минуты от начала дня (02:00 при начале в 07:00 — 1 140, позже 23:00). */
    fun order(minute: Int, start: Int = startMinute): Int = Math.floorMod(minute - start, 24 * 60)

    /** Время до начала дня — это ночь предыдущего дня. */
    fun isNight(t: LocalTime, start: Int = startMinute): Boolean = t.hour * 60 + t.minute < start

    fun label(minutes: Int = startMinute) = "%02d:%02d".format(minutes / 60, minutes % 60)
}
