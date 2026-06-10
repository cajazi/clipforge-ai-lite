package com.clipforge.ai.core.gl

import android.content.Context
import android.os.Build
import android.util.Log
import com.clipforge.ai.data.local.database.ClipForgeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds an ordered render plan for a project's timeline, deciding where real
 * crossfade segments go vs. plain clips.
 *
 * HONEST RULE (no fake feature labels): only transition families with an exported
 * implementation are planned as real effects. Unsupported families fall back to a
 * PLAIN CUT - they are NOT dressed up as a dissolve.
 *
 * This stage LOGS the plan only - it does not render. Verify the plan matches the
 * timeline, then the executor builds on it.
 */
object CrossfadeRenderPlan {

    private const val TAG = "CROSSFADE_PLAN"
    private const val MIN_RENDER_SLICE_MS = 100L

    // transitionType strings that we can actually render today.
    private val REAL_CROSSFADE_TYPES = setOf("DISSOLVE", "CROSS_DISSOLVE")
    // Fade-through-a-color transitions (FADE + FADE_BLACK -> black, FADE_WHITE -> white).
    private val FADE_BLACK_TYPES = setOf("FADE", "FADE_BLACK")
    private val FADE_WHITE_TYPES = setOf("FADE_WHITE")
    // Slide transitions: clip B slides in over static clip A (direction = B motion).
    private val SLIDE_TYPES = setOf("SLIDE_LEFT", "SLIDE_RIGHT", "SLIDE_UP", "SLIDE_DOWN")
    // Push transitions: A moves out while B slides in (direction = B motion).
    private val PUSH_TYPES = setOf("PUSH_LEFT", "PUSH_RIGHT", "PUSH_UP", "PUSH_DOWN")
    // Zoom transitions: clip B zooms into place over static clip A.
    private val ZOOM_TYPES = setOf("ZOOM_IN", "ZOOM_OUT")
    // Phase 0 experimental motion transition: B whips in over a blurred A tail.
    private val WHIP_PAN_TYPES = setOf("WHIP_PAN_LEFT", "WHIP_PAN_RIGHT", "WHIP_PAN_UP", "WHIP_PAN_DOWN")
    // Motion blur transitions: B dissolves in while A's tail is directionally blurred.
    private val MOTION_BLUR_TYPES = setOf("MOTION_BLUR_LEFT", "MOTION_BLUR_RIGHT", "MOTION_BLUR_UP", "MOTION_BLUR_DOWN")

    data class ClipInfo(
        val path: String,
        val sourceDurationMs: Long,   // full source duration (from trimStart/trimEnd or asset)
        val trimStartMs: Long,        // existing trim from the timeline item
        val trimEndMs: Long
    )

    /** One render op the executor will produce. */
    sealed class Op {
        /** Play this clip from [startMs, endMs] (within the source) with no transition. */
        data class PlainClip(val path: String, val startMs: Long, val endMs: Long) : Op()
        /** Crossfade from clip A's tail into clip B's head, of crossfadeMs. */
        data class Crossfade(
            val pathA: String, val aTailStartMs: Long, val aEndMs: Long,
            val pathB: String, val bHeadStartMs: Long, val crossfadeMs: Long
        ) : Op()
        /** Dip through a solid color: A-tail fades to color (durMs/2), then B-head fades from color (durMs/2). */
        data class DipToColor(
            val pathA: String, val aTailStartMs: Long, val aEndMs: Long,
            val pathB: String, val bHeadStartMs: Long, val bHeadEndMs: Long,
            val halfDurationMs: Long, val colorInt: Int
        ) : Op()
        /** Slide clip B in over a static clip A; direction is the motion of B. */
        data class Slide(
            val pathA: String, val aTailStartMs: Long, val aEndMs: Long,
            val pathB: String, val bHeadStartMs: Long, val durationMs: Long,
            val direction: String
        ) : Op()
        /** Push clip A out while clip B slides in over the same overlap window. */
        data class Push(
            val pathA: String, val aTailStartMs: Long, val aEndMs: Long,
            val pathB: String, val bHeadStartMs: Long, val durationMs: Long,
            val direction: String
        ) : Op()
        /** Zoom clip B into place over a static clip A. */
        data class Zoom(
            val pathA: String, val aTailStartMs: Long, val aEndMs: Long,
            val pathB: String, val bHeadStartMs: Long, val durationMs: Long,
            val mode: String
        ) : Op()
        /** Experimental: blur A's tail while clip B whips in. */
        data class WhipPan(
            val pathA: String, val aTailStartMs: Long, val aEndMs: Long,
            val pathB: String, val bHeadStartMs: Long, val durationMs: Long,
            val direction: String
        ) : Op()
        /** Blur A's tail while clip B dissolves in-place over the overlap. */
        data class MotionBlur(
            val pathA: String, val aTailStartMs: Long, val aEndMs: Long,
            val pathB: String, val bHeadStartMs: Long, val durationMs: Long,
            val direction: String
        ) : Op()
    }

