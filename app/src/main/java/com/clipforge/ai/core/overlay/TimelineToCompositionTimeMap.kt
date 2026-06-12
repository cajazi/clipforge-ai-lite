package com.clipforge.ai.core.overlay

data class TimePiece(
    val timelineMs: Long,
    val compositionMs: Long
)

class TimelineToCompositionTimeMap private constructor(
    private val timelinePrefixMs: LongArray,
    private val compositionPrefixMs: LongArray
) {
    val timelineTotalMs: Long get() = timelinePrefixMs.last()
    val compositionTotalMs: Long get() = compositionPrefixMs.last()

    fun toCompositionMs(timelineMs: Long): Long {
        if (timelineMs <= 0L) return 0L
        if (timelineMs >= timelineTotalMs) return compositionTotalMs

        val index = segmentIndexFor(timelineMs)
        val segmentTimelineStart = timelinePrefixMs[index]
        val segmentTimelineEnd = timelinePrefixMs[index + 1]
        val segmentCompositionStart = compositionPrefixMs[index]
        val segmentCompositionEnd = compositionPrefixMs[index + 1]
        val timelineSpan = segmentTimelineEnd - segmentTimelineStart
        val compositionSpan = segmentCompositionEnd - segmentCompositionStart

        if (timelineSpan == 0L || compositionSpan == 0L) return segmentCompositionStart
        val localTimelineMs = timelineMs - segmentTimelineStart
        return segmentCompositionStart + (localTimelineMs * compositionSpan) / timelineSpan
    }

    fun mapWindow(startMs: Long, endMs: Long): LongRange {
        val mappedStart = toCompositionMs(startMs)
        val mappedEnd = toCompositionMs(endMs)
        return if (mappedStart <= mappedEnd) {
            mappedStart..mappedEnd
        } else {
            mappedEnd..mappedStart
        }
    }

    private fun segmentIndexFor(timelineMs: Long): Int {
        var low = 0
        var high = timelinePrefixMs.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (timelinePrefixMs[mid] <= timelineMs) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return (low - 1).coerceIn(0, timelinePrefixMs.size - 2)
    }

    companion object {
        fun build(pieces: List<TimePiece>): TimelineToCompositionTimeMap {
            require(pieces.isNotEmpty()) { "TimelineToCompositionTimeMap requires at least one piece" }

            val timelinePrefix = LongArray(pieces.size + 1)
            val compositionPrefix = LongArray(pieces.size + 1)
            pieces.forEachIndexed { index, piece ->
                require(piece.timelineMs >= 0L) { "TimePiece[$index].timelineMs must be non-negative" }
                require(piece.compositionMs >= 0L) { "TimePiece[$index].compositionMs must be non-negative" }
                require(!(piece.compositionMs > 0L && piece.timelineMs == 0L)) {
                    "TimePiece[$index] cannot create composition time from zero timeline time"
                }
                timelinePrefix[index + 1] = timelinePrefix[index] + piece.timelineMs
                compositionPrefix[index + 1] = compositionPrefix[index] + piece.compositionMs
            }

            return TimelineToCompositionTimeMap(timelinePrefix, compositionPrefix)
        }
    }
}
