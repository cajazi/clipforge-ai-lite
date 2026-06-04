package com.clipforge.ai.presentation.timeline

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.clipforge.ai.domain.model.MediaType

private const val TAG = "TimelinePlayback"

class TimelinePlaybackEngine {
    var currentTimelineMs by mutableLongStateOf(0L)
        private set
    var activeClipId by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var projectDurationMs by mutableLongStateOf(1L)
        private set
    var activeClip by mutableStateOf<ClipUiModel?>(null)
        private set
    var localClipTimeMs by mutableLongStateOf(0L)
        private set

    private var clips: List<ClipUiModel> = emptyList()

    fun setClips(sourceClips: List<ClipUiModel>) {
        clips = sourceClips.primaryTimelineClips()
        projectDurationMs = clips.maxOfOrNull { it.timelineEndMs }?.coerceAtLeast(1L) ?: 1L
        if (currentTimelineMs > projectDurationMs) currentTimelineMs = projectDurationMs
        updateActiveClip("clips")
    }

    fun togglePlayback() {
        updatePlaying(!isPlaying)
    }

    fun updatePlaying(playing: Boolean) {
        if (playing && currentTimelineMs >= projectDurationMs) {
            currentTimelineMs = 0L
        }
        isPlaying = playing && clips.isNotEmpty()
        updateActiveClip("playing")
    }

    fun seekTo(timelineMs: Long) {
        currentTimelineMs = timelineMs.coerceIn(0L, projectDurationMs)
        if (currentTimelineMs >= projectDurationMs) isPlaying = false
        updateActiveClip("seek")
    }

    fun tick(deltaMs: Long) {
        if (!isPlaying) return
        val nextTimelineMs = currentTimelineMs + deltaMs
        val currentClipEndMs = activeClip?.timelineEndMs
        if (nextTimelineMs >= projectDurationMs) {
            currentTimelineMs = projectDurationMs
            isPlaying = false
            updateActiveClip("project-end")
            Log.d(TAG, "project end currentTimelineMs=$currentTimelineMs")
        } else if (currentClipEndMs != null &&
            currentTimelineMs < currentClipEndMs &&
            nextTimelineMs >= currentClipEndMs
        ) {
            currentTimelineMs = currentClipEndMs
            updateActiveClip("boundary")
        } else {
            currentTimelineMs = nextTimelineMs
            updateActiveClip("tick")
        }
    }

    private fun updateActiveClip(reason: String) {
        val nextClip = clips.firstOrNull {
            currentTimelineMs >= it.timelineStartMs && currentTimelineMs < it.timelineEndMs
        }
        val previousClipId = activeClipId
        activeClip = nextClip
        activeClipId = nextClip?.id
        localClipTimeMs = nextClip?.let {
            (it.sourceStartMs + currentTimelineMs - it.timelineStartMs)
                .coerceIn(it.sourceStartMs, it.sourceEndMs)
        } ?: 0L

        if (previousClipId != activeClipId) {
            Log.d(
                TAG,
                "clip switch reason=$reason from=$previousClipId to=$activeClipId currentTimelineMs=$currentTimelineMs localClipTimeMs=$localClipTimeMs"
            )
        }
        nextClip?.let {
            Log.d(
                TAG,
                "currentTimelineMs=$currentTimelineMs activeClipId=${it.id} localClipTimeMs=$localClipTimeMs timelineStartMs=${it.timelineStartMs} timelineEndMs=${it.timelineEndMs}"
            )
        }
    }
}

fun List<ClipUiModel>.primaryTimelineClips(): List<ClipUiModel> =
    filter { it.mediaType == MediaType.VIDEO || it.mediaType == MediaType.IMAGE }
        .ifEmpty { this }
