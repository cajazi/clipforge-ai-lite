# ClipForge AI Transition Family Validation Log

## Slide Family Archive

Status: Archived

Device:
- SM-A165F

Validated transitions:
- SLIDE_LEFT
- SLIDE_RIGHT
- SLIDE_UP
- SLIDE_DOWN

Runtime validation:
- cachePass=true
- usagePass=true
- deltaPass=true
- EXPORT_COMPLETE

Result:
- Real exported MP4 transitions completed successfully.
- CapCut-style motion validated.
- Play Store safe.
- Memory bounded.
- Export stable.

## Zoom Family Milestone

Status: In runtime validation

Target transitions:
- ZOOM_IN
- ZOOM_OUT

Implementation scope:
- Exported MP4 transitions only.
- Media3 OverlayEffect pipeline.
- Bounded CrossfadeFrameCache profile shared with Slide.
- Release-time validation logging includes cachePass, usagePass, and deltaPass.

Runtime validation required:
- ZOOM_IN: complete
- ZOOM_OUT: cachePass=true, usagePass=true, deltaPass=true, EXPORT_COMPLETE

### ZOOM_IN Runtime Validation

Device:
- SM-A165F

Runtime validation:
- cachePass=true
- usagePass=true
- deltaPass=true
- coverage=100%
- EXPORT_COMPLETE
- memory approximately 20MB

Result:
- Real exported MP4 transition completed successfully.
- No regression target for ZOOM_OUT implementation.

## Export Gallery Save Milestone

Status: Implementation ready for physical-device runtime validation

Required behavior after EXPORT_COMPLETE:
- Copy/publish internal exported MP4 to MediaStore.
- Save under Movies/ClipForge AI.
- Surface MediaStore publicUri to export UI state.
- Show success UI with Saved to Gallery, View video, Share, and Done.

Play Store safety:
- No READ_EXTERNAL_STORAGE permission.
- No WRITE_EXTERNAL_STORAGE permission.
- No READ_MEDIA_VIDEO permission.
- Android 10+ uses MediaStore RELATIVE_PATH and IS_PENDING.

Runtime logs to verify:
- EXPORT_MEDIASTORE_SAVE_BEFORE
- EXPORT_MEDIASTORE_SAVE_AFTER uri=...
- EXPORT_MEDIASTORE_SAVE_FAILED error=...
- EXPORT_GALLERY_VISIBLE=true/false

## Preview Timeline Sync Milestone

Status: Implementation ready for physical-device runtime validation

Fix scope:
- Project open routes now enter the CapCut timeline editor instead of the legacy editor placeholder surface.
- Legacy editor route also renders the timeline editor to avoid stale "Add media" preview states.
- Preview chooses current clip, selected clip, then first timeline clip before showing an empty state.
- Selecting a clip syncs playhead to that clip and pauses playback for an immediate preview frame update.
- Media3 preview playlist binding seeks to the active clip/playhead after timeline restore.

Runtime logs to verify:
- PREVIEW_SOURCE_SET
- PREVIEW_PROJECT_RESTORED
- PREVIEW_TIMELINE_BOUND
- PREVIEW_PLAYHEAD_SYNC

## CapCut UI And Split Behavior Pass

Status: Implementation ready for physical-device visual validation

Fix scope:
- Removed visible mojibake/corrupted toolbar glyphs from the timeline editor UI.
- Adjusted preview, transport, timeline, and toolbar heights closer to the CapCut reference layout.
- Toolbar tools now use stable ASCII labels to avoid broken on-device rendering.
- Split now performs an immediate playhead split, keeps the boundary under the playhead, selects the new right clip, pauses playback, and stays in the edit toolbar.

Runtime validation:
- UI should no longer show corrupted symbols such as "â..." in toolbar, transport, or transition controls.
- Split should create left and right clips at the playhead without entering split-adjust mode.
- Right-side clip should be selected after split.
- Preview/playhead should remain synced at the split boundary.

## CapCut Transition Marker UI Pass

Status: Implementation ready for physical-device visual validation

Fix scope:
- Removed the large fixed-width transition buttons previously inserted between timeline clips.
- Transition controls now render as compact boundary overlays on the outgoing clip.
- Existing transitions remain visible as small markers; empty add-transition markers appear only on the selected clip boundary.
- Markers no longer increase timeline row height or push adjacent clips apart.

