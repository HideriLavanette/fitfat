package app.fitfat.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Утреннее взвешивание: одна запись на день, ключ — номер дня от эпохи. */
@Entity(tableName = "weight")
data class WeightEntry(
    @PrimaryKey val epochDay: Long,
    val kg: Double,
    val note: String? = null,
)

/** Дата вынесена в расширение, чтобы Room не пытался сохранить её отдельной колонкой. */
val WeightEntry.date: LocalDate get() = LocalDate.ofEpochDay(epochDay)

/**
 * Продукт или блюдо. Если [perPiece] — КБЖУ указаны на 1 штуку весом [pieceGrams],
 * иначе на 100 г. [aliases] — другие названия через запятую, по ним идёт распознавание.
 */
@Entity(tableName = "product")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val perPiece: Boolean = false,
    val pieceGrams: Double? = null,
    val aliases: String = "",
    val defaultGrams: Double = 100.0,
    val useCount: Int = 0,
    val lastUsed: Long = 0,
)

/** Запись дневника. КБЖУ сохраняются копией, чтобы правка продукта не меняла историю. */
@Entity(tableName = "food_entry")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val minute: Int,
    val meal: String,
    val productId: Long,
    val name: String,
    val category: String,
    val grams: Double,
    val pieces: Double?,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
)

