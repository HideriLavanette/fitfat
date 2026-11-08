package app.fitfat.data

import app.fitfat.platform.Prefs
import app.fitfat.time.*
import app.fitfat.time.Fmt
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import app.fitfat.time.ChronoUnit

/** Записанная тренировка (макет Training, «История»). */
@Entity(tableName = "training")
data class TrainingLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    /** id вида: strength, cardio, run, swim, fight, team, bike, yoga. */
    val type: String,
    val minutes: Int,
)

@Dao
interface TrainingDao {
    @Query("SELECT * FROM training ORDER BY epochDay DESC, id DESC")
    fun observeAll(): Flow<List<TrainingLog>>

    @Insert
    suspend fun insert(t: TrainingLog): Long

    @Query("DELETE FROM training WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Виды тренировок: id, подпись, интенсивные ли (дают день тренировки с добавкой углеводов). */
val TRAINING_TYPES = listOf(
    Triple("strength", "Силовые", true), Triple("cardio", "Кардио", true), Triple("run", "Бег", true),
    Triple("swim", "Плавание", true), Triple("fight", "Единоборства", true), Triple("team", "Командные", true),
    Triple("bike", "Велосипед", true), Triple("yoga", "Йога", false),
)

fun trainingLabel(id: String) = TRAINING_TYPES.firstOrNull { it.first == id }?.second ?: "Тренировка"

/** Как повторяются тренировки (макет Training): дни недели / через N дней / вахта. */
enum class PlanMode(val label: String) { Week("Дни недели"), Interval("Через N дней"), Shift("Вахта") }

/** Частота внутри активного периода вахты. */
enum class ShiftInner(val label: String) { Daily("Каждый день"), Alternate("Через день"), Week("По дням недели") }

data class TrainingPlan(
    val mode: PlanMode = PlanMode.Week,
    /** Дни недели 0 = Пн … 6 = Вс. */
    val days: Set<Int> = setOf(0, 2, 4),
    val trainDays: Int = 1,
    val restDays: Int = 2,
    val onDays: Int = 14,
    val offDays: Int = 14,
    val inner: ShiftInner = ShiftInner.Week,
    val start: LocalDate = LocalDate.now(),
) {
    /** Что в этот день: тренировка, отдых или перерыв вахты. */
    enum class Kind { Train, Rest, Off }

    fun kind(d: LocalDate): Kind {
        val dow = d.dayOfWeek.value - 1
        if (mode == PlanMode.Week) return if (dow in days) Kind.Train else Kind.Rest
        val n = ChronoUnit.DAYS.between(start, d)
        if (n < 0) return Kind.Rest
        if (mode == PlanMode.Interval) return if (n % (trainDays + restDays) < trainDays) Kind.Train else Kind.Rest
        val cyc = n % (onDays + offDays)
        if (cyc >= onDays) return Kind.Off
        return when (inner) {
            ShiftInner.Daily -> Kind.Train
            ShiftInner.Alternate -> if (cyc % 2 == 0L) Kind.Train else Kind.Rest
            ShiftInner.Week -> if (dow in days) Kind.Train else Kind.Rest
        }
    }
}

/**
 * Расписание тренировок. Пока расписание есть, в его дни тип дня по умолчанию — «Тренировка»
 * (только с [since] — дня, когда расписание сохранили: прошлые дни и их нормы не меняются).
 */
object TrainingSchedule {
    private var prefs: Prefs? = null
    private val _plan = MutableStateFlow<TrainingPlan?>(null)
    val plan: StateFlow<TrainingPlan?> = _plan.asStateFlow()
    var since: LocalDate = LocalDate.MAX
        private set

    fun init() {
        val p = Prefs("training")
        prefs = p
        if (!p.contains("mode")) return
        _plan.value = TrainingPlan(
            mode = PlanMode.entries.firstOrNull { it.name == p.getString("mode", null) } ?: PlanMode.Week,
            days = p.getString("days", "")!!.split(',').mapNotNull { it.toIntOrNull() }.toSet(),
            trainDays = p.getInt("tr", 1), restDays = p.getInt("rs", 2), onDays = p.getInt("on", 14), offDays = p.getInt("off", 14),
            inner = ShiftInner.entries.firstOrNull { it.name == p.getString("inner", null) } ?: ShiftInner.Week,
            start = LocalDate.ofEpochDay(p.getLong("start", LocalDate.now().toEpochDay())),
        )
        since = LocalDate.ofEpochDay(p.getLong("since", LocalDate.now().toEpochDay()))
    }

    fun save(t: TrainingPlan, today: LocalDate) {
        prefs?.edit {
            putString("mode", t.mode.name); putString("days", t.days.sorted().joinToString(","))
            putInt("tr", t.trainDays); putInt("rs", t.restDays); putInt("on", t.onDays); putInt("off", t.offDays)
            putString("inner", t.inner.name); putLong("start", t.start.toEpochDay()); putLong("since", today.toEpochDay())
        }
        since = today
        _plan.value = t
    }

    /** Тип дня по расписанию для даты без своей отметки. */
    fun suggests(d: LocalDate): Boolean = _plan.value?.let { !d.isBefore(since) && it.kind(d) == TrainingPlan.Kind.Train } ?: false
}
