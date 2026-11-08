package app.fitfat.data

import app.fitfat.platform.Prefs
import app.fitfat.time.*
import app.fitfat.time.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

enum class Meal(val label: String) { Breakfast("Завтрак"), Lunch("Обед"), Dinner("Ужин");
    companion object {
        fun byTime(t: LocalTime): Meal = when {
            // До начала дня — ночь предыдущего дня: это ужин, а не завтрак
            DayClock.isNight(t) -> Dinner
            t.hour < 11 -> Breakfast
            t.hour < 17 -> Lunch
            else -> Dinner
        }
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: Lunch
    }
}

/** Порядок важен: тип дня хранится по ordinal. «Цикл» — только при женском поле в профиле. */
enum class DayType(val label: String) { Normal("Обычный"), Training("Тренировка"), Cycle("Цикл") }

data class Macros(val kcal: Double = 0.0, val protein: Double = 0.0, val fat: Double = 0.0, val carbs: Double = 0.0) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, fat + o.fat, carbs + o.carbs)
    operator fun times(k: Double) = Macros(kcal * k, protein * k, fat * k, carbs * k)
}

val Product.base get() = Macros(kcal, protein, fat, carbs)

/** Своя порция продукта: «тарелка» — 300 г, «1 штука» — 120 г. */
data class NamedPortion(val name: String, val grams: Double)

val Product.namedPortions: List<NamedPortion>
    get() = portions.lines().mapNotNull { l ->
        val i = l.lastIndexOf('\t')
        if (i <= 0) null else l.substring(i + 1).toDoubleOrNull()?.takeIf { it > 0 }?.let { NamedPortion(l.substring(0, i).trim(), it) }
    }

fun encodePortions(list: List<NamedPortion>): String =
    list.filter { it.name.isNotBlank() && it.grams > 0 }.joinToString("\n") { "${it.name.trim().replace('\t', ' ').replace('\n', ' ')}\t${it.grams}" }

val FoodEntry.macros get() = Macros(kcal, protein, fat, carbs)

/** КБЖУ порции: для штучных — на [pieces] штук, иначе на [grams] граммов. */
fun Product.portion(grams: Double, pieces: Double?): Macros =
    if (perPiece) base * (pieces ?: 1.0) else base * (grams / 100.0)

fun Product.gramsFor(pieces: Double) = (pieceGrams ?: 0.0) * pieces

/** Нормы на день. Калории считаются из БЖУ. По умолчанию: Б 180 · Ж 60 · У 140, в дни тренировок У 240. */
class NormsStore() {
    private val prefs = Prefs("norms")

    /**
     * Нормы типа дня. У дня тренировки и цикла могут быть свои Б и Ж (поправили в первой настройке),
     * иначе — как в обычный день; углеводы — свои или обычные + добавка (тренировка — 100 г по умолчанию, цикл +25 г).
     */
    fun norms(type: DayType): Macros {
        val p = prefs.getFloat("p", 180f).toDouble()
        val f = prefs.getFloat("f", 60f).toDouble()
        val base = prefs.getFloat("u", 140f).toDouble()
        val (pt, ft, u) = when (type) {
            DayType.Training -> Triple(prefs.getFloat("p_train", p.toFloat()).toDouble(), prefs.getFloat("f_train", f.toFloat()).toDouble(), prefs.getFloat("u_train", 240f).toDouble())
            DayType.Cycle -> Triple(prefs.getFloat("p_cycle", p.toFloat()).toDouble(), prefs.getFloat("f_cycle", f.toFloat()).toDouble(), prefs.getFloat("u_cycle", (base + CYCLE_EXTRA_CARBS).toFloat()).toDouble())
            DayType.Normal -> Triple(p, f, base)
        }
        return Macros(pt * 4 + ft * 9 + u * 4, pt, ft, u)
    }

    /** Нормы из первой настройки: обычный день, день тренировки и дни цикла (null — как обычный + добавка). */
    fun setNorms(normal: Macros, training: Macros?, cycle: Macros?, female: Boolean) {
        prefs.edit {
            putFloat("p", normal.protein.toFloat()); putFloat("f", normal.fat.toFloat()); putFloat("u", normal.carbs.toFloat())
            val t = training ?: normal.copy(carbs = normal.carbs + TRAINING_EXTRA_CARBS)
            putFloat("p_train", t.protein.toFloat()); putFloat("f_train", t.fat.toFloat()); putFloat("u_train", t.carbs.toFloat())
            val c = cycle ?: normal.copy(carbs = normal.carbs + CYCLE_EXTRA_CARBS)
            putFloat("p_cycle", c.protein.toFloat()); putFloat("f_cycle", c.fat.toFloat()); putFloat("u_cycle", c.carbs.toFloat())
            putString("sex", if (female) "f" else "m")
        }
        _dayTypesVersion.value++
    }