Runtime validation:
- Timeline clip spacing should remain continuous with no large blue buttons between clips.
- Compact transition markers should be tappable at clip boundaries when visible.
- Timeline row height should remain stable before and after selecting a clip.

## CapCut Split Behavior Equivalence Pass

Status: Implementation ready for physical-device runtime validation

Fix scope:
- Split now records SPLIT_BEFORE and SPLIT_AFTER around the DAO/UI mutation boundary.
- Split preserves the exact pre-split playhead timestamp and logs PLAYHEAD_POSITION.
- Split updates the immediate UI clip/segment model so preview remains bound to the same source frame.
- Existing end-of-clip transitions move to the right split piece, preventing a new transition at the internal split boundary.
- Timeline rebuilds caused by split, undo, or redo request scroll preservation instead of auto-aligning to the playhead.

Runtime logs to verify:
- SPLIT_BEFORE
- SPLIT_AFTER
- PLAYHEAD_POSITION
- PREVIEW_SYNC
- TIMELINE_SYNC

Regression boundary:
- Export engine unchanged.
- Slide transition renderer unchanged.
- Zoom transition renderer unchanged.
- MediaStore gallery-save flow unchanged.
- Project reopen preview restore path unchanged.

## CapCut Left Trim Behavior Pass

Status: Implementation ready for physical-device runtime validation

Fix scope:
- Left trim keeps a separate visual edge offset from the media trim window.
- Left trim updates trimStart/sourceStart only; right trim updates trimEnd/sourceEnd only.
- Timeline thumbnails are keyed by trimStart/trimEnd and extracted from the visible trim window.
- Preview sync logs now report the source frame used for left/right trim.
- Playhead clamps only when a left trim moves past the current playhead.
- Trim commit preserves timeline scroll and clears trim session state.

Runtime logs to verify:
- TRIM_LEFT_START
- TRIM_LEFT_UPDATE
- TRIM_LEFT_COMMIT
- TRIM_RIGHT_START
- TRIM_RIGHT_UPDATE
- TRIM_RIGHT_COMMIT
- TRIM_PREVIEW_SYNC
- TRIM_PLAYHEAD_CLAMP

Regression boundary:
- Export engine unchanged.
- Transition engine unchanged.
- MediaStore gallery-save flow unchanged.
- Split behavior unchanged.
- Project reopen preview restore path unchanged.

## CapCut Shared Split Boundary Trim Pass

Status: Implementation ready for physical-device runtime validation

Fix scope:
- Same-source split boundaries are selectable through a compact boundary marker.
- Selected split boundaries enter dedicated shared-boundary trim mode.
- Left boundary handle updates only the left clip trimEnd/sourceEnd.
- Right boundary handle updates only the right clip trimStart/sourceStart.
- Boundary preview sync reports the adjusted source frame.
- Boundary commit writes both adjacent clips and keeps timeline adjacency normalized.
- Boundary trim records history for undo/redo.

Runtime logs to verify:
- SPLIT_BOUNDARY_SELECTED
- BOUNDARY_TRIM_START
- BOUNDARY_TRIM_UPDATE
- BOUNDARY_TRIM_COMMIT
- BOUNDARY_TRIM_CANCEL
- BOUNDARY_JOIN_CONTINUITY_CHECK
- BOUNDARY_PREVIEW_SYNC
- BOUNDARY_PLAYHEAD_SYNC

Regression boundary:
- Export engine unchanged.
- Transition engine unchanged.
- MediaStore gallery-save flow unchanged.
- Normal split behavior unchanged.
- Normal single-clip trim behavior unchanged.
- Project reopen preview restore path unchanged.

## True Boundary Trim Routing Fix

Status: Implementation ready for physical-device runtime validation

Root cause:
- Seam taps still routed into normal clip-edge trim because clip trim mode was enabled solely by selectedClipId.
- The seam marker used a lower z-index than normal trim handles, so trim handles won the gesture when a selected clip touched the seam.
- Boundary selection used SPLIT_ADJUST state, while the screen predicates still rendered normal trim handles for selected clips.

Fix scope:
- Added explicit BOUNDARY_TRIM interaction mode.
- Boundary selection now clears selectedClipId and logs BOUNDARY_SELECTED.
- Normal CapCutTrimHandle rendering is gated to EditorInteractionMode.TRIM only.
- Same-source seam markers render above normal trim handles so seam taps enter BoundaryTrimMode.
- Boundary drags now log BOUNDARY_LEFT_UPDATE or BOUNDARY_RIGHT_UPDATE instead of LEFT_TRIM/RIGHT_TRIM paths.

