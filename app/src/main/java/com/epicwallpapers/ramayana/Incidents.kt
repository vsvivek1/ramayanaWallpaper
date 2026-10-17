package com.epicwallpapers.ramayana

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate

/** One incident of the epic, as stored in assets/incidents.json. */
data class Incident(
    val id: Int,
    val kanda: String,
    val title: String,
    val story: String,
    /** Wikimedia Commons file name (without "File:"), or null to use the built-in artwork. */
    val image: String?,
    val artist: String?,
) {
    /** Direct image URL. Special:FilePath redirects to a resized copy on upload.wikimedia.org. */
    fun imageUrl(widthPx: Int): String? = image?.let {
        "https://commons.wikimedia.org/wiki/Special:FilePath/${encode(it)}?width=$widthPx"
    }

    /** The Commons page that holds the source and licence for the image. */
    fun sourcePageUrl(): String? = image?.let {
        "https://commons.wikimedia.org/wiki/File:${encode(it)}"
    }

    fun creditLine(): String = when {
        image == null -> "Artwork: drawn by the app"
        artist != null -> "Painting: $artist · public domain · Wikimedia Commons"
        else -> "Image: Wikimedia Commons (public domain)"
    }

    private fun encode(name: String) =
        URLEncoder.encode(name.replace(' ', '_'), "UTF-8").replace("+", "%20")
}

object Incidents {
    @Volatile private var cache: List<Incident>? = null

    fun all(context: Context): List<Incident> {
        cache?.let { return it }
        val text = context.assets.open("incidents.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("incidents")
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Incident(
                id = o.getInt("id"),
                kanda = o.getString("kanda"),
                title = o.getString("title"),
                story = o.getString("story"),
                image = o.optString("image").takeIf { !o.isNull("image") && it.isNotBlank() },
                artist = o.optString("artist").takeIf { !o.isNull("artist") && it.isNotBlank() },
            )
        }
        cache = list
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
