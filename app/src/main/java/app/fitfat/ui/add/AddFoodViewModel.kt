package app.fitfat.ui.add

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class Picked(val key: Long, val product: Product, val grams: Double, val pieces: Double?) {
    val macros: Macros get() = product.portion(grams, pieces)
}

data class Recognized(val fragment: Fragment, val candidates: List<Candidate>)

class AddFoodViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FitfatApp).food
    val products = repo.allProducts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recent = repo.recent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var text by mutableStateOf("")
        private set
    val picked = mutableStateListOf<Picked>()
    var date: LocalDate = LocalDate.now()
    var time by mutableStateOf(LocalTime.now())
        private set
    var meal by mutableStateOf(Meal.byTime(LocalTime.now()))
        private set
    private var mealTouched = false
    private var nextKey = 1L

    fun start(d: LocalDate) {
        date = d
        text = ""; picked.clear()
        time = LocalTime.now(); meal = Meal.byTime(time); mealTouched = false
    }

    fun onText(t: String) { text = t }
    fun setMeal(m: Meal) { meal = m; mealTouched = true }
    fun setTime(t: LocalTime) { time = t; if (!mealTouched) meal = Meal.byTime(t) }

    fun recognized(): List<Recognized> =
        Recognizer.split(text).map { Recognized(it, Recognizer.candidates(it, products.value)) }

    /** Подтверждение продукта: фрагмент уходит из текста, на его месте появляется панель. */
    fun confirm(r: Recognized, p: Product, grams: Double?, pieces: Double?) {
        val pcs = if (p.perPiece) (pieces ?: grams?.let { g -> p.pieceGrams?.takeIf { it > 0 }?.let { g / it } } ?: p.defaultGrams.coerceAtLeast(1.0)) else null
        val g = if (p.perPiece) (p.pieceGrams ?: 0.0) * (pcs ?: 1.0) else (grams ?: p.defaultGrams)
        picked += Picked(nextKey++, p, g, pcs)
        text = removeFragment(text, r.fragment.raw)
    }

    fun addRecent(p: Product) {
        val pcs = if (p.perPiece) p.defaultGrams.coerceAtLeast(1.0) else null
        picked += Picked(nextKey++, p, if (p.perPiece) (p.pieceGrams ?: 0.0) * pcs!! else p.defaultGrams, pcs)
    }

    fun update(item: Picked, grams: Double, pieces: Double?) {
        val i = picked.indexOfFirst { it.key == item.key }
        if (i >= 0) picked[i] = item.copy(grams = grams, pieces = pieces)
    }

    fun remove(item: Picked) { picked.removeAll { it.key == item.key } }

    fun createProduct(p: Product, from: Recognized?, onDone: () -> Unit) = viewModelScope.launch {
        val saved = repo.createProduct(p)
        if (from != null) confirm(from, saved, from.fragment.grams, from.fragment.pieces) else addRecent(saved)
        onDone()
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        if (picked.isEmpty()) return@launch
        repo.add(date, time, meal, picked.map { it.product to Portion(it.grams, it.pieces) })
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
