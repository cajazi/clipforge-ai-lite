package com.clipforge.ai.core.transition

import com.clipforge.ai.domain.model.TransitionType

object TransitionSpec {
    const val ZOOM_IN_SCALE_START = 0.82f
    const val ZOOM_IN_SCALE_END = 1.0f
    const val ZOOM_IN_ALPHA_START = 0.92f
    const val ZOOM_IN_ALPHA_END = 1.0f
    const val ZOOM_OUT_SCALE_START = 1.16f
    const val ZOOM_OUT_SCALE_END = 1.0f
    const val ZOOM_OUT_ALPHA_START = 1.0f
    const val ZOOM_OUT_ALPHA_END = 1.0f

    enum class Family {
        None,
        Crossfade,
        Dip,
        Slide,
        Zoom,
        WhipPan,
        PlainCut
    }

    enum class DipColor {
        Black,
        White
    }

    enum class SlideDirection {
        Left,
        Right,
        Up,
        Down
    }

    enum class ZoomMode {
        In,
        Out
    }

    sealed class Spec(
        val family: Family,
        val exportable: Boolean
    )

    object None : Spec(Family.None, exportable = true)
    object Crossfade : Spec(Family.Crossfade, exportable = true)
    sealed class Dip(val color: DipColor) : Spec(Family.Dip, exportable = true) {
        object Black : Dip(DipColor.Black)
        object White : Dip(DipColor.White)
    }

    sealed class Slide(val direction: SlideDirection) : Spec(Family.Slide, exportable = true) {
        object Left : Slide(SlideDirection.Left)
        object Right : Slide(SlideDirection.Right)
        object Up : Slide(SlideDirection.Up)
        object Down : Slide(SlideDirection.Down)
    }

    sealed class Zoom(val mode: ZoomMode) : Spec(Family.Zoom, exportable = true) {
        object In : Zoom(ZoomMode.In)
        object Out : Zoom(ZoomMode.Out)
    }
    object WhipPanLeft : Spec(Family.WhipPan, exportable = true)
    object PlainCut : Spec(Family.PlainCut, exportable = false)

    fun forType(type: TransitionType?): Spec = when (type) {
        null,
        TransitionType.NONE -> None
        TransitionType.DISSOLVE,
        TransitionType.CROSS_DISSOLVE -> Crossfade
        TransitionType.FADE,
        TransitionType.FADE_BLACK -> Dip.Black
        TransitionType.FADE_WHITE -> Dip.White
        TransitionType.SLIDE_LEFT -> Slide.Left
        TransitionType.SLIDE_RIGHT -> Slide.Right
        TransitionType.SLIDE_UP -> Slide.Up
        TransitionType.SLIDE_DOWN -> Slide.Down
        TransitionType.ZOOM_IN -> Zoom.In
        TransitionType.ZOOM_OUT -> Zoom.Out
        TransitionType.WHIP_PAN_LEFT -> WhipPanLeft
        else -> PlainCut
    }

    fun smoothstep(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun lerp(start: Float, end: Float, progress: Float): Float =
        start + ((end - start) * progress)
}
