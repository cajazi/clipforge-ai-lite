package com.clipforge.ai.validation.c9.support

import android.graphics.Bitmap

/**
 * C9.0 support harness: structural similarity (SSIM) comparison between two equally-sized
 * frames. No image-comparison library is on the androidTest classpath, so this implements the
 * standard block-wise luminance SSIM (Wang et al.) directly against [Bitmap] pixel data.
 */
object GoldenComparator {
    const val DEFAULT_THRESHOLD = 0.98

    data class SsimResult(val score: Double, val pass: Boolean)

    fun compare(expected: Bitmap, actual: Bitmap, threshold: Double = DEFAULT_THRESHOLD): SsimResult {
        require(expected.width == actual.width && expected.height == actual.height) {
            "frame size mismatch: expected=${expected.width}x${expected.height} actual=${actual.width}x${actual.height}"
        }
        val score = ssim(
            toLuminance(expected),
            toLuminance(actual),
            expected.width,
            expected.height
        )
        return SsimResult(score, score >= threshold)
    }

    private fun toLuminance(bitmap: Bitmap): DoubleArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return DoubleArray(width * height) { i ->
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            0.299 * r + 0.587 * g + 0.114 * b
        }
    }

    private fun ssim(a: DoubleArray, b: DoubleArray, width: Int, height: Int): Double {
        val c1 = (0.01 * 255) * (0.01 * 255)
        val c2 = (0.03 * 255) * (0.03 * 255)
        var totalScore = 0.0
        var blockCount = 0

        var blockY = 0
        while (blockY < height) {
            val blockHeight = minOf(BLOCK_SIZE, height - blockY)
            var blockX = 0
            while (blockX < width) {
                val blockWidth = minOf(BLOCK_SIZE, width - blockX)
                var sumA = 0.0
                var sumB = 0.0
                var sumA2 = 0.0
                var sumB2 = 0.0
                var sumAB = 0.0
                var n = 0
                for (y in blockY until blockY + blockHeight) {
                    for (x in blockX until blockX + blockWidth) {
                        val idx = y * width + x
                        val va = a[idx]
                        val vb = b[idx]
                        sumA += va
                        sumB += vb
                        sumA2 += va * va
                        sumB2 += vb * vb
                        sumAB += va * vb
                        n++
                    }
                }
                val meanA = sumA / n
                val meanB = sumB / n
                val varA = sumA2 / n - meanA * meanA
                val varB = sumB2 / n - meanB * meanB
                val covAB = sumAB / n - meanA * meanB

                val numerator = (2 * meanA * meanB + c1) * (2 * covAB + c2)
                val denominator = (meanA * meanA + meanB * meanB + c1) * (varA + varB + c2)
                totalScore += numerator / denominator
                blockCount++
                blockX += BLOCK_SIZE
            }
            blockY += BLOCK_SIZE
        }
        return if (blockCount == 0) 1.0 else totalScore / blockCount
    }

    private const val BLOCK_SIZE = 8
}
