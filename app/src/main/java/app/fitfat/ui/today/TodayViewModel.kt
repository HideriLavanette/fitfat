package app.fitfat.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fitfat.FitfatApp
import app.fitfat.data.DayType
import app.fitfat.data.FoodEntry
import app.fitfat.data.Macros
import app.fitfat.data.macros
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class TodayUi(
    val date: LocalDate = LocalDate.now(),
    val dayType: DayType = DayType.Normal,
    val norms: Macros = Macros(),
    val eaten: Macros = Macros(),
    val entries: List<FoodEntry> = emptyList(),
    val groupByTime: Boolean = false,
    /** Нормы обычного дня — для пояснения «+60 г углеводов» под типом дня. */
    val baseNorms: Macros = Macros(),
    /** Показывать ли «Цикл» (женский пол в профиле). */
    val showCycle: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val fa = app as FitfatApp
    private val repo = fa.food
    private val normsStore = fa.norms

    private val date = MutableStateFlow(LocalDate.now())
    private val dayType = MutableStateFlow(normsStore.dayType(LocalDate.now()))
    private val groupByTime = MutableStateFlow(normsStore.groupByTime)

    private val _lastDeleted = MutableStateFlow<FoodEntry?>(null)
    val lastDeleted: StateFlow<FoodEntry?> = _lastDeleted.asStateFlow()

    val ui: StateFlow<TodayUi> = combine(
        date.flatMapLatest { repo.day(it) }, date, dayType, groupByTime, normsStore.dayTypesVersion,
    ) { entries, d, _, g, _ ->
        // Тип дня могли поменять в правке веса — читаем из хранилища
        val t = normsStore.dayType(d)
        TodayUi(
            d, t, normsStore.norms(t), entries.fold(Macros()) { a, e -> a + e.macros }, entries, g,
            baseNorms = normsStore.norms(DayType.Normal), showCycle = normsStore.isFemale,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())

    fun setDate(d: LocalDate) {
        val clamped = if (d.isAfter(LocalDate.now())) LocalDate.now() else d
        date.value = clamped
        dayType.value = normsStore.dayType(clamped)
    }
    /** Дни месяца, в которых есть записи (epochDay) — точки в календаре. */
    fun daysWithEntries(month: YearMonth) = repo.daysWithEntries(month.atDay(1), month.atEndOfMonth())

    fun setDayType(t: DayType) { normsStore.setDayType(date.value, t); dayType.value = t }
    fun setGroupByTime(v: Boolean) { normsStore.groupByTime = v; groupByTime.value = v }

    fun delete(e: FoodEntry) = viewModelScope.launch { repo.delete(e); _lastDeleted.value = e }
    fun undoDelete() = viewModelScope.launch { _lastDeleted.value?.let { repo.restore(it) }; _lastDeleted.value = null }
    fun clearUndo() { _lastDeleted.value = null }

    fun changePortion(e: FoodEntry, grams: Double, pieces: Double?) = viewModelScope.launch { repo.changePortion(e, grams, pieces) }
    fun repeat(e: FoodEntry, grams: Double, pieces: Double?) = viewModelScope.launch {
        val d = date.value
        val t = if (d == LocalDate.now()) LocalTime.now() else LocalTime.of(e.minute / 60, e.minute % 60)
        repo.repeat(e, d, t, grams, pieces)
    }
}
