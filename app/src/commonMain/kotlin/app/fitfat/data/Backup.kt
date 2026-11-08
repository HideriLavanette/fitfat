package app.fitfat.data

import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import app.fitfat.time.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Экспорт и импорт данных (макет Settings, «Данные»). JSON — все таблицы целиком (вес, продукты, состав блюд,
 * дневник, тренировки) и настройки ([PREFS]: нормы и отметки дней, профиль, тренировки, настройки, подсказки); CSV — дневник
 * еды для таблиц. Импорт JSON заменяет данные — только после подтверждения. Тот же JSON — копия в Google Диске ([CloudBackup]).
 * Фото продуктов в JSON не входят — остаются на телефоне (решение 09.11.2026).
 */
object Backup {
    private val TABLES = listOf("weight", "product", "dish_ingredient", "food_entry", "training")
    /** Файлы настроек в копии. Без входа, черновика «Добавить», согласий ассистента и обновлений — они про этот телефон. */
    private val PREFS = listOf("norms", "profile", "settings", "training", "weight", "choices", "hints")
    private val json = Json { prettyPrint = true; prettyPrintIndent = " " }

    suspend fun exportJson(db: AppDatabase): String {
        val tables = db.useReaderConnection { tx ->
            TABLES.associateWith { t ->
                tx.usePrepared("SELECT * FROM $t") { st ->
                    val rows = mutableListOf<JsonObject>()
                    while (st.step()) {
                        val o = LinkedHashMap<String, JsonElement>()
                        for (i in 0 until st.getColumnCount()) {
                            o[st.getColumnName(i)] = when (st.getColumnType(i)) {
                                SQLITE_DATA_NULL -> JsonNull
                                SQLITE_DATA_INTEGER -> JsonPrimitive(st.getLong(i))
                                SQLITE_DATA_FLOAT -> JsonPrimitive(st.getDouble(i))
                                else -> JsonPrimitive(st.getText(i))
                            }
                        }
                        rows += JsonObject(o)
                    }
                    JsonArray(rows)
                }
            }
        }
        val root = LinkedHashMap<String, JsonElement>()
        root["app"] = JsonPrimitive("fitfat"); root["format"] = JsonPrimitive(2); root["exported"] = JsonPrimitive(LocalDate.now().toString())
        root.putAll(tables)
        root["prefs"] = exportPrefs()
        return json.encodeToString(JsonObject.serializer(), JsonObject(root))
    }

    suspend fun exportCsv(db: AppDatabase): String {
        val sb = StringBuilder("Дата;Время;Приём;Продукт;Граммы;Ккал;Белки;Жиры;Углеводы\n")
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        fun n(v: Double) = Fmt.f1(v)
        db.useReaderConnection { tx ->
            tx.usePrepared("SELECT epochDay, minute, meal, name, grams, kcal, protein, fat, carbs FROM food_entry ORDER BY epochDay, minute") { st ->
                while (st.step()) {
                    val m = st.getLong(1).toInt()
                    sb.append(LocalDate.ofEpochDay(st.getLong(0))).append(';').append(Fmt.hhmm(m)).append(';')
                        .append(Meal.of(st.getText(2)).label).append(';').append(q(st.getText(3))).append(';')
                        .append(n(st.getDouble(4))).append(';').append(n(st.getDouble(5))).append(';').append(n(st.getDouble(6))).append(';')
                        .append(n(st.getDouble(7))).append(';').append(n(st.getDouble(8))).append('\n')
                }
            }
        }
        return sb.toString()
    }

    /** Сколько записей в файле: проверка перед подтверждением. null — это не файл FitFat. */
    fun peek(text: String): Map<String, Int>? = runCatching {
        val root = Json.parseToJsonElement(text).jsonObject
        if (root["app"]?.jsonPrimitive?.content != "fitfat") return null
        TABLES.associateWith { (root[it] as? JsonArray)?.size ?: 0 }
    }.getOrNull()

