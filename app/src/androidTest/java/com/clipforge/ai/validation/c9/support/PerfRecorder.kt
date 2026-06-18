package com.clipforge.ai.validation.c9.support

import android.os.Debug
import android.os.SystemClock

/**
 * C9.0 support harness: collects preview frame-time/FPS, export wall-clock, memory, and
 * concurrency metrics for [com.clipforge.ai.validation.c9.AnimationPerformanceTest] and for the
 * `artifacts/c9/perf/perf-report.json` artifact. No benchmark library is on the androidTest
 * classpath, so timing/memory sampling is implemented directly against platform APIs.
 */
object PerfRecorder {
    data class FrameStats(
        val sampleCount: Int,
        val p50FrameTimeMs: Double,
        val p95FrameTimeMs: Double,
        val averageFps: Double,
        val sustainedFps: Double
    )

    data class MemorySample(val nativeHeapKb: Long, val javaHeapKb: Long) {
        val totalKb: Long get() = nativeHeapKb + javaHeapKb
    }

    /** Accumulates per-frame deltas; call [onFrame] once per rendered/composited preview frame. */
    class FrameTimeTracker {
        private val frameTimesMs = mutableListOf<Double>()
        private var lastFrameNs: Long? = null

        fun onFrame(nowNs: Long = System.nanoTime()) {
            val last = lastFrameNs
            if (last != null) {
                frameTimesMs += (nowNs - last) / 1_000_000.0
            }
            lastFrameNs = nowNs
        }

        fun reset() {
            frameTimesMs.clear()
            lastFrameNs = null
        }

        fun stats(): FrameStats {
            if (frameTimesMs.isEmpty()) return FrameStats(0, 0.0, 0.0, 0.0, 0.0)
            val sorted = frameTimesMs.sorted()
            val p50 = percentile(sorted, 0.50)
            val p95 = percentile(sorted, 0.95)
            val avgFrameMs = sorted.average()
            val avgFps = if (avgFrameMs > 0) 1000.0 / avgFrameMs else 0.0
            // "Sustained" FPS uses the worst (slowest) 10% of frames, matching the
            // performance-regression contract (fail if sustained FPS < 24).
            val worstDecileStart = (sorted.size * 0.9).toInt().coerceIn(0, sorted.size - 1)
            val worstFrames = sorted.subList(worstDecileStart, sorted.size)
            val worstAvgMs = worstFrames.average()
            val sustainedFps = if (worstAvgMs > 0) 1000.0 / worstAvgMs else 0.0
            return FrameStats(sorted.size, p50, p95, avgFps, sustainedFps)
        }

        private fun percentile(sorted: List<Double>, fraction: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val index = ((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.size - 1)
            return sorted[index]
        }
    }

    fun currentMemory(): MemorySample {
        val nativeKb = Debug.getNativeHeapAllocatedSize() / 1024L
        val runtime = Runtime.getRuntime()
        val javaKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L
        return MemorySample(nativeKb, javaKb)
    }

    inline fun <T> timeWallClockMs(block: () -> T): Pair<T, Long> {
        val start = SystemClock.elapsedRealtime()
        val result = block()
        val elapsed = SystemClock.elapsedRealtime() - start
        return result to elapsed
    }
}
