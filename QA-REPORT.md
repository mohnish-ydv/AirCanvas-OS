# QA report

Package: **AirCanvas OS — v2.3.0 Pinch Rebuild**

Verification date: **2026-08-01**

## Release focus

This release addresses the persistent real-device pinch failure at the geometry
and state-machine level. The previous implementation could reject a visually
closed pinch because it depended too heavily on exact thumb-tip/index-tip
proximity and one perspective-sensitive palm measurement. Increasing
sensitivity alone could not make that reliable.

## Root-cause fixes

- Added `PinchDetector`, using the strongest of several palm measurements rather
  than one foreshortening-sensitive reference distance
- Accepts the thumb touching the side of the distal index segment, not only an
  exact tip-to-tip landmark match
- Uses raw landmarks for contact while keeping the cursor smoothed
- Starts after two stable closed frames instead of three
- Holds an active pinch through a short ambiguous landmark frame
- Releases immediately on a clearly open frame and also detects rapid separation
  before smoothing can create an unwanted drawing tail
- Rejects folded-fist postures and clearly separated fingertips
- Prevents bounded adaptive learning from ever making activation meaningfully
  stricter than the reliable base threshold
- Clears old incompatible adaptive ratios once during the v2.3 migration
- Fixes shape/mode palette timing: an early pinch can late-arm after hover settles,
  but selection still commits only on release

The existing thumbs-up shape palette, hacker privacy mask, uploaded orbit-spin
pose, camera-orientation correction, and lower-allocation frame pipeline remain
included.

## Verification performed in this environment

| Check | Result |
|---|---|
| Core gesture / Smart Ink / true-3D harness | PASS — 5/5 |
| Camera-orientation harness | PASS — 5/5 |
| Portfolio Edition harness | PASS — 5/5 |
| Recognition Reliability harness | PASS — 10/10 |
| Gesture Expansion harness | PASS — 3/3 |
| Performance Pacing harness | PASS — 3/3 |
| Landscape Readiness harness | PASS — 5/5 |
| New Pinch Reality harness | PASS — 11/11 |
| Portable Java 17 compilation | PASS — `-Xlint:all -Werror` |
| Auto Text reflection harness | PASS — 8/8 |
| Android XML / manifest / SVG parse | PASS |
| GitHub Actions YAML parse | PASS |
| Final ZIP integrity | PASS |

**Portable gesture/vision total: 47/47 passed.**

`PinchRealityHarness` covers side-of-index contact, natural pinching with the
other three fingers curled, perspective foreshortening, scale and rotation
invariance, visible-gap rejection, fist rejection,
ambiguous-frame continuity, rapid-opening release, clear first-frame release,
adaptive-learning safety, and palette late-arm selection.

## Package identity

| Field | Value |
|---|---|
| Application ID | `com.mohnish.aircanvas` |
| Version | `2.3.0` (`versionCode` 24) |
| Minimum / target SDK | 26 / 36 |
| Phone ABIs | `arm64-v8a`, `armeabi-v7a` |
| Requested permissions | `CAMERA`, `INTERNET`, `ACCESS_NETWORK_STATE` |
| Gesture adaptation | App-private bounded numeric profile only |
| Privacy-mask layering | Camera → mask → canvas/ink/shapes → landmarks/UI |

`INTERNET` and `ACCESS_NETWORK_STATE` are used only for the optional one-time
Digital Ink language-model download. Camera frames and gesture samples are not
uploaded by the app.

## Android release-build boundary

A local Android APK could not be assembled in this sandbox because the Gradle
distribution host is not reachable and no complete Android dependency cache is
available. This report therefore does **not** claim physical-device validation.

The included GitHub Actions workflow runs every portable regression above,
Android unit tests, release lint, APK assembly, signature verification, 16 KB
ZIP-alignment verification, and merged-permission auditing. It uploads no APK
artifact if any gate fails.

## Required physical-device acceptance checks

1. Install v2.3.0 so the one-time migration clears old pinch calibration.
2. Keep the hand roughly 40–90 cm from the camera in normal room lighting.
3. Perform ten natural pinches, including thumb-to-side-of-index contact and a
   mildly bent index; each should begin after roughly two processed frames.
4. Open the fingers quickly; drawing or dragging must end without a tail.
5. Hold thumbs up, hover a shape, begin the pinch slightly early, settle the
   cursor, and release; the intended card should select once.
6. Confirm a closed fist and a visible thumb/index gap do not activate pinch.