Runtime logs to verify:
- BOUNDARY_SELECTED
- BOUNDARY_TRIM_START
- BOUNDARY_LEFT_UPDATE
- BOUNDARY_RIGHT_UPDATE
- BOUNDARY_TRIM_COMMIT
- BOUNDARY_TRIM_CANCEL

Regression boundary:
- Export engine unchanged.
- Transition engine unchanged.
- MediaStore gallery-save flow unchanged.
- Preview renderer unchanged.
- Normal split behavior unchanged.
- Normal single-clip trim behavior remains isolated to EditorInteractionMode.TRIM.

## Timeline Zero-Gap Adjacency Pass

Status: Implementation ready for physical-device runtime validation

Fix scope:
- Timeline clip widths now derive from deterministic rounded pixel boundaries:
  startPx = round(timelineStartMs * pxPerMs), endPx = round(timelineEndMs * pxPerMs), widthPx = max(1, endPx - startPx).
- Adjacent split clips use shared pixel boundaries so nextStartPx equals previousEndPx.
- UI clip-bound normalization logs before/after adjacency.
- Primary timeline normalization logs before/after adjacency and fixes detected gaps.
- Transition markers and handles remain overlays and do not insert row spacing.

Runtime logs to verify:
- TIMELINE_ADJACENCY_NORMALIZE_BEFORE
- TIMELINE_ADJACENCY_NORMALIZE_AFTER
- TIMELINE_GAP_DETECTED
- TIMELINE_GAP_FIXED
- TIMELINE_PIXEL_GAP_CHECK
- BOUNDARY_JOIN_CONTINUITY_CHECK

Regression boundary:
- Export engine unchanged.
- Transition engine unchanged.
- MediaStore gallery-save flow unchanged.
- Preview restore unchanged.
- Normal split behavior unchanged.
- Normal single-clip trim behavior unchanged.

## CapCut Timeline UX Parity Pass

Status: Implementation ready for physical-device visual validation

Root cause:
- Timeline visual weight came from oversized side rail controls, the large sticky add button, thick selected borders, heavy trim handles, edge scrims, and a taller clip row.
- Perceived split gaps came from heavy white handles/selection overlays plus fractional clip widths rather than only model-level adjacency.
- The plus insertion control and side actions were visually competing with the timeline clips and playhead.

Fix scope:
- Reduced side rail width and shortcut scale.
- Reduced sticky add button from oversized 50dp/34dp icon to compact 36dp/24dp icon.
- Reduced timeline clip row height and clip tile height.
- Reduced selected clip outline to 1dp with lower alpha.
- Reduced trim handle visual thickness and overlay weight.
- Reduced transition marker offset/size so it no longer dominates seams.
- Increased thumbnail density by reducing filmstrip tile width.

Runtime logs to verify:
- TIMELINE_UI_AUDIT
- TIMELINE_GAP_CHECK
- TIMELINE_DENSITY_CHECK
- TIMELINE_SELECTION_CHECK
- TIMELINE_CONTROL_SIZE_CHECK

Before/after validation:
- Timeline gap check: adjacent clip gapPx should remain 0 in TIMELINE_GAP_CHECK.
- Selected clip check: selected outline is 1dp and does not add layout spacing.
- Plus button size check: add clip button is compact and does not increase timeline height.
- Trim handle size check: trim handles overlay edges with slim 5dp visual handles.
- Timeline density check: clip row is denser, with 34dp filmstrip tiles.

Regression boundary:
- Export engine unchanged.
- Media3 export pipeline unchanged.
- Transition engine unchanged.
- MediaStore gallery-save flow unchanged.
- Preview renderer unchanged.
- Preview synchronization unchanged.
- Split logic unchanged.
- Trim logic unchanged.
- Boundary trim logic unchanged.

## CapCut Timeline Visual Audit Pass 2

Status: Implementation ready for physical-device side-by-side validation

Root cause:
- Remaining parity mismatch came from non-functional visual weight: boundary handles, seam markers, plus insertion controls, left rail width, clip row height, and thumbnail density still occupied more visual space than the CapCut reference.
- Timeline adjacency was already zero-gap capable, but selected overlays and edge affordances made seams visually louder than the media strip.

