package app.randomchorenotification

import android.content.Context

/** Start / Done logic, shared by the notification buttons and the full-screen activity. */
object Actions {
    fun start(c: Context) {
        if (Store.pending(c) && Store.readyAt(c) == 0L) {
            val readyAt = System.currentTimeMillis() + Store.secs(c) * 1000L
            Store.setReadyAt(c, readyAt)
            Scheduler.setAt(c, readyAt, Scheduler.ACTION_UNLOCK)
            Notifier.post(c)
        }
    }

    /** Returns true if the chore was completed (countdown finished). */
    fun done(c: Context): Boolean {
        val r = Store.readyAt(c)
        if (Store.pending(c) && r != 0L && System.currentTimeMillis() >= r) {
            Store.clear(c)
            Store.recordDone(c)
            Scheduler.cancelPending(c)
            Notifier.cancel(c)
            return true
        }
        return false
    }

    /** Swaps the open chore for a different random one. Only before Start; false if no alternative. */
    fun skip(c: Context): Boolean {
        if (!Store.pending(c) || Store.readyAt(c) != 0L) return false
        val current = Store.chore(c)
        val (chore, secs) = Store.chores(c).filter { it.first != current }.randomOrNull() ?: return false
        Store.begin(c, chore, secs)
        return true
    }
}
