package app.randomchorenotification

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isEmpty
import androidx.core.view.updateLayoutParams
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

    private companion object {
        const val BG = Palette.BG
        const val CARD = 0xFF1E293B.toInt()
        const val CHIP_OFF = 0xFF334155.toInt()
        const val ACCENT = Palette.ACCENT
        const val TEXT = Color.WHITE
        const val MUTED = 0xFF94A3B8.toInt()
        const val DANGER = 0xFFF87171.toInt()
        const val UNDO_MS = 5000L
        const val ACTION_TEST = "app.randomchorenotification.SHORTCUT_TEST"
        const val ACTION_PAUSE = "app.randomchorenotification.SHORTCUT_PAUSE"
        const val ACTION_ADD = "app.randomchorenotification.SHORTCUT_ADD"
        const val PAUSE_MS = 60 * 60_000L

        /** Monday..Sunday as Calendar.DAY_OF_WEEK values. */
        val WEEK = intArrayOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )
    }

    private val dp by lazy { resources.displayMetrics.density }
    private fun px(v: Int) = (v * dp).toInt()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var scroll: ScrollView
    private lateinit var resumeBtn: Button
    private lateinit var choreList: LinearLayout
    private lateinit var nextView: TextView
    private lateinit var statsView: TextView
    private lateinit var setupSection: LinearLayout
    private lateinit var setupCard: LinearLayout
    private lateinit var undoBar: LinearLayout
    private var undoAction: (() -> Unit)? = null
    private val hideUndo = Runnable { dismissUndo() }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) exportChores(uri)
        }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importChores(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(24), px(20), px(96))
        }

        root.addView(text(getString(R.string.title_main), 28f, TEXT, bold = true))
        root.addView(text(getString(R.string.subtitle), 15f, MUTED).apply { setPadding(0, px(2), 0, px(8)) })

        // ---- status ----
        nextView = text("", 16f, TEXT)
        statsView = text("", 14f, MUTED).apply { setPadding(0, px(4), 0, 0) }
        root.addView(card().apply {
            setPadding(px(16), px(14), px(16), px(14))
            addView(nextView)
            addView(statsView)
            resumeBtn = button(getString(R.string.resume), Color.TRANSPARENT, ACCENT, outline = true) {
                Store.setPausedUntil(this@MainActivity, 0L)
                refresh()
            }.apply {
                visibility = View.GONE
                layoutParams = rowParams(top = 12)
            }
            addView(resumeBtn)
        })

        // ---- setup (only while something is missing) ----
        setupSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setupSection.addView(section(getString(R.string.section_setup)))
        setupCard = card()
        setupSection.addView(setupCard)
        root.addView(setupSection)

        // ---- chores ----
        root.addView(section(getString(R.string.section_chores)))
        choreList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(choreList)
        Store.chores(this).forEach { (t, s) -> addChoreRow(t, s) }
        root.addView(
            button(getString(R.string.add_chore), Color.TRANSPARENT, ACCENT, outline = true) {
                addChoreRow("", 60, focus = true)
            }.apply { layoutParams = rowParams(top = 12) }
        )
        root.addView(LinearLayout(this).apply {
            layoutParams = rowParams(top = 8)
            addView(button(getString(R.string.export_chores), Color.TRANSPARENT, MUTED, outline = true) {
                exportLauncher.launch("chores.txt")
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginEnd = px(4) }
            })
            addView(button(getString(R.string.import_chores), Color.TRANSPARENT, MUTED, outline = true) {
                importLauncher.launch(arrayOf("text/*"))
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = px(4) }
            })
        })

        // ---- schedule ----
        root.addView(section(getString(R.string.section_schedule)))
        val perDay = numField(Store.perDay(this))
        var startMin = Store.startMin(this)
        var endMin = Store.endMin(this)
        val start = timeField(startMin) { startMin = it }
        val end = timeField(endMin) { endMin = it }
        var daysMask = Store.days(this)
        root.addView(card().apply {
            addView(settingRow(getString(R.string.chores_per_day), perDay, "×"))
            addView(settingRow(getString(R.string.active_from), start, ""))
            addView(settingRow(getString(R.string.active_until), end, ""))
            addView(text(getString(R.string.active_days), 16f, TEXT).apply { setPadding(0, px(12), 0, px(8)) })
            addView(dayChips(daysMask) { daysMask = it })
        })

        // ---- alerts ----
        val alert = Switch(this).apply {
            isChecked = Store.alert(this@MainActivity)
            minHeight = px(48)
        }
        root.addView(card().apply { addView(settingRow(getString(R.string.sound_vibration), alert, "")) })

        // ---- actions ----
        root.addView(button(getString(R.string.save), ACCENT, TEXT) {
            val s = startMin
            val e = if (endMin <= s) (s + 60).coerceAtMost(24 * 60) else endMin
            val n = (perDay.text.toString().toIntOrNull() ?: 2).coerceIn(1, Store.MAX_PER_DAY)
            perDay.setText(getString(R.string.number_fmt, n))
            dismissUndo()
            Store.saveSettings(this, serializeChores(), n, s, e, daysMask, alert.isChecked)
            Scheduler.scheduleNextNag(this)
            refresh()
            Toast.makeText(this, resources.getQuantityString(R.plurals.saved_toast, n, n, fmt(s), fmt(e)), Toast.LENGTH_SHORT).show()
        }.apply { layoutParams = rowParams(top = 28) })

        root.addView(button(getString(R.string.test_now), CARD, TEXT) { testNow() }.apply { layoutParams = rowParams(top = 10) })

        root.addView(button(getString(R.string.stop), Color.TRANSPARENT, DANGER) { confirmStop() }.apply { layoutParams = rowParams(top = 6) })

        // ---- undo bar, floating over the bottom ----
        undoBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(px(16), px(12), px(16), px(12))
            background = GradientDrawable().apply {
                setColor(CHIP_OFF)
                cornerRadius = px(16).toFloat()
            }
            addView(text(getString(R.string.chore_deleted), 15f, TEXT).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(text(getString(R.string.undo), 15f, Palette.ACCENT_LIGHT, bold = true).apply {
                setPaddingRelative(px(16), px(4), 0, px(4))
                setOnClickListener {
                    undoAction?.invoke()
                    dismissUndo()
                }
            })
        }

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(BG)
            scroll = ScrollView(this@MainActivity).apply {
                isFillViewport = true
                addView(root)
            }
            addView(scroll)
            addView(undoBar, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            ).apply { setMargins(px(16), 0, px(16), px(24)) })

            // Keep content clear of the status bar, navigation bar, cutout and keyboard.
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
                )
                scroll.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                undoBar.updateLayoutParams<FrameLayout.LayoutParams> { bottomMargin = px(24) + bars.bottom }
                insets
            }
        })

        handleShortcut(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcut(intent)
    }

    override fun onResume() {
        super.onResume()
        if (Scheduler.repairIfStale(this)) {
            Toast.makeText(this, getString(R.string.schedule_restored), Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun exportChores(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(serializeChores().toByteArray()) }
            Toast.makeText(this, getString(R.string.exported), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun importChores(uri: Uri) {
        val chores = try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            Store.parseChores(text)
        } catch (e: Exception) {
            emptyList()
        }
        if (chores.isEmpty()) {
            Toast.makeText(this, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
            return
        }
        dismissUndo()
        choreList.removeAllViews()
        chores.forEach { (t, secs) -> addChoreRow(t, secs) }
        Toast.makeText(this, resources.getQuantityString(R.plurals.imported, chores.size, chores.size), Toast.LENGTH_LONG).show()
    }

    private fun confirmStop() {
        AlertDialog.Builder(this)
            .setTitle(R.string.stop_confirm_title)
            .setMessage(R.string.stop_confirm_message)
            .setPositiveButton(R.string.stop_confirm_yes) { _, _ ->
                Scheduler.cancelAll(this)
                Store.clear(this)
                Notifier.cancel(this)
                refresh()
                Toast.makeText(this, getString(R.string.stopped), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- home screen shortcuts ----

    private fun testNow() {
        Store.begin(this, getString(R.string.test_chore), 10)
        Notifier.post(this)
        Scheduler.scheduleRenag(this)
    }

    private fun handleShortcut(i: Intent?) {
        when (i?.action) {
            ACTION_TEST -> testNow()
            ACTION_PAUSE -> {
                val until = System.currentTimeMillis() + PAUSE_MS
                Store.setPausedUntil(this, until)
                refresh()
                Toast.makeText(this, getString(R.string.paused_toast, clock(until)), Toast.LENGTH_LONG).show()
            }
            ACTION_ADD -> {
                addChoreRow("", 60, focus = true)
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
            else -> return
        }
        i?.action = null // handle once, not again on rotation or resume
    }

    // ---- status + setup ----

    private fun doneTodayText(): String {
        val n = Store.doneToday(this)
        return resources.getQuantityString(R.plurals.stat_today, n, n)
    }

    // Asking to be exempt from battery optimization is deliberate: exact chore alarms must fire on time.
    @SuppressLint("BatteryLife")
    private fun refresh() {
        val paused = Store.paused(this)
        nextView.text = if (paused) getString(R.string.paused_until, clock(Store.pausedUntil(this)))
        else getString(R.string.next_chore, nextLabel())
        resumeBtn.visibility = if (paused) View.VISIBLE else View.GONE

        val today = doneTodayText()
        val streak = Store.streak(this)
        statsView.text = if (streak > 0) "$today  ·  ${getString(R.string.stat_streak, streak)}" else today

        setupCard.removeAllViews()
        if (!Settings.canDrawOverlays(this)) {
            setupCard.addView(setupRow(getString(R.string.setup_overlay)) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
                )
            })
        }
        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            setupCard.addView(setupRow(getString(R.string.setup_battery)) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri())
                )
            })
        }
        setupSection.visibility = if (setupCard.isEmpty()) View.GONE else View.VISIBLE
    }

    /** "Today 14:32", "Tomorrow 09:10", "Sat 10:00", or "nothing scheduled". */
    private fun nextLabel(): String {
        val t = Store.nextNagAt(this)
        if (t <= System.currentTimeMillis()) return getString(R.string.nothing_scheduled)
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        val now = Calendar.getInstance()
        val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        fun sameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        val day = when {
            sameDay(cal, now) -> getString(R.string.today)
            sameDay(cal, tomorrow) -> getString(R.string.tomorrow)
            else -> SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time)
        }
        return "$day ${clock(t)}"
    }

    private fun clock(t: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        return fmt(cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))
    }

    private fun setupRow(label: String, onFix: () -> Unit) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, px(6), 0, px(6))
        addView(text(label, 16f, TEXT).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(button(getString(R.string.fix), ACCENT, TEXT, onClick = onFix).apply {
            minWidth = 0
            minimumWidth = 0
            setPadding(px(18), px(6), px(18), px(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
    }

    // ---- chore list ----

    private fun addChoreRow(chore: String, secs: Int, focus: Boolean = false) {
        val row = card(vertical = false).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(px(16), px(4), px(8), px(4))
        }
        val name = EditText(this).apply {
            setText(chore)
            hint = getString(R.string.hint_chore)
            setHintTextColor(MUTED)
            setTextColor(TEXT)
            textSize = 16f
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            tag = "name"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val secsField = numField(secs).apply {
            tag = "secs"
            minWidth = px(48)
            gravity = Gravity.CENTER
        }
        val unit = text(getString(R.string.unit_seconds), 14f, MUTED).apply {
            setPaddingRelative(px(2), 0, px(4), 0)
            contentDescription = getString(R.string.unit_seconds_desc)
        }
        val remove = Button(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(DANGER)
            background = null
            minWidth = px(48)
            minimumWidth = px(48)
            minHeight = px(48)
            minimumHeight = px(48)
            contentDescription = getString(R.string.remove_chore_desc)
            setOnClickListener {
                val index = choreList.indexOfChild(row)
                choreList.removeView(row)
                showUndo { choreList.addView(row, index.coerceAtMost(choreList.childCount)) }
            }
        }
        row.addView(name)
        row.addView(secsField)
        row.addView(unit)
        row.addView(remove)
        row.layoutParams = rowParams(top = 8)
        choreList.addView(row)
        if (focus) name.requestFocus()
    }

    private fun showUndo(action: () -> Unit) {
        undoAction = action
        undoBar.visibility = View.VISIBLE
        handler.removeCallbacks(hideUndo)
        handler.postDelayed(hideUndo, UNDO_MS)
    }

    private fun dismissUndo() {
        handler.removeCallbacks(hideUndo)
        undoAction = null
        undoBar.visibility = View.GONE
    }

    /** Back to the stored "text;seconds" per-line format. */
    private fun serializeChores(): String {
        val lines = ArrayList<String>()
        for (i in 0 until choreList.childCount) {
            val row = choreList.getChildAt(i)
            val t = row.findViewWithTag<EditText>("name").text.toString().replace(';', ',').trim()
            val s = row.findViewWithTag<EditText>("secs").text.toString().toIntOrNull() ?: 60
            if (t.isNotEmpty()) lines.add("$t;${s.coerceAtLeast(1)}")
        }
        return lines.joinToString("\n")
    }

    // ---- small UI helpers ----

    private fun rowParams(top: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = px(top) }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }

    private fun section(t: String) = text(t.uppercase(), 12f, MUTED).apply {
        letterSpacing = 0.15f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPaddingRelative(px(4), px(24), 0, 0)
    }

    private fun card(vertical: Boolean = true) = LinearLayout(this).apply {
        orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        background = GradientDrawable().apply {
            setColor(CARD)
            cornerRadius = px(16).toFloat()
        }
        layoutParams = rowParams(top = 8)
        if (vertical) setPadding(px(16), px(4), px(16), px(4))
    }

    private fun numField(v: Int) = EditText(this).apply {
        setText(context.getString(R.string.number_fmt, v))
        inputType = InputType.TYPE_CLASS_NUMBER
        setTextColor(TEXT)
        textSize = 16f
        background = null
        minWidth = px(56)
        gravity = Gravity.END
    }

    private fun fmt(min: Int) = "%02d:%02d".format((min / 60) % 24, min % 60)

    /** Tappable time that opens a clock. */
    private fun timeField(initial: Int, onPicked: (Int) -> Unit) = EditText(this).apply {
        setText(fmt(initial))
        setTextColor(ACCENT)
        textSize = 16f
        background = null
        isFocusable = false
        isCursorVisible = false
        gravity = Gravity.END
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        var current = initial
        setOnClickListener {
            TimePickerDialog(this@MainActivity, { _, h, m ->
                current = h * 60 + m
                onPicked(current)
                setText(fmt(current))
            }, (current / 60) % 24, current % 60, true).show()
        }
    }

    /** Mon..Sun round toggles; at least one day always stays on. */
    private fun dayChips(initial: Int, onChange: (Int) -> Unit) = LinearLayout(this).apply {
        var mask = initial
        val letters = resources.getStringArray(R.array.days_short)
        val names = resources.getStringArray(R.array.days_full)
        setPadding(0, 0, 0, px(12))
        WEEK.forEachIndexed { i, dow ->
            val bit = 1 shl (dow - 1)
            val chip = TextView(this@MainActivity).apply {
                text = letters[i]
                textSize = 14f
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            fun style() {
                val on = mask and bit != 0
                chip.setTextColor(if (on) TEXT else MUTED)
                if (Build.VERSION.SDK_INT >= 30) {
                    chip.stateDescription = getString(if (on) R.string.day_on else R.string.day_off)
                }
                chip.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (on) ACCENT else CHIP_OFF)
                }
            }
            chip.contentDescription = names[i]
            chip.isClickable = true
            style()
            chip.setOnClickListener {
                val next = mask xor bit
                if (next != 0) {
                    mask = next
                    onChange(mask)
                    style()
                }
            }
            addView(chip, LinearLayout.LayoutParams(px(38), px(38)).apply {
                if (i > 0) marginStart = px(8)
            })
        }
    }

    private fun settingRow(label: String, field: View, unit: String) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        if (field.id == View.NO_ID) field.id = View.generateViewId()
        addView(text(label, 16f, TEXT).apply {
            labelFor = field.id
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(field)
        addView(text(unit, 14f, MUTED).apply { minWidth = px(32); setPaddingRelative(px(4), 0, 0, 0) })
    }

    private fun button(label: String, fill: Int, textColor: Int, outline: Boolean = false, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(textColor)
            stateListAnimator = null
            background = GradientDrawable().apply {
                setColor(fill)
                cornerRadius = px(28).toFloat()
                if (outline) setStroke(px(1), textColor)
            }
            setPadding(0, px(14), 0, px(14))
            minHeight = px(48)
            minimumHeight = px(48)
            setOnClickListener { onClick() }
        }
}
