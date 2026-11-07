package app.fitfat.ui.add

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fitfat.FitfatApp
import app.fitfat.data.Candidate
import app.fitfat.data.Fragment
import app.fitfat.data.Macros
import app.fitfat.data.Meal
import app.fitfat.data.Portion
import app.fitfat.data.Product
import app.fitfat.data.Recognizer
import app.fitfat.data.portion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Подтверждённый продукт. [override] — КБЖУ, поправленные вручную только для этой записи. */
data class Picked(val key: Long, val product: Product, val grams: Double, val pieces: Double?, val override: Macros? = null) {
    val macros: Macros get() = override ?: product.portion(grams, pieces)
}

data class Recognized(val fragment: Fragment, val candidates: List<Candidate>)

/** Частый продукт приёма пищи с привычной порцией. */
data class FrequentItem(val product: Product, val grams: Double, val pieces: Double?)

@OptIn(ExperimentalCoroutinesApi::class)
class AddFoodViewModel(app: Application) : AndroidViewModel(app) {
    private val fa = app as FitfatApp
    private val repo = fa.food
    private val norms = fa.norms
    /** Запомненный выбор в «Какую именно?»: запрос → продукт. */
    private val choices = app.getSharedPreferences("choices", Context.MODE_PRIVATE)

    val products = repo.allProducts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recent = repo.recent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var text by mutableStateOf("")
        private set
    val picked = mutableStateListOf<Picked>()
    var date by mutableStateOf(LocalDate.now())
        private set
    var time by mutableStateOf(LocalTime.now())
        private set
    var meal by mutableStateOf(Meal.byTime(LocalTime.now()))
        private set
    /** Приём выбран руками — время его больше не меняет. */
    var mealTouched by mutableStateOf(false)
        private set
    /** Время или день меняли — в чипе уже не «Сейчас». */
    var timeTouched by mutableStateOf(false)
        private set
    private var nextKey = 1L

    private val dateFlow = MutableStateFlow(date)
    private val mealFlow = MutableStateFlow(meal)

    /** Сколько ккал останется в выбранный день до добавления. */
    val leftKcal = dateFlow.flatMapLatest { d ->
        repo.day(d).map { list -> norms.norms(norms.dayType(d)).kcal - list.sumOf { it.kcal } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val frequent = combine(mealFlow.flatMapLatest { repo.frequent(it) }, products) { rows, all ->
        rows.mapNotNull { r -> all.firstOrNull { it.id == r.productId }?.let { FrequentItem(it, r.grams, r.pieces) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun start(d: LocalDate) {
        setDay(d)
        text = ""; picked.clear()
        time = LocalTime.now(); meal = Meal.byTime(time); mealFlow.value = meal
        mealTouched = false; timeTouched = d != LocalDate.now()
    }

    fun onText(t: String) { text = t }

    fun selectMeal(m: Meal) { meal = m; mealFlow.value = m; mealTouched = true }

    fun changeTime(t: LocalTime) {
        time = t; timeTouched = true
        if (!mealTouched) { meal = Meal.byTime(t); mealFlow.value = meal }
    }

    fun changeDate(d: LocalDate) { setDay(d); timeTouched = true }

    /** «Сейчас»: сегодня, текущее время, приём по времени. */
    fun now() {
        setDay(LocalDate.now())
        time = LocalTime.now(); meal = Meal.byTime(time); mealFlow.value = meal
        mealTouched = false; timeTouched = false
    }

    private fun setDay(d: LocalDate) { date = d; dateFlow.value = d }

    fun recognized(): List<Recognized> =
        Recognizer.split(text).map { f ->
            val found = Recognizer.candidates(f, products.value)
            // Запомненный выбор — первым
            val remembered = choices.getLong(f.query, 0L).takeIf { it > 0 }?.let { id -> products.value.firstOrNull { it.id == id } }
            Recognized(f, if (remembered == null) found else listOf(Candidate(remembered, Int.MAX_VALUE)) + found.filter { it.product.id != remembered.id })
        }

    /** Выбор в «Какую именно?» запоминается для этого запроса. */
    fun remember(r: Recognized, p: Product) = choices.edit { putLong(r.fragment.query, p.id) }

    /** Подтверждение продукта: фрагмент уходит из текста, продукт появляется в карточке «Что съели». */
    fun confirm(r: Recognized, p: Product, grams: Double?, pieces: Double?, override: Macros? = null) {
        add(p, grams, pieces, override)
        text = removeFragment(text, r.fragment.raw)
    }

    /** Добавить продукт с порцией; без порции — порция продукта по умолчанию. */
    fun add(p: Product, grams: Double?, pieces: Double?, override: Macros? = null) {
        val pcs = if (p.perPiece) (pieces ?: grams?.let { g -> p.pieceGrams?.takeIf { it > 0 }?.let { g / it } } ?: p.defaultGrams.coerceAtLeast(1.0)) else null
        val g = if (p.perPiece) (p.pieceGrams ?: 0.0) * (pcs ?: 1.0) else (grams ?: p.defaultGrams)
        picked += Picked(nextKey++, p, g, pcs, override)
    }

    fun update(item: Picked, grams: Double, pieces: Double?, override: Macros?) {
        val i = picked.indexOfFirst { it.key == item.key }
        if (i >= 0) picked[i] = item.copy(grams = grams, pieces = pieces, override = override)
    }

    fun remove(item: Picked) { picked.removeAll { it.key == item.key } }

    fun createProduct(p: Product, from: Recognized?, grams: Double?, pieces: Double?, onDone: () -> Unit) = viewModelScope.launch {
        val saved = repo.createProduct(p)
        if (from != null) {
            remember(from, saved)
            confirm(from, saved, grams, pieces)
        } else add(saved, grams, pieces)
        onDone()
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        if (picked.isEmpty()) return@launch
        repo.add(date, time, meal, picked.map { it.product to Portion(it.grams, it.pieces, it.override) })
        picked.clear(); text = ""
        onDone()
    }

    private fun removeFragment(src: String, raw: String): String {
        val i = src.indexOf(raw)
        if (i < 0) return src
        val rest = (src.substring(0, i) + src.substring(i + raw.length))
        return rest.replace(Regex("^[\\s,;]+|[\\s,;]+$"), "").replace(Regex("\\s*,\\s*,\\s*"), ", ")
    }
}
