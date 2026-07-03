package com.clipforge.ai.presentation.timeline

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.content.Context
import android.util.Log
import android.view.Choreographer
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.R
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectCategory
import com.clipforge.ai.core.effects.EffectReleasePolicy
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.ExportEffectRegistry
import com.clipforge.ai.core.player.EffectPreviewController
import com.clipforge.ai.core.transition.TransitionSpec
import com.clipforge.ai.core.utils.TimeFormatter
import com.clipforge.ai.domain.history.DeleteEffectCommand
import com.clipforge.ai.domain.history.SelectEffectCommand
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.MediaType
import com.clipforge.ai.domain.model.TextOverlay
import com.clipforge.ai.domain.model.TimelineSegment
import com.clipforge.ai.domain.model.TransitionType
import com.clipforge.ai.domain.selection.SelectionController
import com.clipforge.ai.domain.selection.SelectionTarget
import com.clipforge.ai.presentation.animation.AnimationPickerCategory
import com.clipforge.ai.presentation.animation.AnimationSummary
import com.clipforge.ai.presentation.animation.CLIP_ANIMATION_MARKER_TAG
import com.clipforge.ai.presentation.animation.ClipAnimationMarkerState
import com.clipforge.ai.presentation.animation.ClipAnimationSheet
import com.clipforge.ai.presentation.animation.ClipAnimationViewModel
import com.clipforge.ai.presentation.animation.ClipAnimationWindowInput
import com.clipforge.ai.presentation.animation.buildClipAnimationMarkerMap
import com.clipforge.ai.presentation.animation.buildClipAnimationUiState
import com.clipforge.ai.presentation.animation.buildAnimationPickerState
import com.clipforge.ai.presentation.animation.maxDurationForRole
import com.clipforge.ai.presentation.effects.EffectActionBar
import com.clipforge.ai.presentation.effects.EffectCatalogSheet
import com.clipforge.ai.presentation.effects.TimelineEffectLane
import com.clipforge.ai.presentation.effects.buildEffectActionBarState
import com.clipforge.ai.presentation.effects.buildEffectCatalogState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.abs

private val CLIP_W: Dp           = 80.dp
private val TRACK_H: Dp          = 80.dp
private val PX_PER_SEC: Float    = 40f   // 40 px per second of clip
private const val SCREEN_TAG = "TimelineScreen"
private val TRACK_CONTROLS_W: Dp = 40.dp
private val STICKY_ADD_MEDIA_PADDING: Dp = 48.dp
private const val PREVIEW_SEEK_SKIP_MS = 16L
private const val PREVIEW_TRIM_SEEK_THROTTLE_MS = 16L
private const val PREVIEW_SCRUB_DEBOUNCE_MS = 0L
private const val PREVIEW_LOADING_DELAY_MS = 500L
private const val PREVIEW_PLAYER_SYNC_MS = 33L
private const val MIN_TIMELINE_ZOOM = 0.55f
private const val MAX_TIMELINE_ZOOM = 3.0f

private fun playbackStateLabel(state: Int): String = when (state) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> state.toString()
}

private fun threadLabel(): String {
    val thread = Thread.currentThread()
    return "${thread.name}/main=${thread.name.equals("main", ignoreCase = true)}"
}

private data class PreviewPlaybackItem(
    val id: String,
    val uri: String,
    val clips: List<ClipUiModel>,
    val clippingStartMs: Long,
    val clippingEndMs: Long
) {
    val isSameSourceGroup: Boolean get() = clips.size > 1
}

