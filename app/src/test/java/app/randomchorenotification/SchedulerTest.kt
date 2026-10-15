package app.randomchorenotification

import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerTest {

    private val allDays = 0x7F
    private val monday = GregorianCalendar(2026, Calendar.OCTOBER, 12)

    private fun minutesOfDay(t: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    @Test fun slotTimes_returnsOneTimePerChore() {
        assertEquals(4, Scheduler.slotTimes(monday, 4, 9 * 60, 21 * 60, allDays).size)
    }

    @Test fun slotTimes_staysInsideTheActiveWindow() {
        val times = Scheduler.slotTimes(monday, 6, 9 * 60, 21 * 60, allDays)
        times.forEach { assertTrue(minutesOfDay(it) in (9 * 60) until (21 * 60)) }
    }

    @Test fun slotTimes_placesOneTimeInEachSlotInOrder() {
        val times = Scheduler.slotTimes(monday, 3, 9 * 60, 21 * 60, allDays)
        times.forEachIndexed { k, t ->
            val m = minutesOfDay(t)
            assertTrue("slot $k at $m", m in (9 * 60 + k * 240) until (9 * 60 + (k + 1) * 240))
        }
        assertEquals(times.sorted(), times)
    }

    @Test fun slotTimes_isStableForAGivenDay() {
        val a = Scheduler.slotTimes(monday, 3, 9 * 60, 21 * 60, allDays)
        val b = Scheduler.slotTimes(monday.clone() as Calendar, 3, 9 * 60, 21 * 60, allDays)
        assertEquals(a, b)
    }

    @Test fun slotTimes_ignoresTheTimeOfDayOfTheInputCalendar() {
        val late = (monday.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 23) }
        assertEquals(
            Scheduler.slotTimes(monday, 2, 9 * 60, 21 * 60, allDays),
            Scheduler.slotTimes(late, 2, 9 * 60, 21 * 60, allDays)
        )
    }

    @Test fun slotTimes_isEmptyOnInactiveWeekday() {
        val tuesdayOnly = 1 shl (Calendar.TUESDAY - 1)
        assertTrue(Scheduler.slotTimes(monday, 3, 9 * 60, 21 * 60, tuesdayOnly).isEmpty())
    }

    @Test fun slotTimes_isNonEmptyOnActiveWeekday() {
        val mondayOnly = 1 shl (Calendar.MONDAY - 1)
        assertEquals(2, Scheduler.slotTimes(monday, 2, 9 * 60, 21 * 60, mondayOnly).size)
    }

    @Test fun slotTimes_neverSchedulesMoreChoresThanMinutesInWindow() {
        assertTrue(Scheduler.slotTimes(monday, 24, 9 * 60, 9 * 60 + 10, allDays).size <= 10)
    }

    @Test fun slotTimes_handlesWindowEndingAtMidnight() {
        val times = Scheduler.slotTimes(monday, 2, 22 * 60, 24 * 60, allDays)
        assertEquals(2, times.size)
        times.forEach { assertTrue(minutesOfDay(it) >= 22 * 60) }
    }

    @Test fun slotTimes_differsBetweenDays() {
        val tuesday = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val a = Scheduler.slotTimes(monday, 3, 9 * 60, 21 * 60, allDays).map { minutesOfDay(it) }
        val b = Scheduler.slotTimes(tuesday, 3, 9 * 60, 21 * 60, allDays).map { minutesOfDay(it) }
        assertTrue("random times should vary across days", a != b)
    }
}
