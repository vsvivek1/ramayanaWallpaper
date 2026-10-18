package com.calecutech.ramayanam

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Languages the stories are available in. "auto" follows the phone's language,
 * falling back to English when the phone's language isn't one of these.
 */
object Lang {
    data class Language(val code: String, val nativeName: String, val sriRama: String)

    val SUPPORTED = listOf(
        Language("en", "English", "श्री राम"),
        Language("ml", "മലയാളം", "ശ്രീ രാമ"),
        Language("ta", "தமிழ்", "ஸ்ரீ ராம"),
        Language("te", "తెలుగు", "శ్రీ రామ"),
        Language("kn", "ಕನ್ನಡ", "ಶ್ರೀ ರಾಮ"),
        Language("hi", "हिन्दी", "श्री राम"),
    )

    const val AUTO = "auto"

    /** The language code actually in use. */
    fun current(c: Context): String {
        val chosen = Prefs.language(c)
        if (chosen != AUTO && SUPPORTED.any { it.code == chosen }) return chosen
        val phone = Locale.getDefault().language
        return SUPPORTED.firstOrNull { it.code == phone }?.code ?: "en"
    }

    fun info(code: String) = SUPPORTED.firstOrNull { it.code == code } ?: SUPPORTED[0]

    /** A context whose resources (screen text) use the chosen language. */
    fun wrap(base: Context): Context {
        if (Prefs.language(base) == AUTO) return base
        val locale = Locale.forLanguageTag(current(base))
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