    /**
     * Walk the ordered timeline and build the plan. Logs each op. Returns the list.
     */
    suspend fun build(context: Context, projectId: String): List<Op> =
        withContext(Dispatchers.IO) {
            Log.d(
                TAG,
                "BUILD_START projectId=$projectId device=${Build.MANUFACTURER}/${Build.MODEL} " +
                    "sdk=${Build.VERSION.SDK_INT} thread=${Thread.currentThread().name}"
            )
            val db = ClipForgeDatabase.getInstance(context)
            val items = db.timelineDao().getTimelineForProjectOnce(projectId)
            Log.d(TAG, "timeline item count=${items.size}")

            // Collect video items with their paths + transition info, in order.
            data class Entry(
                val path: String,
                val trimStartMs: Long,
                val trimEndMs: Long,
                val transitionType: String?,
                val transitionDurationMs: Long?
            ) {
                val durationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)
            }
            val entries = mutableListOf<Entry>()
            for (item in items) {
                val asset = db.timelineDao().getMediaAssetForItem(item.mediaAssetId) ?: continue
                if (asset.mediaType != "VIDEO") continue
                val assetDur = asset.durationMs ?: 0L
                val hasRealTrim = item.trimEndMs > item.trimStartMs
                val srcStart = if (hasRealTrim) item.trimStartMs else 0L
                val srcEnd = if (hasRealTrim) item.trimEndMs else assetDur
                if (srcEnd <= srcStart) {
                    Log.d(TAG, "skip invalid clip " + asset.localUri.substringAfterLast('/') + " resolved=[" + srcStart + ".." + srcEnd + "] assetDur=" + assetDur)
                    continue
                }
                Log.d(TAG, "entry " + asset.localUri.substringAfterLast('/') + " trim=[" + item.trimStartMs + ".." + item.trimEndMs + "] assetDur=" + assetDur + " resolved=[" + srcStart + ".." + srcEnd + "] transition=" + item.transitionType + " dur=" + item.transitionDurationMs)
                entries.add(
                    Entry(
                        path = asset.localUri,
                        trimStartMs = srcStart,
                        trimEndMs = srcEnd,
                        transitionType = item.transitionType,
                        transitionDurationMs = item.transitionDurationMs
                    )
                )
            }

            if (entries.isEmpty()) {
                Log.d(TAG, "no video clips")
                return@withContext emptyList()
            }
            Log.d(TAG, "video entry count=${entries.size}")

