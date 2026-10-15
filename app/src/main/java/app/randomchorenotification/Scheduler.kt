package app.randomchorenotification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Scheduler {
    const val ACTION_NAG = "app.randomchorenotification.NAG"             // time for a new chore
    const val ACTION_RENAG = "app.randomchorenotification.RENAG"         // chore still open, re-post
    const val ACTION_UNLOCK = "app.randomchorenotification.UNLOCK"       // countdown finished
    const val ACTION_START = "app.randomchorenotification.START"         // Start button
    const val ACTION_DONE = "app.randomchorenotification.DONE"           // Done button
    const val ACTION_DISMISSED = "app.randomchorenotification.DISMISSED" // notification swiped away

    const val ACTION_RELAUNCH = "app.randomchorenotification.RELAUNCH"   // chore screen left, bring it back
    const val RELAUNCH_DELAY_MS = 5_000L
    const val SNOOZE_MS = 10 * 60_000L
    private const val CATCH_UP_WINDOW_MS = 2 * 60 * 60_000L
    private const val STALE_GRACE_MS = 2 * 60_000L

    fun pi(c: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            c, 0,
            Intent(c, NagReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun am(c: Context) = c.getSystemService(AlarmManager::class.java)

    fun setAt(c: Context, atMillis: Long, action: String) {
        val alarm = am(c)
        val p = pi(c, action)
        val canExact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        try {
            if (canExact) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p)
            }
        } catch (e: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p)
        }
    }

    fun cancelAll(c: Context) {
        am(c).cancel(pi(c, ACTION_NAG))
        Store.setNextNag(c, 0L)
        cancelRelaunch(c)
        cancelPending(c)
    }

    /** Cancels the alarms tied to an open chore. */
    fun cancelPending(c: Context) {
        am(c).cancel(pi(c, ACTION_RENAG))
        am(c).cancel(pi(c, ACTION_UNLOCK))
    }

    fun cancelRelaunch(c: Context) {
        am(c).cancel(pi(c, ACTION_RELAUNCH))
    }

    fun scheduleRenag(c: Context) {
        setAt(c, System.currentTimeMillis() + Store.renagMin(c) * 60_000L, ACTION_RENAG)
    }

    /**
     * The chore times for one day: the active window is split into perDay equal slots, with one
     * random (but stable for a given day) time inside each. Empty if the weekday is switched off.
     */
    private fun slotTimes(c: Context, day: Calendar): List<Long> =
        slotTimes(day, Store.perDay(c), Store.startMin(c), Store.endMin(c), Store.days(c))

    internal fun slotTimes(day: Calendar, perDay: Int, s: Int, e: Int, daysMask: Int): List<Long> {
        if (!Store.isDayActive(daysMask, day.get(Calendar.DAY_OF_WEEK))) return emptyList()
        val n = perDay.coerceAtMost((e - s).coerceAtLeast(1))
        val slotMin = ((e - s) / n).coerceAtLeast(1)
        return List(n) { k ->
            val cal = (day.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val seed = cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR) + k * 100_000L
            cal.add(Calendar.MINUTE, s + k * slotMin + java.util.Random(seed).nextInt(slotMin))
            cal.timeInMillis
        }
    }

    /** Schedules the first slot still ahead, looking up to a week out for an active day. */
    fun scheduleNextNag(c: Context) {
        val now = System.currentTimeMillis()
        for (offset in 0..7) {
            val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
            val next = slotTimes(c, day).firstOrNull { it > now + 1000 }
            if (next != null) {
                Store.setNextNag(c, next)
                setAt(c, next, ACTION_NAG)
                return
            }
        }
        Store.setNextNag(c, 0L)
    }

    /**
     * True if the next-chore alarm should already have fired but didn't (killed app, lost alarm,
     * revoked exact-alarm permission). Rebuilds the schedule and catches up a recent slot.
     */
    fun repairIfStale(c: Context): Boolean {
        val next = Store.nextNagAt(c)
        if (next == 0L || System.currentTimeMillis() - next < STALE_GRACE_MS) return false
        scheduleNextNag(c)
        catchUp(c)
        return true
    }

    /** After a reboot or update: if a slot passed recently without firing, run it now. */
    fun catchUp(c: Context) {
        if (Store.pending(c) || Store.paused(c)) return
        val now = System.currentTimeMillis()
        val last = Store.lastNag(c)
        val missed = slotTimes(c, Calendar.getInstance())
            .any { it <= now && it > last && now - it < CATCH_UP_WINDOW_MS }
        if (missed) startChore(c)
    }

    fun startChore(c: Context) {
        val list = Store.chores(c)
        if (list.isEmpty()) return
        val (chore, secs) = list.random()
        Store.begin(c, chore, secs)
        Store.setLastNag(c, System.currentTimeMillis())
        Notifier.post(c)
        scheduleRenag(c)
    }
}
