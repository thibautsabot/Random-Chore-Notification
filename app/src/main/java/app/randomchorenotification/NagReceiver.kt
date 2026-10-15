package app.randomchorenotification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class NagReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val now = System.currentTimeMillis()
        when (i.action) {

            // Scheduled time: start a new chore unless one is still open.
            Scheduler.ACTION_NAG -> {
                if (!Store.pending(c) && !Store.paused(c)) Scheduler.startChore(c)
                Scheduler.scheduleNextNag(c)
            }

            // Start button: begin the countdown and schedule the unlock.
            Scheduler.ACTION_START -> Actions.start(c)

            // Countdown finished: swap in the Done button (alerts the user).
            Scheduler.ACTION_UNLOCK -> {
                if (Store.pending(c)) {
                    Notifier.post(c)
                    Scheduler.scheduleRenag(c)
                }
            }

            // Chore still open after the re-nag delay: post again.
            Scheduler.ACTION_RENAG -> {
                if (Store.pending(c)) {
                    Notifier.post(c)
                    Scheduler.scheduleRenag(c)
                }
            }

            // Chore screen was left while the chore is open: bring it back.
            Scheduler.ACTION_RELAUNCH -> {
                if (Store.pending(c)) {
                    c.startActivity(
                        Intent(c, ChoreActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }

            // Swiped away: put it right back.
            Scheduler.ACTION_DISMISSED -> {
                if (Store.pending(c)) Notifier.post(c)
            }

            // Done button.
            Scheduler.ACTION_DONE -> {
                if (Actions.done(c)) {
                    Toast.makeText(c, c.getString(R.string.nice_done), Toast.LENGTH_SHORT).show()
                } else if (Store.pending(c)) {
                    Toast.makeText(c, c.getString(R.string.too_early), Toast.LENGTH_SHORT).show()
                    Notifier.post(c)
                }
            }

            // Reboot or app update: alarms are lost, so rebuild them.
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Scheduler.scheduleNextNag(c)
                if (Store.pending(c)) {
                    Notifier.post(c)
                    Scheduler.scheduleRenag(c)
                    val r = Store.readyAt(c)
                    if (r > now) Scheduler.setAt(c, r, Scheduler.ACTION_UNLOCK)
                } else {
                    Scheduler.catchUp(c)
                }
            }
        }
    }
}
