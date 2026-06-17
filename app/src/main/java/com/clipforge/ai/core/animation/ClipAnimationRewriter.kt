package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.model.EffectItem

object ClipAnimationRewriter {
    data class ClipWindow(
        val clipId: String,
        val startMs: Long,
        val endMs: Long,
        val incomingTransitionDurationMs: Long = 0L,
        val outgoingTransitionDurationMs: Long = 0L
    )

    fun deleteClipAnimations(effects: List<EffectItem>, clipId: String): List<EffectItem> =
        effects.filterNot { effect ->
            effect.isClipTransformAnimation() && AnimationEffectId.parse(effect.id)?.clipId == clipId
        }

    fun duplicateClipAnimations(
        effects: List<EffectItem>,
        sourceClipId: String,
        newClipId: String,
        windows: List<ClipWindow>
    ): List<EffectItem> {
        val copies = effects
            .filter { AnimationEffectId.parse(it.id)?.clipId == sourceClipId && it.isClipTransformAnimation() }
            .mapNotNull { effect ->
                val role = AnimationEffectId.parse(effect.id)?.role ?: return@mapNotNull null
                effect.copy(id = AnimationEffectId.of(newClipId, role))
            }
        return resolveWindows(effects + copies, windows)
    }

    fun resolveWindows(
        effects: List<EffectItem>,
        windows: List<ClipWindow>,
        minimumUsableDurationMs: Long = 1L
    ): List<EffectItem> {
        val windowsByClip = windows.associateBy { it.clipId }
        val grouped = effects.groupBy { AnimationEffectId.parse(it.id)?.clipId }
        val rewritten = mutableListOf<EffectItem>()
        grouped[null]?.let { rewritten += it }

        grouped
            .filterKeys { it != null }
            .forEach { (clipId, clipEffects) ->
                val window = windowsByClip[clipId]
                if (window == null) return@forEach
                val clipAnimations = clipEffects.filter { it.isClipTransformAnimation() }
                val others = clipEffects - clipAnimations.toSet()
                rewritten += others
                val byRole = clipAnimations.mapNotNull { effect ->
                    val role = effect.clipAnimationRole() ?: return@mapNotNull null
                    role to effect
                }.toMap()
                val inEffect = byRole[AnimationRole.IN]
                val outEffect = byRole[AnimationRole.OUT]
                if (inEffect != null && outEffect != null) {
                    val resolved = AnimationWindowResolver.resolveInOut(
                        clipStartMs = window.startMs,
                        clipEndMs = window.endMs,
                        requestedInDurationMs = inEffect.durationMs,
                        requestedOutDurationMs = outEffect.durationMs,
                        incomingTransitionDurationMs = window.incomingTransitionDurationMs,
                        outgoingTransitionDurationMs = window.outgoingTransitionDurationMs,
                        minimumUsableDurationMs = minimumUsableDurationMs
                    ).associateBy { it.role }
                    resolved[AnimationRole.IN]?.window?.let { rewritten += inEffect.withWindow(it) }
                    resolved[AnimationRole.OUT]?.window?.let { rewritten += outEffect.withWindow(it) }
                } else {
                    listOfNotNull(inEffect, outEffect).forEach { effect ->
                        val role = effect.clipAnimationRole() ?: return@forEach
                        AnimationWindowResolver.resolve(
                            clipStartMs = window.startMs,
                            clipEndMs = window.endMs,
                            requestedDurationMs = effect.durationMs,
                            role = role,
                            incomingTransitionDurationMs = window.incomingTransitionDurationMs,
                            outgoingTransitionDurationMs = window.outgoingTransitionDurationMs,
                            minimumUsableDurationMs = minimumUsableDurationMs
                        )?.let { rewritten += effect.withWindow(it) }
                    }
                }
                byRole[AnimationRole.COMBO]?.let { effect ->
                    AnimationWindowResolver.resolve(
                        clipStartMs = window.startMs,
                        clipEndMs = window.endMs,
                        requestedDurationMs = effect.durationMs,
                        role = AnimationRole.COMBO,
                        incomingTransitionDurationMs = window.incomingTransitionDurationMs,
                        outgoingTransitionDurationMs = window.outgoingTransitionDurationMs,
                        minimumUsableDurationMs = minimumUsableDurationMs
                    )?.let { rewritten += effect.withWindow(it) }
                }
            }
        return rewritten.sortedWith(compareBy<EffectItem> { it.zOrder }.thenBy { it.startMs }.thenBy { it.id })
    }

    private val EffectItem.durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    private fun EffectItem.withWindow(window: AnimationWindowResolver.Window): EffectItem =
        copy(startMs = window.startMs, endMs = window.endMs)

    private fun EffectItem.clipAnimationRole(): AnimationRole? =
        AnimationEffectId.parse(id)?.role?.takeIf { isClipTransformAnimation() }

    private fun EffectItem.isClipTransformAnimation(): Boolean =
        scope == EffectScope.CLIP && effectId == AnimationEffectRegistrations.TRANSFORM_ANIMATION
}
