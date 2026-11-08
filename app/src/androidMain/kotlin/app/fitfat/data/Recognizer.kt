package app.fitfat.data

/**
 * Разбор свободного текста «гречка 200, грудка гриль 150г, 2 яйца» на фрагменты
 * и поиск продуктов по названиям и синонимам. Всё локально, без сети.
 */
/** Что написано после числа, кроме граммов и штук: «20 б», «14у», «300 ккал». */
enum class Nutrient { Kcal, Protein, Fat, Carbs }

data class NutrientTarget(val nutrient: Nutrient, val amount: Double)

data class Fragment(
    val raw: String, val query: String, val grams: Double?, val pieces: Double?,
    /** Позиция [raw] в исходном тексте — чтобы показать подсказку над словом. */
    val start: Int = 0,
    /** «огурец 14 у» — съесть столько, чтобы вышло 14 г углеводов. */
    val target: NutrientTarget? = null,
) {
    val end: Int get() = start + raw.length
    /** Количество написано — граммы, штуки или нутриент. */
    val hasAmount: Boolean get() = grams != null || pieces != null || target != null
}

data class Candidate(val product: Product, val score: Int)

object Recognizer {
    private val SPLIT = Regex("""\s*(?:[,;\n]|\s+и\s+)\s*""")
    /**
     * Число и необязательная единица сразу после него (можно слитно: «200г», «14у»).
     * Единица — целое слово: «2 гречки» не превращается в «2 г» + «речки».
     */
    private val QTY = Regex(
        """(\d+(?:[.,]\d+)?)\s*(кг|килограмм\w*|гр|грамм\w*|г|мл|л|шт\w*|штук\w*|ккал|кк|калори\w*|кал|белк\w*|бел|б|жир\w*|ж|углевод\w*|угл|у)?(?![а-яёa-z])\.?""",
        RegexOption.IGNORE_CASE,
    )

    fun split(text: String): List<Fragment> {
        val parts = mutableListOf<IntRange>()
        var from = 0
        for (m in SPLIT.findAll(text)) { parts += from until m.range.first; from = m.range.last + 1 }
        parts += from until text.length
        return parts.mapNotNull { r ->
            val s = text.substring(r.first, r.last + 1)
            val t = s.trim()
            if (t.isEmpty()) null else parse(t).copy(start = r.first + (s.length - s.trimStart().length))
        }
    }

    private fun parse(raw: String): Fragment {
        val m = QTY.find(raw)
        var grams: Double? = null
        var pieces: Double? = null
        var target: NutrientTarget? = null
        var query = raw
        if (m != null) {
            val n = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            val unit = m.groupValues[2].lowercase()
            when {
                n == null -> {}
                unit.startsWith("кг") || unit.startsWith("килог") || unit == "л" -> grams = n * 1000
                unit.startsWith("шт") -> pieces = n
                unit.startsWith("к") -> target = NutrientTarget(Nutrient.Kcal, n)
                unit.startsWith("б") -> target = NutrientTarget(Nutrient.Protein, n)
                unit.startsWith("ж") -> target = NutrientTarget(Nutrient.Fat, n)
                unit.startsWith("у") -> target = NutrientTarget(Nutrient.Carbs, n)
                unit.isEmpty() && n <= 10 && raw.trim().startsWith(m.value.trim()) -> pieces = n // «2 яйца»
                else -> grams = n
            }
            query = raw.removeRange(m.range)
        }
        return Fragment(raw, normalize(query), grams, pieces, target = target)
    }

    fun normalize(s: String) = s.lowercase().replace('ё', 'е').replace(Regex("[^а-яa-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Грубый стемминг: русские слова сравниваются по первым 4–5 буквам («гречки» ≈ «гречка»). */
    private fun stem(w: String) = if (w.length <= 4) w else w.take(if (w.length >= 7) 5 else 4)

    fun candidates(f: Fragment, products: List<Product>, limit: Int = 4): List<Candidate> {
        if (f.query.isBlank()) return emptyList()
        val q = f.query
        val qStems = q.split(' ').filter { it.isNotBlank() }.map(::stem)
        return products.mapNotNull { p ->
            val names = (listOf(p.name) + p.aliases.split(',')).map(::normalize).filter { it.isNotBlank() }
            val best = names.maxOf { n ->
                when {
                    n == q -> 100
                    n.startsWith(q) || q.startsWith(n) -> 80
                    else -> {
                        val nStems = n.split(' ').map(::stem)
                        val hit = qStems.count { s -> nStems.any { it == s } }
                        if (hit == 0) 0 else 40 + 20 * hit - 5 * (nStems.size - hit).coerceAtLeast(0)
                    }
                }
            }
            if (best <= 0) null else Candidate(p, best + minOf(p.useCount, 20))
        }.sortedByDescending { it.score }.take(limit)
    }

    private val STOP = setOf("с", "со", "в", "во", "на", "и", "без", "из", "под", "по", "для", "шт", "гр", "г", "мл")

    /**
     * Слова запроса, которых нет в названии и синонимах продукта: «манты с говядиной» → «Говядина варёная» не покрывает
     * «манты». Значит, совпадение случайное — тут предлагаем ассистента (DESIGN.md, «Ассистент в „Добавить“»).
     */
    fun uncovered(f: Fragment, p: Product): List<String> {
        val names = (listOf(p.name) + p.aliases.split(',')).map(::normalize).flatMap { it.split(' ') }.filter { it.isNotBlank() }.map(::stem).toSet()
        return f.query.split(' ').filter { it.length >= 3 && it !in STOP && it.all(Char::isLetter) }.filter { stem(it) !in names }
    }

    /**
     * Порция продукта по фрагменту: граммы или штуки как написано, а для «14 у» — сколько съесть,
     * чтобы вышло 14 г углеводов (граммы до 1 г, штуки до 0,5). null, null — посчитать нельзя (в продукте 0 этого нутриента).
     */
    fun portionFor(f: Fragment, p: Product): Pair<Double?, Double?> {
        val t = f.target ?: return f.grams to f.pieces
        val per = when (t.nutrient) { Nutrient.Kcal -> p.kcal; Nutrient.Protein -> p.protein; Nutrient.Fat -> p.fat; Nutrient.Carbs -> p.carbs }
        if (per <= 0) return null to null
        return if (p.perPiece) null to (Math.round(t.amount / per * 2) / 2.0).coerceAtLeast(0.5)
        else Math.round(t.amount / per * 100).toDouble().coerceIn(1.0, 2000.0) to null
    }
}
