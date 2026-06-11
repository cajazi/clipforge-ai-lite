# Transition Pack V10 — Glitch Pro Family: Codex Implementation Specification

**Status**: FINAL design spec — Phase E Step 12
**Workflow**: Claude = architecture/design/review. Codex = implementation/validation/commits.
**Transitions**: `GLITCH_PRO`, `GLITCH_DIGITAL`, `GLITCH_RGB`, `GLITCH_SCANLINE`
**Baseline**: HEAD 416574f / transition-pack-v9. AB seed pinned. Registry dispatch validated. Legacy executor path preserved.

**Scope contract**: additive transition family on the established extension points. No text-overlay work (`docs/specs/text-overlays-v1.1.md` stays unimplemented). No MediaStore, export-timing, or `TimelineViewModel` changes. Registry dispatch and legacy executor path both extended, neither restructured.

---

## 1. CapCut behavior analysis (source of truth)

CapCut glitch transitions are **burst-driven digital corruption** concealing the cut at maximum corruption — not a smooth effect ramp:

- **Temporal structure**: corruption arrives in discrete **bursts** — the image is clean or near-clean between bursts, and burst density/amplitude ramps toward the midpoint, peaks, then decays. Stable within a burst, discontinuous between bursts. The stepped, quantized timing is what makes it read as "glitch" rather than "wobble."
- **The cut**: A to B is a hard swap at the midpoint, landing **inside a guaranteed burst** — displacement is maximal exactly when the content changes, so the swap reads as one more corruption event.
- **Four sub-looks** decompose into four corruption primitives:
  - **GLITCH_PRO** — flagship mix: horizontal slice displacement + RGB split + occasional block corruption + light scanlines.
  - **GLITCH_DIGITAL** — datamosh/compression-artifact look: coarse quantized blocks shifted and color-corrupted, mild slice/RGB, no scanlines, color posterization inside bursts.
  - **GLITCH_RGB** — channel separation dominant: R/B sampled at opposite UV offsets that spike per burst and snap back; minimal geometry displacement.
  - **GLITCH_SCANLINE** — CRT sync-loss: per-row phase jitter, thin dark scanlines, a rolling displaced band traversing the frame; subtle RGB fringe.
- **No full-frame luminance flashes** — corruption is geometric/chromatic (see GG-7).

## 2. Architecture decision

**Shader-only, dual-texture + noise LUT — the validated FilmBurn architecture.** `core/gl/FilmBurnGlEffect.kt` is the line-for-line template: B-head frame cache on texture unit 1 (`updateBTexture` pattern), CPU-baked tileable noise bitmap on unit 2 (`ensureNoiseTexture()`), mode as a parameterized enum (`FilmBurnMode` pattern, FilmBurnGlEffect.kt:16).

**One shader, four primitives, mode = weight vector.** New file `core/gl/GlitchProGlEffect.kt`: `GlitchProGlEffect` + `GlitchProShaderProgram` + `GlitchMode(...)` enum whose constructor carries all weights/constants. Fragment-shader structure:

```glsl
// burst clock: quantized progress => frame-stable, deterministic seeds
float tBurst   = floor(uProgress * uBurstRate) / uBurstRate;
float envelope = 1.0 - abs(uProgress * 2.0 - 1.0);            // triangular, peak at 0.5
float gate     = step(1.0 - (envelope * uBurstDensity),
                      lutNoise(vec2(tBurst, 0.37)));           // burst on/off
float amp      = envelope * gate;                              // 0 between bursts

// P1 slice displacement: rows of height uSliceHeight, seeded x-offset
// P2 block corruption: quantized uv cells, seeded shift + color damage
// P3 RGB split: sample R at uv - rgbOff, B at uv + rgbOff (3 taps)
// P4 scanline: per-row phase jitter + dark line modulation + rolling band

vec2 uvA = corruptedUv(uv, amp);                               // P1+P2+P4 weighted, clamped to [0,1]
vec4 base = (uProgress < 0.5) ? sampleSplit(uTexSampler, uvA, amp)   // P3 inside sampler
                              : sampleSplit(uBTexSampler, uvA, amp);
```

