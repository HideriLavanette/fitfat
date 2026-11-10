package app.fitfat.ui.products

import app.fitfat.platform.Prefs

import app.fitfat.data.AppGraph

import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope
import app.fitfat.data.DishIngredient
import app.fitfat.data.OffProduct
import app.fitfat.data.OpenFoodFacts
import app.fitfat.data.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Запрос к ассистенту для фото упаковки: один продукт на 100 г, цифры — с этикетки после проверки. */
private const val LABEL_ASK = "Это фото упаковки продукта — ингредиент для блюда, его не съели. Верни ровно один продукт: " +
    "название как на упаковке, граммы 100, КБЖУ на 100 г с таблицы пищевой ценности (если указаны на порцию — пересчитай на 100 г). " +
    "Перед записью проверь цифры на правдоподобие. Если КБЖУ на фото не видно — оцени по названию и составу и поставь from_label false."

/** База продуктов и блюд: список, карточки, правка. */
class ProductsViewModel : ViewModel() {
    private val repo = AppGraph.get().food
    val products = repo.allProducts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Состав всех блюд — счётчики ингредиентов в списках. */
    val allIngredients = repo.allIngredients.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Новый продукт со штрихкодом после сканирования (и данными из Open Food Facts, если нашлись). */
    var prefill: Product? = null

    /**
     * Скан штрихкода: продукт уже в базе — [onFound]; иначе ищем в Open Food Facts и готовим новый продукт — [onNew].
     */
    fun resolveBarcode(code: String, onFound: (Product) -> Unit, onNew: () -> Unit) = viewModelScope.launch {
        repo.byBarcode(code)?.let { onFound(it); return@launch }
        prefill = newFromOff(code, OpenFoodFacts.lookup(code))
        onNew()
    }

    suspend fun lookup(code: String): OffProduct? = OpenFoodFacts.lookup(code)

    /** Состав нового блюда из «Создать блюдо» на экране добавления. */
    var draft: List<DishIngredient> = emptyList()
    /** Название, вес готового и шаги нового блюда — из «Блюдо из описания» (ассистент). */
    var draftMeta: DishDraftMeta? = null

    /**
     * «Блюдо из описания»: ингредиенты — продукты базы (если ассистент узнал их) или новые с пометкой «оценка ассистента».
     * Готовит черновик для карточки блюда; [onReady] — открыть её.
     */
    fun draftFromRecipe(r: app.fitfat.data.Assistant.RecipeReply.Ok, onReady: () -> Unit) = viewModelScope.launch {
        val all = repo.allProducts.first()
        // Один и тот же продукт дважды («лук» в зажарку и в бульон) — одной строкой
        val merged = r.ingredients.groupBy { (it.existing ?: it.name).lowercase() }.values.map { l -> l.first().copy(grams = l.sumOf { it.grams }) }
        draft = merged.map { ing ->
            val p = all.firstOrNull { !it.isDish && it.name.equals(ing.existing ?: ing.name, ignoreCase = true) } ?: repo.createProduct(
                Product(
                    name = ing.name, category = ing.category,
                    kcal = ing.per100.kcal, protein = ing.per100.protein, fat = ing.per100.fat, carbs = ing.per100.carbs,
                    aliases = app.fitfat.data.Recognizer.normalize(ing.name), source = app.fitfat.data.SOURCE_AI, weightState = ing.weightState,
                ),
            )
            DishIngredient(dishId = 0, productId = p.id, grams = ing.grams)
        }
        draftMeta = DishDraftMeta(r.name, r.cookedGrams, r.steps)
        onReady()
    }

    fun product(id: Long): Flow<Product?> = repo.product(id)
    fun ingredients(dishId: Long): Flow<List<DishIngredient>> = repo.ingredients(dishId)

    /** Новый ([Product.id] = 0) или изменённый продукт; [onDone] получает id. */
    fun save(p: Product, onDone: (Long) -> Unit) = viewModelScope.launch {
        val id = if (p.id == 0L) repo.createProduct(p).id else { repo.updateProduct(p); p.id }
        onDone(id)
    }

    /** Ассистент читает этикетку для нового ингредиента блюда (0.6.5). */
    var labelBusy by androidx.compose.runtime.mutableStateOf(false)
        private set

