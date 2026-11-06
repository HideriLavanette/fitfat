package app.fitfat

import android.app.Application
import app.fitfat.data.AppDatabase
import app.fitfat.data.FoodRepository
import app.fitfat.data.NormsStore
import app.fitfat.data.WeightRepository

class FitfatApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val weights by lazy { WeightRepository(database.weightDao()) }
    val food by lazy { FoodRepository(database.productDao(), database.foodDao()) }
    val norms by lazy { NormsStore(this) }
}