            // For each clip, how much is consumed by an incoming crossfade (head) and
            // an outgoing crossfade (tail).
            val headConsumed = LongArray(entries.size)
            val tailConsumed = LongArray(entries.size)
            // Decide crossfades on each boundary i -> i+1.
            val isCrossfade = BooleanArray(entries.size) // crossfade from i into i+1
            val crossfadeMsArr = LongArray(entries.size)
            val isDip = BooleanArray(entries.size)
            val dipColorArr = IntArray(entries.size)
            val dipDurMsArr = LongArray(entries.size)
            val isSlide = BooleanArray(entries.size)
            val slideDurArr = LongArray(entries.size)
            val slideDirArr = arrayOfNulls<String>(entries.size)
            val isPush = BooleanArray(entries.size)
            val pushDurArr = LongArray(entries.size)
            val pushDirArr = arrayOfNulls<String>(entries.size)
            val isZoom = BooleanArray(entries.size)
            val zoomDurArr = LongArray(entries.size)
            val zoomModeArr = arrayOfNulls<String>(entries.size)
            val isWhipPan = BooleanArray(entries.size)
            val whipPanDurArr = LongArray(entries.size)
            val whipPanDirArr = arrayOfNulls<String>(entries.size)
            val isMotionBlur = BooleanArray(entries.size)
            val motionBlurDurArr = LongArray(entries.size)
            val motionBlurDirArr = arrayOfNulls<String>(entries.size)
            for (i in 0 until entries.size - 1) {
                val t = entries[i].transitionType?.uppercase()
                val durMs = entries[i].transitionDurationMs ?: 0L
                Log.d(TAG, "boundary $i->${i + 1} rawType=$t rawDurationMs=$durMs leftDur=${entries[i].durationMs} rightDur=${entries[i + 1].durationMs}")
                if (t in REAL_CROSSFADE_TYPES && durMs > 0L) {
                    isCrossfade[i] = true
                    crossfadeMsArr[i] = durMs
                    Log.d(TAG, "boundary $i->${i + 1} plan=XFADE requestedMs=$durMs")
                } else if ((t in FADE_BLACK_TYPES || t in FADE_WHITE_TYPES) && durMs > 0L) {
                    isDip[i] = true
                    dipColorArr[i] = if (t in FADE_WHITE_TYPES) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                    dipDurMsArr[i] = durMs
                    Log.d(TAG, "boundary $i->${i + 1} plan=DIP requestedMs=$durMs color=${dipColorArr[i]}")
                } else if (t in SLIDE_TYPES && durMs > 0L) {
                    isSlide[i] = true
                    slideDirArr[i] = t
                    slideDurArr[i] = durMs
                    Log.d(TAG, "boundary $i->${i + 1} plan=SLIDE requestedMs=$durMs direction=$t")
                } else if (t in PUSH_TYPES && durMs > 0L) {
                    isPush[i] = true
                    pushDirArr[i] = t
                    pushDurArr[i] = durMs
                    Log.d(TAG, "boundary $i->${i + 1} plan=PUSH requestedMs=$durMs direction=$t")
                } else if (t in ZOOM_TYPES && durMs > 0L) {
                    isZoom[i] = true
                    zoomModeArr[i] = t
                    zoomDurArr[i] = durMs
                    Log.d(TAG, "boundary $i->${i + 1} plan=ZOOM requestedMs=$durMs mode=$t")
                } else if (t in WHIP_PAN_TYPES && durMs > 0L) {
                    isWhipPan[i] = true
                    whipPanDirArr[i] = t
                    whipPanDurArr[i] = durMs.coerceIn(300L, 500L)
                    Log.d(TAG, "boundary $i->${i + 1} plan=WHIP_PAN requestedMs=$durMs effectiveMs=${whipPanDurArr[i]} direction=$t")
                } else if (t in MOTION_BLUR_TYPES && durMs > 0L) {
                    isMotionBlur[i] = true
                    motionBlurDirArr[i] = t
                    motionBlurDurArr[i] = durMs
                    Log.d(TAG, "boundary $i->${i + 1} plan=MOTION_BLUR requestedMs=$durMs direction=$t")
                } else if (t != null && t != "NONE" && durMs > 0L) {
                    Log.d(TAG, "boundary $i->${i + 1} type=$t NOT implemented -> plain cut")
                }
            }

