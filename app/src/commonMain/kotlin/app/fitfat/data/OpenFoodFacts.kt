package app.fitfat.data

import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Продукт из Open Food Facts: название и КБЖУ на 100 г. */
data class OffProduct(val name: String, val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

/**
 * Поиск по штрихкоду в открытой базе Open Food Facts. Отправляется только номер штрихкода.
 * null — не нашли, нет сети или нет КБЖУ.
 */
object OpenFoodFacts {
    suspend fun lookup(code: String): OffProduct? = runCatching {
        val resp = Http.client.get("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,product_name_ru,brands,nutriments") {
            header("User-Agent", "FitFat")
            timeout { connectTimeoutMillis = 6000; requestTimeoutMillis = 8000 }
        }
        if (resp.status != HttpStatusCode.OK) return@runCatching null
        val root = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
        if (root.optInt("status") != 1) return@runCatching null
        val p = root.optObject("product") ?: return@runCatching null
        val n = p.optObject("nutriments") ?: return@runCatching null
        fun num(k: String) = n.optDouble(k)
        val prot = num("proteins_100g"); val fat = num("fat_100g"); val carb = num("carbohydrates_100g")
        var kcal = num("energy-kcal_100g")
        if (kcal.isNaN()) num("energy_100g").takeIf { !it.isNaN() }?.let { kcal = it / 4.184 } // кДж
        if (listOf(prot, fat, carb).all { it.isNaN() } && kcal.isNaN()) return@runCatching null
        val name = listOf(p.optString("product_name_ru"), p.optString("product_name")).firstOrNull { it.isNotBlank() }
            ?.let { nm -> p.optString("brands").split(',').firstOrNull()?.trim()?.takeIf { it.isNotBlank() && !nm.contains(it, true) }?.let { "$nm, $it" } ?: nm }
            ?: return@runCatching null
        fun z(v: Double) = if (v.isNaN()) 0.0 else v
        OffProduct(name.trim(), if (kcal.isNaN()) z(prot) * 4 + z(fat) * 9 + z(carb) * 4 else kcal, z(prot), z(fat), z(carb))
    }.getOrNull()
}
