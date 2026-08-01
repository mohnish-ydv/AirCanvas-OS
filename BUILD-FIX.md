# v2.2.1 build fix

## Failure from the uploaded GitHub Actions logs

`AdaptiveFrameGovernorTest.slowInferenceRaisesCadenceWithoutBuildingAQueue` failed at the assertion requiring the adaptive interval to be at least 96 ms after repeated 96 ms inference.

The previous governor multiplied measured inference by `0.78`, producing about 75 ms. It also reduced the interval by 5 ms on repeated equal measurements.

## Correction

- The required cadence is now the ceiling of the moving measured inference time.
- Equal repeated measurements hold the current interval instead of triggering recovery decay.
- Recovery only occurs when the measured requirement is genuinely lower.
- Recovery remains gradual and cannot drop below either the selected profile or the current measured requirement.
- The overload ceiling is bounded at 140 ms.

## Verification

- Exact uploaded-log regression: PASS (`96 ms` measured → `96 ms` interval)
- Recovery regression: PASS (`130 ms` overload returns to the `66 ms` profile)
- Portable project harnesses: 36/36 PASS
- Auto Text reflection harness: 8/8 PASS
