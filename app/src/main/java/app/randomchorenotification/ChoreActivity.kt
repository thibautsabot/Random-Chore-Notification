package app.randomchorenotification

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Full-screen chore screen. Shows over the lock screen, wakes the display,
 * ignores the Back button, and comes back shortly after you leave it (see NagReceiver).
 * The screen may time out normally; the chore stays on top until it is done or snoozed.
 */
class ChoreActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()
    private lateinit var labelView: TextView
    private lateinit var hintView: TextView
    private lateinit var choreView: TextView
    private lateinit var statusView: TextView
    private lateinit var startBtn: Button
    private lateinit var doneBtn: Button
    private lateinit var skipView: TextView
    private lateinit var chorePane: FrameLayout
    private var celebrating = false

    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 250)
        }
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        // Back does nothing: the way out is finishing the chore.
        onBackPressedDispatcher.addCallback(this) { }
        enableEdgeToEdge()

        val pad = px(32)
        val root = FrameLayout(this).apply {
            chorePane = this
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Palette.BG, Palette.INDIGO_DARK)
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }

        fun pill(text: String, color: Int, onClick: () -> Unit) = Button(this).apply {
            this.text = text
            textSize = 20f
            isAllCaps = false
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            stateListAnimator = null
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = px(40).toFloat()
            }
            setPadding(0, px(18), 0, px(18))
            setOnClickListener { onClick() }
        }

        labelView = TextView(this).apply {
            text = getString(R.string.do_it_now)
            textSize = 14f
            letterSpacing = 0.3f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Palette.ACCENT_LIGHT)
            gravity = Gravity.CENTER
        }
        choreView = TextView(this).apply {
            textSize = 38f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, px(16), 0, px(24))
        }
        statusView = TextView(this).apply {
            textSize = 72f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        hintView = TextView(this).apply {
            textSize = 16f
            setTextColor(Palette.SLATE_LIGHT)
            gravity = Gravity.CENTER
            setPadding(0, px(8), 0, px(40))
        }
        startBtn = pill(getString(R.string.start), Palette.ACCENT) {
            Actions.start(this@ChoreActivity)
            render()
        }
        doneBtn = pill(getString(R.string.done), Palette.GREEN) {
            if (Actions.done(this@ChoreActivity)) {
                celebrate()
            } else {
                render()
            }
        }

        val full = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        content.addView(labelView, full)
        content.addView(choreView, full)
        content.addView(statusView, full)
        content.addView(hintView, full)
        content.addView(startBtn, full)
        content.addView(doneBtn, full)
        root.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        fun link(textRes: Int, alpha: Int, onClick: () -> Unit) = TextView(this).apply {
            text = getString(textRes)
            textSize = 14f
            setTextColor((alpha shl 24) or 0xCBD5E1)
            gravity = Gravity.CENTER
            setPadding(pad, px(10), pad, px(10))
            setOnClickListener { onClick() }
        }
        skipView = link(R.string.skip, 0xCC) {
            if (Actions.skip(this)) render() else Toast.makeText(this, getString(R.string.no_other_chore), Toast.LENGTH_SHORT).show()
        }
        val links = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, px(14))
            addView(skipView)
            addView(link(R.string.snooze, 0x99) { snooze() })
        }
        root.addView(links, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ))

        // Keep the chore and the links clear of the status bar, navigation bar and cutout.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            content.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            links.setPadding(bars.left, 0, bars.right, px(14) + bars.bottom)
            insets
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        Scheduler.cancelRelaunch(this)
        handler.post(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        // Left without finishing (Home, recents): schedule the comeback. If the screen simply
        // went to sleep, stay put: the chore is still on top for when the phone is picked up.
        val screenOn = getSystemService(PowerManager::class.java).isInteractive
        if (!isFinishing && screenOn && Store.pending(this)) {
            Scheduler.setAt(
                this,
                System.currentTimeMillis() + Scheduler.RELAUNCH_DELAY_MS,
                Scheduler.ACTION_RELAUNCH
            )
        }
    }

    /** Closes the screen; the re-nag alarm brings it back after SNOOZE_MS. */
    private fun snooze() {
        Scheduler.cancelRelaunch(this)
        Notifier.cancel(this)
        Scheduler.setAt(this, System.currentTimeMillis() + Scheduler.SNOOZE_MS, Scheduler.ACTION_RENAG)
        Toast.makeText(this, getString(R.string.snoozed), Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Green confetti burst for ~2 s, then close. */
    private fun celebrate() {
        celebrating = true
        handler.removeCallbacks(tick)
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels

        val streak = Store.streak(this)
        val today = Store.doneToday(this).let { resources.getQuantityString(R.plurals.stat_today, it, it) }
        val praise = resources.getStringArray(R.array.praise).random()

        val overlay = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Palette.GREEN_DARK, Palette.GREEN)
            )
            alpha = 0f
            isClickable = true
        }
        val check = TextView(this).apply {
            text = "✔"
            textSize = 80f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x33FFFFFF)
            }
            scaleX = 0f
            scaleY = 0f
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(check, LinearLayout.LayoutParams(px(170), px(170)))
            addView(TextView(this@ChoreActivity).apply {
                text = praise
                textSize = 36f
                typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, px(28), 0, px(6))
            })
            addView(TextView(this@ChoreActivity).apply {
                text = if (streak > 0) "$today  ·  ${getString(R.string.stat_streak, streak)}" else today
                textSize = 16f
                setTextColor(0xCCFFFFFF.toInt())
                gravity = Gravity.CENTER
            })
        }
        overlay.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFDE68A.toInt(), Palette.ACCENT_LIGHT, 0xFFFBCFE8.toInt(), 0xFFBBF7D0.toInt())
        val rnd = java.util.Random()
        repeat(40) {
            val size = px(6 + rnd.nextInt(8))
            overlay.addView(View(this).apply {
                setBackgroundColor(colors[rnd.nextInt(colors.size)])
                x = rnd.nextInt(w).toFloat()
                y = -size * 3f
                rotation = rnd.nextInt(360).toFloat()
                animate()
                    .y(h + size * 2f)
                    .rotation(rotation + 360f * (if (rnd.nextBoolean()) 1 else -1))
                    .setStartDelay(rnd.nextInt(500).toLong())
                    .setDuration(1100L + rnd.nextInt(1100))
                    .start()
            }, FrameLayout.LayoutParams(size, size))
        }

        chorePane.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        overlay.animate().alpha(1f).setDuration(220).start()
        check.animate().scaleX(1f).scaleY(1f).setStartDelay(120).setDuration(550)
            .setInterpolator(OvershootInterpolator(2.2f)).start()
        handler.postDelayed({ finish() }, 2100)
    }

    private fun render() {
        if (celebrating) return
        if (!Store.pending(this)) {
            finish()
            return
        }
        val readyAt = Store.readyAt(this)
        val now = System.currentTimeMillis()
        choreView.text = Store.chore(this)

        when {
            readyAt == 0L -> {
                statusView.text = getString(R.string.seconds_fmt, Store.secs(this))
                hintView.text = getString(R.string.hint_start)
                startBtn.visibility = View.VISIBLE
                doneBtn.visibility = View.GONE
                skipView.visibility = View.VISIBLE
            }
            now < readyAt -> {
                val left = ((readyAt - now + 999) / 1000).toInt()
                statusView.text = getString(R.string.timer_fmt, left / 60, left % 60)
                hintView.text = getString(R.string.hint_keep_going)
                startBtn.visibility = View.GONE
                doneBtn.visibility = View.GONE
                skipView.visibility = View.GONE
            }
            else -> {
                statusView.text = getString(R.string.timer_fmt, 0, 0)
                hintView.text = getString(R.string.hint_times_up)
                startBtn.visibility = View.GONE
                doneBtn.visibility = View.VISIBLE
                skipView.visibility = View.GONE
            }
        }
    }
}
