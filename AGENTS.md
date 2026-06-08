# AGENTS.md — ClipForge AI Lite

Operating guide for AI agents working in this repository. **Read this fully before
editing any file.** It defines who does what, which files are protected, and the
non-negotiable process gates (root-cause analysis, confidence scoring, Gradle
verification) that every change must pass through.

---

## 0. Project at a glance

- **App**: Android video editor (CapCut-style), `applicationId = com.clipforge.ai`
- **Language / UI**: Kotlin + Jetpack Compose (MVVM)
- **Video**: Media3 — `exoplayer` (preview), `transformer` + `effect` (export),
  `common`, `ui`
- **SDK**: `compileSdk 35`, `targetSdk 35`, `minSdk 26`
- **DI**: none — service locator via `ClipForgeApp` (cast `application as ClipForgeApp`)
- **Tests**: none yet. Do **not** write "tests pass" — there are none. State what you
  actually verified (compile, lint, manual trace) and what still needs on-device testing.
- **Build**: Gradle KTS, wrapper present (`gradlew.bat` on Windows)

### Module map (the files that matter)

| Area | Path |
|------|------|
| Timeline UI | `app/src/main/java/com/clipforge/ai/presentation/timeline/TimelineScreen.kt` (~4.2k LOC, god composable) |
| Timeline state | `app/src/main/java/com/clipforge/ai/presentation/timeline/TimelineViewModel.kt` (~2.1k LOC) |
| Export orchestration | `app/src/main/java/com/clipforge/ai/core/export/ExportManager.kt` |
| Export render engine | `app/src/main/java/com/clipforge/ai/core/gl/CrossfadeExecutor.kt` |
| Legacy/concat export | `app/src/main/java/com/clipforge/ai/core/gl/ProjectExporter.kt` (mostly dead — see §5) |
| Render plan | `app/src/main/java/com/clipforge/ai/core/gl/CrossfadeRenderPlan.kt` |
| Frame cache | `app/src/main/java/com/clipforge/ai/core/gl/CrossfadeFrameCache.kt` |
| Transition overlays | `core/gl/SlideOverlay.kt`, `ZoomOverlay.kt`, `DipToColorOverlay`, `CrossfadeBitmapOverlay` |
| Export UI | `presentation/export/ExportViewModel.kt`, `ExportProgressScreen.kt` |
| App / service locator | `ClipForgeApp.kt` |

---

## 1. Roles

### Claude — Principal Software Architect
Owns **design, decomposition, risk, and review**. Claude:
- Produces impact analysis, confidence scores, and root-cause write-ups **before** code
  is written.
- Decides *where* a change belongs and *how* to slice it (incremental over big-bang).
- Defines the contract/interface; reviews Codex's implementation against it.
- Is the gatekeeper for the protected files in §3 and §4 — a change to them does not
  proceed until Claude has signed off the impact analysis / validation plan.
- Does **not** dump large implementations into the protected god files; prefers
  extracting smaller, reviewable units.

### Codex — Senior Implementation Engineer
Owns **implementation within the agreed design**. Codex:
- Implements to the interface/plan Claude approved; does not redesign mid-task.
- Writes the mechanical, well-scoped diffs; keeps changes incremental.
- Runs Gradle verification (§6) and reports the actual result.
- Escalates back to Claude (does **not** improvise) when the plan doesn't fit reality,
  when a protected file needs changing, or when confidence drops below the §8 bar.

**Handoff rule**: Architecture → Implementation → Verification → Review. No step is
skipped. If Codex hits a protected file unexpectedly, it stops and returns to Claude.

---

## 2. Process gates (apply to every change)

1. **Root-cause analysis before any bug fix** (§7). No symptom-patching.
2. **Confidence score before implementation** (§8). Below threshold → investigate more.
3. **Impact analysis before touching `TimelineScreen.kt` / `TimelineViewModel.kt`** (§3).
4. **Export validation plan before touching `ExportManager.kt` / `ProjectExporter.kt` /
   `CrossfadeExecutor.kt`** (§4).
5. **Gradle verification after every change** (§6). Report the real outcome.
6. **CapCut UX** (§9) and **Google Play compliance** (§10) checks for user-facing work.
7. **Prefer incremental refactors** (§11) — never bundle a refactor with a feature/fix.

---

