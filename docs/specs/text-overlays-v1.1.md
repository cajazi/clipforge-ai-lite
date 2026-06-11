# Text Overlays v1.1 — Codex Implementation Specification

**Status**: FINAL (supersedes v1.0)
**Workflow**: Claude = architecture/specification/review. Codex = implementation/debugging/testing/commits.
**Feature**: Real CapCut-style text overlays with preview/export parity, designed as the foundation for Caption Timeline, Auto Captions, Stickers, Watermark, and Text Animations.

**Changelog vs v1.0**: pipeline components renamed and retyped to an overlay-generic contract (A1); rasterizer input is a value object (A2); frame/transform accessors are time-parametric (A3); export stage takes composed sources with explicit user/system layering (A4); time mapping promoted to a named shared component (A5); preview/export stacking order is a contract with a parity test (A6); guardrail G11 added.

---

## Scope contract — frozen surfaces

This is an **additive** feature. The following are FROZEN SURFACES — any diff in them is out of scope and must be reverted:

- `app/src/main/java/com/clipforge/ai/core/gl/CrossfadeRenderPlan.kt`
- `app/src/main/java/com/clipforge/ai/core/transition/TransitionRegistrations.kt`
- `app/src/main/java/com/clipforge/ai/core/transition/renderers/OverlayTransitionRenderers.kt`
- `app/src/main/java/com/clipforge/ai/core/transition/OverlayRendererParityHarness.kt`
- All transition GL effect files (`CubeGlEffect.kt`, `FlipGlEffect.kt`, `PageTurnGlEffect.kt`, `FlashOverlay.kt`, etc.)
- `CrossfadeExecutor.kt` item-building, plan, dispatch, and `runningTimeMs` logic
- Transition timing semantics, `TimelineViewModel`, MediaStore/gallery publishing

Exactly **one region** of `CrossfadeExecutor.kt` changes: the composition-level `outputEffects` construction (~line 1062, where `Presentation` is applied via `Composition.Builder(...).setEffects(...)`), plus read-only derivation of inputs to the time map beside it.

---

## 0. Architecture in one paragraph

A `TextOverlay` is persisted per project in its own Room table, positioned in **normalized frame coordinates** and **timeline milliseconds**. It is rendered through an overlay-generic contract — `RenderableOverlay` — whose text implementation rasterizes once via `StaticLayout`/`Canvas` and serves **the same bitmap** to the Compose preview and to a Media3 `BitmapOverlay` in export. At export, all renderables from all sources join **one `OverlayEffect` appended after `Presentation`** in the existing composition-level effects, with timeline-to-composition time conversion handled by one shared `TimelineToCompositionTimeMap`. Future overlay features are new `RenderableOverlay`/`OverlaySource` implementations — not new pipelines.

---

## 1. Core contracts (amendments A1, A2, A3, A4 — implement first)

```kotlin
// core/overlay/ — no Media3 imports, no domain entities (see G11)
enum class OverlayLayer { USER, SYSTEM }          // SYSTEM always renders above USER

data class OverlayTransform(
    val xNorm: Float, val yNorm: Float,            // overlay CENTER, [0..1], origin top-left, y down
    val scale: Float, val rotationDeg: Float, val alpha: Float
)

interface RenderableOverlay {
    val id: String
    val windowStartMs: Long                        // TIMELINE time (see section 6)
    val windowEndMs: Long
    val layer: OverlayLayer
    val zIndex: Int                                // ordering within USER layer
    fun transformAt(progress: Float): OverlayTransform        // progress = window-local [0..1]
    fun frameAt(progress: Float, frameW: Int, frameH: Int): Bitmap
}

interface OverlaySource {
    suspend fun load(projectId: String): List<RenderableOverlay>
}
```

Rules baked into the contract:

- **A3 — time-parametric by signature, constant by implementation**: `transformAt`/`frameAt` take `progress` from day one. The v1 text implementation returns constants (ignores `progress`) — but every consumer must call through these functions per frame and never cache their results across time. Text animations and word-by-word caption highlighting later become new implementations of these two functions with zero consumer changes.
- **A2 — rasterizer input is a value object**: `TextOverlayRasterizer.rasterize(spec: TextRenderSpec, frameW: Int, frameH: Int): Bitmap` where `TextRenderSpec` carries text, fontId, fontSizeNorm, colorArgb, bgColorArgb?, bold, italic, alignment, and `highlightRange: IntRange? = null` (reserved for word highlighting; unused in v1). The rasterizer never sees a domain entity. Captions later resolve `styleId -> preset -> TextRenderSpec` into the same rasterizer.
- The v1 text implementation `TextOverlayRenderable` (in `core/text`) adapts entity -> `TextRenderSpec` -> rasterizer, and is the **only** class that knows both worlds.
- **A1 — overlay-generic pipeline**: the export stage, the Media3 bitmap overlay, and the Compose preview layer depend on `RenderableOverlay` only. Stickers, captions, and watermark become new implementations, not new pipelines.
- **A4 — composed sources**: the export stage takes a list of `OverlaySource`s. v1 passes `[TextOverlaySource]`. The Phase-1 watermark later adds `[WatermarkSource(entitlements)]` with zero stage changes.

---

## 2. Exact files impacted

### New files

| File | Contents |
|---|---|
| `core/overlay/RenderableOverlay.kt` | Contracts above (`RenderableOverlay`, `OverlayTransform`, `OverlayLayer`, `OverlaySource`) |
| `core/overlay/TimelineToCompositionTimeMap.kt` | A5 — shared time mapping (section 6) |
| `core/overlay/OverlayExportStage.kt` | A4 — `build(sources, timeMap, frameW, frameH): OverlayEffect?` |
| `core/gl/RenderableBitmapOverlay.kt` | Media3 `BitmapOverlay` driven by a `RenderableOverlay` (`@UnstableApi`) |
| `core/text/TextRenderSpec.kt` | A2 value object |
| `core/text/TextOverlayRasterizer.kt` | Rasterize-once engine with LRU bitmap cache |
| `core/text/TextOverlayRenderable.kt` | v1 `RenderableOverlay` implementation (entity adapter) |
| `domain/model/TextOverlay.kt` | Domain model (section 3) |
| `data/local/entity/TextOverlayEntity.kt` | Room entity |
| `data/local/dao/TextOverlayDao.kt` | CRUD + `observeForProject(projectId): Flow<List<...>>` |
| `domain/repository/TextOverlayRepository.kt` | Interface |
| `data/repository/TextOverlayRepositoryImpl.kt` | Pattern: `TransitionRepositoryImpl` |
| `data/repository/TextOverlaySource.kt` | `OverlaySource` implementation: repo -> `TextOverlayRenderable` list |
| `presentation/timeline/TextOverlayLane.kt` | Lane chips + selection (text-specific UI is fine — lanes are intentionally not generalized) |

### Modified files

| File | Change | Blast radius |
|---|---|---|
| `data/local/database/ClipForgeDatabase.kt` + `core/utils/Constants.kt` | +entity/+DAO accessor, `DB_VERSION`+1, **explicit `Migration`** | G1 |
| `core/gl/CrossfadeExecutor.kt` | Only the `outputEffects` construction (~line 1062): build sources list + time map, append stage output after `Presentation` | G2/G3 |
| `presentation/text/TextOverlayViewModel.kt` / `TextOverlayScreen.kt` | Wire the existing `addToTimeline()` TODO to the repository; add timing/transform state | UI stub today |
| `presentation/timeline/TimelineScreen.kt` | Overlay preview layer (stacking per section 5) + lane + drag/scale/rotate gestures | Compose-only; transition preview branches untouched |
| `presentation/preview/PreviewScreen.kt` / `PreviewViewModel.kt` | Same overlay layer for full-screen preview | — |
| DI wiring | Provide DAO/repo/source | Follow existing repository wiring pattern |

`TimelineViewModel`: **untouched** (standing rule). New state flows live in a new `TextOverlayTrackViewModel` (or activate the `presentation/overlays/OverlayViewModel` scaffold).

---

## 3. Model and persistence