    /**
     * Фото упаковки ([photo] — файл в `photos`) → КБЖУ на 100 г с этикетки (сервер проверяет их на правдоподобие).
     * [onResult]: продукт-черновик и пояснение ассистента или текст ошибки.
     */
    fun readLabel(photo: String, onResult: (app.fitfat.data.Assistant.Item?, note: String, error: String?) -> Unit) {
        if (labelBusy) return
        labelBusy = true
        viewModelScope.launch {
            val bytes = app.fitfat.data.Photos.forAssistant(photo)
            val reply = if (bytes == null) app.fitfat.data.Assistant.Reply.Error("Не получилось прочитать фото.")
                else app.fitfat.data.Assistant.ask(LABEL_ASK, null, emptyList(), bytes)
            labelBusy = false
            when (reply) {
                is app.fitfat.data.Assistant.Reply.Items -> reply.items.firstOrNull()
                    ?.let { onResult(it, reply.note, null) } ?: onResult(null, "", "Ассистент не нашёл продукт на фото.")
                is app.fitfat.data.Assistant.Reply.Question -> onResult(null, "", reply.text)
                is app.fitfat.data.Assistant.Reply.Answer -> onResult(null, "", reply.text)
                is app.fitfat.data.Assistant.Reply.Edits -> onResult(null, "", "Ассистент не понял фото — попробуйте снять этикетку ближе.")
                is app.fitfat.data.Assistant.Reply.Error -> onResult(null, "", reply.message)
            }
        }
    }

    /** Новый продукт из шторки состава блюда — сразу в базу, [onDone] получает его с id. */
    fun createIngredient(p: Product, onDone: (Product) -> Unit) = viewModelScope.launch { onDone(repo.createProduct(p)) }

    fun saveDish(dish: Product, items: List<DishIngredient>, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        onDone(repo.saveDish(dish, items, products.value))
    }

    fun delete(id: Long, onDone: () -> Unit) = viewModelScope.launch { repo.deleteProduct(id); onDone() }

    // Массовые действия в «Всех продуктах». Правки возвращают прежние версии — для «Отмена».
    fun deleteMany(ids: Set<Long>, onDone: () -> Unit = {}) = viewModelScope.launch { ids.forEach { repo.deleteProduct(it) }; onDone() }

    private fun editMany(ids: Set<Long>, change: (Product) -> Product?, onDone: (List<Product>) -> Unit) = viewModelScope.launch {
        val old = products.value.filter { it.id in ids }
        val changed = old.mapNotNull { p -> change(p)?.takeIf { it != p }?.let { p to it } }
        changed.forEach { repo.updateProduct(it.second) }
        onDone(changed.map { it.first })
    }

    /** Категория — только продуктам (у блюда своя плашка). */
    fun setCategoryMany(ids: Set<Long>, cat: String, onDone: (List<Product>) -> Unit) =
        editMany(ids, { if (it.isDish) null else it.copy(category = cat) }, onDone)

    /** БЖУ на 100 г (у штучных — на 1 шт); ккал считаются из БЖУ. Блюда считаются из состава — их не трогаем. */
    fun setMacrosMany(ids: Set<Long>, p: Double, f: Double, u: Double, onDone: (List<Product>) -> Unit) =
        editMany(ids, { if (it.isDish) null else it.copy(protein = p, fat = f, carbs = u, kcal = p * 4 + f * 9 + u * 4, source = app.fitfat.data.SOURCE_MANUAL) }, onDone)

    /** Обычная порция в граммах — продуктам на 100 г и блюдам; у штучных порция в штуках, их не трогаем. */
    fun setPortionMany(ids: Set<Long>, grams: Double, onDone: (List<Product>) -> Unit) =
        editMany(ids, { if (it.perPiece) null else it.copy(defaultGrams = grams) }, onDone)

    fun restore(old: List<Product>) = viewModelScope.launch { old.forEach { repo.updateProduct(it) } }
}

/** Заготовка нового продукта по штрихкоду: из Open Food Facts или пустая (ввод вручную). */
fun newFromOff(code: String, off: OffProduct?): Product =
    if (off == null) Product(name = "", category = "grain", kcal = 0.0, protein = 0.0, fat = 0.0, carbs = 0.0, barcode = code, source = app.fitfat.data.SOURCE_MANUAL)
    else Product(name = off.name, category = "grain", kcal = off.kcal, protein = off.protein, fat = off.fat, carbs = off.carbs, barcode = code, source = app.fitfat.data.SOURCE_OFF)

/** Черновик нового блюда сверх состава: название, вес готового (null — как сумма сырых), шаги рецепта. */
data class DishDraftMeta(val name: String, val cookedGrams: Double?, val recipe: String)
