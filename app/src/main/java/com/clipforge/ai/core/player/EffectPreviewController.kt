@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.player

import android.util.Log
import androidx.media3.effect.GlEffect
import androidx.media3.exoplayer.ExoPlayer
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.ExportEffectRegistry
import com.clipforge.ai.core.effects.LiveParams
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

const val PREVIEW_EFFECTS_ENABLED = true

class EffectPreviewController(
    player: ExoPlayer,
    private val repository: EffectRepository,
    private val scope: CoroutineScope,
    private val registry: EffectRegistry = ExportEffectRegistry.registry,
    private val logger: (String) -> Unit = { Log.d(TAG, it) },
    private val videoEffectsApplier: (List<GlEffect>) -> Unit = { effects -> player.setVideoEffects(effects) }
) {
    private var boundProjectId: String? = null
    private var observeJob: Job? = null
    private var pendingApplyJob: Job? = null
    private var latestEffects: List<EffectItem> = emptyList()
    private var currentStructuralKeys: List<EffectPreviewPlan.StructuralKey> = emptyList()
    private var liveParamsByItemId: Map<String, LiveParams> = emptyMap()
    private val pendingLiveValues = linkedMapOf<String, MutableMap<String, Float>>()
    private var animationDraftClipId: String? = null
    private var animationDraftItems: List<EffectItem> = emptyList()
    private var suspendDepth = 0
    private var disabledReason: String? = null
    private var released = false

    /**
     * Monotonic token identifying the most recently requested apply. Every operation that changes
     * what should be on screen (a new structural schedule, any immediate apply, a suspend, a rebind,
     * or release) bumps it. A debounced structural apply captures the token at schedule time and
     * runs only if it is still current when its delay elapses, so a superseded apply can never land
     * out of order — independent of how the [scope] dispatcher schedules the resumption.
     */
    private var applyGeneration = 0

    fun bind(projectId: String) {
        if (released || boundProjectId == projectId) return
        boundProjectId = projectId
        observeJob?.cancel()
        pendingApplyJob?.cancel()
        applyGeneration++
        latestEffects = emptyList()
        currentStructuralKeys = emptyList()
        liveParamsByItemId = emptyMap()
        pendingLiveValues.clear()
        animationDraftClipId = null
        animationDraftItems = emptyList()
        logger("EFFECT_PREVIEW CREATE projectId=$projectId")

        if (!PREVIEW_EFFECTS_ENABLED) {
            disabledReason = "kill_switch"
            logger("EFFECT_PREVIEW_DISABLED reason=kill_switch")
            return
        }

        observeJob = scope.launch {
            repository.observeEffectsForProject(projectId).collectLatest { effects ->
                latestEffects = effects
                scheduleStructuralApply()
            }
        }
    }

    fun setParam(effectItemId: String, key: String, value: Float) {
        if (released || disabledReason != null) return
        pendingLiveValues.getOrPut(effectItemId) { linkedMapOf() }[key] = value
        val liveParams = liveParamsByItemId[effectItemId]
        if (liveParams != null) {
            runCatching { liveParams.set(key, value) }
                .onFailure { disable("setParam:${it.message}", it) }
            return
        }
        applyNow(force = true)
    }

    /** Begins a preview-only override for [clipId]'s clip-scoped animation rows. No repository write. */
    fun beginAnimationDraft(clipId: String) {
        if (released) return
        animationDraftClipId = clipId
        animationDraftItems = emptyList()
        logger("EFFECT_PREVIEW_DRAFT_BEGIN clipId=$clipId")
        applyNow(force = true)
    }

    /** Replaces the live draft preview items for [clipId]. No-op if no draft is active for it. */
    fun updateAnimationDraftItems(clipId: String, items: List<EffectItem>) {
        if (released || animationDraftClipId != clipId) return
        animationDraftItems = items
        applyNow(force = true)
    }

    /** Ends the draft override and reverts preview to the persisted [latestEffects]. */
    fun endAnimationDraft() {
        if (released || animationDraftClipId == null) return
        animationDraftClipId = null
        animationDraftItems = emptyList()
        logger("EFFECT_PREVIEW_DRAFT_END")
        applyNow(force = true)
    }

    fun suspendEffects() {
        if (released) return
        suspendDepth++
        logger("EFFECT_PREVIEW SUSPEND depth=$suspendDepth")
        if (suspendDepth == 1) {
            pendingApplyJob?.cancel()
            applyGeneration++
            setVideoEffects(emptyList(), "suspend")
        }
    }

    fun resumeEffects() {
        if (released || suspendDepth == 0) return
        suspendDepth--
        logger("EFFECT_PREVIEW RESUME depth=$suspendDepth")
        if (suspendDepth == 0) {
            applyNow(force = true)
        }
    }

    fun release() {
        if (released) return
        released = true
        logger("EFFECT_PREVIEW RELEASE projectId=$boundProjectId")
        observeJob?.cancel()
        pendingApplyJob?.cancel()
        applyGeneration++
        observeJob = null
        pendingApplyJob = null
        liveParamsByItemId = emptyMap()
        currentStructuralKeys = emptyList()
    }

    private fun scheduleStructuralApply() {
        if (released || disabledReason != null || suspendDepth > 0) return
        val candidate = buildPlan()
        if (candidate.structuralKeys == currentStructuralKeys) {
            return
        }
        pendingApplyJob?.cancel()
        val scheduledGeneration = ++applyGeneration
        pendingApplyJob = scope.launch {
            delay(STRUCTURAL_DEBOUNCE_MS)
            // Drop this apply if anything superseded it while the debounce was pending. Relying on
            // job cancellation alone is not enough: once the delay elapses the resumption may already
            // be queued, so a later operation must be able to invalidate it by generation, not race.
            if (scheduledGeneration != applyGeneration) return@launch
            applyNow(force = false)
        }
    }

    private fun applyNow(force: Boolean) {
        if (released || disabledReason != null || suspendDepth > 0) return
        pendingApplyJob?.cancel()
        // Supersede any debounced apply still pending: this immediate apply is now the latest intent.
        applyGeneration++
        val plan = buildPlan()
        if (!force && plan.structuralKeys == currentStructuralKeys) return
        if (plan.attachments.isEmpty() && currentStructuralKeys.isEmpty()) {
            liveParamsByItemId = emptyMap()
            return
        }
        setVideoEffects(plan.effects, "apply")
        currentStructuralKeys = plan.structuralKeys
        liveParamsByItemId = plan.liveParamsByItemId
        logger("EFFECT_PREVIEW APPLY count=${plan.effects.size} structuralCount=${plan.structuralKeys.size}")
    }

    private fun buildPlan(): EffectPreviewPlan.Result {
        val effects = animationDraftClipId?.let { clipId ->
            EffectPreviewPlan.applyAnimationDraftOverride(latestEffects, clipId, animationDraftItems)
        } ?: latestEffects
        return EffectPreviewPlan.build(
            effects = effects,
            registry = registry,
            pendingLiveValues = pendingLiveValues,
            logger = logger
        )
    }

    private fun setVideoEffects(effects: List<GlEffect>, reason: String) {
        if (disabledReason != null) return
        runCatching { videoEffectsApplier(effects) }
            .onFailure { disable("$reason:${it.message}", it) }
    }

    private fun disable(reason: String, error: Throwable) {
        if (disabledReason != null) return
        disabledReason = reason
        Log.w(TAG, "EFFECT_PREVIEW_DISABLED reason=$reason", error)
        logger("EFFECT_PREVIEW_DISABLED reason=$reason")
        runCatching { videoEffectsApplier(emptyList()) }
    }

    private companion object {
        const val TAG = "EffectPreviewController"
        const val STRUCTURAL_DEBOUNCE_MS = 250L
    }
}
