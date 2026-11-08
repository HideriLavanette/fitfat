package app.fitfat.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Тема приложения: как в системе, светлая, тёмная. */
enum class ThemeMode(val label: String) { System("Как в системе"), Light("Светлая"), Dark("Тёмная") }

/**
 * Цвет приложения (макет Settings): [swatch] — образец в настройках (у сгенерированных — цвет кнопки тёмной темы),
 * [ink] — галочка на образце, [accent] — вторая половина образца (у «Графита» — хвойно-аквамариновый акцент). «Графит» — палитра автора, по умолчанию (08.11.2026).
 */
enum class AppPalette(val label: String, val swatch: Long, val ink: Long, val accent: Long? = null) {
    Graphite("Графит", 0xFF5F7685, 0xFFFFFFFF, 0xFF2A9A83),
    Pink("Розовый", 0xFFFFB0C9, 0xFF541D33),
    Lilac("Сиреневый", 0xFFD0BCFF, 0xFF381E72),
    Blue("Синий", 0xFFA8C7FA, 0xFF062E6F),
    Teal("Бирюзовый", 0xFF80D5D0, 0xFF003735),
    Green("Зелёный", 0xFFA6D394, 0xFF103800),
    Orange("Оранжевый", 0xFFFFB68B, 0xFF522300),
}

/** Дни недели 0 = Пн … 6 = Вс и время (минуты от полуночи). */
data class Schedule(val on: Boolean, val days: Set<Int>, val minute: Int)

/**
 * Настройки приложения (макет Settings, 5.5): тема, цвет приложения и цвета системы, щелчки и вибрация колёс,
 * напоминание о весе и недельный отчёт. Хранятся в `settings` вместе с началом дня ([DayClock]).
 */
object AppSettings {
    private var prefs: android.content.SharedPreferences? = null
    private val _theme = MutableStateFlow(ThemeMode.Dark)
    private val _dynamic = MutableStateFlow(false)
    private val _palette = MutableStateFlow(AppPalette.Graphite)
    private val _haptic = MutableStateFlow(true)
    private val _remind = MutableStateFlow(Schedule(true, (0..6).toSet(), 7 * 60 + 30))
    private val _report = MutableStateFlow(Schedule(true, setOf(6), 20 * 60))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    val dynamic: StateFlow<Boolean> = _dynamic.asStateFlow()
    val palette: StateFlow<AppPalette> = _palette.asStateFlow()
    val haptic: StateFlow<Boolean> = _haptic.asStateFlow()
    val remind: StateFlow<Schedule> = _remind.asStateFlow()
    val report: StateFlow<Schedule> = _report.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        _theme.value = ThemeMode.entries.firstOrNull { it.name == p.getString("theme", null) } ?: ThemeMode.Dark
        _dynamic.value = p.getBoolean("dynamic", false)
        _palette.value = AppPalette.entries.firstOrNull { it.name == p.getString("palette", null) } ?: AppPalette.Graphite
        _haptic.value = p.getBoolean("haptic", true)
        _remind.value = read(p, "remind", _remind.value)
        _report.value = read(p, "report", _report.value)
    }

    private fun read(p: android.content.SharedPreferences, key: String, def: Schedule) = Schedule(
        p.getBoolean("${key}_on", def.on),
        p.getString("${key}_days", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet() ?: def.days,
        p.getInt("${key}_min", def.minute),
    )

    private fun write(key: String, s: Schedule) = prefs?.edit {
        putBoolean("${key}_on", s.on); putString("${key}_days", s.days.sorted().joinToString(",")); putInt("${key}_min", s.minute)
    }

    fun setTheme(t: ThemeMode) { prefs?.edit { putString("theme", t.name) }; _theme.value = t }
    fun setDynamic(v: Boolean) { prefs?.edit { putBoolean("dynamic", v) }; _dynamic.value = v }
    fun setPalette(v: AppPalette) { prefs?.edit { putString("palette", v.name) }; _palette.value = v }
    fun setHaptic(v: Boolean) { prefs?.edit { putBoolean("haptic", v) }; _haptic.value = v }
    fun setRemind(s: Schedule) { write("remind", s); _remind.value = s }
    fun setReport(s: Schedule) { write("report", s); _report.value = s }

    private val dn = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val dpl = listOf("по понедельникам", "по вторникам", "по средам", "по четвергам", "по пятницам", "по субботам", "по воскресеньям")
    fun time(min: Int) = "%02d:%02d".format(min / 60, min % 60)

    /** «каждый день в 07:30», «по будням в 07:30», «Пн, Ср в 07:30». */
    fun remindText(s: Schedule): String {
        val d = s.days.sorted()
        if (d.isEmpty()) return "дни не выбраны"
        val whenText = when (d) {
            (0..6).toList() -> "каждый день"
            (0..4).toList() -> "по будням"
            listOf(5, 6) -> "по выходным"
            else -> d.joinToString(", ") { dn[it] }
        }
        return "$whenText в ${time(s.minute)}"
    }

    /** «по воскресеньям в 20:00». */
    fun reportText(s: Schedule) = "${dpl[s.days.firstOrNull() ?: 6]} в ${time(s.minute)}"
}
