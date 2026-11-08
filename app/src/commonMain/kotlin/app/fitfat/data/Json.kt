package app.fitfat.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/* Чтение JSON в духе org.json: optString / optDouble / isNull, без исключений на отсутствующих полях. */

/** Строка поля или пусто; число тоже вернётся строкой. */
fun JsonObject.optString(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull ?: ""

fun JsonObject.optDouble(key: String, def: Double = Double.NaN): Double = (this[key] as? JsonPrimitive)?.doubleOrNull ?: def

fun JsonObject.optInt(key: String, def: Int = 0): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: def

fun JsonObject.isNull(key: String): Boolean = this[key].let { it == null || it is JsonNull }

fun JsonObject.optObject(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.optArray(key: String): JsonArray? = this[key] as? JsonArray

fun JsonArray.optObject(i: Int): JsonObject? = getOrNull(i) as? JsonObject

fun JsonArray.optString(i: Int): String = (getOrNull(i) as? JsonPrimitive)?.contentOrNull ?: ""

fun JsonElement?.orNull(): JsonElement = this ?: JsonNull
