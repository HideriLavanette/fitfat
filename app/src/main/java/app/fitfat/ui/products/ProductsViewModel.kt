package app.fitfat.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.fitfat.FitfatApp
import app.fitfat.data.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** База продуктов: список, карточка, правка. Схема БД не меняется. */
class ProductsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FitfatApp).food
    val products = repo.allProducts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun product(id: Long): Flow<Product?> = repo.product(id)

    /** Новый ([Product.id] = 0) или изменённый продукт; [onDone] получает id. */
    fun save(p: Product, onDone: (Long) -> Unit) = viewModelScope.launch {
        val id = if (p.id == 0L) repo.createProduct(p).id else { repo.updateProduct(p); p.id }
        onDone(id)
    }

    fun delete(id: Long, onDone: () -> Unit) = viewModelScope.launch { repo.deleteProduct(id); onDone() }
}
