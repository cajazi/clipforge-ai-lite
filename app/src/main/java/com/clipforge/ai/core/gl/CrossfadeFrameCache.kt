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
    private val maxDimension: Int = 1280,
    private val minCoveragePercent: Float = 70f,
    private val fallbackCoveragePercent: Float = minCoveragePercent,
    private val maxEstimatedBytes: Long = Long.MAX_VALUE
) {
    private val tag = "CROSSFADE_CACHE"
    private data class CachedFrame(val ptsUs: Long, val bitmap: Bitmap, val source: String)

    data class FrameHit(
        val bitmap: Bitmap,
        val index: Int,
        val ptsUs: Long,
        val requestedUs: Long,
        val deltaUs: Long
    )

    data class CacheStats(
        val requestedFrames: Int,
        val keptFrames: Int,
        val coveragePercent: Float,
        val minSpacingUs: Long,
        val avgSpacingUs: Long,
        val maxSpacingUs: Long,
        val estimatedBytes: Long,
        val targetCoveragePercent: Float,
        val finalMode: String,
        val prepMs: Long
    )

    private val frames = ArrayList<CachedFrame>()
    private val frameDurationUs: Long = (1_000_000L / fps)
    @Volatile private var built = false
    @Volatile private var finalMode = "NOT_BUILT"
    @Volatile private var prepMs = 0L

    private val frameCount: Int =
        ((windowUs + frameDurationUs - 1) / frameDurationUs).toInt() + 1
    private val endUs: Long = startUs + windowUs

    fun build() {
        if (built) {
            Log.d(tag, "build skipped: already built clip=${clipPath.substringAfterLast('/')} frames=${frames.size}")
            return
        }
        val t0 = System.currentTimeMillis()
        Log.d(
            tag,
            "BUILD_START clip=${clipPath.substringAfterLast('/')} path=$clipPath startUs=$startUs " +
                "endUs=$endUs windowUs=$windowUs fps=$fps frameCount=$frameCount maxDimension=$maxDimension"
        )

        val ok = try {
            Log.d(tag, "MEDIACODEC_BUILD_BEFORE clip=${clipPath.substringAfterLast('/')}")
            buildWithMediaCodec()
        } catch (e: Throwable) {
            Log.e(tag, "MEDIACODEC_BUILD_THROW clip=$clipPath message=${e.message}", e)
            false
        }
        Log.d(tag, "MEDIACODEC_BUILD_AFTER ok=$ok frames=${frames.size} clip=${clipPath.substringAfterLast('/')}")

        val mediaCodecCoverage = coveragePercent()
        if (!ok || frames.isEmpty() || mediaCodecCoverage < minCoveragePercent) {
            if (ok && frames.isNotEmpty()) {
                Log.d(
                    tag,
                    "MEDIACODEC_COVERAGE_LOW coverage=${"%.1f".format(mediaCodecCoverage)}% " +
                        "frames=${frames.size}/$frameCount minRequired=${"%.1f".format(minCoveragePercent)}% -> bounded retriever fill"
                )
            }
            releaseFramesOnly()
            Log.d(
                tag,
                "BOUNDED_RETRIEVER_BUILD_BEFORE clip=${clipPath.substringAfterLast('/')} " +
                    "fallbackCoverage=${"%.1f".format(fallbackCoveragePercent)}% maxEstimatedBytes=$maxEstimatedBytes"
            )
            try {
                buildWithRetrieverBounded()
            } catch (e: Throwable) {
                Log.e(tag, "RETRIEVER_BUILD_THROW clip=$clipPath message=${e.message}", e)
            }
            finalMode = if (frames.size >= frameCount) "RETRIEVER_DENSITY_FILL" else "BOUNDED_RETRIEVER_FILL"
            Log.d(tag, "BOUNDED_RETRIEVER_BUILD_AFTER frames=${frames.size} transitionUs=$windowUs extractionMs=${System.currentTimeMillis() - t0}")
        } else {
            finalMode = "MEDIACODEC_ONLY"
            Log.d(tag, "BUILD_DONE_MEDIACODEC frames=${frames.size} transitionUs=$windowUs extractionMs=${System.currentTimeMillis() - t0}")
        }
        built = true
        prepMs = System.currentTimeMillis() - t0
        logStats("BUILD_DONE")
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
            val convertedFrames = AtomicInteger(0)

        try {
            Log.d(tag, "extractor.setDataSource BEFORE path=$clipPath")
            extractor.setDataSource(clipPath)
            Log.d(tag, "extractor.setDataSource AFTER path=$clipPath")
            val trackIndex = selectVideoTrack(extractor)
            if (trackIndex < 0) { Log.e(tag, "no video track"); return false }
            Log.d(tag, "selectTrack BEFORE trackIndex=$trackIndex")
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val srcW = format.getInteger(MediaFormat.KEY_WIDTH)
            val srcH = format.getInteger(MediaFormat.KEY_HEIGHT)
            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
            val normalizedRotation = ((rotation % 360) + 360) % 360
            val displayW = if (normalizedRotation == 90 || normalizedRotation == 270) srcH else srcW
            val displayH = if (normalizedRotation == 90 || normalizedRotation == 270) srcW else srcH
            val (outW, outH) = scaledSize(displayW, displayH)
            Log.d(tag, "track format mime=$mime src=${srcW}x$srcH display=${displayW}x$displayH rotation=$rotation scaled=${outW}x$outH frameCount=$frameCount")

            val maxImages = 4
            Log.d(tag, "ImageReader.newInstance BEFORE ${srcW}x$srcH maxImages=$maxImages")
            imageReader = ImageReader.newInstance(srcW, srcH, ImageFormat.YUV_420_888, maxImages)
            Log.d(tag, "ImageReader.newInstance AFTER")
            imageReader.setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireNextImage() } catch (e: Exception) { null }
                if (image != null) {
                    try {
                        imagesReceived.incrementAndGet()
                        val ptsUs = image.timestamp / 1000L // ns -> us
                        if (ptsUs in startUs..endUs) {
                            val bmp = yuvImageToBitmap(image, outW, outH, normalizedRotation)
                            if (bmp != null) {
                                collected.add(ptsUs to bmp)
                                convertedFrames.incrementAndGet()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "listener convert failed: ${e.message}", e)
                    } finally {
                        image.close()
                    }
                }
            }, readerHandler)

            Log.d(tag, "MediaCodec.createDecoderByType BEFORE mime=$mime")
            codec = MediaCodec.createDecoderByType(mime)
            Log.d(tag, "MediaCodec.createDecoderByType AFTER codec=${codec.name}")
            Log.d(tag, "codec.configure BEFORE surface=${imageReader.surface}")
            codec.configure(format, imageReader.surface, null, 0)
            Log.d(tag, "codec.configure AFTER")
            Log.d(tag, "codec.start BEFORE")
            codec.start()
            Log.d(tag, "codec.start AFTER")

            Log.d(tag, "extractor.seekTo BEFORE startUs=$startUs")
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            Log.d(tag, "extractor.seekTo AFTER sampleTime=${extractor.sampleTime}")

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var renderedCount = 0
            val deadlineMs = System.currentTimeMillis() + 15_000

            while (!sawOutputEOS) {
                if (System.currentTimeMillis() > deadlineMs) {
                    Log.e(tag, "MediaCodec deadline exceeded -> abort to fallback rendered=$renderedCount images=${imagesReceived.get()} sawInputEOS=$sawInputEOS")
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
                            if (pts > endUs + frameDurationUs) {
                                codec.queueInputBuffer(inIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                                Log.d(tag, "input EOS queued after target window pts=$pts endUs=$endUs")
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
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

            val codecElapsedMs = (15_000 - (deadlineMs - System.currentTimeMillis())).coerceAtLeast(0L)
            Log.d(
                tag,
                "decoded outputBuffers(rendered)=$renderedCount imagesReceived=${imagesReceived.get()} " +
                    "converted=${convertedFrames.get()} codecLoopMs=$codecElapsedMs " +
                    "(src ${srcW}x$srcH rot=$rotation -> ${outW}x$outH)"
            )

            val ordered = synchronized(collected) { collected.sortedBy { it.first } }
            for (p in ordered) frames.add(CachedFrame(p.first, p.second, "codec"))

            Log.d(tag, "frames kept=${frames.size}")
            return frames.isNotEmpty()
        } catch (e: Exception) {
            Log.e(tag, "MediaCodec decode failed: ${e.message}", e)
            return false
        } finally {
            try { codec?.stop() } catch (t: Throwable) { Log.e(tag, "codec.stop failed", t) }
            try { codec?.release() } catch (t: Throwable) { Log.e(tag, "codec.release failed", t) }
            try { imageReader?.close() } catch (t: Throwable) { Log.e(tag, "imageReader.close failed", t) }
            try { extractor.release() } catch (t: Throwable) { Log.e(tag, "extractor.release failed", t) }
            try { readerThread.quitSafely() } catch (t: Throwable) { Log.e(tag, "readerThread.quitSafely failed", t) }
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

            val rotW = if (rotation == 90 || rotation == 270) h else w
            val rotH = if (rotation == 90 || rotation == 270) w else h
            val argb = IntArray(outW * outH)

            fun pixelAt(srcXRaw: Int, srcYRaw: Int): Int {
                val srcX = srcXRaw.coerceIn(0, w - 1)
                val srcY = srcYRaw.coerceIn(0, h - 1)
                val uvRow = (srcY shr 1)
                val yIdx = srcY * yRowStride + srcX * yPixStride
                val uvCol = (srcX shr 1)
                val uIdx = uvRow * uRowStride + uvCol * uPixStride
                val vIdx = uvRow * vRowStride + uvCol * vPixStride

                val yValue = (yBuf.get(yIdx).toInt() and 0xFF)
                val uValue = (uBuf.get(uIdx).toInt() and 0xFF) - 128
                val vValue = (vBuf.get(vIdx).toInt() and 0xFF) - 128

                var r = (yValue + 1.402f * vValue).toInt()
                var g = (yValue - 0.344136f * uValue - 0.714136f * vValue).toInt()
                var b = (yValue + 1.772f * uValue).toInt()
                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)
                return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }

            for (dy in 0 until outH) {
                val ry = (dy.toLong() * rotH / outH).toInt()
                for (dx in 0 until outW) {
                    val rx = (dx.toLong() * rotW / outW).toInt()
                    val (srcX, srcY) = when (rotation) {
                        90 -> ry to (h - 1 - rx)
                        180 -> (w - 1 - rx) to (h - 1 - ry)
                        270 -> (w - 1 - ry) to rx
                        else -> rx to ry
                    }
                    argb[dy * outW + dx] = pixelAt(srcX, srcY)
                }
            }

            Bitmap.createBitmap(argb, outW, outH, Bitmap.Config.ARGB_8888)
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
            Log.d(tag, "retriever.setDataSource BEFORE path=$clipPath")
            mmr.setDataSource(clipPath)
            Log.d(tag, "retriever.setDataSource AFTER")
            for (i in 0 until frameCount) {
                val srcUs = startUs + i * frameDurationUs
                val raw = mmr.getFrameAtTime(srcUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (raw != null) {
                    frames.add(CachedFrame(srcUs, scaleIfNeeded(raw), "retriever"))
                } else if (frames.isNotEmpty()) {
                    frames.add(CachedFrame(srcUs, frames.last().bitmap, "retriever-repeat"))
                }
                if (i < 5 || i == frameCount - 1) {
                    Log.d(tag, "retriever frame index=$i srcUs=$srcUs hit=${raw != null} frames=${frames.size}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "retriever fallback failed: ${e.message}", e)
        } finally {
            try { mmr.release() } catch (t: Throwable) { Log.e(tag, "retriever.release failed", t) }
        }
    }

    private fun buildWithRetrieverBounded() {
        val mmr = MediaMetadataRetriever()
        try {
            Log.d(tag, "retriever.setDataSource BEFORE path=$clipPath")
            mmr.setDataSource(clipPath)
            Log.d(tag, "retriever.setDataSource AFTER")

            val selectedSlots = selectBoundedRetrieverSlots(mmr)
            Log.d(
                tag,
                "bounded retriever selectedSlots=${selectedSlots.size}/$frameCount " +
                    "slots=${selectedSlots.take(12)}${if (selectedSlots.size > 12) "..." else ""}"
            )
            for ((ordinal, slot) in selectedSlots.withIndex()) {
                val srcUs = startUs + slot * frameDurationUs
                val raw = mmr.getFrameAtTime(srcUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (raw != null) {
                    frames.add(CachedFrame(srcUs, scaleIfNeeded(raw), "retriever-bounded"))
                } else if (frames.isNotEmpty()) {
                    frames.add(CachedFrame(srcUs, frames.last().bitmap, "retriever-bounded-repeat"))
                }
                if (ordinal < 5 || ordinal == selectedSlots.lastIndex) {
                    Log.d(tag, "bounded retriever frame ordinal=$ordinal slot=$slot srcUs=$srcUs hit=${raw != null} frames=${frames.size}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "bounded retriever fallback failed: ${e.message}", e)
        } finally {
            try { mmr.release() } catch (t: Throwable) { Log.e(tag, "retriever.release failed", t) }
        }
    }

    private fun selectBoundedRetrieverSlots(mmr: MediaMetadataRetriever): List<Int> {
        val targetCoverage = fallbackCoveragePercent.coerceIn(50f, minCoveragePercent)
        val minSlotsForCoverage = kotlin.math.ceil(frameCount * targetCoverage / 200f).toInt()
            .coerceAtLeast(2)
            .coerceAtMost(frameCount)
        val maxSlotGapForDelta = ((MAX_FRAME_DELTA_US * 2L) / frameDurationUs)
            .toInt()
            .coerceAtLeast(1)
        val minSlotsForDelta = (kotlin.math.ceil((frameCount - 1).toDouble() / maxSlotGapForDelta.toDouble()).toInt() + 1)
            .coerceAtLeast(2)
            .coerceAtMost(frameCount)
        val firstFrameBytes = estimateRetrieverFrameBytes(mmr)
        val memoryCappedSlots = if (maxEstimatedBytes == Long.MAX_VALUE || firstFrameBytes <= 0L) {
            frameCount
        } else {
            (maxEstimatedBytes / firstFrameBytes).toInt().coerceAtLeast(2)
        }.coerceAtMost(frameCount)
        val qualityMinimum = maxOf(minSlotsForCoverage, minSlotsForDelta)
        val count = qualityMinimum.coerceAtMost(memoryCappedSlots).coerceAtLeast(2)
        Log.d(
            tag,
            "bounded retriever sizing targetCoverage=${"%.1f".format(targetCoverage)}% " +
                "minSlotsForCoverage=$minSlotsForCoverage minSlotsForDelta=$minSlotsForDelta " +
                "maxSlotGapForDelta=$maxSlotGapForDelta firstFrameBytes=$firstFrameBytes " +
                "memoryCappedSlots=$memoryCappedSlots selectedCount=$count"
        )
        return evenlySpacedSlots(frameCount, count)
    }

    private fun estimateRetrieverFrameBytes(mmr: MediaMetadataRetriever): Long {
        val raw = try { mmr.getFrameAtTime(startUs, MediaMetadataRetriever.OPTION_CLOSEST) } catch (_: Throwable) { null }
        if (raw == null) return 0L
        val scaled = scaleIfNeeded(raw)
        val bytes = scaled.allocationByteCount.toLong()
        if (!scaled.isRecycled) scaled.recycle()
        return bytes
    }

    private fun evenlySpacedSlots(totalSlots: Int, selectedCount: Int): List<Int> {
        if (selectedCount >= totalSlots) return (0 until totalSlots).toList()
        if (selectedCount <= 1) return listOf(0)
        val slots = linkedSetOf<Int>()
        for (i in 0 until selectedCount) {
            val slot = ((i.toDouble() * (totalSlots - 1).toDouble()) / (selectedCount - 1).toDouble())
                .toInt()
                .coerceIn(0, totalSlots - 1)
            slots.add(slot)
        }
        var cursor = 0
        while (slots.size < selectedCount && cursor < totalSlots) {
            slots.add(cursor++)
        }
        return slots.sorted()
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
        return frameInfoForOffset(intoWindowUs)?.bitmap
    }

    fun frameInfoForOffset(intoWindowUs: Long): FrameHit? {
        if (frames.isEmpty()) return null
        val requestedUs = (startUs + intoWindowUs).coerceIn(startUs, endUs)
        var bestIndex = 0
        var bestDelta = Long.MAX_VALUE
        frames.forEachIndexed { index, frame ->
            val delta = kotlin.math.abs(frame.ptsUs - requestedUs)
            if (delta < bestDelta) {
                bestDelta = delta
                bestIndex = index
            }
        }
        val frame = frames[bestIndex]
        return FrameHit(
            bitmap = frame.bitmap,
            index = bestIndex,
            ptsUs = frame.ptsUs,
            requestedUs = requestedUs,
            deltaUs = bestDelta
        )
    }

    fun isEmpty(): Boolean = frames.isEmpty()

    fun stats(): CacheStats {
        val sortedPts = frames.map { it.ptsUs }.sorted()
        val spacings = sortedPts.zipWithNext { a, b -> (b - a).coerceAtLeast(0L) }
        val estimatedBytes = frames.distinctBy { System.identityHashCode(it.bitmap) }.sumOf { frame ->
            frame.bitmap.allocationByteCount.toLong()
        }
        return CacheStats(
            requestedFrames = frameCount,
            keptFrames = frames.size,
            coveragePercent = coveragePercent(),
            minSpacingUs = spacings.minOrNull() ?: 0L,
            avgSpacingUs = spacings.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L,
            maxSpacingUs = spacings.maxOrNull() ?: 0L,
            estimatedBytes = estimatedBytes,
            targetCoveragePercent = if (finalMode == "MEDIACODEC_ONLY") minCoveragePercent else fallbackCoveragePercent,
            finalMode = finalMode,
            prepMs = prepMs
        )
    }

    fun logStats(label: String) {
        val s = stats()
        Log.d(
            tag,
            "$label built=$built empty=${frames.isEmpty()} clip=${clipPath.substringAfterLast('/')} " +
                "requested=${s.requestedFrames} kept=${s.keptFrames} coverage=${"%.1f".format(s.coveragePercent)}% " +
                "spacingUs[min=${s.minSpacingUs},avg=${s.avgSpacingUs},max=${s.maxSpacingUs}] " +
                "estimatedBytes=${s.estimatedBytes} fps=$fps frameDurationUs=$frameDurationUs " +
                "maxDimension=$maxDimension minCoverage=${"%.1f".format(minCoveragePercent)}% " +
                "fallbackCoverage=${"%.1f".format(fallbackCoveragePercent)}% " +
                "targetCoverage=${"%.1f".format(s.targetCoveragePercent)}% finalMode=${s.finalMode} prepMs=${s.prepMs}"
        )
    }

    private fun releaseFramesOnly() {
        val uniqueBitmaps = frames.map { it.bitmap }.distinctBy { System.identityHashCode(it) }
        for (b in uniqueBitmaps) if (!b.isRecycled) b.recycle()
        frames.clear()
    }

    fun release() {
        Log.d(tag, "release frames=${frames.size} clip=${clipPath.substringAfterLast('/')}")
        releaseFramesOnly()
        built = false
    }

    private fun coveragePercent(): Float {
        if (frames.isEmpty() || frameCount <= 0) return 0f
        val coveredSlots = (0 until frameCount).count { i ->
            val requestedUs = startUs + i * frameDurationUs
            frames.minOf { kotlin.math.abs(it.ptsUs - requestedUs) } <= frameDurationUs
        }
        return coveredSlots * 100f / frameCount.toFloat()
    }

    private companion object {
        const val MAX_FRAME_DELTA_US = 50_000L
    }
}
