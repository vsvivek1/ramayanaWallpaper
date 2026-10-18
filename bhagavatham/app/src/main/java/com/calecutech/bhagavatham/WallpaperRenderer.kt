package com.calecutech.bhagavatham

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

    /** Background colours (top, middle, bottom) and glow colour of the drawn artwork, one set per skandha. */
    private class Palette(val top: Int, val mid: Int, val bottom: Int, val glow: Int)

    private val PALETTES = mapOf(
        "First Skandha" to Palette(0xFF0B1A3A.toInt(), 0xFF1E3F7A.toInt(), 0xFF070F26.toInt(), 0xFFE8C27A.toInt()),
        "Second Skandha" to Palette(0xFF14103A.toInt(), 0xFF3B2E7A.toInt(), 0xFF0B0826.toInt(), 0xFFC9B8F2.toInt()),
        "Third Skandha" to Palette(0xFF0A2222.toInt(), 0xFF1F5A5A.toInt(), 0xFF061616.toInt(), 0xFF7FD1C7.toInt()),
        "Fourth Skandha" to Palette(0xFF261606.toInt(), 0xFF7A5418.toInt(), 0xFF1A0E04.toInt(), 0xFFF2C14E.toInt()),
        "Fifth Skandha" to Palette(0xFF0A2214.toInt(), 0xFF2C5A30.toInt(), 0xFF07170D.toInt(), 0xFFC9D46A.toInt()),
        "Sixth Skandha" to Palette(0xFF221832.toInt(), 0xFF5E4475.toInt(), 0xFF150F20.toInt(), 0xFFF0C9A0.toInt()),
        "Seventh Skandha" to Palette(0xFF1A0505.toInt(), 0xFF8A1E10.toInt(), 0xFF120303.toInt(), 0xFFFF7A2E.toInt()),
        "Eighth Skandha" to Palette(0xFF04141F.toInt(), 0xFF0F4C5C.toInt(), 0xFF030D14.toInt(), 0xFF8FE0F0.toInt()),
        "Ninth Skandha" to Palette(0xFF3A0B0B.toInt(), 0xFF7A1C12.toInt(), 0xFF2A0806.toInt(), 0xFFF29A2E.toInt()),
        "Tenth Skandha" to Palette(0xFF061238.toInt(), 0xFF0E3A6E.toInt(), 0xFF040A22.toInt(), 0xFF6FD3B0.toInt()),
        "Eleventh Skandha" to Palette(0xFF12123A.toInt(), 0xFF3E2A6E.toInt(), 0xFF0D0B26.toInt(), 0xFFE8C27A.toInt()),
        "Twelfth Skandha" to Palette(0xFF1A1A1A.toInt(), 0xFF3A3550.toInt(), 0xFF0E0E14.toInt(), 0xFFF0E0B0.toInt()),
    )

    /**
     * @param centreText the words written in the middle of the drawn artwork ("Sri Krishna" in the chosen script).
     */
    fun render(
        width: Int, height: Int, incident: Incident, painting: Bitmap?, showText: Boolean,
        centreText: String = "श्री कृष्ण",
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val w = width.toFloat()
        val h = height.toFloat()

        // Text sits just above the dock and gesture bar; the picture gets the space above it.
        val maxBottom = h * 0.87f
        val block = if (showText) measureText(incident, w, maxBottom - h * 0.46f) else null
        val textTop = block?.let { maxBottom - it.height } ?: h
        val box = if (block != null) RectF(w * 0.05f, h * 0.07f, w * 0.95f, minOf(h * 0.62f, textTop - h * 0.04f))
        else RectF(w * 0.04f, h * 0.08f, w * 0.96f, h * 0.90f)

        if (painting != null) {
            drawSoftBackground(canvas, painting, w, h)
            drawFramedPainting(canvas, painting, box, w)
        } else {
            drawFallbackArt(canvas, w, h, box, incident, centreText)
        }

        if (block != null) drawStory(canvas, block, textTop, w, h)
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

    private fun drawFallbackArt(canvas: Canvas, w: Float, h: Float, box: RectF, incident: Incident, centreText: String) {
        val pal = PALETTES[incident.kandaKey] ?: PALETTES.getValue("Tenth Skandha")
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(pal.top, pal.mid, pal.bottom),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, bg)

        val cx = box.centerX()
        val cy = box.centerY()
        val r = min(box.width(), box.height()) * 0.46f

        // Warm glow behind the mandala.
        canvas.drawCircle(cx, cy, r * 1.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, r * 1.4f, (pal.glow and 0x00FFFFFF) or (120 shl 24), Color.TRANSPARENT, Shader.TileMode.CLAMP)
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
        // Lotus petals, two layers. The petal count changes from day to day so the art varies.
        val outerPetals = intArrayOf(16, 12, 20, 8)[incident.id % 4]
        for (layer in 0..1) {
            val petals = if (layer == 0) outerPetals else outerPetals / 2
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
        val text = centreText
        val bounds = Rect()
        name.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, cx, cy - bounds.exactCenterY(), name)
    }

    /** The kanda, title and story laid out for a given width, ready to draw. */
    private class TextBlock(
        val kanda: String, val kandaPaint: TextPaint,
        val title: StaticLayout, val story: StaticLayout,
        val gapAfterKanda: Float, val gapAfterTitle: Float,
    ) {
        val height get() = kandaPaint.textSize + gapAfterKanda + title.height + gapAfterTitle + story.height
    }

    /**
     * Lays out the text so it fits in [maxHeight], shrinking the story text (and then the
     * title) when needed. Scripts like Malayalam and Tamil need more room than English.
     */
    private fun measureText(incident: Incident, w: Float, maxHeight: Float): TextBlock {
        val margin = w * 0.07f
        val textWidth = (w - 2 * margin).toInt()
        val latin = incident.story.firstOrNull { it.isLetter() }?.let { it.code < 0x250 } ?: true
        val kandaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SAFFRON
            textSize = w * 0.030f
            letterSpacing = if (latin) 0.14f else 0f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val kanda = if (latin) incident.kanda.uppercase() else incident.kanda
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GOLD
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val storyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PARCHMENT
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        var titleSize = w * 0.062f
        var storySize = w * (if (latin) 0.038f else 0.040f)
        val minStory = w * 0.030f
        while (true) {
            titlePaint.textSize = titleSize
            storyPaint.textSize = storySize
            val block = TextBlock(
                kanda, kandaPaint,
                layout(incident.title, titlePaint, textWidth, 1.05f),
                layout(incident.story, storyPaint, textWidth, if (latin) 1.25f else 1.35f),
                w * 0.02f, w * 0.025f,
            )
            if (block.height <= maxHeight || storySize <= minStory) return block
            storySize *= 0.96f
            titleSize = maxOf(w * 0.050f, titleSize * 0.98f)
        }
    }

    private fun drawStory(canvas: Canvas, block: TextBlock, top: Float, w: Float, h: Float) {
        // Dark band so the text reads on any picture.
        val bandTop = top - h * 0.05f
        canvas.drawRect(0f, bandTop, w, h, Paint().apply {
            shader = LinearGradient(
                0f, bandTop, 0f, h,
                intArrayOf(Color.TRANSPARENT, Color.argb(215, 12, 4, 2), Color.argb(235, 8, 2, 1)),
                floatArrayOf(0f, 0.18f, 1f), Shader.TileMode.CLAMP,
            )
        })
        val margin = w * 0.07f
        var y = top + block.kandaPaint.textSize
        canvas.drawText(block.kanda, margin, y, block.kandaPaint)
        y += block.gapAfterKanda
        canvas.save(); canvas.translate(margin, y); block.title.draw(canvas); canvas.restore()
        y += block.title.height + block.gapAfterTitle
        canvas.save(); canvas.translate(margin, y); block.story.draw(canvas); canvas.restore()
    }

    private fun layout(text: String, paint: TextPaint, width: Int, spacing: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .build()
}
