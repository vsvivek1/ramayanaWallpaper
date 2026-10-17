package com.epicwallpapers.ramayana

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the wallpaper: the painting framed on a softened copy of itself, with the
 * kanda, title and short story in a dark band at the bottom. Incidents without a
 * painting get a drawn mandala on a maroon and saffron background instead.
 */
object WallpaperRenderer {
    private val GOLD = Color.rgb(0xE8, 0xC2, 0x7A)
    private val SAFFRON = Color.rgb(0xF2, 0x9A, 0x2E)
    private val PARCHMENT = Color.rgb(0xFB, 0xF4, 0xE6)

    fun render(width: Int, height: Int, incident: Incident, painting: Bitmap?, showText: Boolean): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val w = width.toFloat()
        val h = height.toFloat()

        // Area the picture may use.
        val box = if (showText) RectF(w * 0.05f, h * 0.07f, w * 0.95f, h * 0.60f)
        else RectF(w * 0.04f, h * 0.08f, w * 0.96f, h * 0.90f)

        if (painting != null) {
            drawSoftBackground(canvas, painting, w, h)
            drawFramedPainting(canvas, painting, box, w)
        } else {
            drawFallbackArt(canvas, w, h, box)
        }

        if (showText) drawStory(canvas, incident, w, h)
        return out
    }

    /** A blurred, darkened cover-fit copy of the painting, made by shrinking and re-enlarging it. */
    private fun drawSoftBackground(canvas: Canvas, painting: Bitmap, w: Float, h: Float) {
        val tinyW = 24
        val tinyH = (tinyW * painting.height / painting.width.toFloat()).toInt().coerceAtLeast(1)
        val tiny = Bitmap.createScaledBitmap(painting, tinyW, tinyH, true)
        val scale = maxOf(w / tiny.width, h / tiny.height)
        val dw = tiny.width * scale
        val dh = tiny.height * scale
        val dst = RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2)
        canvas.drawBitmap(tiny, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        tiny.recycle()
        canvas.drawColor(Color.argb(150, 20, 6, 4))
    }

    private fun drawFramedPainting(canvas: Canvas, painting: Bitmap, box: RectF, w: Float) {
        val scale = min(box.width() / painting.width, box.height() / painting.height)
        val dw = painting.width * scale
        val dh = painting.height * scale
        val left = box.centerX() - dw / 2
        val top = box.centerY() - dh / 2
        val dst = RectF(left, top, left + dw, top + dh)

        // Shadow, then a thin gold frame.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            setShadowLayer(w * 0.03f, 0f, w * 0.01f, Color.argb(180, 0, 0, 0))
        }
        canvas.drawRect(dst, shadow)
        canvas.drawBitmap(painting, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.004f
            color = GOLD
        }
        val inset = -w * 0.008f
        canvas.drawRect(RectF(dst).apply { inset(inset, inset) }, frame)
    }

    private fun drawFallbackArt(canvas: Canvas, w: Float, h: Float, box: RectF) {
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(Color.rgb(0x3A, 0x0B, 0x0B), Color.rgb(0x7A, 0x1C, 0x12), Color.rgb(0x2A, 0x08, 0x06)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, bg)

        val cx = box.centerX()
        val cy = box.centerY()
        val r = min(box.width(), box.height()) * 0.46f

        // Warm glow behind the mandala.
        canvas.drawCircle(cx, cy, r * 1.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, r * 1.4f, Color.argb(120, 0xF2, 0x9A, 0x2E), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = GOLD
            strokeWidth = w * 0.003f
        }
        // Rings.
        for (k in 1..4) {
            line.alpha = 70 + k * 30
            canvas.drawCircle(cx, cy, r * (0.25f + 0.25f * k) * 0.8f, line)
        }
        // Lotus petals, two layers.
        for (layer in 0..1) {
            val petals = if (layer == 0) 16 else 8
            val outer = r * (if (layer == 0) 1.0f else 0.7f)
            val inner = r * (if (layer == 0) 0.62f else 0.32f)
            line.alpha = if (layer == 0) 150 else 220
            for (i in 0 until petals) {
                val a = (2 * Math.PI * i / petals + layer * Math.PI / petals).toFloat()
                val spread = (Math.PI / petals).toFloat()
                val p = Path()
                p.moveTo(cx + inner * cos(a - spread), cy + inner * sin(a - spread))
                p.quadTo(cx + outer * 0.9f * cos(a - spread * 0.6f), cy + outer * 0.9f * sin(a - spread * 0.6f), cx + outer * cos(a), cy + outer * sin(a))
                p.quadTo(cx + outer * 0.9f * cos(a + spread * 0.6f), cy + outer * 0.9f * sin(a + spread * 0.6f), cx + inner * cos(a + spread), cy + inner * sin(a + spread))
                canvas.drawPath(p, line)
            }
        }

        // Name in the centre.
        val name = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            textAlign = Paint.Align.CENTER
            textSize = r * 0.30f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val text = "श्री राम"
        val bounds = Rect()
        name.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, cx, cy - bounds.exactCenterY(), name)
    }

    private fun drawStory(canvas: Canvas, incident: Incident, w: Float, h: Float) {
        // Dark band so the text reads on any picture.
        val bandTop = h * 0.60f
        canvas.drawRect(0f, bandTop, w, h, Paint().apply {
            shader = LinearGradient(
                0f, bandTop, 0f, h,
                intArrayOf(Color.TRANSPARENT, Color.argb(215, 12, 4, 2), Color.argb(235, 8, 2, 1)),
                floatArrayOf(0f, 0.22f, 1f), Shader.TileMode.CLAMP,
            )
        })

        val margin = w * 0.07f
        val textWidth = (w - 2 * margin).toInt()
        val maxBottom = h * 0.87f // keep clear of the dock and gesture bar
        var y = h * 0.635f

        val kandaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SAFFRON
            textSize = w * 0.030f
            letterSpacing = 0.14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        y += kandaPaint.textSize
        canvas.drawText(incident.kanda.uppercase(), margin, y, kandaPaint)
        y += w * 0.02f

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            textSize = w * 0.062f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val titleLayout = layout(incident.title, titlePaint, textWidth, 1.05f)
        canvas.save(); canvas.translate(margin, y); titleLayout.draw(canvas); canvas.restore()
        y += titleLayout.height + w * 0.025f

        // Shrink the story text until it fits above the dock area.
        val storyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PARCHMENT
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        var size = w * 0.038f
        var storyLayout: StaticLayout
        while (true) {
            storyPaint.textSize = size
            storyLayout = layout(incident.story, storyPaint, textWidth, 1.25f)
            if (y + storyLayout.height <= maxBottom || size <= w * 0.026f) break
            size *= 0.94f
        }
        canvas.save(); canvas.translate(margin, y); storyLayout.draw(canvas); canvas.restore()
    }

    private fun layout(text: String, paint: TextPaint, width: Int, spacing: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .build()
}
