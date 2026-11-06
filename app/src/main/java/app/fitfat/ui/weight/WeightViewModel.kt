package app.fitfat.ui.weight

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fitfat.FitfatApp
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
import java.time.LocalDate

enum class Period(val label: String, val days: Long) {
    Week("1 нед", 7), Month("1 мес", 30), Quarter("3 мес", 90), Half("6 мес", 180), Year("1 год", 365)
}

data class WeightPoint(val date: LocalDate, val kg: Double, val trend: Double)

data class WeightUi(
    val all: List<WeightPoint> = emptyList(),
    val period: Period = Period.Month,
    val visible: List<WeightPoint> = emptyList(),
    val today: WeightPoint? = null,
    val last: WeightPoint? = null,
    val weekChange: Double? = null,
)

/** Сглаженный тренд: экспоненциальное среднее, α = 0,1 — суточные скачки воды почти не влияют. */
fun withTrend(entries: List<WeightEntry>): List<WeightPoint> {
    var ema = Double.NaN
    return entries.map { e ->
        ema = if (ema.isNaN()) e.kg else ema + 0.1 * (e.kg - ema)
        WeightPoint(e.date, e.kg, ema)
    }
}

class WeightViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FitfatApp).weights
    private val prefs = app.getSharedPreferences("weight", Context.MODE_PRIVATE)
    private val period = MutableStateFlow(Period.Month)

    val ui: StateFlow<WeightUi> = combine(repo.entries.map(::withTrend), period) { pts, p ->
        val today = LocalDate.now()
        val from = today.minusDays(p.days - 1)
        val last = pts.lastOrNull()
        val weekAgo = pts.lastOrNull { !it.date.isAfter(today.minusDays(7)) }
        WeightUi(
            all = pts,
            period = p,
            visible = pts.filter { !it.date.isBefore(from) },
            today = pts.lastOrNull { it.date == today },
            last = last,
            weekChange = if (last != null && weekAgo != null && weekAgo != last) last.trend - weekAgo.trend else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUi())

    private val _askToday = MutableStateFlow(false)
    val askToday: StateFlow<Boolean> = _askToday.asStateFlow()

    init {
        viewModelScope.launch {
            val today = LocalDate.now()
            val skipped = prefs.getLong(KEY_SKIPPED, -1L) == today.toEpochDay()
            _askToday.value = !skipped && repo.get(today) == null
        }
    }

    fun setPeriod(p: Period) { period.value = p }

    fun save(date: LocalDate, kg: Double) = viewModelScope.launch {
        repo.save(date, kg)
        if (date == LocalDate.now()) _askToday.value = false
    }

    fun delete(date: LocalDate) = viewModelScope.launch { repo.delete(date) }

    fun skipToday() {
        prefs.edit { putLong(KEY_SKIPPED, LocalDate.now().toEpochDay()) }
        _askToday.value = false
    }

    private companion object { const val KEY_SKIPPED = "skipped_day" }
}
