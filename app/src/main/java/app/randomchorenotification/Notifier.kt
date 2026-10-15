package app.randomchorenotification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * Fallback nag, used only without the overlay permission (otherwise the screen opens directly).
 * One notification, three phases:
 *  WAITING   - "Start" button
 *  COUNTDOWN - live countdown, no buttons
 *  READY     - "Done" button
 * Swiping it away fires deleteIntent, which re-posts it (see NagReceiver).
 */
object Notifier {
    private const val ID = 1
    private const val CH_ALERT = "nag_alert"
    private const val CH_QUIET = "nag_quiet"

    private fun nm(c: Context) = c.getSystemService(NotificationManager::class.java)

    private fun ensureChannels(c: Context) {
        nm(c).createNotificationChannel(
            NotificationChannel(CH_ALERT, c.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH)
        )
        nm(c).createNotificationChannel(
            NotificationChannel(CH_QUIET, c.getString(R.string.channel_quiet), NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun post(c: Context) {
        if (!Store.pending(c)) return

        val chore = Store.chore(c)
        val readyAt = Store.readyAt(c)
        val now = System.currentTimeMillis()

        // Display-over-other-apps granted: the screen is the nag, no notification at all.
        if (Settings.canDrawOverlays(c)) {
            nm(c).cancel(ID)
            if (readyAt == 0L || now >= readyAt) {
                if (Store.alert(c)) alertUser(c)
                c.startActivity(
                    Intent(c, ChoreActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
            return
        }
        ensureChannels(c)

        val b = when {
            readyAt == 0L ->
                NotificationCompat.Builder(c, CH_ALERT)
                    .setContentText(c.getString(R.string.notif_start_text, chore, Store.secs(c)))
                    .addAction(0, c.getString(R.string.start), Scheduler.pi(c, Scheduler.ACTION_START))

            now < readyAt ->
                NotificationCompat.Builder(c, CH_QUIET)
                    .setContentText(c.getString(R.string.notif_countdown_text, chore))
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setWhen(readyAt)
                    .setOnlyAlertOnce(true)

            else ->
                NotificationCompat.Builder(c, CH_ALERT)
                    .setContentText(c.getString(R.string.notif_ready_text, chore))
                    .addAction(0, c.getString(R.string.done), Scheduler.pi(c, Scheduler.ACTION_DONE))
        }

        val screenIntent = Intent(c, ChoreActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val screen = PendingIntent.getActivity(
            c, 0, screenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Take over the screen whenever the user has to act (not during the quiet countdown).
        if (readyAt == 0L || now >= readyAt) b.setFullScreenIntent(screen, true)

        val n = b
            .setContentIntent(screen)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(c.getString(R.string.notif_title))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setDeleteIntent(Scheduler.pi(c, Scheduler.ACTION_DISMISSED))
            .build()

        nm(c).notify(ID, n)
    }

    /** Default notification sound plus a double buzz, for the "Sound & vibration" option. */
    private fun alertUser(c: Context) {
        c.getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            ?.play()
    }

    fun cancel(c: Context) = nm(c).cancel(ID)
}
