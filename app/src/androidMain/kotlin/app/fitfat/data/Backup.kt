package app.fitfat.data

import app.fitfat.time.*
import app.fitfat.time.Fmt
import android.content.ContentValues
import android.database.Cursor
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.datetime.LocalDate

/**
 * Экспорт и импорт данных (макет Settings, «Данные»). JSON — все таблицы целиком (вес, продукты, состав блюд,
 * дневник, тренировки); CSV — дневник еды для таблиц. Импорт JSON заменяет данные в этих таблицах — только после подтверждения.
 */
object Backup {
    private val TABLES = listOf("weight", "product", "dish_ingredient", "food_entry", "training")

    fun exportJson(db: AppDatabase): String {
        val sql = db.openHelper.readableDatabase
        val root = JSONObject().put("app", "fitfat").put("format", 1).put("exported", LocalDate.now().toString())
        TABLES.forEach { t ->
            val arr = JSONArray()
            sql.query("SELECT * FROM $t").use { c ->
                while (c.moveToNext()) {
                    val o = JSONObject()
                    for (i in 0 until c.columnCount) {
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> o.put(c.getColumnName(i), JSONObject.NULL)
                            Cursor.FIELD_TYPE_INTEGER -> o.put(c.getColumnName(i), c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> o.put(c.getColumnName(i), c.getDouble(i))
                            else -> o.put(c.getColumnName(i), c.getString(i))
                        }
                    }
                    arr.put(o)
                }
            }
            root.put(t, arr)
        }
        return root.toString(1)
    }

    fun exportCsv(db: AppDatabase): String {
        val sb = StringBuilder("Дата;Время;Приём;Продукт;Граммы;Ккал;Белки;Жиры;Углеводы\n")
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        fun n(v: Double) = Fmt.f1(v)
        db.openHelper.readableDatabase.query("SELECT epochDay, minute, meal, name, grams, kcal, protein, fat, carbs FROM food_entry ORDER BY epochDay, minute").use { c ->
            while (c.moveToNext()) {
                val m = c.getInt(1)
                sb.append(LocalDate.ofEpochDay(c.getLong(0))).append(';').append(Fmt.hhmm(m / 60, m % 60)).append(';')
                    .append(Meal.of(c.getString(2)).label).append(';').append(q(c.getString(3))).append(';')
                    .append(n(c.getDouble(4))).append(';').append(n(c.getDouble(5))).append(';').append(n(c.getDouble(6))).append(';')
                    .append(n(c.getDouble(7))).append(';').append(n(c.getDouble(8))).append('\n')
            }
        }
        return sb.toString()
    }

    /** Сколько записей в файле: проверка перед подтверждением. null — это не файл FitFat. */
    fun peek(json: String): Map<String, Int>? = runCatching {
        val root = JSONObject(json)
        if (root.optString("app") != "fitfat") return null
        TABLES.associateWith { root.optJSONArray(it)?.length() ?: 0 }
    }.getOrNull()

    /** Заменить данные таблиц содержимым файла — одной транзакцией: при ошибке ничего не меняется. */
    fun importJson(db: AppDatabase, json: String) {
        val root = JSONObject(json)
        require(root.optString("app") == "fitfat")
        val sql = db.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            TABLES.forEach { t ->
                val arr = root.optJSONArray(t) ?: return@forEach
                val cols = sql.query("SELECT * FROM $t LIMIT 0").use { it.columnNames.toSet() }
                sql.delete(t, null, null)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val cv = ContentValues()
                    o.keys().forEach { k ->
                        if (k !in cols) return@forEach
                        when (val v = o.get(k)) {
                            JSONObject.NULL -> cv.putNull(k)
                            is Int -> cv.put(k, v.toLong())
                            is Long -> cv.put(k, v)
                            is Double -> cv.put(k, v)
                            is Boolean -> cv.put(k, if (v) 1L else 0L)
                            else -> cv.put(k, v.toString())
                        }
                    }
                    sql.insert(t, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        // Room узнаёт об изменениях и обновляет экраны
        db.invalidationTracker.refreshVersionsAsync()
    }
}
