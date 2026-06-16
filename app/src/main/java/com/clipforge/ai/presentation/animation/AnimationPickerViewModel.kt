package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationEffectItemFactory
import com.clipforge.ai.core.animation.AnimationPreset
import com.clipforge.ai.core.animation.AnimationPresetIds
import com.clipforge.ai.core.animation.AnimationPresetType
import com.clipforge.ai.core.animation.AnimationPresets
import com.clipforge.ai.core.animation.AnimationProperty
import com.clipforge.ai.core.animation.AnimationTargetType
import com.clipforge.ai.core.animation.AnimationTrack
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.domain.history.HistoryRegistry
import com.clipforge.ai.domain.history.ReplaceGlobalAnimationCommand
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository

class AnimationPickerViewModel(
    private val projectId: String,
    private val repository: EffectRepository,
    private val historyRegistry: HistoryRegistry
) {
    suspend fun applyPreset(presetId: String, totalDurationMs: Long) {
        val effect = buildEffectForPreset(
            projectId = projectId,
            presetId = presetId,
            totalDurationMs = totalDurationMs
        )
        historyRegistry.execute(
            ReplaceGlobalAnimationCommand(
                repository = repository,
                projectId = projectId,
                nextEffect = effect
            )
        )
    }

    suspend fun removeAnimation() {
        historyRegistry.execute(
            ReplaceGlobalAnimationCommand(
                repository = repository,
                projectId = projectId,
                nextEffect = null
            )
        )
    }

    companion object {
        fun deterministicEffectId(projectId: String): String = "anim-global-$projectId"

        fun buildEffectForPreset(
            projectId: String,
            presetId: String,
            totalDurationMs: Long
        ): EffectItem {
            require(totalDurationMs > 0L) { "Animation requires a positive project duration" }
            val preset = requireNotNull(AnimationPresets.get(presetId)) { "Unknown animation preset '$presetId'" }
            val track = preset.toProjectOutputTrack(totalDurationMs)

            // The persisted row captures the project duration at apply time. If clips are added,
            // removed, or retimed later, the user must re-apply Animation to regenerate the window.
            return AnimationEffectItemFactory.createTransformAnimationEffectItem(
                id = deterministicEffectId(projectId),
                projectId = projectId,
                track = track,
                startMs = 0L,
                endMs = totalDurationMs
            )
        }
    }
}

private fun AnimationPreset.toProjectOutputTrack(totalDurationMs: Long): AnimationTrack {
    val baseTrack = toTrack(AnimationTargetType.VIDEO)
    val windowUs = totalDurationMs * 1_000L
    val projectProperties = when (presetType) {
        AnimationPresetType.IN -> baseTrack.properties
        AnimationPresetType.OUT -> baseTrack.properties.map { it.shiftToWindowEnd(defaultDurationUs, windowUs) }
        AnimationPresetType.COMBO -> {
            if (id == AnimationPresetIds.SLOW_ZOOM) {
                baseTrack.properties.map { it.stretchEndTo(windowUs) }
            } else {
                baseTrack.properties
            }
        }
        AnimationPresetType.LOOP -> baseTrack.properties.map { it.tileLoop(defaultDurationUs, windowUs) }
    }
    return baseTrack.copy(properties = projectProperties)
}

private fun AnimationProperty.shiftToWindowEnd(durationUs: Long, windowUs: Long): AnimationProperty {
    val offsetUs = (windowUs - durationUs).coerceAtLeast(0L)
    return copy(keyframes = keyframes.map { it.copy(timeUs = (it.timeUs + offsetUs).coerceAtMost(windowUs)) })
}

private fun AnimationProperty.stretchEndTo(windowUs: Long): AnimationProperty {
    val lastUs = keyframes.last().timeUs
    if (lastUs <= 0L || lastUs == windowUs) return this
    return copy(
        keyframes = keyframes.map { frame ->
            frame.copy(timeUs = ((frame.timeUs.toDouble() / lastUs.toDouble()) * windowUs).toLong())
        }
    )
}

private fun AnimationProperty.tileLoop(cycleUs: Long, windowUs: Long): AnimationProperty {
    if (cycleUs <= 0L || windowUs <= cycleUs) return this
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
        keyframes = if (last.timeUs == windowUs) {
            tiled
        } else {
            tiled + last.copy(timeUs = windowUs)
        }
    )
}
