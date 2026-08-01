# AirCanvas OS implementation map

## v2.3 — Pinch Rebuild / Privacy Orbit

- Landscape-first workspace with wide default document, compact chrome, safe editor insets, scrollable short-height dialogs, and complete tool access
- Rebuilt pinch geometry using robust palm scale plus distal-index contact, with tolerance for bent fingers, foreshortening, rotation, and real camera depth jitter
- Two-frame activation, separate start/release hysteresis, ambiguous-frame hold, and rapid-opening release to prevent both missed pinches and drawing tails
- One-time v2.3 migration that clears incompatible learned ratios and restores reliable sensitivity/smoothing defaults without overriding an explicit Eco choice
- Thumbs-up full shape palette with stable hover and pinch-release selection
- Uploaded thumb-plus-sideways-index orbit pose with continuous selected/all-3D X/Y/Z rotation
- Adjustable hacker privacy mask layered strictly below the canvas and workspace chrome
- 512×384 keep-latest analysis, faster adaptive pacing, and pooled camera bitmaps for lower allocation pressure
- Bounded on-device adaptive calibration from sparse high-confidence samples; no camera frames or gesture samples leave the device
- Deterministic command palettes with open guard, stable hover, landmark-dropout grace, late-arm support, and pinch-release confirmation
- Correct ML Kit Digital Ink runtime namespace and true multi-stroke handwriting/word batching
- Auto mode preserves closed-shape cleanup while routing open strokes to text recognition with safe geometric fallback
- Recognition Reliability, Gesture Expansion, Performance Pacing, Landscape Readiness, Pinch Reality, and Auto Text reflection harnesses run in GitHub Actions

## v2.0 — Portfolio Showcase Edition

- Client-facing Portfolio Showcase template with editable 3D architecture scene
- One-tap editable demo or full-screen presentation launch
- Style Studio visual presets, stroke width, and opacity
- Lock/unlock protection plus align and distribute operations
- Focus mode for distraction-free active editing
- Project Insights and rolling performance telemetry
- Share-ready 16:9 portfolio poster export
- Portfolio case study, demo script, client pitch, and hero artwork
- Portable Portfolio Edition regression harness in GitHub Actions

## v1.3 correction and capability layer

- Responsive trajectory-based swipe recognition in all four directions
- Immediate pinch release with last-valid-point finalization
- Smart Ink Auto/Shape/Text modes
- Deterministic rough-shape cleanup plus on-device handwriting recognition
- True cube, sphere, cylinder, pyramid, and cone meshes
- Full 360° X/Y/Z solid rotation with perspective, depth, lighting, hit
  testing, caching, save/import, and export support
- Lower-allocation camera preprocessing and bounded recognition queues
- True 3D Lab discovery template

## M1 — Vision and gesture foundation

- CameraX lifecycle binding with MediaPipe Hand Landmarker live-stream inference
- Latest-frame backpressure, one-in-flight inference, and mirrored front camera
- Adaptive cursor smoothing, pinch hysteresis, fist, palm, swipe, and hand loss
- Up to two hands with optional landmark overlay

## M2 — Canvas engine

- Shared gesture/touch editing state machine
- Shape, connector, pen, text, select, and erase modes
- Zoom, pan, fit, grid, undo, and redo

## M3 — Object editing

- Selection, multi-selection, move, resize, delete, group, and ungroup
- Fist-driven move/pan and two-hand scaling
- Layer ordering and touch fallback

## M4 — Save and export foundation

- Atomic autosave and named local projects
- Project library with open and delete
- PNG, PDF, and versioned editable JSON
- Android document picker destinations

## M5 — Creator templates

- Blank
- Flowchart
- UI Wireframe
- Mind Map
- Boundary Plan

## M6 — Foundation polish and delivery

- Open-palm/pinch/fist calibration
- Eco, Balanced, and Smooth profiles
- First-run tutorial, gesture guide, settings, and camera switching
- Android 16 target, offline permission hardening, tests, and GitHub Actions

## M7 — Full-screen AR-like overlay workspace

- Camera preview is the default full-screen visual background
- Transparent spatial drawing layer covers the complete display
- Edge-to-edge Android layout with optional traditional design-board mode
- Overlay-aware fit, pan, zoom, grid, touch, save, and restore

## M8 — Spatial object transform engine

- Continuous move and resize for individual objects or groups
- X, Y, and Z rotation normalized across a complete 360° range
- Planar perspective projection plus true 3D mesh projection, hit testing, and
  visual bounds
- Duplicate, delete, opacity, send-to-back, and bring-to-front controls
- One-hand transform drag plus two-hand resize/twist

## M9 — Hands-only mode system

- Stable V-sign dwell opens a nine-mode command palette, including Smart Ink
- Air-cursor hover plus pinch commits the selected mode
- Vertical palm swipes cycle the current shape or transform axis
- Palette consumption guards prevent the selection pinch leaking into the canvas
- Gesture sessions reset across calibration, camera changes, and modal windows

## M10 — Shape and design studio

- Rectangle, ellipse, diamond, triangle, hexagon, star, and sticky note
- Boundary/frame, arrow connector, freehand stroke, and text
- Magnetic connector anchors that follow transformed objects
- Shape-aware styling and bounded freehand point buffers

## M11 — Pro editing and presentation

- Magnetic edge/center alignment guides
- Selection, resize, rotate, and axis handles
- Read-only full-screen presentation mode with air laser and fist pan
- Spatial System and Storyboard templates
- Visible-object render culling and a mobile-safe editable-scene ceiling

## M12 — Scene format, export, and performance hardening

- Schema-v3 JSON stores overlay mode, 3-axis rotation, true depth, opacity, and
  anchors
- Safe schema-v1/v2 migration plus malformed/oversized import guards
- Transparent PNG, PDF, editable SVG, and JSON import/export
- Background PNG/PDF rendering with dimension and pixel budgets
- Adaptive camera cadence, coalesced UI delivery, and stable hand ordering
- Indexed connector refresh, bounded undo-memory budget, atomic autosave
- GitHub workflow gates release on tests, lint, signing, alignment, and permissions