private enum class PlaybackRenderMode { PREVIEW, EXPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    projectId: String,
    onBack: () -> Unit,
    onExport: () -> Unit = {},
    onAddText: (() -> Unit)?       = null,
    onAddMusic: (() -> Unit)?      = null,
    onAddOverlay: (() -> Unit)?    = null,
    onAddTransition: (() -> Unit)? = null,
    onEditTransition: ((String) -> Unit)? = null,
    viewModel: TimelineViewModel   = viewModel()
) {
    val uiState    by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val timelineScrollState = rememberScrollState()
    val timelineFrameCache = remember { mutableStateMapOf<String, List<Bitmap?>>() }
    var timelineZoom by remember { mutableFloatStateOf(1f) }
    var comingSoonTool by remember { mutableStateOf<String?>(null) }
    var placeholderTool by remember { mutableStateOf<String?>(null) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    var showTransformSheet by remember { mutableStateOf(false) }
    var showTextSheet by remember { mutableStateOf(false) }
    var showEffectCatalogSheet by rememberSaveable { mutableStateOf(false) }
    var selectedAnimationCategory by rememberSaveable { mutableStateOf(AnimationPickerCategory.BASIC) }
    var animationPreviewRestartKey by remember { mutableLongStateOf(0L) }
    var selectedEffectCatalogCategory by rememberSaveable { mutableStateOf(EffectCategory.TRENDY) }
    var screenRecompositionCount by remember { mutableIntStateOf(0) }
    var previewVolumeClipId by remember { mutableStateOf<String?>(null) }
    var previewVolumeMultiplier by remember { mutableFloatStateOf(1f) }
    val effectPreviewControllerRef = remember { mutableStateOf<EffectPreviewController?>(null) }
    val selectionController = remember { SelectionController() }
    val selectionTarget by selectionController.selection.collectAsState()
    val app = remember(context) { context.applicationContext as ClipForgeApp }
    val historyRegistry = remember(app) { app.historyRegistry }
    val effectHistoryState by historyRegistry.state.collectAsState()
    val effectRepository = remember(app) { app.effectRepository }
    val timelineEffects by remember(projectId, effectRepository) {
        effectRepository.observeEffectsForProject(projectId)
    }.collectAsState(initial = emptyList<EffectItem>())
    val textOverlayRepository = remember(app) { app.textOverlayRepository }
    val timelineTextOverlays by remember(projectId, textOverlayRepository) {
        textOverlayRepository.observeTextOverlaysForProject(projectId)
    }.collectAsState(initial = emptyList<TextOverlay>())
    val clipAnimationViewModel = remember(projectId, effectRepository, historyRegistry) {
        ClipAnimationViewModel(
            projectId = projectId,
            repository = effectRepository,
            historyRegistry = historyRegistry
        )
    }
    val clipAnimationTransientState by clipAnimationViewModel.state.collectAsState()
    val clipAnimationWindows = remember(uiState.clips) {
        uiState.clips
            .sortedBy { it.timelineStartMs }
            .mapIndexed { index, clip ->
                ClipAnimationWindowInput(
                    clipId = clip.id,
                    startMs = clip.timelineStartMs,
                    endMs = clip.timelineEndMs,
                    incomingTransitionDurationMs = uiState.clips
                        .sortedBy { it.timelineStartMs }
                        .getOrNull(index - 1)
                        ?.transition
                        ?.durationMs
                        ?: 0L,
                    outgoingTransitionDurationMs = clip.transition?.durationMs ?: 0L
                )
            }
    }
    val selectedClipAnimationWindow = remember(uiState.selectedClipId, clipAnimationWindows) {
        clipAnimationWindows.firstOrNull { it.clipId == uiState.selectedClipId }
    }
    val clipAnimationState = remember(
        uiState.selectedClipId,
        clipAnimationTransientState,
        timelineEffects,
        clipAnimationWindows
    ) {
        buildClipAnimationUiState(
            selectedClipId = uiState.selectedClipId,
            selectedRole = clipAnimationTransientState.selectedRole,
            effects = timelineEffects,
            clipWindows = clipAnimationWindows,
            inFlightDurationMs = clipAnimationTransientState.inFlightDurationMs,
            draft = clipAnimationTransientState.draft
        )
    }
    val clipAnimationMaxDurationMs = remember(
        clipAnimationState.selectedRole,
        uiState.selectedClipId,
        timelineEffects,
        selectedClipAnimationWindow,
        clipAnimationTransientState.draft
    ) {
        maxDurationForRole(
            role = clipAnimationState.selectedRole,
            selectedClipId = uiState.selectedClipId,
            effects = timelineEffects,
            clipWindow = selectedClipAnimationWindow,
            draft = clipAnimationTransientState.draft
        )
    }
    val clipAnimationMarkers = remember(timelineEffects, clipAnimationTransientState.draft) {
        buildClipAnimationMarkerMap(timelineEffects, clipAnimationTransientState.draft)
    }
    val animationPickerState = remember(clipAnimationState) {
        buildAnimationPickerState(
            hasAnimation = clipAnimationState.inAnimation != null ||
                clipAnimationState.outAnimation != null ||
                clipAnimationState.comboAnimation != null
        )
    }
    val effectCatalogState = remember {
        buildEffectCatalogState(
            registry = ExportEffectRegistry.registry,
            releasePolicy = EffectReleasePolicy()
        )
    }
    val visibleSelectedClipId = selectionTarget.clipId
    val effectActionBarState = remember(timelineEffects, selectionTarget, effectHistoryState) {
        buildEffectActionBarState(
            effects = timelineEffects,
            selectionTarget = selectionTarget,
            canUndo = effectHistoryState.canUndo,
            canRedo = effectHistoryState.canRedo
        )
    }
    val screenScope = rememberCoroutineScope()

    SideEffect {
        screenRecompositionCount++
        if (uiState.isPlaying) {
            Log.v(
                SCREEN_TAG,
                "recomposition target=TimelineScreen count=$screenRecompositionCount " +
                    "currentClipId=${uiState.currentSegment?.clipId} timelinePosition=${uiState.globalProjectTimeMs}"
            )
        }
    }

    val replacePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.replaceSelectedClip(it) } }
    val appendMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris -> viewModel.appendMediaToTimeline(uris) }
    val overlayPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.addOverlay(it) } }
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.addAudio(it) } }

    val launchVisualPicker: () -> Unit = {
        overlayPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    val launchAppendMediaPicker: () -> Unit = {
        appendMediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    val launchReplacePicker: () -> Unit = {
        replacePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    val launchAudioPicker: () -> Unit = {
        audioPicker.launch("audio/*")
    }

    LaunchedEffect(projectId) { viewModel.loadForProject(projectId) }
    LaunchedEffect(uiState.selectedClipId) {
        val selectedClipId = uiState.selectedClipId
        if (selectedClipId != null && selectionTarget !is SelectionTarget.Effect) {
            selectionController.selectClip(selectedClipId)
        }
    }
    LaunchedEffect(timelineEffects, uiState.clips, selectionTarget) {
        when (val target = selectionTarget) {
            is SelectionTarget.Clip -> {
                if (uiState.clips.none { it.id == target.id }) selectionController.clear()
            }
            is SelectionTarget.Effect -> {
                if (timelineEffects.none { it.id == target.id }) selectionController.clear()
            }
            SelectionTarget.None -> Unit
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TimelineEditorEffect.OpenVolumeSheet -> showVolumeSheet = true
                TimelineEditorEffect.OpenSpeedSheet -> showSpeedSheet = true
                TimelineEditorEffect.OpenTransformSheet -> showTransformSheet = true
                TimelineEditorEffect.OpenReplacePicker -> launchReplacePicker()
                is TimelineEditorEffect.ShowPlaceholder -> placeholderTool = effect.tool
                is TimelineEditorEffect.ShowComingSoon -> comingSoonTool = effect.tool
            }
        }
    }
    // Switching the selected clip while the animation draft is open re-baselines the draft to
    // the newly selected clip; any unconfirmed edits on the previously selected clip are dropped.
    LaunchedEffect(uiState.selectedClipId) {
        if (clipAnimationTransientState.panelOpen) {
            clipAnimationViewModel.openPanel(uiState.selectedClipId, timelineEffects)
        }
    }
    LaunchedEffect(selectedClipAnimationWindow) {
        val draftClipId = clipAnimationTransientState.draft?.clipId
        val window = selectedClipAnimationWindow
        if (draftClipId != null && window != null && window.clipId == draftClipId) {
            clipAnimationViewModel.onClipWindowChanged(draftClipId, window)
        }
    }
    LaunchedEffect(uiState.clips) {
        val draftClipId = clipAnimationTransientState.draft?.clipId
        if (draftClipId != null && uiState.clips.none { it.id == draftClipId }) {
            clipAnimationViewModel.discardDraft()
        }
    }
    val activeAnimationDraftClipId = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(clipAnimationTransientState.draft, effectPreviewControllerRef.value) {
        val controller = effectPreviewControllerRef.value
        val draft = clipAnimationTransientState.draft
        if (controller == null) return@LaunchedEffect
        if (draft == null) {
            if (activeAnimationDraftClipId.value != null) {
                controller.endAnimationDraft()
                activeAnimationDraftClipId.value = null
            }
            return@LaunchedEffect
        }
        if (activeAnimationDraftClipId.value != draft.clipId) {
            controller.beginAnimationDraft(draft.clipId)
            activeAnimationDraftClipId.value = draft.clipId
        }
        controller.updateAnimationDraftItems(draft.clipId, draft.resolvedItems())
    }

    comingSoonTool?.let { tool ->
        ComingSoonSheet(tool = tool, onDismiss = { comingSoonTool = null })
    }
    placeholderTool?.let { tool ->
        EditPlaceholderSheet(tool = tool, onDismiss = { placeholderTool = null })
    }
    if (showSpeedSheet) {
        SpeedSelectorSheet(
            initialSpeed = uiState.clips.firstOrNull { it.id == uiState.selectedClipId }?.playbackSpeed ?: 1f,
            onSelect = {
                viewModel.setSelectedClipSpeed(it)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false }
        )
    }
    if (showTransformSheet) {
        TransformSheet(
            initialTransform = uiState.clips.firstOrNull { it.id == uiState.selectedClipId }?.transform ?: ClipTransform(),
            onApply = {
                viewModel.setSelectedClipTransform(it)
                showTransformSheet = false
            },
            onDismiss = { showTransformSheet = false }
        )
    }
    if (showTextSheet) {
        TextOverlaySheet(
            onApply = {
                val plan = planDefaultTimelineTextOverlayCreation(
                    projectId = projectId,
                    text = it,
                    timelineStartMs = uiState.globalProjectTimeMs,
                    totalDurationMs = uiState.totalDurationMs,
                    zIndex = (timelineTextOverlays.maxOfOrNull { overlay -> overlay.zIndex } ?: -1) + 1
                )
                plan.overlay?.let { overlay ->
                    screenScope.launch { textOverlayRepository.upsertTextOverlay(overlay) }
                }
                showTextSheet = false
            },
            onDismiss = { showTextSheet = false }
        )
    }

    Scaffold(
        topBar = { CapCutEditorTopBar(onBack = onBack, onExport = onExport) },
        bottomBar = {
            val splitCandidateClip = uiState.selectedClipId?.let { selectedId ->
                uiState.clips.firstOrNull { it.id == selectedId }
            } ?: uiState.currentSegment?.clipId?.let { activeId ->
                uiState.clips.firstOrNull { it.id == activeId }
            } ?: uiState.clips.firstOrNull { clip ->
                uiState.globalProjectTimeMs >= clip.timelineStartMs &&
                    uiState.globalProjectTimeMs <= clip.timelineEndMs
            }
            val playheadInsideClip = splitCandidateClip?.let { clip ->
                uiState.globalProjectTimeMs > clip.timelineStartMs &&
                    uiState.globalProjectTimeMs < clip.timelineEndMs
            } == true
            val splitButtonEnabled = splitCandidateClip != null
            LaunchedEffect(splitButtonEnabled, splitCandidateClip?.id, uiState.selectedClipId, uiState.globalProjectTimeMs) {
                Log.d(
                    SCREEN_TAG,
                    "SPLIT_BUTTON_ENABLED_STATE enabled=$splitButtonEnabled candidateClipId=${splitCandidateClip?.id} selectedClipId=${uiState.selectedClipId} " +
                        "playheadMs=${uiState.globalProjectTimeMs} playheadInsideClip=$playheadInsideClip " +
                        "blockingModal=${showSpeedSheet || showVolumeSheet || showTransformSheet || showTextSheet || clipAnimationTransientState.panelOpen || comingSoonTool != null || placeholderTool != null}"
                )
            }
            if (effectActionBarState.visible) {
                EffectActionBar(
                    state = effectActionBarState,
                    onDelete = {
                        screenScope.launch {
                            val selectedEffectId = effectActionBarState.selectedEffectId
                            val selectedEffect = timelineEffects.firstOrNull { it.id == selectedEffectId }
                            if (selectedEffect != null) {
                                historyRegistry.execute(
                                    DeleteEffectCommand(
                                        repository = effectRepository,
                                        effect = selectedEffect,
                                        selectionController = selectionController
                                    )
                                )
                            }
                        }
                    },
                    onUndo = {
                        screenScope.launch { historyRegistry.undo() }
                    },
                    onRedo = {
                        screenScope.launch { historyRegistry.redo() }
                    },
                    onClearSelection = { selectionController.clear() }
                )
            } else if (clipAnimationTransientState.panelOpen) {
                ClipAnimationSheet(
                    visible = true,
                    state = animationPickerState,
                    clipAnimationState = clipAnimationState,
                    selectedCategory = selectedAnimationCategory,
                    maxDurationMs = clipAnimationMaxDurationMs,
                    onConfirm = {
                        screenScope.launch { clipAnimationViewModel.confirmDraft() }
                    },
                    onDiscard = { clipAnimationViewModel.discardDraft() },
                    onRoleSelected = clipAnimationViewModel::selectRole,
                    onCategorySelected = { selectedAnimationCategory = it },
                    onDurationDragging = { durationMs ->
                        val clipId = uiState.selectedClipId
                        val window = selectedClipAnimationWindow
                        if (clipId != null && window != null) {
                            clipAnimationViewModel.adjustDuration(
                                clipId = clipId,
                                role = clipAnimationState.selectedRole,
                                durationMs = durationMs,
                                clipWindow = window
                            )
                        }
                    },
                    onDurationCommitted = {
                        val window = selectedClipAnimationWindow
                        if (window != null) {
                            viewModel.seekTo(window.startMs)
                            animationPreviewRestartKey++
                            viewModel.play()
                        }
                    },
                    onApplyPreset = { presetId ->
                        val clipId = uiState.selectedClipId
                        val window = selectedClipAnimationWindow
                        if (clipId == null || window == null) {
                            Toast.makeText(context, "Select a clip first", Toast.LENGTH_SHORT).show()
                        } else {
                            clipAnimationViewModel.selectPreset(
                                clipId = clipId,
                                presetId = presetId,
                                role = clipAnimationState.selectedRole,
                                requestedDurationMs = clipAnimationState.requestedDurationMs,
                                clipWindow = window
                            )
                            viewModel.seekTo(window.startMs)
                            animationPreviewRestartKey++
                            viewModel.play()
                        }
                    },
                    onClearAnimation = {
                        val clipId = uiState.selectedClipId
                        val window = selectedClipAnimationWindow
                        if (clipId != null) {
                            clipAnimationViewModel.clearAnimation(clipId, clipAnimationState.selectedRole)
                            viewModel.seekTo(window?.startMs ?: 0L)
                            animationPreviewRestartKey++
                            viewModel.play()
                        }
                    }
                )
            } else if (showEffectCatalogSheet) {
                EffectCatalogSheet(
                    visible = true,
                    state = effectCatalogState,
                    selectedCategory = selectedEffectCatalogCategory,
                    onDismiss = { showEffectCatalogSheet = false },
                    onCategorySelected = { selectedEffectCatalogCategory = it },
                    onTileClicked = { tile ->
                        Log.d(SCREEN_TAG, "effectCatalogTileClickIgnored effectId=${tile.effectId}")
                    },
                    modifier = Modifier.navigationBarsPadding()
                )
            } else {
                TimelineToolbar(
                    toolbarMode = uiState.toolbarMode,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    splitButtonEnabled = splitButtonEnabled,
                    onPrimaryTool = { label ->
                        when (label) {
                            "Edit" -> viewModel.onPrimaryToolClicked(label)
                            "Audio" -> onAddMusic?.invoke() ?: launchAudioPicker()
                            "Text" -> onAddText?.invoke() ?: run { showTextSheet = true }
                            "Effects" -> showEffectCatalogSheet = true
                            "Overlay" -> onAddOverlay?.invoke() ?: launchVisualPicker()
                            else -> comingSoonTool = label
                        }
                    },
                    onEditTool = { action ->
                        Log.d(SCREEN_TAG, "BOTTOM_TOOLBAR_CLICK_RECEIVED action=${action.label}")
                        if (action == EditToolAction.Effects) {
                            showEffectCatalogSheet = true
                        } else if (action == EditToolAction.Animations) {
                            clipAnimationViewModel.openPanel(uiState.selectedClipId, timelineEffects)
                        } else if (action == EditToolAction.Volume && uiState.selectedClipId == null) {
                            Toast.makeText(context, "Select a clip first", Toast.LENGTH_SHORT).show()
                        } else {
                            if (action == EditToolAction.Split) {
                                Log.d(
                                    SCREEN_TAG,
                                    "SPLIT_BUTTON_TAP candidateClipId=${splitCandidateClip?.id} selectedClipId=${uiState.selectedClipId} currentSegment=${uiState.currentSegment?.clipId} " +
                                        "playheadMs=${uiState.globalProjectTimeMs}"
                                )
                                Log.d(SCREEN_TAG, "SPLIT_ACTION_DISPATCHED source=bottomToolbar handler=existingViewModel")
                            }
                            viewModel.onEditToolClicked(action)
                        }
                    }
                )
            }
        },
        containerColor = AppColors.Background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF151515))
        ) {
            Column(Modifier.fillMaxSize()) {
                CapCutPreviewArea(
                    projectId = projectId,
                    clips = uiState.clips,
                    textOverlays = timelineTextOverlays,
                    clip = uiState.clips.firstOrNull { it.id == uiState.currentSegment?.clipId }
                        ?: uiState.selectedClipId?.let { selectedId -> uiState.clips.firstOrNull { it.id == selectedId } }
                        ?: uiState.clips.firstOrNull(),
                    segment = uiState.currentSegment,
                    isPlaying = uiState.isPlaying,
                    globalTime = uiState.globalProjectTimeMs,
                    isTrimDragging = uiState.trimmingClipId != null || uiState.interactionMode == EditorInteractionMode.BOUNDARY_TRIM,
                    trimPreviewFrameMs = uiState.trimPreviewFrameMs,
                    onPlayerPosition = viewModel::syncPlaybackFromPlayer,
                    resumeAfterPlaylistPrepared = uiState.resumePlaybackAfterPlaylistPrepared,
                    onPreparedPlaylistResume = viewModel::onPreparedPlaylistResumeStarted,
                    activeTransition = uiState.activeTransition,
                    previewVolumeClipId = previewVolumeClipId,
                    previewVolumeMultiplier = previewVolumeMultiplier,
                    lastAddedClipId = uiState.lastAddedClipId,
                    animationPreviewRestartKey = animationPreviewRestartKey,
                    onTogglePlay = { viewModel.togglePlayback() },
                    onEffectPreviewController = { effectPreviewControllerRef.value = it }
                )
                Spacer(Modifier.height(2.dp))
                CapCutTransportBar(
                    isPlaying = uiState.isPlaying,
                    onTogglePlay = { viewModel.togglePlayback() }
                )
                CapCutTimelineEditor(
                    clips = uiState.clips,
                    currentSegment = uiState.currentSegment,
                    globalTime = uiState.globalProjectTimeMs,
                    totalMs = uiState.totalDurationMs,
                    isPlaying = uiState.isPlaying,
                    selectedClipId = visibleSelectedClipId,
                    effects = timelineEffects,
                    textOverlays = timelineTextOverlays,
                    animationMarkers = clipAnimationMarkers,
                    selectionTarget = selectionTarget,
                    interactionMode = uiState.interactionMode,
                    splitAdjustClipAId = uiState.splitAdjustClipAId,
                    splitAdjustClipBId = uiState.splitAdjustClipBId,
                    previewSplitBoundaryMs = uiState.previewSplitBoundaryMs,
                    trimGestureActive = uiState.trimGestureActive,
                    trimSessionSide = uiState.trimSessionSide,
                    trimSessionStartMs = uiState.trimSessionStartMs,
                    preserveTimelineScrollVersion = uiState.preserveTimelineScrollVersion,
                    scrollState = timelineScrollState,
                    zoom = timelineZoom,
                    onZoomChange = { timelineZoom = it.coerceIn(MIN_TIMELINE_ZOOM, MAX_TIMELINE_ZOOM) },
                    frameCache = timelineFrameCache,
                    onSeekTo = { viewModel.seekTo(it) },
                    onBeginDrag = viewModel::beginTimelineDrag,
                    onScrubTo = viewModel::scrubTo,
                    onEndDrag = viewModel::endTimelineDrag,
                    onSuspendEffects = { effectPreviewControllerRef.value?.suspendEffects() },
                    onResumeEffects = { effectPreviewControllerRef.value?.resumeEffects() },
                    onSelectClip = { clipId ->
                        selectionController.selectClip(clipId)
                        viewModel.selectClip(clipId)
                    },
                    onSelectEffect = { effectId ->
                        screenScope.launch {
                            historyRegistry.execute(
                                SelectEffectCommand(
                                    selectionController = selectionController,
                                    effectId = effectId
                                )
                            )
                        }
                    },
                    onAddMusic = { onAddMusic?.invoke() ?: launchAudioPicker() },
                    onAddText = { onAddText?.invoke() ?: run { showTextSheet = true } },
                    audioTrackCount = uiState.audioTrackCount,
                    textTrackCount = uiState.textTrackCount,
                    overlayTrackCount = uiState.overlayTrackCount,
                    onLongPressClip = { clipId ->
                        selectionController.selectClip(clipId)
                        viewModel.selectClip(clipId)
                    },
                    onMoveClip = viewModel::moveClip,
                    onReorderClip = viewModel::reorderClip,
                    onResumePlayback = viewModel::play,
                    onTrimStarted = viewModel::startTrim,
                    onTrimPreviewUpdated = viewModel::updateTrimPreview,
                    onTrimCommitted = viewModel::commitTrim,
                    onTrimStartDragged = viewModel::onTrimStartDragged,
                    onTrimEndDragged = viewModel::onTrimEndDragged,
                    onTrimFinished = viewModel::onTrimFinished,
                    onSplitBoundarySelected = viewModel::enterSplitAdjustMode,
                    onSplitBoundaryTrimStarted = viewModel::startSharedBoundaryTrim,
                    onSplitBoundaryDragged = viewModel::updateSharedBoundaryTrim,
                    onSplitBoundaryFinished = viewModel::commitSharedBoundaryTrim,
                    onSplitAdjustDone = { viewModel.commitSplitBoundary() },
                    onOpenTransition = viewModel::openTransitionPicker,
                    onAddMedia = launchAppendMediaPicker
                )
            }
            if (showVolumeSheet) {
                val selectedClip = uiState.clips.firstOrNull { it.id == uiState.selectedClipId }
                VolumePanel(
                    initialVolume = selectedClip?.volume ?: 1f,
                    hasAudio = selectedClip?.mediaType == MediaType.VIDEO || selectedClip?.mediaType == MediaType.OVERLAY_VIDEO,
                    onPreview = {
                        previewVolumeClipId = selectedClip?.id
                        previewVolumeMultiplier = it
                    },
                    onCommit = {
                        viewModel.setSelectedClipVolume(it)
                    },
                    onReset = {
                        previewVolumeClipId = selectedClip?.id
                        previewVolumeMultiplier = 1f
                        viewModel.setSelectedClipVolume(1f)
                    },
                    onBack = {
                        previewVolumeClipId = null
                        showVolumeSheet = false
                    },
                    onDone = {
                        viewModel.setSelectedClipVolume(it)
                        previewVolumeClipId = null
                        showVolumeSheet = false
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(10f)
                )
            }
            uiState.pickerOpenForClipId?.let { boundaryClipId ->
                val boundaryClip = uiState.clips.firstOrNull { it.id == boundaryClipId }
                CapCutTransitionPanel(
                    fromClip = boundaryClip,
                    toClip = uiState.clips.sortedBy { it.timelineStartMs }
                        .dropWhile { it.id != boundaryClipId }
                        .drop(1)
                        .firstOrNull(),
                    initialType = boundaryClip?.transition?.type ?: TransitionType.NONE,
                    initialDurationMs = boundaryClip?.transition?.durationMs ?: 300L,
                    onBack = viewModel::closeTransitionPicker,
                    onApply = { type, durationMs ->
                        viewModel.applyTransitionToClip(boundaryClipId, type, durationMs, closePanel = false)
                    },
                    onConfirm = { type, durationMs ->
                        viewModel.confirmTransitionSelection(boundaryClipId, type, durationMs)
                    },
                    onApplyAll = { type, durationMs ->
                        viewModel.applyTransitionToAll(type, durationMs)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(11f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CapCutEditorTopBar(onBack: () -> Unit, onExport: () -> Unit) {
    TopAppBar(
        title = {},
        navigationIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
            }
        },
        actions = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF303033)
            ) {
                Text(
                    "AI UHD",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onExport,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20D4F5)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(44.dp).padding(end = AppSpacing.md)
            ) {
                Text("Export", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF151515))
    )
}

private data class PreviewTransitionLayerState(
    val alpha: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scale: Float = 1f,
    val rotationZ: Float = 0f,
    val rotationY: Float = 0f,
    val rotationX: Float = 0f
)

private data class PreviewTransitionVisualState(
    val outgoing: PreviewTransitionLayerState = PreviewTransitionLayerState(),
    val incoming: PreviewTransitionLayerState = PreviewTransitionLayerState(alpha = 0f),
    val overlayColor: Color? = null,
    val overlayAlpha: Float = 0f
)

private fun previewTransitionVisualState(
    type: TransitionType?,
    progress: Float,
    widthPx: Float,
    heightPx: Float
): PreviewTransitionVisualState {
    val rawProgress = progress.coerceIn(0f, 1f)
    val p = TransitionSpec.smoothstep(rawProgress)
    val spec = TransitionSpec.forType(type)
    return when (spec) {
        TransitionSpec.None,
        TransitionSpec.PlainCut -> PreviewTransitionVisualState()
        TransitionSpec.Crossfade -> PreviewTransitionVisualState(
            outgoing = PreviewTransitionLayerState(),
            incoming = PreviewTransitionLayerState(alpha = p)
        )
        is TransitionSpec.Dip -> {
            val color = when (spec.color) {
                TransitionSpec.DipColor.Black -> Color.Black
                TransitionSpec.DipColor.White -> Color.White
            }
            val firstHalf = rawProgress <= 0.5f
            val dipOut = if (firstHalf) TransitionSpec.smoothstep(rawProgress * 2f) else 1f
            val dipIn = if (firstHalf) 1f else 1f - TransitionSpec.smoothstep((rawProgress - 0.5f) * 2f)
            PreviewTransitionVisualState(
                outgoing = PreviewTransitionLayerState(alpha = if (firstHalf) 1f else 0f),
                incoming = PreviewTransitionLayerState(alpha = if (firstHalf) 0f else 1f),
                overlayColor = color,
                overlayAlpha = if (firstHalf) dipOut else dipIn
            )
        }
        is TransitionSpec.Flash -> PreviewTransitionVisualState(
            outgoing = PreviewTransitionLayerState(alpha = if (rawProgress < 0.5f) 1f else 0f),
            incoming = PreviewTransitionLayerState(alpha = if (rawProgress >= 0.5f) 1f else 0f),
            overlayColor = flashPreviewColor(spec.color),
            overlayAlpha = flashPreviewAlpha(rawProgress)
        )
        is TransitionSpec.FilmBurn -> {
            val peak = (1f - abs((rawProgress * 2f) - 1f)).coerceIn(0f, 1f)
            val reveal = TransitionSpec.smoothstep((rawProgress * 1.18f).coerceIn(0f, 1f))
            val overlayAlpha = when (spec.mode) {
                TransitionSpec.FilmBurnMode.Classic -> 0.34f
                TransitionSpec.FilmBurnMode.Warm -> 0.42f
                TransitionSpec.FilmBurnMode.Heavy -> 0.50f
            } * peak
            PreviewTransitionVisualState(
                outgoing = PreviewTransitionLayerState(alpha = 1f - (0.20f * reveal)),
                incoming = PreviewTransitionLayerState(alpha = reveal),
                overlayColor = filmBurnPreviewColor(spec.mode),
                overlayAlpha = overlayAlpha
            )
        }
        is TransitionSpec.Blur -> PreviewTransitionVisualState(
            outgoing = PreviewTransitionLayerState(alpha = 1f - (p * 0.28f)),
            incoming = PreviewTransitionLayerState(alpha = p),
            overlayColor = Color(0xFFB8C7FF),
            overlayAlpha = (0.20f * (1f - abs((rawProgress * 2f) - 1f))).coerceIn(0f, 0.20f)
        )
        is TransitionSpec.Slide -> {
            val remaining = 1f - p
            val incoming = when (spec.direction) {
                TransitionSpec.SlideDirection.Left ->
                    PreviewTransitionLayerState(alpha = 1f, translationX = widthPx * remaining)
                TransitionSpec.SlideDirection.Right ->
                    PreviewTransitionLayerState(alpha = 1f, translationX = -widthPx * remaining)
                TransitionSpec.SlideDirection.Up ->
                    PreviewTransitionLayerState(alpha = 1f, translationY = heightPx * remaining)
                TransitionSpec.SlideDirection.Down ->
                    PreviewTransitionLayerState(alpha = 1f, translationY = -heightPx * remaining)
            }
            PreviewTransitionVisualState(
                outgoing = PreviewTransitionLayerState(),
                incoming = incoming
            )
        }
        is TransitionSpec.Push -> {
            val remaining = 1f - p
            val outgoing = when (spec.direction) {
                TransitionSpec.SlideDirection.Left ->
                    PreviewTransitionLayerState(alpha = 1f, translationX = -widthPx * p)
                TransitionSpec.SlideDirection.Right ->
                    PreviewTransitionLayerState(alpha = 1f, translationX = widthPx * p)
                TransitionSpec.SlideDirection.Up ->
                    PreviewTransitionLayerState(alpha = 1f, translationY = -heightPx * p)
                TransitionSpec.SlideDirection.Down ->
                    PreviewTransitionLayerState(alpha = 1f, translationY = heightPx * p)
            }
            val incoming = when (spec.direction) {
                TransitionSpec.SlideDirection.Left ->
                    PreviewTransitionLayerState(alpha = 1f, translationX = widthPx * remaining)
                TransitionSpec.SlideDirection.Right ->
                    PreviewTransitionLayerState(alpha = 1f, translationX = -widthPx * remaining)
                TransitionSpec.SlideDirection.Up ->
                    PreviewTransitionLayerState(alpha = 1f, translationY = heightPx * remaining)
                TransitionSpec.SlideDirection.Down ->
                    PreviewTransitionLayerState(alpha = 1f, translationY = -heightPx * remaining)
            }
            PreviewTransitionVisualState(
                outgoing = outgoing,
                incoming = incoming
            )
        }
        is TransitionSpec.Wipe -> PreviewTransitionVisualState(
            outgoing = PreviewTransitionLayerState(),
            incoming = PreviewTransitionLayerState(alpha = p)
        )
        is TransitionSpec.Zoom -> {
            val scaleStart = when (spec.mode) {
                TransitionSpec.ZoomMode.In -> TransitionSpec.ZOOM_IN_SCALE_START
                TransitionSpec.ZoomMode.Out -> TransitionSpec.ZOOM_OUT_SCALE_START
            }
            val scaleEnd = when (spec.mode) {
                TransitionSpec.ZoomMode.In -> TransitionSpec.ZOOM_IN_SCALE_END
                TransitionSpec.ZoomMode.Out -> TransitionSpec.ZOOM_OUT_SCALE_END
            }
            val alphaStart = when (spec.mode) {
                TransitionSpec.ZoomMode.In -> TransitionSpec.ZOOM_IN_ALPHA_START
                TransitionSpec.ZoomMode.Out -> TransitionSpec.ZOOM_OUT_ALPHA_START
            }
            val alphaEnd = when (spec.mode) {
                TransitionSpec.ZoomMode.In -> TransitionSpec.ZOOM_IN_ALPHA_END
                TransitionSpec.ZoomMode.Out -> TransitionSpec.ZOOM_OUT_ALPHA_END
            }
            PreviewTransitionVisualState(
                outgoing = PreviewTransitionLayerState(),
                incoming = PreviewTransitionLayerState(
                    alpha = TransitionSpec.lerp(alphaStart, alphaEnd, p),
                    scale = TransitionSpec.lerp(scaleStart, scaleEnd, p)
                )
            )
        }
        is TransitionSpec.Rotation -> {
            val outgoingRotation = when (spec.mode) {
                TransitionSpec.RotationMode.Spin -> -45f * p
                TransitionSpec.RotationMode.Rotate -> -24f * p
                TransitionSpec.RotationMode.CameraRoll -> 10f * p
            }
            val incomingRotationStart = when (spec.mode) {
                TransitionSpec.RotationMode.Spin -> 60f
                TransitionSpec.RotationMode.Rotate -> 24f
                TransitionSpec.RotationMode.CameraRoll -> -10f
            }
            val outgoingScaleStart = when (spec.mode) {
                TransitionSpec.RotationMode.Spin -> 1.08f
                TransitionSpec.RotationMode.Rotate -> 1.04f
                TransitionSpec.RotationMode.CameraRoll -> 1.12f
            }
            val outgoingScaleEnd = when (spec.mode) {
                TransitionSpec.RotationMode.Spin -> 0.94f
                TransitionSpec.RotationMode.Rotate -> 0.98f
                TransitionSpec.RotationMode.CameraRoll -> 1.12f
            }
            val incomingScaleStart = when (spec.mode) {
                TransitionSpec.RotationMode.Spin -> 0.84f
                TransitionSpec.RotationMode.Rotate -> 0.96f
                TransitionSpec.RotationMode.CameraRoll -> 1.12f
            }
            val incomingScaleEnd = when (spec.mode) {
                TransitionSpec.RotationMode.Spin,
                TransitionSpec.RotationMode.Rotate -> 1.0f
                TransitionSpec.RotationMode.CameraRoll -> 1.08f
            }
            val incomingAlphaStart = when (spec.mode) {
                TransitionSpec.RotationMode.Spin -> 0.15f
                TransitionSpec.RotationMode.Rotate -> 0.2f
                TransitionSpec.RotationMode.CameraRoll -> 0.25f
            }
            PreviewTransitionVisualState(
                outgoing = PreviewTransitionLayerState(
                    alpha = 1f,
                    scale = TransitionSpec.lerp(outgoingScaleStart, outgoingScaleEnd, p),
                    rotationZ = outgoingRotation
                ),
                incoming = PreviewTransitionLayerState(
                    alpha = TransitionSpec.lerp(incomingAlphaStart, 1f, p),
                    scale = TransitionSpec.lerp(incomingScaleStart, incomingScaleEnd, p),
                    rotationZ = incomingRotationStart * (1f - p)
                )
            )
        }
        is TransitionSpec.Cube -> {
            val vertical = spec.direction == TransitionSpec.CubeDirection.Up ||
                spec.direction == TransitionSpec.CubeDirection.Down
            // Compose translation sign (Y is down-positive): Up exits toward -Y.
            val sign = when (spec.direction) {
                TransitionSpec.CubeDirection.Left,
                TransitionSpec.CubeDirection.Up -> -1f
                TransitionSpec.CubeDirection.Right,
                TransitionSpec.CubeDirection.Down -> 1f
            }
            PreviewTransitionVisualState(
                outgoing = PreviewTransitionLayerState(
                    alpha = 1f,
                    translationX = if (vertical) 0f else sign * widthPx * 0.38f * p,
                    translationY = if (vertical) sign * heightPx * 0.38f * p else 0f,
                    scale = TransitionSpec.lerp(1f, 0.86f, p),
                    rotationY = if (vertical) 0f else sign * 72f * p,
                    rotationX = if (vertical) -sign * 72f * p else 0f
                ),
                incoming = PreviewTransitionLayerState(
                    alpha = TransitionSpec.lerp(0.82f, 1f, p),
                    translationX = if (vertical) 0f else -sign * widthPx * (1f - p),
                    translationY = if (vertical) -sign * heightPx * (1f - p) else 0f,
                    scale = TransitionSpec.lerp(0.86f, 1f, p),
                    rotationY = if (vertical) 0f else -sign * 72f * (1f - p),
                    rotationX = if (vertical) sign * 72f * (1f - p) else 0f
                )
            )
        }
        is TransitionSpec.Flip -> {
            val horizontal = spec.direction == TransitionSpec.FlipDirection.Left ||
                spec.direction == TransitionSpec.FlipDirection.Right
            val sign = when (spec.direction) {
                TransitionSpec.FlipDirection.Left,
                TransitionSpec.FlipDirection.Up -> -1f
                TransitionSpec.FlipDirection.Right,
                TransitionSpec.FlipDirection.Down -> 1f
            }
            val revealRaw = ((p - 0.5f) * 2f).coerceIn(0f, 1f)
            val reveal = TransitionSpec.smoothstep(revealRaw)
            val edgeScale = TransitionSpec.lerp(0.04f, 1f, reveal)
            PreviewTransitionVisualState(
                outgoing = PreviewTransitionLayerState(
                    alpha = if (p < 0.5f) 1f else 0f,
                    rotationY = if (horizontal) sign * 90f * p.coerceAtMost(0.5f) * 2f else 0f,
                    rotationX = if (horizontal) 0f else sign * 90f * p.coerceAtMost(0.5f) * 2f
                ),
                incoming = PreviewTransitionLayerState(
                    alpha = if (p >= 0.5f) 1f else 0f,
                    scale = edgeScale,
                    rotationY = 0f,
                    rotationX = 0f
                )
            )
        }
        is TransitionSpec.PageTurn -> {
            val sign = when (spec.direction) {
                TransitionSpec.PageTurnDirection.Left -> -1f
                TransitionSpec.PageTurnDirection.Right -> 1f
                TransitionSpec.PageTurnDirection.Up -> -1f
                TransitionSpec.PageTurnDirection.Down -> 1f
            }
            val vertical = spec.direction == TransitionSpec.PageTurnDirection.Up ||
                spec.direction == TransitionSpec.PageTurnDirection.Down
            val reveal = TransitionSpec.smoothstep((p * 1.15f).coerceIn(0f, 1f))
            PreviewTransitionVisualState(
                outgoing = PreviewTransitionLayerState(
                    alpha = 1f - (0.30f * p),
                    translationX = if (vertical) 0f else sign * widthPx * 0.10f * p,
                    translationY = if (vertical) sign * heightPx * 0.10f * p else 0f,
                    scale = TransitionSpec.lerp(1f, 0.93f, p),
                    rotationY = if (vertical) 0f else sign * 58f * p,
                    rotationX = if (vertical) sign * 58f * p else 0f
                ),
                incoming = PreviewTransitionLayerState(alpha = reveal),
                overlayColor = Color.Black,
                overlayAlpha = 0.18f * (1f - abs((rawProgress * 2f) - 1f)).coerceIn(0f, 1f)
            )
        }
        is TransitionSpec.WhipPan -> {
            val remaining = 1f - p
            val peak = (1f - abs((rawProgress * 2f) - 1f)).coerceIn(0f, 1f)
            val incoming = when (spec.direction) {
                TransitionSpec.SlideDirection.Left ->
                    PreviewTransitionLayerState(alpha = 1f, translationX = widthPx * remaining)
                TransitionSpec.SlideDirection.Right ->
                    PreviewTransitionLayerState(alpha = 1f, translationX = -widthPx * remaining)
                TransitionSpec.SlideDirection.Up ->
                    PreviewTransitionLayerState(alpha = 1f, translationY = heightPx * remaining)
                TransitionSpec.SlideDirection.Down ->
                    PreviewTransitionLayerState(alpha = 1f, translationY = -heightPx * remaining)
            }
            PreviewTransitionVisualState(
                outgoing = PreviewTransitionLayerState(),
                incoming = incoming,
                overlayColor = Color.White,
                overlayAlpha = peak * 0.10f
            )
        }
        is TransitionSpec.MotionBlur -> {
            val peak = (1f - abs((rawProgress * 2f) - 1f)).coerceIn(0f, 1f)
            PreviewTransitionVisualState(
                outgoing = PreviewTransitionLayerState(),
                incoming = PreviewTransitionLayerState(alpha = p),
                overlayColor = Color.White,
                overlayAlpha = peak * 0.06f
            )
        }
        is TransitionSpec.GlitchPro -> {
            val burstRate = when (spec.mode) {
                TransitionSpec.GlitchMode.Pro -> 12f
                TransitionSpec.GlitchMode.Digital -> 9f
                TransitionSpec.GlitchMode.Rgb -> 10f
                TransitionSpec.GlitchMode.Scanline -> 14f
            }
            val envelope = (1f - abs((rawProgress * 2f) - 1f)).coerceIn(0f, 1f)
            val tBurst = kotlin.math.floor(rawProgress * burstRate) / burstRate
            val seed = abs(kotlin.math.sin((tBurst * 91.7f) + 0.37f))
            val forcedMid = rawProgress in 0.46f..0.54f
            val gate = forcedMid || seed > (1f - envelope * 0.65f)
            val amp = if (gate) envelope else 0f
            val xBias = when (spec.mode) {
                TransitionSpec.GlitchMode.Rgb -> 0.015f
                TransitionSpec.GlitchMode.Scanline -> 0.010f
                else -> 0.040f
            }
            val yBias = when (spec.mode) {
                TransitionSpec.GlitchMode.Scanline -> 0.035f
                TransitionSpec.GlitchMode.Digital -> 0.018f
                else -> 0.010f
            }
            val sign = if (seed > 0.5f) 1f else -1f
            val tint = when (spec.mode) {
                TransitionSpec.GlitchMode.Pro -> if (seed > 0.5f) Color.Cyan else Color.Magenta
                TransitionSpec.GlitchMode.Digital -> Color(0xFFFF4FBC)
                TransitionSpec.GlitchMode.Rgb -> Color.Cyan
                TransitionSpec.GlitchMode.Scanline -> Color(0xFF74D9FF)
            }
            PreviewTransitionVisualState(
                outgoing = PreviewTransitionLayerState(
                    alpha = if (rawProgress < 0.5f) 1f else 0f,
                    translationX = sign * amp * widthPx * xBias,
                    translationY = -sign * amp * heightPx * yBias,
                    scale = if (spec.mode == TransitionSpec.GlitchMode.Digital) 1f + amp * 0.015f else 1f
                ),
                incoming = PreviewTransitionLayerState(
                    alpha = if (rawProgress >= 0.5f) 1f else 0f,
                    translationX = -sign * amp * widthPx * xBias,
                    translationY = sign * amp * heightPx * yBias,
                    scale = if (spec.mode == TransitionSpec.GlitchMode.Digital) 1f + amp * 0.015f else 1f
                ),
                overlayColor = tint,
                overlayAlpha = (amp * 0.12f).coerceAtMost(0.12f)
            )
        }
    }
}

private val FLASH_PREVIEW_TYPES = setOf(
    TransitionType.FLASH,
    TransitionType.FLASH_BLACK,
    TransitionType.FLASH_WARM,
    TransitionType.FLASH_BLUE
)

private val FILM_BURN_PREVIEW_TYPES = setOf(
    TransitionType.FILM_BURN,
    TransitionType.FILM_BURN_WARM,
    TransitionType.FILM_BURN_HEAVY
)

private fun flashPreviewColor(color: TransitionSpec.FlashColor): Color = when (color) {
    TransitionSpec.FlashColor.White -> Color.White
    TransitionSpec.FlashColor.Black -> Color.Black
    TransitionSpec.FlashColor.Warm -> Color(0xFFFFD680)
    TransitionSpec.FlashColor.Blue -> Color(0xFF78BEFF)
}

private fun flashPreviewAlpha(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return when {
        t <= 0.38f -> TransitionSpec.smoothstep(t / 0.38f)
        t <= 0.52f -> 1f
        else -> 1f - TransitionSpec.smoothstep((t - 0.52f) / 0.48f)
    }.coerceIn(0f, 1f)
}

private fun filmBurnPreviewColor(mode: TransitionSpec.FilmBurnMode): Color = when (mode) {
    TransitionSpec.FilmBurnMode.Classic -> Color(0xFFFF9A3D)
    TransitionSpec.FilmBurnMode.Warm -> Color(0xFFFFC76A)
    TransitionSpec.FilmBurnMode.Heavy -> Color(0xFF2A1208)
}

@Composable
private fun CapCutPreviewArea(
    projectId: String,
    clips: List<ClipUiModel>,
    textOverlays: List<TextOverlay>,
    clip: ClipUiModel?,
    segment: TimelineSegment?,
    isPlaying: Boolean,
    globalTime: Long,
    isTrimDragging: Boolean,
    trimPreviewFrameMs: Long?,
    onPlayerPosition: (Int, String?, Long) -> Unit,
    resumeAfterPlaylistPrepared: Boolean,
    onPreparedPlaylistResume: () -> Unit,
    activeTransition: ActiveTransitionState?,
    previewVolumeClipId: String?,
    previewVolumeMultiplier: Float,
    lastAddedClipId: String?,
    animationPreviewRestartKey: Long,
    onTogglePlay: () -> Unit,
    onEffectPreviewController: (EffectPreviewController?) -> Unit
) {
    val previewClip = clip ?: clips.firstOrNull {
        it.thumbnailUri != null &&
            (it.mediaType == MediaType.VIDEO || it.mediaType == MediaType.OVERLAY_VIDEO)
    }
    LaunchedEffect(clips.size, previewClip?.id, globalTime) {
        Log.d(
            SCREEN_TAG,
            "PREVIEW_TIMELINE_BOUND clips=${clips.size} previewClipId=${previewClip?.id} currentTimelineMs=$globalTime " +
                "placeholderVisible=${clips.isEmpty()}"
        )
    }
    val transitionProgress = activeTransition?.progress?.coerceIn(0f, 1f)
    val transitionType = activeTransition?.transitionType
    val density = LocalDensity.current
    var previewControlsVisible by remember { mutableStateOf(true) }
    var previewZoom by remember { mutableFloatStateOf(1f) }
    var previewPanX by remember { mutableFloatStateOf(0f) }
    var previewPanY by remember { mutableFloatStateOf(0f) }
    val latestPreviewTimeMs by rememberUpdatedState(globalTime)
    val previewTransformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextZoom = (previewZoom * zoomChange).coerceIn(1f, 4f)
        previewZoom = nextZoom
        previewPanX = if (nextZoom == 1f) 0f else (previewPanX + panChange.x).coerceIn(-360f, 360f)
        previewPanY = if (nextZoom == 1f) 0f else (previewPanY + panChange.y).coerceIn(-640f, 640f)
        previewControlsVisible = true
        Log.v(SCREEN_TAG, "PREVIEW_GESTURE_ZOOM scale=$previewZoom panX=$previewPanX panY=$previewPanY")
    }
    val isTransitionActive = transitionProgress != null && transitionType != null && transitionType != TransitionType.NONE
    val incomingClip = activeTransition?.toClipId?.let { toClipId -> clips.firstOrNull { it.id == toClipId } }
    val incomingTransitionFrameMs = if (isTransitionActive && incomingClip != null) {
        val elapsedTransitionMs = ((activeTransition?.transitionDurationMs ?: 0L) * (transitionProgress ?: 0f)).roundToLong()
        (incomingClip.sourceStartMs + elapsedTransitionMs)
            .coerceIn(
                incomingClip.sourceStartMs,
                (incomingClip.sourceEndMs - 1L).coerceAtLeast(incomingClip.sourceStartMs)
            )
    } else {
        null
    }
    val progressBucket = transitionProgress?.let { (it * 10f).roundToInt() }
    LaunchedEffect(activeTransition?.fromClipId, activeTransition?.toClipId, transitionType, progressBucket) {
        activeTransition?.let { transition ->
            Log.d(
                SCREEN_TAG,
                "TRANSITION_ACTIVE progress=${transition.progress} overlayVisible=true previewComposableVisible=${previewClip != null} " +
                    "transitionType=${transition.transitionType.name} transitionDurationMs=${transition.transitionDurationMs} " +
                    "transitionStartMs=${transition.transitionStartMs} transitionEndMs=${transition.transitionEndMs}"
            )
            if (transition.transitionType in setOf(
                    TransitionType.FADE,
                    TransitionType.DISSOLVE,
                    TransitionType.CROSS_DISSOLVE,
                    TransitionType.FADE_BLACK,
                    TransitionType.FADE_WHITE
                )
            ) {
                Log.d(
                    SCREEN_TAG,
                    "FADE_TIMING clipAStartMs=${transition.clipAStartMs} clipAEndMs=${transition.clipAEndMs} " +
                        "transitionDurationMs=${transition.transitionDurationMs} transitionStartMs=${transition.transitionStartMs} " +
                        "transitionEndMs=${transition.transitionEndMs} currentTimelineMs=$globalTime " +
                        "progress=${transition.progress}"
                )
            }
        }
    }
    LaunchedEffect(previewClip?.id) {
        Log.d(
            SCREEN_TAG,
            "PREVIEW_GESTURE_HIT_AREA scopedToPreview=true widthDp=210 aspectRatio=9:16 " +
                "bottomToolbarIntercept=false previewClipId=${previewClip?.id}"
        )
    }
    LaunchedEffect(isPlaying, previewControlsVisible) {
        if (isPlaying && previewControlsVisible) {
            delay(1800L)
            previewControlsVisible = false
            Log.d(SCREEN_TAG, "PREVIEW_CONTROLS_AUTO_HIDE visible=false currentTimelineMs=$latestPreviewTimeMs")
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(304.dp)
            .zIndex(0f)
            .background(Color(0xFF151515)),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .width(210.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(2.dp))
                .graphicsLayer {
                    scaleX = previewZoom
                    scaleY = previewZoom
                    translationX = previewPanX
                    translationY = previewPanY
                }
                .transformable(previewTransformState)
                .pointerInput(previewClip?.id, isPlaying, previewControlsVisible) {
                    detectTapGestures(
                        onDoubleTap = {
                            previewZoom = 1f
                            previewPanX = 0f
                            previewPanY = 0f
                            previewControlsVisible = true
                            Log.d(SCREEN_TAG, "PREVIEW_DOUBLE_TAP_FIT scale=$previewZoom panX=$previewPanX panY=$previewPanY")
                        },
                        onTap = {
                            previewControlsVisible = !previewControlsVisible
                            Log.d(SCREEN_TAG, "PREVIEW_CONTROLS_TOGGLE visible=$previewControlsVisible currentTimelineMs=$globalTime")
                        }
                    )
                }
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val transitionVisualState = previewTransitionVisualState(
                type = transitionType,
                progress = transitionProgress ?: 0f,
                widthPx = with(density) { maxWidth.toPx() },
                heightPx = with(density) { maxHeight.toPx() }
            )
            val outgoingState = transitionVisualState.outgoing
            val incomingState = transitionVisualState.incoming
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val transform = previewClip?.transform ?: ClipTransform()
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offsetX
                        translationY = transform.offsetY
                        rotationZ = transform.rotation
                        alpha = previewClip?.opacity ?: 1f
                    }
                    .zIndex(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = outgoingState.alpha.coerceIn(0f, 1f)
                            translationX = outgoingState.translationX
                            translationY = outgoingState.translationY
                            scaleX = outgoingState.scale
                            scaleY = outgoingState.scale
                            rotationZ = outgoingState.rotationZ
                            rotationY = outgoingState.rotationY
                            rotationX = outgoingState.rotationX
                            cameraDistance = 12f * density.density
                        }
                        .zIndex(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        previewClip?.thumbnailUri != null &&
                            (previewClip.mediaType == MediaType.VIDEO || previewClip.mediaType == MediaType.OVERLAY_VIDEO) -> {
                            VideoPreviewPlayer(
                                projectId = projectId,
                                clips = clips,
                                clip = previewClip,
                                segment = if (segment?.clipId == previewClip.id) segment else null,
                                isPlaying = isPlaying,
                                globalTime = globalTime,
                                isTrimDragging = isTrimDragging && previewClip.id == segment?.clipId,
                                trimPreviewFrameMs = trimPreviewFrameMs,
                                onPlayerPosition = onPlayerPosition,
                                resumeAfterPlaylistPrepared = resumeAfterPlaylistPrepared,
                                onPreparedPlaylistResume = onPreparedPlaylistResume,
                                volume = if (previewVolumeClipId == previewClip.id) {
                                    previewVolumeMultiplier
                                } else {
                                    previewClip.volume
                                },
                                lastAddedClipId = lastAddedClipId,
                                animationPreviewRestartKey = animationPreviewRestartKey,
                                transitionAlpha = 1f,
                                onEffectPreviewController = onEffectPreviewController,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        previewClip?.thumbnailUri != null -> {
                            AsyncImage(
                                model = previewClip.thumbnailUri,
                                contentDescription = "Preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        clips.isNotEmpty() -> {
                            Text("Preview", color = AppColors.TextSecondary, fontSize = 13.sp)
                        }
                        else -> {
                            Text("Preview", color = AppColors.TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
                if (incomingClip != null && incomingState.alpha > 0f) {
                    IncomingTransitionLayer(
                        clip = incomingClip,
                        sourceFrameMs = incomingTransitionFrameMs,
                        alpha = incomingState.alpha,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = incomingState.translationX
                                translationY = incomingState.translationY
                                scaleX = incomingState.scale
                                scaleY = incomingState.scale
                                rotationZ = incomingState.rotationZ
                                rotationY = incomingState.rotationY
                                rotationX = incomingState.rotationX
                                cameraDistance = 12f * density.density
                            }
                            .zIndex(2f)
                    )
                }
                if (transitionVisualState.overlayColor != null && transitionVisualState.overlayAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(transitionVisualState.overlayColor.copy(alpha = transitionVisualState.overlayAlpha))
                            .zIndex(3f)
                    )
                }
            }
            PreviewOverlayHost(
                textOverlays = textOverlays,
                timelineTimeMs = globalTime,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(4f)
            )
            if (isTransitionActive) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .zIndex(5f),
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${transitionType?.label ?: "Transition"}\n" +
                            "LayerA alpha=${((outgoingState.alpha * 100f).roundToInt() / 100f)}\n" +
                            "LayerB alpha=${((incomingState.alpha * 100f).roundToInt() / 100f)}",
                        color = AppColors.Primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
        if (previewControlsVisible) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.20f))
                    .clickable {
                        previewControlsVisible = true
                        onTogglePlay()
                        Log.d(SCREEN_TAG, "PREVIEW_PLAYBACK_TOGGLE requestedPlay=${!isPlaying} currentTimelineMs=$globalTime")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (isPlaying) "Pause" else "Play", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TransitionPreviewOverlay(
    clips: List<ClipUiModel>,
    activeTransition: ActiveTransitionState?
) {
    val transition = activeTransition ?: return
    val fromClip = clips.firstOrNull { it.id == transition.fromClipId } ?: return
    val toClip = clips.firstOrNull { it.id == transition.toClipId } ?: return
    val progressBucket = (transition.progress * 10f).roundToInt()
    LaunchedEffect(transition.fromClipId, transition.toClipId, transition.transitionType, progressBucket) {
        Log.d(
            SCREEN_TAG,
            "TRANSITION_ACTIVE transitionType=${transition.transitionType.name} transitionProgress=${transition.progress} " +
                "transitionStartMs=${transition.transitionStartMs} transitionEndMs=${transition.transitionEndMs} " +
                "fromClipId=${transition.fromClipId} toClipId=${transition.toClipId}"
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val progress = transition.progress.coerceIn(0f, 1f)
        val outgoingAlpha = when (transition.transitionType) {
            TransitionType.FADE,
            TransitionType.DISSOLVE,
            TransitionType.CROSS_DISSOLVE,
            TransitionType.FADE_BLACK,
            TransitionType.FADE_WHITE -> 1f - (progress * 0.65f)
            TransitionType.ZOOM_OUT -> 1f - progress
            TransitionType.BLUR,
            TransitionType.GAUSSIAN_BLUR,
            TransitionType.MOTION_BLUR -> 1f - (progress * 0.35f)
            TransitionType.FLASH,
            TransitionType.FLASH_BLACK,
            TransitionType.FLASH_WARM,
            TransitionType.FLASH_BLUE -> 1f - (progress * 0.18f)
            TransitionType.FILM_BURN,
            TransitionType.FILM_BURN_WARM,
            TransitionType.FILM_BURN_HEAVY -> 1f - (progress * 0.22f)
            else -> 1f
        }.coerceIn(0f, 1f)
        val incomingAlpha = when (transition.transitionType) {
            TransitionType.NONE -> 0f
            TransitionType.FADE,
            TransitionType.DISSOLVE,
            TransitionType.CROSS_DISSOLVE,
            TransitionType.FADE_BLACK,
            TransitionType.FADE_WHITE,
            TransitionType.SLIDE_LEFT,
            TransitionType.SLIDE_RIGHT,
            TransitionType.PUSH_LEFT,
            TransitionType.PUSH_RIGHT,
            TransitionType.ZOOM_IN -> progress
            TransitionType.ZOOM_OUT,
            TransitionType.BLUR,
            TransitionType.GAUSSIAN_BLUR,
            TransitionType.MOTION_BLUR,
            TransitionType.SPIN,
            TransitionType.FLIP_LEFT,
            TransitionType.FLIP_RIGHT,
            TransitionType.FLIP_UP,
            TransitionType.FLIP_DOWN,
            TransitionType.PAGE_TURN_LEFT,
            TransitionType.PAGE_TURN_RIGHT,
            TransitionType.PAGE_TURN_UP,
            TransitionType.PAGE_TURN_DOWN,
            TransitionType.FLASH,
            TransitionType.FLASH_BLACK,
            TransitionType.FLASH_WARM,
            TransitionType.FLASH_BLUE,
            TransitionType.FILM_BURN,
            TransitionType.FILM_BURN_WARM,
            TransitionType.FILM_BURN_HEAVY,
            TransitionType.WIPE,
            TransitionType.WIPE_RIGHT,
            TransitionType.SLIDE_UP,
            TransitionType.SLIDE_DOWN,
            TransitionType.PUSH_UP,
            TransitionType.PUSH_DOWN,
            TransitionType.WIPE_UP,
            TransitionType.WIPE_DOWN,
            TransitionType.MIRROR_FLIP,
            TransitionType.GLITCH -> progress
            else -> progress
        }.coerceIn(0f, 1f)
        val outgoingTranslationX = when (transition.transitionType) {
            TransitionType.SLIDE_LEFT, TransitionType.PUSH_LEFT -> -progress * widthPx
            TransitionType.SLIDE_RIGHT, TransitionType.PUSH_RIGHT -> progress * widthPx
            TransitionType.WIPE -> -progress * widthPx * 0.35f
            TransitionType.WIPE_RIGHT -> progress * widthPx * 0.35f
            else -> 0f
        }
        val incomingTranslationX = when (transition.transitionType) {
            TransitionType.SLIDE_LEFT, TransitionType.PUSH_LEFT -> widthPx - progress * widthPx
            TransitionType.SLIDE_RIGHT, TransitionType.PUSH_RIGHT -> -widthPx + progress * widthPx
            TransitionType.WIPE -> widthPx * (1f - progress) * 0.5f
            TransitionType.WIPE_RIGHT -> -widthPx * (1f - progress) * 0.5f
            else -> 0f
        }
        val outgoingScale = when (transition.transitionType) {
            TransitionType.ZOOM_OUT -> 1f - progress * 0.2f
            TransitionType.ZOOM_IN -> 1f + progress * 0.08f
            TransitionType.SPIN -> 1f + progress * 0.05f
            else -> 1f
        }
        val incomingScale = when (transition.transitionType) {
            TransitionType.ZOOM_IN -> 0.8f + progress * 0.2f
            TransitionType.ZOOM_OUT -> 1.08f - progress * 0.08f
            TransitionType.SPIN -> 0.88f + progress * 0.12f
            else -> 1f
        }
        val outgoingOverlay = when (transition.transitionType) {
            TransitionType.FADE_BLACK -> Color.Black.copy(alpha = (progress * 0.75f).coerceIn(0f, 0.75f))
            TransitionType.FADE_WHITE -> Color.White.copy(alpha = (progress * 0.75f).coerceIn(0f, 0.75f))
            TransitionType.ZOOM_OUT -> Color.Black.copy(alpha = (progress * 0.35f).coerceIn(0f, 0.35f))
            else -> Color.Transparent
        }
        TransitionPreviewLayer(
            clip = fromClip,
            label = "A",
            alpha = outgoingAlpha,
            translationX = outgoingTranslationX,
            scale = outgoingScale,
            rotationZ = if (transition.transitionType == TransitionType.SPIN) -progress * 12f else 0f
        )
        TransitionPreviewLayer(
            clip = toClip,
            label = "B",
            alpha = incomingAlpha,
            translationX = incomingTranslationX,
            scale = incomingScale,
            rotationZ = if (transition.transitionType == TransitionType.SPIN) (1f - progress) * 24f else 0f
        )
        if (outgoingOverlay.alpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(outgoingOverlay)
            )
        }
        if (transition.transitionType in FLASH_PREVIEW_TYPES) {
            val flashSpec = TransitionSpec.forType(transition.transitionType) as? TransitionSpec.Flash
            val flashAlpha = flashPreviewAlpha(progress)
            val flashColor = flashPreviewColor(flashSpec?.color ?: TransitionSpec.FlashColor.White)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(flashColor.copy(alpha = flashAlpha))
            )
        }
        if (transition.transitionType in FILM_BURN_PREVIEW_TYPES) {
            val burnSpec = TransitionSpec.forType(transition.transitionType) as? TransitionSpec.FilmBurn
            val peak = (1f - kotlin.math.abs((progress * 2f) - 1f)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(filmBurnPreviewColor(burnSpec?.mode ?: TransitionSpec.FilmBurnMode.Classic).copy(alpha = peak * 0.38f))
            )
        }
        if (
            transition.transitionType == TransitionType.BLUR ||
            transition.transitionType == TransitionType.GAUSSIAN_BLUR ||
            transition.transitionType == TransitionType.MOTION_BLUR
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFB8C7FF).copy(alpha = (0.32f * (1f - progress)).coerceIn(0f, 0.32f)))
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    "TRANSITION ACTIVE ${transition.transitionType.label} progress=${((progress * 100f).roundToInt() / 100f)} duration=${transition.transitionDurationMs}ms",
                    color = AppColors.Primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TransitionPreviewLayer(
    clip: ClipUiModel,
    label: String,
    alpha: Float,
    translationX: Float,
    scale: Float,
    rotationZ: Float
) {
    val layerModifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            this.alpha = alpha.coerceIn(0f, 1f)
            this.translationX = translationX
            scaleX = scale
            scaleY = scale
            this.rotationZ = rotationZ
        }
    if (clip.thumbnailUri != null) {
        AsyncImage(
            model = clip.thumbnailUri,
            contentDescription = "Transition preview $label",
            contentScale = ContentScale.Crop,
            modifier = layerModifier
        )
    } else {
        Box(
            modifier = layerModifier.background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2B2B34), Color(0xFF111118))
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IncomingTransitionLayer(
    clip: ClipUiModel,
    sourceFrameMs: Long?,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val frameBucketMs = sourceFrameMs?.let { (it / 100L) * 100L }
    val isVideo = clip.mediaType == MediaType.VIDEO || clip.mediaType == MediaType.OVERLAY_VIDEO
    val frame by produceState<Bitmap?>(
        initialValue = null,
        clip.id,
        clip.thumbnailUri,
        frameBucketMs,
        isVideo
    ) {
        value = if (isVideo && clip.thumbnailUri != null && frameBucketMs != null) {
            loadPreviewFrame(
                context = context,
                uriString = clip.thumbnailUri,
                sourceFrameMs = frameBucketMs,
                clipId = clip.id
            )
        } else {
            null
        }
    }
    val layerModifier = modifier.graphicsLayer {
        this.alpha = alpha.coerceIn(0f, 1f)
    }
    val frameVisible = frame != null || clip.thumbnailUri != null
    LaunchedEffect(clip.id, frameBucketMs, alpha, frameVisible) {
        Log.d(
            SCREEN_TAG,
            "CROSSFADE_LAYER_B clipId=${clip.id} alpha=$alpha frameBucketMs=$frameBucketMs " +
                "frameVisible=$frameVisible zeroSize=false hidden=false"
        )
    }
    when {
        frame != null -> {
            Image(
                bitmap = frame!!.asImageBitmap(),
                contentDescription = "Incoming transition preview",
                contentScale = ContentScale.Crop,
                modifier = layerModifier
            )
        }
        clip.thumbnailUri != null -> {
            AsyncImage(
                model = clip.thumbnailUri,
                contentDescription = "Incoming transition preview",
                contentScale = ContentScale.Crop,
                modifier = layerModifier
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPreviewPlayer(
    projectId: String,
    clips: List<ClipUiModel>,
    clip: ClipUiModel,
    segment: TimelineSegment?,
    isPlaying: Boolean,
    globalTime: Long,
    isTrimDragging: Boolean,
    trimPreviewFrameMs: Long?,
    onPlayerPosition: (Int, String?, Long) -> Unit,
    resumeAfterPlaylistPrepared: Boolean,
    onPreparedPlaylistResume: () -> Unit,
    volume: Float = 1f,
    lastAddedClipId: String? = null,
    animationPreviewRestartKey: Long,
    transitionAlpha: Float = 1f,
    onEffectPreviewController: (EffectPreviewController?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(context) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                3_000,
                10_000,
                250,
                500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
            playWhenReady = false
            this.volume = volume
            Log.d(SCREEN_TAG, "playerCreated")
        }
    }
    val effectPreviewController = remember(player, context, scope) {
        val app = context.applicationContext as? ClipForgeApp
        app?.let {
            EffectPreviewController(
                player = player,
                repository = it.effectRepository,
                scope = scope
            )
        }
    }
    LaunchedEffect(effectPreviewController) {
        onEffectPreviewController(effectPreviewController)
    }
    LaunchedEffect(effectPreviewController, projectId) {
        effectPreviewController?.bind(projectId)
    }
    val playableClips = remember(clips) {
        clips.filter {
            it.thumbnailUri != null &&
                (it.mediaType == MediaType.VIDEO || it.mediaType == MediaType.OVERLAY_VIDEO)
        }
    }
    val playbackItems = remember(playableClips) {
        val items = mutableListOf<PreviewPlaybackItem>()
        var group = mutableListOf<ClipUiModel>()
        fun flush() {
            if (group.isEmpty()) return
            val first = group.first()
            val uri = first.thumbnailUri ?: return
            val sameSourceGroup = group.size > 1
            items += PreviewPlaybackItem(
                id = if (sameSourceGroup) group.joinToString("+") { it.id } else first.id,
                uri = uri,
                clips = group.toList(),
                clippingStartMs = if (sameSourceGroup) 0L else first.sourceStartMs,
                clippingEndMs = if (sameSourceGroup) {
                    group.maxOf { it.sourceEndMs.coerceAtLeast(it.sourceStartMs + 1L) }
                } else {
                    first.sourceEndMs.coerceAtLeast(first.sourceStartMs + 1L)
                }
            )
            group = mutableListOf()
        }
        playableClips.forEach { timelineClip ->
            val previous = group.lastOrNull()
            val sameSource = previous != null &&
                previous.mediaAssetId == timelineClip.mediaAssetId &&
                previous.thumbnailUri == timelineClip.thumbnailUri
            if (previous != null && !sameSource) flush()
            group += timelineClip
        }
        flush()
        items
    }
    val playlistSignature = remember(playbackItems) {
        playbackItems.joinToString("|") { item ->
            "${item.id}:${item.uri}:${item.clips.joinToString(",") { "${it.id}:${it.sourceStartMs}:${it.sourceEndMs}:${it.playbackSpeed}" }}"
        }
    }
    val visibleTimelineSignature = remember(playableClips) {
        playableClips.joinToString("|") {
            "${it.id}:${it.thumbnailUri}:${it.sourceStartMs}:${it.sourceEndMs}:${it.durationMs}:${it.playbackSpeed}"
        }
    }
    var preparedPlaylistSignature by remember { mutableStateOf<String?>(null) }
    var lastSeekMs by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var lastSeekWindowIndex by remember { mutableIntStateOf(-1) }
    var prepareStartedAtMs by remember { mutableLongStateOf(0L) }
    var isPreparing by remember { mutableStateOf(false) }
    var showLoading by remember { mutableStateOf(false) }
    var playlistRebuildCount by remember { mutableIntStateOf(0) }
    val latestTrimPreviewFrameMs by rememberUpdatedState(trimPreviewFrameMs)
    val latestOnPlayerPosition by rememberUpdatedState(onPlayerPosition)
    val latestResumeAfterPlaylistPrepared by rememberUpdatedState(resumeAfterPlaylistPrepared)
    val latestOnPreparedPlaylistResume by rememberUpdatedState(onPreparedPlaylistResume)
    val latestGlobalTime by rememberUpdatedState(globalTime)
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val latestCurrentClipId by rememberUpdatedState(clip.id)
    var previewRecompositionCount by remember { mutableIntStateOf(0) }
    var transitionStartTimeMs by remember { mutableLongStateOf(0L) }
    var transitionStartPlayerPositionMs by remember { mutableLongStateOf(0L) }
    var transitionStartClipId by remember { mutableStateOf<String?>(null) }
    var transitionNextClipId by remember { mutableStateOf<String?>(null) }
    var droppedFrames by remember { mutableIntStateOf(0) }
    var decoderInitCount by remember { mutableIntStateOf(0) }
    var rendererResetCount by remember { mutableIntStateOf(0) }
    var frameSampleCount by remember { mutableIntStateOf(0) }
    var totalFrameTimeMs by remember { mutableLongStateOf(0L) }
    var maxFrameTimeMs by remember { mutableLongStateOf(0L) }
    var lastFrameCallbackNs by remember { mutableLongStateOf(0L) }
    val playbackRenderMode = PlaybackRenderMode.PREVIEW

    SideEffect {
        previewRecompositionCount++
        if (isPlaying) {
            Log.v(
                SCREEN_TAG,
                "recomposition target=VideoPreviewPlayer count=$previewRecompositionCount " +
                    "currentClipId=$latestCurrentClipId timelinePosition=$latestGlobalTime " +
                    "playerPosition=${player.currentPosition} playbackState=${playbackStateLabel(player.playbackState)} " +
                    "isLoading=${player.isLoading} bufferedPosition=${player.bufferedPosition}"
            )
        }
    }

    DisposableEffect(player) {
        onDispose {
            effectPreviewController?.release()
            onEffectPreviewController(null)
            Log.d(
                SCREEN_TAG,
                "playerRelease activePlayback=${player.isPlaying || latestIsPlaying} " +
                    "currentMediaItemIndex=${player.currentMediaItemIndex} currentMediaItem=${player.currentMediaItem?.mediaId}"
            )
            player.release()
        }
    }

    DisposableEffect(player) {
        val analyticsListener = object : AnalyticsListener {
            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFramesCount: Int,
                elapsedMs: Long
            ) {
                droppedFrames += droppedFramesCount
                Log.d(
                    SCREEN_TAG,
                    "previewPerf droppedFrames=$droppedFrames droppedFramesDelta=$droppedFramesCount " +
                        "elapsedMs=$elapsedMs decoderInitCount=$decoderInitCount rendererResetCount=$rendererResetCount " +
                        "mode=$playbackRenderMode"
                )
            }

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                decoderInitCount++
                Log.d(
                    SCREEN_TAG,
                    "previewPerf decoderInitialized decoderInitCount=$decoderInitCount decoder=$decoderName " +
                        "initializationDurationMs=$initializationDurationMs mode=$playbackRenderMode"
                )
            }

            override fun onVideoDisabled(
                eventTime: AnalyticsListener.EventTime,
                decoderCounters: androidx.media3.exoplayer.DecoderCounters
            ) {
                rendererResetCount++
                Log.d(
                    SCREEN_TAG,
                    "previewPerf rendererReset rendererResetCount=$rendererResetCount droppedFrames=$droppedFrames " +
                        "decoderInitCount=$decoderInitCount mode=$playbackRenderMode"
                )
            }
        }
        player.addAnalyticsListener(analyticsListener)
        onDispose { player.removeAnalyticsListener(analyticsListener) }
    }

    fun playbackItemForClip(targetClip: ClipUiModel): PreviewPlaybackItem? =
        playbackItems.firstOrNull { item -> item.clips.any { it.id == targetClip.id } }

    fun clipForPlaybackPosition(item: PreviewPlaybackItem?, playerPositionMs: Long): ClipUiModel? {
        if (item == null) return null
        val sourcePositionMs = if (item.isSameSourceGroup) {
            playerPositionMs
        } else {
            item.clips.firstOrNull()?.sourceStartMs?.plus(playerPositionMs) ?: playerPositionMs
        }
        return item.clips.firstOrNull { sourcePositionMs >= it.sourceStartMs && sourcePositionMs < it.sourceEndMs }
            ?: item.clips.lastOrNull()
    }

    fun nextClipInPlaybackItem(item: PreviewPlaybackItem?, currentClip: ClipUiModel?): ClipUiModel? {
        if (item == null || currentClip == null) return null
        val index = item.clips.indexOfFirst { it.id == currentClip.id }
        return item.clips.getOrNull(index + 1)
    }

    fun applyPreviewSpeedForClip(targetClip: ClipUiModel?, reason: String) {
        val targetSpeed = (targetClip?.playbackSpeed ?: 1f).coerceIn(0.1f, 8f)
        if (abs(player.playbackParameters.speed - targetSpeed) < 0.001f) return
        player.setPlaybackSpeed(targetSpeed)
        Log.d(
            SCREEN_TAG,
            "previewSpeedChanged clipId=${targetClip?.id} speed=$targetSpeed reason=$reason"
        )
    }

    DisposableEffect(player, playbackItems) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val currentItem = playbackItems.getOrNull(player.currentMediaItemIndex)
                val currentTimelineClip = clipForPlaybackPosition(currentItem, player.currentPosition)
                applyPreviewSpeedForClip(currentTimelineClip, "playbackStateChanged")
                val nextTimelineClip = nextClipInPlaybackItem(currentItem, currentTimelineClip)
                    ?: playbackItems.getOrNull(player.currentMediaItemIndex + 1)?.clips?.firstOrNull()
                Log.d(
                    SCREEN_TAG,
                    "playbackState state=${playbackStateLabel(playbackState)} itemIndex=${player.currentMediaItemIndex} " +
                        "currentClipId=${currentTimelineClip?.id ?: player.currentMediaItem?.mediaId} nextClipId=${nextTimelineClip?.id} " +
                        "itemMediaUri=${currentTimelineClip?.thumbnailUri} clippingStartMs=${currentTimelineClip?.sourceStartMs} clippingEndMs=${currentTimelineClip?.sourceEndMs} " +
                        "playerPosition=${player.currentPosition} timelinePosition=$latestGlobalTime " +
                        "isLoading=${player.isLoading} bufferedPosition=${player.bufferedPosition} playlistItemCount=${player.mediaItemCount}"
                )
                if (playbackState == Player.STATE_READY && isPreparing) {
                    val loadTimeMs = System.currentTimeMillis() - prepareStartedAtMs
                    isPreparing = false
                    showLoading = false
                    val nextClip = playbackItems.getOrNull(player.currentMediaItemIndex + 1)?.clips?.firstOrNull()
                    Log.d(SCREEN_TAG, "nextClipPrepared activeClipId=${nextClip?.id}")
                    Log.d(SCREEN_TAG, "mediaLoadTimeMs value=$loadTimeMs")
                    if (latestResumeAfterPlaylistPrepared) {
                        Log.d(
                            SCREEN_TAG,
                            "preparedPlaylistResume playbackState=READY playlistItemCount=${player.mediaItemCount} " +
                                "currentMediaItemIndex=${player.currentMediaItemIndex} currentMediaItem=${player.currentMediaItem?.mediaId}"
                        )
                        player.play()
                        latestOnPreparedPlaylistResume()
                    }
                }
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                val currentItem = playbackItems.getOrNull(player.currentMediaItemIndex)
                val currentTimelineClip = clipForPlaybackPosition(currentItem, player.currentPosition)
                val nextTimelineClip = nextClipInPlaybackItem(currentItem, currentTimelineClip)
                    ?: playbackItems.getOrNull(player.currentMediaItemIndex + 1)?.clips?.firstOrNull()
                Log.d(
                    SCREEN_TAG,
                    "isLoading value=$isLoading itemIndex=${player.currentMediaItemIndex} " +
                        "currentClipId=${currentTimelineClip?.id ?: player.currentMediaItem?.mediaId} nextClipId=${nextTimelineClip?.id} " +
                        "itemMediaUri=${currentTimelineClip?.thumbnailUri} clippingStartMs=${currentTimelineClip?.sourceStartMs} clippingEndMs=${currentTimelineClip?.sourceEndMs} " +
                        "playerPosition=${player.currentPosition} timelinePosition=$latestGlobalTime " +
                        "playbackState=${playbackStateLabel(player.playbackState)} bufferedPosition=${player.bufferedPosition} " +
                        "playlistItemCount=${player.mediaItemCount}"
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                transitionStartTimeMs = System.currentTimeMillis()
                transitionStartPlayerPositionMs = player.currentPosition
                transitionStartClipId = mediaItem?.mediaId
                transitionNextClipId = playbackItems.getOrNull(player.currentMediaItemIndex + 1)?.clips?.firstOrNull()?.id
                val switchedClipId = mediaItem?.mediaId
                val currentItem = playbackItems.getOrNull(player.currentMediaItemIndex)
                val currentTimelineClip = clipForPlaybackPosition(currentItem, player.currentPosition)
                applyPreviewSpeedForClip(currentTimelineClip, "mediaItemTransition")
                val beforeTransitionMs = lastSeekMs.takeIf { it != Long.MIN_VALUE } ?: player.currentPosition
                Log.d(
                    SCREEN_TAG,
                    "onMediaItemTransition reason=$reason itemIndex=${player.currentMediaItemIndex} currentClipId=$switchedClipId " +
                        "nextClipId=$transitionNextClipId itemMediaUri=${currentTimelineClip?.thumbnailUri} " +
                        "clippingStartMs=${currentTimelineClip?.sourceStartMs} clippingEndMs=${currentTimelineClip?.sourceEndMs} " +
                        "playerPosition=${player.currentPosition} timelinePosition=$latestGlobalTime " +
                        "playbackState=${playbackStateLabel(player.playbackState)} isLoading=${player.isLoading} " +
                        "bufferedPosition=${player.bufferedPosition} transitionStartTimeMs=$transitionStartTimeMs " +
                        "playlistItemCount=${player.mediaItemCount} playerPositionBeforeTransition=$beforeTransitionMs " +
                        "playerPositionAfterTransition=${player.currentPosition}"
                )
                if (player.playbackState == Player.STATE_READY) {
                    Log.d(SCREEN_TAG, "clipSwitchInstant activeClipId=$switchedClipId")
                } else {
                    val delayMs = System.currentTimeMillis() - prepareStartedAtMs
                    Log.d(SCREEN_TAG, "clipSwitchDelayed activeClipId=$switchedClipId delayMs=$delayMs")
                }
                val nextClip = playbackItems.getOrNull(player.currentMediaItemIndex + 1)?.clips?.firstOrNull()
                if (nextClip != null) {
                    Log.d(SCREEN_TAG, "nextClipPreloadStarted activeClipId=${nextClip.id}")
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                transitionStartTimeMs = System.currentTimeMillis()
                transitionStartPlayerPositionMs = oldPosition.positionMs
                transitionStartClipId = oldPosition.mediaItem?.mediaId
                transitionNextClipId = newPosition.mediaItem?.mediaId
                Log.d(
                    SCREEN_TAG,
                    "discontinuityReason=$reason oldItemIndex=${oldPosition.mediaItemIndex} " +
                        "newItemIndex=${newPosition.mediaItemIndex} currentClipId=$transitionStartClipId nextClipId=$transitionNextClipId " +
                        "playerPosition=${player.currentPosition} timelinePosition=$latestGlobalTime " +
                        "playbackState=${playbackStateLabel(player.playbackState)} isLoading=${player.isLoading} " +
                        "bufferedPosition=${player.bufferedPosition} transitionStartTimeMs=$transitionStartTimeMs " +
                        "playerPositionBeforeTransition=${oldPosition.positionMs} " +
                        "playerPositionAfterTransition=${newPosition.positionMs} playlistItemCount=${player.mediaItemCount}"
                )
            }

            override fun onRenderedFirstFrame() {
                val firstFrameRenderedTimeMs = System.currentTimeMillis()
                val transitionDurationMs = if (transitionStartTimeMs > 0L) {
                    firstFrameRenderedTimeMs - transitionStartTimeMs
                } else {
                    0L
                }
                val currentItem = playbackItems.getOrNull(player.currentMediaItemIndex)
                val currentTimelineClip = clipForPlaybackPosition(currentItem, player.currentPosition)
                Log.d(
                    SCREEN_TAG,
                    "onRenderedFirstFrame currentClipId=${currentTimelineClip?.id ?: player.currentMediaItem?.mediaId} nextClipId=${nextClipInPlaybackItem(currentItem, currentTimelineClip)?.id ?: playbackItems.getOrNull(player.currentMediaItemIndex + 1)?.clips?.firstOrNull()?.id} " +
                        "itemMediaUri=${currentTimelineClip?.thumbnailUri} clippingStartMs=${currentTimelineClip?.sourceStartMs} clippingEndMs=${currentTimelineClip?.sourceEndMs} " +
                        "playerPosition=${player.currentPosition} timelinePosition=$latestGlobalTime " +
                        "playbackState=${playbackStateLabel(player.playbackState)} isLoading=${player.isLoading} " +
                        "bufferedPosition=${player.bufferedPosition} transitionStartTimeMs=$transitionStartTimeMs " +
                        "firstFrameRenderedTimeMs=$firstFrameRenderedTimeMs transitionDurationMs=$transitionDurationMs " +
                        "transitionStartClipId=$transitionStartClipId transitionNextClipId=$transitionNextClipId " +
                        "transitionStartPlayerPosition=$transitionStartPlayerPositionMs"
                )
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(isPreparing) {
        showLoading = false
    }

    fun activeWindowIndex(): Int = playbackItems.indexOfFirst { item -> item.clips.any { it.id == clip.id } }

    fun activeWindowOffsetMs(): Long {
        val segmentStart = segment?.startMs ?: clip.timelineStartMs
        val timelineOffset = (globalTime - segmentStart).coerceIn(0L, clip.durationMs)
        val sourceOffset = (timelineOffset * clip.playbackSpeed).roundToLong()
        val item = playbackItemForClip(clip)
        return if (item?.isSameSourceGroup == true) {
            (clip.sourceStartMs + sourceOffset).coerceIn(item.clippingStartMs, item.clippingEndMs - 1L)
        } else {
            sourceOffset.coerceIn(0L, (clip.sourceEndMs - clip.sourceStartMs - 1L).coerceAtLeast(0L))
        }
    }

    fun requestSeek(windowIndex: Int, targetMs: Long, reason: String) {
        if (windowIndex < 0) return
        val currentMs = player.currentPosition
        if (player.currentMediaItemIndex == windowIndex && abs(currentMs - targetMs) < PREVIEW_SEEK_SKIP_MS) {
            Log.v(SCREEN_TAG, "seekSkipped activeClipId=${clip.id} targetMs=$targetMs currentMs=$currentMs reason=$reason")
            return
        }
        if (lastSeekWindowIndex == windowIndex && lastSeekMs != Long.MIN_VALUE && abs(lastSeekMs - targetMs) < PREVIEW_SEEK_SKIP_MS) {
            Log.v(SCREEN_TAG, "seekSkipped activeClipId=${clip.id} targetMs=$targetMs lastSeekMs=$lastSeekMs windowIndex=$windowIndex reason=$reason")
            return
        }
        lastSeekMs = targetMs
        lastSeekWindowIndex = windowIndex
        Log.d(
            SCREEN_TAG,
            "seekRequested activeClipId=${clip.id} windowIndex=$windowIndex targetMs=$targetMs reason=$reason " +
                "duringActivePlayback=${player.isPlaying || isPlaying} currentMediaItemIndex=${player.currentMediaItemIndex} " +
                "currentMediaItem=${player.currentMediaItem?.mediaId}"
        )
        Log.d(
            SCREEN_TAG,
            "PREVIEW_PLAYHEAD_SYNC reason=$reason activeClipId=${clip.id} windowIndex=$windowIndex " +
                "targetMs=$targetMs currentTimelineMs=$globalTime"
        )
        player.seekTo(windowIndex, targetMs)
    }

    LaunchedEffect(playlistSignature) {
        if (preparedPlaylistSignature == playlistSignature) return@LaunchedEffect
        if (playbackItems.isEmpty()) {
            player.pause()
            player.clearMediaItems()
            preparedPlaylistSignature = playlistSignature
            return@LaunchedEffect
        }
        val startMs = System.currentTimeMillis()
        val playlistContainsNewClip = lastAddedClipId?.let { addedId ->
            playbackItems.any { item -> item.clips.any { it.id == addedId } }
        } ?: false
        val mediaItems = playbackItems.mapIndexed { index, playbackItem ->
            val firstClip = playbackItem.clips.first()
            Log.d(
                SCREEN_TAG,
                "playlistItemBuild playlistItemIndex=$index clipId=${playbackItem.id} " +
                    "itemMediaUri=${playbackItem.uri} clippingStartMs=${playbackItem.clippingStartMs} " +
                    "clippingEndMs=${playbackItem.clippingEndMs} sameSourceGroup=${playbackItem.isSameSourceGroup} " +
                    "visibleClips=${playbackItem.clips.map { "${it.id}@${it.playbackSpeed}x" }}"
            )
            MediaItem.Builder()
                .setMediaId(playbackItem.id)
                .setUri(playbackItem.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(playbackItem.clippingStartMs)
                        .setEndPositionMs(playbackItem.clippingEndMs.coerceAtLeast(playbackItem.clippingStartMs + 1L))
                        .build()
                )
                .build()
        }
        val startIndex = activeWindowIndex().coerceAtLeast(0)
        val startOffsetMs = activeWindowOffsetMs()
        if (!isPlaying && (player.isPlaying || player.playWhenReady)) {
            Log.d(
                SCREEN_TAG,
                "pauseCalledForTimelineEdit currentMediaItemIndex=${player.currentMediaItemIndex} " +
                    "currentMediaItem=${player.currentMediaItem?.mediaId} timelineChangeReason=playlistRebuildBeforePlayback"
            )
            player.pause()
        }
        val wasActivelyPlaying = player.isPlaying || player.playWhenReady || isPlaying
        Log.d(
            SCREEN_TAG,
            "mediaItemChanged playlistItems=${mediaItems.size} activeClipId=${clip.id} " +
                "startIndex=$startIndex startOffsetMs=$startOffsetMs wasActivelyPlaying=$wasActivelyPlaying " +
                "timelineChangeReason=playlistSignatureChanged currentMediaItemIndex=${player.currentMediaItemIndex} " +
                "currentMediaItem=${player.currentMediaItem?.mediaId}"
        )
        playbackItems.getOrNull(startIndex + 1)?.let {
            Log.d(SCREEN_TAG, "nextClipPreloadStarted activeClipId=${it.clips.firstOrNull()?.id}")
        }
        if (!wasActivelyPlaying) {
            Log.d(
                SCREEN_TAG,
                "clearMediaItems called duringActivePlayback=false timelineChangeReason=playlistRebuildIdle"
            )
            player.clearMediaItems()
        }
        Log.d(
            SCREEN_TAG,
            "setMediaItems called duringActivePlayback=$wasActivelyPlaying timelineChangeReason=playlistSignatureChanged " +
                "playlistSize=${mediaItems.size} startIndex=$startIndex currentMediaItem=${player.currentMediaItem?.mediaId}"
        )
        Log.d(
            SCREEN_TAG,
            "PREVIEW_SOURCE_SET activeClipId=${clip.id} firstClipId=${playbackItems.firstOrNull()?.clips?.firstOrNull()?.id} " +
                "playlistSize=${mediaItems.size} startIndex=$startIndex startOffsetMs=$startOffsetMs " +
                "sourceUri=${playbackItems.getOrNull(startIndex)?.uri}"
        )
        player.setMediaItems(mediaItems, startIndex, startOffsetMs)
        Log.d(
            SCREEN_TAG,
            "PREVIEW_PLAYHEAD_SYNC reason=sourceSet activeClipId=${clip.id} windowIndex=$startIndex " +
                "targetMs=$startOffsetMs currentTimelineMs=$globalTime"
        )
        prepareStartedAtMs = startMs
        isPreparing = true
        Log.d(
            SCREEN_TAG,
            "prepare called duringActivePlayback=$wasActivelyPlaying timelineChangeReason=playlistSignatureChanged " +
                "playlistSize=${mediaItems.size} currentMediaItemIndex=${player.currentMediaItemIndex}"
        )
        player.prepare()
        Log.d(SCREEN_TAG, "playlistItemCount count=${player.mediaItemCount}")
        playlistRebuildCount++
        Log.d(
            SCREEN_TAG,
            "ADD_MEDIA_FROM_TIMELINE_PLUS playlistRebuiltCount=$playlistRebuildCount " +
                "playlistContainsNewClip=$playlistContainsNewClip lastAddedClipId=$lastAddedClipId " +
                "playlistSize=${mediaItems.size} activeClipId=${clip.id}"
        )
        if (wasActivelyPlaying) {
            player.play()
        } else {
            player.pause()
        }
        preparedPlaylistSignature = playlistSignature
        lastSeekMs = Long.MIN_VALUE
        lastSeekWindowIndex = -1
    }

    val localPositionMs by remember(
        clip.id,
        segment?.startMs,
        globalTime,
        clip.durationMs,
        clip.sourceStartMs,
        clip.sourceEndMs,
        clip.playbackSpeed
    ) {
        derivedStateOf {
            val segmentStart = segment?.startMs ?: clip.timelineStartMs
            val timelineOffset = (globalTime - segmentStart).coerceIn(0L, clip.durationMs)
            val maxPlayablePosition = (clip.sourceEndMs - 1L).coerceAtLeast(clip.sourceStartMs)
            (clip.sourceStartMs + (timelineOffset * clip.playbackSpeed).roundToLong())
                .coerceIn(clip.sourceStartMs, maxPlayablePosition)
        }
    }

    LaunchedEffect(volume) {
        player.volume = volume
    }

    LaunchedEffect(clip.id, clip.playbackSpeed, preparedPlaylistSignature) {
        if (preparedPlaylistSignature == playlistSignature) {
            applyPreviewSpeedForClip(clip, "activeClipChanged")
        }
    }

    LaunchedEffect(isPlaying, preparedPlaylistSignature) {
        if (preparedPlaylistSignature != playlistSignature) return@LaunchedEffect
        if (isPlaying) {
            val targetIndex = activeWindowIndex()
            val targetOffsetMs = activeWindowOffsetMs()
            val alreadyAtTarget = player.currentMediaItemIndex == targetIndex &&
                abs(player.currentPosition - targetOffsetMs) < PREVIEW_SEEK_SKIP_MS
            if (!player.isPlaying && !alreadyAtTarget) {
                requestSeek(targetIndex, targetOffsetMs, "playbackStart")
            }
            player.play()
        } else {
            player.pause()
        }
    }

    LaunchedEffect(animationPreviewRestartKey, preparedPlaylistSignature) {
        if (animationPreviewRestartKey == 0L || preparedPlaylistSignature != playlistSignature) return@LaunchedEffect
        lastSeekMs = Long.MIN_VALUE
        lastSeekWindowIndex = -1
        requestSeek(activeWindowIndex(), activeWindowOffsetMs(), "animationPresetRestart")
        player.play()
    }

    DisposableEffect(isPlaying, preparedPlaylistSignature, visibleTimelineSignature) {
        if (!isPlaying || preparedPlaylistSignature != playlistSignature) {
            return@DisposableEffect onDispose { }
        }
        val choreographer = Choreographer.getInstance()
        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!latestIsPlaying || preparedPlaylistSignature != playlistSignature) return
                if (lastFrameCallbackNs != 0L) {
                    val frameTimeMs = ((frameTimeNanos - lastFrameCallbackNs) / 1_000_000L).coerceAtLeast(0L)
                    frameSampleCount++
                    totalFrameTimeMs += frameTimeMs
                    maxFrameTimeMs = maxOf(maxFrameTimeMs, frameTimeMs)
                }
                lastFrameCallbackNs = frameTimeNanos
            val itemIndex = player.currentMediaItemIndex
            val currentItem = playbackItems.getOrNull(itemIndex)
            val playerPositionMs = player.currentPosition.coerceAtLeast(0L)
            val currentClip = clipForPlaybackPosition(currentItem, playerPositionMs)
            val nextClip = nextClipInPlaybackItem(currentItem, currentClip)
            applyPreviewSpeedForClip(currentClip, "frameSync")
            val sourcePositionMs = if (currentItem?.isSameSourceGroup == true) {
                playerPositionMs
            } else {
                (currentClip?.sourceStartMs ?: 0L) + playerPositionMs
            }
            val sameSourceTransition = currentItem?.isSameSourceGroup == true && currentClip != null && nextClip != null
            val remainingMs = currentClip?.let { (it.sourceEndMs - sourcePositionMs).coerceAtLeast(0L) } ?: 0L
            if (sameSourceTransition && remainingMs <= 500L) {
                Log.v(
                    SCREEN_TAG,
                    "sameSourcePreload currentClipId=${currentClip.id} nextClipId=${nextClip.id} " +
                        "remainingMs=$remainingMs currentSourcePositionMs=$sourcePositionMs nextSourceStartMs=${nextClip.sourceStartMs} " +
                        "decoderWarm=true rendererReset=false"
                )
            }
            if (sameSourceTransition && remainingMs <= PREVIEW_PLAYER_SYNC_MS) {
                val boundaryStartMs = System.currentTimeMillis()
                player.seekTo(itemIndex, nextClip.sourceStartMs)
                if (!player.isPlaying && isPlaying) player.play()
                val boundaryDurationMs = System.currentTimeMillis() - boundaryStartMs
                val averageFrameTime = if (frameSampleCount > 0) totalFrameTimeMs / frameSampleCount else 0L
                Log.d(
                    SCREEN_TAG,
                    "sameSourceBoundaryOptimized currentClipId=${currentClip.id} nextClipId=${nextClip.id} " +
                        "boundaryDurationMs=$boundaryDurationMs decoderReinitialization=false rendererReset=false droppedFrames=0 " +
                        "totalDroppedFrames=$droppedFrames decoderInitCount=$decoderInitCount rendererResetCount=$rendererResetCount " +
                        "averageFrameTime=$averageFrameTime maxFrameTime=$maxFrameTimeMs boundaryFrameGapMs=$boundaryDurationMs " +
                        "setMediaItemsCalled=false prepareCalled=false stopCalled=false seekCalled=true loadingUiShown=false"
                )
                latestOnPlayerPosition(itemIndex, nextClip.id, 0L)
                choreographer.postFrameCallback(this)
                return
            }
            val currentClipId = currentClip?.id ?: player.currentMediaItem?.mediaId
            val localMs = currentClip?.let {
                ((sourcePositionMs - it.sourceStartMs).coerceAtLeast(0L) / it.playbackSpeed)
                    .roundToLong()
                    .coerceIn(0L, it.durationMs)
            } ?: playerPositionMs
            val clipEndMs = currentClip?.durationMs ?: 0L
            val averageFrameTime = if (frameSampleCount > 0) totalFrameTimeMs / frameSampleCount else 0L
            Log.v(
                SCREEN_TAG,
                "playbackVerify itemIndex=$itemIndex clipId=$currentClipId localPositionMs=$localMs " +
                    "clipEndMs=$clipEndMs remainingMsBeforeSwitch=$remainingMs noManualSeekAtBoundary=true " +
                    "droppedFrames=$droppedFrames decoderInitCount=$decoderInitCount rendererResetCount=$rendererResetCount " +
                    "averageFrameTime=$averageFrameTime maxFrameTime=$maxFrameTimeMs " +
                    "setMediaItemsCalled=false prepareCalled=false stopCalled=false seekCalled=false " +
                    "roomReloadCalled=false thumbnailGenerationCalled=false"
            )
            latestOnPlayerPosition(itemIndex, currentClipId, localMs)
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(frameCallback)
        onDispose { choreographer.removeFrameCallback(frameCallback) }
    }

    LaunchedEffect(isTrimDragging, clip.id, preparedPlaylistSignature) {
        if (preparedPlaylistSignature != playlistSignature) return@LaunchedEffect
        if (isTrimDragging) {
            player.pause()
            while (true) {
                latestTrimPreviewFrameMs?.let {
                    val targetClipItem = playbackItemForClip(clip)
                    requestSeek(
                        windowIndex = activeWindowIndex(),
                        targetMs = if (targetClipItem?.isSameSourceGroup == true) {
                            it.coerceIn(targetClipItem.clippingStartMs, targetClipItem.clippingEndMs - 1L)
                        } else {
                            (it - clip.sourceStartMs).coerceIn(0L, (clip.sourceEndMs - clip.sourceStartMs - 1L).coerceAtLeast(0L))
                        },
                        reason = "trimPreview"
                    )
                }
                delay(PREVIEW_TRIM_SEEK_THROTTLE_MS)
            }
        } else {
            trimPreviewFrameMs?.let {
                val targetClipItem = playbackItemForClip(clip)
                requestSeek(
                    windowIndex = activeWindowIndex(),
                    targetMs = if (targetClipItem?.isSameSourceGroup == true) {
                        it.coerceIn(targetClipItem.clippingStartMs, targetClipItem.clippingEndMs - 1L)
                    } else {
                        (it - clip.sourceStartMs).coerceIn(0L, (clip.sourceEndMs - clip.sourceStartMs - 1L).coerceAtLeast(0L))
                    },
                    reason = "trimFinal"
                )
            }
        }
    }

    LaunchedEffect(localPositionMs, isPlaying, isTrimDragging, preparedPlaylistSignature) {
        if (!isPlaying && !isTrimDragging && preparedPlaylistSignature == playlistSignature) {
            if (PREVIEW_SCRUB_DEBOUNCE_MS > 0L) delay(PREVIEW_SCRUB_DEBOUNCE_MS)
            if (!player.isPlaying) {
                Log.d(
                    SCREEN_TAG,
                    "PREVIEW_SCRUB_FRAME_SYNC activeClipId=${clip.id} localPositionMs=$localPositionMs currentTimelineMs=$globalTime"
                )
                requestSeek(activeWindowIndex(), activeWindowOffsetMs(), "pausedScrub")
            }
        }
    }

    val androidViewAlpha = transitionAlpha.coerceIn(0f, 1f)

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = androidViewAlpha
                },
            factory = { ctx ->
                Log.d(
                    SCREEN_TAG,
                    "playerViewCreated activePlayback=${player.isPlaying || latestIsPlaying} " +
                        "currentMediaItemIndex=${player.currentMediaItemIndex} currentMediaItem=${player.currentMediaItem?.mediaId}"
                )
                (LayoutInflater.from(ctx).inflate(
                    R.layout.clipforge_preview_player_view,
                    null,
                    false
                ) as PlayerView).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    alpha = androidViewAlpha
                    this.player = player
                    Log.d(
                        SCREEN_TAG,
                        "previewSurfaceCreated surface=${getVideoSurfaceView()?.javaClass?.simpleName} transitionOverlayCompositable=true"
                    )
                }
            },
            update = { view ->
                view.setKeepContentOnPlayerReset(true)
                view.setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                view.setBackgroundColor(AndroidColor.TRANSPARENT)
                view.alpha = androidViewAlpha
                if (view.player !== player) view.player = player
            }
        )
        if (showLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(28.dp)
            )
        }
    }
}

@Composable
private fun CapCutTransportBar(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color(0xFF151515))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("[ ]", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            if (isPlaying) "Pause" else "Play",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onTogglePlay)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Snap\nON", color = Color.White, fontSize = 10.sp, lineHeight = 10.sp, textAlign = TextAlign.Center)
            Text("Undo", color = AppColors.TextSecondary, fontSize = 11.sp)
            Text("Redo", color = AppColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CapCutTimelineEditor(
    clips: List<ClipUiModel>,
    currentSegment: TimelineSegment?,
    globalTime: Long,
    totalMs: Long,
    isPlaying: Boolean,
    selectedClipId: String?,
    effects: List<EffectItem>,
    textOverlays: List<TextOverlay>,
    animationMarkers: Map<String, ClipAnimationMarkerState>,
    selectionTarget: SelectionTarget,
    interactionMode: EditorInteractionMode,
    splitAdjustClipAId: String?,
    splitAdjustClipBId: String?,
    previewSplitBoundaryMs: Long?,
    trimGestureActive: Boolean,
    trimSessionSide: TrimSide?,
    trimSessionStartMs: Long?,
    preserveTimelineScrollVersion: Long,
    scrollState: androidx.compose.foundation.ScrollState,
    frameCache: MutableMap<String, List<Bitmap?>>,
    onSeekTo: (Long) -> Unit,
    onBeginDrag: () -> Unit,
    onScrubTo: (Long) -> Unit,
    onEndDrag: (Long) -> Unit,
    onSuspendEffects: () -> Unit,
    onResumeEffects: () -> Unit,
    onSelectClip: (String) -> Unit,
    onSelectEffect: (String) -> Unit,
    onAddMusic: () -> Unit,
    onAddText: () -> Unit,
    audioTrackCount: Int,
    textTrackCount: Int,
    overlayTrackCount: Int,
    onLongPressClip: (String) -> Unit,
    onMoveClip: (String, Int) -> Unit,
    onReorderClip: (String, Int) -> Unit,
    onResumePlayback: () -> Unit,
    onTrimStarted: (String, TrimSide) -> Unit,
    onTrimPreviewUpdated: (String, TrimSide, Long) -> Unit,
    onTrimCommitted: (String, TrimSide) -> Unit,
    onTrimStartDragged: (String, Long) -> Unit,
    onTrimEndDragged: (String, Long) -> Unit,
    onTrimFinished: (String) -> Unit,
    onSplitBoundarySelected: (String, String) -> Unit,
    onSplitBoundaryTrimStarted: () -> Unit,
    onSplitBoundaryDragged: (Long) -> Unit,
    onSplitBoundaryFinished: () -> Unit,
    onSplitAdjustDone: () -> Unit,
    onOpenTransition: (String) -> Unit,
    onAddMedia: () -> Unit,
    zoom: Float,
    onZoomChange: (Float) -> Unit
) {
    val density = LocalDensity.current
    val pxPerMs by remember(density, zoom) {
        derivedStateOf { with(density) { (28.dp.toPx() * zoom.coerceIn(MIN_TIMELINE_ZOOM, MAX_TIMELINE_ZOOM)) } / 1000f }
    }
    val haptics = LocalHapticFeedback.current
    val zoomState = rememberTransformableState { zoomChange, _, _ ->
        onZoomChange((zoom * zoomChange).coerceIn(MIN_TIMELINE_ZOOM, MAX_TIMELINE_ZOOM))
    }
    var programmaticScroll by remember { mutableStateOf(false) }
    var userDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val displayClips = remember { mutableStateListOf<ClipUiModel>() }
    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var draggingOffsetPx by remember { mutableStateOf(0f) }
    var dragStartedWhilePlaying by remember { mutableStateOf(false) }
    var insertionIndex by remember { mutableIntStateOf(-1) }
    var suppressTimelineDragEnd by remember { mutableStateOf(false) }
    var lastPreserveTimelineScrollVersion by remember { mutableLongStateOf(preserveTimelineScrollVersion) }
    var timelineRecompositionCount by remember { mutableIntStateOf(0) }
    val isTrimMode = interactionMode == EditorInteractionMode.TRIM
    val isActivelyTrimming = isTrimMode && trimGestureActive
    val timeFromScroll by remember(scrollState.value, pxPerMs, totalMs) {
        derivedStateOf { (scrollState.value / pxPerMs).roundToInt().toLong().coerceIn(0L, totalMs) }
    }

    LaunchedEffect(Unit) {
        Log.d(
            SCREEN_TAG,
            "TIMELINE_UI_AUDIT trackControlsW=${TRACK_CONTROLS_W.value} clipRowH=44 addButton=24 trimHandleVisual=2 " +
                "selectedOutline=1 transitionMarker=8 continuousStrip=true boxedAppearance=false"
        )
        Log.d(SCREEN_TAG, "TIMELINE_DENSITY_CHECK clipHeight=44 rowHeight=44 tileWidth=20 rulerHeight=28")
        Log.d(SCREEN_TAG, "TIMELINE_SELECTION_CHECK outlineDp=1 handleOverlay=true layoutShift=false alpha=0.36")
        Log.d(SCREEN_TAG, "TIMELINE_CONTROL_SIZE_CHECK plusButtonDp=24 sideShortcutDp=18 trimHandleDp=2 boundaryHandleDp=1")
    }

    SideEffect {
        timelineRecompositionCount++
        if (isPlaying) {
            Log.v(
                SCREEN_TAG,
                "recomposition target=CapCutTimelineEditor count=$timelineRecompositionCount " +
                    "currentClipId=${currentSegment?.clipId} timelinePosition=$globalTime " +
                    "scrollOffset=${scrollState.value} clips=${clips.size}"
            )
        }
    }

    LaunchedEffect(clips, draggingClipId) {
        if (draggingClipId == null) {
            displayClips.clear()
            displayClips.addAll(clips)
        }
    }
    val timelineClips: List<ClipUiModel> = if (draggingClipId == null) clips else displayClips

    fun clipWidthPx(clip: ClipUiModel): Float = (clip.durationMs * pxPerMs).coerceAtLeast(18f)

    fun updateInsertionForDrag(clipId: String) {
        val currentIndex = displayClips.indexOfFirst { it.id == clipId }
        if (currentIndex < 0) return
        val widths = displayClips.map(::clipWidthPx)
        val clipStart = widths.take(currentIndex).sum()
        val draggedCenter = clipStart + widths[currentIndex] / 2f + draggingOffsetPx
        var target = 0
        var cursor = 0f
        displayClips.forEachIndexed { index, item ->
            if (item.id != clipId) {
                val center = cursor + widths[index] / 2f
                if (draggedCenter > center) target++
            }
            cursor += widths[index]
        }
        target = target.coerceIn(0, displayClips.size - 1)
        insertionIndex = target
        if (target != currentIndex) {
            val moving = displayClips.removeAt(currentIndex)
            displayClips.add(target, moving)
            draggingOffsetPx = 0f
        }
    }

    fun autoScrollDuringReorder(deltaPx: Float) {
        if (deltaPx == 0f) return
        val edgeSpeed = when {
            deltaPx > 0f -> 24
            else -> -24
        }
        scope.launch {
            scrollState.scrollTo((scrollState.value + edgeSpeed).coerceIn(0, scrollState.maxValue))
        }
    }

    LaunchedEffect(globalTime, isPlaying, pxPerMs) {
        if (isPlaying && !userDragging && !isActivelyTrimming) {
            val target = (globalTime * pxPerMs).roundToInt().coerceAtLeast(0)
            if (kotlin.math.abs(scrollState.value - target) > 1) {
                programmaticScroll = true
                scrollState.scrollTo(target)
                Log.v(SCREEN_TAG, "AUTO_SCROLL_TO_TIMELINE_MS currentTimelineMs=$globalTime offset=$target")
                Log.v(SCREEN_TAG, "timeline scroll offset=$target currentTimelineMs=$globalTime activeClipId=${currentSegment?.clipId} isPlaying=$isPlaying")
                programmaticScroll = false
            }
        } else if (isPlaying) {
            Log.v(
                SCREEN_TAG,
                "PLAYBACK_SYNC_SUPPRESSED_REASON userDragging=$userDragging isActivelyTrimming=$isActivelyTrimming " +
                    "interactionMode=$interactionMode"
            )
        }
    }

    LaunchedEffect(scrollState, pxPerMs, totalMs) {
        snapshotFlow { scrollState.value }
            .collect { offset ->
                if (userDragging && !programmaticScroll) {
                    val t = (offset / pxPerMs).roundToInt().toLong().coerceIn(0L, totalMs)
                    onScrubTo(t)
                    Log.v(SCREEN_TAG, "timeline scroll offset=$offset currentTimelineMs=$t activeClipId=${currentSegment?.clipId} isPlaying=false")
                }
            }
    }

    LaunchedEffect(clips, preserveTimelineScrollVersion) {
        if (preserveTimelineScrollVersion != lastPreserveTimelineScrollVersion) {
            lastPreserveTimelineScrollVersion = preserveTimelineScrollVersion
            Log.d(
                SCREEN_TAG,
                "TIMELINE_SYNC autoScroll=false preserveTimelineScrollVersion=$preserveTimelineScrollVersion " +
                    "currentTimelineMs=$globalTime scrollOffset=${scrollState.value}"
            )
            return@LaunchedEffect
        }
        if (!isActivelyTrimming && scrollState.value == 0 && globalTime > 0) {
            scrollState.scrollTo((globalTime * pxPerMs).roundToInt().coerceAtLeast(0))
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(256.dp)
            .background(Color(0xFF1D1D1D))
    ) {
        val playheadTrackLead = remember(maxWidth) {
            (maxWidth / 2f - TRACK_CONTROLS_W).coerceAtLeast(0.dp)
        }
        val playheadTrackTrail = remember(maxWidth) {
            (maxWidth / 2f).coerceAtLeast(playheadTrackLead)
        }
        Column(Modifier.fillMaxSize()) {
            CapCutTimeRuler(globalTime = globalTime, totalMs = totalMs)
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                CapCutTrackControls(
                    firstClip = clips.firstOrNull(),
                    onAddMusic = onAddMusic,
                    onAddText = onAddText
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp)
                ) {
                    // SPLIT_ADJUST pill removed — split is instant like CapCut.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                                .height(44.dp)
                            .padding(end = 8.dp)
                            .transformable(zoomState)
                            .background(Color(0xFF242424))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(pxPerMs, totalMs, isTrimMode) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        val scrollAtDown = scrollState.value
                                        if (isActivelyTrimming) {
                                            do {
                                                val event = awaitPointerEvent()
                                            } while (event.changes.any { it.pressed })
                                            return@awaitEachGesture
                                        }
                                         if (!programmaticScroll) {
                                             userDragging = true
                                             onSuspendEffects()
                                             onBeginDrag()
                                         }
                                        do {
                                            val event = awaitPointerEvent()
                                        } while (event.changes.any { it.pressed })
                                         if (userDragging) {
                                             userDragging = false
                                             onResumeEffects()
                                             if (suppressTimelineDragEnd || scrollState.value == scrollAtDown) {
                                                 suppressTimelineDragEnd = false
                                             } else {
                                                onEndDrag(timeFromScroll)
                                                Log.d(
                                                    SCREEN_TAG,
                                                    "timeline drag end offset=${scrollState.value} currentTimelineMs=$timeFromScroll activeClipId=${currentSegment?.clipId} isPlaying=$isPlaying"
                                                )
                                            }
                                        }
                                    }
                                }
                                .horizontalScroll(scrollState),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.width(playheadTrackLead))
                            if (timelineClips.isEmpty()) {
                                EmptyCapCutClipSlot()
                            } else {
                                timelineClips.forEachIndexed { index, clip ->
                                    key(clip.id) {
                                        val nextClip = timelineClips.getOrNull(index + 1)
                                        val hasTransition = clip.transition != null && clip.transition.type != TransitionType.NONE
                                        val isSplitBoundary = nextClip != null &&
                                            clip.mediaAssetId == nextClip.mediaAssetId &&
                                            clip.sourceEndMs <= nextClip.sourceEndMs &&
                                            nextClip.sourceStartMs >= clip.sourceStartMs
                                        val boundarySelected = interactionMode == EditorInteractionMode.BOUNDARY_TRIM &&
                                            splitAdjustClipAId == clip.id &&
                                            splitAdjustClipBId == nextClip?.id
                                        val startPx = (clip.timelineStartMs * pxPerMs).roundToInt()
                                        val endPx = (clip.timelineEndMs * pxPerMs).roundToInt()
                                        val widthPx = (endPx - startPx).coerceAtLeast(1)
                                        val pixelWidthDp = with(density) { widthPx.toDp() }
                                        if (nextClip != null) {
                                            val nextStartPx = (nextClip.timelineStartMs * pxPerMs).roundToInt()
                                            Log.v(
                                                SCREEN_TAG,
                                                "TIMELINE_PIXEL_GAP_CHECK leftClipId=${clip.id} rightClipId=${nextClip.id} " +
                                                    "leftEndPx=$endPx rightStartPx=$nextStartPx gapPx=${nextStartPx - endPx}"
                                            )
                                            Log.d(
                                                SCREEN_TAG,
                                                "TIMELINE_GAP_CHECK leftClipId=${clip.id} rightClipId=${nextClip.id} gapPx=${nextStartPx - endPx}"
                                            )
                                            if (nextStartPx != endPx) {
                                                Log.w(
                                                    SCREEN_TAG,
                                                    "TIMELINE_GAP_DETECTED leftClipId=${clip.id} rightClipId=${nextClip.id} " +
                                                        "leftEndPx=$endPx rightStartPx=$nextStartPx gapPx=${nextStartPx - endPx}"
                                                )
                                            }
                                        }
                                        if (draggingClipId != null && insertionIndex == index && draggingClipId != clip.id) {
                                            CapCutInsertionIndicator()
                                        }
                                        CapCutThumbnailClip(
                                            clip = clip,
                                            width = pixelWidthDp,
                                            selected = clip.id == selectedClipId,
                                            active = clip.id == currentSegment?.clipId,
                                            dragged = clip.id == draggingClipId,
                                            dragOffsetPx = if (clip.id == draggingClipId) draggingOffsetPx else 0f,
                                            splitAffected = interactionMode == EditorInteractionMode.BOUNDARY_TRIM &&
                                                (clip.id == splitAdjustClipAId || clip.id == splitAdjustClipBId),
                                            showTransitionMarker = nextClip != null && (hasTransition || clip.id == selectedClipId || isSplitBoundary),
                                            hasTransition = hasTransition,
                                            transitionIcon = clip.transition?.type?.let(::capCutTransitionIcon),
                                            animationMarkers = animationMarkers[clip.id],
                                            isSplitBoundaryMarker = isSplitBoundary,
                                            boundarySelected = boundarySelected,
                                            // AUTO-TRIM: show trim handles whenever a clip is selected, like CapCut.
                                            trimMode = interactionMode == EditorInteractionMode.TRIM && clip.id == selectedClipId,
                                            trimSessionSide = trimSessionSide,
                                            trimSessionStartMs = trimSessionStartMs,
                                            frameCache = frameCache,
                                            isPlaying = isPlaying || trimGestureActive,
                                            onClick = {
                                                onSelectClip(clip.id)
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onSeekTo(clip.timelineStartMs)
                                            },
                                            onReorderStart = {
                                                dragStartedWhilePlaying = isPlaying
                                                suppressTimelineDragEnd = true
                                                if (displayClips.isEmpty()) {
                                                    displayClips.addAll(clips)
                                                }
                                                draggingClipId = clip.id
                                                draggingOffsetPx = 0f
                                                insertionIndex = index
                                                onLongPressClip(clip.id)
                                                onBeginDrag()
                                                Log.d(SCREEN_TAG, "reorder drag start clip=${clip.id}")
                                            },
                                            onReorderDrag = { dragAmount ->
                                                draggingOffsetPx += dragAmount
                                                updateInsertionForDrag(clip.id)
                                                autoScrollDuringReorder(dragAmount)
                                            },
                                            onReorderEnd = {
                                                val target = displayClips.indexOfFirst { it.id == clip.id }
                                                draggingClipId = null
                                                draggingOffsetPx = 0f
                                                insertionIndex = -1
                                                if (target >= 0) onReorderClip(clip.id, target)
                                                if (dragStartedWhilePlaying) onResumePlayback()
                                                dragStartedWhilePlaying = false
                                                Log.d(SCREEN_TAG, "reorder drag end clip=${clip.id} target=$target")
                                            },
                                            pxPerMs = pxPerMs,
                                            zoom = zoom,
                                            onTrimStartDragged = onTrimStartDragged,
                                            onTrimEndDragged = onTrimEndDragged,
                                            onTrimFinished = onTrimFinished,
                                            onTrimStarted = onTrimStarted,
                                            onTrimPreviewUpdated = onTrimPreviewUpdated,
                                            onTrimCommitted = onTrimCommitted,
                                            onTransitionClick = if (nextClip != null) {
                                                {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    if (isSplitBoundary) {
                                                        onSplitBoundarySelected(clip.id, nextClip.id)
                                                    } else {
                                                        onSelectClip(clip.id)
                                                        onOpenTransition(clip.id)
                                                    }
                                                }
                                            } else {
                                                null
                                            },
                                            onBoundarySelected = if (nextClip != null && isSplitBoundary) {
                                                { onSplitBoundarySelected(clip.id, nextClip.id) }
                                            } else {
                                                null
                                            },
                                            onBoundaryTrimStarted = onSplitBoundaryTrimStarted,
                                            onBoundaryTrimDragged = onSplitBoundaryDragged,
                                            onBoundaryTrimFinished = onSplitBoundaryFinished
                                        )
                                        if (draggingClipId != null && insertionIndex == timelineClips.size && index == timelineClips.lastIndex) {
                                            CapCutInsertionIndicator()
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.width(playheadTrackTrail + STICKY_ADD_MEDIA_PADDING))
                        }
                        CapCutAddClipButton(
                            onClick = onAddMedia,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                                .zIndex(10f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    TimelineEffectLane(
                        effects = effects,
                        selectionTarget = selectionTarget,
                        pxPerMs = pxPerMs,
                        scrollState = scrollState,
                        playheadTrackLead = playheadTrackLead,
                        playheadTrackTrail = playheadTrackTrail + STICKY_ADD_MEDIA_PADDING,
                        onSelectEffect = { effectId ->
                            onSelectEffect(effectId)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                    if (audioTrackCount > 0) {
                        AddTrackLane("Audio track", onClick = onAddMusic)
                    }
                    if (textTrackCount > 0 || textOverlays.isNotEmpty()) {
                        TextOverlayLane(
                            overlays = textOverlays,
                            pxPerMs = pxPerMs,
                            scrollState = scrollState,
                            playheadTrackLead = playheadTrackLead,
                            playheadTrackTrail = playheadTrackTrail + STICKY_ADD_MEDIA_PADDING
                        )
                    }
                    if (overlayTrackCount > 0) {
                        AddTrackLane("Overlay track", onClick = null)
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
                .width(2.dp)
                .height(214.dp)
                .background(Color.White)
        )
    }
}

@Composable
private fun CapCutTimeRuler(globalTime: Long, totalMs: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "${TimeFormatter.formatMs(globalTime)} / ${TimeFormatter.formatMs(totalMs)}",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text("00:00        |        00:02        |        00:04        |", color = AppColors.TextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun CapCutTrackControls(
    firstClip: ClipUiModel?,
    onAddMusic: (() -> Unit)?,
    onAddText: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .width(TRACK_CONTROLS_W)
            .padding(start = 2.dp, top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("M", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text("Mute\nclip", color = Color.White, fontSize = 5.sp, lineHeight = 5.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 26.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF3A332F)),
            contentAlignment = Alignment.Center
        ) {
            if (firstClip?.thumbnailUri != null) {
                AsyncImage(
                    model = firstClip.thumbnailUri,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text("E", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Cover", color = Color.White, fontSize = 5.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp))
        }
        Spacer(Modifier.height(4.dp))
        SmallTrackShortcut("+", onClick = onAddMusic)
        Spacer(Modifier.height(4.dp))
        SmallTrackShortcut("T", onClick = onAddText)
    }
}

@Composable
private fun SmallTrackShortcut(label: String, onClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF2B2B2B))
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = AppColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CapCutInsertionIndicator() {
    Box(
        modifier = Modifier
            .width(10.dp)
            .height(54.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
        )
    }
}

@Composable
private fun SplitAdjustModePill(
    boundaryMs: Long?,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(end = 14.dp, bottom = 6.dp)
            .background(Color(0xFF2E2E34), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("Split adjust mode", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                "Drag to adjust split${boundaryMs?.let { " - ${TimeFormatter.formatMs(it)}" } ?: ""}",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "Done",
            color = Color.Black,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White)
                .clickable(onClick = onDone)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CapCutThumbnailClip(
    clip: ClipUiModel,
    width: Dp,
    selected: Boolean,
    active: Boolean,
    dragged: Boolean,
    dragOffsetPx: Float,
    splitAffected: Boolean,
    showTransitionMarker: Boolean,
    hasTransition: Boolean,
    transitionIcon: String?,
    animationMarkers: ClipAnimationMarkerState?,
    isSplitBoundaryMarker: Boolean,
    boundarySelected: Boolean,
    trimMode: Boolean,
    trimSessionSide: TrimSide?,
    trimSessionStartMs: Long?,
    frameCache: MutableMap<String, List<Bitmap?>>,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onReorderStart: () -> Unit,
    onReorderDrag: (Float) -> Unit,
    onReorderEnd: () -> Unit,
    pxPerMs: Float,
    zoom: Float,
    onTrimStartDragged: (String, Long) -> Unit,
    onTrimEndDragged: (String, Long) -> Unit,
    onTrimFinished: (String) -> Unit,
    onTrimStarted: (String, TrimSide) -> Unit,
    onTrimPreviewUpdated: (String, TrimSide, Long) -> Unit,
    onTrimCommitted: (String, TrimSide) -> Unit,
    onTransitionClick: (() -> Unit)?,
    onBoundarySelected: (() -> Unit)?,
    onBoundaryTrimStarted: () -> Unit,
    onBoundaryTrimDragged: (Long) -> Unit,
    onBoundaryTrimFinished: () -> Unit
) {
    val leftTrimEdgeOffsetPx = if (trimMode && trimSessionSide == TrimSide.LEFT && trimSessionStartMs != null) {
        (clip.sourceStartMs - trimSessionStartMs).coerceAtLeast(0L) * pxPerMs
    } else {
        0f
    }
    val dragModifier = if (trimMode) {
        Modifier
    } else {
        Modifier.pointerInput(clip.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    onReorderStart()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onReorderDrag(dragAmount.x)
                },
                onDragEnd = {
                    onReorderEnd()
                },
                onDragCancel = { onReorderEnd() }
            )
        }
    }
    Box(
        modifier = Modifier
            .size(width = width, height = 44.dp)
            .zIndex(if (dragged) 20f else 1f)
            .graphicsLayer {
                translationX = dragOffsetPx + leftTrimEdgeOffsetPx
                scaleX = if (dragged) 1.05f else 1f
                scaleY = if (dragged) 1.05f else 1f
                alpha = if (dragged) 0.9f else 1f
                shadowElevation = if (dragged) 18f else 0f
            }
            .shadow(if (dragged) 14.dp else 0.dp, RoundedCornerShape(4.dp))
            .border(
                width = if (selected || splitAffected || trimMode || boundarySelected) 1.dp else 0.dp,
                color = when {
                    boundarySelected -> Color.White.copy(alpha = 0.36f)
                    trimMode -> Color.White.copy(alpha = 0.38f)
                    selected -> Color.White.copy(alpha = 0.30f)
                    splitAffected -> Color.White.copy(alpha = 0.22f)
                    active -> Color.White.copy(alpha = 0.16f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(0.dp)
            )
            .background(Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .then(dragModifier)
    ) {
        CapCutFilmstrip(
            clip = clip,
            width = width,
            frameCache = frameCache,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize()
        )
        animationMarkers?.let { markers ->
            ClipAnimationMarkers(
                clip = clip,
                markers = markers,
                pxPerMs = pxPerMs
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.02f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.06f))
        )
        if (showTransitionMarker && onTransitionClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 0.dp)
                    .size(if (hasTransition) 10.dp else 8.dp)
                    .zIndex(if (isSplitBoundaryMarker) 80f else 6f)
                    .clip(CircleShape)
                    .background(if (hasTransition) AppColors.Primary.copy(alpha = 0.34f) else Color(0xFF24242A).copy(alpha = 0.38f))
                    .border(0.5.dp, Color.Black.copy(alpha = 0.18f), CircleShape)
                    .clickable(onClick = onTransitionClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (hasTransition) (transitionIcon ?: "T") else "+",
                    color = if (hasTransition) Color.Black else Color.White,
                    fontSize = if (hasTransition) 6.sp else 7.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 8.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (isSplitBoundaryMarker && onBoundarySelected != null) {
            CapCutSharedBoundaryHandle(
                modifier = Modifier.align(Alignment.CenterEnd),
                pxPerMs = pxPerMs,
                onSelected = onBoundarySelected,
                onStarted = onBoundaryTrimStarted,
                onDragged = onBoundaryTrimDragged,
                onFinished = onBoundaryTrimFinished
            )
        }
        if (selected || splitAffected || trimMode) {
            if (trimMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.07f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.07f))
                )
            }
            Text(
                TimeFormatter.formatMs(clip.durationMs),
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .background(Color.Black.copy(alpha = 0.34f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
            if (trimMode && !splitAffected) {
                CapCutTrimHandle(
                    modifier = Modifier.align(Alignment.CenterStart),
                    clipId = clip.id,
                    pxPerMs = pxPerMs,
                    side = TrimSide.LEFT,
                    onStarted = onTrimStarted,
                    onDragged = onTrimPreviewUpdated,
                    onFinished = onTrimCommitted
                )
                CapCutTrimHandle(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    clipId = clip.id,
                    pxPerMs = pxPerMs,
                    side = TrimSide.RIGHT,
                    onStarted = onTrimStarted,
                    onDragged = onTrimPreviewUpdated,
                    onFinished = onTrimCommitted
                )
            }
        }
    }
}

@Composable
private fun BoxScope.ClipAnimationMarkers(
    clip: ClipUiModel,
    markers: ClipAnimationMarkerState,
    pxPerMs: Float
) {
    val density = LocalDensity.current
    markers.comboMarker?.let { summary ->
        TimelineAnimationMarker(
            clip = clip,
            summary = summary,
            pxPerMs = pxPerMs,
            density = density,
            color = AppColors.Primary.copy(alpha = 0.82f),
            yAlignment = Alignment.BottomStart,
            height = 3.dp
        )
    }
    markers.inMarker?.let { summary ->
        TimelineAnimationMarker(
            clip = clip,
            summary = summary,
            pxPerMs = pxPerMs,
            density = density,
            color = Color(0xFF7AF0B0).copy(alpha = 0.88f),
            yAlignment = Alignment.TopStart,
            height = 4.dp
        )
    }
    markers.outMarker?.let { summary ->
        TimelineAnimationMarker(
            clip = clip,
            summary = summary,
            pxPerMs = pxPerMs,
            density = density,
            color = Color(0xFFFFD166).copy(alpha = 0.88f),
            yAlignment = Alignment.TopStart,
            height = 4.dp
        )
    }
}

@Composable
private fun BoxScope.TimelineAnimationMarker(
    clip: ClipUiModel,
    summary: AnimationSummary,
    pxPerMs: Float,
    density: androidx.compose.ui.unit.Density,
    color: Color,
    yAlignment: Alignment,
    height: Dp
) {
    val startOffsetMs = (summary.windowStartMs - clip.timelineStartMs).coerceAtLeast(0L)
    val widthPx = (summary.effectiveDurationMs * pxPerMs).coerceAtLeast(1f)
    val offsetPx = startOffsetMs * pxPerMs
    Box(
        modifier = Modifier
            .align(yAlignment)
            .offset(x = with(density) { offsetPx.toDp() })
            .width(with(density) { widthPx.toDp() })
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
            .testTag(CLIP_ANIMATION_MARKER_TAG)
    )
}

@Composable
private fun CapCutTrimHandle(
    modifier: Modifier,
    clipId: String,
    pxPerMs: Float,
    side: TrimSide,
    onStarted: (String, TrimSide) -> Unit,
    onDragged: (String, TrimSide, Long) -> Unit,
    onFinished: (String, TrimSide) -> Unit
) {
    var accumulatedDeltaPx by remember { mutableStateOf(0f) }
    Box(
        modifier = modifier
            .width(14.dp)
            .fillMaxHeight()
            .zIndex(30f)
            .pointerInput(clipId, side, pxPerMs) {
                detectDragGestures(
                    onDragStart = {
                        accumulatedDeltaPx = 0f
                        onStarted(clipId, side)
                    },
                    onDragEnd = {
                        accumulatedDeltaPx = 0f
                        onFinished(clipId, side)
                    },
                    onDragCancel = {
                        accumulatedDeltaPx = 0f
                        onFinished(clipId, side)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val msPerPx = 1f / pxPerMs.coerceAtLeast(0.0001f)
                        accumulatedDeltaPx += dragAmount.x
                        val deltaMs = (accumulatedDeltaPx * msPerPx).roundToInt().toLong()
                        if (side == TrimSide.LEFT) {
                            Log.d(SCREEN_TAG, "LEFT_TRIM_DELTA_PX clipId=$clipId deltaPx=${dragAmount.x}")
                            Log.d(SCREEN_TAG, "LEFT_TRIM_ACCUMULATED_PX clipId=$clipId value=$accumulatedDeltaPx")
                        } else {
                            Log.d(SCREEN_TAG, "RIGHT_TRIM_ACCUMULATED_PX clipId=$clipId value=$accumulatedDeltaPx")
                            Log.d(SCREEN_TAG, "RIGHT_TRIM_DELTA_MS clipId=$clipId deltaMs=$deltaMs")
                        }
                        onDragged(clipId, side, deltaMs)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.66f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.Black.copy(alpha = 0.18f))
            )
        }
    }
}

@Composable
private fun CapCutSharedBoundaryHandle(
    modifier: Modifier,
    pxPerMs: Float,
    onSelected: () -> Unit,
    onStarted: () -> Unit,
    onDragged: (Long) -> Unit,
    onFinished: () -> Unit
) {
    var accumulatedDeltaPx by remember { mutableStateOf(0f) }
    Box(
        modifier = modifier
            .offset(x = 1.dp)
            .width(24.dp)
            .fillMaxHeight()
            .zIndex(100f)
            .pointerInput(pxPerMs) {
                detectDragGestures(
                    onDragStart = {
                        accumulatedDeltaPx = 0f
                        onSelected()
                        onStarted()
                    },
                    onDragEnd = {
                        accumulatedDeltaPx = 0f
                        onFinished()
                    },
                    onDragCancel = {
                        accumulatedDeltaPx = 0f
                        onFinished()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val msPerPx = 1f / pxPerMs.coerceAtLeast(0.0001f)
                        accumulatedDeltaPx += dragAmount.x
                        val deltaMs = (accumulatedDeltaPx * msPerPx).roundToInt().toLong()
                        if (deltaMs != 0L) onDragged(deltaMs)
                    }
                )
            }
            .clickable {
                onSelected()
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.34f))
        )
    }
}

@Composable
private fun CapCutSplitBoundaryHandle(
    pxPerMs: Float,
    onDragged: (Long) -> Unit,
    onFinished: () -> Unit
) {
    var remainderPx by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .width(18.dp)
            .height(46.dp)
            .pointerInput(pxPerMs) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        remainderPx = 0f
                        onFinished()
                    },
                    onDragCancel = {
                        remainderPx = 0f
                        onFinished()
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val msPerPx = 1f / pxPerMs.coerceAtLeast(0.0001f)
                        val rawDeltaMs = (dragAmount + remainderPx) * msPerPx
                        val deltaMs = rawDeltaMs.roundToInt().toLong()
                        remainderPx = (rawDeltaMs - deltaMs) / msPerPx
                        if (deltaMs != 0L) onDragged(deltaMs)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.66f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(Color.Black.copy(alpha = 0.16f))
            )
        }
    }
}

@Composable
private fun CapCutFilmstrip(
    clip: ClipUiModel,
    width: Dp,
    frameCache: MutableMap<String, List<Bitmap?>>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val tileCount = (width.value / 20f).roundToInt().coerceAtLeast(2)
    val isVideo = clip.mediaType == MediaType.VIDEO || clip.mediaType == MediaType.OVERLAY_VIDEO
    val context = LocalContext.current
    val cacheKey = "${clip.id}:${clip.thumbnailUri}:${clip.sourceStartMs}:${clip.sourceEndMs}:${clip.durationMs}"
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val videoFrames by produceState<List<Bitmap?>>(initialValue = frameCache[cacheKey].orEmpty(), cacheKey) {
        value = if (isVideo && clip.thumbnailUri != null) {
            frameCache[cacheKey]?.also {
                if (latestIsPlaying) {
                    Log.v(
                        SCREEN_TAG,
                        "thumbnail cacheHit whilePlaying=true clipId=${clip.id} thread=${threadLabel()}"
                    )
                }
            } ?: if (latestIsPlaying) {
                Log.d(
                    SCREEN_TAG,
                    "thumbnailGenerationSkippedDuringPlayback clipId=${clip.id} thumbnailGenerationCalled=false " +
                        "thread=${threadLabel()}"
                )
                emptyList()
            } else {
                loadTimelineFrames(
                    context = context,
                    uriString = clip.thumbnailUri,
                    sourceStartMs = clip.sourceStartMs,
                    durationMs = clip.durationMs,
                    count = 9,
                    clipId = clip.id,
                    playbackActiveAtRequest = false
                ).also {
                    frameCache[cacheKey] = it
                }
            }
        } else {
            emptyList()
        }
    }

    Row(modifier = modifier.background(Color(0xFF2B2B2B))) {
        repeat(tileCount) { index ->
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .fillMaxHeight()
            ) {
                val frame = if (videoFrames.isNotEmpty()) videoFrames[index % videoFrames.size] else null
                when {
                    frame != null -> {
                        Image(
                            bitmap = frame.asImageBitmap(),
                            contentDescription = clip.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    clip.thumbnailUri != null && !isVideo -> {
                        AsyncImage(
                            model = clip.thumbnailUri,
                            contentDescription = clip.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    clip.thumbnailUri != null -> {
                        AsyncImage(
                            model = clip.thumbnailUri,
                            contentDescription = clip.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> Unit
                }
                if (frame == null && clip.thumbnailUri == null) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF303030)))
                }
                if (frame == null && isVideo) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF303030)))
                }
                if (frame == null && isVideo && videoFrames.isEmpty()) {
                    AsyncImage(
                        model = clip.thumbnailUri,
                        contentDescription = clip.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.02f))
                )
            }
        }
    }
}

private suspend fun loadTimelineFrames(
    context: Context,
    uriString: String,
    sourceStartMs: Long,
    durationMs: Long,
    count: Int,
    clipId: String,
    playbackActiveAtRequest: Boolean
): List<Bitmap?> = withContext(Dispatchers.IO) {
    val startMs = System.currentTimeMillis()
    Log.d(
        SCREEN_TAG,
        "thumbnailExtractionStarted clipId=$clipId playbackActiveAtRequest=$playbackActiveAtRequest " +
            "thread=${threadLabel()} durationMs=$durationMs count=$count"
    )
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, Uri.parse(uriString))
        val frames = List(count) { index ->
            val fraction = if (count <= 1) 0f else index.toFloat() / (count - 1)
            val sourceFrameMs = sourceStartMs.coerceAtLeast(0L) +
                (durationMs.coerceAtLeast(1L) * fraction).roundToLong()
            val timeUs = sourceFrameMs * 1000L
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let { Bitmap.createScaledBitmap(it, 96, 108, false) }
        }
        Log.d(
            SCREEN_TAG,
            "thumbnailExtractionFinished clipId=$clipId playbackActiveAtRequest=$playbackActiveAtRequest " +
                "thread=${threadLabel()} elapsedMs=${System.currentTimeMillis() - startMs} generated=${frames.count { it != null }}"
        )
        frames
    } catch (_: Throwable) {
        Log.w(
            SCREEN_TAG,
            "thumbnailExtractionFailed clipId=$clipId playbackActiveAtRequest=$playbackActiveAtRequest " +
                "thread=${threadLabel()} elapsedMs=${System.currentTimeMillis() - startMs}"
        )
        List(count) { null }
    } finally {
        runCatching { retriever.release() }
    }
}

private suspend fun loadPreviewFrame(
    context: Context,
    uriString: String,
    sourceFrameMs: Long,
    clipId: String
): Bitmap? = withContext(Dispatchers.IO) {
    val startMs = System.currentTimeMillis()
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, Uri.parse(uriString))
        val frame = retriever.getFrameAtTime(
            sourceFrameMs.coerceAtLeast(0L) * 1000L,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        )?.let { Bitmap.createScaledBitmap(it, 420, 746, false) }
        Log.d(
            SCREEN_TAG,
            "CROSSFADE_INCOMING_FRAME clipId=$clipId sourceFrameMs=$sourceFrameMs " +
                "frameVisible=${frame != null} elapsedMs=${System.currentTimeMillis() - startMs}"
        )
        frame
    } catch (_: Throwable) {
        Log.w(
            SCREEN_TAG,
            "CROSSFADE_INCOMING_FRAME_FAILED clipId=$clipId sourceFrameMs=$sourceFrameMs " +
                "elapsedMs=${System.currentTimeMillis() - startMs}"
        )
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun ClipUiModel.capCutTimelineWidth(zoom: Float): Dp =
    (durationMs / 1000f * 28f * zoom.coerceIn(MIN_TIMELINE_ZOOM, MAX_TIMELINE_ZOOM))
        .coerceAtLeast(18f)
        .dp

@Composable
private fun CapCutAddClipButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.74f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add clip", tint = Color.Black.copy(alpha = 0.76f), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun EmptyCapCutClipSlot() {
    Box(
        modifier = Modifier
            .size(width = 120.dp, height = 54.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2A2A2A)),
        contentAlignment = Alignment.Center
    ) {
        Text("+", color = Color.White, fontSize = 28.sp)
    }
}

@Composable
private fun AddTrackLane(label: String, onClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(top = 2.dp, end = 12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF262626))
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, color = AppColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

private data class TimelineToolbarTool(
    val label: String,
    val icon: String,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null
)

private val primaryTimelineTools = listOf(
    TimelineToolbarTool("Edit", "Edit"),
    TimelineToolbarTool("Audio", "Aud"),
    TimelineToolbarTool("Text", "T"),
    TimelineToolbarTool("Effects", "Fx"),
    TimelineToolbarTool("Overlay", "Ov"),
    TimelineToolbarTool("Captions", "Cap"),
    TimelineToolbarTool("Filters", "Fil"),
    TimelineToolbarTool("Adjust", "Adj"),
    TimelineToolbarTool("Stickers", "Stk"),
    TimelineToolbarTool("AI Avatar", "AI"),
    TimelineToolbarTool("AI Media", "AI"),
    TimelineToolbarTool("Aspect Ratio", "9:16"),
    TimelineToolbarTool("Background", "BG")
)

private val editTimelineTools = listOf(
    TimelineToolbarTool("Back", "<"),
    TimelineToolbarTool("Undo", "Undo"),
    TimelineToolbarTool("Redo", "Redo"),
    TimelineToolbarTool("Trim", "Trim"),
    TimelineToolbarTool("Transition", "T"),
    TimelineToolbarTool("Split", "Split"),
    TimelineToolbarTool("Volume", "Vol"),
    TimelineToolbarTool("Animation", "Anim"),
    TimelineToolbarTool("Effects", "Fx"),
    TimelineToolbarTool("Delete", "Del"),
    TimelineToolbarTool("Speed", "1x"),
    TimelineToolbarTool("Beats", "Beat"),
    TimelineToolbarTool("Crop", "Crop"),
    TimelineToolbarTool("Duplicate", "Dup"),
    TimelineToolbarTool("Replace", "Rep"),
    TimelineToolbarTool("Overlay", "Ov"),
    TimelineToolbarTool("Adjust", "Adj"),
    TimelineToolbarTool("Filters", "Fil"),
    TimelineToolbarTool("Retouch", "Face"),
    TimelineToolbarTool("Video Quality", "HD"),
    TimelineToolbarTool("Remove BG", "BG"),
    TimelineToolbarTool("AI Remove", "AI"),
    TimelineToolbarTool("AI Expand", "AI"),
    TimelineToolbarTool("AI Remix", "AI"),
    TimelineToolbarTool("Eye Contact", "Eye"),
    TimelineToolbarTool("Relight", "Sun"),
    TimelineToolbarTool("Opacity", "%"),
    TimelineToolbarTool("Motion Blur", "Blur"),
    TimelineToolbarTool("Lip Sync", "Lip"),
    TimelineToolbarTool("Transform", "Move"),
    TimelineToolbarTool("Auto Reframe", "Frame"),
    TimelineToolbarTool("Stabilize", "Stab"),
    TimelineToolbarTool("Camera Tracking", "Track"),
    TimelineToolbarTool("Extract Audio", "Aud"),
    TimelineToolbarTool("Isolate Voice", "Voice"),
    TimelineToolbarTool("Reduce Noise", "Noise"),
    TimelineToolbarTool("Audio Effects", "Fx"),
    TimelineToolbarTool("Enhance Voice", "Voice"),
    TimelineToolbarTool("Video Translator", "Lang"),
    TimelineToolbarTool("Freeze", "Hold"),
    TimelineToolbarTool("Reverse", "Rev"),
    TimelineToolbarTool("Mask", "Mask"),
    TimelineToolbarTool("Unlink", "Link")
)

private fun editActionForLabel(label: String): EditToolAction? = when (label) {
    "Back" -> EditToolAction.Back
    "Undo" -> EditToolAction.Undo
    "Redo" -> EditToolAction.Redo
    "Trim" -> EditToolAction.Trim
    "Transition" -> EditToolAction.Transition
    "Split" -> EditToolAction.Split
    "Volume" -> EditToolAction.Volume
    "Animation" -> EditToolAction.Animations
    "Effects" -> EditToolAction.Effects
    "Delete" -> EditToolAction.Delete
    "Speed" -> EditToolAction.Speed
    "Beats" -> EditToolAction.Beats
    "Crop" -> EditToolAction.Crop
    "Duplicate" -> EditToolAction.Duplicate
    "Replace" -> EditToolAction.Replace
    "Overlay" -> EditToolAction.Overlay
    "Adjust" -> EditToolAction.Adjust
    "Filters" -> EditToolAction.Filters
    "Retouch" -> EditToolAction.Retouch
    "Video Quality" -> EditToolAction.VideoQuality
    "Remove BG" -> EditToolAction.RemoveBg
    "AI Remove" -> EditToolAction.AiRemove
    "AI Expand" -> EditToolAction.AiExpand
    "AI Remix" -> EditToolAction.AiRemix
    "Eye Contact" -> EditToolAction.EyeContact
    "Relight" -> EditToolAction.Relight
    "Opacity" -> EditToolAction.Opacity
    "Motion Blur" -> EditToolAction.MotionBlur
    "Lip Sync" -> EditToolAction.LipSync
    "Transform" -> EditToolAction.Transform
    "Auto Reframe" -> EditToolAction.AutoReframe
    "Stabilize" -> EditToolAction.Stabilize
    "Camera Tracking" -> EditToolAction.CameraTracking
    "Extract Audio" -> EditToolAction.ExtractAudio
    "Isolate Voice" -> EditToolAction.IsolateVoice
    "Reduce Noise" -> EditToolAction.ReduceNoise
    "Audio Effects" -> EditToolAction.AudioEffects
    "Enhance Voice" -> EditToolAction.EnhanceVoice
    "Video Translator" -> EditToolAction.VideoTranslator
    "Freeze" -> EditToolAction.Freeze
    "Reverse" -> EditToolAction.Reverse
    "Mask" -> EditToolAction.Mask
    "Unlink" -> EditToolAction.Unlink
    else -> null
}

@Composable
private fun TimelineToolbar(
    toolbarMode: EditorToolbarMode,
    canUndo: Boolean,
    canRedo: Boolean,
    splitButtonEnabled: Boolean,
    onPrimaryTool: (String) -> Unit,
    onEditTool: (EditToolAction) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .zIndex(50f),
        color = Color(0xFF0F0F14),
        tonalElevation = 4.dp
    ) {
        if (toolbarMode == EditorToolbarMode.EDIT) {
            val tools = editTimelineTools.map { tool ->
                val enabled = when (tool.label) {
                    "Undo" -> canUndo
                    "Redo" -> canRedo
                    "Split" -> splitButtonEnabled
                    else -> true
                }
                tool.copy(
                    enabled = enabled,
                    onClick = if (enabled) ({ editActionForLabel(tool.label)?.let(onEditTool) }) else null
                )
            }
            TimelineToolLazyRow(
                tools = tools,
                modifier = Modifier.fillMaxWidth().height(80.dp)
            )
        } else {
            val tools = primaryTimelineTools.map { tool ->
                tool.copy(onClick = { onPrimaryTool(tool.label) })
            }
            TimelineToolLazyRow(
                tools = tools,
                modifier = Modifier.fillMaxWidth().height(76.dp)
            )
        }
    }
}

