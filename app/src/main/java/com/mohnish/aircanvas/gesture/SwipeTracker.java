package com.mohnish.aircanvas.gesture;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Trajectory-based four-direction swipe recognizer.
 *
 * <p>A brief pose-classification drop is tolerated so one motion-blurred
 * frame cannot erase an otherwise valid swipe. Net displacement, velocity,
 * direction dominance, and path efficiency all participate in the decision,
 * which keeps the lower thresholds responsive without turning camera jitter
 * into commands.</p>
 */
final class SwipeTracker {
    private static final long WINDOW_MS = 560L;
    private static final long POSE_GAP_GRACE_MS = 150L;
    private static final long COOLDOWN_MS = 380L;

    private final Deque<Sample> samples = new ArrayDeque<>();
    private long lastEligibleAt;
    private long cooldownUntil;

    GestureEvent.Type update(
            long timestampMs,
            float x,
            float y,
            float palmSize,
            float sensitivity,
            boolean eligible
    ) {
        if (!eligible) {
            if (samples.isEmpty()
                    || timestampMs - lastEligibleAt > POSE_GAP_GRACE_MS) {
                samples.clear();
            }
            return null;
        }
        lastEligibleAt = timestampMs;
        if (timestampMs < cooldownUntil) {
            samples.clear();
            return null;
        }

        Sample previous = samples.peekLast();
        if (previous == null
                || timestampMs - previous.timeMs >= 45L
                || distance(previous.x, previous.y, x, y) >= 0.006f) {
            samples.addLast(new Sample(timestampMs, x, y));
        }
        while (!samples.isEmpty()
                && timestampMs - samples.peekFirst().timeMs > WINDOW_MS) {
            samples.removeFirst();
        }
        if (samples.size() < 3) {
            return null;
        }

        Sample first = samples.peekFirst();
        Sample last = samples.peekLast();
        if (first == null || last == null) {
            return null;
        }
        long durationMs = last.timeMs - first.timeMs;
        if (durationMs < 80L) {
            return null;
        }
        float dx = last.x - first.x;
        float dy = last.y - first.y;
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        float net = (float) Math.hypot(dx, dy);
        float path = pathLength();
        float efficiency = net / Math.max(0.001f, path);
        float seconds = durationMs / 1000f;
        float velocity = net / Math.max(0.001f, seconds);
        float safeSensitivity = clamp(sensitivity, 0f, 1f);
        float minimumDisplacement = clamp(
                palmSize * 0.82f + (0.5f - safeSensitivity) * 0.05f,
                0.085f,
                0.145f
        );
        float minimumVelocity = 0.24f + (0.5f - safeSensitivity) * 0.10f;
        if (net < minimumDisplacement
                || velocity < minimumVelocity
                || efficiency < 0.56f) {
            return null;
        }

        GestureEvent.Type result = null;
        if (absX >= absY * 1.24f + 0.012f) {
            result = dx < 0f
                    ? GestureEvent.Type.SWIPE_LEFT
                    : GestureEvent.Type.SWIPE_RIGHT;
        } else if (absY >= absX * 1.24f + 0.012f) {
            result = dy < 0f
                    ? GestureEvent.Type.SWIPE_UP
                    : GestureEvent.Type.SWIPE_DOWN;
        }
        if (result != null) {
            samples.clear();
            cooldownUntil = timestampMs + COOLDOWN_MS;
        }
        return result;
    }

    void reset() {
        samples.clear();
        lastEligibleAt = 0L;
    }

    void resetAll() {
        reset();
        cooldownUntil = 0L;
    }

    private float pathLength() {
        float length = 0f;
        Sample previous = null;
        for (Sample sample : samples) {
            if (previous != null) {
                length += distance(previous.x, previous.y, sample.x, sample.y);
            }
            previous = sample;
        }
        return length;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Sample(long timeMs, float x, float y) {
    }
}
