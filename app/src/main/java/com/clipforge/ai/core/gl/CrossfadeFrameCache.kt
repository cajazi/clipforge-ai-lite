package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log

/**
 * Pre-extracts the crossfade overlap window's frames ONCE, sequentially, into an
 * in-memory list, then serves them by frame index during rendering.
 *
 * Why: MediaMetadataRetriever.getFrameAtTime per rendered frame seek-thrashes (a
 * full seek+decode each call), which made a ~2.8s crossfade take ~2.5 min. By
 * extracting the ~ (durationSec * fps) frames up front and serving cached Bitmaps
 * by index, the render loop does zero seeks.
 *
 * Accuracy: keeps OPTION_CLOSEST during pre-extraction (NOT OPTION_CLOSEST_SYNC,
 * which snaps to keyframes and would blur transition timing).
 *
 * This is the interim fast path. Later this extractor is replaced by a proper
 * MediaCodec streaming decoder for CapCut-level performance.
 *
 * Call release() after export to free the bitmaps.
 *
 * @param clipPath      source clip (clip B) to extract frames from.
 * @param startUs       where in the clip the window begins (clip B's head start).
 * @param windowUs      length of the crossfade window in microseconds.
 * @param fps           sampling rate; render is ~30fps so 30 aligns the cache.
 * @param maxDimension  longest-edge cap for stored bitmaps to bound memory.
 */
class CrossfadeFrameCache(
    private val clipPath: String,
    private val startUs: Long,
    private val windowUs: Long,
    private val fps: Int = 30,
    private val maxDimension: Int = 1280
) {
    private val tag = "CROSSFADE_CACHE"
    private val frames = ArrayList<Bitmap>()
    private val frameDurationUs: Long = (1_000_000L / fps)
    @Volatile private var built = false

    /** Number of frames to extract to cover the window (with one frame of padding). */
    private val frameCount: Int =
        ((windowUs + frameDurationUs - 1) / frameDurationUs).toInt() + 1

    /** Extract all window frames sequentially. Call once, off the main thread. */
    fun build() {
        if (built) return
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(clipPath)
            var extracted = 0
            for (i in 0 until frameCount) {
                val srcUs = startUs + i * frameDurationUs
                val raw = mmr.getFrameAtTime(srcUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (raw != null) {
                    frames.add(scaleIfNeeded(raw))
                    extracted++
                } else if (frames.isNotEmpty()) {
                    // Past end of clip / decode miss: repeat last good frame.
                    frames.add(frames.last())
                }
            }
            built = true
            Log.d(tag, "built $extracted/$frameCount frames from ${clipPath.substringAfterLast('/')} window=${windowUs}us fps=$fps")
        } catch (e: Exception) {
            Log.e(tag, "build failed: ${e.message}", e)
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    /**
     * Return the frame for a given offset into the window (0 .. windowUs).
     * Index = intoWindowUs / frameDurationUs, clamped to the cached range.
     */
    fun frameForOffset(intoWindowUs: Long): Bitmap? {
        if (frames.isEmpty()) return null
        val idx = (intoWindowUs / frameDurationUs).toInt().coerceIn(0, frames.size - 1)
        return frames[idx]
    }

    fun isEmpty(): Boolean = frames.isEmpty()

    private fun scaleIfNeeded(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val longEdge = maxOf(w, h)
        if (longEdge <= maxDimension) return bmp
        val ratio = maxDimension.toFloat() / longEdge
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true)
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    fun release() {
        for (b in frames) {
            if (!b.isRecycled) b.recycle()
        }
        frames.clear()
        built = false
    }
}
