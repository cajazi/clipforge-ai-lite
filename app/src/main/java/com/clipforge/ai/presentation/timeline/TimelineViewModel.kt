package com.clipforge.ai.presentation.timeline

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.data.local.entity.MediaAssetEntity
import com.clipforge.ai.data.local.entity.TimelineItemEntity
import com.clipforge.ai.data.repository.TransitionRepositoryImpl
import com.clipforge.ai.domain.history.ClipSnapshotCommand
import com.clipforge.ai.domain.model.MediaType
import com.clipforge.ai.domain.model.Transition
import com.clipforge.ai.domain.model.TransitionType
import com.clipforge.ai.domain.model.TimelineSegment
import com.clipforge.ai.domain.model.buildTimelineSegments
import com.clipforge.ai.domain.selection.SelectionTarget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.roundToLong
import java.util.UUID

private const val TAG     = "TimelineVM"
private const val TICK_MS = 33L
private const val MIN_PREVIEW_TRANSITION_DURATION_MS = 500L

data class TimelineUiState(
    val clips:               List<ClipUiModel>     = emptyList(),
    val segments:            List<TimelineSegment> = emptyList(),
    val totalDurationMs:     Long                  = 0L,
    val globalProjectTimeMs: Long                  = 0L,
    val isPlaying:           Boolean               = false,
    val currentSegment:      TimelineSegment?      = null,
    val selectedClipId:       String?               = null,
    val isLoading:           Boolean               = false,
    val pickerOpenForClipId: String?               = null,
    val isPro:               Boolean               = false,
    val audioTrackCount:     Int                    = 0,
    val textTrackCount:      Int                    = 0,
    val overlayTrackCount:   Int                    = 0,
    val toolbarMode:         EditorToolbarMode      = EditorToolbarMode.PRIMARY,
    val trimmingClipId:      String?                = null,
    val trimPreviewFrameMs:  Long?                  = null,
    val trimGestureActive:   Boolean                = false,
    val trimSessionSide:     TrimSide?              = null,
    val trimSessionStartMs:  Long?                  = null,
    val trimSessionEndMs:    Long?                  = null,
    val canUndo:             Boolean                = false,
    val canRedo:             Boolean                = false,
    val interactionMode:     EditorInteractionMode   = EditorInteractionMode.NONE,
    val splitAdjustClipAId:  String?                = null,
    val splitAdjustClipBId:  String?                = null,
    val activeSplitLeftClipId: String?              = null,
    val activeSplitRightClipId: String?             = null,
    val previewSplitBoundaryMs: Long?               = null,
    val resumePlaybackAfterPlaylistPrepared: Boolean = false,
    val activeTransition: ActiveTransitionState?     = null,
    val lastAddedClipId: String?                     = null,
    val preserveTimelineScrollVersion: Long          = 0L
)

data class ActiveTransitionState(
    val fromClipId: String,
    val toClipId: String,
    val transitionType: TransitionType,
    val transitionDurationMs: Long,
    val clipAStartMs: Long,
    val clipAEndMs: Long,
    val transitionStartMs: Long,
    val transitionEndMs: Long,
    val progress: Float
)

enum class EditorToolbarMode { PRIMARY, EDIT }
enum class EditorInteractionMode { NONE, TRIM, BOUNDARY_TRIM, SPLIT_ADJUST }
enum class TrimSide { LEFT, RIGHT }

sealed class EditToolAction(val label: String) {
    data object Back : EditToolAction("Back")
    data object Undo : EditToolAction("Undo")
    data object Redo : EditToolAction("Redo")
    data object Trim : EditToolAction("Trim")
    data object Split : EditToolAction("Split")
    data object Transition : EditToolAction("Transition")
    data object Volume : EditToolAction("Volume")
    data object Animations : EditToolAction("Animations")
    data object Effects : EditToolAction("Effects")
    data object Delete : EditToolAction("Delete")
    data object Speed : EditToolAction("Speed")
    data object Beats : EditToolAction("Beats")
    data object Crop : EditToolAction("Crop")
    data object Duplicate : EditToolAction("Duplicate")
    data object Replace : EditToolAction("Replace")
    data object Overlay : EditToolAction("Overlay")
    data object Adjust : EditToolAction("Adjust")
    data object Filters : EditToolAction("Filters")
    data object Retouch : EditToolAction("Retouch")
    data object VideoQuality : EditToolAction("Video Quality")
    data object RemoveBg : EditToolAction("Remove BG")
    data object AiRemove : EditToolAction("AI Remove")
    data object AiExpand : EditToolAction("AI Expand")
    data object AiRemix : EditToolAction("AI Remix")
    data object EyeContact : EditToolAction("Eye Contact")
    data object Relight : EditToolAction("Relight")
    data object Opacity : EditToolAction("Opacity")
    data object MotionBlur : EditToolAction("Motion Blur")
    data object LipSync : EditToolAction("Lip Sync")
    data object Transform : EditToolAction("Transform")
    data object AutoReframe : EditToolAction("Auto Reframe")
    data object Stabilize : EditToolAction("Stabilize")
    data object CameraTracking : EditToolAction("Camera Tracking")
    data object ExtractAudio : EditToolAction("Extract Audio")
    data object IsolateVoice : EditToolAction("Isolate Voice")
    data object ReduceNoise : EditToolAction("Reduce Noise")
    data object AudioEffects : EditToolAction("Audio Effects")
    data object EnhanceVoice : EditToolAction("Enhance Voice")
    data object VideoTranslator : EditToolAction("Video Translator")
    data object Freeze : EditToolAction("Freeze")
    data object Reverse : EditToolAction("Reverse")
    data object Mask : EditToolAction("Mask")
    data object Unlink : EditToolAction("Unlink")
}

sealed class TimelineEditorEffect {
    data object OpenVolumeSheet : TimelineEditorEffect()
    data object OpenSpeedSheet : TimelineEditorEffect()
    data object OpenTransformSheet : TimelineEditorEffect()
    data object OpenReplacePicker : TimelineEditorEffect()
    data class ShowPlaceholder(val tool: String) : TimelineEditorEffect()
    data class ShowComingSoon(val tool: String) : TimelineEditorEffect()
}

class TimelineViewModel(application: Application) : AndroidViewModel(application) {

    private val app  = application as ClipForgeApp
    private val dao  = app.database.timelineDao()
    private val historyRegistry = app.historyRegistry

    private val transitionRepo by lazy {
        TransitionRepositoryImpl(dao, app.authManager.sessionManager)
    }

    private val _uiState = MutableStateFlow(TimelineUiState())
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<TimelineEditorEffect>()
    val effects: SharedFlow<TimelineEditorEffect> = _effects.asSharedFlow()

    private var currentProjectId: String? = null
    private var playbackJob: Job?         = null