## 3. Protected: Timeline files — require impact analysis

**Never edit `TimelineScreen.kt` or `TimelineViewModel.kt` without a written impact
analysis first.** These are the largest, most coupled files in the app; a careless edit
ripples into playback, gestures, transitions, undo/redo, and recomposition cost.

Before editing, produce:

```
IMPACT ANALYSIS — <file>
- What changes:        <one-line description>
- Blast radius:        which composables / StateFlows / effects are affected
- Recomposition cost:  does this run during playback? per-frame? (check screenRecompositionCount paths)
- State boundary:      is this local Compose state or TimelineUiState/StateFlow? (don't mix)
- Undo/redo impact:    does this mutate history? (history is in-memory only — lost on process death)
- Player coupling:     does this touch ExoPlayer listeners / seek / sync?
- Extraction option:   can this go in a NEW smaller file instead of growing the god file? (preferred)
- Rollback:            how to revert cleanly
```

Hard rules:
- Do **not** grow `TimelineScreen.kt` further if the logic can live in a new extracted
  composable. The architect default is *extract, don't append*.
- Do **not** add `withContext(Dispatchers.IO)` around DAO calls in `TimelineViewModel`
  without checking Room's threading contract — undo/redo ordering assumes main-thread
  sequencing.
- A timeline transition-type change must update **both** the preview path
  (`previewTransitionVisualState()` in `TimelineScreen`) **and** the render path
  (`CrossfadeRenderPlan` op mapping). They are two sources of truth — keep them in sync.

---

## 4. Protected: Export files — require export validation

**Never edit `ExportManager.kt`, `ProjectExporter.kt`, or `CrossfadeExecutor.kt` without
an export validation plan, executed on a real device.** There are no automated tests;
this pipeline is validated by exporting on hardware and inspecting the result frame by
frame (see commit history: dissolve/dip work was approved this way).

Before editing, produce:

```
EXPORT VALIDATION PLAN
- Change:              <what and why>
- Pipeline stage:      plan (CrossfadeRenderPlan) | cache (CrossfadeFrameCache) | execute (CrossfadeExecutor) | publish (ExportManager)
- Device test matrix:  at least one real device; note manufacturer/model/SDK
- Visual checks:       colors correct, orientation correct, NO stray flashes / black frames, natural timing
- Duration check:      timeline duration unchanged (transition time is CONSUMED, not added)
- Audio check:         B-clip audio not clipped by overlay overlap
- Regression check:    confirm DISSOLVE and other untouched transitions still render
- MediaCodec fallback: note whether fallback=NO (fast path) or fell back to MediaMetadataRetriever
- MediaStore publish:  no orphaned / zero-byte gallery entries on failure path
```

Hard rules:
- `ProjectExporter.kt` is **legacy/dead** for the transition flow (its own comments admit
  trims/baked transitions are not applied). Do **not** extend it — the live path is
  `CrossfadeExecutor`. Only touch it to delete/quarantine, and only after Claude confirms
  no live references.
- Keep the `onStage(String)` progress callback contract intact when editing
  `CrossfadeExecutor.renderProjectTimeline` — `ExportManager` and the export UI depend on
  the stage strings.
- Hardcoded constants (cache FPS/bytes, deadlines, default 720p dims) are intentional
  pins — if you change one, call it out explicitly in the validation plan so it can be
  device-retuned.

---

## 5. Legacy / dead code

`ProjectExporter.kt` is retained for reference but is **not** the export engine. When you
see it in a grep for "export", the real engine is `core/export/ExportManager.kt` →
`core/gl/CrossfadeExecutor.kt`. Do not build new features on `ProjectExporter`.

---

## 6. Gradle verification — required after every change

Run after **every** code change and report the actual result (success or failure output),
not an assumption.

