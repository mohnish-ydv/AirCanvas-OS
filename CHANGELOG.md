# Changelog

## 2.3.0 — Pinch Rebuild

- Replaced the old absolute-gap/single-palm-scale pinch test with a robust
  multi-measure detector that accepts thumb contact on the distal index segment
- Added real-camera tolerance for bent-index contact, foreshortening, hand scale,
  rotation, and limited landmark depth jitter
- Reduced deliberate pinch activation from three stable frames to two
- Added start/release hysteresis, ambiguous-frame hold, and rapid-opening release
  so a live pinch does not flicker and an opening hand does not leave drawing tails
- Fixed command-palette timing so a pinch begun before hover readiness can late-arm
  after the cursor settles and still commits only on release
- Added a one-time v2.3 migration that clears incompatible learned pinch ratios
  and restores reliable sensitivity/smoothing defaults
- Bounded adaptive learning so repeated tight pinches can never train the detector
  into becoming stricter
- Added `PinchRealityHarness` with eleven geometry, posture, release, adaptation, and palette
  scenarios; the complete portable suite now passes 47/47 checks

## 2.2.1 — Frame Pacing Build Fix

- Fixed the failing `AdaptiveFrameGovernorTest` release gate.
- The adaptive analyzer interval now never runs faster than measured inference time, preventing avoidable analyzer pressure on slow frames.
- Raised only the overload safety ceiling to 140 ms while preserving the selected 24–110 ms base profiles and gradual recovery.
- Updated portable pacing regression expectations, version metadata, and GitHub artifact naming.
- Removed accidentally packaged compiled `.class` files from the Java source tree.

## 2.2.0 — Pinch Rescue / Privacy Orbit

- Reworked pinch recognition to accept natural bent-index fingertip contact while preserving immediate release and near-pinch rejection.
- Added a thumbs-up shape palette containing every 2D and 3D shape; hover, pinch, and release to select.
- Added the uploaded thumb-plus-sideways-index pose for automatic planet-like 3D rotation. Selected 3D objects rotate first; otherwise all unlocked 3D objects rotate.
- Added an adjustable hacker privacy mask rendered between camera preview and canvas, keeping shapes, text, ink, cursor, and UI above the mask.
- Added a one-time upgrade migration that clears v2.1's strict learned pinch profile and promotes legacy Balanced installs to Smooth while preserving explicit Eco choices.
- Reduced hand-analysis resolution and latency, lowered confidence gates safely, tightened frame pacing, pooled camera bitmaps to avoid per-frame allocation stalls, and made Smooth the default profile.
- Added lifecycle cleanup so palettes and auto-spin cannot continue in the background.

## 2.1.0 — Landscape Adaptive Recognition

- Rebuilt pinch activation around three stable frames and conservative fingertip contact
- Rejected closed-fist and visibly separated thumb/index false positives
- Added bounded, sparse, on-device adaptive gesture calibration with reset control
- Reworked the V-sign command palette around stable hover and pinch-release confirmation
- Corrected ML Kit Digital Ink runtime namespace and added multi-stroke word batching
- Routed open/line-like Auto Ink strokes to handwriting while preserving closed-shape cleanup
- Made sensor landscape the default while retaining an optional portrait setting
- Added compact landscape chrome, safe editor insets, scrollable short-screen dialogs, and wide palette layout
- Added recognition reliability, Auto Text reflection, and landscape readiness release gates

## 2.0.0 — Portfolio Showcase Edition

- Added the editable Portfolio Showcase scene and full-screen demo launch
- Added Style Studio with six professional visual presets
- Added stroke-width and opacity controls for selected objects
- Added lock/unlock protection, six-way alignment, and two-axis distribution
- Added Focus mode that hides chrome while editing remains active
- Added Project Insights with scene and live-engine metrics
- Added rolling FPS/inference telemetry to the editor status chip
- Added a 2400×1350 portfolio poster export with developer credit
- Added Portfolio, Client Pitch, and 60-second Demo Script documentation
- Added deterministic Portfolio Edition and telemetry regression coverage
- Preserved the v1.3.3 camera-orientation recovery and gesture pipeline

