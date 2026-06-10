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
        Push,
        Zoom,
        WhipPan,
        MotionBlur,
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

    sealed class Push(val direction: SlideDirection) : Spec(Family.Push, exportable = true) {
        object Left : Push(SlideDirection.Left)
        object Right : Push(SlideDirection.Right)
        object Up : Push(SlideDirection.Up)
        object Down : Push(SlideDirection.Down)
    }

    sealed class Zoom(val mode: ZoomMode) : Spec(Family.Zoom, exportable = true) {
        object In : Zoom(ZoomMode.In)
        object Out : Zoom(ZoomMode.Out)
    }
    sealed class WhipPan(val direction: SlideDirection) : Spec(Family.WhipPan, exportable = true) {
        object Left : WhipPan(SlideDirection.Left)
        object Right : WhipPan(SlideDirection.Right)
        object Up : WhipPan(SlideDirection.Up)
        object Down : WhipPan(SlideDirection.Down)
    }
    sealed class MotionBlur(val direction: SlideDirection) : Spec(Family.MotionBlur, exportable = true) {
        object Left : MotionBlur(SlideDirection.Left)
        object Right : MotionBlur(SlideDirection.Right)
        object Up : MotionBlur(SlideDirection.Up)
        object Down : MotionBlur(SlideDirection.Down)
    }
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
        TransitionType.PUSH_LEFT -> Push.Left
        TransitionType.PUSH_RIGHT -> Push.Right
        TransitionType.PUSH_UP -> Push.Up
        TransitionType.PUSH_DOWN -> Push.Down
        TransitionType.ZOOM_IN -> Zoom.In
        TransitionType.ZOOM_OUT -> Zoom.Out
        TransitionType.WHIP_PAN_LEFT -> WhipPan.Left
        TransitionType.WHIP_PAN_RIGHT -> WhipPan.Right
        TransitionType.WHIP_PAN_UP -> WhipPan.Up
        TransitionType.WHIP_PAN_DOWN -> WhipPan.Down
        TransitionType.MOTION_BLUR_LEFT -> MotionBlur.Left
        TransitionType.MOTION_BLUR_RIGHT -> MotionBlur.Right
        TransitionType.MOTION_BLUR_UP -> MotionBlur.Up
        TransitionType.MOTION_BLUR_DOWN -> MotionBlur.Down
        else -> PlainCut
    }

    fun smoothstep(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun lerp(start: Float, end: Float, progress: Float): Float =
        start + ((end - start) * progress)
}
