package com.mohnish.aircanvas.vision;

public final class PerformanceTelemetry {
    public record Snapshot(
            float fps,
            float averageInferenceMs,
            String health,
            int handCount
    ) {
        public String compactLabel() {
            return health
                    + " • "
                    + Math.round(averageInferenceMs)
                    + " ms • "
                    + Math.round(fps)
                    + " fps";
        }
    }

    private static final float ALPHA = 0.16f;
    private long lastFrameAt;
    private float averageFrameIntervalMs;
    private float averageInferenceMs;
    private int handCount;

    public synchronized Snapshot record(long frameAtMs, long inferenceMs, int hands) {
        long safeInference = Math.max(0L, Math.min(2000L, inferenceMs));
        if (lastFrameAt > 0L && frameAtMs > lastFrameAt) {
            float interval = Math.min(1000f, frameAtMs - lastFrameAt);
            averageFrameIntervalMs = averageFrameIntervalMs <= 0f
                    ? interval
                    : lerp(averageFrameIntervalMs, interval, ALPHA);
        }
        averageInferenceMs = averageInferenceMs <= 0f
                ? safeInference
                : lerp(averageInferenceMs, safeInference, ALPHA);
        lastFrameAt = Math.max(lastFrameAt, frameAtMs);
        handCount = Math.max(0, Math.min(2, hands));
        return snapshot();
    }

    public synchronized Snapshot snapshot() {
        float fps = averageFrameIntervalMs <= 0f
                ? 0f
                : Math.min(120f, 1000f / averageFrameIntervalMs);
        String health;
        if (averageInferenceMs <= 48f && fps >= 18f) {
            health = "Smooth";
        } else if (averageInferenceMs <= 85f && fps >= 10f) {
            health = "Stable";
        } else if (averageInferenceMs <= 0f) {
            health = "Starting";
        } else {
            health = "Adaptive";
        }
        return new Snapshot(fps, averageInferenceMs, health, handCount);
    }

    public synchronized void reset() {
        lastFrameAt = 0L;
        averageFrameIntervalMs = 0f;
        averageInferenceMs = 0f;
        handCount = 0;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}