/** Строка выборки частых продуктов. */
data class Frequent(val productId: Long, val grams: Double, val pieces: Double?, val lastId: Long, val n: Int)

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight ORDER BY epochDay ASC")
    fun observeAll(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight WHERE epochDay = :day")
    suspend fun get(day: Long): WeightEntry?

    @Upsert
    suspend fun upsert(entry: WeightEntry)

    @Query("DELETE FROM weight WHERE epochDay = :day")
    suspend fun delete(day: Long)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM product ORDER BY useCount DESC, name ASC")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM product WHERE lastUsed > 0 ORDER BY lastUsed DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Product>>

    @Insert
    suspend fun insert(p: Product): Long

    @Update
    suspend fun update(p: Product)

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun get(id: Long): Product?

    @Query("SELECT * FROM product WHERE id = :id")
    fun observe(id: Long): Flow<Product?>

    @Query("DELETE FROM product WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE product SET useCount = useCount + 1, lastUsed = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)
}

@Dao
interface FoodDao {
    /** Частые продукты приёма пищи; граммы — из последней записи (в SQLite голые столбцы берутся из строки MAX). */
    @Query("SELECT productId, grams, pieces, MAX(id) AS lastId, COUNT(*) AS n FROM food_entry WHERE meal = :meal AND productId > 0 GROUP BY productId ORDER BY n DESC, lastId DESC LIMIT :limit")
    fun observeFrequent(meal: String, limit: Int): Flow<List<Frequent>>

    @Query("SELECT * FROM food_entry WHERE epochDay = :day ORDER BY minute DESC, id DESC")
    fun observeDay(day: Long): Flow<List<FoodEntry>>

    @Insert
    suspend fun insert(e: FoodEntry): Long

    @Insert
    suspend fun insertAll(e: List<FoodEntry>)

    @Update
    suspend fun update(e: FoodEntry)

    /** Дни с записями в диапазоне — для точек в календаре. */
    @Query("SELECT DISTINCT epochDay FROM food_entry WHERE epochDay BETWEEN :from AND :to")
    fun observeDaysWithEntries(from: Long, to: Long): Flow<List<Long>>

    @Query("DELETE FROM food_entry WHERE id = :id")
    suspend fun delete(id: Long)
}

private val SEED_ROWS = listOf(
        "('Гречка варёная', 'grain', 110, 4.2, 1.1, 21.3, 0, NULL, 'гречка,греча,гречневая каша', 200, 0, 0)",
        "('Рис варёный', 'grain', 130, 2.7, 0.3, 28, 0, NULL, 'рис', 180, 0, 0)",
        "('Овсянка на воде', 'grain', 88, 3, 1.7, 15, 0, NULL, 'овсянка,овсяная каша,геркулес', 250, 0, 0)",
        "('Макароны варёные', 'grain', 158, 5.8, 0.9, 31, 0, NULL, 'макароны,паста,спагетти', 200, 0, 0)",
        "('Картофель отварной', 'grain', 82, 2, 0.4, 17, 0, NULL, 'картошка,картофель', 200, 0, 0)",
        "('Хлеб пшеничный', 'grain', 265, 9, 3.2, 49, 0, NULL, 'хлеб,батон', 30, 0, 0)",
        "('Хлеб ржаной', 'grain', 210, 6.6, 1.2, 40, 0, NULL, 'черный хлеб,ржаной хлеб', 30, 0, 0)",
        "('Куриная грудка варёная', 'meat', 137, 29.8, 1.8, 0, 0, NULL, 'грудка,курица,куриное филе,куриная грудка', 150, 0, 0)",
        "('Куриная грудка гриль', 'meat', 165, 31, 3.6, 0, 0, NULL, 'грудка гриль,курица гриль', 150, 0, 0)",
        "('Индейка, филе', 'meat', 114, 24, 1.5, 0, 0, NULL, 'индейка', 150, 0, 0)",
        "('Говядина варёная', 'meat', 254, 25.8, 16.8, 0, 0, NULL, 'говядина,мясо', 150, 0, 0)",
        "('Тунец в собственном соку', 'meat', 96, 21, 1, 0, 0, NULL, 'тунец', 100, 0, 0)",
        "('Яйцо варёное', 'egg', 85, 6.9, 5.8, 0.6, 1, 55, 'яйцо,яйца,яички', 2, 0, 0)",
        "('Творог 5%', 'dairy', 121, 17.2, 5, 1.8, 0, NULL, 'творог', 200, 0, 0)",
        "('Кефир 2,5%', 'dairy', 53, 2.9, 2.5, 4, 0, NULL, 'кефир', 250, 0, 0)",
        "('Молоко 2,5%', 'dairy', 52, 2.8, 2.5, 4.7, 0, NULL, 'молоко', 200, 0, 0)",
        "('Греческий йогурт 2%', 'dairy', 73, 10, 2, 3.6, 0, NULL, 'греческий йогурт,йогурт', 150, 0, 0)",
        "('Сыр твёрдый', 'dairy', 350, 25, 27, 0, 0, NULL, 'сыр', 30, 0, 0)",
        "('Сметана 15%', 'dairy', 158, 2.6, 15, 3, 0, NULL, 'сметана', 20, 0, 0)",
        "('Банан', 'fruit', 89, 1.1, 0.3, 22.8, 0, NULL, 'банан,бананы', 120, 0, 0)",
        "('Яблоко', 'fruit', 47, 0.4, 0.4, 9.8, 0, NULL, 'яблоко,яблоки', 150, 0, 0)",
        "('Огурец', 'veg', 15, 0.8, 0.1, 2.8, 0, NULL, 'огурец,огурцы', 150, 0, 0)",
        "('Помидор', 'veg', 20, 0.9, 0.2, 3.7, 0, NULL, 'помидор,помидоры,томат', 150, 0, 0)",
        "('Брокколи', 'veg', 34, 2.8, 0.4, 6.6, 0, NULL, 'брокколи', 150, 0, 0)",
        "('Масло подсолнечное', 'oil', 899, 0, 99.9, 0, 0, NULL, 'масло,подсолнечное масло', 10, 0, 0)",
        "('Масло сливочное', 'oil', 748, 0.5, 82.5, 0.8, 0, NULL, 'сливочное масло', 10, 0, 0)",
        "('Орехи грецкие', 'oil', 654, 15.2, 65.2, 7, 0, NULL, 'грецкие орехи,орехи', 30, 0, 0)",
        "('Арахисовая паста', 'oil', 588, 25, 50, 20, 0, NULL, 'арахисовая паста,арахисовое масло', 20, 0, 0)",
        "('Сахар', 'sweet', 399, 0, 0, 99.8, 0, NULL, 'сахар', 5, 0, 0)",
        "('Мёд', 'sweet', 329, 0.8, 0, 81.5, 0, NULL, 'мед,мёд', 15, 0, 0)",
        "('Сникерс', 'sweet', 248, 4.3, 12, 30.5, 1, 50, 'сникерс,snickers', 1, 0, 0)"
)

private fun seed(db: SupportSQLiteDatabase) {
    val cols = "name, category, kcal, protein, fat, carbs, perPiece, pieceGrams, aliases, defaultGrams, useCount, lastUsed"
    SEED_ROWS.forEach { db.execSQL("INSERT INTO product ($cols) VALUES $it") }
}

/** 0.2 → 0.3: добавились продукты и дневник. Таблица веса не трогается, записи сохраняются. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `product` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `kcal` REAL NOT NULL, `protein` REAL NOT NULL, `fat` REAL NOT NULL, `carbs` REAL NOT NULL, `perPiece` INTEGER NOT NULL, `pieceGrams` REAL, `aliases` TEXT NOT NULL, `defaultGrams` REAL NOT NULL, `useCount` INTEGER NOT NULL, `lastUsed` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `food_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `meal` TEXT NOT NULL, `productId` INTEGER NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `grams` REAL NOT NULL, `pieces` REAL, `kcal` REAL NOT NULL, `protein` REAL NOT NULL, `fat` REAL NOT NULL, `carbs` REAL NOT NULL)")
        seed(db)
    }
}

@Database(entities = [WeightEntry::class, Product::class, FoodEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun productDao(): ProductDao
    abstract fun foodDao(): FoodDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fitfat.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = seed(db)
                })
                .build()
    }
}

class WeightRepository(private val dao: WeightDao) {
    val entries: Flow<List<WeightEntry>> = dao.observeAll()
    suspend fun get(date: LocalDate) = dao.get(date.toEpochDay())
    /** [note] = null — заметку не трогать (утреннее окно), пустая строка — стереть. */
    suspend fun save(date: LocalDate, kg: Double, note: String? = null) {
        val old = dao.get(date.toEpochDay())
        val n = if (note == null) old?.note else note.trim().ifEmpty { null }
        dao.upsert(WeightEntry(date.toEpochDay(), Math.round(kg * 10) / 10.0, n))
    }
    suspend fun delete(date: LocalDate) = dao.delete(date.toEpochDay())
}
