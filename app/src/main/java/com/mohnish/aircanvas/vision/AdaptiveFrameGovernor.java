package com.mohnish.aircanvas.vision;

/**
 * Keeps live hand inference from being submitted faster than the device can
 * complete it. The governor reacts to measured end-to-end inference time and
 * gradually returns to the selected performance profile after recovery.
 */
public final class AdaptiveFrameGovernor {
    private static final long MIN_INTERVAL_MS = 24L;
    private static final long MAX_INTERVAL_MS = 140L;
    private static final long RECOVERY_STEP_MS = 5L;

    private long baseIntervalMs;
    private long effectiveIntervalMs;
    private float movingInferenceMs;

    public AdaptiveFrameGovernor(long baseIntervalMs) {
        setBaseInterval(baseIntervalMs);
    }

    public synchronized void setBaseInterval(long intervalMs) {
        baseIntervalMs = clamp(intervalMs, MIN_INTERVAL_MS, 110L);
        if (effectiveIntervalMs == 0L) {
            effectiveIntervalMs = baseIntervalMs;
        } else {
            effectiveIntervalMs = Math.max(baseIntervalMs, effectiveIntervalMs);
        }
    }

    public synchronized void recordInference(long inferenceMs) {
        float safe = clamp(inferenceMs, 1L, 500L);
        movingInferenceMs = movingInferenceMs == 0f
                ? safe
                : movingInferenceMs * 0.82f + safe * 0.18f;

        // Never ask the analyzer to submit frames faster than the measured
        // inference pipeline can finish. The previous 0.78 multiplier could
        // produce a 75 ms cadence for a 96 ms inference, which violated this
        // invariant and failed the release unit test.
        long required = (long) Math.ceil(movingInferenceMs);
        if (required > effectiveIntervalMs) {
            effectiveIntervalMs = Math.min(MAX_INTERVAL_MS, required);
        } else if (required < effectiveIntervalMs && effectiveIntervalMs > baseIntervalMs) {
            // Recover gradually, but never decay below the current measured
            // requirement. Equal repeated measurements must hold cadence rather
            // than incorrectly shaving 5 ms on every frame.
            long recoveryFloor = Math.max(baseIntervalMs, required);
            effectiveIntervalMs = Math.max(
                    recoveryFloor,
                    effectiveIntervalMs - RECOVERY_STEP_MS
            );
        }
    }

    public synchronized long intervalMs() {
        return Math.max(baseIntervalMs, effectiveIntervalMs);
    }

    public synchronized float movingInferenceMs() {
        return movingInferenceMs;
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
