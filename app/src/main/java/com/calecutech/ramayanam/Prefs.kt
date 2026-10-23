package com.calecutech.ramayanam

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** Wallpaper targets, matching WallpaperManager.FLAG_SYSTEM / FLAG_LOCK. */
enum class Target { BOTH, HOME, LOCK }

/** How much room to leave at the bottom of the wallpaper for the dock (fixed apps) and gesture bar. */
enum class DockSpace(val fraction: Float) { SMALL(0.13f), MEDIUM(0.20f), LARGE(0.27f) }

/** Which home screen page shows the picture and story when the story page is on. */
enum class StoryPage { FIRST, SECOND, THIRD, LAST }

object Prefs {
    private fun p(c: Context): SharedPreferences =
        c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The day the story started. Set on first launch so the epic begins at incident one. */
    fun startEpochDay(c: Context): Long {
        val prefs = p(c)
        if (!prefs.contains("start_day")) {
            prefs.edit().putLong("start_day", LocalDate.now().toEpochDay()).apply()
        }
        return prefs.getLong("start_day", LocalDate.now().toEpochDay())
    }

    fun restartFromToday(c: Context) {
        p(c).edit()
            .putLong("start_day", LocalDate.now().toEpochDay())
            .remove("last_applied_day")
            .apply()
    }

    fun dailyEnabled(c: Context) = p(c).getBoolean("daily", true)
    fun setDailyEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("daily", v).apply()

    fun showText(c: Context) = p(c).getBoolean("show_text", true)
    fun setShowText(c: Context, v: Boolean) = p(c).edit().putBoolean("show_text", v).apply()

    /** Story language code, or "auto" to follow the phone. */
    fun language(c: Context): String = p(c).getString("language", Lang.AUTO) ?: Lang.AUTO
    fun setLanguage(c: Context, code: String) = p(c).edit().putString("language", code).apply()

    fun target(c: Context): Target =
        runCatching { Target.valueOf(p(c).getString("target", Target.BOTH.name)!!) }
            .getOrDefault(Target.BOTH)

    fun setTarget(c: Context, t: Target) = p(c).edit().putString("target", t.name).apply()

    /** Epoch day the daily wallpaper was last applied, or -1. */
    fun lastAppliedDay(c: Context) = p(c).getLong("last_applied_day", -1)
    fun setLastAppliedDay(c: Context, day: Long) =
        p(c).edit().putLong("last_applied_day", day).apply()

    /** Whether the last daily wallpaper had to use fallback art because the image was missing. */
    fun lastAppliedWithImage(c: Context) = p(c).getBoolean("last_with_image", true)
    fun setLastAppliedWithImage(c: Context, v: Boolean) =
        p(c).edit().putBoolean("last_with_image", v).apply()

    fun dockSpace(c: Context): DockSpace =
        runCatching { DockSpace.valueOf(p(c).getString("dock_space", DockSpace.MEDIUM.name)!!) }
            .getOrDefault(DockSpace.MEDIUM)

    fun setDockSpace(c: Context, v: DockSpace) = p(c).edit().putString("dock_space", v.name).apply()

    fun storyPage(c: Context): StoryPage =
        runCatching { StoryPage.valueOf(p(c).getString("story_page", StoryPage.FIRST.name)!!) }
            .getOrDefault(StoryPage.FIRST)

    fun setStoryPage(c: Context, v: StoryPage) = p(c).edit().putString("story_page", v.name).apply()

    /**
     * The incident the story page shows, when it was picked by hand today ("Set as wallpaper"
     * on another incident). Returns null when the story page should show today's incident.
     */
    fun liveIncidentId(c: Context): Int? {
        val prefs = p(c)
        if (prefs.getLong("live_day", -1) != LocalDate.now().toEpochDay()) return null
        return prefs.getInt("live_incident", -1).takeIf { it >= 0 }
    }

    /** Stores the story page's incident and tells the running story page to redraw. */
    fun setLiveIncident(c: Context, id: Int) = p(c).edit()
        .putInt("live_incident", id)
        .putLong("live_day", LocalDate.now().toEpochDay())
        .putLong("live_version", System.currentTimeMillis())
        .apply()

    fun registerListener(c: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) =
        p(c).registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(c: Context, l: SharedPreferences.OnSharedPreferenceChangeListener) =
        p(c).unregisterOnSharedPreferenceChangeListener(l)
}
