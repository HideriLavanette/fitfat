package app.fitfat.data

import androidx.room.ColumnInfo
import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.fitfat.time.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

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
    /** Блюдо (0.4): КБЖУ на 100 г готового считаются из ингредиентов ([DishIngredient]). */
    @ColumnInfo(defaultValue = "0") val isDish: Boolean = false,
    /** Вес готового блюда, г. */
    val cookedGrams: Double? = null,
    /** Текст рецепта. */
    val recipe: String? = null,
    /** Штрихкод (EAN/UPC), 0.4. */
    val barcode: String? = null,
    /** Откуда данные: [SOURCE_SEED] встроенная база, [SOURCE_OFF] Open Food Facts, [SOURCE_MANUAL] введены вручную, [SOURCE_AI] оценка ассистента. */
    val source: String? = null,
    /** Фото — имя файла в `files/photos` (см. [Photos]), 0.4. */
    val photo: String? = null,
    /** Свои порции: строки «название ⇥ граммы» (через табуляцию), см. [namedPortions], 0.4. */
    @ColumnInfo(defaultValue = "") val portions: String = "",
    /** КБЖУ указаны на сухой, сырой или готовый вес ([WEIGHT_DRY] и т. д., см. [WeightState]); null — не важно. 0.6. */
    val weightState: String? = null,
)

const val SOURCE_SEED = "seed"
const val SOURCE_OFF = "off"
const val SOURCE_MANUAL = "manual"
/** Примерная оценка ассистента (0.6) — значения неточные, их стоит уточнить. */
const val SOURCE_AI = "ai"
/** КБЖУ переписаны ассистентом с этикетки на фото и прошли проверку на правдоподобие (0.6.4) — это не оценка. */
const val SOURCE_LABEL = "label"

/** Ингредиент блюда: продукт и его сырой вес в рецепте. */
@Entity(tableName = "dish_ingredient")
data class DishIngredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dishId: Long,
    val productId: Long,
    val grams: Double,
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

    @Query("SELECT * FROM product WHERE barcode = :code LIMIT 1")
    suspend fun byBarcode(code: String): Product?

    @Query("SELECT * FROM product WHERE id = :id")
    fun observe(id: Long): Flow<Product?>

    @Query("SELECT photo FROM product WHERE photo IS NOT NULL")
    suspend fun photos(): List<String>

    @Query("DELETE FROM product WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE product SET useCount = useCount + 1, lastUsed = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)
}

@Dao
interface DishDao {
    @Query("SELECT * FROM dish_ingredient WHERE dishId = :dishId ORDER BY id")
    fun observe(dishId: Long): Flow<List<DishIngredient>>

    @Query("SELECT * FROM dish_ingredient WHERE dishId = :dishId ORDER BY id")
    suspend fun get(dishId: Long): List<DishIngredient>

    @Query("SELECT * FROM dish_ingredient ORDER BY id")
    fun observeAll(): Flow<List<DishIngredient>>

    @Query("DELETE FROM dish_ingredient WHERE dishId = :dishId")
    suspend fun clear(dishId: Long)

    @Insert
    suspend fun insertAll(list: List<DishIngredient>)

    /** Состав целиком: старый стираем, новый пишем. */
    @Transaction
    suspend fun replace(dishId: Long, list: List<DishIngredient>) {
        clear(dishId)
        insertAll(list.map { it.copy(id = 0, dishId = dishId) })
    }
}

@Dao
interface FoodDao {
    /** Частые продукты приёма пищи; граммы — из последней записи (в SQLite голые столбцы берутся из строки MAX). */
    @Query("SELECT productId, grams, pieces, MAX(id) AS lastId, COUNT(*) AS n FROM food_entry WHERE meal = :meal AND productId > 0 GROUP BY productId ORDER BY n DESC, lastId DESC LIMIT :limit")
    fun observeFrequent(meal: String, limit: Int): Flow<List<Frequent>>

