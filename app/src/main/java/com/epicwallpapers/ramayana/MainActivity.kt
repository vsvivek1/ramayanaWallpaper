package com.epicwallpapers.ramayana

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var incidents: List<Incident>
    private var todayIndex = 0
    private var shownIndex = 0
    private var previewBitmap: Bitmap? = null
    private var renderToken = 0

    private lateinit var preview: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var kanda: TextView
    private lateinit var title: TextView
    private lateinit var story: TextView
    private lateinit var credit: TextView
    private lateinit var dayLabel: TextView
    private lateinit var todayButton: Button

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Lang.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyInsets()

        incidents = Incidents.all(this)
        todayIndex = Incidents.indexFor(this)
        shownIndex = todayIndex

        preview = findViewById(R.id.preview)
        progress = findViewById(R.id.progress)
        kanda = findViewById(R.id.kanda)
        title = findViewById(R.id.title)
        story = findViewById(R.id.story)
        credit = findViewById(R.id.credit)
        dayLabel = findViewById(R.id.dayLabel)
        todayButton = findViewById(R.id.today)
        credit.movementMethod = LinkMovementMethod.getInstance()

        findViewById<Button>(R.id.prev).setOnClickListener { show(shownIndex - 1) }
        findViewById<Button>(R.id.next).setOnClickListener { show(shownIndex + 1) }
        findViewById<Button>(R.id.browse).setOnClickListener { pickIncident() }
        todayButton.setOnClickListener { show(todayIndex) }
        findViewById<Button>(R.id.setNow).setOnClickListener { setShownAsWallpaper() }
        findViewById<Button>(R.id.restart).setOnClickListener { confirmRestart() }
        findViewById<Button>(R.id.language).setOnClickListener { pickLanguage() }

        setupSettings()

        if (Prefs.dailyEnabled(this)) {
            DailyWallpaperJob.schedule(this)
            // First open of the day: apply today's wallpaper straight away.
            worker.execute { runCatching { WallpaperSetter.applyTodayIfNeeded(applicationContext) } }
        }

        show(todayIndex)
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdown()
    }

    /** Pads the screen so content clears the status and navigation bars (edge-to-edge on Android 15+). */
    private fun applyInsets() {
        val header = findViewById<View>(R.id.header)
        val root = findViewById<View>(R.id.root)
        val baseTop = header.paddingTop
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            header.setPadding(header.paddingLeft, baseTop + insets.systemWindowInsetTop, header.paddingRight, header.paddingBottom)
            @Suppress("DEPRECATION")
            root.setPadding(0, 0, 0, insets.systemWindowInsetBottom)
            insets
        }
    }

    private fun setupSettings() {
        findViewById<Button>(R.id.language).text = languageButtonText()

        val daily = findViewById<Switch>(R.id.dailySwitch)
        daily.isChecked = Prefs.dailyEnabled(this)
        daily.setOnCheckedChangeListener { _, on ->
            Prefs.setDailyEnabled(this, on)
            if (on) {
                DailyWallpaperJob.schedule(this)
                worker.execute { runCatching { WallpaperSetter.applyTodayIfNeeded(applicationContext) } }
            } else {
                DailyWallpaperJob.cancel(this)
            }
        }

        val text = findViewById<Switch>(R.id.textSwitch)
        text.isChecked = Prefs.showText(this)
        text.setOnCheckedChangeListener { _, on ->
            Prefs.setShowText(this, on)
            renderPreview()
        }

        val group = findViewById<RadioGroup>(R.id.targetGroup)
        group.check(
            when (Prefs.target(this)) {
                Target.BOTH -> R.id.targetBoth
                Target.HOME -> R.id.targetHome
                Target.LOCK -> R.id.targetLock
            }
        )
        group.setOnCheckedChangeListener { _, id ->
            Prefs.setTarget(
                this,
                when (id) {
                    R.id.targetHome -> Target.HOME
                    R.id.targetLock -> Target.LOCK
                    else -> Target.BOTH
                }
            )
        }
    }

    private fun show(index: Int) {
        shownIndex = Math.floorMod(index, incidents.size)
        val incident = incidents[shownIndex]
        kanda.text = incident.kanda
        title.text = incident.title
        story.text = incident.story

        val source = incident.sourcePageUrl()
        credit.text = if (source != null) {
            Html.fromHtml("${incident.creditLine(this)} · <a href=\"$source\">${getString(R.string.source)}</a>", Html.FROM_HTML_MODE_COMPACT)
        } else {
            incident.creditLine(this)
        }

        val isToday = shownIndex == todayIndex
        val dayNumber = shownIndex + 1
        val date = LocalDate.now().plusDays((shownIndex - todayIndex).toLong())
        val dateText = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", resources.configuration.locales[0]))
        dayLabel.text = if (isToday) getString(R.string.today_label, dateText, dayNumber, incidents.size)
        else getString(R.string.incident_label, dayNumber, incidents.size)
        todayButton.visibility = if (isToday) View.GONE else View.VISIBLE

        renderPreview()
    }

    /** Draws the real wallpaper at a smaller size so the preview matches what the phone shows. */
    private fun renderPreview() {
        val incident = incidents[shownIndex]
        val token = ++renderToken
        progress.visibility = View.VISIBLE
        val showText = Prefs.showText(this)
        val centreText = Lang.info(Lang.current(this)).sriRama
        worker.execute {
            val (sw, sh) = WallpaperSetter.screenSize(applicationContext)
            val w = 720
            val h = (w.toLong() * sh / sw).toInt()
            val painting = runCatching { ImageStore.load(applicationContext, incident) }.getOrNull()
            val bmp = WallpaperRenderer.render(w, h, incident, painting, showText, centreText)
            painting?.recycle()
            main.post {
                if (isDestroyed || token != renderToken) {
                    bmp.recycle(); return@post
                }
                preview.setImageBitmap(bmp)
                previewBitmap?.recycle()
                previewBitmap = bmp
                progress.visibility = View.GONE
                if (painting == null && incident.image != null) {
                    Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setShownAsWallpaper() {
        val incident = incidents[shownIndex]
        val index = shownIndex
        progress.visibility = View.VISIBLE
        worker.execute {
            val result = runCatching { WallpaperSetter.apply(applicationContext, incident) }
            if (result.isSuccess && index == todayIndex) {
                Prefs.setLastAppliedDay(applicationContext, LocalDate.now().toEpochDay())
                Prefs.setLastAppliedWithImage(applicationContext, result.getOrDefault(false))
            }
            main.post {
                if (isDestroyed) return@post
                progress.visibility = View.GONE
                val msg = if (result.isSuccess) getString(R.string.wallpaper_set, incident.title) else getString(R.string.wallpaper_failed)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pickIncident() {
        val labels = incidents.mapIndexed { i, it -> "${i + 1}. ${it.title}  ·  ${it.kanda}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.browse_all)
            .setItems(labels) { _, which -> show(which) }
            .show()
    }

    private fun languageButtonText(): String {
        val chosen = Prefs.language(this)
        val name = if (chosen == Lang.AUTO) getString(R.string.language_auto, Lang.info(Lang.current(this)).nativeName)
        else Lang.info(chosen).nativeName
        return getString(R.string.language_button, name)
    }

    private fun pickLanguage() {
        val codes = listOf(Lang.AUTO) + Lang.SUPPORTED.map { it.code }
        val labels = codes.map {
            if (it == Lang.AUTO) getString(R.string.language_phone) else Lang.info(it).nativeName
        }.toTypedArray()
        val checked = codes.indexOf(Prefs.language(this)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                dialog.dismiss()
                if (codes[which] == Prefs.language(this)) return@setSingleChoiceItems
                Prefs.setLanguage(this, codes[which])
                // Redraw today's wallpaper in the new language, then reload the screen.
                worker.execute { runCatching { WallpaperSetter.applyTodayIfNeeded(applicationContext, force = Prefs.dailyEnabled(applicationContext)) } }
                recreate()
            }
            .show()
    }

    private fun confirmRestart() {
        AlertDialog.Builder(this)
            .setTitle(R.string.restart)
            .setMessage(R.string.restart_message)
            .setPositiveButton(R.string.restart_confirm) { _, _ ->
                Prefs.restartFromToday(this)
                todayIndex = Incidents.indexFor(this)
                show(todayIndex)
                worker.execute { runCatching { WallpaperSetter.applyTodayIfNeeded(applicationContext, force = true) } }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
