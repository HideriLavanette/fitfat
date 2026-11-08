package app.fitfat

import android.app.Application
import app.fitfat.data.AppGraph
import app.fitfat.data.Photos
import app.fitfat.platform.AndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FitfatApp : Application() {
    lateinit var graph: AppGraph
        private set

    val database get() = graph.database
    val weights get() = graph.weights
    val food get() = graph.food
    val norms get() = graph.norms
    val trainings get() = graph.trainings
    val profile get() = graph.profile

    override fun onCreate() {
        super.onCreate()
        AndroidApp.context = this
        graph = AppGraph.start()
        app.fitfat.notify.Reminders.schedule()
        // Фото, на которые больше не ссылается ни один продукт (заменили, убрали, отменили правку), — удаляем
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { Photos.cleanup(database.productDao().photos().toSet()) }
        }
    }
}