            fun boundaryConsumption(i: Int): Long = when {
                isCrossfade[i] -> crossfadeMsArr[i]
                isDip[i] -> dipDurMsArr[i] / 2
                isSlide[i] -> slideDurArr[i]
                isPush[i] -> pushDurArr[i]
                isZoom[i] -> zoomDurArr[i]
                isWhipPan[i] -> whipPanDurArr[i]
                isMotionBlur[i] -> motionBlurDurArr[i]
                else -> 0L
            }

            fun setBoundaryConsumption(i: Int, consumptionMs: Long) {
                when {
                    isCrossfade[i] -> crossfadeMsArr[i] = consumptionMs
                    isDip[i] -> dipDurMsArr[i] = consumptionMs * 2
                    isSlide[i] -> slideDurArr[i] = consumptionMs
                    isPush[i] -> pushDurArr[i] = consumptionMs
                    isZoom[i] -> zoomDurArr[i] = consumptionMs
                    isWhipPan[i] -> whipPanDurArr[i] = consumptionMs
                    isMotionBlur[i] -> motionBlurDurArr[i] = consumptionMs
                }
            }

            repeat(3) {
                java.util.Arrays.fill(headConsumed, 0L)
                java.util.Arrays.fill(tailConsumed, 0L)
                for (i in 0 until entries.size - 1) {
                    val consumption = boundaryConsumption(i)
                    tailConsumed[i] += consumption
                    headConsumed[i + 1] += consumption
                }

                for (i in entries.indices) {
                    val consumed = headConsumed[i] + tailConsumed[i]
                    val maxConsumable = (entries[i].durationMs - MIN_RENDER_SLICE_MS).coerceAtLeast(0L)
                    if (consumed > maxConsumable) {
                        val scale = if (consumed == 0L) 0f else maxConsumable.toFloat() / consumed.toFloat()
                        if (i > 0) {
                            val previous = boundaryConsumption(i - 1)
                            setBoundaryConsumption(i - 1, (previous * scale).toLong())
                        }
                        if (i < entries.lastIndex) {
                            val next = boundaryConsumption(i)
                            setBoundaryConsumption(i, (next * scale).toLong())
                        }
                        Log.d(TAG, "clamped transitions around clip $i consumed=$consumed max=$maxConsumable scale=$scale")
                    }
                }
            }

            java.util.Arrays.fill(headConsumed, 0L)
            java.util.Arrays.fill(tailConsumed, 0L)
            for (i in 0 until entries.size - 1) {
                val consumption = boundaryConsumption(i)
                if (consumption <= 0L) {
                    Log.d(TAG, "boundary $i->${i + 1} disabled after clamp consumption=$consumption")
                    isCrossfade[i] = false
                    isDip[i] = false
                    isSlide[i] = false
                    isPush[i] = false
                    isZoom[i] = false
                    isWhipPan[i] = false
                    isMotionBlur[i] = false
                    continue
                }
                tailConsumed[i] += consumption
                headConsumed[i + 1] += consumption
                Log.d(
                    TAG,
                        "boundary $i->${i + 1} finalConsumptionMs=$consumption xfade=${isCrossfade[i]} " +
                        "dip=${isDip[i]} slide=${isSlide[i]} slideDir=${slideDirArr[i]} " +
                        "push=${isPush[i]} pushDir=${pushDirArr[i]} " +
                        "zoom=${isZoom[i]} zoomMode=${zoomModeArr[i]} " +
                        "whipPan=${isWhipPan[i]} whipPanDir=${whipPanDirArr[i]} " +
                        "motionBlur=${isMotionBlur[i]} motionBlurDir=${motionBlurDirArr[i]}"
                )
            }

