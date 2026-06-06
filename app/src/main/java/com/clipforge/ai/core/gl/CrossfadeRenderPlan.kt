package com.clipforge.ai.core.gl

import android.content.Context
import android.util.Log
import com.clipforge.ai.data.local.database.ClipForgeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds an ordered render plan for a project's timeline, deciding where real
 * crossfade segments go vs. plain clips.
 *
 * HONEST RULE (no fake feature labels): only DISSOLVE / CROSS_DISSOLVE transitions
 * render the real crossfade. Every other transitionType (Slide, Blur, Glitch, 3D...)
 * has no implementation yet, so it falls back to a PLAIN CUT - it is NOT dressed up
 * as a dissolve. Each type lights up for real when its shader is built.
 *
 * This stage LOGS the plan only - it does not render. Verify the plan matches the
 * timeline, then the executor builds on it.
 */
object CrossfadeRenderPlan {

    private const val TAG = "CROSSFADE_PLAN"

    // transitionType strings that we can actually render today.
    private val REAL_CROSSFADE_TYPES = setOf("DISSOLVE", "CROSS_DISSOLVE")
    // Fade-through-a-color transitions (FADE + FADE_BLACK -> black, FADE_WHITE -> white).
    private val FADE_BLACK_TYPES = setOf("FADE", "FADE_BLACK")
    private val FADE_WHITE_TYPES = setOf("FADE_WHITE")

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
    }

    /**
     * Walk the ordered timeline and build the plan. Logs each op. Returns the list.
     */
    suspend fun build(context: Context, projectId: String): List<Op> =
        withContext(Dispatchers.IO) {
            val db = ClipForgeDatabase.getInstance(context)
            val items = db.timelineDao().getTimelineForProjectOnce(projectId)

            // Collect video items with their paths + transition info, in order.
            data class Entry(
                val path: String,
                val trimStartMs: Long,
                val trimEndMs: Long,
                val transitionType: String?,
                val transitionDurationMs: Long?
            )
            val entries = mutableListOf<Entry>()
            for (item in items) {
                val asset = db.timelineDao().getMediaAssetForItem(item.mediaAssetId) ?: continue
                if (asset.mediaType != "VIDEO") continue
                val assetDur = asset.durationMs ?: 0L
                val hasRealTrim = item.trimEndMs > item.trimStartMs
                val srcStart = if (hasRealTrim) item.trimStartMs else 0L
                val srcEnd = if (hasRealTrim) item.trimEndMs else assetDur
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
            for (i in 0 until entries.size - 1) {
                val t = entries[i].transitionType?.uppercase()
                val durMs = entries[i].transitionDurationMs ?: 0L
                if (t in REAL_CROSSFADE_TYPES && durMs > 0L) {
                    isCrossfade[i] = true
                    crossfadeMsArr[i] = durMs
                    tailConsumed[i] += durMs
                    headConsumed[i + 1] += durMs
                } else if ((t in FADE_BLACK_TYPES || t in FADE_WHITE_TYPES) && durMs > 0L) {
                    isDip[i] = true
                    dipColorArr[i] = if (t in FADE_WHITE_TYPES) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                    dipDurMsArr[i] = durMs
                    val half = durMs / 2
                    tailConsumed[i] += half
                    headConsumed[i + 1] += half
                } else if (t != null && t != "NONE" && durMs > 0L) {
                    Log.d(TAG, "boundary $i->${i + 1} type=$t NOT implemented -> plain cut")
                }
            }

            val ops = mutableListOf<Op>()
            for (i in entries.indices) {
                val e = entries[i]
                val clipStart = e.trimStartMs
                val clipEnd = e.trimEndMs
                val bodyStart = clipStart + headConsumed[i]
                val bodyEnd = clipEnd - tailConsumed[i]

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
                // Dip-through-color: emit A-tail (fade to color) + B-head (fade from color), each half.
                if (i < entries.size - 1 && isDip[i]) {
                    val half = dipDurMsArr[i] / 2
                    val next = entries[i + 1]
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

            Log.d(TAG, "=== RENDER PLAN (${ops.size} ops) ===")
            ops.forEachIndexed { idx, op ->
                when (op) {
                    is Op.PlainClip -> Log.d(TAG, "[$idx] PLAIN ${op.path.substringAfterLast('/')} [${op.startMs}..${op.endMs}]")
                    is Op.Crossfade -> Log.d(TAG, "[$idx] XFADE ${op.crossfadeMs}ms  A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}]  B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}]")
                    is Op.DipToColor -> Log.d(TAG, "[$idx] DIP color=${op.colorInt} half=${op.halfDurationMs}ms  A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}]  B=${op.pathB.substringAfterLast('/')}[${op.bHeadStartMs}..${op.bHeadEndMs}]")
                }
            }
            ops
        }
}
