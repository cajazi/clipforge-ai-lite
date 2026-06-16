package com.clipforge.ai.core.animation

import kotlin.math.roundToLong

object AnimationWindowResolver {
    data class Window(
        val startMs: Long,
        val endMs: Long
    ) {
        val durationMs: Long get() = endMs - startMs
    }

    data class Result(
        val role: AnimationRole,
        val window: Window
    )

    fun resolve(
        clipStartMs: Long,
        clipEndMs: Long,
        requestedDurationMs: Long,
        role: AnimationRole,
        incomingTransitionDurationMs: Long = 0L,
        outgoingTransitionDurationMs: Long = 0L,
        minimumUsableDurationMs: Long = 1L
    ): Window? {
        val clean = cleanWindow(
            clipStartMs = clipStartMs,
            clipEndMs = clipEndMs,
            incomingTransitionDurationMs = incomingTransitionDurationMs,
            outgoingTransitionDurationMs = outgoingTransitionDurationMs
        ) ?: return null
        if (clean.durationMs < minimumUsableDurationMs) return null
        return when (role) {
            AnimationRole.IN -> {
                val duration = requestedDurationMs.coerceIn(0L, clean.durationMs)
                clean.copy(endMs = clean.startMs + duration).takeIfUsable(minimumUsableDurationMs)
            }
            AnimationRole.OUT -> {
                val duration = requestedDurationMs.coerceIn(0L, clean.durationMs)
                clean.copy(startMs = clean.endMs - duration).takeIfUsable(minimumUsableDurationMs)
            }
            AnimationRole.COMBO -> clean.takeIfUsable(minimumUsableDurationMs)
        }
    }

    fun resolveInOut(
        clipStartMs: Long,
        clipEndMs: Long,
        requestedInDurationMs: Long,
        requestedOutDurationMs: Long,
        incomingTransitionDurationMs: Long = 0L,
        outgoingTransitionDurationMs: Long = 0L,
        minimumUsableDurationMs: Long = 1L
    ): List<Result> {
        val clean = cleanWindow(
            clipStartMs = clipStartMs,
            clipEndMs = clipEndMs,
            incomingTransitionDurationMs = incomingTransitionDurationMs,
            outgoingTransitionDurationMs = outgoingTransitionDurationMs
        ) ?: return emptyList()
        if (clean.durationMs < minimumUsableDurationMs) return emptyList()

        val requestedIn = requestedInDurationMs.coerceAtLeast(0L)
        val requestedOut = requestedOutDurationMs.coerceAtLeast(0L)
        val total = requestedIn + requestedOut
        val scale = if (total > clean.durationMs && total > 0L) {
            clean.durationMs.toDouble() / total.toDouble()
        } else {
            1.0
        }
        val inDuration = (requestedIn * scale).roundToLong().coerceAtMost(clean.durationMs)
        val outDuration = (requestedOut * scale).roundToLong().coerceAtMost(clean.durationMs - inDuration)
        return listOfNotNull(
            clean.copy(endMs = clean.startMs + inDuration)
                .takeIfUsable(minimumUsableDurationMs)
                ?.let { Result(AnimationRole.IN, it) },
            clean.copy(startMs = clean.endMs - outDuration)
                .takeIfUsable(minimumUsableDurationMs)
                ?.let { Result(AnimationRole.OUT, it) }
        )
    }

    private fun cleanWindow(
        clipStartMs: Long,
        clipEndMs: Long,
        incomingTransitionDurationMs: Long,
        outgoingTransitionDurationMs: Long
    ): Window? {
        val cleanStart = clipStartMs + incomingTransitionDurationMs.coerceAtLeast(0L)
        val cleanEnd = clipEndMs - outgoingTransitionDurationMs.coerceAtLeast(0L)
        val normalizedEnd = cleanEnd.coerceAtLeast(cleanStart)
        return Window(cleanStart, normalizedEnd).takeIf { it.durationMs > 0L }
    }

    private fun Window.takeIfUsable(minimumUsableDurationMs: Long): Window? =
        takeIf { durationMs >= minimumUsableDurationMs && startMs < endMs }
}
