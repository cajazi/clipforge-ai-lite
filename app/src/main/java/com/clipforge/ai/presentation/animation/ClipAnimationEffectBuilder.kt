package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationEffectItemFactory
import com.clipforge.ai.core.animation.AnimationPresetIds
import com.clipforge.ai.core.animation.AnimationPresetType
import com.clipforge.ai.core.animation.AnimationPresets
import com.clipforge.ai.core.animation.AnimationProperty
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.animation.AnimationTargetType
import com.clipforge.ai.core.animation.AnimationTrack
import com.clipforge.ai.core.animation.AnimationWindowResolver
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue

/** Pure construction of clip-scoped transform animation [EffectItem]s. No repository/history access. */
object ClipAnimationEffectBuilder {

    fun buildEffect(
        projectId: String,
        clipId: String,
        presetId: String,
        role: AnimationRole,
        requestedDurationMs: Long,
        clipWindow: ClipAnimationWindowInput
    ): EffectItem? {
        val preset = AnimationPresets.get(presetId) ?: return null
        val resolvedWindow = AnimationWindowResolver.resolve(
            clipStartMs = clipWindow.startMs,
            clipEndMs = clipWindow.endMs,
            requestedDurationMs = requestedDurationMs,
            role = role,
            incomingTransitionDurationMs = clipWindow.incomingTransitionDurationMs,
            outgoingTransitionDurationMs = clipWindow.outgoingTransitionDurationMs
        ) ?: return null
        val track = preset.toClipOutputTrack(role, requestedDurationMs)

        return AnimationEffectItemFactory.createTransformAnimationEffectItem(
            id = AnimationEffectId.of(clipId, role),
            projectId = projectId,
            track = track,
            startMs = resolvedWindow.startMs,
            endMs = resolvedWindow.endMs,
            scope = EffectScope.CLIP
        )
    }

    fun rescale(
        existing: EffectItem,
        role: AnimationRole,
        requestedDurationMs: Long,
        clipWindow: ClipAnimationWindowInput
    ): EffectItem? {
        val resolvedWindow = AnimationWindowResolver.resolve(
            clipStartMs = clipWindow.startMs,
            clipEndMs = clipWindow.endMs,
            requestedDurationMs = requestedDurationMs,
            role = role,
            incomingTransitionDurationMs = clipWindow.incomingTransitionDurationMs,
            outgoingTransitionDurationMs = clipWindow.outgoingTransitionDurationMs
        ) ?: return null
        return existing.copy(
            startMs = resolvedWindow.startMs,
            endMs = resolvedWindow.endMs,
            params = existing.params.scaleKeyframesTo(requestedDurationMs)
        )
    }
}

private fun Map<String, EffectParamValue>.scaleKeyframesTo(durationMs: Long): Map<String, EffectParamValue> {
    val durationUs = durationMs.coerceAtLeast(MIN_CLIP_ANIMATION_DURATION_MS) * 1_000L
    val currentMaxUs = values
        .mapNotNull { (it as? EffectParamValue.Keyframed)?.frames?.maxOfOrNull { frame -> frame.timeUs } }
        .maxOrNull()
        ?: return this
    if (currentMaxUs <= 0L || currentMaxUs == durationUs) return this
    return mapValues { (_, value) ->
        when (value) {
            is EffectParamValue.Constant -> value
            is EffectParamValue.Keyframed -> value.copy(
                frames = value.frames.map { frame ->
                    frame.copy(timeUs = ((frame.timeUs.toDouble() / currentMaxUs.toDouble()) * durationUs).toLong())
                }
            )
        }
    }
}

private fun com.clipforge.ai.core.animation.AnimationPreset.toClipOutputTrack(
    role: AnimationRole,
    requestedDurationMs: Long
): AnimationTrack {
    val baseTrack = toTrack(AnimationTargetType.VIDEO)
    val requestedUs = requestedDurationMs.coerceAtLeast(MIN_CLIP_ANIMATION_DURATION_MS) * 1_000L
    val properties = when (role) {
        AnimationRole.IN -> baseTrack.properties.map { it.scaleToDuration(requestedUs) }
        AnimationRole.OUT -> baseTrack.properties.map { it.scaleToDuration(requestedUs) }
        AnimationRole.COMBO -> {
            if (id == AnimationPresetIds.SLOW_ZOOM || presetType == AnimationPresetType.COMBO) {
                baseTrack.properties.map { it.scaleToDuration(requestedUs) }
            } else {
                baseTrack.properties.map { it.tileLoop(defaultDurationUs, requestedUs) }
            }
        }
    }
    return baseTrack.copy(properties = properties)
}

private fun AnimationProperty.scaleToDuration(durationUs: Long): AnimationProperty {
    val lastUs = keyframes.maxOfOrNull { it.timeUs } ?: return this
    if (lastUs <= 0L || lastUs == durationUs) return this
    return copy(
        keyframes = keyframes.map { frame ->
            frame.copy(timeUs = ((frame.timeUs.toDouble() / lastUs.toDouble()) * durationUs).toLong())
        }
    )
}

private fun AnimationProperty.tileLoop(cycleUs: Long, windowUs: Long): AnimationProperty {
    if (cycleUs <= 0L || windowUs <= cycleUs) return scaleToDuration(windowUs)
    val tiled = mutableListOf<Keyframe>()
    var cycleStartUs = 0L
    while (cycleStartUs < windowUs) {
        keyframes.forEachIndexed { index, frame ->
            if (cycleStartUs > 0L && index == 0) return@forEachIndexed
            val timeUs = cycleStartUs + frame.timeUs
            if (timeUs <= windowUs && (tiled.lastOrNull()?.timeUs ?: -1L) < timeUs) {
                tiled += frame.copy(timeUs = timeUs)
            }
        }
        cycleStartUs += cycleUs
    }
    if (tiled.isEmpty()) return this
    val last = tiled.last()
    return copy(
        keyframes = if (last.timeUs == windowUs) tiled else tiled + last.copy(timeUs = windowUs)
    )
}
