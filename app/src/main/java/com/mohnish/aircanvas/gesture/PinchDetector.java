package com.mohnish.aircanvas.gesture;

import java.util.List;

/**
 * Geometry-only pinch detector designed for noisy mobile-camera landmarks.
 *
 * <p>The previous detector mixed a very small absolute pixel-normalized gap
 * with a single foreshortening-sensitive palm measurement. On a real hand,
 * MediaPipe can place the thumb tip on the side of the index fingertip or make
 * the wrist-to-middle-MCP distance look short, so a visually closed pinch was
 * rejected forever. This detector uses a robust palm scale, accepts contact
 * anywhere on the distal index segment, and keeps separate start/release
 * thresholds for hysteresis.</p>
 */
public final class PinchDetector {
    private static final int WRIST = 0;
    private static final int THUMB_MCP = 2;
    private static final int THUMB_IP = 3;
    private static final int THUMB_TIP = 4;
    private static final int INDEX_MCP = 5;
    private static final int INDEX_PIP = 6;
    private static final int INDEX_DIP = 7;
    private static final int INDEX_TIP = 8;
    private static final int MIDDLE_MCP = 9;
    private static final int PINKY_MCP = 17;

    public enum State {
        OPEN,
        AMBIGUOUS,
        CLOSED
    }

    public record Observation(
            State state,
            float tipRatio,
            float distalSegmentRatio,
            float handScale,
            boolean strongContact
    ) {
        public boolean isClosed() {
            return state == State.CLOSED;
        }

        public boolean isOpen() {
            return state == State.OPEN;
        }
    }

    private PinchDetector() {
    }

    public static Observation evaluate(
            List<LandmarkPoint> hand,
            float startThreshold,
            float releaseThreshold,
            boolean alreadyActive
    ) {
        if (hand == null || hand.size() < 21) {
            return new Observation(
                    State.OPEN,
                    Float.POSITIVE_INFINITY,
                    Float.POSITIVE_INFINITY,
                    0f,
                    false
            );
        }

        LandmarkPoint wrist = hand.get(WRIST);
        LandmarkPoint thumbTip = hand.get(THUMB_TIP);
        LandmarkPoint indexTip = hand.get(INDEX_TIP);
        LandmarkPoint indexDip = hand.get(INDEX_DIP);
        LandmarkPoint indexPip = hand.get(INDEX_PIP);

        float scale = handScale(hand);
        float tipRatio = weightedDistance(thumbTip, indexTip) / scale;
        float segmentRatio = pointToSegmentDistance(
                thumbTip,
                indexDip,
                indexTip
        ) / scale;

        float start = clamp(startThreshold, 0.16f, 0.27f);
        float release = clamp(
                Math.max(releaseThreshold, start + 0.12f),
                start + 0.12f,
                0.46f
        );
        float segmentStart = start * 0.90f;
        float segmentRelease = release * 0.84f;

        float palmX = (hand.get(INDEX_MCP).x
                + hand.get(MIDDLE_MCP).x
                + hand.get(PINKY_MCP).x) / 3f;
        float palmY = (hand.get(INDEX_MCP).y
                + hand.get(MIDDLE_MCP).y
                + hand.get(PINKY_MCP).y) / 3f;
        float indexFromPalm = distance2D(indexTip.x, indexTip.y, palmX, palmY);
        float thumbFromPalm = distance2D(thumbTip.x, thumbTip.y, palmX, palmY);

        float indexMcpReach = weightedDistance(wrist, hand.get(INDEX_MCP));
        float indexTipReach = weightedDistance(wrist, indexTip);
        float thumbMcpReach = weightedDistance(wrist, hand.get(THUMB_MCP));
        float thumbTipReach = weightedDistance(wrist, thumbTip);
        float indexBend = angle(hand.get(INDEX_MCP), indexPip, indexTip);
        float depthDifference = Math.abs(thumbTip.z - indexTip.z);

        boolean indexAvailable = indexTipReach >= indexMcpReach * 0.86f
                || indexFromPalm >= scale * 0.48f;
        boolean thumbAvailable = thumbTipReach >= thumbMcpReach * 0.84f
                || thumbFromPalm >= scale * 0.42f;
        boolean notBuriedFist = indexBend >= 48f
                && (indexFromPalm >= scale * 0.42f
                || tipRatio <= start * 0.68f
                || segmentRatio <= segmentStart * 0.58f);
        boolean depthCompatible = depthDifference <= Math.max(0.22f, scale * 0.95f);
        boolean postureCompatible = indexAvailable
                && thumbAvailable
                && notBuriedFist
                && depthCompatible;

        boolean strong = postureCompatible && (
                tipRatio <= start * 0.64f
                        || segmentRatio <= segmentStart * 0.56f
        );

        boolean closedAtStart = postureCompatible && (
                tipRatio <= start
                        || segmentRatio <= segmentStart
        );
        boolean closedWhileActive = postureCompatible && (
                tipRatio <= release
                        || segmentRatio <= segmentRelease
        );

        if ((!alreadyActive && closedAtStart) || (alreadyActive && closedWhileActive)) {
            return new Observation(State.CLOSED, tipRatio, segmentRatio, scale, strong);
        }

        float ambiguousTip = alreadyActive ? release + 0.045f : start + 0.035f;
        float ambiguousSegment = alreadyActive
                ? segmentRelease + 0.040f
                : segmentStart + 0.030f;
        if (postureCompatible
                && (tipRatio <= ambiguousTip || segmentRatio <= ambiguousSegment)) {
            return new Observation(
                    State.AMBIGUOUS,
                    tipRatio,
                    segmentRatio,
                    scale,
                    false
            );
        }

        return new Observation(State.OPEN, tipRatio, segmentRatio, scale, false);
    }

