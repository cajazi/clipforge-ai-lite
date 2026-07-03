package com.clipforge.ai.core.overlay

data class OverlayFrameRate private constructor(
    val framesPerSecondNumerator: Long,
    val framesPerSecondDenominator: Long
) {
    init {
        require(framesPerSecondNumerator > 0L) { "Frame-rate numerator must be positive" }
        require(framesPerSecondDenominator > 0L) { "Frame-rate denominator must be positive" }
    }

    companion object {
        val FPS_24: OverlayFrameRate = of(24L)
        val FPS_25: OverlayFrameRate = of(25L)
        val FPS_30: OverlayFrameRate = of(30L)
        val FPS_60: OverlayFrameRate = of(60L)
        val NTSC_23_976: OverlayFrameRate = of(24_000L, 1_001L)
        val NTSC_29_97: OverlayFrameRate = of(30_000L, 1_001L)

        fun of(framesPerSecond: Long): OverlayFrameRate = of(framesPerSecond, 1L)

        fun of(numerator: Long, denominator: Long): OverlayFrameRate {
            require(numerator > 0L) { "Frame-rate numerator must be positive" }
            require(denominator > 0L) { "Frame-rate denominator must be positive" }
            val divisor = gcd(numerator, denominator)
            return OverlayFrameRate(numerator / divisor, denominator / divisor)
        }

        private tailrec fun gcd(a: Long, b: Long): Long =
            if (b == 0L) kotlin.math.abs(a) else gcd(b, a % b)
    }
}

data class DeterministicOverlayFrameClock(
    val frameRate: OverlayFrameRate = OverlayFrameRate.FPS_30
) {
    fun frameIndexAt(timeUs: Long): Long {
        if (timeUs <= 0L) return 0L
        val divisor = MICROS_PER_SECOND * frameRate.framesPerSecondDenominator
        return (multiplyDivCeil(timeUs + 1L, frameRate.framesPerSecondNumerator, divisor) - 1L)
            .coerceAtLeast(0L)
    }

    fun frameTimeUs(frameIndex: Long): Long {
        require(frameIndex >= 0L) { "Frame index must be non-negative" }
        val multiplier = MICROS_PER_SECOND * frameRate.framesPerSecondDenominator
        return multiplyDivFloor(frameIndex, multiplier, frameRate.framesPerSecondNumerator)
    }

    fun snappedTimeUs(timeUs: Long): Long = frameTimeUs(frameIndexAt(timeUs))

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000L

        fun multiplyDivFloor(value: Long, multiplier: Long, divisor: Long): Long {
            require(value >= 0L) { "Value must be non-negative" }
            require(multiplier > 0L) { "Multiplier must be positive" }
            require(divisor > 0L) { "Divisor must be positive" }
            val whole = value / divisor
            val remainder = value % divisor
            return Math.addExact(
                Math.multiplyExact(whole, multiplier),
                Math.multiplyExact(remainder, multiplier) / divisor
            )
        }

        fun multiplyDivCeil(value: Long, multiplier: Long, divisor: Long): Long {
            val floor = multiplyDivFloor(value, multiplier, divisor)
            val hasRemainder = Math.multiplyExact(value % divisor, multiplier) % divisor != 0L
            return if (hasRemainder) Math.addExact(floor, 1L) else floor
        }
    }
}