    @Query("SELECT * FROM food_entry WHERE epochDay = :day ORDER BY minute DESC, id DESC")
    fun observeDay(day: Long): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entry WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, minute")
    fun observeRange(from: Long, to: Long): Flow<List<FoodEntry>>

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

/**
 * Сухие и сырые продукты (0.6): КБЖУ по весу до готовки — так считать точнее, чем по варёному.
 * Отдельно от [SEED_ROWS]: по их числу старые миграции отличают встроенные продукты от своих.
 */
private val DRY_ROWS = listOf(
    "('Гречка сухая', 'grain', 313, 12.6, 3.3, 62.1, 'гречка сухая,сухая гречка,гречневая крупа', 70, '$WEIGHT_DRY')",
    "('Рис сухой', 'grain', 344, 6.7, 0.7, 78.9, 'рис сухой,сухой рис,рисовая крупа', 70, '$WEIGHT_DRY')",
    "('Овсяные хлопья', 'grain', 366, 11.9, 7.2, 69.3, 'овсяные хлопья,хлопья овсяные', 50, '$WEIGHT_DRY')",
    "('Макароны сухие', 'grain', 344, 10.4, 1.1, 71.5, 'макароны сухие,сухие макароны', 80, '$WEIGHT_DRY')",
    "('Куриная грудка сырая', 'meat', 113, 23.6, 1.9, 0.4, 'грудка сырая,куриное филе сырое', 150, '$WEIGHT_RAW')",
    "('Говядина сырая', 'meat', 187, 18.9, 12.4, 0, 'говядина сырая,сырая говядина', 150, '$WEIGHT_RAW')",
)

/** Сухие и сырые продукты, которых ещё нет в базе (по названию). */
private fun seedDry(db: SQLiteConnection) {
    val cols = "name, category, kcal, protein, fat, carbs, aliases, defaultGrams, weightState, perPiece, useCount, lastUsed, isDish, portions, source"
    DRY_ROWS.forEach { row ->
        val name = row.substringAfter("('").substringBefore("'")
        db.execSQL("INSERT INTO product ($cols) SELECT ${row.removePrefix("(").removeSuffix(")")}, 0, 0, 0, 0, '', '$SOURCE_SEED' WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = '$name')")
    }
}

/**
 * Базовые продукты (0.6.6, просьба автора 10.11.2026): овощи, фрукты, ягоды, кисломолочное разной жирности, крупы и бобовые
 * сухими, мясо, птица и рыба сырыми. КБЖУ на 100 г — по справочнику Скурихина и USDA. Добавляются, только если продукта
 * с таким названием ещё нет; свои продукты человека не трогаются.
 */
private val BASIC_ROWS = listOf(
    "('Лук репчатый', 'veg', 41, 1.4, 0, 8.2, 'лук,луковица,репчатый лук', 80, NULL)",
    "('Лук зелёный', 'veg', 19, 1.3, 0, 3.2, 'зеленый лук,зелёный лук', 20, NULL)",
    "('Чеснок', 'veg', 143, 6.5, 0.5, 29.9, 'чеснок', 5, NULL)",
    "('Морковь', 'veg', 35, 1.3, 0.1, 6.9, 'морковь,морковка', 80, NULL)",
    "('Капуста белокочанная', 'veg', 27, 1.8, 0.1, 4.7, 'капуста', 150, NULL)",
    "('Капуста цветная', 'veg', 30, 2.5, 0.3, 4.2, 'цветная капуста', 150, NULL)",
    "('Капуста пекинская', 'veg', 16, 1.2, 0.2, 2, 'пекинская капуста', 100, NULL)",
    "('Свёкла', 'veg', 42, 1.5, 0.1, 8.8, 'свекла,свёкла,буряк', 100, NULL)",
    "('Перец болгарский', 'veg', 27, 1.3, 0, 5.3, 'болгарский перец,перец', 100, NULL)",
    "('Кабачок', 'veg', 24, 0.6, 0.3, 4.6, 'кабачок,кабачки,цукини', 150, NULL)",
    "('Баклажан', 'veg', 24, 1.2, 0.1, 4.5, 'баклажан,баклажаны', 150, NULL)",
    "('Тыква', 'veg', 22, 1, 0.1, 4.4, 'тыква', 150, NULL)",
    "('Картофель сырой', 'veg', 77, 2, 0.4, 16.3, 'сырой картофель,картофель сырой', 200, '$WEIGHT_RAW')",
    "('Шпинат', 'veg', 22, 2.9, 0.3, 2, 'шпинат', 50, NULL)",
    "('Салат листовой', 'veg', 14, 1.2, 0.3, 1.3, 'салат,листья салата,айсберг', 50, NULL)",
    "('Укроп', 'veg', 38, 2.5, 0.5, 6.3, 'укроп', 5, NULL)",
    "('Петрушка', 'veg', 49, 3.7, 0.4, 7.6, 'петрушка', 5, NULL)",
    "('Редис', 'veg', 19, 1.2, 0.1, 3.4, 'редис,редиска', 50, NULL)",
    "('Сельдерей стеблевой', 'veg', 13, 0.9, 0.1, 2.1, 'сельдерей', 50, NULL)",
    "('Фасоль стручковая', 'veg', 24, 2, 0.2, 3.6, 'стручковая фасоль', 150, NULL)",
    "('Шампиньоны', 'veg', 27, 4.3, 1, 0.1, 'шампиньоны,грибы', 100, NULL)",
    "('Кукуруза консервированная', 'veg', 58, 2.2, 0.4, 11.2, 'кукуруза', 50, NULL)",
    "('Горошек зелёный консервированный', 'veg', 40, 3.1, 0.2, 6.5, 'горошек,зеленый горошек', 50, NULL)",
    "('Апельсин', 'fruit', 43, 0.9, 0.2, 8.1, 'апельсин,апельсины', 150, NULL)",
    "('Мандарин', 'fruit', 38, 0.8, 0.2, 7.5, 'мандарин,мандарины', 80, NULL)",
    "('Лимон', 'fruit', 34, 0.9, 0.1, 3, 'лимон', 20, NULL)",
    "('Грейпфрут', 'fruit', 35, 0.7, 0.2, 6.5, 'грейпфрут', 150, NULL)",
    "('Груша', 'fruit', 47, 0.4, 0.3, 10.3, 'груша,груши', 150, NULL)",
    "('Персик', 'fruit', 45, 0.9, 0.1, 9.5, 'персик,персики', 120, NULL)",
    "('Абрикос', 'fruit', 44, 0.9, 0.1, 9, 'абрикос,абрикосы', 50, NULL)",
    "('Слива', 'fruit', 49, 0.8, 0.3, 9.6, 'слива,сливы', 30, NULL)",
    "('Виноград', 'fruit', 72, 0.6, 0.6, 15.4, 'виноград', 150, NULL)",
    "('Киви', 'fruit', 47, 0.8, 0.4, 8.1, 'киви', 75, NULL)",
    "('Ананас', 'fruit', 52, 0.4, 0.2, 11.5, 'ананас', 150, NULL)",
    "('Манго', 'fruit', 60, 0.8, 0.4, 15, 'манго', 150, NULL)",
    "('Хурма', 'fruit', 67, 0.5, 0.4, 15.3, 'хурма', 150, NULL)",
    "('Гранат', 'fruit', 72, 0.7, 0.6, 14.5, 'гранат', 150, NULL)",
    "('Арбуз', 'fruit', 27, 0.6, 0.1, 5.8, 'арбуз', 300, NULL)",
    "('Дыня', 'fruit', 35, 0.6, 0.3, 7.4, 'дыня', 200, NULL)",
    "('Авокадо', 'fruit', 160, 2, 14.7, 1.8, 'авокадо', 100, NULL)",
    "('Изюм', 'fruit', 264, 2.9, 0.6, 66, 'изюм', 30, NULL)",
    "('Курага', 'fruit', 232, 5.2, 0.3, 51, 'курага', 30, NULL)",
    "('Финики', 'fruit', 274, 2.5, 0.5, 69.2, 'финики,финик', 30, NULL)",
    "('Клубника', 'fruit', 41, 0.8, 0.4, 7.5, 'клубника,земляника', 150, NULL)",
    "('Малина', 'fruit', 46, 0.8, 0.5, 8.3, 'малина', 100, NULL)",
    "('Черника', 'fruit', 44, 1.1, 0.4, 7.6, 'черника', 100, NULL)",
    "('Голубика', 'fruit', 39, 1, 0.5, 6.6, 'голубика', 100, NULL)",
    "('Вишня', 'fruit', 52, 0.8, 0.2, 10.6, 'вишня', 100, NULL)",
    "('Черешня', 'fruit', 52, 1.1, 0.4, 10.6, 'черешня', 100, NULL)",
    "('Смородина чёрная', 'fruit', 44, 1, 0.4, 7.3, 'смородина', 100, NULL)",
    "('Клюква', 'fruit', 28, 0.5, 0.2, 3.7, 'клюква', 50, NULL)",
    "('Ежевика', 'fruit', 34, 2, 0, 4.4, 'ежевика', 100, NULL)",
    "('Крыжовник', 'fruit', 44, 0.7, 0.2, 9.1, 'крыжовник', 100, NULL)",
    "('Творог 0%', 'dairy', 71, 16.5, 0, 1.3, 'обезжиренный творог', 200, NULL)",
    "('Творог 2%', 'dairy', 103, 18, 2, 3.3, '', 200, NULL)",
    "('Творог 9%', 'dairy', 159, 16.7, 9, 2, '', 200, NULL)",
    "('Творог 18%', 'dairy', 232, 14, 18, 2.8, 'жирный творог', 200, NULL)",
    "('Кефир 1%', 'dairy', 40, 3, 1, 4, '', 250, NULL)",
    "('Кефир 3,2%', 'dairy', 59, 2.9, 3.2, 4, '', 250, NULL)",
    "('Молоко 1,5%', 'dairy', 44, 2.8, 1.5, 4.7, '', 200, NULL)",
    "('Молоко 3,2%', 'dairy', 59, 2.9, 3.2, 4.7, '', 200, NULL)",
    "('Ряженка 2,5%', 'dairy', 54, 2.9, 2.5, 4.2, '', 250, NULL)",
    "('Ряженка 4%', 'dairy', 76, 2.8, 4, 4.2, 'ряженка', 250, NULL)",
    "('Йогурт натуральный 3,2%', 'dairy', 64, 5, 3.2, 3.5, 'натуральный йогурт', 150, NULL)",
    "('Сметана 10%', 'dairy', 115, 3, 10, 2.9, '', 20, NULL)",
    "('Сметана 20%', 'dairy', 206, 2.8, 20, 3.2, '', 20, NULL)",
    "('Сливки 10%', 'dairy', 118, 3, 10, 4, 'сливки', 20, NULL)",
    "('Сливки 20%', 'dairy', 205, 2.8, 20, 3.7, '', 20, NULL)",
    "('Моцарелла', 'dairy', 280, 22, 22, 0, 'моцарелла', 30, NULL)",
    "('Сыр адыгейский', 'dairy', 264, 19.8, 19.8, 1.5, 'адыгейский сыр', 30, NULL)",
    "('Брынза', 'dairy', 260, 17.9, 20.1, 0, 'брынза,фета', 30, NULL)",
    "('Булгур сухой', 'grain', 342, 12.3, 1.3, 63.4, 'булгур', 70, '$WEIGHT_DRY')",
    "('Пшено сухое', 'grain', 348, 11.5, 3.3, 66.5, 'пшено,пшенная крупа', 70, '$WEIGHT_DRY')",
    "('Перловка сухая', 'grain', 315, 9.3, 1.1, 66.9, 'перловка,перловая крупа', 70, '$WEIGHT_DRY')",
    "('Кускус сухой', 'grain', 376, 12.8, 0.6, 72.4, 'кускус', 70, '$WEIGHT_DRY')",
    "('Киноа сухая', 'grain', 368, 14.1, 6.1, 57.2, 'киноа', 70, '$WEIGHT_DRY')",
    "('Манка сухая', 'grain', 328, 10.3, 1, 70.6, 'манка,манная крупа', 50, '$WEIGHT_DRY')",
    "('Ячневая крупа', 'grain', 313, 10, 1.3, 65.4, 'ячневая крупа,ячка', 70, '$WEIGHT_DRY')",
    "('Рис бурый сухой', 'grain', 337, 7.4, 1.8, 72.9, 'бурый рис', 70, '$WEIGHT_DRY')",
    "('Чечевица сухая', 'grain', 295, 24, 1.5, 46.3, 'чечевица', 70, '$WEIGHT_DRY')",
    "('Нут сухой', 'grain', 309, 20.1, 4.3, 46.2, 'нут', 70, '$WEIGHT_DRY')",
    "('Фасоль сухая', 'grain', 298, 21, 2, 47, 'фасоль', 70, '$WEIGHT_DRY')",
    "('Горох колотый сухой', 'grain', 298, 20.5, 2, 49.5, 'горох', 70, '$WEIGHT_DRY')",
    "('Мука пшеничная', 'grain', 334, 10.3, 1.1, 69.9, 'мука', 30, '$WEIGHT_DRY')",
    "('Лаваш тонкий', 'grain', 236, 7.9, 1, 47.6, 'лаваш', 60, NULL)",
    "('Свинина нежирная сырая', 'meat', 142, 19.4, 7.1, 0, 'свинина', 150, '$WEIGHT_RAW')",
    "('Говяжий фарш сырой', 'meat', 254, 17.2, 20, 0, 'фарш,говяжий фарш', 150, '$WEIGHT_RAW')",
    "('Куриный фарш сырой', 'meat', 143, 17.4, 8.1, 0, 'куриный фарш', 150, '$WEIGHT_RAW')",
    "('Фарш свино-говяжий сырой', 'meat', 263, 15, 22.5, 0, 'домашний фарш,смешанный фарш', 150, '$WEIGHT_RAW')",
    "('Куриное бедро без кожи сырое', 'meat', 121, 19.7, 4.1, 0, 'бедро,куриное бедро', 150, '$WEIGHT_RAW')",
    "('Куриное бедро с кожей сырое', 'meat', 221, 15.5, 17.6, 0, 'бедро с кожей', 150, '$WEIGHT_RAW')",
    "('Телятина сырая', 'meat', 97, 19.7, 2, 0, 'телятина', 150, '$WEIGHT_RAW')",
    "('Баранина сырая', 'meat', 209, 15.6, 16.3, 0, 'баранина', 150, '$WEIGHT_RAW')",
    "('Печень говяжья сырая', 'meat', 127, 17.9, 3.7, 5.3, 'говяжья печень,печень', 150, '$WEIGHT_RAW')",
    "('Печень куриная сырая', 'meat', 137, 20.4, 5.9, 0.7, 'куриная печень', 150, '$WEIGHT_RAW')",
    "('Лосось сырой', 'meat', 208, 20.4, 13.4, 0, 'лосось,семга,сёмга', 150, '$WEIGHT_RAW')",
    "('Горбуша сырая', 'meat', 140, 20.5, 6.5, 0, 'горбуша', 150, '$WEIGHT_RAW')",
    "('Треска сырая', 'meat', 69, 16, 0.6, 0, 'треска', 150, '$WEIGHT_RAW')",
    "('Минтай сырой', 'meat', 72, 15.9, 0.9, 0, 'минтай', 150, '$WEIGHT_RAW')",
    "('Тилапия сырая', 'meat', 96, 20.1, 1.7, 0, 'тилапия', 150, '$WEIGHT_RAW')",
    "('Скумбрия сырая', 'meat', 191, 18, 13.2, 0, 'скумбрия', 150, '$WEIGHT_RAW')",
    "('Креветки варёные', 'meat', 99, 24, 0.3, 0.2, 'креветки', 100, NULL)",
    "('Сельдь слабосолёная', 'meat', 217, 19.8, 15.4, 0, 'селедка,сельдь', 50, NULL)",
    "('Крабовые палочки', 'meat', 73, 6, 1, 10, 'крабовые палочки', 100, NULL)",
    "('Колбаса варёная', 'meat', 257, 12.8, 22.2, 1.5, 'докторская,колбаса', 50, NULL)",
    "('Сосиски', 'meat', 266, 11, 23.9, 1.6, 'сосиски,сосиска', 100, NULL)",
    "('Яичный белок', 'egg', 44, 11.1, 0, 0, 'белок яйца,яичные белки', 33, NULL)"
)

/** Базовые продукты, которых ещё нет в базе (по названию). */
private fun seedBasic(db: SQLiteConnection) {
    val cols = "name, category, kcal, protein, fat, carbs, aliases, defaultGrams, weightState, perPiece, useCount, lastUsed, isDish, portions, source"
    BASIC_ROWS.forEach { row ->
        val name = row.substringAfter("('").substringBefore("'")
        db.execSQL("INSERT INTO product ($cols) SELECT ${row.removePrefix("(").removeSuffix(")")}, 0, 0, 0, 0, '', '$SOURCE_SEED' WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = '$name')")
    }
}

/** Отметить сухой / сырой / готовый вес у продуктов без отметки — по названию ([WeightState.guess]). */
private fun markWeightStates(db: SQLiteConnection) {
    val found = mutableListOf<Pair<Long, String>>()
    val sel = db.prepare("SELECT id, name FROM product WHERE weightState IS NULL")
    try {
        while (sel.step()) WeightState.guess(sel.getText(1))?.let { found += sel.getLong(0) to it }
    } finally { sel.close() }
    found.forEach { (id, state) ->
        val upd = db.prepare("UPDATE product SET weightState = ? WHERE id = ?")
        try { upd.bindText(1, state); upd.bindLong(2, id); upd.step() } finally { upd.close() }
    }
}

private fun seed(db: SQLiteConnection) {
    val cols = "name, category, kcal, protein, fat, carbs, perPiece, pieceGrams, aliases, defaultGrams, useCount, lastUsed"
    SEED_ROWS.forEach { db.execSQL("INSERT INTO product ($cols) VALUES $it") }
}

/** 0.2 → 0.3: добавились продукты и дневник. Таблица веса не трогается, записи сохраняются. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `product` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `kcal` REAL NOT NULL, `protein` REAL NOT NULL, `fat` REAL NOT NULL, `carbs` REAL NOT NULL, `perPiece` INTEGER NOT NULL, `pieceGrams` REAL, `aliases` TEXT NOT NULL, `defaultGrams` REAL NOT NULL, `useCount` INTEGER NOT NULL, `lastUsed` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `food_entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `minute` INTEGER NOT NULL, `meal` TEXT NOT NULL, `productId` INTEGER NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `grams` REAL NOT NULL, `pieces` REAL, `kcal` REAL NOT NULL, `protein` REAL NOT NULL, `fat` REAL NOT NULL, `carbs` REAL NOT NULL)")
        seed(connection)
    }
}

/** 0.3 → 0.4: блюда. Продукту добавлены признак блюда, вес готового и рецепт; новая таблица состава. Данные не трогаются. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `product` ADD COLUMN `isDish` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `product` ADD COLUMN `cookedGrams` REAL")
        connection.execSQL("ALTER TABLE `product` ADD COLUMN `recipe` TEXT")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `dish_ingredient` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dishId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `grams` REAL NOT NULL)")
    }
}

/** 0.4: штрихкод и источник данных продукта. Встроенные продукты помечаются как встроенные, остальные — как введённые вручную. */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `product` ADD COLUMN `barcode` TEXT")
        connection.execSQL("ALTER TABLE `product` ADD COLUMN `source` TEXT")
        connection.execSQL("UPDATE `product` SET `source` = '$SOURCE_SEED' WHERE `id` <= ${SEED_ROWS.size}")
        connection.execSQL("UPDATE `product` SET `source` = '$SOURCE_MANUAL' WHERE `id` > ${SEED_ROWS.size}")
    }
}

