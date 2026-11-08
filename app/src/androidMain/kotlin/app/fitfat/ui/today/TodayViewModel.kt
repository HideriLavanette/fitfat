package app.fitfat.ui.today

import app.fitfat.time.*
import app.fitfat.time.Fmt
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fitfat.FitfatApp
import app.fitfat.data.Compensation
import app.fitfat.data.DayClock
import app.fitfat.data.DayType
import app.fitfat.data.FoodEntry
import app.fitfat.data.Macros
import app.fitfat.data.ProfileStore
import app.fitfat.data.macros
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth

data class TodayUi(
    val date: LocalDate = DayClock.today(),
    val dayType: DayType = DayType.Normal,
    val norms: Macros = Macros(),
    val eaten: Macros = Macros(),
    val entries: List<FoodEntry> = emptyList(),
    val groupByTime: Boolean = false,
    /** Нормы обычного дня — для пояснения «+60 г углеводов» под типом дня. */
    val baseNorms: Macros = Macros(),
    /** Показывать ли «Цикл» (женский пол в профиле). */
    val showCycle: Boolean = false,
    /** Компенсация переборов этой недели: на сколько ккал снижена норма дня и за какие дни. */
    val cut: Double = 0.0,
    val cutFrom: List<LocalDate> = emptyList(),
    /** Норма типа дня без компенсации — от неё считается прибавка тренировки / цикла. */
    val typeNorms: Macros = Macros(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val fa = app as FitfatApp
    private val repo = fa.food
    private val normsStore = fa.norms
    private val profiles = ProfileStore(app)

    private val date = MutableStateFlow(DayClock.today())
    private val dayType = MutableStateFlow(normsStore.dayType(DayClock.today()))
    private val groupByTime = MutableStateFlow(normsStore.groupByTime)

    private val _lastDeleted = MutableStateFlow<FoodEntry?>(null)
    val lastDeleted: StateFlow<FoodEntry?> = _lastDeleted.asStateFlow()

    val ui: StateFlow<TodayUi> = combine(
        // Дата едет вместе со своими записями — без промежуточного кадра «новый день, старые записи»
        // Вместе с днём — записи его недели с понедельника: из них считается компенсация переборов
        date.flatMapLatest { d -> combine(repo.day(d), repo.range(Compensation.weekStart(d), d)) { day, week -> Triple(d, day, week) } },
        dayType, groupByTime, normsStore.dayTypesVersion,
    ) { (d, entries, week), _, g, _ ->
        // Тип дня могли поменять в правке веса — читаем из хранилища
        val t = normsStore.dayType(d)
        val typeNorms = normsStore.norms(t)
        val comp = Compensation.of(normsStore, week, d, d, DayClock.today(), profiles.load()?.goal).cuts[d]
        TodayUi(
            d, t, Compensation.apply(typeNorms, comp?.kcal ?: 0.0), entries.fold(Macros()) { a, e -> a + e.macros }, entries, g,
            baseNorms = normsStore.norms(DayType.Normal), showCycle = normsStore.isFemale,
            cut = comp?.kcal ?: 0.0, cutFrom = comp?.from.orEmpty(), typeNorms = typeNorms,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())

    fun setDate(d: LocalDate) {
        val clamped = if (d.isAfter(DayClock.today())) DayClock.today() else d
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
        val t = if (d == DayClock.today()) LocalTime.now() else LocalTime.of(e.minute / 60, e.minute % 60)
        repo.repeat(e, d, t, grams, pieces)
    }
}