    init {
        viewModelScope.launch {
            historyRegistry.state.collect { history ->
                _uiState.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }
            }
        }
    }

    fun loadForProject(projectId: String) {
        if (currentProjectId == projectId) return
        currentProjectId = projectId
        historyRegistry.clear()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            dao.getTimelineForProject(projectId).collect { entities ->
                val assetMap = runCatching {
                    app.database.mediaAssetDao().getAssetsForProjectOnce(projectId).associateBy { it.id }
                }.getOrDefault(emptyMap())

                val audioCount = entities.count { entity ->
                    assetMap[entity.mediaAssetId]?.mediaType == MediaType.AUDIO.name
                }
                val overlayCount = entities.count { entity ->
                    val type = assetMap[entity.mediaAssetId]?.mediaType
                    type == MediaType.OVERLAY_IMAGE.name || type == MediaType.OVERLAY_VIDEO.name
                }
                val textCount = entities.count { entity ->
                    assetMap[entity.mediaAssetId]?.mediaType == MediaType.LOGO.name &&
                        assetMap[entity.mediaAssetId]?.mimeType == TEXT_OVERLAY_MIME
                }

                var primaryTimelineCursorMs = 0L
                val clips = entities.mapIndexedNotNull { _, entity ->
                    val asset = assetMap[entity.mediaAssetId]
                    val mediaType = asset?.mediaType?.let {
                        runCatching { MediaType.valueOf(it) }.getOrNull()
                    } ?: MediaType.VIDEO
                    val isPrimaryTimelineClip = mediaType == MediaType.VIDEO || mediaType == MediaType.IMAGE
                    if (!isPrimaryTimelineClip) return@mapIndexedNotNull null
                    val sourceStartMs = entity.trimStartMs.coerceAtLeast(0L)
                    val fallbackEntityDuration = (entity.endMs - entity.startMs).coerceAtLeast(1L)
                    val assetDuration = asset?.durationMs?.takeIf { it > 0L }
                    val sourceDurationMs = assetDuration ?: (entity.trimEndMs.takeIf { it > 0L } ?: fallbackEntityDuration)
                    val sourceEndMs = if (entity.trimEndMs > sourceStartMs) {
                        entity.trimEndMs.coerceAtMost(sourceDurationMs)
                    } else {
                        sourceStartMs + sourceDurationMs
                    }
                    val playbackSpeed = entity.playbackSpeed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
                    val sourceSpanMs = (sourceEndMs - sourceStartMs).coerceAtLeast(1L)
                    val effectiveDurationMs = (sourceSpanMs / playbackSpeed).roundToLong().coerceAtLeast(1L)
                    val timelineStartMs = primaryTimelineCursorMs
                    val timelineEndMs = timelineStartMs + effectiveDurationMs
                    primaryTimelineCursorMs = timelineEndMs
                    val trans = entity.transitionType?.let { name ->
                        runCatching {
                            val type = TransitionType.valueOf(name)
                            val rawDurationMs = entity.transitionDurationMs
                            val readDurationMs = normalizeSavedTransitionDurationMs(rawDurationMs ?: MIN_PREVIEW_TRANSITION_DURATION_MS)
                            Log.d(
                                TAG,
                                "TRANSITION_DURATION_READ clipId=${entity.id} transitionType=$type " +
                                    "rawTransitionDurationMs=$rawDurationMs transitionDurationMs=$readDurationMs"
                            )
                            Transition(type, readDurationMs, type.isPremium)
                        }.getOrNull()
                    }
                    Log.d(
                        TAG,
                        "clipTiming clipId=${entity.id} originalDurationMs=${assetDuration ?: fallbackEntityDuration} " +
                            "sourceStartMs=$sourceStartMs sourceEndMs=$sourceEndMs playbackSpeed=$playbackSpeed effectiveDurationMs=$effectiveDurationMs " +
                            "segmentStartMs=$timelineStartMs segmentEndMs=$timelineEndMs"
                    )
                    ClipUiModel(
                        id           = entity.id,
                        mediaAssetId = entity.mediaAssetId,
                        label        = "Clip ${entity.orderIndex + 1}",
                        durationMs   = effectiveDurationMs,
                        thumbnailUri = asset?.localUri,
                        transition   = trans,
                        mediaType    = mediaType,
                        timelineStartMs = timelineStartMs,
                        timelineEndMs = timelineEndMs,
                        sourceStartMs = sourceStartMs,
                        sourceEndMs = sourceEndMs,
                        sourceDurationMs = sourceDurationMs,
                        playbackSpeed = playbackSpeed,
                        volume = entity.volume,
                        opacity = entity.opacity,
                        transform = decodeTransform(entity.fitMode)
                    )
                }
                rebuildTimeline(clips, audioCount, textCount, overlayCount)
            }
        }
    }

    private fun rebuildTimeline(
        clips: List<ClipUiModel>,
        audioTrackCount: Int = _uiState.value.audioTrackCount,
        textTrackCount: Int = _uiState.value.textTrackCount,
        overlayTrackCount: Int = _uiState.value.overlayTrackCount
    ) {
        val segments  = buildTimelineSegments(clips)
        val total     = segments.lastOrNull()?.endMs ?: 0L
        val t         = _uiState.value.globalProjectTimeMs.coerceIn(0L, total)
        val seg       = resolveSegment(segments, t)
        val activeTransition = resolveActiveTransition(clips, t)
        val selected = _uiState.value.selectedClipId?.takeIf { id -> clips.any { it.id == id } }
        _uiState.update { it.copy(clips = clips, segments = segments, totalDurationMs = total,
            globalProjectTimeMs = t, currentSegment = seg, selectedClipId = selected, isLoading = false,
            activeTransition = activeTransition,
            audioTrackCount = audioTrackCount, textTrackCount = textTrackCount, overlayTrackCount = overlayTrackCount) }
        Log.d(TAG, "Rebuilt: ${clips.size} clips, total=${total}ms")
        Log.d(
            TAG,
            "PREVIEW_PROJECT_RESTORED clips=${clips.size} playableClips=${clips.count { it.thumbnailUri != null && (it.mediaType == MediaType.VIDEO || it.mediaType == MediaType.OVERLAY_VIDEO) }} " +
                "currentTimelineMs=$t activeClipId=${seg?.clipId} firstClipId=${clips.firstOrNull()?.id}"
        )
        segments.forEachIndexed { i, s ->
            Log.d(
                TAG,
                "segmentTiming index=$i clipId=${s.clipId} effectiveDurationMs=${s.durationMs} " +
                    "segmentStartMs=${s.startMs} segmentEndMs=${s.endMs} currentTimelineMs=$t"
            )
        }
    }

    private fun resolveSegment(segments: List<TimelineSegment>, globalTimeMs: Long): TimelineSegment? {
        if (segments.isEmpty()) return null
        return segments.firstOrNull { it.contains(globalTimeMs) }
    }

    fun play() {
        if (_uiState.value.isPlaying) return
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = true, resumePlaybackAfterPlaylistPrepared = false) }
        Log.d(TAG, "playback started playerPositionSync=true")
    }

    fun pause() {
        playbackJob?.cancel()
        val s = _uiState.value
        Log.d(TAG, "pause currentTimelineMs=${s.globalProjectTimeMs} activeClipId=${s.currentSegment?.clipId}")
        _uiState.update { it.copy(isPlaying = false, resumePlaybackAfterPlaylistPrepared = false) }
    }
    fun togglePlayback() = if (_uiState.value.isPlaying) pause() else play()

    fun onPreparedPlaylistResumeStarted() {
        _uiState.update {
            it.copy(
                isPlaying = true,
                resumePlaybackAfterPlaylistPrepared = false
            )
        }
        Log.d(TAG, "prepared playlist resume started timelineChangeReason=playlistReadyAfterEdit")
    }

    fun seekTo(globalTimeMs: Long, fromUser: Boolean = false) {
        val s = _uiState.value
        val t = globalTimeMs.coerceIn(0L, s.totalDurationMs)
        val seg = resolveSegment(s.segments, t)
        val activeTransition = resolveActiveTransition(s.clips, t)
        Log.d(TAG, "seekTo currentTimelineMs=$t activeClipId=${seg?.clipId} fromUser=$fromUser isPlaying=${s.isPlaying}")
        _uiState.update { it.copy(globalProjectTimeMs = t, currentSegment = seg, activeTransition = activeTransition) }
    }

    fun syncPlaybackFromPlayer(itemIndex: Int, clipId: String?, localPositionMs: Long) {
        val s = _uiState.value
        if (!s.isPlaying || itemIndex < 0 || clipId == null) {
            Log.v(
                TAG,
                "PLAYBACK_SYNC_SUPPRESSED_REASON isPlaying=${s.isPlaying} itemIndex=$itemIndex clipId=$clipId " +
                    "trimmingClipId=${s.trimmingClipId} interactionMode=${s.interactionMode}"
            )
            return
        }
        val segment = s.segments.firstOrNull { it.clipId == clipId } ?: return
        val boundedLocal = localPositionMs.coerceIn(0L, segment.durationMs)
        val projectTime = (segment.startMs + boundedLocal).coerceIn(0L, s.totalDurationMs)
        val resolved = resolveSegment(s.segments, projectTime)
        val activeTransition = resolveActiveTransition(s.clips, projectTime)
        val remainingMs = (segment.endMs - projectTime).coerceAtLeast(0L)
        Log.v(TAG, "PLAYBACK_SYNC_ENABLED clipId=${segment.clipId}")
        Log.v(TAG, "PLAYER_POSITION_TO_TIMELINE_MS playerLocalMs=$boundedLocal currentTimelineMs=$projectTime")
        Log.v(
            TAG,
            "playerPosition itemIndex=$itemIndex clipId=${segment.clipId} localPositionMs=$boundedLocal " +
                "clipEndMs=${segment.durationMs} remainingMsBeforeSwitch=$remainingMs currentTimelineMs=$projectTime"
        )
        _uiState.update {
            it.copy(
                globalProjectTimeMs = projectTime,
                currentSegment = resolved ?: segment,
                activeTransition = activeTransition,
                isPlaying = projectTime < s.totalDurationMs,
                trimmingClipId = null,
                trimGestureActive = false
            )
        }
    }

    fun beginTimelineDrag() {
        if (_uiState.value.isPlaying) pause()
        val s = _uiState.value
        Log.d(TAG, "begin drag currentTimelineMs=${s.globalProjectTimeMs} activeClipId=${s.currentSegment?.clipId}")
    }

    fun scrubTo(globalTimeMs: Long) {
        seekTo(globalTimeMs, fromUser = true)
    }

    fun endTimelineDrag(globalTimeMs: Long) {
        seekTo(globalTimeMs, fromUser = true)
        val s = _uiState.value
        Log.d(TAG, "end drag currentTimelineMs=${s.globalProjectTimeMs} activeClipId=${s.currentSegment?.clipId}")
    }

    fun selectClip(id: String) {
        val s = _uiState.value
        val target = s.clips.firstOrNull { it.id == id }
        val targetTime = target?.timelineStartMs?.coerceIn(0L, s.totalDurationMs) ?: s.globalProjectTimeMs
        val seg = resolveSegment(s.segments, targetTime)
        val activeTransition = resolveActiveTransition(s.clips, targetTime)
        // AUTO-EDIT + AUTO-TRIM: tapping a clip enters EDIT mode and activates
        // trim handles immediately, exactly like CapCut.
        val switchingClip = _uiState.value.selectedClipId != id
        _uiState.update {
            it.copy(
                selectedClipId = id,
                globalProjectTimeMs = targetTime,
                currentSegment = seg,
                activeTransition = activeTransition,
                isPlaying = false,
                toolbarMode = EditorToolbarMode.EDIT,
                interactionMode = EditorInteractionMode.TRIM,
                // Reset trim gesture state when switching to a new clip
                trimmingClipId = if (switchingClip) null else it.trimmingClipId,
                trimPreviewFrameMs = if (switchingClip) null else it.trimPreviewFrameMs,
                trimGestureActive = if (switchingClip) false else it.trimGestureActive,
                trimSessionSide = if (switchingClip) null else it.trimSessionSide,
                trimSessionStartMs = if (switchingClip) null else it.trimSessionStartMs,
                trimSessionEndMs = if (switchingClip) null else it.trimSessionEndMs
            )
        }
        Log.d(TAG, "select clip selectedClipId=$id activeClipId=${seg?.clipId} currentTimelineMs=$targetTime toolbarMode=EDIT")
        Log.d(TAG, "PREVIEW_PLAYHEAD_SYNC reason=clipSelected selectedClipId=$id currentTimelineMs=$targetTime activeClipId=${seg?.clipId}")
    }

    fun onTrimStartDragged(clipId: String, deltaMs: Long) {
        if (deltaMs == 0L) return
        updateTrimPreview(clipId, TrimSide.LEFT, deltaMs)
    }

    fun onTrimEndDragged(clipId: String, deltaMs: Long) {
        if (deltaMs == 0L) return
        updateTrimPreview(clipId, TrimSide.RIGHT, deltaMs)
    }

    fun startTrim(clipId: String, side: TrimSide) {
        val clip = _uiState.value.clips.firstOrNull { it.id == clipId }
        if (clip != null) {
            if (_uiState.value.isPlaying) pause()
            _uiState.update {
                it.copy(
                    trimmingClipId = clipId,
                    selectedClipId = clipId,
                    trimGestureActive = true,
                    trimSessionSide = side,
                    trimSessionStartMs = clip.sourceStartMs,
                    trimSessionEndMs = clip.sourceEndMs,
                    trimPreviewFrameMs = if (side == TrimSide.LEFT) clip.sourceStartMs else (clip.sourceEndMs - 1L).coerceAtLeast(clip.sourceStartMs)
                )
            }
        }
        if (side == TrimSide.LEFT) {
            Log.d(
                TAG,
                "TRIM_LEFT_START clipId=$clipId trimStartMs=${clip?.sourceStartMs} trimEndMs=${clip?.sourceEndMs} " +
                    "timelineStartMs=${clip?.timelineStartMs} timelineEndMs=${clip?.timelineEndMs}"
            )
            Log.d(TAG, "LEFT_TRIM_DRAG_START clipId=$clipId sourceStartMs=${clip?.sourceStartMs} sourceEndMs=${clip?.sourceEndMs}")
        } else {
            Log.d(
                TAG,
                "TRIM_RIGHT_START clipId=$clipId trimStartMs=${clip?.sourceStartMs} trimEndMs=${clip?.sourceEndMs} " +
                    "timelineStartMs=${clip?.timelineStartMs} timelineEndMs=${clip?.timelineEndMs}"
            )
        }
        Log.d(
            TAG,
            "startTrim side=$side clipId=$clipId sourceStartMs=${clip?.sourceStartMs} sourceEndMs=${clip?.sourceEndMs}"
        )
    }

    fun updateTrimPreview(clipId: String, side: TrimSide, deltaMs: Long) {
        if (_uiState.value.trimmingClipId != clipId || _uiState.value.trimSessionSide != side) {
            startTrim(clipId, side)
        }
        val sessionStart = _uiState.value.trimSessionStartMs
        val sessionEnd = _uiState.value.trimSessionEndMs
        val playheadBeforeMs = _uiState.value.globalProjectTimeMs
        updateTrimmedClip(clipId) { clip ->
            val beforeStart = sessionStart ?: clip.sourceStartMs
            val beforeEnd = sessionEnd ?: clip.sourceEndMs
            when (side) {
                TrimSide.LEFT -> {
                    val sourceDeltaMs = (deltaMs * clip.playbackSpeed).roundToLong()
                    val maxStart = (beforeEnd - MIN_TRIM_DURATION_MS).coerceAtLeast(0L)
                    val newStart = (beforeStart + sourceDeltaMs).coerceIn(0L, maxStart)
                    val newDurationMs = ((beforeEnd - newStart).coerceAtLeast(MIN_TRIM_DURATION_MS) / clip.playbackSpeed)
                        .roundToLong()
                        .coerceAtLeast(MIN_TRIM_DURATION_MS)
                    val updated = clip.copy(
                        sourceStartMs = newStart,
                        sourceEndMs = beforeEnd,
                        durationMs = newDurationMs
                    )
                    val visibleStartMs = clip.timelineStartMs + ((newStart - beforeStart).coerceAtLeast(0L) / clip.playbackSpeed).roundToLong()
                    Log.d(
                        TAG,
                        "TRIM_LEFT_UPDATE clipId=$clipId trimStartMs=$newStart trimEndMs=$beforeEnd " +
                            "visibleStartMs=$visibleStartMs visibleEndStableMs=${clip.timelineEndMs} " +
                            "deltaMs=$deltaMs sourceDeltaMs=$sourceDeltaMs"
                    )
                    Log.d(
                        TAG,
                        "LEFT_TRIM_HANDLE_DRAG clipId=$clipId sourceStartMs before=$beforeStart after=${updated.sourceStartMs} " +
                            "sourceEndMs before=$beforeEnd after=${updated.sourceEndMs}"
                    )
                    Log.d(TAG, "LEFT_TRIM_DELTA_MS clipId=$clipId deltaMs=$deltaMs sourceDeltaMs=$sourceDeltaMs speed=${clip.playbackSpeed}")
                    Log.d(TAG, "LEFT_TRIM_PREVIEW_START clipId=$clipId value=${updated.sourceStartMs}")
                    Log.d(TAG, "TRIM_PREVIEW_SYNC side=LEFT clipId=$clipId previewFrameMs=${updated.sourceStartMs}")
                    updated to updated.sourceStartMs
                }
                TrimSide.RIGHT -> {
                    val sourceDeltaMs = (deltaMs * clip.playbackSpeed).roundToLong()
                    val minEnd = beforeStart + MIN_TRIM_DURATION_MS
                    val maxEnd = clip.sourceDurationMs.coerceAtLeast(minEnd)
                    val newEnd = (beforeEnd + sourceDeltaMs).coerceIn(minEnd, maxEnd)
                    val newDurationMs = ((newEnd - beforeStart).coerceAtLeast(MIN_TRIM_DURATION_MS) / clip.playbackSpeed)
                        .roundToLong()
                        .coerceAtLeast(MIN_TRIM_DURATION_MS)
                    val updated = clip.copy(
                        sourceStartMs = beforeStart,
                        sourceEndMs = newEnd,
                        durationMs = newDurationMs
                    )
                    Log.d(
                        TAG,
                        "TRIM_RIGHT_UPDATE clipId=$clipId trimStartMs=$beforeStart trimEndMs=$newEnd " +
                            "visibleStartStableMs=${clip.timelineStartMs} deltaMs=$deltaMs sourceDeltaMs=$sourceDeltaMs"
                    )
                    Log.d(
                        TAG,
                        "RIGHT_TRIM_HANDLE_DRAG clipId=$clipId sourceStartMs before=$beforeStart after=${updated.sourceStartMs} " +
                            "sourceEndMs before=$beforeEnd after=${updated.sourceEndMs}"
                    )
                    Log.d(TAG, "RIGHT_TRIM_DELTA_MS clipId=$clipId deltaMs=$deltaMs sourceDeltaMs=$sourceDeltaMs speed=${clip.playbackSpeed}")
                    Log.d(TAG, "TRIM_PREVIEW_SYNC side=RIGHT clipId=$clipId previewFrameMs=${(updated.sourceEndMs - 1L).coerceAtLeast(updated.sourceStartMs)}")
                    updated to (updated.sourceEndMs - 1L).coerceAtLeast(updated.sourceStartMs)
                }
            }
        }
        val updated = _uiState.value.clips.firstOrNull { it.id == clipId }
        if (updated != null && side == TrimSide.LEFT && sessionStart != null) {
            val visibleStartMs = updated.timelineStartMs +
                ((updated.sourceStartMs - sessionStart).coerceAtLeast(0L) / updated.playbackSpeed).roundToLong()
            if (_uiState.value.globalProjectTimeMs < visibleStartMs) {
                val clampedMs = visibleStartMs.coerceIn(0L, _uiState.value.totalDurationMs)
                val seg = resolveSegment(_uiState.value.segments, clampedMs)
                _uiState.update {
                    it.copy(
                        globalProjectTimeMs = clampedMs,
                        currentSegment = seg,
                        activeTransition = resolveActiveTransition(it.clips, clampedMs)
                    )
                }
                Log.d(TAG, "TRIM_PLAYHEAD_CLAMP side=LEFT clipId=$clipId beforeMs=$playheadBeforeMs afterMs=$clampedMs clamped=true")
            } else {
                Log.d(TAG, "TRIM_PLAYHEAD_CLAMP side=LEFT clipId=$clipId beforeMs=$playheadBeforeMs afterMs=${_uiState.value.globalProjectTimeMs} clamped=false")
            }
        } else if (side == TrimSide.RIGHT) {
            Log.d(TAG, "TRIM_PLAYHEAD_CLAMP side=RIGHT clipId=$clipId beforeMs=$playheadBeforeMs afterMs=${_uiState.value.globalProjectTimeMs} clamped=false")
        }
    }

    fun commitTrim(clipId: String, side: TrimSide) {
        Log.d(TAG, "commitTrim side=$side clipId=$clipId")
        val clip = _uiState.value.clips.firstOrNull { it.id == clipId }
        if (side == TrimSide.LEFT) {
            Log.d(TAG, "TRIM_LEFT_COMMIT clipId=$clipId trimStartMs=${clip?.sourceStartMs} trimEndMs=${clip?.sourceEndMs} durationMs=${clip?.durationMs}")
            Log.d(TAG, "LEFT_TRIM_DRAG_END clipId=$clipId sourceStartMs=${clip?.sourceStartMs} sourceEndMs=${clip?.sourceEndMs}")
        } else {
            Log.d(TAG, "TRIM_RIGHT_COMMIT clipId=$clipId trimStartMs=${clip?.sourceStartMs} trimEndMs=${clip?.sourceEndMs} durationMs=${clip?.durationMs}")
        }
        Log.d(TAG, "TRIM_ENDED_CLEAR_FLAGS clipId=$clipId side=$side")
        onTrimFinished(clipId)
    }

    fun cancelTrim() {
        _uiState.update {
            it.copy(
                trimmingClipId = null,
                trimPreviewFrameMs = null,
                trimGestureActive = false,
                trimSessionSide = null,
                trimSessionStartMs = null,
                trimSessionEndMs = null
            )
        }
        Log.d(TAG, "cancelTrim")
    }

    fun onTrimFinished(clipId: String) {
        val pid = currentProjectId ?: return
        val clip = _uiState.value.clips.firstOrNull { it.id == clipId } ?: return
        val finalPreviewFrameMs = _uiState.value.trimPreviewFrameMs
        val selectedBefore = _uiState.value.selectedClipId
        val committedClips = _uiState.value.clips.withRecalculatedTimelineBounds()
        rebuildTimeline(committedClips)
        _uiState.update {
            it.copy(
                trimmingClipId = null,
                trimPreviewFrameMs = finalPreviewFrameMs,
                trimGestureActive = false,
                trimSessionSide = null,
                trimSessionStartMs = null,
                trimSessionEndMs = null,
                preserveTimelineScrollVersion = it.preserveTimelineScrollVersion + 1L
            )
        }
        Log.d(TAG, "trimDragEnded clip=$clipId")
        viewModelScope.launch {
            val before = dao.getTimelineForProjectOnce(pid)
            val item = before.find { it.id == clipId } ?: return@launch
            dao.upsertTimelineItem(
                item.copy(
                    endMs = item.startMs + clip.durationMs.coerceAtLeast(MIN_TRIM_DURATION_MS),
                    trimStartMs = clip.sourceStartMs.coerceAtLeast(0L),
                    trimEndMs = clip.sourceEndMs.coerceAtLeast(clip.sourceStartMs + MIN_TRIM_DURATION_MS)
                )
            )
            normalizePrimaryTimeline(pid)
            recordHistory(pid, "Trim", before, dao.getTimelineForProjectOnce(pid), selectedBefore, clipId)
            Log.d(TAG, "timelineCommitted clip=$clipId")
            Log.d(TAG, "trim finished clip=$clipId start=${clip.sourceStartMs} end=${clip.sourceEndMs} duration=${clip.durationMs}")
        }
    }

    fun openTransitionPicker(clipId: String) = _uiState.update { it.copy(pickerOpenForClipId = clipId) }
    fun closeTransitionPicker() {
        _uiState.update { it.copy(pickerOpenForClipId = null) }
        Log.d(TAG, "TRANSITION_PANEL_CLOSED")
    }

    fun setToolbarMode(mode: EditorToolbarMode) {
        _uiState.update { it.copy(toolbarMode = mode) }
    }

    fun onPrimaryToolClicked(label: String) {
        if (label == "Edit") {
            setToolbarMode(EditorToolbarMode.EDIT)
        }
    }

    fun onEditToolClicked(action: EditToolAction) {
        // SPLIT_ADJUST mode removed — split is instant like CapCut.
        // No boundary commit needed before handling tool actions.
        if (action == EditToolAction.Split) {
            val s = _uiState.value
            Log.d(
                TAG,
                "SPLIT_ACTION_DISPATCHED source=viewModel selectedClipId=${s.selectedClipId} " +
                    "activeClipId=${s.currentSegment?.clipId} playheadMs=${s.globalProjectTimeMs}"
            )
        }
        when (action) {
            EditToolAction.Back -> _uiState.update {
                it.copy(
                    toolbarMode = EditorToolbarMode.PRIMARY,
                    selectedClipId = null,
                    interactionMode = EditorInteractionMode.NONE,
                    trimmingClipId = null,
                    trimPreviewFrameMs = null,
                    trimGestureActive = false,
                    trimSessionSide = null,
                    trimSessionStartMs = null,
                    trimSessionEndMs = null,
                    splitAdjustClipAId = null,
                    splitAdjustClipBId = null,
                    activeSplitLeftClipId = null,
                    activeSplitRightClipId = null,
                    previewSplitBoundaryMs = null
                )
            }
            EditToolAction.Undo -> undo()
            EditToolAction.Redo -> redo()
            EditToolAction.Trim -> enterTrimMode()
            EditToolAction.Split -> splitClipAtPlayhead()
            EditToolAction.Transition -> openTransitionForSelectedBoundary()
            EditToolAction.Volume -> emitEffect(TimelineEditorEffect.OpenVolumeSheet)
            EditToolAction.Delete -> deleteSelectedClip()
            EditToolAction.Duplicate -> duplicateSelectedClip()
            EditToolAction.Speed -> emitEffect(TimelineEditorEffect.OpenSpeedSheet)
            EditToolAction.Replace -> emitEffect(TimelineEditorEffect.OpenReplacePicker)
            EditToolAction.Crop, EditToolAction.Transform -> emitEffect(TimelineEditorEffect.OpenTransformSheet)
            EditToolAction.Animations,
            EditToolAction.Effects,
            EditToolAction.Filters,
            EditToolAction.Adjust,
            EditToolAction.Overlay -> emitEffect(TimelineEditorEffect.ShowPlaceholder(action.label))
            EditToolAction.RemoveBg,
            EditToolAction.AiRemove,
            EditToolAction.AiExpand,
            EditToolAction.AiRemix,
            EditToolAction.EyeContact,
            EditToolAction.Relight,
            EditToolAction.LipSync,
            EditToolAction.AutoReframe,
            EditToolAction.Stabilize,
            EditToolAction.CameraTracking,
            EditToolAction.IsolateVoice,
            EditToolAction.EnhanceVoice,
            EditToolAction.VideoTranslator -> emitEffect(TimelineEditorEffect.ShowComingSoon(action.label))
            else -> emitEffect(TimelineEditorEffect.ShowPlaceholder(action.label))
        }
    }

    private fun enterTrimMode() {
        val target = selectedOrActiveClip() ?: return
        if (_uiState.value.isPlaying) pause()
        _uiState.update {
            it.copy(
                selectedClipId = target.id,
                interactionMode = EditorInteractionMode.TRIM,
                trimmingClipId = null,
                trimPreviewFrameMs = null
            )
        }
        Log.d(TAG, "trim mode entered clipId=${target.id} sourceStartMs=${target.sourceStartMs} sourceEndMs=${target.sourceEndMs}")
    }

    private fun emitEffect(effect: TimelineEditorEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    fun undo() {
        viewModelScope.launch {
            val playheadBeforeMs = _uiState.value.globalProjectTimeMs
            val handled = historyRegistry.undo()
            if (!handled) return@launch
            Log.d(TAG, "PLAYHEAD_POSITION action=UNDO beforeMs=$playheadBeforeMs afterMs=${_uiState.value.globalProjectTimeMs}")
            Log.d(TAG, "PREVIEW_SYNC action=UNDO currentTimelineMs=${_uiState.value.globalProjectTimeMs} selectedClipId=${_uiState.value.selectedClipId}")
            Log.d(TAG, "TIMELINE_SYNC action=UNDO autoScroll=false preserveTimelineScrollVersion=${_uiState.value.preserveTimelineScrollVersion}")
            Log.d(TAG, "undo action=sharedHistory")
        }
    }

    fun redo() {
        viewModelScope.launch {
            val playheadBeforeMs = _uiState.value.globalProjectTimeMs
            val handled = historyRegistry.redo()
            if (!handled) return@launch
            Log.d(TAG, "PLAYHEAD_POSITION action=REDO beforeMs=$playheadBeforeMs afterMs=${_uiState.value.globalProjectTimeMs}")
            Log.d(TAG, "PREVIEW_SYNC action=REDO currentTimelineMs=${_uiState.value.globalProjectTimeMs} selectedClipId=${_uiState.value.selectedClipId}")
            Log.d(TAG, "TIMELINE_SYNC action=REDO autoScroll=false preserveTimelineScrollVersion=${_uiState.value.preserveTimelineScrollVersion}")
            Log.d(TAG, "redo action=sharedHistory")
        }
    }

    fun openTransitionForSelectedBoundary() {
        val clips = _uiState.value.clips.sortedBy { it.timelineStartMs }
        if (clips.size < 2) return
        val selectedId = selectedClipId()
        val selectedIndex = clips.indexOfFirst { it.id == selectedId }
        val boundaryClip = when {
            selectedIndex in 0 until clips.lastIndex -> clips[selectedIndex]
            selectedIndex == clips.lastIndex -> clips.getOrNull(selectedIndex - 1)
            else -> _uiState.value.currentSegment?.clipId?.let { currentId ->
                val currentIndex = clips.indexOfFirst { it.id == currentId }
                clips.getOrNull(currentIndex.coerceIn(0, clips.lastIndex - 1))
            } ?: clips.first()
        } ?: return
        openTransitionPicker(boundaryClip.id)
        Log.d(TAG, "openTransitionForSelectedBoundary fromClipId=${boundaryClip.id}")
    }

    fun applyTransitionToClip(
        clipId: String,
        type: TransitionType,
        durationMs: Long = 500L,
        closePanel: Boolean = true
    ) {
        val pid = currentProjectId ?: return
        viewModelScope.launch {
            val selectedBefore = _uiState.value.selectedClipId
            val before = dao.getTimelineForProjectOnce(pid)
            val item = before.find { it.id == clipId } ?: return@launch
            val cleanDuration = if (type == TransitionType.NONE) 0L else normalizeTransitionDurationForSave(durationMs)
            val cleanType = if (type == TransitionType.NONE) null else type.name
            dao.upsertTimelineItem(
                item.copy(
                    transitionType = cleanType,
                    transitionDurationMs = cleanDuration.takeIf { cleanType != null }
                )
            )
            Log.d(
                TAG,
                "TRANSITION_DURATION_SAVED fromClipId=$clipId transitionType=${cleanType ?: "NONE"} " +
                    "rawDurationMs=$durationMs transitionDurationMs=$cleanDuration"
            )
            val after = dao.getTimelineForProjectOnce(pid)
            recordHistory(pid, "Transition", before, after, selectedBefore, clipId)
            val orderedClips = _uiState.value.clips.sortedBy { it.timelineStartMs }
            val boundaryIndex = orderedClips.indexOfFirst { it.id == clipId }
            val boundaryClip = orderedClips.getOrNull(boundaryIndex)
            val toClip = orderedClips.getOrNull(boundaryIndex + 1)
            val previewStartMs = boundaryClip?.let {
                (it.timelineEndMs - cleanDuration).coerceAtLeast(it.timelineStartMs)
            } ?: _uiState.value.globalProjectTimeMs
            _uiState.update {
                val updatedClips = it.clips.map { clip ->
                    if (clip.id == clipId) {
                        clip.copy(
                            transition = if (type == TransitionType.NONE) {
                                null
                            } else {
                                Transition(type, cleanDuration, type.isPremium)
                            }
                        )
                    } else {
                        clip
                    }
                }
                it.copy(
                    pickerOpenForClipId = if (closePanel) null else clipId,
                    clips = updatedClips,
                    selectedClipId = clipId,
                    globalProjectTimeMs = previewStartMs,
                    activeTransition = resolveActiveTransition(updatedClips, previewStartMs),
                    isPlaying = type != TransitionType.NONE
                )
            }
            if (closePanel) {
                Log.d(TAG, "TRANSITION_CONFIRMED fromClipId=$clipId transitionType=${cleanType ?: "NONE"} transitionDurationMs=$cleanDuration")
                Log.d(TAG, "TRANSITION_PANEL_CLOSED")
            } else {
                Log.d(TAG, "TRANSITION_PANEL_REOPEN_BLOCKED fromClipId=$clipId livePreview=true")
            }
            Log.d(
                TAG,
                "TRANSITION_APPLIED fromClipId=$clipId toClipId=${toClip?.id} transitionType=${cleanType ?: "NONE"} " +
                    "transitionDurationMs=$cleanDuration transitionTypeSaved=$cleanType transitionDurationSaved=$cleanDuration " +
                    "undoable=true previewStartMs=$previewStartMs"
            )
        }
    }

    fun confirmTransitionSelection(clipId: String, type: TransitionType, durationMs: Long) {
        Log.d(TAG, "TRANSITION_CONFIRM_CLICKED fromClipId=$clipId transitionType=$type transitionDurationMs=$durationMs")
        applyTransitionToClip(clipId, type, durationMs, closePanel = true)
    }

    fun applyTransitionToAll(type: TransitionType, durationMs: Long = 300L) {
        val pid = currentProjectId ?: return
        viewModelScope.launch {
            val selectedBefore = _uiState.value.selectedClipId
            val before = dao.getTimelineForProjectOnce(pid)
            val assets = app.database.mediaAssetDao().getAssetsForProjectOnce(pid).associateBy { it.id }
            val primary = before.filter { isPrimaryVisual(assets[it.mediaAssetId]?.mediaType) }.sortedBy { it.orderIndex }
            val transitionClipIds = primary.dropLast(1).map { it.id }.toSet()
            val cleanDuration = if (type == TransitionType.NONE) 0L else normalizeTransitionDurationForSave(durationMs)
            val cleanType = if (type == TransitionType.NONE) null else type.name
            val updated = before.map { item ->
                if (item.id in transitionClipIds) {
                    item.copy(
                        transitionType = cleanType,
                        transitionDurationMs = cleanDuration.takeIf { cleanType != null }
                    )
                } else {
                    item
                }
            }
            dao.upsertAll(updated)
            Log.d(
                TAG,
                "TRANSITION_DURATION_SAVED transitionScope=ALL transitionType=${cleanType ?: "NONE"} " +
                    "rawDurationMs=$durationMs transitionDurationMs=$cleanDuration count=${transitionClipIds.size}"
            )
            val after = dao.getTimelineForProjectOnce(pid)
            recordHistory(pid, "Transition All", before, after, selectedBefore, selectedBefore)
            _uiState.update {
                val transition = if (type == TransitionType.NONE) null else Transition(type, cleanDuration, type.isPremium)
                val transitionClipIdsSet = transitionClipIds
                val updatedClips = it.clips.map { clip ->
                    if (clip.id in transitionClipIdsSet) clip.copy(transition = transition) else clip
                }
                it.copy(
                    clips = updatedClips,
                    activeTransition = resolveActiveTransition(updatedClips, it.globalProjectTimeMs)
                )
            }
            Log.d(TAG, "transitionAppliedAll transitionType=${cleanType ?: "NONE"} transitionDurationMs=$cleanDuration count=${transitionClipIds.size}")
        }
    }

    fun splitClipAtPlayhead() = splitSelectedAtPlayhead()

    fun splitSelectedAtPlayhead() {
        val pid = currentProjectId ?: return
        val clip = selectedOrActiveClip() ?: return
        val stateBefore = _uiState.value
        val selectedBefore = stateBefore.selectedClipId
        val playheadBeforeMs = stateBefore.globalProjectTimeMs
        val currentSegmentBefore = stateBefore.currentSegment
        val activeTransitionBefore = stateBefore.activeTransition
        val splitOffset = (playheadBeforeMs - clip.timelineStartMs)
            .coerceIn(0L, clip.durationMs)
        if (splitOffset <= MIN_EDIT_SLICE_MS || clip.durationMs - splitOffset <= MIN_EDIT_SLICE_MS) {
            Log.d(TAG, "split ignored splitOffset=$splitOffset duration=${clip.durationMs}")
            return
        }
        Log.d(
            TAG,
            "SPLIT_BEFORE clipId=${clip.id} selectedClipId=$selectedBefore playheadMs=$playheadBeforeMs " +
                "clipStartMs=${clip.timelineStartMs} clipEndMs=${clip.timelineEndMs} splitOffsetMs=$splitOffset " +
                "sourceFrameMs=${clip.sourceStartMs + (splitOffset * clip.playbackSpeed).roundToLong()} " +
                "activeClipId=${currentSegmentBefore?.clipId} activeTransitionType=${activeTransitionBefore?.transitionType}"
        )
        viewModelScope.launch {
            val before = dao.getTimelineForProjectOnce(pid)
            val assets = app.database.mediaAssetDao().getAssetsForProjectOnce(pid).associateBy { it.id }
            val primary = before
                .filter { isPrimaryVisual(assets[it.mediaAssetId]?.mediaType) }
                .sortedBy { it.orderIndex }
            val originalIndex = primary.indexOfFirst { it.id == clip.id }
            val src = primary.getOrNull(originalIndex) ?: return@launch
            val sourceSplit = (clip.sourceStartMs + (splitOffset * clip.playbackSpeed).roundToLong())
                .coerceIn(clip.sourceStartMs + MIN_EDIT_SLICE_MS, clip.sourceEndMs - MIN_EDIT_SLICE_MS)
            val leftDurationMs = splitOffset.coerceAtLeast(MIN_EDIT_SLICE_MS)
            val rightDurationMs = (clip.durationMs - splitOffset).coerceAtLeast(MIN_EDIT_SLICE_MS)
            val first = src.copy(
                endMs = src.startMs + leftDurationMs,
                trimStartMs = clip.sourceStartMs,
                trimEndMs = sourceSplit,
                transitionType = null,
                transitionDurationMs = null
            )
            val second = src.copy(
                id = UUID.randomUUID().toString(),
                orderIndex = src.orderIndex + 1,
                startMs = first.endMs,
                endMs = first.endMs + rightDurationMs,
                trimStartMs = sourceSplit,
                trimEndMs = clip.sourceEndMs
            )
            val reorderedPrimary = primary.toMutableList().apply {
                removeAt(originalIndex)
                add(originalIndex, first)
                add(originalIndex + 1, second)
            }
            val shiftedClips = reorderedPrimary.drop(originalIndex + 2).map { it.id }
            val finalPrimary = layoutPrimaryTimeline(reorderedPrimary)
            dao.upsertAll(finalPrimary)
            val left = finalPrimary[originalIndex]
            val right = finalPrimary[originalIndex + 1]
            val splitTimelineMs = right.startMs
            val splitSegment = TimelineSegment(
                clipId = right.id,
                mediaAssetId = right.mediaAssetId,
                localUri = clip.thumbnailUri,
                startMs = right.startMs,
                endMs = right.endMs,
                thumbnailUri = clip.thumbnailUri,
                label = clip.label,
                transition = null
            )
            val finalPrimaryById = finalPrimary.associateBy { it.id }
            val finalUiClips = stateBefore.clips.flatMap { uiClip ->
                if (uiClip.id == clip.id) {
                    listOf(
                        clip.copy(
                            id = left.id,
                            durationMs = leftDurationMs,
                            transition = null,
                            timelineStartMs = left.startMs,
                            timelineEndMs = left.endMs,
                            sourceEndMs = sourceSplit
                        ),
                        clip.copy(
                            id = right.id,
                            durationMs = rightDurationMs,
                            transition = clip.transition,
                            timelineStartMs = right.startMs,
                            timelineEndMs = right.endMs,
                            sourceStartMs = sourceSplit
                        )
                    )
                } else {
                    listOf(uiClip)
                }
            }.map { uiClip ->
                finalPrimaryById[uiClip.id]?.let { entity ->
                    uiClip.copy(
                        timelineStartMs = entity.startMs,
                        timelineEndMs = entity.endMs,
                        durationMs = (entity.endMs - entity.startMs).coerceAtLeast(1L)
                    )
                } ?: uiClip
            }.sortedBy { it.timelineStartMs }
            val finalSegments = buildTimelineSegments(finalUiClips)
            val finalActiveTransition = resolveActiveTransition(finalUiClips, playheadBeforeMs)
            // CAPCUT SPLIT: instant split at the playhead, select the right clip.
            // No SPLIT_ADJUST mode - split is final immediately.
            _uiState.update {
                it.copy(
                    clips = finalUiClips,
                    segments = finalSegments,
                    totalDurationMs = finalSegments.lastOrNull()?.endMs ?: it.totalDurationMs,
                    selectedClipId = second.id,
                    globalProjectTimeMs = playheadBeforeMs,
                    currentSegment = splitSegment,
                    isPlaying = false,
                    resumePlaybackAfterPlaylistPrepared = stateBefore.isPlaying,
                    toolbarMode = EditorToolbarMode.EDIT,
                    interactionMode = EditorInteractionMode.NONE,
                    splitAdjustClipAId = null,
                    splitAdjustClipBId = null,
                    activeSplitLeftClipId = null,
                    activeSplitRightClipId = null,
                    trimmingClipId = null,
                    trimPreviewFrameMs = null,
                    trimGestureActive = false,
                    trimSessionSide = null,
                    trimSessionStartMs = null,
                    trimSessionEndMs = null,
                    previewSplitBoundaryMs = null,
                    activeTransition = finalActiveTransition,
                    preserveTimelineScrollVersion = it.preserveTimelineScrollVersion + 1L
                )
            }
            recordHistory(pid, "Split", before, dao.getTimelineForProjectOnce(pid), selectedBefore, second.id)
            val sourceFrameAfterMs = right.trimStartMs
            Log.d(
                TAG,
                "SPLIT_AFTER leftClipId=${left.id} rightClipId=${right.id} splitAtMs=$splitTimelineMs " +
                    "playheadMs=$playheadBeforeMs selectedClipId=${second.id} leftDurationMs=$leftDurationMs " +
                    "rightDurationMs=$rightDurationMs clipCountBefore=${primary.size} clipCountAfter=${finalPrimary.size}"
            )
            Log.d(
                TAG,
                "PLAYHEAD_POSITION beforeMs=$playheadBeforeMs afterMs=$playheadBeforeMs stationary=${playheadBeforeMs == splitTimelineMs}"
            )
            Log.d(
                TAG,
                "PREVIEW_SYNC beforeClipId=${currentSegmentBefore?.clipId} afterClipId=${right.id} " +
                    "beforeSourceFrameMs=$sourceSplit afterSourceFrameMs=$sourceFrameAfterMs unchanged=${sourceSplit == sourceFrameAfterMs}"
            )
            Log.d(
                TAG,
                "TIMELINE_SYNC autoScroll=false preserveTimelineScrollVersion=${_uiState.value.preserveTimelineScrollVersion} " +
                    "splitAtPlayhead=true transitionEngineTouched=false mediaStoreTouched=false"
            )
            Log.d(
                TAG,
                "split completed activeClipId=${right.id} activeOrderIndex=${right.orderIndex} currentTimelineMs=$splitTimelineMs " +
                    "localSplitMs=$sourceSplit speed=${clip.playbackSpeed} leftDurationMs=$leftDurationMs rightDurationMs=$rightDurationMs " +
                    "leftSourceRange=${clip.sourceStartMs}-$sourceSplit rightSourceRange=$sourceSplit-${clip.sourceEndMs} " +
                    "leftClipId=${left.id} leftOrderIndex=${left.orderIndex} " +
                    "rightClipId=${right.id} rightOrderIndex=${right.orderIndex} shiftedClips=$shiftedClips " +
                    "finalClipOrder=${finalPrimary.map { "${it.id}:${it.orderIndex}" }} " +
                    "timelineChangeReason=capcutImmediateSplit splitAtPlayhead=true selectedRightClip=true playbackPausedForPlaylistRebuild=true " +
                    "transitionPreservedOnRightClip=${second.transitionType == src.transitionType} internalSplitTransition=false " +
                    "resumePlaybackAfterPlaylistPrepared=${stateBefore.isPlaying}"
            )
            Log.d(TAG, "split clip=${clip.id} at=$splitTimelineMs newClip=${second.id}")
        }
    }

    fun enterSplitAdjustMode(clipAId: String, clipBId: String) {
        pause()
        val clipA = _uiState.value.clips.firstOrNull { it.id == clipAId }
        val clipB = _uiState.value.clips.firstOrNull { it.id == clipBId }
        val boundaryMs = clipA?.timelineEndMs ?: clipB?.timelineStartMs ?: _uiState.value.globalProjectTimeMs
        _uiState.update {
            it.copy(
                interactionMode = EditorInteractionMode.BOUNDARY_TRIM,
                splitAdjustClipAId = clipAId,
                splitAdjustClipBId = clipBId,
                activeSplitLeftClipId = clipAId,
                activeSplitRightClipId = clipBId,
                selectedClipId = null,
                globalProjectTimeMs = boundaryMs.coerceIn(0L, it.totalDurationMs),
                currentSegment = resolveSegment(it.segments, boundaryMs),
                trimPreviewFrameMs = clipB?.sourceStartMs,
                previewSplitBoundaryMs = clipB?.sourceStartMs,
                trimSessionStartMs = clipA?.sourceEndMs,
                trimSessionEndMs = clipB?.sourceStartMs,
                preserveTimelineScrollVersion = it.preserveTimelineScrollVersion + 1L
            )
        }
        Log.d(
            TAG,
            "BOUNDARY_SELECTED leftClipId=$clipAId rightClipId=$clipBId sameSource=${clipA?.mediaAssetId == clipB?.mediaAssetId} " +
                "leftTrimEndMs=${clipA?.sourceEndMs} rightTrimStartMs=${clipB?.sourceStartMs} boundaryMs=$boundaryMs"
        )
        Log.d(TAG, "entering BOUNDARY_TRIM active left clip id=$clipAId active right clip id=$clipBId")
    }

    fun startSplitBoundaryTrim(side: TrimSide) {
        val s = _uiState.value
        val clipAId = s.splitAdjustClipAId ?: return
        val clipBId = s.splitAdjustClipBId ?: return
        val clipA = s.clips.firstOrNull { it.id == clipAId } ?: return
        val clipB = s.clips.firstOrNull { it.id == clipBId } ?: return
        _uiState.update {
            it.copy(
                trimGestureActive = true,
                trimSessionSide = side,
                trimSessionStartMs = clipA.sourceEndMs,
                trimSessionEndMs = clipB.sourceStartMs
            )
        }
        Log.d(
            TAG,
            "BOUNDARY_TRIM_START side=$side leftClipId=$clipAId rightClipId=$clipBId " +
                "leftTrimEndMs=${clipA.sourceEndMs} rightTrimStartMs=${clipB.sourceStartMs}"
        )
    }

    fun startSharedBoundaryTrim() {
        val s = _uiState.value
        val clipAId = s.splitAdjustClipAId ?: return
        val clipBId = s.splitAdjustClipBId ?: return
        val clipA = s.clips.firstOrNull { it.id == clipAId } ?: return
        val clipB = s.clips.firstOrNull { it.id == clipBId } ?: return
        _uiState.update {
            it.copy(
                trimGestureActive = true,
                trimSessionSide = null,
                trimSessionStartMs = clipA.sourceEndMs,
                trimSessionEndMs = clipB.sourceStartMs
            )
        }
        Log.d(
            TAG,
            "BOUNDARY_TRIM_START leftClipId=$clipAId rightClipId=$clipBId " +
                "boundarySourceMs=${clipA.sourceEndMs} rightTrimStartMs=${clipB.sourceStartMs}"
        )
    }

    fun updateSharedBoundaryTrim(deltaMs: Long) {
        if (deltaMs == 0L) return
        val s = _uiState.value
        val clipAId = s.splitAdjustClipAId ?: return
        val clipBId = s.splitAdjustClipBId ?: return
        val clipA = s.clips.firstOrNull { it.id == clipAId } ?: return
        val clipB = s.clips.firstOrNull { it.id == clipBId } ?: return
        val baseBoundary = s.trimSessionStartMs ?: clipA.sourceEndMs
        val sourceDeltaMs = (deltaMs * clipA.playbackSpeed).roundToLong()
        val minBoundary = clipA.sourceStartMs + MIN_SPLIT_SLICE_MS
        val maxBoundary = clipB.sourceEndMs - MIN_SPLIT_SLICE_MS
        val nextBoundary = (baseBoundary + sourceDeltaMs).coerceIn(minBoundary, maxBoundary)
        val updatedClips = s.clips.map { clip ->
            when (clip.id) {
                clipAId -> clip.copy(
                    sourceEndMs = nextBoundary,
                    durationMs = ((nextBoundary - clip.sourceStartMs).coerceAtLeast(MIN_SPLIT_SLICE_MS) / clip.playbackSpeed)
                        .roundToLong()
                        .coerceAtLeast(MIN_SPLIT_SLICE_MS)
                )
                clipBId -> clip.copy(
                    sourceStartMs = nextBoundary,
                    durationMs = ((clip.sourceEndMs - nextBoundary).coerceAtLeast(MIN_SPLIT_SLICE_MS) / clip.playbackSpeed)
                        .roundToLong()
                        .coerceAtLeast(MIN_SPLIT_SLICE_MS)
                )
                else -> clip
            }
        }.withRecalculatedTimelineBounds()
        val segments = buildTimelineSegments(updatedClips)
        val left = updatedClips.firstOrNull { it.id == clipAId } ?: return
        val boundaryTimeMs = left.timelineEndMs.coerceIn(0L, segments.lastOrNull()?.endMs ?: s.totalDurationMs)
        _uiState.update {
            it.copy(
                clips = updatedClips,
                segments = segments,
                totalDurationMs = segments.lastOrNull()?.endMs ?: it.totalDurationMs,
                globalProjectTimeMs = boundaryTimeMs,
                currentSegment = resolveSegment(segments, boundaryTimeMs),
                trimPreviewFrameMs = nextBoundary,
                previewSplitBoundaryMs = nextBoundary,
                activeTransition = resolveActiveTransition(updatedClips, boundaryTimeMs),
                preserveTimelineScrollVersion = it.preserveTimelineScrollVersion + 1L
            )
        }
        Log.d(
            TAG,
            "BOUNDARY_TRIM_UPDATE leftClipId=$clipAId rightClipId=$clipBId boundarySourceMs=$nextBoundary " +
                "boundaryTimeMs=$boundaryTimeMs noGap=true noOverlap=true"
        )
    }

    fun commitSharedBoundaryTrim() {
        commitSplitBoundary(null)
    }

    fun updateSplitBoundaryPreview(side: TrimSide, deltaMs: Long) {
        if (deltaMs == 0L) return
        val s = _uiState.value
        val clipAId = s.splitAdjustClipAId ?: return
        val clipBId = s.splitAdjustClipBId ?: return
        val clipA = s.clips.firstOrNull { it.id == clipAId } ?: return
        val clipB = s.clips.firstOrNull { it.id == clipBId } ?: return
        val leftBaseEnd = s.trimSessionStartMs ?: clipA.sourceEndMs
        val rightBaseStart = s.trimSessionEndMs ?: clipB.sourceStartMs
        val updatedClips = s.clips.map { clip ->
            when {
                side == TrimSide.LEFT && clip.id == clipAId -> {
                    val sourceDeltaMs = (deltaMs * clip.playbackSpeed).roundToLong()
                    val minEnd = clip.sourceStartMs + MIN_SPLIT_SLICE_MS
                    val maxEnd = clip.sourceDurationMs.coerceAtLeast(minEnd)
                    val nextEnd = (leftBaseEnd + sourceDeltaMs).coerceIn(minEnd, maxEnd)
                    clip.copy(
                        sourceEndMs = nextEnd,
                        durationMs = ((nextEnd - clip.sourceStartMs).coerceAtLeast(MIN_SPLIT_SLICE_MS) / clip.playbackSpeed)
                            .roundToLong()
                            .coerceAtLeast(MIN_SPLIT_SLICE_MS)
                    )
                }
                side == TrimSide.RIGHT && clip.id == clipBId -> {
                    val sourceDeltaMs = (deltaMs * clip.playbackSpeed).roundToLong()
                    val minStart = 0L
                    val maxStart = (clip.sourceEndMs - MIN_SPLIT_SLICE_MS).coerceAtLeast(minStart)
                    val nextStart = (rightBaseStart + sourceDeltaMs).coerceIn(minStart, maxStart)
                    clip.copy(
                        sourceStartMs = nextStart,
                        durationMs = ((clip.sourceEndMs - nextStart).coerceAtLeast(MIN_SPLIT_SLICE_MS) / clip.playbackSpeed)
                            .roundToLong()
                            .coerceAtLeast(MIN_SPLIT_SLICE_MS)
                    )
                }
                else -> clip
            }
        }.withRecalculatedTimelineBounds()
        val updatedLeft = updatedClips.firstOrNull { it.id == clipAId } ?: clipA
        val updatedRight = updatedClips.firstOrNull { it.id == clipBId } ?: clipB
        val segments = buildTimelineSegments(updatedClips)
        val boundaryMs = updatedLeft.timelineEndMs.coerceIn(0L, segments.lastOrNull()?.endMs ?: s.totalDurationMs)
        val previewFrameMs = when (side) {
            TrimSide.LEFT -> (updatedLeft.sourceEndMs - 1L).coerceAtLeast(updatedLeft.sourceStartMs)
            TrimSide.RIGHT -> updatedRight.sourceStartMs
        }
        val sourceContinuous = updatedLeft.mediaAssetId == updatedRight.mediaAssetId &&
            updatedLeft.sourceEndMs == updatedRight.sourceStartMs
        _uiState.update {
            it.copy(
                clips = updatedClips,
                segments = segments,
                totalDurationMs = segments.lastOrNull()?.endMs ?: it.totalDurationMs,
                currentSegment = resolveSegment(segments, boundaryMs),
                globalProjectTimeMs = boundaryMs,
                trimPreviewFrameMs = previewFrameMs,
                previewSplitBoundaryMs = previewFrameMs,
                activeTransition = resolveActiveTransition(updatedClips, boundaryMs),
                preserveTimelineScrollVersion = it.preserveTimelineScrollVersion + 1L
            )
        }
        Log.d(
            TAG,
                "${if (side == TrimSide.LEFT) "BOUNDARY_LEFT_UPDATE" else "BOUNDARY_RIGHT_UPDATE"} " +
                    "leftClipId=$clipAId rightClipId=$clipBId deltaMs=$deltaMs " +
                "leftTrimEndMs=${updatedLeft.sourceEndMs} rightTrimStartMs=${updatedRight.sourceStartMs} boundaryMs=$boundaryMs"
        )
        Log.d(TAG, "BOUNDARY_PREVIEW_SYNC side=$side previewFrameMs=$previewFrameMs boundaryMs=$boundaryMs")
        Log.d(TAG, "BOUNDARY_PLAYHEAD_SYNC side=$side boundaryMs=$boundaryMs playheadMs=${_uiState.value.globalProjectTimeMs} clamped=false")
        Log.d(
            TAG,
            "BOUNDARY_JOIN_CONTINUITY_CHECK side=$side noGap=true noOverlap=true sameSource=${updatedLeft.mediaAssetId == updatedRight.mediaAssetId} " +
                "sourceContinuous=$sourceContinuous leftEnd=${updatedLeft.sourceEndMs} rightStart=${updatedRight.sourceStartMs}"
        )
    }

    fun updateSplitBoundaryPreview(deltaMs: Long) {
        updateSplitBoundaryPreview(TrimSide.LEFT, deltaMs)
    }

    fun commitSplitBoundary(side: TrimSide? = null) {
        val pid = currentProjectId ?: return
        val s = _uiState.value
        val clipAId = s.splitAdjustClipAId ?: return
        val clipBId = s.splitAdjustClipBId ?: return
        val clipA = s.clips.firstOrNull { it.id == clipAId } ?: return
        val clipB = s.clips.firstOrNull { it.id == clipBId } ?: return
        val selectedBefore = s.selectedClipId
        val committedClips = s.clips.withRecalculatedTimelineBounds()
        val segments = buildTimelineSegments(committedClips)
        val boundaryMs = clipA.timelineEndMs.coerceIn(0L, segments.lastOrNull()?.endMs ?: s.totalDurationMs)
        val sourceContinuous = clipA.mediaAssetId == clipB.mediaAssetId && clipA.sourceEndMs == clipB.sourceStartMs
        _uiState.update {
            it.copy(
                clips = committedClips,
                segments = segments,
                totalDurationMs = segments.lastOrNull()?.endMs ?: it.totalDurationMs,
                interactionMode = EditorInteractionMode.NONE,
                splitAdjustClipAId = null,
                splitAdjustClipBId = null,
                activeSplitLeftClipId = null,
                activeSplitRightClipId = null,
                trimPreviewFrameMs = null,
                previewSplitBoundaryMs = null,
                trimGestureActive = false,
                trimSessionSide = null,
                trimSessionStartMs = null,
                trimSessionEndMs = null,
                selectedClipId = clipBId,
                globalProjectTimeMs = boundaryMs,
                currentSegment = resolveSegment(segments, boundaryMs),
                preserveTimelineScrollVersion = it.preserveTimelineScrollVersion + 1L
            )
        }
        viewModelScope.launch {
            val before = dao.getTimelineForProjectOnce(pid)
            val itemA = before.find { it.id == clipAId } ?: return@launch
            val itemB = before.find { it.id == clipBId } ?: return@launch
            dao.upsertAll(
                listOf(
                    itemA.copy(
                        endMs = itemA.startMs + clipA.durationMs.coerceAtLeast(MIN_SPLIT_SLICE_MS),
                        trimStartMs = clipA.sourceStartMs,
                        trimEndMs = clipA.sourceEndMs
                    ),
                    itemB.copy(
                        startMs = itemA.startMs + clipA.durationMs.coerceAtLeast(MIN_SPLIT_SLICE_MS),
                        endMs = itemA.startMs + clipA.durationMs.coerceAtLeast(MIN_SPLIT_SLICE_MS) + clipB.durationMs.coerceAtLeast(MIN_SPLIT_SLICE_MS),
                        trimStartMs = clipB.sourceStartMs,
                        trimEndMs = clipB.sourceEndMs
                    )
                )
            )
            normalizePrimaryTimeline(pid)
            recordHistory(pid, "Boundary Trim", before, dao.getTimelineForProjectOnce(pid), selectedBefore, clipBId)
            Log.d(
                TAG,
                "BOUNDARY_TRIM_COMMIT side=${side ?: s.trimSessionSide} leftClipId=$clipAId rightClipId=$clipBId " +
                    "leftTrimEndMs=${clipA.sourceEndMs} rightTrimStartMs=${clipB.sourceStartMs} boundaryMs=$boundaryMs"
            )
            Log.d(
                TAG,
                "BOUNDARY_JOIN_CONTINUITY_CHECK commit=true noGap=true noOverlap=true sameSource=${clipA.mediaAssetId == clipB.mediaAssetId} " +
                    "sourceContinuous=$sourceContinuous leftEnd=${clipA.sourceEndMs} rightStart=${clipB.sourceStartMs}"
            )
        }
    }

    fun onSplitBoundaryDragFinished() {
        val s = _uiState.value
        Log.d(
            TAG,
            "split boundary drag finished mode=${s.interactionMode} activeLeft=${s.activeSplitLeftClipId} " +
                "activeRight=${s.activeSplitRightClipId} boundary=${s.previewSplitBoundaryMs}"
        )
    }

    fun cancelSplitBoundaryAdjustment() {
        _uiState.update {
            it.copy(
                interactionMode = EditorInteractionMode.NONE,
                splitAdjustClipAId = null,
                splitAdjustClipBId = null,
                activeSplitLeftClipId = null,
                activeSplitRightClipId = null,
                trimPreviewFrameMs = null,
                previewSplitBoundaryMs = null,
                trimGestureActive = false,
                trimSessionSide = null,
                trimSessionStartMs = null,
                trimSessionEndMs = null
            )
        }
        Log.d(TAG, "BOUNDARY_TRIM_CANCEL")
        Log.d(TAG, "split adjust cancel")
    }

    fun deleteSelectedClip() {
        val pid = currentProjectId ?: return
        val stateBefore = _uiState.value
        val id = stateBefore.selectedClipId ?: return
        val selectedBefore = stateBefore.selectedClipId
        val deletedClip = stateBefore.clips.firstOrNull { it.id == id }
        val deletedDurationMs = deletedClip?.durationMs ?: 0L
        val oldProjectDurationMs = stateBefore.totalDurationMs
        val oldPlayheadMs = stateBefore.globalProjectTimeMs
        val adjustedPlayheadMs = when {
            deletedClip == null -> oldPlayheadMs
            oldPlayheadMs >= deletedClip.timelineEndMs -> oldPlayheadMs - deletedDurationMs
            oldPlayheadMs >= deletedClip.timelineStartMs -> deletedClip.timelineStartMs
            else -> oldPlayheadMs
        }
        viewModelScope.launch {
            val deleteDbStartMs = System.currentTimeMillis()
            Log.d(
                TAG,
                "rippleDeleteDbStarted deletedClipId=$id oldPlayheadMs=$oldPlayheadMs " +
                    "oldProjectDurationMs=$oldProjectDurationMs wasPlaying=${stateBefore.isPlaying} " +
                    "timelineChangeReason=deleteSelectedClip"
            )
            val before = dao.getTimelineForProjectOnce(pid)
            val assets = app.database.mediaAssetDao().getAssetsForProjectOnce(pid).associateBy { it.id }
            val beforePrimary = before
                .filter { isPrimaryVisual(assets[it.mediaAssetId]?.mediaType) }
                .sortedBy { it.orderIndex }
            val deletedIndex = beforePrimary.indexOfFirst { it.id == id }
            dao.deleteItemById(id)
            normalizePrimaryTimeline(pid)
            val after = dao.getTimelineForProjectOnce(pid)
            val afterPrimary = after
                .filter { isPrimaryVisual(assets[it.mediaAssetId]?.mediaType) }
                .sortedBy { it.orderIndex }
            val deleteDbDurationMs = System.currentTimeMillis() - deleteDbStartMs
            Log.d(
                TAG,
                "rippleDeleteDbFinished deletedClipId=$id dbDurationMs=$deleteDbDurationMs " +
                    "remainingItems=${after.size} remainingPrimary=${afterPrimary.size}"
            )
            val remainingClips = stateBefore.clips
                .filterNot { it.id == id }
                .withRecalculatedTimelineBounds()
            val segments = buildTimelineSegments(remainingClips)
            val newProjectDurationMs = segments.lastOrNull()?.endMs ?: 0L
            val newPlayheadMs = adjustedPlayheadMs.coerceIn(0L, newProjectDurationMs)
            val currentSegment = resolveSegment(segments, newPlayheadMs)
            val nextSelectionId = remainingClips.getOrNull(deletedIndex)?.id
                ?: remainingClips.getOrNull(deletedIndex - 1)?.id
            _uiState.update {
                it.copy(
                    clips = remainingClips,
                    segments = segments,
                    totalDurationMs = newProjectDurationMs,
                    globalProjectTimeMs = newPlayheadMs,
                    currentSegment = currentSegment,
                    isPlaying = false,
                    resumePlaybackAfterPlaylistPrepared = stateBefore.isPlaying,
                    selectedClipId = nextSelectionId,
                    interactionMode = EditorInteractionMode.NONE,
                    splitAdjustClipAId = null,
                    splitAdjustClipBId = null,
                    activeSplitLeftClipId = null,
                    activeSplitRightClipId = null,
                    previewSplitBoundaryMs = null
                )
            }
            recordHistory(pid, "Delete", before, after, selectedBefore, nextSelectionId)
            val playableAfterDelete = remainingClips.count {
                it.thumbnailUri != null &&
                    (it.mediaType == MediaType.VIDEO || it.mediaType == MediaType.OVERLAY_VIDEO)
            }
            Log.d(
                TAG,
                "ripple delete deletedClipId=$id deletedDurationMs=$deletedDurationMs " +
                    "oldProjectDurationMs=$oldProjectDurationMs newProjectDurationMs=$newProjectDurationMs " +
                    "oldPlayheadMs=$oldPlayheadMs newPlayheadMs=$newPlayheadMs " +
                    "playlistItemCountAfterDelete=$playableAfterDelete " +
                    "gapDetectedBetweenSegments=${hasGapBetweenSegments(segments)} " +
                    "timelineChangeReason=deleteSelectedClip playbackPausedForPlaylistRebuild=true " +
                    "resumePlaybackAfterPlaylistPrepared=${stateBefore.isPlaying}"
            )
            Log.d(
                TAG,
                "selected clip deleted clipId=$id deletedIndex=$deletedIndex " +
                    "finalClipOrderAfterDelete=${afterPrimary.map { "${it.id}:${it.orderIndex}" }}"
            )
        }
    }

    fun deleteClip(id: String) {
        val pid = currentProjectId ?: return
        viewModelScope.launch {
            dao.deleteItemById(id)
            normalizePrimaryTimeline(pid)
        }
    }

    fun duplicateClip(id: String) {
        viewModelScope.launch {
            val pid  = currentProjectId ?: return@launch
            val selectedBefore = _uiState.value.selectedClipId
            val before = dao.getTimelineForProjectOnce(pid)
            val assets = app.database.mediaAssetDao().getAssetsForProjectOnce(pid).associateBy { it.id }
            val primary = before
                .filter { isPrimaryVisual(assets[it.mediaAssetId]?.mediaType) }
                .sortedBy { it.orderIndex }
            val src = primary.find { it.id == id } ?: return@launch
            val copyId = UUID.randomUUID().toString()
            val copy = src.copy(id = copyId)
            val reordered = primary.toMutableList().apply {
                add((indexOfFirst { it.id == id } + 1).coerceIn(0, size), copy)
            }
            dao.upsertAll(layoutPrimaryTimeline(reordered))
            normalizePrimaryTimeline(pid)
            _uiState.update { it.copy(selectedClipId = copyId) }
            recordHistory(pid, "Duplicate", before, dao.getTimelineForProjectOnce(pid), selectedBefore, copyId)
        }
    }

    fun duplicateSelectedClip() {
        val id = selectedClipId() ?: return
        duplicateClip(id)
    }

    fun replaceSelectedClip(uri: Uri) {
        val pid = currentProjectId ?: return
        val id = selectedClipId() ?: return
        viewModelScope.launch {
            val item = dao.getTimelineForProjectOnce(pid).find { it.id == id } ?: return@launch
            val oldDuration = (item.endMs - item.startMs).coerceAtLeast(1L)
            val asset = buildAssetFromUri(pid, uri, MediaType.VIDEO) ?: return@launch
            app.database.mediaAssetDao().upsertAsset(asset)
            val replacementDuration = asset.durationMs?.takeIf { it > 0L } ?: oldDuration
            val effectiveDuration = oldDuration.coerceAtMost(replacementDuration)
            dao.upsertTimelineItem(
                item.copy(
                    mediaAssetId = asset.id,
                    endMs = item.startMs + effectiveDuration,
                    trimStartMs = 0L,
                    trimEndMs = effectiveDuration
                )
            )
            normalizePrimaryTimeline(pid)
            Log.d(TAG, "replace clip=$id media=${asset.id} duration=$effectiveDuration")
        }
    }

    fun setSelectedClipSpeed(speed: Float) {
        val pid = currentProjectId ?: return
        val id = selectedClipId() ?: return
        viewModelScope.launch {
            val selectedBefore = _uiState.value.selectedClipId
            val before = dao.getTimelineForProjectOnce(pid)
            val item = before.find { it.id == id } ?: return@launch
            val multiplier = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
            val sourceStart = item.trimStartMs.coerceAtLeast(0L)
            val fallbackDuration = (item.endMs - item.startMs).coerceAtLeast(1L)
            val sourceEnd = item.trimEndMs.takeIf { it > sourceStart } ?: (sourceStart + fallbackDuration)
            val sourceSpan = (sourceEnd - sourceStart).coerceAtLeast(1L)
            val newDuration = (sourceSpan / multiplier).roundToLong().coerceAtLeast(MIN_EDIT_SLICE_MS)
            dao.upsertTimelineItem(
                item.copy(
                    endMs = item.startMs + newDuration,
                    playbackSpeed = multiplier
                )
            )
            normalizePrimaryTimeline(pid)
            val after = dao.getTimelineForProjectOnce(pid)
            _uiState.update { it.copy(selectedClipId = id, resumePlaybackAfterPlaylistPrepared = it.isPlaying, isPlaying = false) }
            recordHistory(pid, "Speed", before, after, selectedBefore, id)
            Log.d(
                TAG,
                "speed clip=$id speed=$multiplier sourceSpanMs=$sourceSpan duration=$newDuration " +
                    "timelineCommitted=true"
            )
        }
    }

    fun setSelectedClipVolume(volume: Float) {
        val id = selectedClipId() ?: return
        val pid = currentProjectId ?: return
        viewModelScope.launch {
            val selectedBefore = _uiState.value.selectedClipId
            val before = dao.getTimelineForProjectOnce(pid)
            val item = before.find { it.id == id } ?: return@launch
            val multiplier = volume.coerceIn(MIN_VOLUME_MULTIPLIER, MAX_VOLUME_MULTIPLIER)
            dao.upsertTimelineItem(item.copy(volume = multiplier))
            val after = dao.getTimelineForProjectOnce(pid)
            _uiState.update { state ->
                state.copy(
                    selectedClipId = id,
                    clips = state.clips.map { clip ->
                        if (clip.id == id) clip.copy(volume = multiplier) else clip
                    }
                )
            }
            recordHistory(pid, "Volume", before, after, selectedBefore, id)
            Log.d(TAG, "volume clip=$id volumeMultiplier=$multiplier timelineDurationUnchanged=true")
        }
    }

    fun setSelectedClipTransform(transform: ClipTransform) {
        val id = selectedClipId() ?: return
        val pid = currentProjectId ?: return
        viewModelScope.launch {
            val item = dao.getTimelineForProjectOnce(pid).find { it.id == id } ?: return@launch
            dao.upsertTimelineItem(item.copy(fitMode = encodeTransform(transform)))
            Log.d(TAG, "transform clip=$id $transform")
        }
    }

    fun addAudio(uri: Uri) {
        addPickedTrack(uri = uri, hint = MediaType.AUDIO, trackIndex = 1)
    }

    fun addOverlay(uri: Uri) {
        val context = getApplication<Application>()
        val mime = context.contentResolver.getType(uri).orEmpty()
        val hint = if (mime.startsWith("video")) MediaType.OVERLAY_VIDEO else MediaType.OVERLAY_IMAGE
        addPickedTrack(uri = uri, hint = hint, trackIndex = 2)
    }

    fun appendMediaToTimeline(uri: Uri) {
        appendMediaToTimeline(listOf(uri))
    }

    fun appendMediaToTimeline(uris: List<Uri>) {
        val pid = currentProjectId ?: return
        val cleanUris = uris.filter { it.toString().isNotBlank() }
        if (cleanUris.isEmpty()) return
        val wasPlaying = _uiState.value.isPlaying
        val selectedBefore = _uiState.value.selectedClipId
        viewModelScope.launch {
            val before = dao.getTimelineForProjectOnce(pid)
            var assetMap = app.database.mediaAssetDao().getAssetsForProjectOnce(pid).associateBy { it.id }
            var primaryTimeline = before
                .filter { isPrimaryVisual(assetMap[it.mediaAssetId]?.mediaType) }
                .sortedBy { it.orderIndex }
            val insertedItems = mutableListOf<TimelineItemEntity>()
            cleanUris.forEachIndexed { offset, uri ->
                val newOrderIndex = (primaryTimeline.maxOfOrNull { it.orderIndex } ?: -1) + 1
                val newStartMs = primaryTimeline.maxOfOrNull { it.endMs } ?: 0L
                val asset = buildAssetFromUri(pid, uri, MediaType.VIDEO) ?: return@forEachIndexed
                if (!isPrimaryVisual(asset.mediaType)) {
                    Log.w(TAG, "ADD_MEDIA_FROM_TIMELINE_PLUS ignoredNonPrimary newMediaUri=$uri type=${asset.mediaType}")
                    return@forEachIndexed
                }
                val duration = asset.durationMs?.takeIf { it > 0L } ?: DEFAULT_OVERLAY_DURATION_MS
                val item = newPrimaryTimelineItem(
                    projectId = pid,
                    assetId = asset.id,
                    orderIndex = newOrderIndex,
                    startMs = newStartMs,
                    duration = duration
                )
                app.database.mediaAssetDao().upsertAsset(asset)
                dao.upsertTimelineItem(item)
                insertedItems += item
                primaryTimeline = (primaryTimeline + item).sortedBy { it.orderIndex }
                assetMap = assetMap + (asset.id to asset)
                Log.d(
                    TAG,
                    "ADD_MEDIA_FROM_TIMELINE_PLUS newMediaUri=$uri newDurationMs=$duration " +
                        "newOrderIndex=$newOrderIndex timelineItemInserted=true timelineItemId=${item.id} " +
                        "sourceStartMs=0 sourceEndMs=$duration durationMs=$duration playbackSpeed=1.0 volumeMultiplier=1.0 " +
                        "batchIndex=$offset"
                )
            }
            if (insertedItems.isEmpty()) return@launch
            normalizePrimaryTimeline(pid)
            val after = dao.getTimelineForProjectOnce(pid)
            val lastInserted = insertedItems.last()
            val timelineReloadedCount = after.count { item ->
                val type = assetMap[item.mediaAssetId]?.mediaType
                isPrimaryVisual(type)
            }
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    resumePlaybackAfterPlaylistPrepared = wasPlaying,
                    selectedClipId = lastInserted.id,
                    globalProjectTimeMs = lastInserted.startMs,
                    interactionMode = EditorInteractionMode.NONE,
                    lastAddedClipId = lastInserted.id
                )
            }
            recordHistory(pid, "Add Media", before, after, selectedBefore, lastInserted.id)
            Log.d(
                TAG,
                "ADD_MEDIA_FROM_TIMELINE_PLUS completed insertedCount=${insertedItems.size} " +
                    "timelineReloadedCount=$timelineReloadedCount lastAddedClipId=${lastInserted.id} " +
                    "newClipStartMs=${lastInserted.startMs} newClipEndMs=${lastInserted.endMs} " +
                    "wasPlaying=$wasPlaying"
            )
        }
    }

    fun addTextOverlay(text: String) {
        val pid = currentProjectId ?: return
        val clean = text.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val asset = MediaAssetEntity(
                id = id,
                projectId = pid,
                mediaType = MediaType.LOGO.name,
                localUri = "text://$clean",
                remoteUrl = null,
                durationMs = DEFAULT_OVERLAY_DURATION_MS,
                fileSizeBytes = clean.length.toLong(),
                mimeType = TEXT_OVERLAY_MIME,
                createdAt = System.currentTimeMillis()
            )
            app.database.mediaAssetDao().upsertAsset(asset)
            dao.upsertTimelineItem(newTimelineItem(pid, id, 3, DEFAULT_OVERLAY_DURATION_MS))
            Log.d(TAG, "text overlay added currentTimelineMs=${_uiState.value.globalProjectTimeMs}")
        }
    }

    fun reorderSelectedClipAfter(targetClipId: String) {
        val pid = currentProjectId ?: return
        val selectedId = selectedClipId() ?: return
        if (selectedId == targetClipId) return
        val selectedBefore = _uiState.value.selectedClipId
        viewModelScope.launch {
            val before = dao.getTimelineForProjectOnce(pid)
            val items = before.toMutableList()
            val assets = app.database.mediaAssetDao().getAssetsForProjectOnce(pid).associateBy { it.id }
            val primary = items
                .filter { isPrimaryVisual(assets[it.mediaAssetId]?.mediaType) }
                .sortedBy { it.orderIndex }
                .toMutableList()
            val moving = primary.firstOrNull { it.id == selectedId } ?: return@launch
            primary.removeAll { it.id == selectedId }
            val targetIndex = primary.indexOfFirst { it.id == targetClipId }.takeIf { it >= 0 } ?: return@launch
            primary.add((targetIndex + 1).coerceAtMost(primary.size), moving)
            val updated = layoutPrimaryTimeline(primary)
            dao.upsertAll(updated)
            recordHistory(pid, "Reorder", before, dao.getTimelineForProjectOnce(pid), selectedBefore, selectedId)
            Log.d(TAG, "reorder selected=$selectedId after=$targetClipId")
        }
    }

    fun moveClip(clipId: String, direction: Int) {
        val pid = currentProjectId ?: return
        if (direction == 0) return
        val selectedBefore = _uiState.value.selectedClipId
        viewModelScope.launch {
            val before = dao.getTimelineForProjectOnce(pid)
            val items = before
            val assets = app.database.mediaAssetDao().getAssetsForProjectOnce(pid).associateBy { it.id }
            val primary = items
                .filter { isPrimaryVisual(assets[it.mediaAssetId]?.mediaType) }
                .sortedBy { it.orderIndex }
                .toMutableList()
            val from = primary.indexOfFirst { it.id == clipId }
            if (from < 0) return@launch
            val to = (from + direction.coerceIn(-1, 1)).coerceIn(0, primary.lastIndex)
            if (from == to) return@launch
            val moving = primary.removeAt(from)
            primary.add(to, moving)
            val updated = layoutPrimaryTimeline(primary)
            dao.upsertAll(updated)
            _uiState.update { it.copy(selectedClipId = clipId) }
            recordHistory(pid, "Move", before, dao.getTimelineForProjectOnce(pid), selectedBefore, clipId)
            Log.d(TAG, "move clip=$clipId from=$from to=$to")
        }
    }

    fun reorderClip(clipId: String, targetIndex: Int) {
        val pid = currentProjectId ?: return
        val selectedBefore = _uiState.value.selectedClipId
        viewModelScope.launch {
            val before = dao.getTimelineForProjectOnce(pid)
            val assets = app.database.mediaAssetDao().getAssetsForProjectOnce(pid).associateBy { it.id }
            val primary = before
                .filter { isPrimaryVisual(assets[it.mediaAssetId]?.mediaType) }
                .sortedBy { it.orderIndex }
                .toMutableList()
            val from = primary.indexOfFirst { it.id == clipId }
            if (from < 0) return@launch
            val moving = primary.removeAt(from)
            val to = targetIndex.coerceIn(0, primary.size)
            if (from == to) return@launch
            primary.add(to, moving)
            dao.upsertAll(layoutPrimaryTimeline(primary))
            _uiState.update { it.copy(selectedClipId = clipId) }
            recordHistory(pid, "Reorder", before, dao.getTimelineForProjectOnce(pid), selectedBefore, clipId)
            Log.d(TAG, "reorder drop clip=$clipId from=$from to=$to")
        }
    }

    override fun onCleared() { super.onCleared(); playbackJob?.cancel() }

    private fun beginTrimDragIfNeeded(clipId: String) {
        if (_uiState.value.trimmingClipId == clipId) return
        if (_uiState.value.isPlaying) pause()
        _uiState.update { it.copy(trimmingClipId = clipId, selectedClipId = clipId) }
        Log.d(TAG, "trimDragStarted clip=$clipId")
    }

    private fun updateTrimmedClip(clipId: String, transform: (ClipUiModel) -> Pair<ClipUiModel, Long>) {
        val state = _uiState.value
        var previewFrameMs: Long? = null
        val rawUpdated = state.clips.map { clip ->
            if (clip.id == clipId) {
                val (updatedClip, frameMs) = transform(clip)
                previewFrameMs = frameMs
                updatedClip
            } else {
                clip
            }
        }
        if (rawUpdated == state.clips) return
        _uiState.update {
            it.copy(
                clips = rawUpdated,
                trimPreviewFrameMs = previewFrameMs
            )
        }
        val nextClip = rawUpdated.firstOrNull { it.id == clipId }
        Log.v(TAG, "trim drag clip=$clipId duration=${nextClip?.durationMs} source=${nextClip?.sourceStartMs}-${nextClip?.sourceEndMs}")
    }

    private fun List<ClipUiModel>.withRecalculatedTimelineBounds(): List<ClipUiModel> {
        Log.d(
            TAG,
            "TIMELINE_ADJACENCY_NORMALIZE_BEFORE source=uiClipBounds count=$size " +
                "bounds=${map { "${it.id}:${it.timelineStartMs}-${it.timelineEndMs}" }}"
        )
        var cursor = 0L
        val normalized = map { clip ->
            if (clip.timelineStartMs != cursor) {
                Log.w(
                    TAG,
                    "TIMELINE_GAP_DETECTED source=uiClipBounds clipId=${clip.id} expectedStartMs=$cursor actualStartMs=${clip.timelineStartMs} " +
                        "gapMs=${clip.timelineStartMs - cursor}"
                )
                Log.d(TAG, "TIMELINE_GAP_FIXED source=uiClipBounds clipId=${clip.id} fixedStartMs=$cursor")
            }
            val duration = clip.durationMs.coerceAtLeast(MIN_TRIM_DURATION_MS)
            clip.copy(
                durationMs = duration,
                timelineStartMs = cursor,
                timelineEndMs = cursor + duration
            ).also { cursor += duration }
        }
        Log.d(
            TAG,
            "TIMELINE_ADJACENCY_NORMALIZE_AFTER source=uiClipBounds count=${normalized.size} " +
                "bounds=${normalized.map { "${it.id}:${it.timelineStartMs}-${it.timelineEndMs}" }}"
        )
        return normalized
    }

    private fun hasGapBetweenSegments(segments: List<TimelineSegment>): Boolean {
        return segments.zipWithNext().any { (left, right) -> left.endMs != right.startMs }
    }

    private fun selectedClipId(): String? = _uiState.value.selectedClipId ?: _uiState.value.currentSegment?.clipId

    private fun selectedOrActiveClip(): ClipUiModel? {
        val s = _uiState.value
        val selected = s.selectedClipId?.let { id -> s.clips.firstOrNull { it.id == id } }
        val selectedContainsPlayhead = selected?.let { clip ->
            s.globalProjectTimeMs > clip.timelineStartMs && s.globalProjectTimeMs < clip.timelineEndMs
        } == true
        if (selectedContainsPlayhead) return selected
        val activeId = s.currentSegment?.clipId
        return s.clips.firstOrNull { it.id == activeId } ?: selected
    }

    private fun resolveActiveTransition(
        clips: List<ClipUiModel>,
        currentTimelineMs: Long
    ): ActiveTransitionState? {
        val active = clips.sortedBy { it.timelineStartMs }
            .zipWithNext()
            .firstNotNullOfOrNull { (fromClip, toClip) ->
                val transition = fromClip.transition ?: return@firstNotNullOfOrNull null
                if (transition.type == TransitionType.NONE || transition.durationMs <= 0L) {
                    return@firstNotNullOfOrNull null
                }
                val savedDurationMs = normalizeSavedTransitionDurationMs(transition.durationMs)
                val shorterNeighborMs = minOf(fromClip.durationMs, toClip.durationMs)
                val maxAllowedDurationMs = (shorterNeighborMs * 0.90f).roundToLong().coerceAtLeast(1L)
                val durationMs = savedDurationMs.coerceAtMost(maxAllowedDurationMs)
                val boundaryMs = fromClip.timelineEndMs
                val halfDurationMs = durationMs / 2L
                val startMs = (boundaryMs - halfDurationMs).coerceAtLeast(fromClip.timelineStartMs)
                val endMs = (boundaryMs + halfDurationMs).coerceAtMost(toClip.timelineEndMs)
                if (currentTimelineMs >= startMs && currentTimelineMs < endMs) {
                    val progress = ((currentTimelineMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L).toFloat())
                        .coerceIn(0f, 1f)
                    Log.d(
                        TAG,
                        "TRANSITION_WINDOW currentTimelineMs=$currentTimelineMs transitionStartMs=$startMs " +
                            "transitionEndMs=$endMs boundaryMs=$boundaryMs progress=$progress " +
                            "transitionDurationMs=$durationMs transitionType=${transition.type}"
                    )
                    ActiveTransitionState(
                        fromClipId = fromClip.id,
                        toClipId = toClip.id,
                        transitionType = transition.type,
                        transitionDurationMs = durationMs,
                        clipAStartMs = fromClip.timelineStartMs,
                        clipAEndMs = boundaryMs,
                        transitionStartMs = startMs,
                        transitionEndMs = endMs,
                        progress = progress
                    )
                } else {
                    null
                }
            }
        Log.v(
            TAG,
            "TRANSITION_RENDER_CHECK currentTimelineMs=$currentTimelineMs " +
                "transitionStartMs=${active?.transitionStartMs} transitionEndMs=${active?.transitionEndMs} " +
                "transitionType=${active?.transitionType} progress=${active?.progress} isTransitionActive=${active != null}"
        )
        return active
    }

    private fun normalizeTransitionDurationForSave(rawDurationMs: Long): Long =
        normalizeSavedTransitionDurationMs(rawDurationMs).coerceAtLeast(1L)

    private fun normalizeSavedTransitionDurationMs(rawDurationMs: Long): Long {
        return when {
            rawDurationMs in 1L..10L -> rawDurationMs * 1_000L
            rawDurationMs <= 0L -> MIN_PREVIEW_TRANSITION_DURATION_MS
            else -> rawDurationMs
        }
    }

    private fun normalizePreviewTransitionDurationMs(rawDurationMs: Long): Long =
        normalizeSavedTransitionDurationMs(rawDurationMs)

    private suspend fun normalizePrimaryTimeline(projectId: String) {
        val items = dao.getTimelineForProjectOnce(projectId)
        val assets = app.database.mediaAssetDao().getAssetsForProjectOnce(projectId).associateBy { it.id }
        val primary = layoutPrimaryTimeline(items.filter { item ->
            isPrimaryVisual(assets[item.mediaAssetId]?.mediaType)
        }.sortedBy { it.orderIndex })
        val primaryById = primary.associateBy { it.id }
        val merged = items.map { primaryById[it.id] ?: it }
        dao.upsertAll(merged)
        Log.d(TAG, "normalize timeline items=${merged.size} duration=${primary.lastOrNull()?.endMs ?: 0L}")
    }

    private fun layoutPrimaryTimeline(items: List<TimelineItemEntity>): List<TimelineItemEntity> {
        Log.d(
            TAG,
            "TIMELINE_ADJACENCY_NORMALIZE_BEFORE source=primaryTimeline count=${items.size} " +
                "bounds=${items.map { "${it.id}:${it.startMs}-${it.endMs}" }}"
        )
        var cursor = 0L
        val normalized = items.mapIndexed { index, item ->
            if (item.startMs != cursor) {
                Log.w(
                    TAG,
                    "TIMELINE_GAP_DETECTED source=primaryTimeline clipId=${item.id} expectedStartMs=$cursor actualStartMs=${item.startMs} " +
                        "gapMs=${item.startMs - cursor}"
                )
                Log.d(TAG, "TIMELINE_GAP_FIXED source=primaryTimeline clipId=${item.id} fixedStartMs=$cursor")
            }
            val duration = (item.endMs - item.startMs).coerceAtLeast(1L)
            item.copy(trackIndex = 0, orderIndex = index, startMs = cursor, endMs = cursor + duration)
                .also { cursor += duration }
        }
        Log.d(
            TAG,
            "TIMELINE_ADJACENCY_NORMALIZE_AFTER source=primaryTimeline count=${normalized.size} " +
                "bounds=${normalized.map { "${it.id}:${it.startMs}-${it.endMs}" }}"
        )
        return normalized
    }

    private fun recordHistory(
        projectId: String,
        label: String,
        before: List<TimelineItemEntity>,
        after: List<TimelineItemEntity>,
        selectedBefore: String?,
        selectedAfter: String?
    ) {
        if (before == after) return
        historyRegistry.record(
            ClipSnapshotCommand(
                label = label,
                before = before,
                after = after,
                selectedBefore = selectedBefore.toSelectionTarget(),
                selectedAfter = selectedAfter.toSelectionTarget()
            ) { items, selection ->
                restoreTimelineSnapshot(projectId, items, selection.clipId)
            }
        )
    }

    private suspend fun restoreTimelineSnapshot(
        projectId: String,
        items: List<TimelineItemEntity>,
        selectedClipId: String?
    ) {
        dao.deleteAllForProject(projectId)
        if (items.isNotEmpty()) dao.upsertAll(items)
        _uiState.update {
            it.copy(
                selectedClipId = selectedClipId,
                isPlaying = false,
                resumePlaybackAfterPlaylistPrepared = false,
                preserveTimelineScrollVersion = it.preserveTimelineScrollVersion + 1L
            )
        }
    }

    private fun updateHistoryAvailability() {
        val history = historyRegistry.state.value
        _uiState.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    private fun addPickedTrack(uri: Uri, hint: MediaType, trackIndex: Int) {
        val pid = currentProjectId ?: return
        viewModelScope.launch {
            val asset = buildAssetFromUri(pid, uri, hint) ?: return@launch
            app.database.mediaAssetDao().upsertAsset(asset)
            val duration = asset.durationMs?.takeIf { it > 0L } ?: DEFAULT_OVERLAY_DURATION_MS
            dao.upsertTimelineItem(newTimelineItem(pid, asset.id, trackIndex, duration))
            Log.d(TAG, "add track type=${asset.mediaType} currentTimelineMs=${_uiState.value.globalProjectTimeMs}")
        }
    }

    private fun newTimelineItem(projectId: String, assetId: String, trackIndex: Int, duration: Long): TimelineItemEntity {
        val start = _uiState.value.globalProjectTimeMs.coerceAtLeast(0L)
        return TimelineItemEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            mediaAssetId = assetId,
            trackIndex = trackIndex,
            orderIndex = System.currentTimeMillis().toInt(),
            startMs = start,
            endMs = start + duration.coerceAtLeast(1L),
            trimStartMs = 0L,
            trimEndMs = duration.coerceAtLeast(1L),
            fitMode = "FIT",
            transitionType = null,
            transitionDurationMs = null,
            volume = 1f,
            opacity = 1f
        )
    }

    private fun newPrimaryTimelineItem(
        projectId: String,
        assetId: String,
        orderIndex: Int,
        startMs: Long,
        duration: Long
    ): TimelineItemEntity {
        val cleanDuration = duration.coerceAtLeast(1L)
        val cleanStart = startMs.coerceAtLeast(0L)
        return TimelineItemEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            mediaAssetId = assetId,
            trackIndex = 0,
            orderIndex = orderIndex,
            startMs = cleanStart,
            endMs = cleanStart + cleanDuration,
            trimStartMs = 0L,
            trimEndMs = cleanDuration,
            fitMode = "FIT",
            transitionType = null,
            transitionDurationMs = null,
            volume = 1f,
            opacity = 1f
        )
    }

    private suspend fun buildAssetFromUri(projectId: String, uri: Uri, hint: MediaType): MediaAssetEntity? =
        withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val mime = context.contentResolver.getType(uri) ?: return@withContext null
            val type = resolveType(mime, hint)
            MediaAssetEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                mediaType = type.name,
                localUri = uri.toString(),
                remoteUrl = null,
                durationMs = if (type == MediaType.VIDEO || type == MediaType.AUDIO || type == MediaType.OVERLAY_VIDEO) {
                    queryDuration(context, uri)
                } else {
                    null
                },
                fileSizeBytes = queryFileSize(context, uri),
                mimeType = mime,
                createdAt = System.currentTimeMillis()
            )
        }

    private fun resolveType(mime: String, hint: MediaType): MediaType = when {
        hint == MediaType.OVERLAY_IMAGE -> MediaType.OVERLAY_IMAGE
        hint == MediaType.OVERLAY_VIDEO -> MediaType.OVERLAY_VIDEO
        mime.startsWith("video") -> if (hint == MediaType.OVERLAY_VIDEO) MediaType.OVERLAY_VIDEO else MediaType.VIDEO
        mime.startsWith("image") -> if (hint == MediaType.OVERLAY_IMAGE) MediaType.OVERLAY_IMAGE else MediaType.IMAGE
        mime.startsWith("audio") -> MediaType.AUDIO
        else -> hint
    }

    private fun queryDuration(context: Context, uri: Uri): Long? = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        retriever.release()
        duration
    } catch (e: Exception) {
        Log.w(TAG, "duration unavailable uri=$uri error=${e.message}")
        null
    }

    private fun queryFileSize(context: Context, uri: Uri): Long =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } ?: 0L

    private fun timelineToSourceMs(clip: ClipUiModel, timelineOffsetMs: Long): Long {
        val sourceSpan = (clip.sourceEndMs - clip.sourceStartMs).coerceAtLeast(1L)
        val sourceOffset = (timelineOffsetMs * clip.playbackSpeed).roundToLong()
        return (clip.sourceStartMs + sourceOffset).coerceIn(clip.sourceStartMs, clip.sourceEndMs)
    }

    private fun isPrimaryVisual(mediaType: String?): Boolean =
        mediaType == MediaType.VIDEO.name || mediaType == MediaType.IMAGE.name

    private fun encodeTransform(transform: ClipTransform): String =
        "TRANSFORM|${transform.scale}|${transform.offsetX}|${transform.offsetY}|${transform.rotation}"

    private fun decodeTransform(fitMode: String): ClipTransform {
        val parts = fitMode.split("|")
        return if (parts.size == 5 && parts.first() == "TRANSFORM") {
            ClipTransform(
                scale = parts[1].toFloatOrNull() ?: 1f,
                offsetX = parts[2].toFloatOrNull() ?: 0f,
                offsetY = parts[3].toFloatOrNull() ?: 0f,
                rotation = parts[4].toFloatOrNull() ?: 0f
            )
        } else {
            ClipTransform()
        }
    }
}

private const val MIN_EDIT_SLICE_MS = 100L
private const val MIN_TRIM_DURATION_MS = 100L
private const val MIN_SPLIT_SLICE_MS = 500L
private const val MIN_PLAYBACK_SPEED = 0.1f
private const val MAX_PLAYBACK_SPEED = 8f
private const val MIN_VOLUME_MULTIPLIER = 0f
private const val MAX_VOLUME_MULTIPLIER = 10f
private const val DEFAULT_OVERLAY_DURATION_MS = 3000L
private const val TEXT_OVERLAY_MIME = "application/x-clipforge-text"

private fun String?.toSelectionTarget(): SelectionTarget =
    if (this == null) SelectionTarget.None else SelectionTarget.Clip(this)