```
TextOverlay(
  id: String,                  // UUID
  projectId: String,
  text: String,
  startMs: Long, endMs: Long,  // TIMELINE time (preview player coordinates) — see section 6
  xNorm: Float, yNorm: Float,  // overlay CENTER, [0..1], origin top-left of frame
  scale: Float,                // 1.0 = rasterized size
  rotationDeg: Float,
  fontId: String,              // bundled fonts only in v1 (G7)
  fontSizeNorm: Float,         // fraction of frame HEIGHT (e.g. 0.05) — never px/sp
  colorArgb: Int, bgColorArgb: Int?,  // bg nullable = none
  bold: Boolean, italic: Boolean,
  alignment: TextAlignment,    // LEFT|CENTER|RIGHT
  zIndex: Int
)
```

- Entity mirrors the model 1:1 with queryable columns (no JSON blobs). Table `text_overlays`, indexed on `projectId`, FK -> project with `onDelete = CASCADE`.
- **Why `fontSizeNorm` and `xNorm/yNorm`**: every size/position is relative to frame dimensions so the preview viewport (arbitrary dp) and export frame (e.g. 1280x720) compute identical placement. Pixels appear only inside the rasterizer.
- Future overlay types get **their own tables** (captions, stickers). Unification happens at `RenderableOverlay`, not the schema.
- No re-anchoring on clip edits in v1: overlays keep absolute timeline time; an overlay past project end is clamped at export and visually flagged in the lane.
- Repository exposes `observe(projectId)`, `upsert`, `delete`. Export reads a one-shot snapshot via the same repository.

---

## 4. Preview rendering architecture

1. Rasterizer: `StaticLayout` at `fontSizeNorm x frameH` text size, padding, optional background rounded rect. LRU cache (~8 MB cap) keyed by hash of (`TextRenderSpec`, frameW, frameH). Transform (position/scale/rotation) is **never** baked into the bitmap (A3) — transform-only edits are cache hits.
2. Compose layer: for each renderable where `playerPositionMs` is in `[windowStartMs, windowEndMs)`, compute window-local `progress`, call `transformAt(progress)` / `frameAt(progress, viewportW, viewportH)`, draw via `Image` + `graphicsLayer` (translation from normalized center x viewport; scaleX/Y; rotationZ; alpha).
3. Position source: `PreviewPlayerManager`'s existing position ticker. No player changes.
4. Edit gestures (drag/pinch/rotate on the selected overlay) update normalized fields through the ViewModel with debounced upsert; the rasterizer cache is untouched by transform-only edits.

---

## 5. Stacking order — THE PARITY CONTRACT (A6)

One ordering, enforced identically on both sides:

```
Player surface
  -> transition preview layers (outgoing/incoming clips AND the
     overlayColor plane used by dip/flash/page-turn previews)
  -> user overlay layer, by zIndex ascending
  -> system/watermark overlay layer
  -> UI chrome (selection gizmos, lane handles)
```

- **Preview**: the overlay Compose layer is composed **above** the transition preview overlay Box *including its full-screen `overlayColor` plane* (TimelineScreen.kt ~line 990). Getting this wrong makes text vanish under a dip in preview while remaining visible in export — a guaranteed parity bug (device test 9.3b exists to catch it).
- **Export**: composition effects list order = `Presentation` -> user+system `OverlayEffect` (stage output, internally ordered USER-by-zIndex then SYSTEM). Composition-level effects apply after all item effects, so text naturally sits above transition visuals — matching the preview contract above.
- The SYSTEM layer is empty in v1. The Phase-1 watermark later becomes a `WatermarkSource` returning one `SYSTEM` renderable — no stage, preview, or ordering changes.

---

## 6. Time mapping — `TimelineToCompositionTimeMap` (A5)

