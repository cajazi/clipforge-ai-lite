package com.clipforge.ai.core.transition

import androidx.media3.common.util.UnstableApi
import com.clipforge.ai.core.transition.renderers.CrossfadeTransitionRenderer
import com.clipforge.ai.core.transition.renderers.CubeTransitionRenderer
import com.clipforge.ai.core.transition.renderers.DipToColorTransitionRenderer
import com.clipforge.ai.core.transition.renderers.FilmBurnTransitionRenderer
import com.clipforge.ai.core.transition.renderers.FlashTransitionRenderer
import com.clipforge.ai.core.transition.renderers.FlipTransitionRenderer
import com.clipforge.ai.core.transition.renderers.GlitchProTransitionRenderer
import com.clipforge.ai.core.transition.renderers.MotionBlurTransitionRenderer
import com.clipforge.ai.core.transition.renderers.PageTurnTransitionRenderer
import com.clipforge.ai.core.transition.renderers.PushTransitionRenderer
import com.clipforge.ai.core.transition.renderers.RotationTransitionRenderer
import com.clipforge.ai.core.transition.renderers.SlideTransitionRenderer
import com.clipforge.ai.core.transition.renderers.WhipPanTransitionRenderer
import com.clipforge.ai.core.transition.renderers.WipeTransitionRenderer
import com.clipforge.ai.core.transition.renderers.ZoomTransitionRenderer
import com.clipforge.ai.domain.model.TransitionType

/**
 * Built-in transition registrations for the currently-exportable families, plus the mapping
 * between the persisted [TransitionType] enum and framework [TransitionId]s.
 *
 * IMPORTANT (Phase B safety): calling [registerBuiltIns] only populates [TransitionRegistry].
 * Nothing in the live export/preview path reads the registry yet (the executor flip is Phase
 * D), so registering changes NO runtime behavior. It exists so the dual-run parity gate and
 * future phases have a populated registry to validate against.
 *
 * The family grouping mirrors CrossfadeRenderPlan's type-string sets exactly.
 */
@UnstableApi
object TransitionRegistrations {

    // Stable framework ids (one per family member we currently bake).
    val DISSOLVE = TransitionId("dissolve")
    val FADE_BLACK = TransitionId("fade_black")
    val FADE_WHITE = TransitionId("fade_white")
    val FLASH_WHITE = TransitionId("flash_white")
    val FLASH_BLACK = TransitionId("flash_black")
    val FLASH_WARM = TransitionId("flash_warm")
    val FLASH_BLUE = TransitionId("flash_blue")
    val FILM_BURN = TransitionId("film_burn")
    val FILM_BURN_WARM = TransitionId("film_burn_warm")
    val FILM_BURN_HEAVY = TransitionId("film_burn_heavy")
    val SLIDE_LEFT = TransitionId("slide_left")
    val SLIDE_RIGHT = TransitionId("slide_right")
    val SLIDE_UP = TransitionId("slide_up")
    val SLIDE_DOWN = TransitionId("slide_down")
    val PUSH_LEFT = TransitionId("push_left")
    val PUSH_RIGHT = TransitionId("push_right")
    val PUSH_UP = TransitionId("push_up")
    val PUSH_DOWN = TransitionId("push_down")
    val ZOOM_IN = TransitionId("zoom_in")
    val ZOOM_OUT = TransitionId("zoom_out")
    val SPIN = TransitionId("spin")
    val ROTATE = TransitionId("rotate")
    val CAMERA_ROLL = TransitionId("camera_roll")
    val CUBE_LEFT = TransitionId("cube_left")
    val CUBE_RIGHT = TransitionId("cube_right")
    val CUBE_UP = TransitionId("cube_up")
    val CUBE_DOWN = TransitionId("cube_down")
    val FLIP_LEFT = TransitionId("flip_left")
    val FLIP_RIGHT = TransitionId("flip_right")
    val FLIP_UP = TransitionId("flip_up")
    val FLIP_DOWN = TransitionId("flip_down")
    val PAGE_TURN_LEFT = TransitionId("page_turn_left")
    val PAGE_TURN_RIGHT = TransitionId("page_turn_right")
    val PAGE_TURN_UP = TransitionId("page_turn_up")
    val PAGE_TURN_DOWN = TransitionId("page_turn_down")
    val WHIP_PAN_LEFT = TransitionId("whip_pan_left")
    val WHIP_PAN_RIGHT = TransitionId("whip_pan_right")
    val WHIP_PAN_UP = TransitionId("whip_pan_up")
    val WHIP_PAN_DOWN = TransitionId("whip_pan_down")
    val MOTION_BLUR_LEFT = TransitionId("motion_blur_left")
    val MOTION_BLUR_RIGHT = TransitionId("motion_blur_right")
    val MOTION_BLUR_UP = TransitionId("motion_blur_up")
    val MOTION_BLUR_DOWN = TransitionId("motion_blur_down")
    val WIPE = TransitionId("wipe")
    val WIPE_RIGHT = TransitionId("wipe_right")
    val WIPE_UP = TransitionId("wipe_up")
    val WIPE_DOWN = TransitionId("wipe_down")
    val GLITCH_PRO = TransitionId("glitch_pro")
    val GLITCH_DIGITAL = TransitionId("glitch_digital")
    val GLITCH_RGB = TransitionId("glitch_rgb")
    val GLITCH_SCANLINE = TransitionId("glitch_scanline")

