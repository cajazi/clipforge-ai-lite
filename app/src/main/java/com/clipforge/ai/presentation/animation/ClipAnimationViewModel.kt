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
import com.clipforge.ai.domain.history.ApplyComboAnimationCommand
import com.clipforge.ai.domain.history.ApplyInAnimationCommand
import com.clipforge.ai.domain.history.ApplyOutAnimationCommand
import com.clipforge.ai.domain.history.HistoryRegistry
import com.clipforge.ai.domain.history.RemoveAnimationCommand
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ClipAnimationTransientState(
    val selectedRole: AnimationRole = AnimationRole.IN,
    val sessionSelectedPresetId: String? = null,
    val inFlightDurationMs: Long? = null,
    val panelOpen: Boolean = false
)

class ClipAnimationViewModel(
    private val projectId: String,
    private val repository: EffectRepository,
    private val historyRegistry: HistoryRegistry
) {
    private val mutableState = MutableStateFlow(ClipAnimationTransientState())
    val state: StateFlow<ClipAnimationTransientState> = mutableState.asStateFlow()

    fun openPanel() {
        mutableState.update { it.copy(panelOpen = true) }
    }

    fun closePanel() {
        mutableState.update { it.copy(panelOpen = false, inFlightDurationMs = null) }
    }

    fun selectRole(role: AnimationRole) {
        mutableState.update { it.copy(selectedRole = role, inFlightDurationMs = null) }
    }

    fun setInFlightDuration(durationMs: Long) {
        mutableState.update { it.copy(inFlightDurationMs = durationMs.roundToStep()) }
    }

    suspend fun applyPreset(
        clipId: String,
        presetId: String,
        role: AnimationRole,
        durationMs: Long,
        clipWindow: ClipAnimationWindowInput
    ) {
        val effect = buildClipEffect(
            clipId = clipId,
            presetId = presetId,
            role = role,
            requestedDurationMs = durationMs.roundToStep(),
            clipWindow = clipWindow
        )
        historyRegistry.execute(effect.toApplyCommand(clipId, role))
        mutableState.update {
            it.copy(
                selectedRole = role,
                sessionSelectedPresetId = presetId,
                inFlightDurationMs = null
            )
        }
    }

    suspend fun commitDuration(
        clipId: String,
        role: AnimationRole,
        durationMs: Long,
        clipWindow: ClipAnimationWindowInput
    ) {
        val current = repository.getEffectsForProject(projectId)
            .firstOrNull { it.id == AnimationEffectId.of(clipId, role) }
            ?: return
        val requestedDurationMs = durationMs.roundToStep()
        val resolvedWindow = AnimationWindowResolver.resolve(
            clipStartMs = clipWindow.startMs,
            clipEndMs = clipWindow.endMs,
            requestedDurationMs = requestedDurationMs,
            role = role,
            incomingTransitionDurationMs = clipWindow.incomingTransitionDurationMs,
            outgoingTransitionDurationMs = clipWindow.outgoingTransitionDurationMs
        ) ?: return
        val next = current.copy(
            startMs = resolvedWindow.startMs,
            endMs = resolvedWindow.endMs,
            params = current.params.scaleKeyframesTo(requestedDurationMs)
        )
        historyRegistry.execute(next.toApplyCommand(clipId, role))
        mutableState.update { it.copy(inFlightDurationMs = null) }
    }

    suspend fun removeAnimation(clipId: String, role: AnimationRole) {
        historyRegistry.execute(
            RemoveAnimationCommand(
                repository = repository,
                projectId = projectId,
                clipId = clipId,
                role = role
            )
        )
        mutableState.update { it.copy(sessionSelectedPresetId = null, inFlightDurationMs = null) }
    }

    private fun buildClipEffect(
        clipId: String,
        presetId: String,
        role: AnimationRole,
        requestedDurationMs: Long,
        clipWindow: ClipAnimationWindowInput
    ): EffectItem {
        val preset = requireNotNull(AnimationPresets.get(presetId)) { "Unknown animation preset '$presetId'" }
        val resolvedWindow = AnimationWindowResolver.resolve(
            clipStartMs = clipWindow.startMs,
            clipEndMs = clipWindow.endMs,
            requestedDurationMs = requestedDurationMs,
            role = role,
            incomingTransitionDurationMs = clipWindow.incomingTransitionDurationMs,
            outgoingTransitionDurationMs = clipWindow.outgoingTransitionDurationMs
        ) ?: error("Animation window is too short for role=$role clipId=$clipId")
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

    private fun EffectItem.toApplyCommand(clipId: String, role: AnimationRole) = when (role) {
        AnimationRole.IN -> ApplyInAnimationCommand(repository, projectId, clipId, this)
        AnimationRole.OUT -> ApplyOutAnimationCommand(repository, projectId, clipId, this)
        AnimationRole.COMBO -> ApplyComboAnimationCommand(repository, projectId, clipId, this)
    }

private fun Long.roundToStep(): Long =
        ((this + CLIP_ANIMATION_DURATION_STEP_MS / 2L) / CLIP_ANIMATION_DURATION_STEP_MS)
            .coerceAtLeast(1L) * CLIP_ANIMATION_DURATION_STEP_MS
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
