package com.clipforge.ai.core.effects

import com.clipforge.ai.domain.model.EffectParamValue

object EffectParamResolver {
    enum class ConstantMode {
        Snapshot,
        Live
    }

    data class Result(
        val provider: ParamProvider,
        val keyframedParamsSignature: String,
        val liveParams: LiveParams?
    )

    fun resolve(
        itemId: String,
        storedParams: Map<String, EffectParamValue>,
        specs: List<ParamSpec>,
        windowStartUs: Long,
        constantMode: ConstantMode,
        pendingLiveValues: Map<String, Float> = emptyMap(),
        logPrefix: String,
        logger: (String) -> Unit = {}
    ): Result {
        val specsByKey = specs.associateBy { it.key }
        val stored = linkedMapOf<String, EffectParamValue>()

        storedParams.forEach { (key, value) ->
            val spec = specsByKey[key]
            if (spec == null) {
                logger("${logPrefix}_DROP_UNKNOWN_PARAM item=$itemId key=$key")
            } else {
                stored[key] = clampValue(itemId, spec, value, logPrefix, logger)
            }
        }
        val signature = keyframedSignature(stored)

        pendingLiveValues.forEach { (key, value) ->
            val spec = specsByKey[key]
            if (spec == null) {
                logger("${logPrefix}_DROP_UNKNOWN_PENDING_PARAM item=$itemId key=$key")
            } else {
                stored[key] = clampValue(itemId, spec, EffectParamValue.Constant(value), logPrefix, logger)
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
            Result(
                provider = KeyframedParams(tracks),
                keyframedParamsSignature = signature,
                liveParams = null
            )
        } else {
            val values = specs.associate { spec ->
                val value = stored[spec.key] as? EffectParamValue.Constant
                spec.key to (value?.value ?: spec.default)
            }
            val liveParams = if (constantMode == ConstantMode.Live && values.isNotEmpty()) LiveParams(values) else null
            Result(
                provider = liveParams ?: ConstantParams(values),
                keyframedParamsSignature = signature,
                liveParams = liveParams
            )
        }
    }

    private fun keyframedSignature(stored: Map<String, EffectParamValue>): String =
        stored
            .entries
            .filter { it.value is EffectParamValue.Keyframed }
            .sortedBy { it.key }
            .joinToString("|") { (key, value) ->
                val frames = (value as EffectParamValue.Keyframed).frames.joinToString(",") { frame ->
                    "${frame.timeUs}:${frame.value}:${frame.easing.name}"
                }
                "$key=[$frames]"
            }

    private fun clampValue(
        itemId: String,
        spec: ParamSpec,
        value: EffectParamValue,
        logPrefix: String,
        logger: (String) -> Unit
    ): EffectParamValue = when (value) {
        is EffectParamValue.Constant -> {
            val clamped = value.value.coerceIn(spec.min, spec.max)
            if (clamped != value.value) {
                logger("${logPrefix}_CLAMP_PARAM item=$itemId key=${spec.key} value=${value.value} clamped=$clamped")
            }
            EffectParamValue.Constant(clamped)
        }
        is EffectParamValue.Keyframed -> {
            EffectParamValue.Keyframed(
                value.frames.map { frame ->
                    val clamped = frame.value.coerceIn(spec.min, spec.max)
                    if (clamped != frame.value) {
                        logger("${logPrefix}_CLAMP_KEYFRAME item=$itemId key=${spec.key} timeUs=${frame.timeUs} value=${frame.value} clamped=$clamped")
                    }
                    frame.copy(value = clamped)
                }
            )
        }
    }
}