    /** Женский пол в профиле — тогда есть «Цикл». */
    val isFemale: Boolean get() = prefs.getString("sex", null) == "f"

    /** Своя отметка дня, иначе по расписанию тренировок «Тренировка», иначе «Обычный». */
    fun dayType(date: LocalDate): DayType {
        val key = "day_${date.toEpochDay()}"
        if (prefs.contains(key)) return DayType.entries.getOrNull(prefs.getInt(key, 0)) ?: DayType.Normal
        return if (TrainingSchedule.suggests(date)) DayType.Training else DayType.Normal
    }

    private val _dayTypesVersion = MutableStateFlow(0)
    /** Растёт при каждой смене типа дня — экраны перечитывают отметки (главный, вес). */
    val dayTypesVersion: StateFlow<Int> = _dayTypesVersion.asStateFlow()

    /** Расписание тренировок поменялось — экраны перечитывают типы дней. */
    fun touch() { _dayTypesVersion.value++ }

    fun setDayType(date: LocalDate, t: DayType) {
        prefs.edit { putInt("day_${date.toEpochDay()}", t.ordinal) }
        _dayTypesVersion.value++
    }

    var groupByTime: Boolean
        get() = prefs.getBoolean("group_time", false)
        set(v) = prefs.edit { putBoolean("group_time", v) }
}

class FoodRepository(private val products: ProductDao, private val food: FoodDao, private val dishes: DishDao) {
    val allProducts = products.observeAll()
    val recent = products.observeRecent(8)
    /** Записи дня: новые сверху; ночные (до начала дня) — после вечерних, как самые поздние. */
    fun day(date: LocalDate) = kotlinx.coroutines.flow.combine(food.observeDay(date.toEpochDay()), DayClock.start) { list, start ->
        list.sortedWith(compareByDescending<FoodEntry> { DayClock.order(it.minute, start) }.thenByDescending { it.id })
    }
    fun frequent(meal: Meal, limit: Int = 4) = food.observeFrequent(meal.name, limit)
    /** Записи за дни с [from] по [to] включительно (недельный отчёт). */
    fun range(from: LocalDate, to: LocalDate) = food.observeRange(from.toEpochDay(), to.toEpochDay())
    fun daysWithEntries(from: LocalDate, to: LocalDate) = food.observeDaysWithEntries(from.toEpochDay(), to.toEpochDay())

    suspend fun createProduct(p: Product): Product = p.copy(id = products.insert(p))
    fun product(id: Long) = products.observe(id)
    suspend fun byBarcode(code: String) = products.byBarcode(code)
    suspend fun updateProduct(p: Product) = products.update(p)
    /** Записи дневника не трогаем: КБЖУ в них сохранены копией. */
    suspend fun deleteProduct(id: Long) { dishes.clear(id); products.delete(id) }

    fun ingredients(dishId: Long) = dishes.observe(dishId)
    val allIngredients = dishes.observeAll()

    /**
     * Сохранить блюдо: КБЖУ на 100 г готового = сумма ингредиентов × 100 / вес готового.
     * Новое блюдо ([Product.id] = 0) создаётся. Возвращает id.
     */
    suspend fun saveDish(dish: Product, items: List<DishIngredient>, all: List<Product>): Long {
        val total = dishTotals(items, all)
        val raw = items.sumOf { it.grams }
        val cooked = (dish.cookedGrams ?: raw).takeIf { it > 0 } ?: raw.coerceAtLeast(1.0)
        val k = 100.0 / cooked
        val p = dish.copy(
            isDish = true, perPiece = false, pieceGrams = null, cookedGrams = cooked,
            kcal = total.kcal * k, protein = total.protein * k, fat = total.fat * k, carbs = total.carbs * k,
        )
        val id = if (p.id == 0L) products.insert(p) else { products.update(p); p.id }
        dishes.replace(id, items)
        return id
    }

    suspend fun add(date: LocalDate, time: LocalTime, meal: Meal, items: List<Pair<Product, Portion>>) {
        val now = nowMillis()
        food.insertAll(items.map { (p, por) ->
            val m = por.macros ?: p.portion(por.grams, por.pieces)
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

/** [macros] — КБЖУ, поправленные вручную только для этой записи. */
data class Portion(val grams: Double, val pieces: Double?, val macros: Macros? = null)
const val CYCLE_EXTRA_CARBS = 25.0

/** КБЖУ всего блюда по сырым весам ингредиентов. Штучные продукты пересчитываются через вес штуки. */
fun dishTotals(items: List<DishIngredient>, all: List<Product>): Macros = items.fold(Macros()) { acc, i ->
    val p = all.firstOrNull { it.id == i.productId } ?: return@fold acc
    val pcs = if (p.perPiece) p.pieceGrams?.takeIf { it > 0 }?.let { i.grams / it } ?: 0.0 else null
    acc + p.portion(i.grams, pcs)
}
