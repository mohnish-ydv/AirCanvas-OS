# AirCanvas OS — Pinch Rebuild / Privacy Orbit Edition

![AirCanvas OS portfolio hero](docs/aircanvas-portfolio-hero.svg)

AirCanvas OS is a native Android spatial-creation prototype. A transparent,
edge-to-edge editing plane sits above the live camera so shapes, connectors,
notes, Smart Ink, and true 3D objects feel suspended in the scene. Every core
operation supports both hand gestures and touch fallback.

This `v2.3.0` build replaces the pinch-recognition core instead of applying
another threshold patch. It is designed around real mobile-camera landmark
behavior: side-of-fingertip contact, a bent index finger, perspective
foreshortening, temporary landmark ambiguity, and fast release. It also keeps
the thumbs-up full shape palette, privacy mask, uploaded orbit-spin pose, and
lower-allocation camera pipeline.
**AirCanvas OS remains a development codename, not the final public product
name.**

## v2.3 Pinch Rebuild / Privacy Orbit highlights

- **Rebuilt pinch geometry** — a robust multi-measure palm scale and distal
  index-segment contact test accept real side contact, mild index bend, camera
  angle changes, rotation, and hand-size changes while still rejecting visible gaps
- **Two-frame start + fast release** — deliberate contact starts after two stable
  frames; clear opening ends immediately, and rapid separation ends before
  smoothing can drag unwanted ink across the canvas
- **Stable hysteresis** — one ambiguous landmark frame no longer flickers a live
  pinch off, while fist/posture guards prevent a folded hand from becoming a pinch
- **Upgrade migration** — old learned ratios are cleared because they are not
  compatible with the rebuilt scale; low-sensitivity legacy installs are moved
  to reliable defaults while an explicit Eco choice remains respected
- **Thumbs-up shape palette** — all 2D and true-3D shapes are visible together;
  hover the target, pinch, and release instead of cycling one by one
- **Privacy hacker mask** — adjustable recording overlay sits above the camera
  but below shapes, text, ink, cursor, and all workspace controls
- **Orbit-spin pose** — the uploaded thumb-plus-sideways-index pose rotates
  selected 3D shapes continuously on X/Y/Z and saves the final orientation
- **Lower-allocation vision loop** — 512×384 analysis, keep-latest backpressure,
  adaptive pacing, and pooled camera bitmaps reduce stalls and garbage collection
- **Adaptive on-device calibration** — sparse high-confidence samples personalize
  safe thresholds while bounded limits prevent self-reinforcing false gestures
- **Reliable command palette** — a pinch that begins just before the hover
  guard is ready can late-arm once the cursor settles, then commits only on release
- **Auto Text repaired** — the correct ML Kit Digital Ink namespace is used and
  nearby pinch strokes are batched into one word-level recognition request
- **Landscape-first workspace** — sensor landscape is the default, editor chrome
  is compact, the canvas avoids system/tool overlays, dialogs scroll on short
  screens, and every tool/shape/3D solid remains available
- **Portrait remains optional** — disable Landscape-first in Settings when a
  portrait workflow is needed

The adaptive profile is stored only in app-private preferences. It is bounded
calibration, not server-side model training, and can be reset from Settings.

## Portfolio Edition highlights

- **Portfolio Showcase** — one-tap editable demo scene or full-screen presentation
- **Style Studio** — Neon Glass, Hologram, Blueprint, Sunset Signal, Mono Pro,
  and Warning visual systems
- **Precision arrangement** — align left/right/top/bottom/center and distribute
  horizontally or vertically
- **Selection protection** — lock or unlock important objects without losing the
  current selection
- **Focus mode** — hide chrome while keeping editing active
- **Project Insights** — object, true-3D, connector, text, group, and lock metrics
- **Live engine health** — rolling FPS and inference telemetry in the status chip
- **Portfolio poster export** — generates a 2400×1350 presentation graphic with
  project metrics and developer credit
- **Portable QA gate** — GitHub Actions runs deterministic JVM harnesses before
  unit tests, lint, APK assembly, signature checks, alignment checks, and the
  permission audit

## Core spatial workspace

- Full-screen camera overlay or traditional dark design board
- Rectangle, ellipse, diamond, triangle, hexagon, star, sticky-note, boundary,
  connector, freehand, text, and frame objects
- Smart Ink Auto, Shape, and Text modes
- Rough closed ink snaps into clean editable geometry on pinch release
- Rough letters and continuous writing can become editable text through
  on-device handwriting recognition
- Real cube, sphere, cylinder, pyramid, and cone meshes with perspective,
  depth-sorted faces, lighting, hit testing, and 360° X/Y/Z rotation
- Two-hand resize and Z twist, plus single-hand true-3D axis rotation
- V-sign command palette, palm swipe commands, presentation laser, and fist pan
- Atomic autosave, local project library, JSON import/export, transparent PNG,
  PDF, editable SVG, and portfolio PNG

See [PORTFOLIO.md](PORTFOLIO.md) for the technical case study,
[DEMO-SCRIPT.md](DEMO-SCRIPT.md) for the 60-second recording flow, and
[CLIENT-PITCH.md](CLIENT-PITCH.md) for a client-ready introduction.

## Gesture map

| Gesture | Result |
|---|---|
| Pinch | Create, draw, connect, select, drag, erase, or add text with the active mode |
| Open-palm dwell | Select the object under the air cursor |
| Fist + move | Move the selection; pan when nothing is selected |
| Palm swipe left / right | Undo / redo |
| Palm swipe up / down | Cycle the active shape, Smart Ink mode, or X/Y/Z transform axis |
| Hold a V sign | Open the command palette; hover and pinch to choose a mode |
| Pinch with both hands | Resize the selected object or group |
| Twist two pinched hands | Rotate the selection around Z |
| True 3D mode + pinch-drag | Rotate around X, Y, Z, or all three axes |

