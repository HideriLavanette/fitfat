package app.fitfat.ui.weight

import app.fitfat.time.*
import app.fitfat.time.Fmt
import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fitfat.FitfatApp
import app.fitfat.data.DayType
import app.fitfat.data.WeightEntry
import app.fitfat.data.date
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class Period(val label: String, val days: Long) {
    Week("1 нед", 7), Month("1 мес", 30), Quarter("3 мес", 90), Half("6 мес", 180), Year("1 год", 365)
}

data class WeightPoint(val date: LocalDate, val kg: Double, val trend: Double, val note: String? = null)

data class WeightUi(
    val all: List<WeightPoint> = emptyList(),
    val period: Period = Period.Month,
    val visible: List<WeightPoint> = emptyList(),
    val today: WeightPoint? = null,
    val last: WeightPoint? = null,
    /** Изменение тренда за выбранный период. */
    val periodChange: Double? = null,
    /** Номер обновления типов дней — календарь перечитывает отметки. */
    val marksVersion: Int = 0,
    val female: Boolean = false,
) {
    val byDay: Map<Long, WeightPoint> by lazy { all.associateBy { it.date.toEpochDay() } }
    fun before(d: LocalDate) = all.lastOrNull { it.date.isBefore(d) }
}

/** Сглаженный тренд: экспоненциальное среднее, α = 0,1 — суточные скачки воды почти не влияют. */
fun withTrend(entries: List<WeightEntry>): List<WeightPoint> {
    var ema = Double.NaN
    return entries.map { e ->
        ema = if (ema.isNaN()) e.kg else ema + 0.1 * (e.kg - ema)
        WeightPoint(e.date, e.kg, ema, e.note)
    }
}

class WeightViewModel(app: Application) : AndroidViewModel(app) {
    private val fa = app as FitfatApp
    private val repo = fa.weights
    private val norms = fa.norms
    private val prefs = app.getSharedPreferences("weight", Context.MODE_PRIVATE)
    private val period = MutableStateFlow(Period.Month)

    val ui: StateFlow<WeightUi> = combine(repo.entries.map(::withTrend), period, norms.dayTypesVersion) { pts, p, v ->
        val today = LocalDate.now()
        val from = today.minusDays(p.days - 1)
        val visible = pts.filter { !it.date.isBefore(from) }
        WeightUi(
            all = pts,
            period = p,
            visible = visible,
            today = pts.lastOrNull { it.date == today },
            last = pts.lastOrNull(),
            periodChange = if (visible.size >= 2) visible.last().trend - visible.first().trend else null,
            marksVersion = v,
            female = norms.isFemale,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUi())

    private val _askToday = MutableStateFlow(false)
    val askToday: StateFlow<Boolean> = _askToday.asStateFlow()

    /** Подсказку пальцем показываем, пока круг ни разу не крутили. */
    private val _hint = MutableStateFlow(!prefs.getBoolean(KEY_LEARNED, false))
    val hint: StateFlow<Boolean> = _hint.asStateFlow()

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            val skipped = prefs.getLong(KEY_SKIPPED, -1L) == today.toEpochDay()
            _askToday.value = !skipped && repo.get(today) == null
        }
    }

    fun setPeriod(p: Period) { period.value = p }

    fun dayType(d: LocalDate): DayType = norms.dayType(d)

    /** Еда за день — для сводки дня в календаре. */
    fun food(d: LocalDate) = fa.food.day(d)

    /** Нормы дня с учётом его типа. */
    fun normsFor(d: LocalDate) = norms.norms(norms.dayType(d))

    fun learned() {
        if (_hint.value) { _hint.value = false; prefs.edit { putBoolean(KEY_LEARNED, true) } }
    }

    fun save(date: LocalDate, kg: Double) = viewModelScope.launch {
        repo.save(date, kg)
        if (date == LocalDate.now()) _askToday.value = false
    }

    /** Правка дня: вес, заметка и отметка — тип дня (тот же, что на главном). */
    fun saveDay(date: LocalDate, kg: Double, note: String, type: DayType) = viewModelScope.launch {
        repo.save(date, kg, note)
        if (norms.dayType(date) != type) norms.setDayType(date, type)
        if (date == LocalDate.now()) _askToday.value = false
    }

    fun delete(date: LocalDate) = viewModelScope.launch { repo.delete(date) }

    fun skipToday() {
        prefs.edit { putLong(KEY_SKIPPED, LocalDate.now().toEpochDay()) }
        _askToday.value = false
    }

    private companion object {
        const val KEY_SKIPPED = "skipped_day"
        const val KEY_LEARNED = "dial_learned"
    }
}
