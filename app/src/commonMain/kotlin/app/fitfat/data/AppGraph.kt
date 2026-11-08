package app.fitfat.data

/**
 * Всё, что живёт пока работает приложение: база, репозитории, хранилища настроек.
 * Создаётся платформой при старте ([start]); экраны и модели берут через [get].
 */
class AppGraph(val database: AppDatabase) {
    val weights: WeightRepository by lazy { WeightRepository(database.weightDao()) }
    val food: FoodRepository by lazy { FoodRepository(database.productDao(), database.foodDao(), database.dishDao()) }
    val norms: NormsStore by lazy { NormsStore() }
    val trainings: TrainingDao get() = database.trainingDao()
    val profile: ProfileStore by lazy { ProfileStore() }

    companion object {
        private var instance: AppGraph? = null

        /** Читает настройки и открывает базу. Вызывать один раз при старте, когда платформенные хранилища уже доступны. */
        fun start(): AppGraph {
            instance?.let { return it }
            DayClock.init()
            AppSettings.init()
            TrainingSchedule.init()
            return AppGraph(createDatabase()).also { instance = it }
        }

        fun get(): AppGraph = instance ?: error("AppGraph.start() ещё не вызван")
    }
}
