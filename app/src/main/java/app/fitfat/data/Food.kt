package app.fitfat.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import java.time.LocalTime

enum class Meal(val label: String) { Breakfast("Завтрак"), Lunch("Обед"), Dinner("Ужин");
    companion object {
        fun byTime(t: LocalTime): Meal = when {
            t.hour < 11 -> Breakfast
            t.hour < 17 -> Lunch
            else -> Dinner
        }
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: Lunch
    }
}

enum class DayType(val label: String) { Normal("Обычный"), Training("Тренировка") }

data class Macros(val kcal: Double = 0.0, val protein: Double = 0.0, val fat: Double = 0.0, val carbs: Double = 0.0) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, fat + o.fat, carbs + o.carbs)
    operator fun times(k: Double) = Macros(kcal * k, protein * k, fat * k, carbs * k)
}

val Product.base get() = Macros(kcal, protein, fat, carbs)
val FoodEntry.macros get() = Macros(kcal, protein, fat, carbs)

/** КБЖУ порции: для штучных — на [pieces] штук, иначе на [grams] граммов. */
fun Product.portion(grams: Double, pieces: Double?): Macros =
    if (perPiece) base * (pieces ?: 1.0) else base * (grams / 100.0)

fun Product.gramsFor(pieces: Double) = (pieceGrams ?: 0.0) * pieces

/** Нормы на день. Калории считаются из БЖУ. По умолчанию: Б 180 · Ж 60 · У 140, в дни тренировок У 240. */
class NormsStore(context: Context) {
    private val prefs = context.getSharedPreferences("norms", Context.MODE_PRIVATE)

    fun norms(type: DayType): Macros {
        val p = prefs.getFloat("p", 180f).toDouble()
        val f = prefs.getFloat("f", 60f).toDouble()
        val u = if (type == DayType.Training) prefs.getFloat("u_train", 240f).toDouble() else prefs.getFloat("u", 140f).toDouble()
        return Macros(p * 4 + f * 9 + u * 4, p, f, u)
    }

    fun dayType(date: LocalDate): DayType =
        DayType.entries.getOrNull(prefs.getInt("day_${date.toEpochDay()}", 0)) ?: DayType.Normal

    fun setDayType(date: LocalDate, t: DayType) = prefs.edit { putInt("day_${date.toEpochDay()}", t.ordinal) }

    var groupByTime: Boolean
        get() = prefs.getBoolean("group_time", false)
        set(v) = prefs.edit { putBoolean("group_time", v) }
}

class FoodRepository(private val products: ProductDao, private val food: FoodDao) {
    val allProducts = products.observeAll()
    val recent = products.observeRecent(8)
    fun day(date: LocalDate) = food.observeDay(date.toEpochDay())

    suspend fun createProduct(p: Product): Product = p.copy(id = products.insert(p))

    suspend fun add(date: LocalDate, time: LocalTime, meal: Meal, items: List<Pair<Product, Portion>>) {
        val now = System.currentTimeMillis()
        food.insertAll(items.map { (p, por) ->
            val m = p.portion(por.grams, por.pieces)
            FoodEntry(
                epochDay = date.toEpochDay(), minute = time.hour * 60 + time.minute, meal = meal.name,
                productId = p.id, name = p.name, category = p.category,
                grams = if (p.perPiece) p.gramsFor(por.pieces ?: 1.0) else por.grams, pieces = if (p.perPiece) por.pieces else null,
                kcal = m.kcal, protein = m.protein, fat = m.fat, carbs = m.carbs,
            )
        })
        items.forEach { products.markUsed(it.first.id, now) }
    }

    /** Новая граммовка: КБЖУ пересчитываются пропорционально сохранённой копии. */
    suspend fun changePortion(e: FoodEntry, grams: Double, pieces: Double?) {
        val k = if (e.pieces != null && pieces != null) pieces / e.pieces else if (e.grams > 0) grams / e.grams else 1.0
        food.update(e.copy(grams = grams, pieces = pieces, kcal = e.kcal * k, protein = e.protein * k, fat = e.fat * k, carbs = e.carbs * k))
    }

    suspend fun repeat(e: FoodEntry, date: LocalDate, time: LocalTime, grams: Double, pieces: Double?) {
        val k = if (e.pieces != null && pieces != null) pieces / e.pieces else if (e.grams > 0) grams / e.grams else 1.0
        food.insert(e.copy(id = 0, epochDay = date.toEpochDay(), minute = time.hour * 60 + time.minute, meal = Meal.byTime(time).name,
            grams = grams, pieces = pieces, kcal = e.kcal * k, protein = e.protein * k, fat = e.fat * k, carbs = e.carbs * k))
    }

    suspend fun delete(e: FoodEntry) = food.delete(e.id)
    suspend fun restore(e: FoodEntry) = food.insert(e)
}

data class Portion(val grams: Double, val pieces: Double?)
