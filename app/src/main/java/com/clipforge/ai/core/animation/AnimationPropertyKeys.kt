package com.clipforge.ai.core.animation

/**
 * Animation property identity + metadata (C8.1 foundation — constants only, no rendering).
 *
 * Each key names one animatable Float track that a future transform renderer will read from a
 * [com.clipforge.ai.core.effects.ParamProvider]. Defaults are IDENTITY values: applying a key
 * at its default must be a visual no-op (scale=1, opacity=1, position/rotation=0, anchor=centre).
 *
 * Pure Kotlin: no Android, no Media3, no rendering logic.
 */
object AnimationPropertyKeys {
    const val POSITION_X = "positionX"
    const val POSITION_Y = "positionY"
    const val SCALE = "scale"
    const val SCALE_X = "scaleX"
    const val SCALE_Y = "scaleY"
    const val ROTATION = "rotation"
    const val OPACITY = "opacity"
    const val ANCHOR_X = "anchorX"
    const val ANCHOR_Y = "anchorY"

    /** Metadata for one animatable property. [defaultValue] is the identity (no-op) value. */
    data class Meta(val key: String, val defaultValue: Float)

    /** All known properties, in canonical order. Single source of truth for validation. */
    val ALL: List<Meta> = listOf(
        Meta(POSITION_X, 0f),
        Meta(POSITION_Y, 0f),
        Meta(SCALE, 1f),
        Meta(SCALE_X, 1f),
        Meta(SCALE_Y, 1f),
        Meta(ROTATION, 0f),
        Meta(OPACITY, 1f),
        Meta(ANCHOR_X, 0.5f),
        Meta(ANCHOR_Y, 0.5f)
    )

    private val byKey: Map<String, Meta> = ALL.associateBy { it.key }

    /** True if [key] is a recognized animatable property. */
    fun isKnown(key: String): Boolean = byKey.containsKey(key)

    /** Identity/default value for [key], or null if unknown. */
    fun defaultValue(key: String): Float? = byKey[key]?.defaultValue

    /** All known keys, in canonical order. */
    fun keys(): List<String> = ALL.map { it.key }
}
