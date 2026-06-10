package com.clipforge.ai.core.transition

import androidx.media3.common.util.UnstableApi
import com.clipforge.ai.core.transition.renderers.CrossfadeTransitionRenderer
import com.clipforge.ai.core.transition.renderers.CubeTransitionRenderer
import com.clipforge.ai.core.transition.renderers.DipToColorTransitionRenderer
import com.clipforge.ai.core.transition.renderers.FlipTransitionRenderer
import com.clipforge.ai.core.transition.renderers.MotionBlurTransitionRenderer
import com.clipforge.ai.core.transition.renderers.PushTransitionRenderer
import com.clipforge.ai.core.transition.renderers.RotationTransitionRenderer
import com.clipforge.ai.core.transition.renderers.SlideTransitionRenderer
import com.clipforge.ai.core.transition.renderers.WhipPanTransitionRenderer
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
    val FLIP_LEFT = TransitionId("flip_left")
    val FLIP_RIGHT = TransitionId("flip_right")
    val FLIP_UP = TransitionId("flip_up")
    val FLIP_DOWN = TransitionId("flip_down")
    val WHIP_PAN_LEFT = TransitionId("whip_pan_left")
    val WHIP_PAN_RIGHT = TransitionId("whip_pan_right")
    val WHIP_PAN_UP = TransitionId("whip_pan_up")
    val WHIP_PAN_DOWN = TransitionId("whip_pan_down")
    val MOTION_BLUR_LEFT = TransitionId("motion_blur_left")
    val MOTION_BLUR_RIGHT = TransitionId("motion_blur_right")
    val MOTION_BLUR_UP = TransitionId("motion_blur_up")
    val MOTION_BLUR_DOWN = TransitionId("motion_blur_down")

    /**
     * Maps a persisted [TransitionType] to its framework [TransitionId], or null if that type
     * is not yet exportable (falls back to a plain cut in export, as today).
     */
    fun idFor(type: TransitionType): TransitionId? = when (type) {
        TransitionType.DISSOLVE, TransitionType.CROSS_DISSOLVE -> DISSOLVE
        TransitionType.FADE, TransitionType.FADE_BLACK -> FADE_BLACK
        TransitionType.FADE_WHITE -> FADE_WHITE
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
        TransitionType.FLIP_LEFT -> FLIP_LEFT
        TransitionType.FLIP_RIGHT -> FLIP_RIGHT
        TransitionType.FLIP_UP -> FLIP_UP
        TransitionType.FLIP_DOWN -> FLIP_DOWN
        TransitionType.WHIP_PAN_LEFT -> WHIP_PAN_LEFT
        TransitionType.WHIP_PAN_RIGHT -> WHIP_PAN_RIGHT
        TransitionType.WHIP_PAN_UP -> WHIP_PAN_UP
        TransitionType.WHIP_PAN_DOWN -> WHIP_PAN_DOWN
        TransitionType.MOTION_BLUR_LEFT -> MOTION_BLUR_LEFT
        TransitionType.MOTION_BLUR_RIGHT -> MOTION_BLUR_RIGHT
        TransitionType.MOTION_BLUR_UP -> MOTION_BLUR_UP
        TransitionType.MOTION_BLUR_DOWN -> MOTION_BLUR_DOWN
        else -> null
    }

    /** Idempotent: registers every built-in family into [TransitionRegistry]. */
    fun registerBuiltIns(registry: TransitionRegistry = TransitionRegistry) {
        val crossfade = CrossfadeTransitionRenderer()
        val dip = DipToColorTransitionRenderer()
        val slide = SlideTransitionRenderer()
        val push = PushTransitionRenderer()
        val zoom = ZoomTransitionRenderer()
        val rotation = RotationTransitionRenderer()
        val cube = CubeTransitionRenderer()
        val flip = FlipTransitionRenderer()
        val whip = WhipPanTransitionRenderer()
        val motionBlur = MotionBlurTransitionRenderer()

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
        val sSlide = "Preparing slide transition..."
        val sPush = "Preparing push transition..."
        val sZoom = "Preparing zoom transition..."
        val sRotation = "Preparing rotation transition..."
        val sCube = "Preparing cube transition..."
        val sFlip = "Preparing flip transition..."
        val sWhip = "Preparing whip pan transition..."
        val sMotionBlur = "Preparing motion blur transition..."

        reg(DISSOLVE, "Dissolve", TransitionCategory.DISSOLVE, TimingModel.Overlap, crossfade, sDissolve)
        reg(FADE_BLACK, "Fade Black", TransitionCategory.FADE, TimingModel.SequentialDip, dip, sFade)
        reg(FADE_WHITE, "Fade White", TransitionCategory.FADE, TimingModel.SequentialDip, dip, sFade)
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
        reg(FLIP_LEFT, "Flip Left", TransitionCategory.THREE_D, TimingModel.Overlap, flip, sFlip)
        reg(FLIP_RIGHT, "Flip Right", TransitionCategory.THREE_D, TimingModel.Overlap, flip, sFlip)
        reg(FLIP_UP, "Flip Up", TransitionCategory.THREE_D, TimingModel.Overlap, flip, sFlip)
        reg(FLIP_DOWN, "Flip Down", TransitionCategory.THREE_D, TimingModel.Overlap, flip, sFlip)
        reg(WHIP_PAN_LEFT, "Whip Pan Left", TransitionCategory.BLUR, TimingModel.Overlap, whip, sWhip, Easing.ExpoOut)
        reg(WHIP_PAN_RIGHT, "Whip Pan Right", TransitionCategory.BLUR, TimingModel.Overlap, whip, sWhip, Easing.ExpoOut)
        reg(WHIP_PAN_UP, "Whip Pan Up", TransitionCategory.BLUR, TimingModel.Overlap, whip, sWhip, Easing.ExpoOut)
        reg(WHIP_PAN_DOWN, "Whip Pan Down", TransitionCategory.BLUR, TimingModel.Overlap, whip, sWhip, Easing.ExpoOut)
        reg(MOTION_BLUR_LEFT, "Motion Blur Left", TransitionCategory.BLUR, TimingModel.Overlap, motionBlur, sMotionBlur)
        reg(MOTION_BLUR_RIGHT, "Motion Blur Right", TransitionCategory.BLUR, TimingModel.Overlap, motionBlur, sMotionBlur)
        reg(MOTION_BLUR_UP, "Motion Blur Up", TransitionCategory.BLUR, TimingModel.Overlap, motionBlur, sMotionBlur)
        reg(MOTION_BLUR_DOWN, "Motion Blur Down", TransitionCategory.BLUR, TimingModel.Overlap, motionBlur, sMotionBlur)
    }
}