    /**
     * Maps a persisted [TransitionType] to its framework [TransitionId], or null if that type
     * is not yet exportable (falls back to a plain cut in export, as today).
     */
    fun idFor(type: TransitionType): TransitionId? = when (type) {
        TransitionType.DISSOLVE, TransitionType.CROSS_DISSOLVE -> DISSOLVE
        TransitionType.FADE, TransitionType.FADE_BLACK -> FADE_BLACK
        TransitionType.FADE_WHITE -> FADE_WHITE
        TransitionType.FLASH -> FLASH_WHITE
        TransitionType.FLASH_BLACK -> FLASH_BLACK
        TransitionType.FLASH_WARM -> FLASH_WARM
        TransitionType.FLASH_BLUE -> FLASH_BLUE
        TransitionType.FILM_BURN -> FILM_BURN
        TransitionType.FILM_BURN_WARM -> FILM_BURN_WARM
        TransitionType.FILM_BURN_HEAVY -> FILM_BURN_HEAVY
        TransitionType.SLIDE_LEFT -> SLIDE_LEFT
        TransitionType.SLIDE_RIGHT -> SLIDE_RIGHT
        TransitionType.SLIDE_UP -> SLIDE_UP
        TransitionType.SLIDE_DOWN -> SLIDE_DOWN
        TransitionType.PUSH_LEFT -> PUSH_LEFT
        TransitionType.PUSH_RIGHT -> PUSH_RIGHT
        TransitionType.PUSH_UP -> PUSH_UP
        TransitionType.PUSH_DOWN -> PUSH_DOWN
        TransitionType.ZOOM_IN -> ZOOM_IN
        TransitionType.ZOOM_OUT -> ZOOM_OUT
        TransitionType.SPIN -> SPIN
        TransitionType.ROTATE -> ROTATE
        TransitionType.CAMERA_ROLL -> CAMERA_ROLL
        TransitionType.CUBE_LEFT -> CUBE_LEFT
        TransitionType.CUBE_RIGHT -> CUBE_RIGHT
        TransitionType.CUBE_UP -> CUBE_UP
        TransitionType.CUBE_DOWN -> CUBE_DOWN
        TransitionType.FLIP_LEFT -> FLIP_LEFT
        TransitionType.FLIP_RIGHT -> FLIP_RIGHT
        TransitionType.FLIP_UP -> FLIP_UP
        TransitionType.FLIP_DOWN -> FLIP_DOWN
        TransitionType.PAGE_TURN_LEFT -> PAGE_TURN_LEFT
        TransitionType.PAGE_TURN_RIGHT -> PAGE_TURN_RIGHT
        TransitionType.PAGE_TURN_UP -> PAGE_TURN_UP
        TransitionType.PAGE_TURN_DOWN -> PAGE_TURN_DOWN
        TransitionType.WHIP_PAN_LEFT -> WHIP_PAN_LEFT
        TransitionType.WHIP_PAN_RIGHT -> WHIP_PAN_RIGHT
        TransitionType.WHIP_PAN_UP -> WHIP_PAN_UP
        TransitionType.WHIP_PAN_DOWN -> WHIP_PAN_DOWN
        TransitionType.MOTION_BLUR_LEFT -> MOTION_BLUR_LEFT
        TransitionType.MOTION_BLUR_RIGHT -> MOTION_BLUR_RIGHT
        TransitionType.MOTION_BLUR_UP -> MOTION_BLUR_UP
        TransitionType.MOTION_BLUR_DOWN -> MOTION_BLUR_DOWN
        TransitionType.WIPE -> WIPE
        TransitionType.WIPE_RIGHT -> WIPE_RIGHT
        TransitionType.WIPE_UP -> WIPE_UP
        TransitionType.WIPE_DOWN -> WIPE_DOWN
        TransitionType.GLITCH_PRO -> GLITCH_PRO
        TransitionType.GLITCH_DIGITAL -> GLITCH_DIGITAL
        TransitionType.GLITCH_RGB -> GLITCH_RGB
        TransitionType.GLITCH_SCANLINE -> GLITCH_SCANLINE
        else -> null
    }