    public static float pinchRatio(List<LandmarkPoint> hand) {
        if (hand == null || hand.size() < 21) {
            return Float.POSITIVE_INFINITY;
        }
        return weightedDistance(hand.get(THUMB_TIP), hand.get(INDEX_TIP))
                / handScale(hand);
    }

    static float handScale(List<LandmarkPoint> hand) {
        LandmarkPoint wrist = hand.get(WRIST);
        float wristMiddle = weightedDistance(wrist, hand.get(MIDDLE_MCP));
        float wristIndex = weightedDistance(wrist, hand.get(INDEX_MCP));
        float wristPinky = weightedDistance(wrist, hand.get(PINKY_MCP));
        float palmWidth = weightedDistance(hand.get(INDEX_MCP), hand.get(PINKY_MCP));
        return Math.max(
                0.055f,
                Math.max(
                        Math.max(wristMiddle, wristIndex),
                        Math.max(wristPinky, palmWidth * 0.95f)
                )
        );
    }

    private static float pointToSegmentDistance(
            LandmarkPoint point,
            LandmarkPoint start,
            LandmarkPoint end
    ) {
        float dx = end.x - start.x;
        float dy = end.y - start.y;
        float denominator = dx * dx + dy * dy;
        float t = denominator <= 1e-8f
                ? 0f
                : ((point.x - start.x) * dx + (point.y - start.y) * dy)
                / denominator;
        t = clamp(t, 0f, 1f);
        float projectedX = start.x + dx * t;
        float projectedY = start.y + dy * t;
        float projectedZ = start.z + (end.z - start.z) * t;
        float x = point.x - projectedX;
        float y = point.y - projectedY;
        float z = (point.z - projectedZ) * 0.35f;
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static float weightedDistance(LandmarkPoint first, LandmarkPoint second) {
        float x = first.x - second.x;
        float y = first.y - second.y;
        float z = (first.z - second.z) * 0.35f;
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static float angle(LandmarkPoint a, LandmarkPoint b, LandmarkPoint c) {
        float abX = a.x - b.x;
        float abY = a.y - b.y;
        float cbX = c.x - b.x;
        float cbY = c.y - b.y;
        float denominator = (float) Math.sqrt(
                (abX * abX + abY * abY) * (cbX * cbX + cbY * cbY)
        );
        if (denominator < 1e-6f) {
            return 0f;
        }
        float cosine = clamp((abX * cbX + abY * cbY) / denominator, -1f, 1f);
        return (float) Math.toDegrees(Math.acos(cosine));
    }

    private static float distance2D(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
