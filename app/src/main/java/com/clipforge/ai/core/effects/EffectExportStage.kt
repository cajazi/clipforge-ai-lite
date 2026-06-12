@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.effects

import androidx.media3.effect.GlEffect
import com.clipforge.ai.core.overlay.TimelineToCompositionTimeMap
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue

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

                val mappedWindow = map.mapWindow(item.startMs, item.endMs)
                val windowStartUs = mappedWindow.first * 1000L
                val windowEndUs = mappedWindow.last * 1000L
                val provider = providerFor(item, registration.descriptor.paramSpecs, windowStartUs, logger)
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

    private fun providerFor(
        item: EffectItem,
        specs: List<ParamSpec>,
        windowStartUs: Long,
        logger: (String) -> Unit
    ): ParamProvider {
        val specsByKey = specs.associateBy { it.key }
        val stored = linkedMapOf<String, EffectParamValue>()

        item.params.forEach { (key, value) ->
            val spec = specsByKey[key]
            if (spec == null) {
                logger("EFFECT_EXPORT_DROP_UNKNOWN_PARAM item=${item.id} key=$key")
            } else {
                stored[key] = clampValue(item.id, spec, value, logger)
            }
        }

        val hasKeyframed = stored.values.any { it is EffectParamValue.Keyframed }
        return if (hasKeyframed) {
            val tracks = specs.associate { spec ->
                val value = stored[spec.key]
                val frames = when (value) {
                    is EffectParamValue.Keyframed -> value.frames.map { frame ->
                        frame.copy(timeUs = frame.timeUs + windowStartUs)
                    }
                    is EffectParamValue.Constant -> listOf(Keyframe(windowStartUs, value.value))
                    null -> listOf(Keyframe(windowStartUs, spec.default))
                }
                spec.key to frames
            }
            KeyframedParams(tracks)
        } else {
            val values = specs.associate { spec ->
                val value = stored[spec.key] as? EffectParamValue.Constant
                spec.key to (value?.value ?: spec.default)
            }
            ConstantParams(values)
        }
    }

    private fun clampValue(
        itemId: String,
        spec: ParamSpec,
        value: EffectParamValue,
        logger: (String) -> Unit
    ): EffectParamValue = when (value) {
        is EffectParamValue.Constant -> {
            val clamped = value.value.coerceIn(spec.min, spec.max)
            if (clamped != value.value) {
                logger("EFFECT_EXPORT_CLAMP_PARAM item=$itemId key=${spec.key} value=${value.value} clamped=$clamped")
            }
            EffectParamValue.Constant(clamped)
        }
        is EffectParamValue.Keyframed -> {
            EffectParamValue.Keyframed(
                value.frames.map { frame ->
                    val clamped = frame.value.coerceIn(spec.min, spec.max)
                    if (clamped != frame.value) {
                        logger("EFFECT_EXPORT_CLAMP_KEYFRAME item=$itemId key=${spec.key} timeUs=${frame.timeUs} value=${frame.value} clamped=$clamped")
                    }
                    frame.copy(value = clamped)
                }
            )
        }
    }
}
