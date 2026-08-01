package com.mohnish.aircanvas.gesture;

/**
 * Small on-device personalization profile for pinch recognition.
 *
 * <p>This is deliberately bounded calibration, not an opaque self-training model.
 * Only high-confidence confirmed pinches and clearly open-hand samples are learned,
 * so one accidental frame cannot make the detector progressively worse.</p>
 */
public final class AdaptiveGestureProfile {
    public record Snapshot(
            float pinchMean,
            int pinchSamples,
            float openMean,
            int openSamples,
            long revision
    ) {
    }

    private static final int MAX_EFFECTIVE_SAMPLES = 96;
    private float pinchMean = 0.13f;
    private int pinchSamples;
    private float openMean = 1.05f;
    private int openSamples;
    private long revision;

    public AdaptiveGestureProfile() {
    }

    public AdaptiveGestureProfile(Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        pinchMean = clamp(snapshot.pinchMean(), 0.06f, 0.34f);
        pinchSamples = clampCount(snapshot.pinchSamples());
        openMean = clamp(snapshot.openMean(), 0.52f, 2.50f);
        openSamples = clampCount(snapshot.openSamples());
        revision = Math.max(0L, snapshot.revision());
    }

    public synchronized void observeConfirmedPinch(float ratio) {
        if (!Float.isFinite(ratio) || ratio < 0.04f || ratio > 0.34f) {
            return;
        }
        pinchMean = updateMean(pinchMean, pinchSamples, ratio);
        pinchSamples = Math.min(MAX_EFFECTIVE_SAMPLES, pinchSamples + 1);
        revision++;
    }

    public synchronized void observeClearlyOpen(float ratio) {
        if (!Float.isFinite(ratio) || ratio < 0.52f || ratio > 2.50f) {
            return;
        }
        openMean = updateMean(openMean, openSamples, ratio);
        openSamples = Math.min(MAX_EFFECTIVE_SAMPLES, openSamples + 1);
        revision++;
    }

    public synchronized float startThreshold(float sensitivity) {
        // Ratio uses PinchDetector's robust palm scale. Keep the activation band
        // below a clearly visible fingertip gap while allowing real landmark
        // jitter and a thumb contacting the side of the index fingertip.
        float base = 0.195f + clamp01(sensitivity) * 0.030f;
        if (pinchSamples < 4) {
            return clamp(base, 0.185f, 0.225f);
        }
        float learned = pinchMean + 0.060f;
        if (openSamples >= 4) {
            learned = Math.min(learned, openMean - 0.34f);
        }
        // Personalization may widen the detector for a user's camera/hand, but
        // repeated very-tight pinches must never train it into becoming harder.
        float blended = (base * 0.75f) + (learned * 0.25f);
        return clamp(blended, base - 0.008f, base + 0.020f);
    }

    public synchronized float releaseThreshold(float sensitivity) {
        float start = startThreshold(sensitivity);
        float release = start + 0.105f;
        if (openSamples >= 4) {
            release = Math.min(release, openMean - 0.22f);
        }
        return clamp(release, 0.285f, 0.355f);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(pinchMean, pinchSamples, openMean, openSamples, revision);
    }

    private static float updateMean(float current, int count, float value) {
        if (count <= 0) {
            return value;
        }
        int effective = Math.min(MAX_EFFECTIVE_SAMPLES, count);
        float alpha = 1f / Math.min(24f, effective + 1f);
        return current + (value - current) * alpha;
    }

    private static int clampCount(int value) {
        return Math.max(0, Math.min(MAX_EFFECTIVE_SAMPLES, value));
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.5f;
        }
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