The preview player plays clips at full length; the export composition is **shorter**, because every overlap transition consumes its duration (the AB matrix's 4533 ms vs 5000 ms is exactly this). Every overlay window must be converted:

```
exportTimeMs(t) = t - SUM(consumedMs(b)) for every boundary b fully before t;
                  inside a transition window, clamp into that window
                  (two timeline-seconds compress into the transition's
                   composition window)
```

- Build the map **from the executor's already-built op list** (PlainClip + transition op durations). Dip (`FADE_BLACK`/`FADE_WHITE`) boundaries consume zero net composition time and fall out automatically when the map is derived from op durations rather than family assumptions.
- It lives in `core/overlay` as a standalone pure class with its own unit suite — it will be reused verbatim by captions, stickers, watermark, and later the audio engine. **Do not** modify `CrossfadeRenderPlan` to produce it.
- `OverlayExportStage` applies it to every renderable's window. **No raw timeline ms ever reaches a Media3 overlay** (G4).

---

## 7. Export rendering architecture

1. `RenderableBitmapOverlay(renderable, windowStartUs/EndUs (composition time), frameW, frameH)`:
   - `getBitmap(ptsUs)`: inside window -> `renderable.frameAt(progress, frameW, frameH)` (export-size raster, export-owned instances — G9); outside -> last/placeholder bitmap (never null — G5).
   - `getOverlaySettings(ptsUs)`: inside -> from `transformAt(progress)`; outside -> alpha 0.
   - **NDC conversion (G6 — the known sign trap)**: the model is top-left-origin y-down; Media3 anchors are center-origin **y-up**. Exact mapping: `bgAnchorX = xNorm*2 - 1`, `bgAnchorY = 1 - yNorm*2`, `overlayFrameAnchor = (0,0)` (bitmap centered on that point).
   - Rotation via `StaticOverlaySettings` if the API supports it at the current Media3 version; otherwise the v1 fallback may bake rotation into the rasterized bitmap **behind the `transformAt` seam only**, with a `TODO(WP-1.2 Media3 bump)` — the fallback is animation-hostile and must not outlive the bump.
2. `OverlayExportStage.build(sources, timeMap, frameW, frameH): OverlayEffect?` — loads all sources, orders per section 5, maps windows per section 6, wraps each renderable in a `RenderableBitmapOverlay`, returns **null when empty** (zero-overlay export must be byte-identical to today — validation 8.7).
3. **Attach point** — the only executor edit, where `outputEffects` is built (~line 1062):
   - Today: `Effects(emptyList(), listOf(Presentation...))`
   - After: `Effects(emptyList(), listOf(Presentation..., stageEffect))` — stage output **after** `Presentation` so overlay coordinates are in final output space (G3).
   - No changes to items, plan, dispatch, caches, or `runningTimeMs`.

---

## 8. Validation checklist (Codex must run all before commit)

1. **Unit — time map**: no transitions; overlap boundary before/inside/after the overlay window; dip boundary (zero consumption); multiple boundaries; window spanning a boundary; clamp past project end.
2. **Unit — NDC conversion**: all four corners + center map to expected NDC; rotation sign.
3. **Unit — contracts (A3 regression trap)**: `transformAt`/`frameAt` are called through the interface per frame by both the Compose layer and `RenderableBitmapOverlay` — verify with a counting fake invoked at progress 0.0 and 0.9.
4. **Rasterizer**: deterministic output for the same key; cache hit on transform-only change; multiline + emoji do not crash `StaticLayout`.
5. **DB migration (G1)**: instrumented Room migration test from the current `DB_VERSION` with an existing project — **the project survives**.
6. **AB regression**: full transition matrix (pinned synthetic seed) with **zero overlays** — all cases pass, and durations byte-match the pre-change baseline. This is the proof the transition system is undamaged.
7. **Zero-overlay path**: `OverlayExportStage.build` returns null and the effects list is identical to today's (log-assert list size).
8. **New instrumented export test** (extend the AB harness pattern): project with 2 clips + 1 overlap transition + 1 text overlay spanning the boundary -> export completes; extract frames inside the overlay window and assert non-background pixels at the expected normalized region; extract a frame outside the window and assert absence.
9. **G11 mechanical check**: `grep -r "TextOverlay" app/src/main/java/com/clipforge/ai/core/gl app/src/main/java/com/clipforge/ai/core/overlay` returns **zero hits** (the adapter lives in `core/text`; data sources in `data/`).
10. `assembleDebug` + full androidTest compile green.

---

## 9. Real-device testing checklist (SM-A165F)

| # | Scenario | Pass criterion |
|---|---|---|
| 1 | Lower-third overlay on a plain clip | Position/size visually identical preview vs exported file |
| 2 | Overlay spanning an overlap transition (e.g. CUBE_LEFT) | Text stays static while clips transition beneath; appears/disappears at the same content moments in preview and export |
| 3a | Overlay spanning FADE_BLACK — **export** | Text visible above the black plane at the dip's darkest frame |
| 3b | Same — **preview** (A6 twin) | Scrub the preview through the dip: text visible above the preview `overlayColor` plane at its darkest frame |
| 4 | Two overlays overlapping in time, different zIndex | Stacking order identical preview/export |
| 5 | Rotation 30 degrees + scale 1.8 | Identical placement both sides |
| 6 | Emoji + 3-line text + background pill | Renders, no clipping, parity holds |
| 7 | Export with 0 overlays | Output duration/bytes match pre-feature baseline run |
| 8 | 20 overlays across a 60 s project | Export completes; wall-time within ~10% of baseline; `TEXT_OV` logs healthy; no OOM |
| 9 | App process death mid-edit, reopen | Overlays restored from Room |

---

## 10. Guardrails for Codex (G1-G11)

- **G1 — Database wipe hazard (CRITICAL)**: `ClipForgeDatabase` uses `fallbackToDestructiveMigration()`. Bumping `DB_VERSION` without an explicit `Migration` **silently deletes every user's projects**. Add `Migration(N, N+1)` (CREATE TABLE for `text_overlays`) so the destructive fallback never fires. Do not remove the fallback in this change; do not skip the migration test (8.5).
- **G2 — Frozen surfaces**: any diff in the files listed under "Scope contract" is out of scope — revert it. Permitted executor changes: the `outputEffects` region and read-only inputs to the time map.
- **G3 — Effects order**: stage output **after** `Presentation` in the composition effects list; within the stage, USER by zIndex ascending, then SYSTEM. Mirrors section 5 exactly. Wrong order = mispositioned text at non-source resolutions.
- **G4 — Time mapping**: never pass raw timeline ms into any Media3 overlay window. Everything goes through `TimelineToCompositionTimeMap`. The unit tests in 8.1 are non-negotiable.
- **G5 — Idle overlay cost**: outside its window an overlay must return alpha 0 *and* still return a real (small) bitmap — never null, never recycle while the transformer may still sample (proven pattern: `FlashRevealOverlay`).
- **G6 — Y-axis sign**: NDC conversion per section 7.1. Verify with device scenario 9.1 before building further UI on top. Same trap class the cube and page-turn families hit.
- **G7 — Fonts**: bundled assets only in v1; `fontId` resolves through a static registry with a guaranteed default. No dynamic font download in this slice.
- **G8 — UnstableApi**: annotate new Media3-touching classes `@UnstableApi` consistently with neighbors; do not suppress at module level.
- **G9 — Bitmap lifecycle**: preview bitmaps belong to the shared LRU cache (never recycled by consumers); export rasterizes its own instances at export size and releases them in `release()` — matching every transition overlay's lifecycle.
- **G10 — Logging discipline**: `TEXT_OV` tag with CREATE / sampled settings / released-with-counts log lines, mirroring the house style (`CUBE_OV`, `FLASH_COLOR_OV`), so device validation is log-verifiable.
- **G11 — Layer purity (the architecture enforcer)**: the words `TextOverlay` must not appear in `core/gl` or `core/overlay`, and the export stage's public signatures speak only `RenderableOverlay`/`OverlaySource`/`OverlayTransform`/`Bitmap`. If the entity is being passed below the ViewModel/adapter layer, stop and re-read section 1. Enforced mechanically by check 8.9.

---

## Codex implementation rules

1. Implement section 1 contracts first; everything else depends on them.
2. Build order: contracts -> persistence (+ migration + test 8.5) -> rasterizer -> time map (+ tests 8.1) -> export stage + executor attach -> preview layer -> lane UI -> gestures.
3. Run validation checklist section 8 in full before commit; the AB byte-match (8.6) and the G11 grep (8.9) are hard gates.
4. Device checklist section 9 scenarios 1-7 (including both 3a and 3b) verified on the SM-A165F before the feature is declared done.
5. Commit in reviewable slices (contracts/persistence, render pipeline, UI) — never one monolithic commit.
6. If any step appears to require touching a frozen surface, stop and escalate to Claude review instead of proceeding.

**Definition of done**: section 8 fully green, section 9 scenarios 1-7 verified on device, no diffs on frozen surfaces, migration test proves data survival.
