package app.fitfat.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import kotlin.math.roundToInt

/** Ответы первой настройки (макеты 6.1–6.4) и выбор на «Цель и нормы» (5.3). */
data class Profile(
    val sex: Sex = Sex.Male,
    val age: Int = 28,
    val heightCm: Int = 180,
    val weightKg: Double = 78.5,
    val bodyFatPct: Int = 18,
    val gym: Boolean = true,
    val sports: Set<String> = setOf("run"),
    /** Не спрашиваем и не учитываем (решение 08.11.2026); поле — для совместимости. */
    val activity: Activity = Activity.Light,
    val goal: Goal = Goal.Recomp,
    /** Белок на кг веса, выбранный на «Цель и нормы»; null — по методике. */
    val proteinPerKg: Double? = null,
    /** Целевой вес (похудение и набор), кг. */
    val targetKg: Double? = null,
) {
    /**
     * Интенсивно тренируется: зал или интенсивный спорт (всё, кроме йоги). Тогда есть день тренировки (+90 г углеводов)
     * и белок считается от общего веса. Йога и прогулки калорий не добавляют — они помогают худеть.
     */
    val trains: Boolean get() = gym || sports.any { it !in LOW_INTENSITY }
    fun body(weight: Double = weightKg) = Body(sex, weight, bodyFatPct.toDouble(), goal, trains)

    companion object {
        /** Стартовые значения макета для пола. */
        fun defaults(sex: Sex) = if (sex == Sex.Female) Profile(Sex.Female, 27, 166, 61.5, 27) else Profile()
    }
}

/** Нормы трёх типов дня. */
data class DayNorms(val normal: Macros, val training: Macros, val cycle: Macros) {
    operator fun get(id: String) = when (id) { "t" -> training; "c" -> cycle; else -> normal }
}

/** Нормы из профиля: методика [NormsCalc], выбранный белок на кг и срезы при застое (по 150 ккал). */
object NormsPlan {
    /** Белок на кг веса для барабана: выбранный или по методике (округлён до 0,1 в пределах 1,6–2,2). */
    fun proteinPerKg(p: Profile, weight: Double): Double = p.proteinPerKg
        ?: ((NormsCalc.defaultProtein(p.body(weight)) / weight * 10).roundToInt() / 10.0).coerceIn(NormsCalc.PROTEIN_MIN, NormsCalc.PROTEIN_MAX)

    fun calc(p: Profile, weight: Double, cuts: Int): DayNorms {
        val b = p.body(weight)
        val protein = p.proteinPerKg?.let { it * weight } ?: NormsCalc.defaultProtein(b)
        var n = NormsCalc.norms(b, protein = protein)
        repeat(cuts) { n = NormsCalc.cut(n, NormsCalc.lean(b)) ?: n }
        val cycle = n.copy(carbs = n.carbs + CYCLE_EXTRA_CARBS, kcal = n.kcal + CYCLE_EXTRA_CARBS * 4)
        return DayNorms(n, NormsCalc.trainingDay(b, n), cycle)
    }

    /** Итог с учётом своих значений по типам дня ("n", "t", "c"). */
    fun withCustom(plan: DayNorms, custom: Map<String, Macros>) =
        DayNorms(custom["n"] ?: plan.normal, custom["t"] ?: plan.training, custom["c"] ?: plan.cycle)
}

/** Профиль хранится в `profile`; [done] — первая настройка пройдена. [version] растёт при каждом сохранении. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    val done: Boolean get() = prefs.getBoolean("done", false)

    fun load(): Profile? {
        if (!prefs.contains("sex")) return null
        return Profile(
            sex = if (prefs.getString("sex", "m") == "f") Sex.Female else Sex.Male,
            age = prefs.getInt("age", 28),
            heightCm = prefs.getInt("h", 180),
            weightKg = prefs.getFloat("w", 78.5f).toDouble(),
            bodyFatPct = prefs.getInt("bf", 18),
            gym = prefs.getBoolean("gym", true),
            sports = prefs.getStringSet("sports", emptySet())!!.toSet(),
            activity = Activity.entries.getOrNull(prefs.getInt("act", 1)) ?: Activity.Light,
            goal = Goal.entries.firstOrNull { it.name == prefs.getString("goal", null) } ?: Goal.Recomp,
            proteinPerKg = prefs.getFloat("pk", -1f).takeIf { it > 0 }?.let { (it * 10).roundToInt() / 10.0 },
            targetKg = prefs.getFloat("target", -1f).takeIf { it > 0 }?.toDouble(),
        )
    }

    fun save(p: Profile) {
        prefs.edit {
            putString("sex", if (p.sex == Sex.Female) "f" else "m")
            putInt("age", p.age); putInt("h", p.heightCm); putFloat("w", p.weightKg.toFloat()); putInt("bf", p.bodyFatPct)
            putBoolean("gym", p.gym); putStringSet("sports", p.sports)
            putInt("act", p.activity.ordinal); putString("goal", p.goal.name)
            putFloat("pk", p.proteinPerKg?.toFloat() ?: -1f)
            putFloat("target", p.targetKg?.toFloat() ?: -1f)
            putBoolean("done", true)
        }
        _version.value++
    }

    /** Сколько раз срезали по 150 ккал из-за застоя веса. */
    var cuts: Int
        get() = prefs.getInt("cuts", 0)
        set(v) { prefs.edit { putInt("cuts", v.coerceAtLeast(0)) }; _version.value++ }

    /** С какого дня действуют текущие нормы — от него считаются 2 недели до проверки застоя. */
    var normsSince: LocalDate
        get() = LocalDate.ofEpochDay(prefs.getLong("since", LocalDate.now().toEpochDay()))
        set(d) { prefs.edit { putLong("since", d.toEpochDay()) }; _version.value++ }

    /** «Позже» на карточке застоя — не показывать до этого дня. */
    var plateauSnooze: LocalDate?
        get() = prefs.getLong("snooze", -1).takeIf { it >= 0 }?.let { LocalDate.ofEpochDay(it) }
        set(d) { prefs.edit { putLong("snooze", d?.toEpochDay() ?: -1) }; _version.value++ }

    /** Свои нормы по типам дня ("n", "t", "c"), поправленные чипами. */
    var custom: Map<String, Macros>
        get() = listOf("n", "t", "c").mapNotNull { id ->
            val s = prefs.getString("custom_$id", null) ?: return@mapNotNull null
            val (p, f, u) = s.split(';').map { it.toDouble() }
            id to Macros(p * 4 + f * 9 + u * 4, p, f, u)
        }.toMap()
        set(m) {
            prefs.edit { listOf("n", "t", "c").forEach { id -> m[id]?.let { putString("custom_$id", "${it.protein};${it.fat};${it.carbs}") } ?: remove("custom_$id") } }
            _version.value++
        }
}

/** Низкоинтенсивные занятия: не дают дня тренировки с добавкой углеводов. */
val LOW_INTENSITY = setOf("yoga")

/** Добавка углеводов дня тренировки к обычному дню (план автора: 140 → 230). */
const val TRAINING_EXTRA_CARBS = NormsCalc.TRAINING_CARBS
