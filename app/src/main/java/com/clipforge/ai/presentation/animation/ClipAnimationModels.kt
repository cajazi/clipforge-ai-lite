package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationPreset
import com.clipforge.ai.core.animation.AnimationPresetType
import com.clipforge.ai.core.animation.AnimationPresets
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.animation.AnimationWindowResolver
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue

data class ClipAnimationUiState(
    val selectedClipId: String?,
    val selectedRole: AnimationRole,
    val inAnimation: AnimationSummary?,
    val outAnimation: AnimationSummary?,
    val comboAnimation: AnimationSummary?,
    val availablePresets: List<AnimationPreset>,
    val requestedDurationMs: Long,
    val effectiveDurationMs: Long,
    val sessionSelectedPresetId: String?
)

data class AnimationSummary(
    val present: Boolean,
    val requestedDurationMs: Long,
    val effectiveDurationMs: Long,
    val windowStartMs: Long,
    val windowEndMs: Long
)

data class ClipAnimationWindowInput(
    val clipId: String,
    val startMs: Long,
    val endMs: Long,
    val incomingTransitionDurationMs: Long = 0L,
    val outgoingTransitionDurationMs: Long = 0L
) {
    val cleanDurationMs: Long
        get() = (endMs - outgoingTransitionDurationMs.coerceAtLeast(0L) -
            (startMs + incomingTransitionDurationMs.coerceAtLeast(0L))).coerceAtLeast(0L)
}

data class ClipAnimationMarkerState(
    val inMarker: AnimationSummary? = null,
    val outMarker: AnimationSummary? = null,
    val comboMarker: AnimationSummary? = null
)

fun buildClipAnimationUiState(
    selectedClipId: String?,
    selectedRole: AnimationRole,
    effects: List<EffectItem>,
    clipWindows: List<ClipAnimationWindowInput>,
    sessionSelectedPresetId: String?,
    inFlightDurationMs: Long?
): ClipAnimationUiState {
    val summaries = selectedClipId
        ?.let { clipId -> clipAnimationSummaries(effects, clipId) }
        .orEmpty()
    val roleSummary = summaries[selectedRole]
    val rolePresetType = selectedRole.toPresetType()
    val presets = AnimationPresets.byType(rolePresetType)
    val defaultDurationMs = presets.firstOrNull()?.defaultDurationMs() ?: MIN_CLIP_ANIMATION_DURATION_MS
    val requestedDurationMs = inFlightDurationMs
        ?: roleSummary?.requestedDurationMs
        ?: defaultDurationMs
    val effectiveDurationMs = selectedClipId
        ?.let { clipId ->
            val window = clipWindows.firstOrNull { it.clipId == clipId }
            effectiveDurationFor(
                role = selectedRole,
                requestedDurationMs = requestedDurationMs,
                selectedClipId = clipId,
                effects = effects,
                clipWindow = window
            )
        }
        ?: 0L

    return ClipAnimationUiState(
        selectedClipId = selectedClipId,
        selectedRole = selectedRole,
        inAnimation = summaries[AnimationRole.IN],
        outAnimation = summaries[AnimationRole.OUT],
        comboAnimation = summaries[AnimationRole.COMBO],
        availablePresets = presets,
        requestedDurationMs = requestedDurationMs,
        effectiveDurationMs = effectiveDurationMs,
        sessionSelectedPresetId = sessionSelectedPresetId
    )
}

fun buildClipAnimationMarkerMap(effects: List<EffectItem>): Map<String, ClipAnimationMarkerState> {
    val grouped = effects
        .filter { it.isClipTransformAnimation() }
        .mapNotNull { effect ->
            val parsed = AnimationEffectId.parse(effect.id) ?: return@mapNotNull null
            parsed.clipId to (parsed.role to effect.toAnimationSummary())
        }
        .groupBy({ it.first }, { it.second })

    return grouped.mapValues { (_, values) ->
        val byRole = values.toMap()
        ClipAnimationMarkerState(
            inMarker = byRole[AnimationRole.IN],
            outMarker = byRole[AnimationRole.OUT],
            comboMarker = byRole[AnimationRole.COMBO]
        )
    }
}

fun clipAnimationSummaries(
    effects: List<EffectItem>,
    clipId: String
): Map<AnimationRole, AnimationSummary> =
    effects
        .filter { it.isClipTransformAnimation() }
        .mapNotNull { effect ->
            val parsed = AnimationEffectId.parse(effect.id) ?: return@mapNotNull null
            if (parsed.clipId != clipId) return@mapNotNull null
            parsed.role to effect.toAnimationSummary()
        }
        .toMap()

