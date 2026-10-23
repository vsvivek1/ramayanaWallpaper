package com.calecutech.ramayanam

import android.app.WallpaperManager
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The "story page" live wallpaper. One home screen page shows the painting and the story,
 * laid out clear of the dock; the other pages show the painting only.
 *
 * It follows the launcher's page scroll through onOffsetsChanged. Launchers that don't report
 * pages (or a home screen with a single page) get the story layout, and a double tap on an
 * empty spot switches between story and picture.
 */
class StoryWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = StoryEngine()

    private inner class StoryEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val main = Handler(Looper.getMainLooper())
        private val renderThread = HandlerThread("story-render").apply { start() }
        private val renderer = Handler(renderThread.looper)

        private var width = 0
        private var height = 0
        private var storyBitmap: Bitmap? = null
        private var plainBitmap: Bitmap? = null
        /** What the current bitmaps were drawn for, so they are redrawn only when something changed. */
        private var renderedKey: String? = null
        private var renderToken = 0

        /** Page position reported by the launcher, and the number of pages. */
        private var pagePos = 0f
        private var pageCount = 0
        private val pagesKnown get() = pageCount >= 2
        /** Fallback mode: whether a double tap switched to the picture-only view. */
        private var pictureOnly = false
        private var lastTap = 0L

        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
            Prefs.registerListener(applicationContext, this)
        }

        override fun onDestroy() {
            Prefs.unregisterListener(applicationContext, this)
            renderThread.quitSafely()
            main.removeCallbacksAndMessages(null)
            storyBitmap?.recycle(); storyBitmap = null
            plainBitmap?.recycle(); plainBitmap = null
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w; height = h
            refresh(force = false)
            draw()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            // The day may have changed, or settings were changed in the app.
            if (visible) { refresh(force = false); draw() }
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            main.post { refresh(force = false); draw() }
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            if (xStep > 0f && xStep < 1f) {
                pageCount = (1f / xStep).roundToInt() + 1
                pagePos = xOffset / xStep
            } else {
                pageCount = 0
            }
            draw()
        }

        override fun onCommand(action: String?, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            if (action == WallpaperManager.COMMAND_TAP && !pagesKnown) {
                val now = SystemClock.uptimeMillis()
                if (now - lastTap < 400) {
                    pictureOnly = !pictureOnly
                    lastTap = 0
                    draw()
                } else {
                    lastTap = now
                }
            }
            return null
        }

        /** How much of the story layout to show: 1 on the story page, 0 on the others, a blend in between. */
        private fun storyAmount(): Float {
            if (isPreview) return 1f
            if (!pagesKnown) return if (pictureOnly) 0f else 1f
            val storyIndex = when (Prefs.storyPage(applicationContext)) {
                StoryPage.FIRST -> 0
                StoryPage.SECOND -> 1
                StoryPage.THIRD -> 2
                StoryPage.LAST -> pageCount - 1
            }.coerceAtMost(pageCount - 1)
            return (1f - abs(pagePos - storyIndex)).coerceIn(0f, 1f)
        }

        private fun currentIncident(): Incident {
            val c = applicationContext
            val incidents = Incidents.all(c)
            val picked = Prefs.liveIncidentId(c)?.let { id -> incidents.firstOrNull { it.id == id } }
            return picked ?: incidents[Incidents.indexFor(c, LocalDate.now())]
        }

        /** Redraws both bitmaps in the background when the incident, language, size or settings changed. */
        private fun refresh(force: Boolean) {
            if (width <= 0 || height <= 0) return
            val c = applicationContext
            val incident = currentIncident()
            val lang = Lang.current(c)
            val space = Prefs.dockSpace(c)
            val key = "${incident.id}|$lang|$space|${width}x$height"
            if (!force && key == renderedKey) return
            renderedKey = key
            val token = ++renderToken
            val w = width; val h = height
            renderer.post {
                val painting = runCatching { ImageStore.load(c, incident) }.getOrNull()
                val centre = Lang.info(lang).sriRama
                val story = WallpaperRenderer.render(w, h, incident, painting, true, centre, space.fraction)
                val plain = WallpaperRenderer.render(w, h, incident, painting, false, centre, space.fraction)
                painting?.recycle()
                main.post {
                    if (token != renderToken) { story.recycle(); plain.recycle(); return@post }
                    storyBitmap?.recycle(); plainBitmap?.recycle()
                    storyBitmap = story; plainBitmap = plain
                    draw()
                }
            }
        }

        private fun draw() {
            val holder = surfaceHolder ?: return
            val canvas: Canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull()
                ?: runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                val story = storyBitmap
                val plain = plainBitmap
                if (story == null || plain == null) {
                    canvas.drawColor(Color.rgb(0x2A, 0x08, 0x06))
                    return
                }
                val amount = storyAmount()
                val rect = android.graphics.Rect(0, 0, canvas.width, canvas.height)
                paint.alpha = 255
                if (amount < 1f) canvas.drawBitmap(plain, null, rect, paint)
                if (amount > 0f) {
                    paint.alpha = (amount * 255).roundToInt()
                    canvas.drawBitmap(story, null, rect, paint)
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
