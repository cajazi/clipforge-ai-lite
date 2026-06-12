@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.player

import androidx.media3.effect.GlEffect
import com.clipforge.ai.core.effects.EffectParamResolver
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.LiveParams
import com.clipforge.ai.core.effects.ParamProvider
import com.clipforge.ai.domain.model.EffectItem

const val MAX_PREVIEW_EFFECTS = 3

object EffectPreviewPlan {
    data class StructuralKey(
        val id: String,
        val effectId: String,
        val startMs: Long,
        val endMs: Long,
        val zOrder: Int,
        val keyframedParamsSignature: String
    )

    data class Attachment(
        val structuralKey: StructuralKey,
        val windowStartUs: Long,
        val windowEndUs: Long,
        val provider: ParamProvider,
        val liveParams: LiveParams?,
        val effect: GlEffect
    )

    data class Result(
        val attachments: List<Attachment>
    ) {
        val structuralKeys: List<StructuralKey> get() = attachments.map { it.structuralKey }
        val effects: List<GlEffect> get() = attachments.map { it.effect }
        val liveParamsByItemId: Map<String, LiveParams> =
            attachments.mapNotNull { attachment ->
                attachment.liveParams?.let { attachment.structuralKey.id to it }
            }.toMap()
    }

    fun build(
        effects: List<EffectItem>,
        registry: EffectRegistry,
        pendingLiveValues: Map<String, Map<String, Float>> = emptyMap(),
        logger: (String) -> Unit = {}
    ): Result {
        val sorted = effects
            .filter { item ->
                if (item.scope == EffectScope.GLOBAL) {
                    true
                } else {
                    logger("EFFECT_PREVIEW_SKIP_SCOPE id=${item.id} scope=${item.scope}")
                    false
                }
            }
            .sortedWith(compareBy<EffectItem> { it.zOrder }.thenBy { it.startMs }.thenBy { it.id })

        val capped = if (sorted.size > MAX_PREVIEW_EFFECTS) {
            logger("EFFECT_PREVIEW_CAPPED requested=${sorted.size} applied=$MAX_PREVIEW_EFFECTS")
            sorted.take(MAX_PREVIEW_EFFECTS)
        } else {
            sorted
        }

        val attachments = capped.mapNotNull { item ->
            val registration = registry.get(item.effectId)
            if (registration == null) {
                logger("EFFECT_PREVIEW_UNKNOWN_ID id=${item.id} effectId=${item.effectId}")
                return@mapNotNull null
            }

            val windowStartUs = item.startMs * 1000L
            val windowEndUs = item.endMs * 1000L
            val resolved = EffectParamResolver.resolve(
                itemId = item.id,
                storedParams = item.params,
                specs = registration.descriptor.paramSpecs,
                windowStartUs = windowStartUs,
                constantMode = EffectParamResolver.ConstantMode.Live,
                pendingLiveValues = pendingLiveValues[item.id].orEmpty(),
                logPrefix = "EFFECT_PREVIEW",
                logger = logger
            )
            val structuralKey = StructuralKey(
                id = item.id,
                effectId = item.effectId,
                startMs = item.startMs,
                endMs = item.endMs,
                zOrder = item.zOrder,
                keyframedParamsSignature = resolved.keyframedParamsSignature
            )
            Attachment(
                structuralKey = structuralKey,
                windowStartUs = windowStartUs,
                windowEndUs = windowEndUs,
                provider = resolved.provider,
                liveParams = resolved.liveParams,
                effect = registration.factory.create(windowStartUs, windowEndUs, resolved.provider)
            )
        }

        return Result(attachments)
    }
}
