package app.randomchorenotification

import android.content.Context
import androidx.core.content.edit
import java.util.Calendar

/** All persistent state and settings, kept in SharedPreferences. */
object Store {
    const val DEFAULT_CHORES = "Drink a glass of water;60\n" +
        "Stand up and stretch;120\n" +
        "Fix your posture;45\n" +
        "10 deep breaths;90\n" +
        "Tidy your desk;180\n" +
        "Look away from the screen;30"

    private fun p(c: Context) = c.getSharedPreferences("nag", Context.MODE_PRIVATE)

    // ---- settings ----
    fun choresText(c: Context): String = p(c).getString("chores", DEFAULT_CHORES) ?: DEFAULT_CHORES

    /** One chore per line, format "text;seconds". Missing/invalid seconds default to 60. */
    fun chores(c: Context): List<Pair<String, Int>> = parseChores(choresText(c))

    fun parseChores(text: String): List<Pair<String, Int>> =
        text.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty()) return@mapNotNull null
            val i = t.lastIndexOf(';')
            val name = if (i < 0) t else t.substring(0, i).trim()
            val secs = if (i < 0) 60 else t.substring(i + 1).trim().toIntOrNull() ?: 60
            if (name.isEmpty()) null else name to secs.coerceAtLeast(1)
        }

    const val MAX_PER_DAY = 24

    fun perDay(c: Context) = p(c).getInt("perDay", 2).coerceIn(1, MAX_PER_DAY)
    /** Active window, in minutes since midnight (end may be 1440 = midnight). */
    fun startMin(c: Context) = p(c).getInt("startMin", 9 * 60)
    fun endMin(c: Context) = p(c).getInt("endMin", 21 * 60)
    fun renagMin(c: Context) = p(c).getInt("renag", 10)

    /** Active weekdays: bit (Calendar.DAY_OF_WEEK - 1) set = active. Default all seven. */
    fun days(c: Context) = p(c).getInt("days", 0x7F)
    fun dayActive(c: Context, dayOfWeek: Int) = isDayActive(days(c), dayOfWeek)
    fun isDayActive(mask: Int, dayOfWeek: Int) = (mask shr (dayOfWeek - 1)) and 1 == 1

    /** Sound + vibration when the chore screen opens. */
    fun alert(c: Context) = p(c).getBoolean("alert", false)

    fun saveSettings(
        c: Context, chores: String, perDay: Int, startMin: Int, endMin: Int, days: Int, alert: Boolean
    ) {
        p(c).edit {
            putString("chores", chores)
            putInt("perDay", perDay)
            putInt("startMin", startMin)
            putInt("endMin", endMin)
            putInt("days", days)
            putBoolean("alert", alert)
            putLong("lastNag", maxOf(lastNag(c), System.currentTimeMillis()))
        }
    }

    // ---- schedule bookkeeping ----
    /** While now < pausedUntil, scheduled chores are skipped (an already open chore is unaffected). */
    fun pausedUntil(c: Context) = p(c).getLong("pausedUntil", 0L)
    fun setPausedUntil(c: Context, t: Long) = p(c).edit { putLong("pausedUntil", t) }
    fun paused(c: Context) = pausedUntil(c) > System.currentTimeMillis()

    /** Epoch millis of the next scheduled chore, 0 = nothing scheduled. */
    fun nextNagAt(c: Context) = p(c).getLong("nextNag", 0L)
    fun setNextNag(c: Context, t: Long) = p(c).edit { putLong("nextNag", t) }

    /** When the last chore was started; used to catch up on a slot missed while the phone was off. */
    fun lastNag(c: Context) = p(c).getLong("lastNag", 0L)
    fun setLastNag(c: Context, t: Long) = p(c).edit { putLong("lastNag", t) }

    // ---- history ----
    internal fun dayKey(cal: Calendar) =
        cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)

    /** Stored as "yyyyMMdd=count;..." for the last 60 days. */
    private fun doneMap(c: Context): MutableMap<Int, Int> = parseDone(p(c).getString("done", "") ?: "")

    internal fun parseDone(text: String): MutableMap<Int, Int> {
        val m = HashMap<Int, Int>()
        text.split(';').forEach {
            val kv = it.split('=')
            val k = kv.getOrNull(0)?.toIntOrNull()
            val v = kv.getOrNull(1)?.toIntOrNull()
            if (k != null && v != null) m[k] = v
        }
        return m
    }

    fun recordDone(c: Context) {
        val m = doneMap(c)
        val k = dayKey(Calendar.getInstance())
        m[k] = (m[k] ?: 0) + 1
        val kept = m.keys.sortedDescending().take(60)
        p(c).edit { putString("done", kept.joinToString(";") { "$it=${m[it]}" }) }
    }

    fun doneToday(c: Context) = doneMap(c)[dayKey(Calendar.getInstance())] ?: 0

    /** Consecutive days with at least one chore done, ending today (or yesterday if none yet today). */
    fun streak(c: Context): Int = streakOf(doneMap(c), Calendar.getInstance())

    internal fun streakOf(m: Map<Int, Int>, today: Calendar): Int {
        val cal = today.clone() as Calendar
        if ((m[dayKey(cal)] ?: 0) == 0) cal.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while ((m[dayKey(cal)] ?: 0) > 0) {
            n++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }

    // ---- current chore state ----
    fun pending(c: Context) = p(c).getBoolean("pending", false)
    fun chore(c: Context) = p(c).getString("chore", "") ?: ""
    fun secs(c: Context) = p(c).getInt("secs", 60)

    /** 0 = timer not started yet. Otherwise epoch millis when Done unlocks. */
    fun readyAt(c: Context) = p(c).getLong("readyAt", 0L)

    fun begin(c: Context, chore: String, secs: Int) {
        p(c).edit {
            putBoolean("pending", true)
            putString("chore", chore)
            putInt("secs", secs)
            putLong("readyAt", 0L)
        }
    }

    fun setReadyAt(c: Context, t: Long) = p(c).edit { putLong("readyAt", t) }

    fun clear(c: Context) {
        p(c).edit {
            putBoolean("pending", false)
            putLong("readyAt", 0L)
        }
    }
}