## 1.3.3 — camera orientation recovery

- Fixed the front-camera hand/cursor appearing inverted or moving on the wrong axes
- Replaced metadata-only landmark alignment with a preview-aligned rotation/mirror pass
- Added explicit CameraX target rotation for both Preview and ImageAnalysis
- Refreshes target rotation and clears stale gesture state after device configuration changes
- Discards in-flight results produced under an obsolete display transform
- Uses a single matrix render pass and reusable output bitmap to control GC pressure
- Added deterministic 0/90/180/270 and front-mirror regression coverage
- Preserved responsive swipes, instant pinch release, Smart Ink, true 3D, saves, and exports

## 1.3.2 — Compile fix

- Fixed the GitHub Actions Java compiler error in `OnDeviceInkRecognizer`.
- Added a typed text callback so reflected recognition results cannot be passed as raw `Object` values.
- Recompiled the recognizer with Java 17 and reran the core gesture/Smart Ink/3D regression harness.
- Updated version, APK artifact name, and QA metadata.

## 1.3.1 — GitHub build fix

- Fixed the GitHub Actions compile failure in `OnDeviceInkRecognizer`
- Replaced direct ML Kit Digital Ink imports with a compile-safe runtime bridge
- Preserved on-device handwriting recognition when the ML Kit classes are available
- Added graceful Smart Ink fallback when handwriting classes are unavailable
- Updated APK artifact naming, QA notes, and version metadata

## 1.3.0 — responsive gestures, Intelligent Ink, and true 3D

- Rebuilt four-direction swipes around a lower-latency trajectory classifier
  with motion/pose-drop tolerance and shorter cooldown
- Made pinch release immediate and anchored `PINCH_END` to the final closed
  pinch position, eliminating the open-hand drawing tail
- Added Smart Ink Auto/Shape/Text modes to the toolbar and gesture palette
- Added deterministic rough-geometry cleanup and on-device English
  handwriting recognition for letters and continuous words
- Added one-time language-model download handling, pre-context, writing-area
  hints, bounded serial recognition, and safe raw-ink fallback
- Added real cube, sphere, cylinder, pyramid, and cone meshes with perspective,
  depth-sorted shading, hit testing, cached projection, depth persistence, and
  PNG/PDF/SVG support
- Preserved existing planar objects, transforms, templates, tools, touch
  controls, gesture controls, saves, and exports
- Removed the per-analysis rotated/mirrored bitmap copy by using MediaPipe
  image-processing rotation metadata and mirrored output coordinates
- Added the True 3D Lab template and expanded regression/API-surface checks

## 1.2.0 — M7–M12 spatial workspace

- Replaced the limited board-first view with a full-screen camera overlay plane
- Added eight spatial shape families, sticky notes, boundaries, and anchors
- Added move, resize, opacity, duplicate, layers, and continuous X/Y/Z rotation
- Added two-hand scale/twist and single-hand axis transform gestures
- Added a V-sign gesture command palette and vertical gesture mode controls
- Added magnetic alignment guides and connectors that follow transformed objects
- Added a read-only full-screen presentation mode with an air laser
- Added Spatial System and Storyboard templates
- Added editable SVG export and safe schema-v2 JSON import/export
- Moved PNG/PDF rendering off the UI thread and added export memory guards
- Added frame cadence control, UI-frame coalescing, render culling, and hand ordering
- Added indexed connector refresh and bounded undo/freehand/scene memory
- Expanded the deterministic regression suite and GitHub release audit gates

## 0.6.0 — M1–M6 foundation

- Built a new native Android project for the AirCanvas OS codename
- Added the on-device camera and hand-landmark pipeline
- Added calibrated pinch, palm, fist, swipe, and two-hand gestures
- Added the gesture/touch spatial canvas and six editing tools
- Added object selection, transform, grouping, ordering, and history
- Added five creator templates
- Added local autosave, project library, and PNG/PDF/JSON export
- Added onboarding, settings, performance modes, and camera switching
- Added Android 16, offline-permission, and 16 KB packaging hardening
- Added JVM tests, lint gating, Gradle wrapper checksum, and GitHub Actions
