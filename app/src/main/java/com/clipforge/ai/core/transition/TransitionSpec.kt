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
        Flash,
        FilmBurn,
        Slide,
        Push,
        Zoom,
        Rotation,
        Cube,
        Flip,
        PageTurn,
        WhipPan,
        MotionBlur,
        PlainCut
    }

    enum class DipColor {
        Black,
        White
    }

    enum class FlashColor {
        White,
        Black,
        Warm,
        Blue
    }

    enum class FilmBurnMode {
        Classic,
        Warm,
        Heavy
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

    enum class RotationMode {
        Spin,
        Rotate,
        CameraRoll
    }

    enum class CubeDirection {
        Left,
        Right,
        Up,
        Down
    }

    enum class FlipDirection {
        Left,
        Right,
        Up,
        Down
    }

    enum class PageTurnDirection {
        Left,
        Right,
        Up,
        Down
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
    sealed class Flash(val color: FlashColor) : Spec(Family.Flash, exportable = true) {
        object White : Flash(FlashColor.White)
        object Black : Flash(FlashColor.Black)
        object Warm : Flash(FlashColor.Warm)
        object Blue : Flash(FlashColor.Blue)
    }
    sealed class FilmBurn(val mode: FilmBurnMode) : Spec(Family.FilmBurn, exportable = true) {
        object Classic : FilmBurn(FilmBurnMode.Classic)
        object Warm : FilmBurn(FilmBurnMode.Warm)
        object Heavy : FilmBurn(FilmBurnMode.Heavy)
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
    sealed class Rotation(val mode: RotationMode) : Spec(Family.Rotation, exportable = true) {
        object Spin : Rotation(RotationMode.Spin)
        object Rotate : Rotation(RotationMode.Rotate)
        object CameraRoll : Rotation(RotationMode.CameraRoll)
    }
    sealed class Cube(val direction: CubeDirection) : Spec(Family.Cube, exportable = true) {
        object Left : Cube(CubeDirection.Left)
        object Right : Cube(CubeDirection.Right)
        object Up : Cube(CubeDirection.Up)
        object Down : Cube(CubeDirection.Down)
    }
    sealed class Flip(val direction: FlipDirection) : Spec(Family.Flip, exportable = true) {
        object Left : Flip(FlipDirection.Left)
        object Right : Flip(FlipDirection.Right)
        object Up : Flip(FlipDirection.Up)
        object Down : Flip(FlipDirection.Down)
    }
    sealed class PageTurn(val direction: PageTurnDirection) : Spec(Family.PageTurn, exportable = true) {
        object Left : PageTurn(PageTurnDirection.Left)
        object Right : PageTurn(PageTurnDirection.Right)
        object Up : PageTurn(PageTurnDirection.Up)
        object Down : PageTurn(PageTurnDirection.Down)
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
        TransitionType.FLASH -> Flash.White
        TransitionType.FLASH_BLACK -> Flash.Black
        TransitionType.FLASH_WARM -> Flash.Warm
        TransitionType.FLASH_BLUE -> Flash.Blue
        TransitionType.FILM_BURN -> FilmBurn.Classic
        TransitionType.FILM_BURN_WARM -> FilmBurn.Warm
        TransitionType.FILM_BURN_HEAVY -> FilmBurn.Heavy
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
        TransitionType.SPIN -> Rotation.Spin
        TransitionType.ROTATE -> Rotation.Rotate
        TransitionType.CAMERA_ROLL -> Rotation.CameraRoll
        TransitionType.CUBE_LEFT -> Cube.Left
        TransitionType.CUBE_RIGHT -> Cube.Right
        TransitionType.CUBE_UP -> Cube.Up
        TransitionType.CUBE_DOWN -> Cube.Down
        TransitionType.FLIP_LEFT -> Flip.Left
        TransitionType.FLIP_RIGHT -> Flip.Right
        TransitionType.FLIP_UP -> Flip.Up
        TransitionType.FLIP_DOWN -> Flip.Down
        TransitionType.PAGE_TURN_LEFT -> PageTurn.Left
        TransitionType.PAGE_TURN_RIGHT -> PageTurn.Right
        TransitionType.PAGE_TURN_UP -> PageTurn.Up
        TransitionType.PAGE_TURN_DOWN -> PageTurn.Down
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
