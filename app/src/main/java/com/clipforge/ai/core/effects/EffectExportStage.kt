@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.effects

import androidx.media3.effect.GlEffect
import com.clipforge.ai.core.overlay.TimelineToCompositionTimeMap
import com.clipforge.ai.domain.model.EffectItem

object ExportEffectRegistry {
    val registry = EffectRegistry()
}

object EffectExportStage {
    data class Attachment(
        val effectId: String,
        val zOrder: Int,
        val windowStartUs: Long,
        val windowEndUs: Long,
        val provider: ParamProvider,
        val effect: GlEffect
    )

    data class Result(
        val attachments: List<Attachment>
    ) {
        val effects: List<GlEffect> get() = attachments.map { it.effect }
    }

    fun build(
        effects: List<EffectItem>,
        registry: EffectRegistry,
        map: TimelineToCompositionTimeMap,
        releasePolicy: EffectReleasePolicy = EffectReleasePolicy(),
        logger: (String) -> Unit = {}
    ): Result {
        val attachments = effects
            .sortedWith(compareBy<EffectItem> { it.zOrder }.thenBy { it.startMs }.thenBy { it.id })
            .mapNotNull { item ->
                if (item.scope != EffectScope.GLOBAL) {
                    logger("EFFECT_EXPORT_SKIP_SCOPE id=${item.id} scope=${item.scope}")
                    return@mapNotNull null
                }
                val registration = registry.get(item.effectId)
                if (registration == null) {
                    logger("EFFECT_EXPORT_UNKNOWN_ID id=${item.id} effectId=${item.effectId}")
                    return@mapNotNull null
                }
                if (!releasePolicy.isExportReady(item.effectId)) {
                    logger("EFFECT_EXPORT_SKIPPED reason=not_export_ready id=${item.id} effectId=${item.effectId}")
                    return@mapNotNull null
                }

                val mappedWindow = map.mapWindow(item.startMs, item.endMs)
                val windowStartUs = mappedWindow.first * 1000L
                val windowEndUs = mappedWindow.last * 1000L
                val provider = EffectParamResolver.resolve(
                    itemId = item.id,
                    storedParams = item.params,
                    specs = registration.descriptor.paramSpecs,
                    windowStartUs = windowStartUs,
                    constantMode = EffectParamResolver.ConstantMode.Snapshot,
                    logPrefix = "EFFECT_EXPORT",
                    logger = logger
                ).provider
                val effect = registration.factory.create(windowStartUs, windowEndUs, provider)
                Attachment(
                    effectId = item.effectId,
                    zOrder = item.zOrder,
                    windowStartUs = windowStartUs,
                    windowEndUs = windowEndUs,
                    provider = provider,
                    effect = effect
                )
            }
        return Result(attachments)
    }
}
