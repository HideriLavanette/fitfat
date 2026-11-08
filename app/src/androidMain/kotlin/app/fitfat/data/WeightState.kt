package app.fitfat.data

/**
 * В каком виде взвешен продукт, для которого указаны КБЖУ на 100 г (DESIGN.md, «Сухой и готовый вес»):
 * сухой (крупа, макароны), сырой (мясо, рыба) или готовый (варёный, жареный). У готового цифры сильно зависят
 * от воды и масла, поэтому точнее считать по сухому или сырому весу. null — не важно (хлеб, молочное, фрукты).
 */
const val WEIGHT_DRY = "dry"
const val WEIGHT_RAW = "raw"
const val WEIGHT_COOKED = "cooked"

object WeightState {
    val all = listOf(WEIGHT_DRY, WEIGHT_RAW, WEIGHT_COOKED)

    /** Короткая метка: «Сухой вес». */
    fun label(state: String?): String? = when (state) {
        WEIGHT_DRY -> "Сухой вес"
        WEIGHT_RAW -> "Сырой вес"
        WEIGHT_COOKED -> "Готовый вес"
        else -> null
    }

    /** Одно слово для строки ингредиента и переключателя: «сухой». */
    fun word(state: String?): String? = when (state) {
        WEIGHT_DRY -> "сухой"
        WEIGHT_RAW -> "сырой"
        WEIGHT_COOKED -> "готовый"
        else -> null
    }

    /** Пояснение под КБЖУ в карточке продукта. */
    fun hint(state: String?): String? = when (state) {
        WEIGHT_DRY -> "КБЖУ — на 100 г сухого продукта. Взвешивайте до варки: так точнее всего, после готовки вес меняется в 2–3 раза."
        WEIGHT_RAW -> "КБЖУ — на 100 г сырого продукта. Взвешивайте до готовки: так точнее всего."
        WEIGHT_COOKED -> "КБЖУ — на 100 г приготовленного. У готового цифры могут сильно отличаться: всё зависит от воды, масла и времени готовки. Точнее взвешивать сухим или сырым, до готовки."
        else -> null
    }

    private val COOKED = listOf("варен", "отварн", "жарен", "гриль", "тушен", "запечен", "на воде", "на молоке", "каша", "копчен")
    private val DRY = Regex("""(^|\s)(сух(ой|ая|ое|ие)|крупа|хлопья)(\s|,|$)""")
    private val RAW = Regex("""(^|\s)сыр(ой|ая|ое|ые)(\s|,|$)""")

    /** Догадка по названию: «Гречка варёная» → готовый, «Рис сухой» → сухой, «Говядина сырая» → сырой, иначе null. */
    fun guess(name: String): String? {
        val n = name.lowercase().replace('ё', 'е')
        return when {
            // Яйцо весит одинаково до и после варки — отметка ни к чему
            n.startsWith("яйц") -> null
            DRY.containsMatchIn(n) -> WEIGHT_DRY
            RAW.containsMatchIn(n) -> WEIGHT_RAW
            COOKED.any { it in n } -> WEIGHT_COOKED
            else -> null
        }
    }
}
