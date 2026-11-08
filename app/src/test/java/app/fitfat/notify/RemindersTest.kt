package app.fitfat.notify

import app.fitfat.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class RemindersTest {
    // 2026-11-09 — понедельник
    private val mon0700 = LocalDateTime.of(2026, 11, 9, 7, 0)

    @Test fun sameDayLater() =
        assertEquals(LocalDateTime.of(2026, 11, 9, 7, 30), Reminders.next(Schedule(true, (0..6).toSet(), 7 * 60 + 30), mon0700))

    @Test fun passedTodayGoesTomorrow() =
        assertEquals(LocalDateTime.of(2026, 11, 10, 6, 0), Reminders.next(Schedule(true, (0..6).toSet(), 6 * 60), mon0700))

    @Test fun weeklyReportOnSunday() =
        assertEquals(LocalDateTime.of(2026, 11, 15, 20, 0), Reminders.next(Schedule(true, setOf(6), 20 * 60), mon0700))

    @Test fun sameWeekdayPassedGoesNextWeek() =
        assertEquals(LocalDateTime.of(2026, 11, 16, 6, 0), Reminders.next(Schedule(true, setOf(0), 6 * 60), mon0700))

    @Test fun offOrNoDays() {
        assertNull(Reminders.next(Schedule(false, setOf(0), 600), mon0700))
        assertNull(Reminders.next(Schedule(true, emptySet(), 600), mon0700))
    }
}