/** 0.4: фото продукта и блюда — только новая пустая колонка. */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `product` ADD COLUMN `photo` TEXT")
    }
}

/** 0.4: именованные порции продукта — новая колонка, по умолчанию пустая. */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `product` ADD COLUMN `portions` TEXT NOT NULL DEFAULT ''")
    }
}

/** 0.5: журнал тренировок — новая таблица, остальное не трогаем. */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `training` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `epochDay` INTEGER NOT NULL, `type` TEXT NOT NULL, `minutes` INTEGER NOT NULL)")
    }
}

/** 0.6: сухой / сырой / готовый вес у продукта — новая колонка, отметки по названию; сухие крупы и сырое мясо во встроенную базу. */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `product` ADD COLUMN `weightState` TEXT")
        markWeightStates(connection)
        seedDry(connection)
    }
}

/** 0.6.6: базовые овощи, фрукты, ягоды, кисломолочное, крупы и мясо — только новые строки, схема та же. */
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        seedBasic(connection)
    }
}

@Database(entities = [WeightEntry::class, Product::class, FoodEntry::class, DishIngredient::class, TrainingLog::class], version = 9, exportSchema = false)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weightDao(): WeightDao
    abstract fun productDao(): ProductDao
    abstract fun foodDao(): FoodDao
    abstract fun dishDao(): DishDao
    abstract fun trainingDao(): TrainingDao
}

/** Room сам генерирует `actual` для каждой платформы. */
@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Путь к файлу `fitfat.db` на платформе — тот же, что был у Android-версии, данные сохраняются. */
expect fun databaseBuilder(): RoomDatabase.Builder<AppDatabase>

/** Одна база на приложение: миграции без потери данных, встроенный SQLite на обеих платформах. */
fun createDatabase(): AppDatabase = databaseBuilder()
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
    .addCallback(object : RoomDatabase.Callback() {
        override fun onCreate(connection: SQLiteConnection) {
            seed(connection)
            connection.execSQL("UPDATE `product` SET `source` = '$SOURCE_SEED'")
            markWeightStates(connection)
            seedDry(connection)
            seedBasic(connection)
        }
    })
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(app.fitfat.platform.ioDispatcher)
    .build()

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