            val ops = mutableListOf<Op>()
            for (i in entries.indices) {
                val e = entries[i]
                val clipStart = e.trimStartMs
                val clipEnd = e.trimEndMs
                val incomingBoundary = i - 1
                val hasDipIncoming = incomingBoundary >= 0 && isDip[incomingBoundary]
                val hasOverlapIncoming = incomingBoundary >= 0 &&
                    (isCrossfade[incomingBoundary] || isSlide[incomingBoundary] || isPush[incomingBoundary] || isZoom[incomingBoundary] || isWhipPan[incomingBoundary] || isMotionBlur[incomingBoundary])
                val outgoingBoundary = i
                val hasDipOutgoing = outgoingBoundary < entries.lastIndex && isDip[outgoingBoundary]
                val hasOverlapOutgoing = outgoingBoundary < entries.lastIndex &&
                    (isCrossfade[outgoingBoundary] || isSlide[outgoingBoundary] || isPush[outgoingBoundary] || isZoom[outgoingBoundary] || isWhipPan[outgoingBoundary] || isMotionBlur[outgoingBoundary])

                // Crossfade/Slide/Zoom are overlap families: the transition op already
                // renders A's tail and samples B's head, so the surrounding plain clips
                // must consume those windows to keep total duration at A + B - transition.
                // Dip is serial, but its op still owns A's tail and B's head exactly once.
                val bodyStart = if (hasDipIncoming || hasOverlapIncoming) {
                    clipStart + headConsumed[i]
                } else {
                    clipStart
                }
                val bodyEnd = if (hasDipOutgoing || hasOverlapOutgoing) {
                    clipEnd - tailConsumed[i]
                } else {
                    clipEnd
                }

                // Emit the plain body of this clip (the part not consumed by crossfades).
                if (bodyEnd > bodyStart) {
                    ops.add(Op.PlainClip(e.path, bodyStart, bodyEnd))
                } else {
                    Log.d(TAG, "clip $i body fully consumed by transitions (short clip)")
                }

                // If this clip crossfades into the next, emit the crossfade segment.
                if (i < entries.size - 1 && isCrossfade[i]) {
                    val cfMs = crossfadeMsArr[i]
                    val next = entries[i + 1]
                    if (cfMs > 0L) {
                        ops.add(
                            Op.Crossfade(
                                pathA = e.path,
                                aTailStartMs = clipEnd - cfMs,
                                aEndMs = clipEnd,
                                pathB = next.path,
                                bHeadStartMs = next.trimStartMs,
                                crossfadeMs = cfMs
                            )
                        )
                    }
                }
                // Dip-through-color: emit A-tail (fade to color) + B-head (fade from color), each half.
                if (i < entries.size - 1 && isDip[i]) {
                    val half = dipDurMsArr[i] / 2
                    val next = entries[i + 1]
                    if (half > 0L) {
                        ops.add(
                            Op.DipToColor(
                                pathA = e.path,
                                aTailStartMs = clipEnd - half,
                                aEndMs = clipEnd,
                                pathB = next.path,
                                bHeadStartMs = next.trimStartMs,
                                bHeadEndMs = next.trimStartMs + half,
                                halfDurationMs = half,
                                colorInt = dipColorArr[i]
                            )
                        )
                    }
                }
                // Slide: clip B slides in over A's tail (overlap-style, like dissolve).
                if (i < entries.size - 1 && isSlide[i]) {
                    val sMs = slideDurArr[i]
                    val next = entries[i + 1]
                    if (sMs > 0L) {
                        ops.add(
                            Op.Slide(
                                pathA = e.path,
                                aTailStartMs = clipEnd - sMs,
                                aEndMs = clipEnd,
                                pathB = next.path,
                                bHeadStartMs = next.trimStartMs,
                                durationMs = sMs,
                                direction = slideDirArr[i] ?: "SLIDE_LEFT"
                            )
                        )
                    }
                }
                // Push: A moves out while B slides in over A's tail (overlap-style).
                if (i < entries.size - 1 && isPush[i]) {
                    val pMs = pushDurArr[i]
                    val next = entries[i + 1]
                    if (pMs > 0L) {
                        ops.add(
                            Op.Push(
                                pathA = e.path,
                                aTailStartMs = clipEnd - pMs,
                                aEndMs = clipEnd,
                                pathB = next.path,
                                bHeadStartMs = next.trimStartMs,
                                durationMs = pMs,
                                direction = pushDirArr[i] ?: "PUSH_LEFT"
                            )
                        )
                    }
                }
                // Zoom: clip B scales into place over A's tail (overlap-style, like slide).
                if (i < entries.size - 1 && isZoom[i]) {
                    val zMs = zoomDurArr[i]
                    val next = entries[i + 1]
                    if (zMs > 0L) {
                        ops.add(
                            Op.Zoom(
                                pathA = e.path,
                                aTailStartMs = clipEnd - zMs,
                                aEndMs = clipEnd,
                                pathB = next.path,
                                bHeadStartMs = next.trimStartMs,
                                durationMs = zMs,
                                mode = zoomModeArr[i] ?: "ZOOM_IN"
                            )
                        )
                    }
                }
                // Experimental Whip Pan: B whips in over a blurred A tail.
                if (i < entries.size - 1 && isWhipPan[i]) {
                    val wMs = whipPanDurArr[i]
                    val next = entries[i + 1]
                    if (wMs > 0L) {
                        ops.add(
                            Op.WhipPan(
                                pathA = e.path,
                                aTailStartMs = clipEnd - wMs,
                                aEndMs = clipEnd,
                                pathB = next.path,
                                bHeadStartMs = next.trimStartMs,
                                durationMs = wMs,
                                direction = whipPanDirArr[i] ?: "WHIP_PAN_LEFT"
                            )
                        )
                    }
                }
                // Motion Blur: B dissolves in over a directionally blurred A tail.
                if (i < entries.size - 1 && isMotionBlur[i]) {
                    val mbMs = motionBlurDurArr[i]
                    val next = entries[i + 1]
                    if (mbMs > 0L) {
                        ops.add(
                            Op.MotionBlur(
                                pathA = e.path,
                                aTailStartMs = clipEnd - mbMs,
                                aEndMs = clipEnd,
                                pathB = next.path,
                                bHeadStartMs = next.trimStartMs,
                                durationMs = mbMs,
                                direction = motionBlurDirArr[i] ?: "MOTION_BLUR_LEFT"
                            )
                        )
                    }
                }
            }

