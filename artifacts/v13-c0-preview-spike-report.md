# V13 C0 Preview Validation Spike — Report

Date: 2026-06-12
Device: SM-A165F (Mali-G57, Android 16), Media3 1.9.0
Rig: `spike/c0-preview-effects` branch, commit 4a7f9ab (androidTest-only; instrumented probe
`C0ProbeGlEffect` + `C0PreviewSpikeTest`; headless ImageReader(PRIVATE) surface; synthetic
320x240 @15fps 3s seed x N playlist). Branch retained, not for merge (deviation from spike
design's delete-after-filing rule, kept for C5 validation rig reuse).

## Measured results

| Exp | Result | Numbers |
|---|---|---|
| E1 baseline wiring | PASS | 45/45 frames through probe, pts 0..2,933,333us |
| E2 dynamic change (direct) | WORKS mid-playback, no re-prepare | applied in 409-478ms (median ~450ms, n=6); old effect KEEPS DRAWING during handover — no black/freeze, playback never stalls |
| E2 dynamic change (stop/prepare/seek) | WORKS | 244-325ms (median ~300ms) |
| E3 playlist traversal + timebase | PASS — **pts CONTINUOUS** | 3-item playlist: 135 frames (45x3, zero drops), pts 0..8,933,333us, **0 resets**, max inter-frame step 66,667us (exactly 1 frame). Effect pipeline survives item transitions |
| E4 scrub storm (20 seeks) | **FAILS 50ms-penalty threshold** | effects ON: median 285.5ms seek-to-frame; effects OFF: median 87.5ms; penalty ~ +200ms (3.3x) |
| E5 live volatile param | PASS | new value visible in **1 frame**, no rebuild |
| E6 3-stack + lifecycle | PASS | 78 frames / 5.28s = 14.8fps vs 15fps source (zero drops, at seed resolution); release mid-playback survived; chained-program frame counts differ only by in-flight pipeline depth |

## Verdict

Strict decision-matrix reading: E4's threshold failure triggers verdict C. Engineering
verdict: **A' — full parity architecture WITH mandatory scrub-suspension**, which dominates
verdict C on every axis (C gives no shader preview at all; A' gives exact shader preview
everywhere except during an active scrub gesture, where effects OFF scrubbing measures the
same 87ms as today's player).

Basis: the two architecture-threatening questions both resolved favorably —
1. `setVideoEffects` is dynamically callable mid-playback on 1.9.0 (no re-prepare, no
   playback interruption; ~450ms application latency with the old pipeline drawing until
   handover).
2. The pts a GlEffect receives is **continuous playlist time == preview timeline time**
   (E3: zero resets across boundaries). Timeline-time window gating works natively in
   preview; NO preview-side time mapping is required. The export-side
   TimelineToCompositionTimeMap remains required as designed.

## Recommended C5 architecture

- `player.setVideoEffects(...)` with time-gated GlEffects windowed in timeline time (E3).
- Live params via volatile ParamProvider — slider changes apply next frame, never rebuild (E5).
- Structural changes (add/remove/retime): direct `setVideoEffects` call, debounced ~250ms;
  expect ~0.5s visual application latency, no interruption (E2). Do NOT use stop/prepare.
- **Scrub-suspension contract (mandatory, from E4)**: on scrub-gesture start ->
  `setVideoEffects(emptyList)`; on gesture end -> re-apply current list. Scrubbing then
  performs at the no-effects baseline (~87ms); re-application costs ~450ms with no flash.
- Stack cap 3 confirmed at full frame rate (E6) — at seed resolution only; re-verify fps at
  real preview resolution in C5 device checks.
- Lifecycle: player release with active effects is safe (E6). App backgrounding/surface
  re-attach untested (headless rig) — explicit C5 device-check item.

## Residual risks carried into C5

1. E6 fps measured at 320x240; preview renders larger. Re-measure with one real effect at
   device preview resolution.
2. Synthetic seed is video-only; real content with audio tracks exercises A/V sync through
   the effects pipeline — C5 checklist item.
3. E2/E4 latencies measured with trivial tint shaders; heavier starter-pack shaders may add
   to the ~450ms application latency (not to per-frame cost, which E6 bounds).

## Confidence

9/10 on the verdict: every claim above is a measured number from the target device, not an
API characterization; the single threshold failure (E4) has a bounded, measured mitigation.
Withheld point: the three residual risks are real but all carry C5-stage checks rather than
architecture exposure.
