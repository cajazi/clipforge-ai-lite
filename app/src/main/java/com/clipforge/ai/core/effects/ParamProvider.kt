package com.clipforge.ai.core.effects

import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Per-frame parameter source for effect shaders.
 *
 * Threading contract (A' spec): the GL thread calls [valueAt] every frame; writers (if
 * any) are on the main thread. Implementations must be lock-free and allocation-free on
 * the read path. An unknown key is a programming error (the C4 stage builds providers by
 * merging ParamSpec defaults with item values, so every declared key is always present)
 * and throws [IllegalArgumentException].
 */
interface ParamProvider {
    fun valueAt(key: String, presentationTimeUs: Long): Float
}

/**
 * Immutable snapshot provider. Export ALWAYS uses this (or [KeyframedParams]) so a render
 * in flight can never observe a live edit.
 */
class ConstantParams(values: Map<String, Float>) : ParamProvider {

    private val values: Map<String, Float> = HashMap(values)

    override fun valueAt(key: String, presentationTimeUs: Long): Float =
        values[key] ?: throw IllegalArgumentException("Unknown param key '$key'")
}

/**
 * Live provider for preview sliders (C0/E5: a write is visible to the GL thread on the
 * next frame, with no pipeline rebuild).
 *
 * The key set is fixed at construction; values are stored as float bits in an
 * [AtomicIntegerArray], giving element-volatile, lock-free, box-free reads on the GL
 * thread.
 */
class LiveParams(initialValues: Map<String, Float>) : ParamProvider {

    private val indexByKey: Map<String, Int>
    private val bits: AtomicIntegerArray

    init {
        require(initialValues.isNotEmpty()) { "LiveParams requires at least one key" }
        val keys = initialValues.keys.toList()
        indexByKey = keys.withIndex().associate { (i, k) -> k to i }
        bits = AtomicIntegerArray(keys.size)
        keys.forEachIndexed { i, k -> bits.set(i, initialValues.getValue(k).toRawBits()) }
    }

    fun set(key: String, value: Float) {
        bits.set(indexOf(key), value.toRawBits())
    }

    override fun valueAt(key: String, presentationTimeUs: Long): Float =
        Float.fromBits(bits.get(indexOf(key)))

    private fun indexOf(key: String): Int =
        indexByKey[key] ?: throw IllegalArgumentException("Unknown param key '$key'")
}

/**
 * Immutable time-varying provider backed by [KeyframeSampler]. Tracks are validated at
 * construction; constant keys may be mixed in via single-frame tracks.
 */
class KeyframedParams(tracks: Map<String, List<Keyframe>>) : ParamProvider {

    private val tracks: Map<String, List<Keyframe>>

    init {
        require(tracks.isNotEmpty()) { "KeyframedParams requires at least one track" }
        tracks.forEach { (key, frames) ->
            try {
                KeyframeSampler.requireSorted(frames)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Track '$key': ${e.message}", e)
            }
        }
        this.tracks = tracks.mapValues { (_, frames) -> frames.toList() }
    }

    override fun valueAt(key: String, presentationTimeUs: Long): Float {
        val frames = tracks[key] ?: throw IllegalArgumentException("Unknown param key '$key'")
        return KeyframeSampler.sample(frames, presentationTimeUs)
    }
}