Hard requirements:

1. **Determinism**: all randomness from the noise LUT indexed by `tBurst` and quantized coordinates — never wall-clock, never device random. Identical input produces identical output every run (AB stability, preview/export agreement).
2. **Guaranteed midpoint burst**: force `gate = 1` for `uProgress` in `[0.46, 0.54]` so the swap always lands inside corruption. This is the concealment contract (analogous to the flash family's peak plateau).
3. **UV clamping**: every displaced coordinate clamps to [0,1] — no wrap-around sampling.
4. **Mode = weights**: no shader permutations; the four modes set uniforms only.

### Mode parameter table (starting values)

| Uniform | PRO | DIGITAL | RGB | SCANLINE | Notes |
|---|---|---|---|---|---|
| `wSlice` (P1) | 0.80 | 0.30 | 0.20 | 0.30 | slice offset amplitude; max displacement = `wSlice x 0.12` of width |
| `wBlock` (P2) | 0.40 | 1.00 | 0.00 | 0.00 | block corruption probability weight |
| `wRgb` (P3) | 0.60 | 0.30 | 1.00 | 0.25 | max channel offset = `wRgb x 0.025` UV |
| `wScan` (P4) | 0.20 | 0.00 | 0.00 | 1.00 | row jitter + line darkening + rolling band |
| `uBurstRate` | 12.0 | 9.0 | 10.0 | 14.0 | bursts per window (500 ms => 4-7 visible bursts after gating) |
| `uBurstDensity` | 0.65 | 0.55 | 0.60 | 0.75 | fraction of burst slots active at peak envelope |
| `uSliceHeight` | 1/14 | 1/8 | 1/20 | 1/96 | slice/row height, fraction of frame height (SCANLINE = per-line) |
| `uBlockSize` | 1/16 | 1/10 | — | — | block cell size, fraction of frame width |
| `uPosterize` | 0.0 | 6.0 | 0.0 | 0.0 | color levels inside bursts (0 = off) |

All values normalized to frame dimensions (resolution-independent). Tuning iterations are expected; constants live only in the `GlitchMode` enum constructor (GG-9).

## 3. Legacy upgrade decision (requires product sign-off; separable commit)

`GLITCH`, `RGB_SPLIT`, and `CHROMATIC_ABERRATION` already exist as picker-visible, plain-cut-on-export `TransitionType`s — the exact pre-flash situation.

**Recommendation (Option A — the precedent shipped four times: cube, flip, flash, page turn)**: upgrade via mapping only —

- `GLITCH -> glitch_pro`
- `RGB_SPLIT -> glitch_rgb`
- `CHROMATIC_ABERRATION -> glitch_rgb`

(Multi-type-to-one-id is precedented by `DISSOLVE`/`CROSS_DISSOLVE` -> `DISSOLVE`.)

Consequences are identical to the flash upgrade: legacy projects gain the effect and lose the overlap duration on re-export. The upgrade is exactly three `idFor` + three `forType` lines and **must be its own commit** (GG-8) so product can accept or revert it independently. The four new types ship regardless of this decision.

## 4. File impact list (film burn 12-file template)

| File | Change |
|---|---|
| **NEW** `core/gl/GlitchProGlEffect.kt` | Effect + shader program + `GlitchMode` enum with the section-2 parameter table; three-texture lifecycle cloned from `FilmBurnGlEffect` (`updateBTexture`, `ensureNoiseTexture`, `release`); `GLITCH_PRO` log tag in house style |
| `domain/model/Transition.kt` | + `GLITCH_PRO("Glitch Pro")`, `GLITCH_DIGITAL("Glitch Digital")`, `GLITCH_RGB("Glitch RGB")`, `GLITCH_SCANLINE("Glitch Scanline")` |
| `core/transition/TransitionSpec.kt` | `Family.GlitchPro`, `GlitchMode`, four specs, `forType` mappings (+3 legacy lines in the separable commit) |
| `core/transition/TransitionRegistrations.kt` | Ids `glitch_pro/digital/rgb/scanline`; `idFor` (+legacy); `GlitchProTransitionRenderer`; four `reg(...)` — **`TransitionCategory.GLITCH`** (first use of this category), `TimingModel.Overlap`, stage `"Preparing glitch transition..."` |
| `core/gl/CrossfadeRenderPlan.kt` | `GLITCH_PRO_TYPES` set (4 new, +3 legacy in separable commit); `Op.GlitchPro(mode)`; full new-family checklist — arrays, classification branch, `boundaryConsumption`, clamp-set, disable-after-clamp, **both** overlap-membership lines, op emission, describe/dump log lines |
| `core/gl/CrossfadeExecutor.kt` | Registry `Dispatch` (param `MODE`) + legacy executor branch cloned from the film burn branch + `glitchModeFor` + `describeOp` |
| `core/transition/renderers/OverlayTransitionRenderers.kt` | `GlitchProTransitionRenderer` (slide-profile cache, single item, no `OverlayEffect`) |
| `core/transition/OverlayRendererParityHarness.kt` | `Op.GlitchPro` case + `glitchProId()` |
| `presentation/timeline/TimelineScreen.kt` | **All six sites**: preview branch (section 6); `capCutTransitionIcon` (`GP`/`GD`/`GR`/`GS`) + `transitionCardBrush` (join existing GLITCH gradient group — compiler-enforced); both `CapCutTransitionPanel` lists; cosmetic `TransitionPreviewOverlay` list |
| `presentation/timeline/TransitionPickerSheet.kt` | Badges + `GLITCH_TYPES` list (joins existing `GLITCH`, `RGB_SPLIT`, `CHROMATIC_ABERRATION`) |
| `androidTest .../TransitionExportAbValidationTest.kt` | 4 cases (matrix 41 -> 45); +2 legacy cases (`GLITCH`, `RGB_SPLIT`) in the separable upgrade commit |
| `core/transition/renderers/OverlayRenderSupport.kt` | Only if a new param key were needed — reuse `MODE`; expected zero-change |

## 5. Render-plan / export behavior

Standard overlap family: A-tail re-rendered as the glitch item, B head from `trimStartMs` via the frame cache, `occupiedMs = durationMs`, expected AB duration identical to every overlap row against the pinned seed. Export timing rules untouched. Both dispatch paths emit the identical single item. The hard swap at `uProgress = 0.5` happens **inside the shader** (B sampled from the cache after midpoint) — the same single-item concealment model as flash/film burn, so there is no plan-level novelty.

## 6. Preview behavior

Compose cannot displace UVs, so the preview is an honest approximation built from existing machinery, **deterministic from progress** (pure function — no `Random`):

- Quantize progress with the same `tBurst` formula; derive a pseudo-seed via a hash of `tBurst`; during active bursts apply small `translationX/Y` jitter (<= 4% of width) to the visible layer and brief `overlayColor` tints (cyan/magenta, alpha <= 0.12) for the RGB feel; hard A-to-B alpha swap at p = 0.5; clean image between bursts.
- Per-mode flavor: DIGITAL adds a slight scale snap; SCANLINE biases jitter to Y; RGB biases to color tints over geometry.
- `forType` mappings are mandatory (the silent `else -> PlainCut` trap).

## 7. Validation matrix

| Check | Mechanism |
|---|---|
| 4 new AB cases (+2 legacy if upgrade lands) — COMPLETED, gallery-visible, uniform overlap duration | hardened AB harness, pinned synthetic seed |
| Full-matrix regression (45 rows) — film burn rows specifically gate the shared three-texture path | same run |
| Legacy<->registry parity for `Op.GlitchPro` | `OverlayRendererParityHarness` |
| Determinism: export the same project twice, byte-compare glitch-segment frames | frame extract + hash |
| Midpoint concealment: frame-step +/-3 frames around p = 0.5 — swap occurs within an active burst | pulled export |
| Burst character: discrete bursts with clean frames between, no continuous wobble | manual visual per mode |
| Edge sampling: no wrapped/smeared frame edges at max displacement | manual visual |
| Per-mode look vs CapCut reference (4 modes) | manual side-by-side |
| Export wall-time within ~10% of film burn baseline | run timing |

## 8. SM-A165F device checklist

| # | Scenario | Pass criterion |
|---|---|---|
| 1 | Each mode exported on the AB seed project | All 4 complete; logs show mode, burst params, three-texture upload/release counts |
| 2 | Double-export determinism (GLITCH_PRO) | Glitch-window frames byte-identical across runs |
| 3 | Midpoint frame-step (all 4 modes) | Swap concealed inside an active burst |
| 4 | Edge inspection at max displacement | Clamped edges, no wrap-around smear |
| 5 | Preview scrub vs export side-by-side | Burst cadence comparable; hard swap at same content moment |
| 6 | Picker + CapCut panel | 4 new tiles visible in Glitch category in BOTH surfaces |
| 7 | Memory across full 45-case matrix | No GPU memory creep (noise + B textures released per case) |
| 8 | Legacy upgrade commit (if approved) | GLITCH project re-export gains effect; duration delta matches documentation |

## 9. Guardrails for Codex (GG-1 through GG-9)

- **GG-1 — Frozen surfaces**: existing transition families, executor item/plan/dispatch logic, export timing, MediaStore publishing, `TimelineViewModel`. New-family code joins at the named extension points only; any other diff -> revert.
- **GG-2 — Determinism is a hard requirement**: no `Random`, no wall-clock, no uninitialized LUT reads. The byte-compare double-export is a hard gate. If bursts differ run-to-run, the implementation is wrong even if it looks right.
- **GG-3 — UV clamp**: every displaced sample clamps to [0,1]. Edge behavior is where shaders fail on device — history: cube/page-turn sign traps.
- **GG-4 — Mediump precision**: all randomness via the noise LUT (film burn precedent — zero hash functions in GLSL); quantized cell math in `highp` where the platform honors it.
- **GG-5 — Three-texture lifecycle**: clone `ensureNoiseTexture`/`release` from `FilmBurnGlEffect` verbatim. Texture leak = GPU memory creep across the 45-case matrix; watch released-with-counts logs.
- **GG-6 — Non-compiler-enforced UI lists**: both `CapCutTransitionPanel` lists and picker `GLITCH_TYPES` fail silently if missed (documented Step 8 failure mode). Six TimelineScreen sites total.
- **GG-7 — Photosensitivity (product-safety constraint)**: no full-frame luminance flashes; corruption is geometric/chromatic only. Any whole-frame brightness excursion stays under ~10%; no strobe primitive. Bursts move pixels — they do not flash them. (WCAG 2.3.1 territory.)
- **GG-8 — Legacy upgrade in its own commit** (section 3) so product can accept/revert independently of the four new types.
- **GG-9 — Tuning isolation**: every section-2 constant lives in the `GlitchMode` enum constructor — tuning rounds never touch GLSL.

## 10. Codex implementation rules

1. Build order: GL effect (extract burst/envelope math to a small unit-testable Kotlin object if practical) -> plan wiring -> executor both paths -> registry/spec -> UI six sites + picker -> AB cases -> device run -> tuning round -> separable legacy-upgrade commit.
2. Commit in reviewable slices; the legacy upgrade (section 3) is always its own commit.
3. Hard gates before declaring done: 45-row AB matrix green on SM-A165F, determinism byte-compare passes, midpoint-concealment frame check passes, all four modes visually reviewed against CapCut reference, no frozen-surface diffs.
4. If any step appears to require touching a frozen surface, stop and escalate to Claude review instead of proceeding.

---

**Confidence: 8.5 / 10.** Every structural element is a validated clone: the three-texture shader skeleton shipped in film burn, the new-family wiring has been executed seven times, the mode-as-parameterized-enum pattern exists at the exact file being templated, and this family finally uses the `GLITCH` registry category present since the framework landed. The half-point gaps: glitch is the most tuning-sensitive family yet (burstiness is a feel, not a formula — the section-2 table will move), and the burst-gate math is the first time-quantized shader logic in the pack, where an off-by-one in quantization produces flicker only device frames reveal. Both risks are bounded by GG-2's determinism check and the frame-step validation.