@Composable
private fun TimelineToolLazyRow(
    tools: List<TimelineToolbarTool>,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(tools) { _, tool ->
            TimelineToolbarButton(tool = tool)
        }
    }
}

@Composable
private fun TimelineToolbarButton(tool: TimelineToolbarTool) {
    val isBack = tool.label == "Back"
    val contentAlpha = if (tool.enabled) 1f else 0.34f
    Column(
        modifier = Modifier
            .width(if (isBack) 48.dp else 66.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(if (isBack) 4.dp else 0.dp))
            .background(if (isBack) Color(0xFF25252A) else Color.Transparent)
            .clickable(enabled = tool.enabled && tool.onClick != null) { tool.onClick?.invoke() }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            tool.icon,
            color = Color.White.copy(alpha = contentAlpha),
            fontSize = if (tool.icon.length <= 1) 22.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(26.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            tool.label,
            color = Color.White.copy(alpha = 0.86f * contentAlpha),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// â”€â”€ Preview area â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComingSoonSheet(tool: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1B1B1F)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Coming soon", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "$tool will be available in a future update.",
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("OK")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPlaceholderSheet(tool: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1B1B1F)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(tool, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "This edit panel is ready for the next control pass.",
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Done")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSelectorSheet(initialSpeed: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    var selectedMode by remember { mutableStateOf("Normal") }
    val presets = remember {
        listOf(0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f, 8.0f)
    }
    val selectedSpeed = initialSpeed.coerceIn(0.1f, 8f)
    fun speedLabel(speed: Float): String {
        return if (speed % 1f == 0f) "${speed.toInt()}.0x" else "${speed}x"
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1B1B1F)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Speed: ${speedLabel(selectedSpeed)}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF26262A), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Normal", "Curve").forEach { mode ->
                    val selected = selectedMode == mode
                    TextButton(
                        onClick = { selectedMode = mode },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (selected) Color(0xFF3A3A40) else Color.Transparent,
                            contentColor = if (selected) Color.White else AppColors.TextSecondary
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(mode, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (selectedMode == "Normal") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    presets.forEach { speed ->
                        val selected = abs(speed - selectedSpeed) < 0.001f
                        Button(
                            onClick = { onSelect(speed) },
                            modifier = Modifier.widthIn(min = 68.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) AppColors.Primary else Color(0xFF2D2D32),
                                contentColor = if (selected) Color.Black else Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(speedLabel(speed), maxLines = 1, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(Color(0xFF242428), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Curve speed is coming soon", color = AppColors.TextSecondary, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun CapCutTransitionPanel(
    fromClip: ClipUiModel?,
    toClip: ClipUiModel?,
    initialType: TransitionType,
    initialDurationMs: Long,
    onBack: () -> Unit,
    onApply: (TransitionType, Long) -> Unit,
    onConfirm: (TransitionType, Long) -> Unit,
    onApplyAll: (TransitionType, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by remember(initialType) { mutableStateOf(initialType) }
    var selectedDurationMs by remember(initialDurationMs) {
        mutableLongStateOf(initialDurationMs.takeIf { it > 0L } ?: 500L)
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Trending") }
    val categories = remember { listOf("Trending", "Basic", "Slide", "Push", "Zoom", "Blur", "Glitch", "Wipe", "Camera", "Effects", "3D") }
    val transitionTypes = remember {
        listOf(
            TransitionType.NONE,
            TransitionType.FADE,
            TransitionType.DISSOLVE,
            TransitionType.FADE_BLACK,
            TransitionType.FADE_WHITE,
            TransitionType.CROSS_DISSOLVE,
            TransitionType.FADE_BLACK,
            TransitionType.FADE_WHITE,
            TransitionType.SLIDE_LEFT,
            TransitionType.SLIDE_RIGHT,
            TransitionType.SLIDE_UP,
            TransitionType.SLIDE_DOWN,
            TransitionType.PUSH_LEFT,
            TransitionType.PUSH_RIGHT,
            TransitionType.PUSH_UP,
            TransitionType.PUSH_DOWN,
            TransitionType.ZOOM_IN,
            TransitionType.ZOOM_OUT,
            TransitionType.BLUR,
            TransitionType.MOTION_BLUR,
            TransitionType.MOTION_BLUR_LEFT,
            TransitionType.MOTION_BLUR_RIGHT,
            TransitionType.MOTION_BLUR_UP,
            TransitionType.MOTION_BLUR_DOWN,
            TransitionType.GAUSSIAN_BLUR,
            TransitionType.GLITCH,
            TransitionType.GLITCH_PRO,
            TransitionType.GLITCH_DIGITAL,
            TransitionType.GLITCH_RGB,
            TransitionType.GLITCH_SCANLINE,
            TransitionType.RGB_SPLIT,
            TransitionType.CHROMATIC_ABERRATION,
            TransitionType.WIPE,
            TransitionType.WIPE_RIGHT,
            TransitionType.WIPE_UP,
            TransitionType.WIPE_DOWN,
            TransitionType.SPIN,
            TransitionType.ROTATE,
            TransitionType.CAMERA_ROLL,
            TransitionType.WHIP_PAN_LEFT,
            TransitionType.WHIP_PAN_RIGHT,
            TransitionType.WHIP_PAN_UP,
            TransitionType.WHIP_PAN_DOWN,
            TransitionType.FLASH,
            TransitionType.FLASH_BLACK,
            TransitionType.FLASH_WARM,
            TransitionType.FLASH_BLUE,
            TransitionType.FILM_BURN,
            TransitionType.FILM_BURN_WARM,
            TransitionType.FILM_BURN_HEAVY,
            TransitionType.BOUNCE,
            TransitionType.SHAKE,
            TransitionType.SWING,
            TransitionType.POP,
            TransitionType.CUBE_LEFT,
            TransitionType.CUBE_RIGHT,
            TransitionType.CUBE_UP,
            TransitionType.CUBE_DOWN,
            TransitionType.FLIP_LEFT,
            TransitionType.FLIP_RIGHT,
            TransitionType.FLIP_UP,
            TransitionType.FLIP_DOWN,
            TransitionType.FLIP_HORIZONTAL,
            TransitionType.FLIP_VERTICAL,
            TransitionType.DOOR_OPEN,
            TransitionType.DOOR_CLOSE,
            TransitionType.CAROUSEL,
            TransitionType.BOOK_TURN,
            TransitionType.PAGE_TURN,
            TransitionType.PAGE_TURN_LEFT,
            TransitionType.PAGE_TURN_RIGHT,
            TransitionType.PAGE_TURN_UP,
            TransitionType.PAGE_TURN_DOWN,
            TransitionType.FOLD,
            TransitionType.TUNNEL,
            TransitionType.PRISM
        )
    }
    val visibleTypes = remember(transitionTypes, searchQuery, selectedCategory) {
        val categoryTypes = when (selectedCategory) {
            "Basic" -> listOf(TransitionType.NONE, TransitionType.FADE, TransitionType.DISSOLVE, TransitionType.FADE_BLACK, TransitionType.FADE_WHITE, TransitionType.CROSS_DISSOLVE)
            "Slide" -> listOf(TransitionType.SLIDE_LEFT, TransitionType.SLIDE_RIGHT, TransitionType.SLIDE_UP, TransitionType.SLIDE_DOWN)
            "Push" -> listOf(TransitionType.PUSH_LEFT, TransitionType.PUSH_RIGHT, TransitionType.PUSH_UP, TransitionType.PUSH_DOWN)
            "Zoom" -> listOf(TransitionType.ZOOM_IN, TransitionType.ZOOM_OUT)
            "Blur" -> listOf(
                TransitionType.BLUR,
                TransitionType.MOTION_BLUR,
                TransitionType.MOTION_BLUR_LEFT,
                TransitionType.MOTION_BLUR_RIGHT,
                TransitionType.MOTION_BLUR_UP,
                TransitionType.MOTION_BLUR_DOWN,
                TransitionType.GAUSSIAN_BLUR
            )
            "Glitch" -> listOf(
                TransitionType.GLITCH_PRO,
                TransitionType.GLITCH_DIGITAL,
                TransitionType.GLITCH_RGB,
                TransitionType.GLITCH_SCANLINE,
                TransitionType.GLITCH,
                TransitionType.RGB_SPLIT,
                TransitionType.CHROMATIC_ABERRATION
            )
            "Wipe" -> listOf(
                TransitionType.WIPE,
                TransitionType.WIPE_RIGHT,
                TransitionType.WIPE_UP,
                TransitionType.WIPE_DOWN
            )
            "Camera" -> listOf(
                TransitionType.SPIN,
                TransitionType.ROTATE,
                TransitionType.CAMERA_ROLL,
                TransitionType.WHIP_PAN_LEFT,
                TransitionType.WHIP_PAN_RIGHT,
                TransitionType.WHIP_PAN_UP,
                TransitionType.WHIP_PAN_DOWN
            )
            "Effects" -> listOf(
                TransitionType.FLASH,
                TransitionType.FLASH_BLACK,
                TransitionType.FLASH_WARM,
                TransitionType.FLASH_BLUE,
                TransitionType.FILM_BURN,
                TransitionType.FILM_BURN_WARM,
                TransitionType.FILM_BURN_HEAVY,
                TransitionType.BOUNCE,
                TransitionType.SHAKE,
                TransitionType.SWING,
                TransitionType.POP
            )
            "3D" -> listOf(
                TransitionType.CUBE_LEFT,
                TransitionType.CUBE_RIGHT,
                TransitionType.CUBE_UP,
                TransitionType.CUBE_DOWN,
                TransitionType.FLIP_LEFT,
                TransitionType.FLIP_RIGHT,
                TransitionType.FLIP_UP,
                TransitionType.FLIP_DOWN,
                TransitionType.FLIP_HORIZONTAL,
                TransitionType.FLIP_VERTICAL,
                TransitionType.DOOR_OPEN,
                TransitionType.DOOR_CLOSE,
                TransitionType.CAROUSEL,
                TransitionType.BOOK_TURN,
                TransitionType.PAGE_TURN,
                TransitionType.PAGE_TURN_LEFT,
                TransitionType.PAGE_TURN_RIGHT,
                TransitionType.PAGE_TURN_UP,
                TransitionType.PAGE_TURN_DOWN,
                TransitionType.FOLD,
                TransitionType.TUNNEL,
                TransitionType.PRISM
            )
            else -> transitionTypes
        }
        val q = searchQuery.trim()
        categoryTypes.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
    }
    val appliedDuration = if (selectedType == TransitionType.NONE) 0L else selectedDurationMs
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1B1B1F))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("<", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Transition", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (selectedType == TransitionType.NONE) "None" else "${selectedType.label}  ${transitionDurationLabel(selectedDurationMs)}",
                    color = AppColors.TextSecondary,
                    fontSize = 11.sp
                )
            }
            TextButton(
                onClick = {
                    onConfirm(selectedType, appliedDuration)
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("OK", color = AppColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            placeholder = { Text("Search transitions", color = AppColors.TextSecondary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AppColors.Primary,
                unfocusedBorderColor = Color(0xFF3A3A40),
                focusedContainerColor = Color(0xFF242428),
                unfocusedContainerColor = Color(0xFF242428)
            ),
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                val selected = selectedCategory == category
                Text(
                    category,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) AppColors.Primary else Color(0xFF2D2D32))
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransitionPreviewTile(fromClip, "A", Modifier.weight(1f).fillMaxHeight())
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .background(if (selectedType == TransitionType.NONE) Color(0xFF2B2B2B) else AppColors.Primary.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Text(capCutTransitionIcon(selectedType), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            TransitionPreviewTile(toClip, "B", Modifier.weight(1f).fillMaxHeight())
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            visibleTypes.forEach { type ->
                val selected = selectedType == type
                Column(
                    modifier = Modifier
                        .width(92.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) AppColors.Primary.copy(alpha = 0.22f) else Color(0xFF26262A))
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) AppColors.Primary else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            selectedType = type
                            if (type == TransitionType.NONE) selectedDurationMs = 500L
                            onApply(type, if (type == TransitionType.NONE) 0L else selectedDurationMs)
                        }
                        .padding(7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(transitionCardBrush(type)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(capCutTransitionIcon(type), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(5.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.Primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("OK", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(type.label, color = Color.White, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Duration", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(transitionDurationLabel(selectedDurationMs), color = AppColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = selectedDurationMs.toFloat(),
            onValueChange = {
                selectedDurationMs = it.roundToInt().toLong().coerceIn(100L, 3_000L)
            },
            onValueChangeFinished = {
                onApply(selectedType, appliedDuration)
            },
            valueRange = 100f..3_000f,
            enabled = selectedType != TransitionType.NONE
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    selectedType = TransitionType.NONE
                    selectedDurationMs = 300L
                    onApply(TransitionType.NONE, 0L)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Remove")
            }
            Button(
                onClick = { onApplyAll(selectedType, appliedDuration) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D32))
            ) {
                Text("Apply all", color = Color.White)
            }
        }
    }
}

@Composable
private fun TransitionPreviewTile(clip: ClipUiModel?, fallback: String, modifier: Modifier) {
    Box(modifier = modifier.background(Color(0xFF242428)), contentAlignment = Alignment.Center) {
        if (clip?.thumbnailUri != null) {
            AsyncImage(
                model = clip.thumbnailUri,
                contentDescription = clip.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(fallback, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun capCutTransitionIcon(type: TransitionType): String = when (type) {
    TransitionType.NONE -> "+"
    TransitionType.DISSOLVE, TransitionType.CROSS_DISSOLVE -> "D"
    TransitionType.FADE -> "F"
    TransitionType.FADE_BLACK -> "B"
    TransitionType.FADE_WHITE -> "W"
    TransitionType.SLIDE_LEFT -> "<"
    TransitionType.SLIDE_RIGHT -> ">"
    TransitionType.PUSH_LEFT -> "<<"
    TransitionType.PUSH_RIGHT -> ">>"
    TransitionType.ZOOM_IN -> "+"
    TransitionType.ZOOM_OUT -> "-"
    TransitionType.BLUR -> "B"
    TransitionType.SPIN -> "S"
    TransitionType.FLASH -> "*"
    TransitionType.FLASH_WARM -> "W*"
    TransitionType.FLASH_BLUE -> "U*"
    TransitionType.FILM_BURN -> "FB"
    TransitionType.FILM_BURN_WARM -> "FW"
    TransitionType.FILM_BURN_HEAVY -> "FH"
    TransitionType.WIPE -> "/"
    TransitionType.WIPE_RIGHT -> "/>"
    TransitionType.SLIDE_UP -> "^"
    TransitionType.SLIDE_DOWN -> "v"
    TransitionType.PUSH_UP -> "^^"
    TransitionType.PUSH_DOWN -> "vv"
    TransitionType.WIPE_UP -> "/^"
    TransitionType.WIPE_DOWN -> "/v"
    TransitionType.MIRROR_FLIP -> "M"
    TransitionType.GLITCH -> "G"
    TransitionType.GLITCH_PRO -> "GP"
    TransitionType.GLITCH_DIGITAL -> "GD"
    TransitionType.GLITCH_RGB -> "GR"
    TransitionType.GLITCH_SCANLINE -> "GS"
    TransitionType.MOTION_BLUR -> "MB"
    TransitionType.MOTION_BLUR_LEFT -> "M<"
    TransitionType.MOTION_BLUR_RIGHT -> "M>"
    TransitionType.MOTION_BLUR_UP -> "M^"
    TransitionType.MOTION_BLUR_DOWN -> "Mv"
    TransitionType.GAUSSIAN_BLUR -> "GB"
    TransitionType.ROTATE -> "R"
    TransitionType.CAMERA_ROLL -> "CR"
    TransitionType.WHIP_PAN_LEFT -> "<~"
    TransitionType.WHIP_PAN_RIGHT -> "~>"
    TransitionType.WHIP_PAN_UP -> "^~"
    TransitionType.WHIP_PAN_DOWN -> "v~"
    TransitionType.FLASH_BLACK -> "B*"
    TransitionType.BOUNCE -> "Bo"
    TransitionType.SHAKE -> "Sh"
    TransitionType.SWING -> "Sw"
    TransitionType.POP -> "Po"
    TransitionType.RGB_SPLIT -> "RGB"
    TransitionType.CHROMATIC_ABERRATION -> "CA"
    TransitionType.CUBE_LEFT -> "3L"
    TransitionType.CUBE_RIGHT -> "3R"
    TransitionType.CUBE_UP -> "3U"
    TransitionType.CUBE_DOWN -> "3D"
    TransitionType.FLIP_LEFT -> "FL"
    TransitionType.FLIP_RIGHT -> "FR"
    TransitionType.FLIP_UP -> "FU"
    TransitionType.FLIP_DOWN -> "FD"
    TransitionType.FLIP_HORIZONTAL -> "FH"
    TransitionType.FLIP_VERTICAL -> "FV"
    TransitionType.DOOR_OPEN -> "DO"
    TransitionType.DOOR_CLOSE -> "DC"
    TransitionType.CAROUSEL -> "Ca"
    TransitionType.BOOK_TURN -> "BT"
    TransitionType.PAGE_TURN -> "PT"
    TransitionType.PAGE_TURN_LEFT -> "PL"
    TransitionType.PAGE_TURN_RIGHT -> "PR"
    TransitionType.PAGE_TURN_UP -> "PU"
    TransitionType.PAGE_TURN_DOWN -> "PD"
    TransitionType.FOLD -> "Fo"
    TransitionType.TUNNEL -> "Tu"
    TransitionType.PRISM -> "Pr"
}

private fun transitionCardBrush(type: TransitionType): Brush = when (type) {
    TransitionType.NONE -> Brush.linearGradient(listOf(Color(0xFF3A3A40), Color(0xFF242428)))
    TransitionType.FADE,
    TransitionType.DISSOLVE,
    TransitionType.CROSS_DISSOLVE -> Brush.linearGradient(listOf(Color(0xFF30343D), Color(0xFF6C5CFF)))
    TransitionType.FADE_BLACK -> Brush.linearGradient(listOf(Color.Black, Color(0xFF3A3A40)))
    TransitionType.FADE_WHITE -> Brush.linearGradient(listOf(Color.White, Color(0xFF8B93A7)))
    TransitionType.SLIDE_LEFT,
    TransitionType.SLIDE_RIGHT,
    TransitionType.PUSH_LEFT,
    TransitionType.PUSH_RIGHT -> Brush.linearGradient(listOf(Color(0xFF20D4F5), Color(0xFF26262A)))
    TransitionType.ZOOM_IN,
    TransitionType.ZOOM_OUT -> Brush.linearGradient(listOf(Color(0xFF6C5CFF), Color(0xFF20D4F5)))
    TransitionType.FLASH,
    TransitionType.FLASH_BLACK,
    TransitionType.FLASH_WARM,
    TransitionType.FLASH_BLUE,
    TransitionType.FILM_BURN,
    TransitionType.FILM_BURN_WARM,
    TransitionType.FILM_BURN_HEAVY,
    TransitionType.BOUNCE,
    TransitionType.SHAKE,
    TransitionType.SWING,
    TransitionType.POP -> Brush.linearGradient(listOf(Color(0xFFFFF176), Color(0xFF26262A)))
    TransitionType.BLUR,
    TransitionType.MOTION_BLUR,
    TransitionType.MOTION_BLUR_LEFT,
    TransitionType.MOTION_BLUR_RIGHT,
    TransitionType.MOTION_BLUR_UP,
    TransitionType.MOTION_BLUR_DOWN,
    TransitionType.GAUSSIAN_BLUR -> Brush.linearGradient(listOf(Color(0xFF8B93A7), Color(0xFF242428)))
    TransitionType.SPIN,
    TransitionType.ROTATE,
    TransitionType.CAMERA_ROLL,
    TransitionType.WHIP_PAN_LEFT,
    TransitionType.WHIP_PAN_RIGHT,
    TransitionType.WHIP_PAN_UP,
    TransitionType.WHIP_PAN_DOWN -> Brush.linearGradient(listOf(Color(0xFFFF6B9A), Color(0xFF6C5CFF)))
    TransitionType.WIPE,
    TransitionType.WIPE_RIGHT -> Brush.linearGradient(listOf(Color(0xFF2D2D32), Color(0xFF9BFFB7)))
    TransitionType.SLIDE_UP,
    TransitionType.SLIDE_DOWN,
    TransitionType.PUSH_UP,
    TransitionType.PUSH_DOWN -> Brush.linearGradient(listOf(Color(0xFF20D4F5), Color(0xFF26262A)))
    TransitionType.WIPE_UP,
    TransitionType.WIPE_DOWN -> Brush.linearGradient(listOf(Color(0xFF2D2D32), Color(0xFF9BFFB7)))
    TransitionType.MIRROR_FLIP,
    TransitionType.CUBE_LEFT,
    TransitionType.CUBE_RIGHT,
    TransitionType.CUBE_UP,
    TransitionType.CUBE_DOWN,
    TransitionType.FLIP_LEFT,
    TransitionType.FLIP_RIGHT,
    TransitionType.FLIP_UP,
    TransitionType.FLIP_DOWN,
    TransitionType.FLIP_HORIZONTAL,
    TransitionType.FLIP_VERTICAL,
    TransitionType.DOOR_OPEN,
    TransitionType.DOOR_CLOSE,
    TransitionType.CAROUSEL,
    TransitionType.BOOK_TURN,
    TransitionType.PAGE_TURN,
    TransitionType.PAGE_TURN_LEFT,
    TransitionType.PAGE_TURN_RIGHT,
    TransitionType.PAGE_TURN_UP,
    TransitionType.PAGE_TURN_DOWN,
    TransitionType.FOLD,
    TransitionType.TUNNEL,
    TransitionType.PRISM -> Brush.linearGradient(listOf(Color(0xFF6C5CFF), Color(0xFF20D4F5)))
    TransitionType.GLITCH,
    TransitionType.GLITCH_PRO,
    TransitionType.GLITCH_DIGITAL,
    TransitionType.GLITCH_RGB,
    TransitionType.GLITCH_SCANLINE,
    TransitionType.RGB_SPLIT,
    TransitionType.CHROMATIC_ABERRATION -> Brush.linearGradient(listOf(Color(0xFFFF6B9A), Color(0xFF6C5CFF)))
}

private fun transitionDurationLabel(durationMs: Long): String =
    if (durationMs >= 1_000L) "${durationMs / 1_000f}s" else "0.${durationMs / 100L}s"

@Composable
private fun VolumePanel(
    initialVolume: Float,
    hasAudio: Boolean,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onDone: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var volume by remember(initialVolume) { mutableStateOf(initialVolume.coerceIn(0f, 10f)) }
    val presets = remember { listOf(0f, 0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 5f, 10f) }
    fun volumeLabel(value: Float): String = "${(value * 100f).roundToInt()}%"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1B1B1F))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text("<", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Volume",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            TextButton(
                onClick = { onDone(volume) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Done", color = AppColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            volumeLabel(volume),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        if (!hasAudio) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF242428), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("This clip has no audio", color = AppColors.TextSecondary, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
        Slider(
            value = volume,
            onValueChange = {
                volume = it.coerceIn(0f, 10f)
                onPreview(volume)
            },
            onValueChangeFinished = { onCommit(volume) },
            valueRange = 0f..10f
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presets.forEach { preset ->
                val selected = abs(preset - volume) < 0.001f
                Button(
                    onClick = {
                        volume = preset
                        onPreview(volume)
                        onCommit(volume)
                    },
                    modifier = Modifier.widthIn(min = 68.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) AppColors.Primary else Color(0xFF2D2D32),
                        contentColor = if (selected) Color.Black else Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(volumeLabel(preset), maxLines = 1, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    volume = 1f
                    onPreview(volume)
                    onReset()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Reset")
            }
            Button(
                onClick = { onDone(volume) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                Text("Done", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransformSheet(initialTransform: ClipTransform, onApply: (ClipTransform) -> Unit, onDismiss: () -> Unit) {
    var scale by remember(initialTransform) { mutableStateOf(initialTransform.scale.coerceIn(0.5f, 3f)) }
    var offsetX by remember(initialTransform) { mutableStateOf(initialTransform.offsetX.coerceIn(-160f, 160f)) }
    var offsetY by remember(initialTransform) { mutableStateOf(initialTransform.offsetY.coerceIn(-240f, 240f)) }
    var rotation by remember(initialTransform) { mutableStateOf(initialTransform.rotation.coerceIn(-180f, 180f)) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1B1B1F)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Transform", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TransformSlider("Scale", scale, 0.5f..3f) { scale = it }
            TransformSlider("Move X", offsetX, -160f..160f) { offsetX = it }
            TransformSlider("Move Y", offsetY, -240f..240f) { offsetY = it }
            TransformSlider("Rotate", rotation, -180f..180f) { rotation = it }
            Button(
                onClick = { onApply(ClipTransform(scale, offsetX, offsetY, rotation)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply")
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun TransformSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Text("$label ${value.roundToInt()}", color = AppColors.TextSecondary, fontSize = 12.sp)
    Slider(value = value, onValueChange = onValueChange, valueRange = range)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextOverlaySheet(onApply: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1B1B1F)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Add text", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter text") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AppColors.Primary,
                    unfocusedBorderColor = Color(0xFF56565C)
                )
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = { onApply(text) }, modifier = Modifier.fillMaxWidth(), enabled = text.isNotBlank()) {
                Text("Add")
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun PreviewArea(
    segment: TimelineSegment?,
    isPlaying: Boolean,
    globalTime: Long,
    totalMs: Long,
    onTogglePlay: () -> Unit
) {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (segment?.thumbnailUri != null) {
            AsyncImage(
                model              = segment.thumbnailUri,
                contentDescription = "Preview",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize()
            )
        }
        // Play/pause overlay
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center
        ) {
            Text(if (isPlaying) "Pause" else "Play", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        // Time overlay bottom-left
        Text(
            TimeFormatter.formatMs(globalTime),
            color    = Color.White,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
        )
    }
}

// â”€â”€ Clip tile â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun TimelineClipTile(
    clip: ClipUiModel,
    isCurrent: Boolean,
    onTap: () -> Unit
) {
    // Width proportional to duration but capped for readability
    val widthDp = (clip.durationMs / 1000f * PX_PER_SEC)
        .coerceIn(60f, 240f).dp

    Box(
        modifier = Modifier
            .size(width = widthDp, height = TRACK_H)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isCurrent) 2.dp else 0.dp,
                color = if (isCurrent) AppColors.Primary else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .background(if (isCurrent) AppColors.Primary.copy(alpha = 0.15f) else AppColors.Surface)
            .clickable(onClick = onTap)
    ) {
        if (clip.thumbnailUri != null) {
            AsyncImage(
                model              = clip.thumbnailUri,
                contentDescription = clip.label,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        }
        // Bottom label
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Column {
                Text(clip.label, color = Color.White, fontSize = 9.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(TimeFormatter.formatMs(clip.durationMs), color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp)
            }
        }
    }
}

// â”€â”€ Clip actions panel â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun ClipActionsPanel(
    clip: ClipUiModel,
    segment: TimelineSegment,
    onDelete: () -> Unit,
    onDupe: () -> Unit,
    onSetTransition: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = AppColors.Surface)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md)) {
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(clip.label, fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground, fontSize = 15.sp)
                    Text(TimeFormatter.formatMs(clip.durationMs), color = AppColors.TextSecondary, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Start: ${TimeFormatter.formatMs(segment.startMs)}", color = AppColors.TextSecondary, fontSize = 11.sp)
                    Text("End: ${TimeFormatter.formatMs(segment.endMs)}", color = AppColors.TextSecondary, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(AppSpacing.sm))
            // Transition row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceVariant)
                    .clickable(onClick = onSetTransition)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val type = clip.transition?.type ?: TransitionType.NONE
                val icon = if (type == TransitionType.NONE) "x" else capCutTransitionIcon(type)
                Text(icon, fontSize = 16.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Transition", color = AppColors.TextSecondary, fontSize = 11.sp)
                    Text(type.label, color = AppColors.OnBackground, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Text("Change", color = AppColors.Primary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(AppSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(
                    onClick  = onDupe,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, AppColors.SurfaceVariant)
                ) { Text("Duplicate", color = AppColors.OnBackground, fontSize = 13.sp) }
                OutlinedButton(
                    onClick  = onDelete,
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(8.dp),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Error.copy(alpha = 0.5f))
                ) { Text("Delete", color = AppColors.Error, fontSize = 13.sp) }
            }
        }
    }
}