Fix scope:
- Compressed left timeline rail to 58dp to return horizontal space to clips.
- Reduced clip row to 50dp and thumbnail media height to 46dp.
- Increased thumbnail density with 30dp filmstrip tiles.
- Reduced sticky plus insertion control to 30dp with a 20dp icon.
- Reduced trim handle visual width to 3dp and boundary seam visual width to 1dp.
- Lowered selection, trim, and boundary overlay opacity so media remains the primary visual object.
- Reduced transition/seam marker footprint to 12-14dp and kept markers overlaid on clip edges.

Validation logs:
- TIMELINE_UI_AUDIT
- TIMELINE_GAP_CHECK
- TIMELINE_DENSITY_CHECK
- TIMELINE_SELECTION_CHECK
- TIMELINE_CONTROL_SIZE_CHECK

Build verification:
- .\gradlew.bat assembleDebug passed.

Regression boundary:
- Export engine unchanged.
- Media3 export pipeline unchanged.
- Transition engine unchanged.
- MediaStore/Gallery unchanged.
- Split logic unchanged.
- Trim logic unchanged.
- Boundary trim logic unchanged.
- Preview renderer unchanged.
- Preview synchronization unchanged.

Confidence: 0.89 pending final physical-device screenshot comparison against the CapCut reference.

## CapCut Timeline Final Polish Pass

Status: Implementation ready for physical-device side-by-side validation

Root cause:
- The timeline still read as separate boxes because per-clip outlines, internal tile dividers, seam edge lines, and selection opacity were visually stronger than the media thumbnails.
- Transition insertion controls stayed dominant because marker bubbles had high contrast and occupied too much of the clip edge.
- The left rail occupied more than the CapCut reference because its width, shortcut tiles, cover tile, and typography were still oversized.
- Thumbnail continuity differed because the filmstrip tiles were too wide and had dark dividers that made each thumbnail read as a separate card.

Fix scope:
- Compressed the left timeline rail to 50dp and reduced rail controls, cover tile, typography, and vertical spacing.
- Reduced clip strip row to 48dp and media height to 44dp.
- Increased thumbnail continuity with 26dp tiles, seven-frame sampling, and lower-contrast internal dividers.
- Reduced selected, trim, split, and boundary overlay alpha.
- Reduced seam edge contrast so adjacent clips read as a continuous strip.
- Reduced transition marker size to 10-12dp with lower contrast and opacity.
- Reduced sticky add button to 28dp with an 18dp icon and lower visual contrast.
- Reduced visible trim handles to 2dp and shared-boundary seam visibility to a subtle 1dp overlay.

Validation logs:
- TIMELINE_UI_AUDIT continuousStrip=true
- TIMELINE_GAP_CHECK
- TIMELINE_DENSITY_CHECK
- TIMELINE_SELECTION_CHECK
- TIMELINE_CONTROL_SIZE_CHECK

Build verification:
- .\gradlew.bat assembleDebug passed.

Regression boundary:
- Export engine unchanged.
- Media3 export pipeline unchanged.
- Transition engine unchanged.
- MediaStore/Gallery unchanged.
- Preview renderer unchanged.
- Preview synchronization unchanged.
- Split logic unchanged.
- Trim logic unchanged.
- Boundary trim logic unchanged.

Confidence: 0.91 pending final physical-device screenshot comparison against the CapCut reference.

## CapCut Timeline 95 Percent Parity Polish Pass

Status: Implementation ready for physical-device side-by-side validation

Root cause:
- ClipForge still read as separate boxes because low-level UI chrome remained too visible: per-clip edge lines, thumbnail dividers, selection opacity, and seam affordances competed with the media thumbnails.
- Transition insertion controls remained noticeable because the marker still used a bright circular shape with enough size and contrast to pull attention away from the strip.
- The left rail consumed more space than CapCut because its fixed width, cover tile, shortcut tiles, and text spacing were still scaled above the reference.
- Thumbnail continuity differed because 26dp frame tiles and visible dark separators made the strip read as repeated cells instead of continuous media.
- Timeline density was lower because row height, panel height, rail footprint, and add-track lane height still spent extra pixels on chrome.

Before/after reasoning:
- Before: 50dp rail, 48dp row, 44dp media strip, 26dp tiles, 10-12dp transition markers, and stronger seam opacity.
- After: 46dp rail, 46dp row, 42dp media strip, 24dp tiles, 8-10dp transition markers, lower edge/seam contrast, and quieter selection/handle opacity.
- Result: Preview and playhead remain visually dominant, timeline media becomes the next strongest element, and insertion/trim controls become secondary.

