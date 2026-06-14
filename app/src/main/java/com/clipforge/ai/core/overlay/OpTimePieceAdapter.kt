package com.clipforge.ai.core.overlay

import com.clipforge.ai.core.gl.CrossfadeRenderPlan

object OpTimePieceAdapter {
    fun toTimePieces(ops: List<CrossfadeRenderPlan.Op>): List<TimePiece> = ops.map { op ->
        when (op) {
            is CrossfadeRenderPlan.Op.PlainClip -> {
                val len = (op.endMs - op.startMs).coerceAtLeast(0L)
                TimePiece(timelineMs = len, compositionMs = len)
            }
            is CrossfadeRenderPlan.Op.Crossfade ->
                overlap(op.crossfadeMs)
            is CrossfadeRenderPlan.Op.DipToColor -> {
                val len = op.halfDurationMs * 2L
                TimePiece(timelineMs = len, compositionMs = len)
            }
            is CrossfadeRenderPlan.Op.Flash ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.FilmBurn ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.Slide ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.Push ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.Zoom ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.Bounce ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.Rotation ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.Cube ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.Flip ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.PageTurn ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.Blur ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.WhipPan ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.MotionBlur ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.Wipe ->
                overlap(op.durationMs)
            is CrossfadeRenderPlan.Op.GlitchPro ->
                overlap(op.durationMs)
        }
    }

    private fun overlap(durationMs: Long): TimePiece =
        TimePiece(timelineMs = durationMs * 2L, compositionMs = durationMs)
}
