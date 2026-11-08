package app.fitfat.data

import app.fitfat.platform.Prefs
import app.fitfat.platform.uptimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Подсказки жестами (DESIGN.md, «Подсказки и жесты»): палец показывает, как крутить барабан, строка со свайпом
 * чуть тянется в сторону. Показываются до первого использования; барабаны — ещё и снова, если человек долго ничего
 * не трогает на экране ([IDLE_MS]). Свайпы после первого раза не напоминаются. Освоенные — в [Prefs] `hints`;
 * «Показать подсказки заново» в Настройках сбрасывает.
 */
class Hints private constructor() {
    private val prefs = Prefs("hints")
    private val _learned = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    /** Жесты, которыми уже пользовались. */
    val learned: StateFlow<Set<String>> = _learned.asStateFlow()

    private val _lastTouch = MutableStateFlow(0L)
    /** Последнее касание экрана (uptime, мс) — для повторной подсказки, если человек застрял. */
    val lastTouch: StateFlow<Long> = _lastTouch.asStateFlow()

    fun used(key: String) {
        if (key in _learned.value) return
        val s = _learned.value + key
        _learned.value = s
        prefs.edit { putStringSet(KEY, s) }
    }

    fun touch() { _lastTouch.value = uptimeMillis() }

    fun reset() {
        _learned.value = emptySet()
        prefs.edit { remove(KEY) }
    }

    companion object {
        private const val KEY = "learned"
        /** Сколько без касаний, чтобы снова показать палец на освоенном барабане. */
        const val IDLE_MS = 10_000L

        /** Чип с барабаном: открывается нажатием или удержанием. */
        const val DRUM_CHIP = "drum_chip"
        /** Видимый барабан-столбик. */
        const val DRUM_COLUMN = "drum_column"
        const val SWIPE_DIARY = "swipe_diary"
        const val SWIPE_PRODUCTS = "swipe_products"
        const val SWIPE_ADD = "swipe_add"
        const val SWIPE_DISH = "swipe_dish"

        private val instance: Hints by lazy { Hints() }
        fun get(): Hints = instance
    }
}