Fix scope:
- Reduced left rail width, padding, typography, cover tile, shortcut tile size, and vertical spacing.
- Reduced timeline panel and clip row height to return vertical space to media.
- Reduced clip media height while increasing thumbnail density.
- Increased timeline frame sampling from seven to eight frames for a smoother filmstrip.
- Reduced clip edge lines and internal thumbnail dividers to near-background contrast.
- Reduced transition marker size, contrast, and offset so it behaves like a CapCut insertion point instead of a primary button.
- Reduced sticky add button size and icon contrast.
- Reduced visible trim and boundary handle opacity while preserving gesture hit areas and behavior.

Validation logs:
- TIMELINE_UI_AUDIT boxedAppearance=false continuousStrip=true
- TIMELINE_GAP_CHECK
- TIMELINE_DENSITY_CHECK
- TIMELINE_SELECTION_CHECK
- TIMELINE_CONTROL_SIZE_CHECK

Build verification:
- .\gradlew.bat assembleDebug passed.

Regression boundary:
- Export engine unchanged.
- Media3 export pipeline unchanged.
- Transition engine unchanged.
- MediaStore/Gallery unchanged.
- Preview renderer unchanged.
- Preview synchronization unchanged.
- Split logic unchanged.
- Trim logic unchanged.
- Boundary trim logic unchanged.

Confidence: 0.95 for implementation readiness; final percentage should be confirmed with physical-device screenshots against the CapCut reference.

## CapCut Timeline Production Polish Completion

Status: Production-ready UI polish pass complete

Final focus:
- Left rail compactness.
- Timeline vertical density.
- Thumbnail continuity.

Root cause:
- The final screenshot delta was no longer timeline behavior. It was visual footprint: the left rail still used too much horizontal space, the clip strip had slightly less media area than CapCut, and thumbnail separators still made the strip read as segmented.

Fix scope:
- Reduced left rail from 46dp to 40dp.
- Reduced rail padding, cover tile, shortcut tiles, and label typography.
- Reduced sticky add padding from 52dp to 48dp.
- Reduced timeline panel from 268dp to 256dp.
- Set clip row and clip media height to 44dp so the strip occupies the row instead of floating inside it.
- Increased thumbnail continuity with 20dp tiles and nine-frame sampling.
- Reduced clip edge and thumbnail divider alpha to near-background levels.
- Reduced add button to 24dp with a 14dp icon and lower opacity.

Build verification:
- .\gradlew.bat assembleDebug passed.

Protected areas unchanged:
- Export engine.
- Media3 export pipeline.
- Preview renderer.
- Preview synchronization.
- Split logic.
- Trim logic.
- Boundary trim logic.
- Transition engine.
- MediaStore/Gallery.

Confidence: 0.96. Timeline UX polish is considered complete and production ready pending only final device screenshot approval.

## CapCut Preview Player Behavior Pass

Status: Implementation ready for physical-device validation

Root cause:
- Preview scrubbing could lag because paused preview sync used a debounce and a broad seek-skip threshold.
- Playback controls were static instead of CapCut-style reveal/hide controls on the preview surface.
- Preview zoom was tied only to clip transform state and did not support direct pinch-to-zoom or double-tap fit gestures.

Fix scope:
- Reduced preview seek skip threshold to frame-level timing.
- Removed paused scrub debounce so playhead drag requests preview frames immediately.
- Reduced trim preview seek throttle to frame-level timing.
- Added tap preview to reveal/hide playback controls.
- Added auto-hide playback controls while playing.
- Added pinch-to-zoom preview with bounded pan.
- Added double-tap preview to fit/reset zoom.
- Preserved aspect ratio by scaling X/Y together.
- Kept PlayerView shutter transparent and keep-content behavior unchanged to avoid preview flashing.

Validation logs:
- PREVIEW_SCRUB_FRAME_SYNC
- PREVIEW_PLAYHEAD_SYNC
- PREVIEW_CONTROLS_TOGGLE
- PREVIEW_CONTROLS_AUTO_HIDE
- PREVIEW_GESTURE_ZOOM
- PREVIEW_DOUBLE_TAP_FIT
- PREVIEW_PLAYBACK_TOGGLE

