package com.clipforge.ai.data.repository

import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.KeyframeEasing
import com.clipforge.ai.core.effects.KeyframeSampler
import com.clipforge.ai.domain.model.EffectParamValue
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object EffectParamsCodec {
    private const val VERSION = 1
    private val gson = Gson()

    fun encode(params: Map<String, EffectParamValue>): String {
        val root = JsonObject()
        root.addProperty("v", VERSION)
        val paramsObject = JsonObject()
        params.toSortedMap().forEach { (key, value) ->
            val valueObject = JsonObject()
            when (value) {
                is EffectParamValue.Constant -> {
                    valueObject.addProperty("type", "constant")
                    valueObject.addProperty("value", value.value)
                }
                is EffectParamValue.Keyframed -> {
                    KeyframeSampler.requireSorted(value.frames)
                    valueObject.addProperty("type", "keyframes")
                    val frames = com.google.gson.JsonArray()
                    value.frames.forEach { frame ->
                        val frameObject = JsonObject()
                        frameObject.addProperty("timeUs", frame.timeUs)
                        frameObject.addProperty("value", frame.value)
                        frameObject.addProperty("easing", frame.easing.name)
                        frames.add(frameObject)
                    }
                    valueObject.add("frames", frames)
                }
            }
            paramsObject.add(key, valueObject)
        }
        root.add("params", paramsObject)
        return gson.toJson(root)
    }

    fun decode(paramsJson: String): Map<String, EffectParamValue> {
        val root = JsonParser.parseString(paramsJson).asObject("root")
        val version = root.required("v").asIntStrict("v")
        require(version == VERSION) { "Unsupported paramsJson version: $version" }
        val paramsObject = root.required("params").asObject("params")
        val decoded = linkedMapOf<String, EffectParamValue>()
        paramsObject.entrySet().forEach { (key, element) ->
            val valueObject = element.asObject("params.$key")
            when (val type = valueObject.required("type").asStringStrict("params.$key.type")) {
                "constant" -> decoded[key] = EffectParamValue.Constant(
                    valueObject.required("value").asFloatStrict("params.$key.value")
                )
                "keyframes" -> {
                    val framesElement = valueObject.required("frames")
                    require(framesElement.isJsonArray) { "params.$key.frames must be an array" }
                    val frames = framesElement.asJsonArray.mapIndexed { index, frameElement ->
                        val frame = frameElement.asObject("params.$key.frames[$index]")
                        Keyframe(
                            timeUs = frame.required("timeUs").asLongStrict("params.$key.frames[$index].timeUs"),
                            value = frame.required("value").asFloatStrict("params.$key.frames[$index].value"),
                            easing = parseEasing(
                                frame.required("easing").asStringStrict("params.$key.frames[$index].easing")
                            )
                        )
                    }
                    KeyframeSampler.requireSorted(frames)
                    decoded[key] = EffectParamValue.Keyframed(frames)
                }
                else -> throw IllegalArgumentException("Unknown param type '$type' for key '$key'")
            }
        }
        return decoded
    }

    private fun parseEasing(raw: String): KeyframeEasing =
        runCatching { KeyframeEasing.valueOf(raw) }
            .getOrElse { throw IllegalArgumentException("Unknown keyframe easing '$raw'", it) }

    private fun JsonObject.required(name: String): JsonElement {
        require(has(name)) { "Missing JSON field '$name'" }
        val value = get(name)
        require(value != null && !value.isJsonNull) { "Missing JSON field '$name'" }
        return value
    }

    private fun JsonElement.asObject(path: String): JsonObject {
        require(isJsonObject) { "$path must be an object" }
        return asJsonObject
    }

    private fun JsonElement.asIntStrict(path: String): Int {
        require(isJsonPrimitive && asJsonPrimitive.isNumber) { "$path must be a number" }
        return asInt
    }

    private fun JsonElement.asLongStrict(path: String): Long {
        require(isJsonPrimitive && asJsonPrimitive.isNumber) { "$path must be a number" }
        return asLong
    }

    private fun JsonElement.asFloatStrict(path: String): Float {
        require(isJsonPrimitive && asJsonPrimitive.isNumber) { "$path must be a number" }
        return asFloat
    }

    private fun JsonElement.asStringStrict(path: String): String {
        require(isJsonPrimitive && asJsonPrimitive.isString) { "$path must be a string" }
        return asString
    }
}