    /** Idempotent: registers every built-in family into [TransitionRegistry]. */
    fun registerBuiltIns(registry: TransitionRegistry = TransitionRegistry) {
        val crossfade = CrossfadeTransitionRenderer()
        val dip = DipToColorTransitionRenderer()
        val flash = FlashTransitionRenderer()
        val filmBurn = FilmBurnTransitionRenderer()
        val slide = SlideTransitionRenderer()
        val push = PushTransitionRenderer()
        val zoom = ZoomTransitionRenderer()
        val rotation = RotationTransitionRenderer()
        val cube = CubeTransitionRenderer()
        val flip = FlipTransitionRenderer()
        val pageTurn = PageTurnTransitionRenderer()
        val whip = WhipPanTransitionRenderer()
        val motionBlur = MotionBlurTransitionRenderer()
        val wipe = WipeTransitionRenderer()
        val glitchPro = GlitchProTransitionRenderer()

        fun reg(
            id: TransitionId,
            name: String,
            category: TransitionCategory,
            timing: TimingModel,
            renderer: com.clipforge.ai.core.transition.TransitionRenderer,
            stageMessage: String,
            easing: Easing = Easing.Smoothstep
        ) {
            registry.register(
                TransitionRegistration(
                    descriptor = TransitionDescriptor(
                        id = id,
                        displayName = name,
                        category = category,
                        timingModel = timing,
                        easing = easing,
                        stageMessage = stageMessage,
                        isExportable = true
                    ),
                    renderer = renderer,
                    previewRenderer = PreviewRenderer.PlainCut // preview migration is a later phase
                )
            )
        }

        // stageMessage strings mirror the legacy CrossfadeExecutor onStage(...) text exactly.
        val sDissolve = "Preparing dissolve transition..."
        val sFade = "Preparing fade transition..."
        val sFlash = "Preparing flash transition..."
        val sFilmBurn = "Preparing film burn transition..."
        val sSlide = "Preparing slide transition..."
        val sPush = "Preparing push transition..."
        val sZoom = "Preparing zoom transition..."
        val sRotation = "Preparing rotation transition..."
        val sCube = "Preparing cube transition..."
        val sFlip = "Preparing flip transition..."
        val sPageTurn = "Preparing page turn transition..."
        val sWhip = "Preparing whip pan transition..."
        val sMotionBlur = "Preparing motion blur transition..."
        val sWipe = "Preparing wipe transition..."
        val sGlitch = "Preparing glitch transition..."

        reg(DISSOLVE, "Dissolve", TransitionCategory.DISSOLVE, TimingModel.Overlap, crossfade, sDissolve)
        reg(FADE_BLACK, "Fade Black", TransitionCategory.FADE, TimingModel.SequentialDip, dip, sFade)
        reg(FADE_WHITE, "Fade White", TransitionCategory.FADE, TimingModel.SequentialDip, dip, sFade)
        reg(FLASH_WHITE, "Flash", TransitionCategory.FADE, TimingModel.Overlap, flash, sFlash)
        reg(FLASH_BLACK, "Flash Black", TransitionCategory.FADE, TimingModel.Overlap, flash, sFlash)
        reg(FLASH_WARM, "Flash Warm", TransitionCategory.FADE, TimingModel.Overlap, flash, sFlash)
        reg(FLASH_BLUE, "Flash Blue", TransitionCategory.FADE, TimingModel.Overlap, flash, sFlash)
        reg(FILM_BURN, "Film Burn", TransitionCategory.FADE, TimingModel.Overlap, filmBurn, sFilmBurn)
        reg(FILM_BURN_WARM, "Film Burn Warm", TransitionCategory.FADE, TimingModel.Overlap, filmBurn, sFilmBurn)
        reg(FILM_BURN_HEAVY, "Film Burn Heavy", TransitionCategory.FADE, TimingModel.Overlap, filmBurn, sFilmBurn)
        reg(SLIDE_LEFT, "Slide Left", TransitionCategory.MOTION, TimingModel.Overlap, slide, sSlide)
        reg(SLIDE_RIGHT, "Slide Right", TransitionCategory.MOTION, TimingModel.Overlap, slide, sSlide)
        reg(SLIDE_UP, "Slide Up", TransitionCategory.MOTION, TimingModel.Overlap, slide, sSlide)
        reg(SLIDE_DOWN, "Slide Down", TransitionCategory.MOTION, TimingModel.Overlap, slide, sSlide)
        reg(PUSH_LEFT, "Push Left", TransitionCategory.MOTION, TimingModel.Overlap, push, sPush)
        reg(PUSH_RIGHT, "Push Right", TransitionCategory.MOTION, TimingModel.Overlap, push, sPush)
        reg(PUSH_UP, "Push Up", TransitionCategory.MOTION, TimingModel.Overlap, push, sPush)
        reg(PUSH_DOWN, "Push Down", TransitionCategory.MOTION, TimingModel.Overlap, push, sPush)
        reg(ZOOM_IN, "Zoom In", TransitionCategory.MOTION, TimingModel.Overlap, zoom, sZoom)
        reg(ZOOM_OUT, "Zoom Out", TransitionCategory.MOTION, TimingModel.Overlap, zoom, sZoom)
        reg(SPIN, "Spin", TransitionCategory.MOTION, TimingModel.Overlap, rotation, sRotation)
        reg(ROTATE, "Rotate", TransitionCategory.MOTION, TimingModel.Overlap, rotation, sRotation)
        reg(CAMERA_ROLL, "Camera Roll", TransitionCategory.MOTION, TimingModel.Overlap, rotation, sRotation)
        reg(CUBE_LEFT, "3D Cube Left", TransitionCategory.THREE_D, TimingModel.Overlap, cube, sCube)
        reg(CUBE_RIGHT, "3D Cube Right", TransitionCategory.THREE_D, TimingModel.Overlap, cube, sCube)
        reg(CUBE_UP, "3D Cube Up", TransitionCategory.THREE_D, TimingModel.Overlap, cube, sCube)
        reg(CUBE_DOWN, "3D Cube Down", TransitionCategory.THREE_D, TimingModel.Overlap, cube, sCube)
        reg(FLIP_LEFT, "Flip Left", TransitionCategory.THREE_D, TimingModel.Overlap, flip, sFlip)
        reg(FLIP_RIGHT, "Flip Right", TransitionCategory.THREE_D, TimingModel.Overlap, flip, sFlip)
        reg(FLIP_UP, "Flip Up", TransitionCategory.THREE_D, TimingModel.Overlap, flip, sFlip)
        reg(FLIP_DOWN, "Flip Down", TransitionCategory.THREE_D, TimingModel.Overlap, flip, sFlip)
        reg(PAGE_TURN_LEFT, "Page Turn Left", TransitionCategory.THREE_D, TimingModel.Overlap, pageTurn, sPageTurn)
        reg(PAGE_TURN_RIGHT, "Page Turn Right", TransitionCategory.THREE_D, TimingModel.Overlap, pageTurn, sPageTurn)
        reg(PAGE_TURN_UP, "Page Turn Up", TransitionCategory.THREE_D, TimingModel.Overlap, pageTurn, sPageTurn)
        reg(PAGE_TURN_DOWN, "Page Turn Down", TransitionCategory.THREE_D, TimingModel.Overlap, pageTurn, sPageTurn)
        reg(WHIP_PAN_LEFT, "Whip Pan Left", TransitionCategory.BLUR, TimingModel.Overlap, whip, sWhip, Easing.ExpoOut)
        reg(WHIP_PAN_RIGHT, "Whip Pan Right", TransitionCategory.BLUR, TimingModel.Overlap, whip, sWhip, Easing.ExpoOut)
        reg(WHIP_PAN_UP, "Whip Pan Up", TransitionCategory.BLUR, TimingModel.Overlap, whip, sWhip, Easing.ExpoOut)
        reg(WHIP_PAN_DOWN, "Whip Pan Down", TransitionCategory.BLUR, TimingModel.Overlap, whip, sWhip, Easing.ExpoOut)
        reg(MOTION_BLUR_LEFT, "Motion Blur Left", TransitionCategory.BLUR, TimingModel.Overlap, motionBlur, sMotionBlur)
        reg(MOTION_BLUR_RIGHT, "Motion Blur Right", TransitionCategory.BLUR, TimingModel.Overlap, motionBlur, sMotionBlur)
        reg(MOTION_BLUR_UP, "Motion Blur Up", TransitionCategory.BLUR, TimingModel.Overlap, motionBlur, sMotionBlur)
        reg(MOTION_BLUR_DOWN, "Motion Blur Down", TransitionCategory.BLUR, TimingModel.Overlap, motionBlur, sMotionBlur)
        reg(WIPE, "Wipe", TransitionCategory.WIPE, TimingModel.Overlap, wipe, sWipe)
        reg(WIPE_RIGHT, "Wipe Right", TransitionCategory.WIPE, TimingModel.Overlap, wipe, sWipe)
        reg(WIPE_UP, "Wipe Up", TransitionCategory.WIPE, TimingModel.Overlap, wipe, sWipe)
        reg(WIPE_DOWN, "Wipe Down", TransitionCategory.WIPE, TimingModel.Overlap, wipe, sWipe)
        reg(GLITCH_PRO, "Glitch Pro", TransitionCategory.GLITCH, TimingModel.Overlap, glitchPro, sGlitch)
        reg(GLITCH_DIGITAL, "Glitch Digital", TransitionCategory.GLITCH, TimingModel.Overlap, glitchPro, sGlitch)
        reg(GLITCH_RGB, "Glitch RGB", TransitionCategory.GLITCH, TimingModel.Overlap, glitchPro, sGlitch)
        reg(GLITCH_SCANLINE, "Glitch Scanline", TransitionCategory.GLITCH, TimingModel.Overlap, glitchPro, sGlitch)
    }
}
