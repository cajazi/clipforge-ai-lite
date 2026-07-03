package com.clipforge.ai.core.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

interface TextOverlayRasterizer {
    fun rasterize(
        spec: TextRenderSpec,
        frameW: Int,
        frameH: Int
    ): Bitmap
}

class CanvasTextOverlayRasterizer(
    cacheByteTarget: Int = DEFAULT_CACHE_BYTES
) : TextOverlayRasterizer {

    internal var cacheHitCount: Int = 0
        private set

    internal var cacheMissCount: Int = 0
        private set

    private val cache = object : LruCache<RasterCacheKey, Bitmap>(cacheByteTarget) {
        override fun sizeOf(key: RasterCacheKey, value: Bitmap): Int = value.allocationByteCount
    }

    override fun rasterize(spec: TextRenderSpec, frameW: Int, frameH: Int): Bitmap {
        require(frameW > 0) { "frameW must be positive" }
        require(frameH > 0) { "frameH must be positive" }

        val key = RasterCacheKey.from(spec, frameW, frameH)
        synchronized(cache) {
            cache.get(key)?.let { cached ->
                cacheHitCount += 1
                return cached
            }
            cacheMissCount += 1
        }

        val rendered = render(spec, frameW, frameH)
        synchronized(cache) {
            cache.put(key, rendered)
        }
        return rendered
    }

    private fun render(spec: TextRenderSpec, frameW: Int, frameH: Int): Bitmap {
        val fontSizePx = (spec.fontSizeNorm * frameH).coerceAtLeast(MIN_FONT_SIZE_PX)
        val paddingPx = max(MIN_PADDING_PX, ceil(fontSizePx * PADDING_TO_TEXT_RATIO).roundToInt())
        val maxContentWidth = max(1, frameW - paddingPx * 2)

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = spec.colorArgb
            textSize = fontSizePx
            typeface = BundledFontResolver.resolve(spec.fontId, spec.bold, spec.italic)
        }

        val contentWidth = desiredContentWidth(spec.text, paint, maxContentWidth)
        val layout = buildLayout(spec.text, paint, contentWidth, spec.alignment)
        val contentHeight = max(1, layout.height)
        val bitmapW = contentWidth + paddingPx * 2
        val bitmapH = contentHeight + paddingPx * 2
        val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        spec.bgColorArgb?.let { bgColor ->
            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bgColor
                style = Paint.Style.FILL
            }
            val rect = RectF(0f, 0f, bitmapW.toFloat(), bitmapH.toFloat())
            val radius = bitmapH / 2f
            canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
        }

        canvas.save()
        canvas.translate(paddingPx.toFloat(), paddingPx.toFloat())
        layout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    private fun desiredContentWidth(text: String, paint: TextPaint, maxContentWidth: Int): Int {
        if (text.isEmpty()) return 1
        val desired = ceil(Layout.getDesiredWidth(text, paint).toDouble()).toInt().coerceAtLeast(1)
        return desired.coerceAtMost(maxContentWidth)
    }

    private fun buildLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        alignment: TextAlignment
    ): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignment.toLayoutAlignment())
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()

    private fun TextAlignment.toLayoutAlignment(): Layout.Alignment =
        when (this) {
            TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }

    private data class RasterCacheKey(
        val text: String,
        val fontId: String,
        val fontSizeNorm: Float,
        val colorArgb: Int,
        val bgColorArgb: Int?,
        val bold: Boolean,
        val italic: Boolean,
        val alignment: TextAlignment,
        val highlightRange: IntRange?,
        val frameW: Int,
        val frameH: Int
    ) {
        companion object {
            fun from(spec: TextRenderSpec, frameW: Int, frameH: Int): RasterCacheKey =
                RasterCacheKey(
                    text = spec.text,
                    fontId = spec.fontId,
                    fontSizeNorm = spec.fontSizeNorm,
                    colorArgb = spec.colorArgb,
                    bgColorArgb = spec.bgColorArgb,
                    bold = spec.bold,
                    italic = spec.italic,
                    alignment = spec.alignment,
                    highlightRange = spec.highlightRange,
                    frameW = frameW,
                    frameH = frameH
                )
        }
    }

    private object BundledFontResolver {
        fun resolve(fontId: String, bold: Boolean, italic: Boolean): Typeface {
            val style = when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            return Typeface.create(Typeface.DEFAULT, style)
        }
    }

    companion object {
        const val DEFAULT_CACHE_BYTES: Int = 8 * 1024 * 1024
        private const val MIN_FONT_SIZE_PX = 1f
        private const val MIN_PADDING_PX = 4
        private const val PADDING_TO_TEXT_RATIO = 0.35f
    }
}