fun AnimationRole.toPresetType(): AnimationPresetType = when (this) {
    AnimationRole.IN -> AnimationPresetType.IN
    AnimationRole.OUT -> AnimationPresetType.OUT
    AnimationRole.COMBO -> AnimationPresetType.COMBO
}

fun AnimationPreset.defaultDurationMs(): Long =
    (defaultDurationUs / 1_000L).coerceAtLeast(MIN_CLIP_ANIMATION_DURATION_MS)

fun maxDurationForRole(
    role: AnimationRole,
    selectedClipId: String?,
    effects: List<EffectItem>,
    clipWindow: ClipAnimationWindowInput?
): Long {
    val cleanDuration = clipWindow?.cleanDurationMs ?: 0L
    if (cleanDuration <= 0L || selectedClipId == null) return MIN_CLIP_ANIMATION_DURATION_MS
    val summaries = clipAnimationSummaries(effects, selectedClipId)
    val reserved = when (role) {
        AnimationRole.IN -> summaries[AnimationRole.OUT]?.effectiveDurationMs ?: 0L
        AnimationRole.OUT -> summaries[AnimationRole.IN]?.effectiveDurationMs ?: 0L
        AnimationRole.COMBO -> 0L
    }
    return (cleanDuration - reserved)
        .coerceAtLeast(MIN_CLIP_ANIMATION_DURATION_MS)
}

fun effectiveDurationFor(
    role: AnimationRole,
    requestedDurationMs: Long,
    selectedClipId: String,
    effects: List<EffectItem>,
    clipWindow: ClipAnimationWindowInput?
): Long {
    val window = clipWindow ?: return 0L
    val summaries = clipAnimationSummaries(effects, selectedClipId)
    if (role == AnimationRole.IN && summaries[AnimationRole.OUT] != null) {
        return AnimationWindowResolver.resolveInOut(
            clipStartMs = window.startMs,
            clipEndMs = window.endMs,
            requestedInDurationMs = requestedDurationMs,
            requestedOutDurationMs = summaries[AnimationRole.OUT]?.requestedDurationMs ?: 0L,
            incomingTransitionDurationMs = window.incomingTransitionDurationMs,
            outgoingTransitionDurationMs = window.outgoingTransitionDurationMs
        ).firstOrNull { it.role == AnimationRole.IN }?.window?.durationMs ?: 0L
    }
    if (role == AnimationRole.OUT && summaries[AnimationRole.IN] != null) {
        return AnimationWindowResolver.resolveInOut(
            clipStartMs = window.startMs,
            clipEndMs = window.endMs,
            requestedInDurationMs = summaries[AnimationRole.IN]?.requestedDurationMs ?: 0L,
            requestedOutDurationMs = requestedDurationMs,
            incomingTransitionDurationMs = window.incomingTransitionDurationMs,
            outgoingTransitionDurationMs = window.outgoingTransitionDurationMs
        ).firstOrNull { it.role == AnimationRole.OUT }?.window?.durationMs ?: 0L
    }
    return AnimationWindowResolver.resolve(
        clipStartMs = window.startMs,
        clipEndMs = window.endMs,
        requestedDurationMs = requestedDurationMs,
        role = role,
        incomingTransitionDurationMs = window.incomingTransitionDurationMs,
        outgoingTransitionDurationMs = window.outgoingTransitionDurationMs
    )?.durationMs ?: 0L
}

private fun EffectItem.toAnimationSummary(): AnimationSummary =
    AnimationSummary(
        present = true,
        requestedDurationMs = requestedDurationMsFromParams().takeIf { it > 0L } ?: (endMs - startMs).coerceAtLeast(0L),
        effectiveDurationMs = (endMs - startMs).coerceAtLeast(0L),
        windowStartMs = startMs,
        windowEndMs = endMs
    )

private fun EffectItem.requestedDurationMsFromParams(): Long =
    params.values
        .asSequence()
        .mapNotNull { value -> (value as? EffectParamValue.Keyframed)?.frames?.maxOfOrNull { it.timeUs } }
        .maxOrNull()
        ?.let { it / 1_000L }
        ?: 0L

private fun EffectItem.isClipTransformAnimation(): Boolean =
    projectId.isNotBlank() &&
        scope == EffectScope.CLIP &&
        effectId == AnimationEffectRegistrations.TRANSFORM_ANIMATION &&
        AnimationEffectId.parse(id) != null

const val MIN_CLIP_ANIMATION_DURATION_MS = 100L
const val CLIP_ANIMATION_DURATION_STEP_MS = 100L
