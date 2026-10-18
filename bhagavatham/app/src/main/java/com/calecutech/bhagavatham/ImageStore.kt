package com.calecutech.bhagavatham

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads paintings from Wikimedia Commons and keeps them on disk, so each
 * image is fetched once and the wallpaper still works offline afterwards.
 */
object ImageStore {
    private const val WIDTH = 1600
    private const val USER_AGENT =
        "BhagavathamWallpapers/1.0 (Android; daily wallpaper app; public-domain art from Wikimedia Commons)"

    private fun dir(c: Context) = File(c.filesDir, "images").apply { mkdirs() }
    private fun file(c: Context, incident: Incident) = File(dir(c), "${incident.id}.jpg")

    fun isCached(c: Context, incident: Incident) =
        incident.image == null || hasAsset(c, incident) || file(c, incident).let { it.exists() && it.length() > 0 }

    private fun hasAsset(c: Context, incident: Incident) =
        runCatching { c.assets.open(incident.assetPath).close(); true }.getOrDefault(false)

    /** Paintings shipped inside the app, so they show even with no internet. */
    private fun loadAsset(c: Context, incident: Incident): Bitmap? = runCatching {
        c.assets.open(incident.assetPath).use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    /** Returns the painting, downloading it if needed. Null if there is none or it can't be fetched. */
    fun load(c: Context, incident: Incident, allowNetwork: Boolean = true): Bitmap? {
        if (incident.image == null) return null
        loadAsset(c, incident)?.let { return it }
        val url = incident.imageUrl(WIDTH) ?: return null
        val f = file(c, incident)
        if (!f.exists() || f.length() == 0L) {
            if (!allowNetwork || !download(url, f)) return null
        }
        return decode(f) ?: run { f.delete(); null }
    }

    private fun download(url: String, dest: File): Boolean {
        var current = URL(url)
        // Follow redirects by hand (commons.wikimedia.org -> upload.wikimedia.org).
        repeat(5) {
            val conn = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                when (conn.responseCode) {
                    in 300..399 -> {
                        val loc = conn.getHeaderField("Location") ?: return false
                        current = URL(current, loc)
                    }
                    200 -> {
                        val tmp = File(dest.parentFile, dest.name + ".part")
                        conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                        return tmp.length() > 0 && tmp.renameTo(dest)
                    }
                    else -> return false
                }
            } catch (e: Exception) {
                return false
            } finally {
                conn.disconnect()
            }
        }
        return false
    }

    private fun decode(f: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= WIDTH) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