Build verification:
- .\gradlew.bat assembleDebug passed.

Protected areas unchanged:
- Export engine.
- Timeline editing model.
- Split logic.
- Trim logic.
- Boundary trim logic.
- Transition engine.

Confidence: 0.92 pending device validation for gesture feel and scrub latency.

## Split Button Click Routing Regression Fix

Status: Implementation ready for physical-device validation

Root cause:
- The preview/player gesture patch added new touch handling on the preview surface, but the toolbar path had no explicit hit-area or dispatch logging to prove the bottom toolbar remained isolated from preview gestures.
- The Split button route still mapped to the existing split handler, so this fix is limited to click routing, z-order guardrails, enabled-state visibility, and verification logs.

Fix scope:
- Scoped preview gesture logging to the 210dp 9:16 preview surface.
- Explicitly kept the preview layer below the bottom toolbar in z-order.
- Raised the bottom toolbar surface z-order.
- Added Split enabled-state logging based on selected clip and playhead-in-clip state.
- Added bottom toolbar click receipt logging.
- Added Split tap and dispatch logging before calling the existing ViewModel handler.
- Added ViewModel-side Split dispatch logging before the existing split algorithm.

Validation logs:
- SPLIT_BUTTON_TAP
- SPLIT_BUTTON_ENABLED_STATE
- SPLIT_ACTION_DISPATCHED
- PREVIEW_GESTURE_HIT_AREA
- BOTTOM_TOOLBAR_CLICK_RECEIVED

Build verification:
- .\gradlew.bat assembleDebug passed.

Protected areas unchanged:
- Export engine.
- Media3 export pipeline.
- Transition engine.
- MediaStore/Gallery.
- Trim logic.
- Boundary trim logic.
- Existing split algorithm.

Confidence: 0.94 pending physical-device tap validation.

## Split Button Enabled-State Follow-Up

Status: Implementation ready for physical-device validation

Root cause:
- The previous click-routing fix still over-gated the Split button with selectedClipId != null and strict playhead-inside bounds.
- At clip start, or when the active clip existed but selectedClipId was temporarily null, the button could render disabled even though the existing split handler could resolve the active clip.

Fix scope:
- Split button enabled state now follows the selected or active split candidate clip.
- Playhead-inside status remains logged for diagnostics, but it no longer disables the Split button prematurely.
- Existing split algorithm and edit behavior remain unchanged.

Validation logs:
- SPLIT_BUTTON_ENABLED_STATE candidateClipId=...
- SPLIT_BUTTON_TAP candidateClipId=...
- SPLIT_ACTION_DISPATCHED

Build verification:
- .\gradlew.bat assembleDebug passed.

Protected areas unchanged:
- Existing split algorithm.
- Trim logic.
- Boundary trim logic.
- Transition engine.
- Export engine.
- MediaStore/Gallery.

Confidence: 0.96 pending physical-device tap validation.

## CapCut Timeline Visual Refinement Pass

Status: Implementation ready for physical-device visual validation

Root cause:
- Boundary handles and seam markers still read as dominant white blocks instead of subtle CapCut edge affordances.
- Left toolbar still consumed too much horizontal space, reducing visible timeline media.
- Plus controls remained more visually prominent than secondary actions in the reference.
- Timeline density remained lower because clip height and filmstrip tile width were still too large.

Fix scope:
- Reduced left toolbar width to return more horizontal space to timeline clips.
- Reduced cover/track shortcut controls and typography.
- Reduced clip height, timeline row height, and overall timeline panel height.
- Reduced plus button and icon scale again.
- Reduced seam marker size, opacity, and offset.
- Reduced selected/boundary outline alpha.
- Reduced trim handle gesture width and visible handle thickness.
- Reduced shared boundary handle visible line to a subtle 1dp overlay.
- Increased thumbnail density with smaller filmstrip tiles.

Validation focus:
- Boundary handles should no longer dominate the strip.
- Split seams should read as continuous media with subtle edge affordances.
- Plus insertion actions should feel secondary.
- Left toolbar should match CapCut proportions more closely.
- Timeline viewport should show more media and less empty/control weight.

Regression boundary:
- Export engine unchanged.
- Media3 export pipeline unchanged.
- Transition engine unchanged.
- MediaStore/Gallery unchanged.
- Split logic unchanged.
- Trim logic unchanged.
- Boundary trim logic unchanged.
