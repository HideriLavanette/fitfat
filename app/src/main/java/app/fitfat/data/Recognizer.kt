package app.fitfat.data

/**
 * Разбор свободного текста «гречка 200, грудка гриль 150г, 2 яйца» на фрагменты
 * и поиск продуктов по названиям и синонимам. Всё локально, без сети.
 */
data class Fragment(
    val raw: String, val query: String, val grams: Double?, val pieces: Double?,
    /** Позиция [raw] в исходном тексте — чтобы показать подсказку над словом. */
    val start: Int = 0,
) {
    val end: Int get() = start + raw.length
}

data class Candidate(val product: Product, val score: Int)

object Recognizer {
    private val SPLIT = Regex("""\s*(?:[,;\n]|\s+и\s+)\s*""")
    private val QTY = Regex("""(\d+(?:[.,]\d+)?)\s*(кг|килограмм\w*|г|гр|грамм\w*|мл|л|шт\w*|штук\w*)?\.?""", RegexOption.IGNORE_CASE)

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
        var query = raw
        if (m != null) {
            val n = m.groupValues[1].replace(',', '.').toDoubleOrNull()
            val unit = m.groupValues[2].lowercase()
            when {
                n == null -> {}
                unit.startsWith("кг") || unit.startsWith("килог") || unit == "л" -> grams = n * 1000
                unit.startsWith("шт") -> pieces = n
                unit.isEmpty() && n <= 10 && raw.trim().startsWith(m.value.trim()) -> pieces = n // «2 яйца»
                else -> grams = n
            }
            query = raw.removeRange(m.range)
        }
        return Fragment(raw, normalize(query), grams, pieces)
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
}