    /** Заменить данные таблиц содержимым файла — одной транзакцией: при ошибке ничего не меняется. */
    suspend fun importJson(db: AppDatabase, text: String) {
        val root = Json.parseToJsonElement(text).jsonObject
        require(root["app"]?.jsonPrimitive?.content == "fitfat")
        db.useWriterConnection { tx ->
            tx.immediateTransaction {
                TABLES.forEach { t ->
                    val arr = root[t]?.jsonArray ?: return@forEach
                    val cols = usePrepared("SELECT * FROM $t LIMIT 0") { st -> (0 until st.getColumnCount()).map { st.getColumnName(it) } }.toSet()
                    usePrepared("DELETE FROM $t") { it.step() }
                    for (el in arr) {
                        val o = el.jsonObject
                        val keys = o.keys.filter { it in cols }
                        if (keys.isEmpty()) continue
                        val sql = "INSERT OR REPLACE INTO $t (${keys.joinToString(", ") { "`$it`" }}) VALUES (${keys.joinToString(", ") { "?" }})"
                        usePrepared(sql) { st ->
                            keys.forEachIndexed { i, k ->
                                val idx = i + 1
                                when (val v = o[k]) {
                                    null, JsonNull -> st.bindNull(idx)
                                    is JsonPrimitive -> when {
                                        v.isString -> st.bindText(idx, v.content)
                                        v.booleanOrNull != null -> st.bindLong(idx, if (v.booleanOrNull == true) 1 else 0)
                                        v.longOrNull != null -> st.bindLong(idx, v.longOrNull!!)
                                        v.doubleOrNull != null -> st.bindDouble(idx, v.doubleOrNull!!)
                                        else -> st.bindText(idx, v.content)
                                    }
                                    else -> st.bindText(idx, v.toString())
                                }
                            }
                            st.step()
                        }
                    }
                }
            }
        }
        (root["prefs"] as? JsonObject)?.let(::importPrefs)
        // Room узнаёт об изменениях и обновляет экраны
        db.invalidationTracker.refreshAsync()
    }

    /** Настройки: файл → ключ → {тип: значение}, тип i/l/f/b/s/ss — чтобы вернуть ровно тем же типом. */
    private fun exportPrefs(): JsonObject = JsonObject(PREFS.associateWith { name ->
        JsonObject(app.fitfat.platform.Prefs(name).all().mapNotNull { (k, v) ->
            val (t, el) = when (v) {
                is Int -> "i" to JsonPrimitive(v)
                is Long -> "l" to JsonPrimitive(v)
                is Float -> "f" to JsonPrimitive(v)
                is Double -> "f" to JsonPrimitive(v)
                is Boolean -> "b" to JsonPrimitive(v)
                is String -> "s" to JsonPrimitive(v)
                is Set<*> -> "ss" to JsonArray(v.map { JsonPrimitive(it.toString()) })
                else -> return@mapNotNull null
            }
            k to JsonObject(mapOf(t to el))
        }.toMap())
    })

    private fun importPrefs(all: JsonObject) {
        for (name in PREFS) {
            val keys = all[name] as? JsonObject ?: continue
            app.fitfat.platform.Prefs(name).edit {
                clear()
                for ((k, el) in keys) {
                    val (t, v) = (el as? JsonObject)?.entries?.firstOrNull() ?: continue
                    runCatching {
                        when (t) {
                            "i" -> putInt(k, v.jsonPrimitive.content.toDouble().toInt())
                            "l" -> putLong(k, v.jsonPrimitive.content.toDouble().toLong())
                            "f" -> putFloat(k, v.jsonPrimitive.content.toFloat())
                            "b" -> putBoolean(k, v.jsonPrimitive.content.toBoolean())
                            "s" -> putString(k, v.jsonPrimitive.content)
                            "ss" -> putStringSet(k, v.jsonArray.map { it.jsonPrimitive.content }.toSet())
                        }
                    }
                }
            }
        }
    }

    /** На телефоне ещё нет своих записей — копию можно предложить восстановить сразу после входа. */
    suspend fun isEmpty(db: AppDatabase): Boolean = db.useReaderConnection { tx ->
        listOf("food_entry", "weight").all { t -> tx.usePrepared("SELECT COUNT(*) FROM $t") { st -> st.step(); st.getLong(0) == 0L } }
    }
}