Gesture recognition depends on lighting, framing, and camera quality. Run
**Menu → Calibrate gestures** on the target phone for the first session. After that, high-confidence usage gradually personalizes the local profile without a separate Train button.

## Smoothness and stability design

- CameraX uses a 640×480 analysis target and keeps only the latest frame
- Exactly one MediaPipe inference can be in flight, preventing queue buildup
- An adaptive frame governor backs off under slow inference or thermal load
- Camera results are coalesced so stale frames cannot flood the UI thread
- Stable left/right hand ordering prevents false 180° two-hand rotation jumps
- A trajectory-based swipe detector uses displacement, velocity, direction
  dominance, and path efficiency while tolerating brief pose drops
- Pinch starts after two stable contact frames and releases on clear opening; the stroke
  ends at the last valid pinch point so an open-hand jump cannot create a tail
- Cursor smoothing adapts to motion speed while raw landmarks provide the
  fast pinch-release guard
- Camera analysis is normalized into the same rotation and front-camera mirror
  as the visible preview before MediaPipe runs
- Rotation and mirroring share one matrix pass, and its output bitmap is reused
  after inference to limit allocation/GC pressure
- Display-rotation changes reset gesture state and discard stale transformed frames
- Rendering is hardware accelerated and off-screen objects are culled
- 3D projections are cached until geometry or rotation changes
- Handwriting work is serial, bounded, and downsampled
- Connector refresh uses indexed lookup and avoids duplicate per-frame passes
- Undo history has both a 32-step cap and a 12 MB adaptive memory budget
- Freehand point buffers, imported data, export pixels, and editable scene size
  are bounded to avoid runaway memory use
- PNG/PDF rasterization runs on the background I/O executor, not the UI thread
- Autosave is atomic; interrupted gestures commit their last stable geometry

Choose **Eco**, **Balanced**, or **Smooth** in Settings. Balanced is the safe
default; Smooth is intended for a cool, well-lit device. The governor can
automatically reduce cadence if inference becomes slower than the chosen target.

## Touch controls

Every core operation also works by touch. Choose a mode from the bottom bar,
then tap or drag anywhere on the overlay. Select provides move, resize, and
rotation handles. In True 3D mode, two-finger touch resizes the selection;
otherwise two-finger touch zooms the viewport. **Menu → Fit canvas** restores
the fitted view.

The camera overlay is the default. **Menu → Switch to design board** restores a
traditional dark board when a camera background is not useful.

## Save, import, and export

- Atomic autosave and named local project saves
- In-app project library with open and delete
- Schema-v3 editable JSON stores true depth and safely migrates schema v1/v2
- JSON import through Android's document picker
- Transparent PNG for compositing
- PDF document output
- Editable SVG vector output
- Share-ready 2400×1350 portfolio poster output

Exports use a pixel and dimension budget so extreme imported aspect ratios
cannot request an unsafe bitmap allocation.

## Privacy

Hand-landmark inference runs on-device with the bundled model. Camera frames
are not stored or uploaded. Intelligent Ink recognition also runs on-device;
`INTERNET` and `ACCESS_NETWORK_STATE` are used only to download the selected
language model once (approximately 20 MB). Project files remain in app-private
storage unless the user saves or exports them through Android's document
picker.

## Build locally

Requirements:

- JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- Internet access for the first dependency download

The phone APK includes `arm64-v8a` and `armeabi-v7a`. Emulator-only x86 native
libraries are excluded to keep the install smaller.

On macOS/Linux:

```bash
./gradlew --no-daemon clean testDebugUnitTest lintRelease assembleRelease
```

On Windows:

```powershell
.\gradlew.bat --no-daemon clean testDebugUnitTest lintRelease assembleRelease
```

The installable codename APK is generated at:

```text
app/build/outputs/apk/release/app-release.apk
```

The codename release uses Android's debug signing key so a fresh GitHub
repository can produce an installable APK without repository secrets. Configure
a private release keystore before Play Store publication.

## Build from a phone

Push the extracted project to GitHub, open **Actions**, run
**Build verified APK**, and download
`AirCanvas-OS-Codename-v2.3.0-Pinch-Rebuild`. The workflow runs recognition, Auto Text, landscape and core regressions, release
lint, release assembly, signature verification, 16 KB ZIP alignment, and a
merged-permission audit before uploading the APK.

Detailed phone steps are in [PHONE-ONLY-BUILD.md](PHONE-ONLY-BUILD.md).

## Project layout

```text
app/src/main/assets/       bundled on-device hand-landmarker model
app/src/main/java/         app, spatial editor, gesture, storage, and export code
app/src/test/java/         deterministic JVM regression tests
.github/workflows/         verified GitHub Actions APK build
```

See [MILESTONES.md](MILESTONES.md) for the full implementation map and
[QA-REPORT.md](QA-REPORT.md) for the exact checks performed on this package.

## v2.3 gesture shortcuts

- **Thumbs up:** opens the full shape palette. Hover a card, pinch, and release.
- **Thumb + sideways index (uploaded reference pose):** starts automatic axis rotation for selected 3D shapes; release the pose to save the final orientation.
- **Hacker face mask:** open the overflow menu, choose **Hacker face mask**, then enable and position the privacy overlay. The canvas is intentionally rendered above it.
- **Pinch:** uses rebuilt raw-landmark geometry, two-frame activation, active-state hysteresis, and immediate rapid-opening release.
