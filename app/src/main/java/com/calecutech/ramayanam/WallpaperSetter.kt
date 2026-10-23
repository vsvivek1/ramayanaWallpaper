package com.calecutech.ramayanam

import android.app.WallpaperManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import java.time.LocalDate

object WallpaperSetter {

    /** Full screen size in pixels (portrait). */
    fun screenSize(c: Context): Pair<Int, Int> {
        val dm = c.getSystemService(DisplayManager::class.java)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        dm.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
        val w = minOf(metrics.widthPixels, metrics.heightPixels)
        val h = maxOf(metrics.widthPixels, metrics.heightPixels)
        return w to h
    }

    /** Whether this app's story page (live wallpaper) is the current home screen wallpaper. */
    fun isStoryPageActive(c: Context): Boolean =
        runCatching { WallpaperManager.getInstance(c).wallpaperInfo?.packageName == c.packageName }
            .getOrDefault(false)

    /**
     * Renders and applies the wallpaper for one incident.
     * Returns true when the painting was available (false means fallback art was used).
     *
     * When the story page is on, the home screen draws itself, so it is only told which
     * incident to show; the lock screen still gets a picture if it is one of the targets.
     */
    fun apply(c: Context, incident: Incident, allowNetwork: Boolean = true): Boolean {
        val target = Prefs.target(c)
        val live = isStoryPageActive(c)
        if (live) {
            Prefs.setLiveIncident(c, incident.id)
            if (target == Target.HOME) return true
        }
        val (w, h) = screenSize(c)
        val painting = ImageStore.load(c, incident, allowNetwork)
        val bitmap = WallpaperRenderer.render(
            w, h, incident, painting, Prefs.showText(c), Lang.info(Lang.current(c)).sriRama,
            Prefs.dockSpace(c).fraction,
        )
        val flags = when {
            // Setting the home screen picture would replace the story page.
            live -> WallpaperManager.FLAG_LOCK
            target == Target.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            target == Target.HOME -> WallpaperManager.FLAG_SYSTEM
            else -> WallpaperManager.FLAG_LOCK
        }
        WallpaperManager.getInstance(c).setBitmap(bitmap, null, true, flags)
        bitmap.recycle()
        painting?.recycle()
        return painting != null || incident.image == null
    }

    /** Turns the story page off by putting the normal picture back on the home screen. */
    fun turnOffStoryPage(c: Context, incident: Incident) {
        val (w, h) = screenSize(c)
        val painting = ImageStore.load(c, incident)
        val bitmap = WallpaperRenderer.render(
            w, h, incident, painting, Prefs.showText(c), Lang.info(Lang.current(c)).sriRama,
            Prefs.dockSpace(c).fraction,
        )
        val flags = if (Prefs.target(c) == Target.HOME) WallpaperManager.FLAG_SYSTEM
        else WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        WallpaperManager.getInstance(c).setBitmap(bitmap, null, true, flags)
        bitmap.recycle()
        painting?.recycle()
    }

    /**
     * Applies today's incident if it hasn't been applied yet today, or if it was applied
     * with fallback art and the painting can now be downloaded. Also prefetches tomorrow's image.
     */
    fun applyTodayIfNeeded(c: Context, force: Boolean = false) {
        if (!Prefs.dailyEnabled(c) && !force) return
        val today = LocalDate.now()
        val todayDay = today.toEpochDay()
        val incidents = Incidents.all(c)
        val incident = incidents[Incidents.indexFor(c, today)]

        val alreadyDone = Prefs.lastAppliedDay(c) == todayDay
        val needsRetry = alreadyDone && !Prefs.lastAppliedWithImage(c)
        if (force || !alreadyDone || needsRetry) {
            if (needsRetry && !force) {
                // Only redo it if the painting is now obtainable.
                ImageStore.load(c, incident)?.recycle() ?: return
            }
            val withImage = apply(c, incident)
            Prefs.setLastAppliedDay(c, todayDay)
            Prefs.setLastAppliedWithImage(c, withImage)
        }

        // Fetch tomorrow's painting now, so it is ready even if the phone is offline at midnight.
        val tomorrow = incidents[Incidents.indexFor(c, today.plusDays(1))]
        if (!ImageStore.isCached(c, tomorrow)) ImageStore.load(c, tomorrow)?.recycle()
    }
}
