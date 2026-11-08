package app.fitfat

import android.app.Application
import app.fitfat.data.AppDatabase
import app.fitfat.data.FoodRepository
import app.fitfat.data.NormsStore
import app.fitfat.data.WeightRepository

import app.fitfat.data.DayClock
import app.fitfat.data.Photos
import kotlinx.coroutines.launch

class FitfatApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val weights by lazy { WeightRepository(database.weightDao()) }
    val food by lazy { FoodRepository(database.productDao(), database.foodDao(), database.dishDao()) }
    val norms by lazy { NormsStore(this) }
    val trainings by lazy { database.trainingDao() }

    override fun onCreate() {
        super.onCreate()
        DayClock.init(this)
        app.fitfat.data.AppSettings.init(this)
        app.fitfat.data.TrainingSchedule.init(this)
        app.fitfat.notify.Reminders.schedule(this)
        // Фото, на которые больше не ссылается ни один продукт (заменили, убрали, отменили правку), — удаляем
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { Photos.cleanup(this@FitfatApp, database.productDao().photos().toSet()) }
        }
    }
}
