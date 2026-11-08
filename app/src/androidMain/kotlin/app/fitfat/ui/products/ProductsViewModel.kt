package app.fitfat.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fitfat.FitfatApp
import app.fitfat.data.DishIngredient
import app.fitfat.data.OffProduct
import app.fitfat.data.OpenFoodFacts
import app.fitfat.data.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** База продуктов и блюд: список, карточки, правка. */
class ProductsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FitfatApp).food
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

    fun product(id: Long): Flow<Product?> = repo.product(id)
    fun ingredients(dishId: Long): Flow<List<DishIngredient>> = repo.ingredients(dishId)

    /** Новый ([Product.id] = 0) или изменённый продукт; [onDone] получает id. */
    fun save(p: Product, onDone: (Long) -> Unit) = viewModelScope.launch {
        val id = if (p.id == 0L) repo.createProduct(p).id else { repo.updateProduct(p); p.id }
        onDone(id)
    }

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
