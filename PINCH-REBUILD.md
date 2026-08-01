# Pinch rebuild — engineering note

## Why the old fix still failed

The previous recognizer treated pinch as a near-exact thumb-tip/index-tip gap
scaled by a single palm distance. Mobile hand-landmark models frequently place
the thumb on the side of the index tip, shorten a wrist-to-MCP distance under
perspective, or jitter depth while the fingers are visibly touching. A smaller
threshold caused misses; a larger threshold caused false positives. The defect
was therefore geometric, not simply a sensitivity setting.

## New detection model

`PinchDetector` calculates a robust scale from wrist-to-middle, wrist-to-index,
wrist-to-pinky, and palm-width measurements. It evaluates both:

1. weighted thumb-tip to index-tip distance, and
2. weighted thumb-tip to the distal index segment distance.

A posture gate checks index/thumbnail availability, index bend, palm reach, and
reasonable depth compatibility. Separate start and release bands provide
hysteresis.

## State-machine behavior

- Two consecutive closed observations start pinch.
- Ambiguous observations keep an already-active pinch alive.
- Clear opening ends immediately.
- A strong frame-to-frame contact-distance increase also ends immediately,
  preventing smoothed landmarks from drawing a release tail.
- Two-hand scale retains its own two-hand debounce and suppress-until-release
  safety.

## Palette behavior

The palette now owns the entire pinch cycle whenever it is open. If the pinch
starts just before hover dwell is complete, `PaletteSelectionGate` records that
the fingers are down and late-arms the stable hovered card. Commit occurs only
on release, preventing both missed selections and pinch leakage into the canvas.

## Regression gate

`qa/PinchRealityHarness.java` covers eleven realistic geometry/state scenarios and
is executed by GitHub Actions before Android unit tests and APK assembly.