Windows (this repo's primary environment):

```powershell
.\gradlew.bat assembleDebug          # compile the app
.\gradlew.bat lintDebug              # lint
```

Faster inner loop while iterating:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Rules:
- If the build fails, **stop** — do not proceed to the next change. Fix or escalate.
- Never report "BUILD SUCCESSFUL" you did not actually observe.
- Do not skip verification because "it's a one-line change."

---

## 7. Root-cause analysis before bug fixes

No fix is written until the cause is understood. Required write-up:

```
ROOT CAUSE
- Symptom:        what the user/observer sees
- Reproduction:   exact steps / conditions
- Mechanism:      the actual code path that produces it (file:line)
- Why now:        what introduced or exposed it
- Fix options:    >=2 options with trade-offs
- Chosen fix:     which option and why it addresses the CAUSE, not the symptom
- Side effects:   what else the fix touches
```

Symptom-patching (try/catch swallowing, clamping a value to hide an overflow, sleeping to
dodge a race) is rejected unless the root cause is documented and the patch is explicitly
labeled as a stopgap with a follow-up.

---

## 8. Confidence score before implementation

State a confidence score (0–100%) before writing code, with the reasoning:

```
CONFIDENCE: <n>%
- Understanding of the cause/requirement: ...
- Coverage of edge cases: ...
- Risk to protected files (§3/§4): ...
- Unknowns that could invalidate the approach: ...
```

Thresholds:
- **≥ 85%** — proceed to implementation.
- **60–84%** — proceed only for low-risk, non-protected files; for protected files,
  investigate further first.
- **< 60%** — do not implement. Gather more context (read code, trace the path, ask the
  user a targeted question).

---

## 9. CapCut UX patterns

ClipForge mirrors CapCut's editing model. Match these expectations:
- Playhead-centered timeline; pinch-to-zoom on the track; clips trimmed by dragging edge
  handles, split at the playhead.
- Transitions live **between** clips and **consume** clip time (they do not extend total
  duration).
- Contextual bottom sheets/toolbars per selection (speed, volume, transform, text,
  transitions) — not modal dialogs.
- Immediate, smooth preview feedback; transitions previewed in-place before export.
- Smoothstep easing for transitions (`t*t*(3-2f*t)`) — the established "soft, CapCut-like"
  feel. Reuse it; don't invent new easing without reason.
- Non-destructive edits; undo/redo always available.

When adding UI, ask: "What does CapCut do here?" and follow that affordance unless the
user says otherwise.

---

## 10. Google Play policy compliance

Every user-facing or data-touching change must stay compliant:
- **Permissions**: request the minimum; use scoped storage / `MediaStore` for gallery
  output (already the pattern — keep it). No broad legacy storage permissions.
- **Data safety**: don't add new data collection / network egress without flagging it for
  the Play Data Safety form. Auth/Supabase calls must respect existing token handling.
- **Ads & billing**: AdManager / BillingManager / Entitlements changes must follow Play
  Billing and Families/Ads policies; no dark patterns around paywalls or consent.
- **Content**: watermark/quality gating is policy- and monetization-relevant — don't
  silently remove it.
- **Background work / foreground services**: export is user-initiated; keep it that way
  and respect `targetSdk 35` foreground-service and notification requirements.

If a change has any policy dimension, note it in the handoff so it can be reviewed before
release.

---

## 11. Incremental refactors

- One concern per change. **Never** bundle a refactor with a feature or bug fix.
- Decompose the god files (`TimelineScreen.kt`, `TimelineViewModel.kt`) by **extracting**
  small, named, independently reviewable units — not by rewriting in place.
- Each refactor step must compile and keep behavior identical (verify via §6); land it,
  then take the next step.
- Centralizing magic numbers, unifying the duplicated transition definitions, and
  persisting undo/redo are all *separate* incremental tracks — don't merge them.

---

## 12. Worked examples

Each example shows the expected agent flow for a representative task.

### 12.1 Timeline — "Add a 'snap clip to playhead' gesture"
1. **Claude (impact analysis, §3)**: touches `TimelineScreen` gesture handling +
   `TimelineViewModel` clip layout; runs during drag (recomposition-sensitive); affects
   undo/redo (a snap is an edit → must `recordHistory`). Decision: extract the gesture
   into a new `TimelineSnapGesture` composable rather than growing `TimelineScreen`.
2. **Claude (confidence, §8)**: e.g. 88% — proceed.
3. **Codex**: implement the extracted composable + a `snapClipToPlayhead()` VM method that
   records history; wire it in. Keep snap state in `TimelineUiState`, not local `remember`.
4. **Verify (§6)** `assembleDebug`; **CapCut check (§9)**: snapping with haptic-style
   visual feedback like CapCut. On-device manual test of drag + undo.

### 12.2 Transitions — "Add a 'Wipe' transition"
1. **Claude**: a transition spans preview **and** render paths. Plan: add `Op.Wipe` to
   `CrossfadeRenderPlan` (+ type strings), a `WipeOverlay` in `core/gl` (reuse
   `CrossfadeFrameCache`, smoothstep easing §9), an executor arm in `CrossfadeExecutor`,
   **and** the matching `previewTransitionVisualState()` entry in `TimelineScreen`
   (the two-sources-of-truth rule, §3).
2. **Export validation plan (§4)** required because `CrossfadeExecutor` changes.
3. **Codex**: implement all four touch-points in one cohesive change; preserve the
   `onStage` callback (emit `"Preparing wipe transition..."`).
4. **Verify**: build, then on-device export — colors/orientation/no flashes, duration
   unchanged, DISSOLVE regression-checked, audio intact, note MediaCodec fallback state.

### 12.3 Export — "Add 1080p export option"
1. **Claude**: `ExportManagerState.quality`/`hasWatermark` already exist but are unwired.
   Plan: surface a quality picker in export UI → pass through `ExportManager` →
   `CrossfadeExecutor.outputDimensions()`. Protected files (`ExportManager`,
   `CrossfadeExecutor`) → **export validation plan (§4)**.
2. **Play compliance (§10)**: confirm watermark/quality gating still respects entitlement.
3. **Codex**: thread the quality enum end-to-end; don't change the default-on-parse-failure
   fallback (720×1280) without flagging it.
4. **Verify**: build + on-device export at 720p (regression) **and** 1080p; check output
   dims, file size, MediaStore publish has no orphaned entries.

### 12.4 Text editing — "Add a text-style preset row"
1. **Claude**: text sheet lives in `TimelineScreen` (god file). Decision: extract a
   `TextStyleSheet` composable; **impact analysis (§3)** for the wiring touch-point.
2. **CapCut UX (§9)**: bottom-sheet preset chips, live preview on the canvas.
3. **Codex**: build the extracted sheet; persist style via the VM (records history).
4. **Verify**: build; on-device — preview updates live, undo reverts the style.

### 12.5 Audio editing — "Add per-clip volume fade-in/out"
1. **Claude (root cause / requirement, §7-style)**: clarify whether fade is a render-time
   effect (Media3 audio processor in `CrossfadeExecutor`) or a preview-only gain ramp.
   This is both → protected export file → **export validation plan (§4)** + **timeline
   impact analysis (§3)** for the preview/VM side.
2. **Codex**: add the audio effect to the export item chain; add the volume-curve state to
   the VM; preview applies the same ramp in `VideoPreviewPlayer`.
3. **Verify**: build; on-device export — listen for correct fade and no clipping at clip
   boundaries; confirm preview matches export.

### 12.6 Preview player — "Fix: preview freezes after a transition during playback"
1. **Claude (root cause, §7)**: trace `VideoPreviewPlayer` ExoPlayer listener →
   `onMediaItemTransition` / `syncPlaybackFromPlayer`. Identify the actual mechanism
   (e.g. stale captured state via `rememberUpdatedState`, or a seek race), at `file:line`.
   Document mechanism + ≥2 fix options.
2. **Impact analysis (§3)**: `TimelineScreen` (player composable) + possibly VM sync — note
   recomposition/threading (ExoPlayer callbacks are main-thread via `Player.Listener`).
3. **Confidence (§8)**: only implement at ≥85% understanding of the cause.
4. **Codex**: apply the cause-addressing fix (not a `try/catch` band-aid).
5. **Verify**: build; on-device — play across multiple transitions repeatedly, confirm no
   freeze, seek still accurate, no main-thread jank.

---

## 13. Quick checklist (paste into your working notes)

- [ ] Root cause documented (if a bug) — §7
- [ ] Confidence score stated, ≥ threshold — §8
- [ ] Impact analysis done (if Timeline files) — §3
- [ ] Export validation plan done + device-tested (if export files) — §4
- [ ] Change is incremental, single-concern — §11
- [ ] CapCut UX honored (if user-facing) — §9
- [ ] Play policy checked (if permissions/data/ads/billing/content) — §10
- [ ] Gradle verification run, real result reported — §6
- [ ] Preview ≡ Export parity confirmed (if it affects rendering)
- [ ] No automated tests claimed — manual/on-device verification stated explicitly
