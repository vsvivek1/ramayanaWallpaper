package com.calecutech.bhagavatham

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate

/** One incident of the epic, as stored in assets/incidents.json. */
data class Incident(
    val id: Int,
    /** English kanda name, used to pick the artwork colours. */
    val kandaKey: String,
    /** Kanda, title and story in the chosen language. */
    val kanda: String,
    val title: String,
    val story: String,
    /** Wikimedia Commons file name (without "File:"), or null to use the built-in artwork. */
    val image: String?,
    val artist: String?,
    /** Licence short name from Commons, e.g. "Public domain" or "CC BY-SA 4.0". */
    val license: String? = null,
) {
    /** Path of the painting bundled inside the app, if there is one. */
    val assetPath: String get() = "paintings/$id.jpg"

    /** Direct image URL. Special:FilePath redirects to a resized copy on upload.wikimedia.org. */
    fun imageUrl(widthPx: Int): String? = image?.let {
        "https://commons.wikimedia.org/wiki/Special:FilePath/${encode(it)}?width=$widthPx"
    }

    /** The Commons page that holds the source and licence for the image. */
    fun sourcePageUrl(): String? = image?.let {
        "https://commons.wikimedia.org/wiki/File:${encode(it)}"
    }

    fun creditLine(c: Context): String = when {
        image == null -> c.getString(R.string.credit_drawn)
        license != null && !isPublicDomain(license) ->
            c.getString(R.string.credit_licensed, artist ?: "Wikimedia Commons", license)
        artist != null -> c.getString(R.string.credit_artist, artist)
        else -> c.getString(R.string.credit_commons)
    }

    private fun isPublicDomain(l: String) =
        l.contains("public domain", true) || l.startsWith("PD", true) || l.contains("CC0", true)

    private fun encode(name: String) =
        URLEncoder.encode(name.replace(' ', '_'), "UTF-8").replace("+", "%20")
}

object Incidents {
    @Volatile private var cache: Pair<String, List<Incident>>? = null

    /**
     * All incidents, with text in the current language. Translations live in
     * assets/i18n/<code>.json as {"kandas": {english: local}, "incidents": {"id": {"title", "story"}}};
     * anything missing there falls back to English.
     */
    fun all(context: Context): List<Incident> {
        val lang = Lang.current(context)
        cache?.let { if (it.first == lang) return it.second }
        val text = context.assets.open("incidents.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("incidents")
        val tr = if (lang == "en") null else runCatching {
            JSONObject(context.assets.open("i18n/$lang.json").bufferedReader().use { it.readText() })
        }.getOrNull()
        val kandas = tr?.optJSONObject("kandas")
        val trIncidents = tr?.optJSONObject("incidents")
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getInt("id")
            val kandaKey = o.getString("kanda")
            val t = trIncidents?.optJSONObject(id.toString())
            Incident(
                id = id,
                kandaKey = kandaKey,
                kanda = kandas?.optString(kandaKey)?.takeIf { it.isNotBlank() } ?: kandaKey,
                title = t?.optString("title")?.takeIf { it.isNotBlank() } ?: o.getString("title"),
                story = t?.optString("story")?.takeIf { it.isNotBlank() } ?: o.getString("story"),
                image = o.optString("image").takeIf { !o.isNull("image") && it.isNotBlank() },
                artist = o.optString("artist").takeIf { !o.isNull("artist") && it.isNotBlank() },
                license = o.optString("license").takeIf { !o.isNull("license") && it.isNotBlank() },
            )
        }
        cache = lang to list
        return list
    }

    /**
     * Index of the incident for a given date. Day one after install is the first incident,
     * then one step per day, wrapping round to the start after the last one.
     */
    fun indexFor(context: Context, date: LocalDate = LocalDate.now()): Int {
        val size = all(context).size
        val start = Prefs.startEpochDay(context)
        val offset = date.toEpochDay() - start
        return Math.floorMod(offset, size.toLong()).toInt()
    }
}
