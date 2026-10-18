package com.calecutech.bhagavatham

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** Wallpaper targets, matching WallpaperManager.FLAG_SYSTEM / FLAG_LOCK. */
enum class Target { BOTH, HOME, LOCK }

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
}
