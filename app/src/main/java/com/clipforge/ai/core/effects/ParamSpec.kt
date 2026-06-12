package com.clipforge.ai.core.effects

/**
 * Declares one adjustable parameter of an effect (the registry-side schema for
 * `paramsJson` values and for building the param bottom sheet).
 */
data class ParamSpec(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val default: Float
) {
    init {
        require(key.isNotBlank()) { "ParamSpec key must not be blank" }
        require(min <= max) { "ParamSpec '$key': min ($min) must be <= max ($max)" }
        require(default in min..max) { "ParamSpec '$key': default ($default) must be within [$min, $max]" }
    }
}