            Log.d(TAG, "=== RENDER PLAN (${ops.size} ops) ===")
            ops.forEachIndexed { idx, op ->
                when (op) {
                    is Op.PlainClip -> Log.d(TAG, "[$idx] PLAIN ${op.path.substringAfterLast('/')} [${op.startMs}..${op.endMs}]")
                    is Op.Crossfade -> Log.d(TAG, "[$idx] XFADE ${op.crossfadeMs}ms  A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}]  B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}]")
                    is Op.DipToColor -> Log.d(TAG, "[$idx] DIP color=${op.colorInt} half=${op.halfDurationMs}ms  A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}]  B=${op.pathB.substringAfterLast('/')}[${op.bHeadStartMs}..${op.bHeadEndMs}]")
                    is Op.Slide -> Log.d(TAG, "[$idx] SLIDE dir=${op.direction} ${op.durationMs}ms  A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}]  B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}]")
                    is Op.Push -> Log.d(TAG, "[$idx] PUSH dir=${op.direction} ${op.durationMs}ms  A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}]  B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}]")
                    is Op.Zoom -> Log.d(TAG, "[$idx] ZOOM mode=${op.mode} ${op.durationMs}ms  A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}]  B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}]")
                    is Op.WhipPan -> Log.d(TAG, "[$idx] WHIP_PAN dir=${op.direction} ${op.durationMs}ms  A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}]  B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}]")
                    is Op.MotionBlur -> Log.d(TAG, "[$idx] MOTION_BLUR dir=${op.direction} ${op.durationMs}ms  A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}]  B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}]")
                }
            }
            ops
        }
}
