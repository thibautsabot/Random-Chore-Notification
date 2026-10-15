package app.randomchorenotification

import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreTest {

    private fun day(y: Int, m: Int, d: Int) = GregorianCalendar(y, m, d)

    private fun key(cal: Calendar) = Store.dayKey(cal)

    // ---- parseChores ----

    @Test fun parseChores_readsNameAndSeconds() {
        assertEquals(listOf("Stretch" to 120), Store.parseChores("Stretch;120"))
    }

    @Test fun parseChores_defaultsMissingOrInvalidSeconds() {
        assertEquals(
            listOf("Water" to 60, "Breathe" to 60),
            Store.parseChores("Water\nBreathe;abc")
        )
    }

    @Test fun parseChores_skipsBlankLinesAndEmptyNames() {
        assertEquals(listOf("A" to 10), Store.parseChores("\n  \n;60\nA;10\n"))
    }

    @Test fun parseChores_usesLastSemicolonAndTrims() {
        assertEquals(listOf("a;b" to 30), Store.parseChores("  a;b ; 30  "))
    }

    @Test fun parseChores_clampsSecondsToAtLeastOne() {
        assertEquals(listOf("X" to 1, "Y" to 1), Store.parseChores("X;0\nY;-5"))
    }

    @Test fun parseChores_defaultListIsValid() {
        assertEquals(6, Store.parseChores(Store.DEFAULT_CHORES).size)
    }

    // ---- weekdays ----

    @Test fun isDayActive_usesCalendarDayOfWeekBits() {
        val mondayOnly = 1 shl (Calendar.MONDAY - 1)
        assertTrue(Store.isDayActive(mondayOnly, Calendar.MONDAY))
        assertFalse(Store.isDayActive(mondayOnly, Calendar.TUESDAY))
        assertTrue(Store.isDayActive(0x7F, Calendar.SUNDAY))
    }

    // ---- history ----

    @Test fun dayKey_formatsAsYyyyMmDd() {
        assertEquals(20261015, key(day(2026, Calendar.OCTOBER, 15)))
    }

    @Test fun parseDone_ignoresGarbage() {
        val m = Store.parseDone("20261015=3;oops;20261014=x;20261013=1")
        assertEquals(mapOf(20261015 to 3, 20261013 to 1), m)
    }

    @Test fun parseDone_emptyString() {
        assertTrue(Store.parseDone("").isEmpty())
    }

    @Test fun streak_countsConsecutiveDaysEndingToday() {
        val today = day(2026, Calendar.OCTOBER, 15)
        val m = mapOf(20261015 to 1, 20261014 to 2, 20261013 to 1, 20261011 to 4)
        assertEquals(3, Store.streakOf(m, today))
    }

    @Test fun streak_survivesUntilTodayIfYesterdayWasDone() {
        val today = day(2026, Calendar.OCTOBER, 15)
        assertEquals(2, Store.streakOf(mapOf(20261014 to 1, 20261013 to 1), today))
    }

    @Test fun streak_isZeroAfterAMissedDay() {
        val today = day(2026, Calendar.OCTOBER, 15)
        assertEquals(0, Store.streakOf(mapOf(20261013 to 5), today))
    }

    @Test fun streak_crossesMonthBoundary() {
        val today = day(2026, Calendar.NOVEMBER, 1)
        assertEquals(2, Store.streakOf(mapOf(20261101 to 1, 20261031 to 1), today))
    }

    @Test fun streak_doesNotMutateTheCalendarPassedIn() {
        val today = day(2026, Calendar.OCTOBER, 15)
        Store.streakOf(mapOf(20261014 to 1), today)
        assertEquals(20261015, key(today))
    }
}
