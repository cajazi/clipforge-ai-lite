package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pre-extracts the crossfade overlap window's frames ONCE into an in-memory list,
 * then serves them by frame index during rendering (render loop does zero seeks).
 *
 * PRIMARY path: MediaCodec streaming decoder -> YUV_420_888 ImageReader. Images are
 * captured ASYNCHRONOUSLY via OnImageAvailableListener on a dedicated HandlerThread
 * (acquireNextImage), converted YUV->RGB, paired with PTS, and sorted at the end.
 *
 * Why YUV_420_888 (not RGBA_8888): hardware video decoders deliver YUV from the
 * decoder surface; an RGBA_8888 ImageReader received 0 images on the SM-A165F. YUV
 * is the format decoders actually produce.
 *
 * FALLBACK: MediaMetadataRetriever (slow but reliable), only if MediaCodec fails.
 *
 * Public interface (unchanged): build(), frameForOffset(Long), isEmpty(), release().
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

    private val frameCount: Int =
        ((windowUs + frameDurationUs - 1) / frameDurationUs).toInt() + 1
    private val endUs: Long = startUs + windowUs

    fun build() {
        if (built) return
        val t0 = System.currentTimeMillis()
        Log.d(tag, "decoder start clip=${clipPath.substringAfterLast('/')} startUs=$startUs windowUs=$windowUs")

        val ok = try {
            buildWithMediaCodec()
        } catch (e: Throwable) {
            Log.e(tag, "MediaCodec path threw: ${e.message}", e)
            false
        }

        if (!ok || frames.isEmpty()) {
            releaseFramesOnly()
            Log.d(tag, "FALLBACK -> MediaMetadataRetriever")
            buildWithRetriever()
            Log.d(tag, "built ${frames.size} frames (fallback=YES) transitionUs=$windowUs extractionMs=${System.currentTimeMillis() - t0}")
        } else {
            Log.d(tag, "built ${frames.size} frames (fallback=NO) transitionUs=$windowUs extractionMs=${System.currentTimeMillis() - t0}")
        }
        built = true
    }

    // ---------------- PRIMARY: MediaCodec + YUV_420_888 ImageReader ----------------

    private fun buildWithMediaCodec(): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var imageReader: ImageReader? = null
        val readerThread = HandlerThread("xfade-imgreader").apply { start() }
        val readerHandler = Handler(readerThread.looper)

        val collected = java.util.Collections.synchronizedList(ArrayList<Pair<Long, Bitmap>>())
        val imagesReceived = AtomicInteger(0)

        try {
            extractor.setDataSource(clipPath)
            val trackIndex = selectVideoTrack(extractor)
            if (trackIndex < 0) { Log.e(tag, "no video track"); return false }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val srcW = format.getInteger(MediaFormat.KEY_WIDTH)
            val srcH = format.getInteger(MediaFormat.KEY_HEIGHT)
            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
            val (outW, outH) = scaledSize(srcW, srcH)

            imageReader = ImageReader.newInstance(srcW, srcH, ImageFormat.YUV_420_888, (frameCount + 8).coerceAtMost(64))
            imageReader.setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireNextImage() } catch (e: Exception) { null }
                if (image != null) {
                    try {
                        imagesReceived.incrementAndGet()
                        val ptsUs = image.timestamp / 1000L // ns -> us
                        if (ptsUs in startUs..endUs) {
                            val bmp = yuvImageToBitmap(image, outW, outH, rotation)
                            if (bmp != null) collected.add(ptsUs to bmp)
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "listener convert failed: ${e.message}")
                    } finally {
                        image.close()
                    }
                }
            }, readerHandler)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, imageReader.surface, null, 0)
            codec.start()

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var renderedCount = 0
            val deadlineMs = System.currentTimeMillis() + 15_000

            while (!sawOutputEOS) {
                if (System.currentTimeMillis() > deadlineMs) {
                    Log.e(tag, "MediaCodec deadline exceeded -> abort to fallback")
                    return false
                }

                if (!sawInputEOS) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex >= 0) {
                    val ptsUs = bufferInfo.presentationTimeUs
                    val render = ptsUs in (startUs - frameDurationUs)..(endUs + frameDurationUs)
                    codec.releaseOutputBuffer(outIndex, render)
                    if (render) renderedCount++
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) sawOutputEOS = true
                    if (ptsUs > endUs + frameDurationUs) sawOutputEOS = true
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(tag, "output format: ${codec.outputFormat}")
                }
            }

            // Drain: wait for the listener to deliver up to renderedCount images.
            val drainDeadline = System.currentTimeMillis() + 10_000
            while (imagesReceived.get() < renderedCount && System.currentTimeMillis() < drainDeadline) {
                Thread.sleep(10)
            }

            Log.d(tag, "decoded outputBuffers(rendered)=$renderedCount imagesReceived=${imagesReceived.get()} (src ${srcW}x$srcH rot=$rotation -> ${outW}x$outH)")

            val ordered = synchronized(collected) { collected.sortedBy { it.first } }
            for (p in ordered) frames.add(p.second)

            Log.d(tag, "frames kept=${frames.size}")
            return frames.isNotEmpty()
        } catch (e: Exception) {
            Log.e(tag, "MediaCodec decode failed: ${e.message}", e)
            return false
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { imageReader?.close() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { readerThread.quitSafely() } catch (_: Exception) {}
            if (frames.isEmpty()) {
                synchronized(collected) { for (p in collected) if (!p.second.isRecycled) p.second.recycle() }
            }
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return i
        }
        return -1
    }

    /**
     * Convert a YUV_420_888 Image to an ARGB Bitmap, then rotate + scale.
     * Handles arbitrary rowStride/pixelStride per plane (works for planar I420 and
     * semi-planar NV12/NV21, where U/V pixelStride is typically 2 and interleaved).
     */
    private fun yuvImageToBitmap(image: Image, outW: Int, outH: Int, rotation: Int): Bitmap? {
        return try {
            val w = image.width
            val h = image.height
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yBuf: ByteBuffer = yPlane.buffer
            val uBuf: ByteBuffer = uPlane.buffer
            val vBuf: ByteBuffer = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val yPixStride = yPlane.pixelStride
            val uRowStride = uPlane.rowStride
            val uPixStride = uPlane.pixelStride
            val vRowStride = vPlane.rowStride
            val vPixStride = vPlane.pixelStride

            val argb = IntArray(w * h)

            for (y in 0 until h) {
                val uvRow = (y shr 1)
                val yRowBase = y * yRowStride
                val uRowBase = uvRow * uRowStride
                val vRowBase = uvRow * vRowStride
                for (x in 0 until w) {
                    val uvCol = (x shr 1)
                    val yIdx = yRowBase + x * yPixStride
                    val uIdx = uRowBase + uvCol * uPixStride
                    val vIdx = vRowBase + uvCol * vPixStride

                    val Y = (yBuf.get(yIdx).toInt() and 0xFF)
                    val U = (uBuf.get(uIdx).toInt() and 0xFF) - 128
                    val V = (vBuf.get(vIdx).toInt() and 0xFF) - 128

                    // BT.601 YUV -> RGB
                    var r = (Y + 1.402f * V).toInt()
                    var g = (Y - 0.344136f * U - 0.714136f * V).toInt()
                    var b = (Y + 1.772f * U).toInt()
                    r = if (r < 0) 0 else if (r > 255) 255 else r
                    g = if (g < 0) 0 else if (g > 255) 255 else g
                    b = if (b < 0) 0 else if (b > 255) 255 else b

                    argb[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            val base = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888)

            val rotated = if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                val r = Bitmap.createBitmap(base, 0, 0, base.width, base.height, m, true)
                if (r !== base) base.recycle()
                r
            } else base

            val scaled = if (rotated.width == outW && rotated.height == outH) rotated
            else {
                val s = Bitmap.createScaledBitmap(rotated, outW, outH, true)
                if (s !== rotated) rotated.recycle()
                s
            }
            scaled
        } catch (e: Exception) {
            Log.e(tag, "yuvImageToBitmap failed: ${e.message}")
            null
        }
    }

    private fun scaledSize(w: Int, h: Int): Pair<Int, Int> {
        val longEdge = maxOf(w, h)
        if (longEdge <= maxDimension) return w to h
        val ratio = maxDimension.toFloat() / longEdge
        return (w * ratio).toInt().coerceAtLeast(1) to (h * ratio).toInt().coerceAtLeast(1)
    }

    // ---------------- FALLBACK: MediaMetadataRetriever ----------------

    private fun buildWithRetriever() {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(clipPath)
            for (i in 0 until frameCount) {
                val srcUs = startUs + i * frameDurationUs
                val raw = mmr.getFrameAtTime(srcUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (raw != null) frames.add(scaleIfNeeded(raw))
                else if (frames.isNotEmpty()) frames.add(frames.last())
            }
        } catch (e: Exception) {
            Log.e(tag, "retriever fallback failed: ${e.message}", e)
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    private fun scaleIfNeeded(bmp: Bitmap): Bitmap {
        val longEdge = maxOf(bmp.width, bmp.height)
        if (longEdge <= maxDimension) return bmp
        val ratio = maxDimension.toFloat() / longEdge
        val nw = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val nh = (bmp.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true)
        if (scaled !== bmp) bmp.recycle()
        return scaled
    }

    // ---------------- serving / lifecycle (unchanged interface) ----------------

    fun frameForOffset(intoWindowUs: Long): Bitmap? {
        if (frames.isEmpty()) return null
        val idx = (intoWindowUs / frameDurationUs).toInt().coerceIn(0, frames.size - 1)
        return frames[idx]
    }

    fun isEmpty(): Boolean = frames.isEmpty()

    private fun releaseFramesOnly() {
        for (b in frames) if (!b.isRecycled) b.recycle()
        frames.clear()
    }

    fun release() {
        releaseFramesOnly()
        built = false
    }
}
