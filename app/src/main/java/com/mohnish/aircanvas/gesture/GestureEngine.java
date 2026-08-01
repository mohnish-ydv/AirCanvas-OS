package com.mohnish.aircanvas.gesture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class GestureEngine {
    public enum Pose {
        NONE("No hand"),
        PINCH("Pinch"),
        FIST("Fist"),
        OPEN_PALM("Open palm"),
        TWO_FINGER("Two fingers"),
        THUMBS_UP("Thumbs up"),
        AUTO_SPIN("Orbit spin"),
        POINT("Point"),
        TRACKING("Tracking");

        public final String label;

        Pose(String label) {
            this.label = label;
        }
    }

    private static final int WRIST = 0;
    private static final int THUMB_MCP = 2;
    private static final int THUMB_IP = 3;
    private static final int THUMB_TIP = 4;
    private static final int INDEX_MCP = 5;
    private static final int INDEX_PIP = 6;
    private static final int INDEX_DIP = 7;
    private static final int INDEX_TIP = 8;
    private static final int MIDDLE_MCP = 9;
    private static final int MIDDLE_PIP = 10;
    private static final int MIDDLE_DIP = 11;
    private static final int MIDDLE_TIP = 12;
    private static final int RING_MCP = 13;
    private static final int RING_PIP = 14;
    private static final int RING_DIP = 15;
    private static final int RING_TIP = 16;
    private static final int PINKY_MCP = 17;
    private static final int PINKY_PIP = 18;
    private static final int PINKY_DIP = 19;
    private static final int PINKY_TIP = 20;

    private final List<List<LandmarkPoint>> smoothedHands = new ArrayList<>();
    private final SwipeTracker swipeTracker = new SwipeTracker();
    private AdaptiveGestureProfile adaptiveProfile = new AdaptiveGestureProfile();

    private float sensitivity = 0.58f;
    private float smoothing = 0.62f;
    private boolean pinchActive;
    private int pinchCandidateFrames;
    private boolean pinchSampleRecorded;
    private long lastOpenSampleAt;
    private boolean pinchSuppressedUntilRelease;
    private boolean fistActive;
    private int fistCandidateFrames;
    private boolean twoHandScaleActive;
    private int twoHandCandidateFrames;
    private float initialTwoHandDistance;
    private float initialTwoHandAngle;
    private float lastFistX;
    private float lastFistY;
    private float lastPinchX = 0.5f;
    private float lastPinchY = 0.5f;
    private float lastPinchContactMetric = Float.POSITIVE_INFINITY;
    private long palmDwellStartedAt;
    private float palmDwellX;
    private float palmDwellY;
    private boolean palmDwellEmitted;
    private long modeDwellStartedAt;
    private float modeDwellX;
    private float modeDwellY;
    private boolean modeDwellEmitted;
    private long shapeDwellStartedAt;
    private float shapeDwellX;
    private float shapeDwellY;
    private boolean shapeDwellEmitted;
    private boolean autoSpinActive;
    private int autoSpinCandidateFrames;
    private int autoSpinReleaseFrames;
    private Pose lastPose = Pose.NONE;

    public void configure(float sensitivity, float smoothing) {
        this.sensitivity = clamp01(sensitivity);
        this.smoothing = clamp01(smoothing);
    }

    public synchronized void setAdaptiveProfile(AdaptiveGestureProfile.Snapshot snapshot) {
        adaptiveProfile = new AdaptiveGestureProfile(snapshot);
    }

    public synchronized AdaptiveGestureProfile.Snapshot adaptiveSnapshot() {
        return adaptiveProfile.snapshot();
    }

    public synchronized void reset() {
        smoothedHands.clear();
        swipeTracker.resetAll();
        pinchActive = false;
        pinchCandidateFrames = 0;
        pinchSampleRecorded = false;
        lastOpenSampleAt = 0L;
        pinchSuppressedUntilRelease = false;
        lastPinchContactMetric = Float.POSITIVE_INFINITY;
        fistActive = false;
        fistCandidateFrames = 0;
        twoHandScaleActive = false;
        twoHandCandidateFrames = 0;
        initialTwoHandDistance = 0f;
        initialTwoHandAngle = 0f;
        palmDwellStartedAt = 0L;
        palmDwellEmitted = false;
        modeDwellStartedAt = 0L;
        modeDwellEmitted = false;
        shapeDwellStartedAt = 0L;
        shapeDwellEmitted = false;
        autoSpinActive = false;
        autoSpinCandidateFrames = 0;
        autoSpinReleaseFrames = 0;
        lastPose = Pose.NONE;
    }

    public static float pinchRatio(List<LandmarkPoint> hand) {
        return PinchDetector.pinchRatio(hand);
    }

    public synchronized GestureFrame process(
            List<List<LandmarkPoint>> detectedHands,
            long timestampMs
    ) {
        List<GestureEvent> events = new ArrayList<>(10);
        List<List<LandmarkPoint>> rawHands = validHands(detectedHands);
        List<List<LandmarkPoint>> hands = smoothHands(rawHands);

        if (hands.isEmpty()) {
            endActiveGestures(events, timestampMs, 0.5f, 0.5f);
            if (lastPose != Pose.NONE) {
                events.add(GestureEvent.at(GestureEvent.Type.HAND_LOST, 0.5f, 0.5f, timestampMs));
                events.add(GestureEvent.pose(Pose.NONE.label, 0.5f, 0.5f, timestampMs));
            }
            resetPalmTracking();
            lastPose = Pose.NONE;
            return new GestureFrame(hands, events, lastPose.label);
        }

        List<LandmarkPoint> primary = hands.get(0);
        LandmarkPoint cursor = primary.get(INDEX_TIP);
        events.add(GestureEvent.at(
                GestureEvent.Type.CURSOR,
                clamp01(cursor.x),
                clamp01(cursor.y),
                timestampMs
        ));

        float pinchStartThreshold = adaptiveProfile.startThreshold(sensitivity);
        float pinchReleaseThreshold = adaptiveProfile.releaseThreshold(sensitivity);
        PinchDetector.Observation primaryPinch = PinchDetector.evaluate(
                rawHands.get(0),
                pinchStartThreshold,
                pinchReleaseThreshold,
                pinchActive || twoHandScaleActive
        );
        boolean primaryPinching = pinchActive || twoHandScaleActive
                ? !primaryPinch.isOpen()
                : primaryPinch.isClosed();
        PinchDetector.Observation secondaryPinch = hands.size() > 1
                ? PinchDetector.evaluate(
                        rawHands.get(1),
                        pinchStartThreshold,
                        pinchReleaseThreshold,
                        twoHandScaleActive
                )
                : null;
        boolean secondaryPinching = secondaryPinch != null
                && (twoHandScaleActive
                ? !secondaryPinch.isOpen()
                : secondaryPinch.isClosed());
        float primaryContactMetric = Math.min(
                primaryPinch.tipRatio(),
                primaryPinch.distalSegmentRatio()
        );
        boolean rapidlyOpening = pinchActive
                && !twoHandScaleActive
                && primaryPinching
                && primaryContactMetric > pinchStartThreshold + 0.018f
                && primaryContactMetric - lastPinchContactMetric > 0.032f;
        if (rapidlyOpening) {
            primaryPinching = false;
        }

        if (hands.size() > 1 && primaryPinching && secondaryPinching) {
            twoHandCandidateFrames++;
            if (twoHandScaleActive || twoHandCandidateFrames >= 2) {
                handleTwoHandScale(hands, events, timestampMs);
            }
            resetModeTracking();
            Pose pose = Pose.PINCH;
            emitPoseIfChanged(pose, cursor, events, timestampMs);
            return new GestureFrame(hands, events, pose.label);
        }
        twoHandCandidateFrames = 0;

        if (twoHandScaleActive) {
            events.add(GestureEvent.scale(
                    GestureEvent.Type.TWO_HAND_SCALE_END,
                    cursor.x,
                    cursor.y,
                    1f,
                    timestampMs
            ));
            twoHandScaleActive = false;
            pinchSuppressedUntilRelease = true;
        }

        Metrics metrics = metrics(primary);
        Pose pose = classify(metrics, primaryPinching);
        updateAdaptiveProfile(rawHands.get(0), metrics, primaryPinching, timestampMs);
        handlePinch(primaryPinching, cursor, events, timestampMs);
        if (pinchActive) {
            lastPinchContactMetric = primaryContactMetric;
        } else if (!primaryPinching) {
            lastPinchContactMetric = Float.POSITIVE_INFINITY;
        }
        handleFist(pose == Pose.FIST && !pinchActive, cursor, events, timestampMs);
        boolean swipeEligible = !pinchActive
                && !fistActive
                && pose != Pose.TWO_FINGER
                && metrics.extendedCount >= 3;
        handlePalm(
                pose == Pose.OPEN_PALM && !pinchActive,
                swipeEligible,
                metrics.palmX,
                metrics.palmY,
                metrics.palmSize,
                events,
                timestampMs
        );
        handleModePose(pose == Pose.TWO_FINGER && !pinchActive, cursor, events, timestampMs);
        handleShapePose(pose == Pose.THUMBS_UP && !pinchActive, cursor, events, timestampMs);
        handleAutoSpin(pose == Pose.AUTO_SPIN && !pinchActive, cursor, events, timestampMs);
        emitPoseIfChanged(pose, cursor, events, timestampMs);

        return new GestureFrame(hands, events, pose.label);
    }

    private static List<List<LandmarkPoint>> validHands(
            List<List<LandmarkPoint>> detected
    ) {
        if (detected == null || detected.isEmpty()) {
            return List.of();
        }
        List<List<LandmarkPoint>> validHands = new ArrayList<>(detected.size());
        for (List<LandmarkPoint> hand : detected) {
            if (hand != null && hand.size() >= 21) {
                validHands.add(hand);
            }
        }
        if (validHands.isEmpty()) {
            return List.of();
        }
        if (validHands.size() > 1) {
            // MediaPipe does not guarantee a persistent result-list order.
            // Spatial ordering prevents a harmless hand-order swap from
            // becoming a 180-degree transform jump.
            validHands.sort(Comparator.comparingDouble(
                    hand -> hand.get(WRIST).x
            ));
        }
        return validHands;
    }

    private List<List<LandmarkPoint>> smoothHands(List<List<LandmarkPoint>> validHands) {
        if (validHands.isEmpty()) {
            smoothedHands.clear();
            return List.of();
        }

        while (smoothedHands.size() > validHands.size()) {
            smoothedHands.remove(smoothedHands.size() - 1);
        }

        for (int handIndex = 0; handIndex < validHands.size(); handIndex++) {
            List<LandmarkPoint> raw = validHands.get(handIndex);
            if (handIndex >= smoothedHands.size()) {
                smoothedHands.add(Collections.unmodifiableList(new ArrayList<>(raw)));
                continue;
            }
            List<LandmarkPoint> previous = smoothedHands.get(handIndex);
            if (previous.size() != raw.size()) {
                smoothedHands.set(
                        handIndex,
                        Collections.unmodifiableList(new ArrayList<>(raw))
                );
                continue;
            }
            float baseAlpha = 0.68f - smoothing * 0.48f;
            List<LandmarkPoint> next = new ArrayList<>(raw.size());
            for (int pointIndex = 0; pointIndex < raw.size(); pointIndex++) {
                LandmarkPoint oldPoint = previous.get(pointIndex);
                LandmarkPoint newPoint = raw.get(pointIndex);
                float speed = oldPoint.distance2D(newPoint);
                float adaptiveAlpha = clamp(baseAlpha + speed * 3.4f, baseAlpha, 0.88f);
                next.add(oldPoint.mix(newPoint, adaptiveAlpha));
            }
            smoothedHands.set(handIndex, Collections.unmodifiableList(next));
        }

        List<List<LandmarkPoint>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(validHands.size(), smoothedHands.size()); i++) {
            if (smoothedHands.get(i).size() >= 21) {
                result.add(smoothedHands.get(i));
            }
        }
        return result;
    }

    private void handlePinch(
            boolean pinching,
            LandmarkPoint cursor,
            List<GestureEvent> events,
            long timestampMs
    ) {
        if (pinchSuppressedUntilRelease) {
            if (!pinching) {
                pinchSuppressedUntilRelease = false;
            }
            return;
        }

        if (!pinchActive) {
            pinchCandidateFrames = pinching ? pinchCandidateFrames + 1 : 0;
            if (pinchCandidateFrames >= 2) {
                pinchActive = true;
                pinchCandidateFrames = 0;
                pinchSampleRecorded = false;
                lastPinchX = cursor.x;
                lastPinchY = cursor.y;
                events.add(GestureEvent.at(
                        GestureEvent.Type.PINCH_START,
                        cursor.x,
                        cursor.y,
                        timestampMs
                ));
            }
            return;
        }

        if (pinching) {
            lastPinchX = cursor.x;
            lastPinchY = cursor.y;
            events.add(GestureEvent.at(
                    GestureEvent.Type.PINCH_MOVE,
                    cursor.x,
                    cursor.y,
                    timestampMs
            ));
        } else {
            pinchActive = false;
            pinchSampleRecorded = false;
            events.add(GestureEvent.at(
                    GestureEvent.Type.PINCH_END,
                    lastPinchX,
                    lastPinchY,
                    timestampMs
            ));
        }
    }

    private void handleFist(
            boolean fist,
            LandmarkPoint cursor,
            List<GestureEvent> events,
            long timestampMs
    ) {
        if (pinchActive) {
            fist = false;
        }
        if (!fistActive) {
            fistCandidateFrames = fist ? fistCandidateFrames + 1 : 0;
            if (fistCandidateFrames >= 3) {
                fistActive = true;
                fistCandidateFrames = 0;
                lastFistX = cursor.x;
                lastFistY = cursor.y;
                events.add(GestureEvent.at(
                        GestureEvent.Type.FIST_START,
                        cursor.x,
                        cursor.y,
                        timestampMs
                ));
            }
            return;
        }
        if (!fist) {
            fistActive = false;
            events.add(GestureEvent.at(
                    GestureEvent.Type.FIST_END,
                    cursor.x,
                    cursor.y,
                    timestampMs
            ));
            return;
        }
        float dx = cursor.x - lastFistX;
        float dy = cursor.y - lastFistY;
        lastFistX = cursor.x;
        lastFistY = cursor.y;
        events.add(GestureEvent.move(
                GestureEvent.Type.FIST_MOVE,
                cursor.x,
                cursor.y,
                dx,
                dy,
                timestampMs
        ));
    }

    private void handlePalm(
            boolean openPalm,
            boolean swipeEligible,
            float palmX,
            float palmY,
            float palmSize,
            List<GestureEvent> events,
            long timestampMs
    ) {
        if (openPalm) {
            if (palmDwellStartedAt == 0L
                    || distance(palmX, palmY, palmDwellX, palmDwellY) > 0.045f) {
                palmDwellStartedAt = timestampMs;
                palmDwellX = palmX;
                palmDwellY = palmY;
                palmDwellEmitted = false;
            } else if (!palmDwellEmitted
                    && timestampMs - palmDwellStartedAt >= 520L) {
                palmDwellEmitted = true;
                events.add(GestureEvent.at(
                        GestureEvent.Type.OPEN_PALM_DWELL,
                        palmX,
                        palmY,
                        timestampMs
                ));
            }
        } else {
            palmDwellStartedAt = 0L;
            palmDwellEmitted = false;
        }

        GestureEvent.Type swipe = swipeTracker.update(
                timestampMs,
                palmX,
                palmY,
                palmSize,
                sensitivity,
                swipeEligible
        );
        if (swipe != null) {
            events.add(GestureEvent.at(
                    swipe,
                    palmX,
                    palmY,
                    timestampMs
            ));
            palmDwellStartedAt = timestampMs;
            palmDwellX = palmX;
            palmDwellY = palmY;
            palmDwellEmitted = false;
        }
    }

    private void handleModePose(
            boolean twoFingerPose,
            LandmarkPoint cursor,
            List<GestureEvent> events,
            long timestampMs
    ) {
        if (!twoFingerPose) {
            resetModeTracking();
            return;
        }
        if (modeDwellStartedAt == 0L
                || distance(cursor.x, cursor.y, modeDwellX, modeDwellY) > 0.055f) {
            modeDwellStartedAt = timestampMs;
            modeDwellX = cursor.x;
            modeDwellY = cursor.y;
            modeDwellEmitted = false;
            return;
        }
        if (!modeDwellEmitted && timestampMs - modeDwellStartedAt >= 560L) {
            modeDwellEmitted = true;
            events.add(GestureEvent.at(
                    GestureEvent.Type.MODE_MENU,
                    cursor.x,
                    cursor.y,
                    timestampMs
            ));
        }
    }

    private void handleShapePose(
            boolean thumbsUp,
            LandmarkPoint cursor,
            List<GestureEvent> events,
            long timestampMs
    ) {
        if (!thumbsUp) {
            shapeDwellStartedAt = 0L;
            shapeDwellEmitted = false;
            return;
        }
        if (shapeDwellStartedAt == 0L
                || distance(cursor.x, cursor.y, shapeDwellX, shapeDwellY) > 0.075f) {
            shapeDwellStartedAt = timestampMs;
            shapeDwellX = cursor.x;
            shapeDwellY = cursor.y;
            shapeDwellEmitted = false;
            return;
        }
        if (!shapeDwellEmitted && timestampMs - shapeDwellStartedAt >= 340L) {
            shapeDwellEmitted = true;
            events.add(GestureEvent.at(
                    GestureEvent.Type.SHAPE_MENU,
                    cursor.x,
                    cursor.y,
                    timestampMs
            ));
        }
    }

    private void handleAutoSpin(
            boolean spinPose,
            LandmarkPoint cursor,
            List<GestureEvent> events,
            long timestampMs
    ) {
        if (!autoSpinActive) {
            autoSpinReleaseFrames = 0;
            autoSpinCandidateFrames = spinPose ? autoSpinCandidateFrames + 1 : 0;
            if (autoSpinCandidateFrames >= 3) {
                autoSpinCandidateFrames = 0;
                autoSpinActive = true;
                events.add(GestureEvent.at(
                        GestureEvent.Type.AUTO_SPIN_START,
                        cursor.x,
                        cursor.y,
                        timestampMs
                ));
            }
            return;
        }
        if (spinPose) {
            autoSpinReleaseFrames = 0;
            return;
        }
        autoSpinReleaseFrames++;
        if (autoSpinReleaseFrames >= 2) {
            autoSpinReleaseFrames = 0;
            autoSpinActive = false;
            events.add(GestureEvent.at(
                    GestureEvent.Type.AUTO_SPIN_END,
                    cursor.x,
                    cursor.y,
                    timestampMs
            ));
        }
    }

    private void handleTwoHandScale(
            List<List<LandmarkPoint>> hands,
            List<GestureEvent> events,
            long timestampMs
    ) {
        LandmarkPoint first = hands.get(0).get(INDEX_TIP);
        LandmarkPoint second = hands.get(1).get(INDEX_TIP);
        float centerX = (first.x + second.x) * 0.5f;
        float centerY = (first.y + second.y) * 0.5f;
        float distance = Math.max(0.02f, first.distance2D(second));
        float angle = (float) Math.toDegrees(Math.atan2(
                second.y - first.y,
                second.x - first.x
        ));

        if (!twoHandScaleActive) {
            if (pinchActive) {
                pinchActive = false;
                events.add(GestureEvent.at(
                        GestureEvent.Type.PINCH_END,
                        lastPinchX,
                        lastPinchY,
                        timestampMs
                ));
            }
            if (fistActive) {
                fistActive = false;
                events.add(GestureEvent.at(
                        GestureEvent.Type.FIST_END,
                        first.x,
                        first.y,
                        timestampMs
                ));
            }
            twoHandScaleActive = true;
            initialTwoHandDistance = distance;
            initialTwoHandAngle = angle;
            events.add(GestureEvent.transform(
                    GestureEvent.Type.TWO_HAND_SCALE_START,
                    centerX,
                    centerY,
                    1f,
                    0f,
                    timestampMs
            ));
        } else {
            float factor = clamp(distance / initialTwoHandDistance, 0.25f, 4f);
            float rotation = normalizeAngle(angle - initialTwoHandAngle);
            events.add(GestureEvent.transform(
                    GestureEvent.Type.TWO_HAND_SCALE_UPDATE,
                    centerX,
                    centerY,
                    factor,
                    rotation,
                    timestampMs
            ));
        }
        resetPalmTracking();
    }

    private void endActiveGestures(
            List<GestureEvent> events,
            long timestampMs,
            float x,
            float y
    ) {
        if (pinchActive) {
            events.add(GestureEvent.at(
                    GestureEvent.Type.PINCH_END,
                    lastPinchX,
                    lastPinchY,
                    timestampMs
            ));
        }
        if (fistActive) {
            events.add(GestureEvent.at(GestureEvent.Type.FIST_END, x, y, timestampMs));
        }
        if (twoHandScaleActive) {
            events.add(GestureEvent.scale(
                    GestureEvent.Type.TWO_HAND_SCALE_END,
                    x,
                    y,
                    1f,
                    timestampMs
            ));
        }
        if (autoSpinActive) {
            events.add(GestureEvent.at(
                    GestureEvent.Type.AUTO_SPIN_END,
                    x,
                    y,
                    timestampMs
            ));
        }
        pinchActive = false;
        fistActive = false;
        twoHandScaleActive = false;
        twoHandCandidateFrames = 0;
        pinchCandidateFrames = 0;
        pinchSampleRecorded = false;
        lastPinchContactMetric = Float.POSITIVE_INFINITY;
        fistCandidateFrames = 0;
        pinchSuppressedUntilRelease = false;
        autoSpinActive = false;
        autoSpinCandidateFrames = 0;
        autoSpinReleaseFrames = 0;
        resetModeTracking();
        shapeDwellStartedAt = 0L;
        shapeDwellEmitted = false;
    }

    private void emitPoseIfChanged(
            Pose pose,
            LandmarkPoint cursor,
            List<GestureEvent> events,
            long timestampMs
    ) {
        if (pose != lastPose) {
            events.add(GestureEvent.pose(pose.label, cursor.x, cursor.y, timestampMs));
            lastPose = pose;
        }
    }

    private void updateAdaptiveProfile(
            List<LandmarkPoint> rawHand,
            Metrics metrics,
            boolean pinching,
            long timestampMs
    ) {
        float ratio = pinchRatio(rawHand);
        float highConfidenceLimit = Math.min(
                0.28f,
                adaptiveProfile.startThreshold(sensitivity) * 0.80f
        );
        if (pinchActive
                && pinching
                && !pinchSampleRecorded
                && ratio <= highConfidenceLimit) {
            adaptiveProfile.observeConfirmedPinch(ratio);
            pinchSampleRecorded = true;
            return;
        }
        float clearlyOpenThreshold = Math.max(
                0.60f,
                adaptiveProfile.releaseThreshold(sensitivity) + 0.18f
        );
        if (!pinchActive
                && metrics.extendedCount >= 3
                && ratio >= clearlyOpenThreshold
                && timestampMs - lastOpenSampleAt >= 1200L) {
            adaptiveProfile.observeClearlyOpen(ratio);
            lastOpenSampleAt = timestampMs;
        }
    }

    static Pose classify(Metrics metrics, boolean pinching) {
        if (pinching) {
            return Pose.PINCH;
        }
        boolean threeFingersCurled = !metrics.middleExtended
                && !metrics.ringExtended
                && !metrics.pinkyExtended;
        if (metrics.thumbExtended
                && threeFingersCurled
                && metrics.indexPresented
                && metrics.indexSideways
                && metrics.thumbIndexAngle >= 28f) {
            return Pose.AUTO_SPIN;
        }
        if (metrics.thumbExtended
                && threeFingersCurled
                && !metrics.indexExtended
                && !metrics.indexSideways
                && metrics.thumbAbovePalm) {
            return Pose.THUMBS_UP;
        }
        if (metrics.extendedCount <= 1 && metrics.meanTipToPalm < metrics.palmSize * 1.22f) {
            return Pose.FIST;
        }
        if (metrics.extendedCount >= 4) {
            return Pose.OPEN_PALM;
        }
        if (metrics.indexExtended
                && metrics.middleExtended
                && !metrics.ringExtended
                && !metrics.pinkyExtended) {
            return Pose.TWO_FINGER;
        }
        if (metrics.indexExtended && metrics.extendedCount <= 2) {
            return Pose.POINT;
        }
        return Pose.TRACKING;
    }

    static Metrics metrics(List<LandmarkPoint> h) {
        LandmarkPoint wrist = h.get(WRIST);
        float palmX = (h.get(INDEX_MCP).x + h.get(MIDDLE_MCP).x
                + h.get(RING_MCP).x + h.get(PINKY_MCP).x) * 0.25f;
        float palmY = (h.get(INDEX_MCP).y + h.get(MIDDLE_MCP).y
                + h.get(RING_MCP).y + h.get(PINKY_MCP).y) * 0.25f;
        float palmSize = Math.max(0.035f, wrist.distance2D(h.get(MIDDLE_MCP)));

        boolean thumb = angle(h.get(THUMB_MCP), h.get(THUMB_IP), h.get(THUMB_TIP)) >= 118f
                && h.get(THUMB_TIP).distance2D(wrist)
                >= h.get(THUMB_IP).distance2D(wrist) * 1.01f;
        boolean index = fingerExtended(h, INDEX_MCP, INDEX_PIP, INDEX_DIP, INDEX_TIP, wrist);
        boolean middle = fingerExtended(h, MIDDLE_MCP, MIDDLE_PIP, MIDDLE_DIP, MIDDLE_TIP, wrist);
        boolean ring = fingerExtended(h, RING_MCP, RING_PIP, RING_DIP, RING_TIP, wrist);
        boolean pinky = fingerExtended(h, PINKY_MCP, PINKY_PIP, PINKY_DIP, PINKY_TIP, wrist);
        int extended = (thumb ? 1 : 0)
                + (index ? 1 : 0)
                + (middle ? 1 : 0)
                + (ring ? 1 : 0)
                + (pinky ? 1 : 0);
        float tipDistance = (
                distanceTo(h.get(THUMB_TIP), palmX, palmY)
                        + distanceTo(h.get(INDEX_TIP), palmX, palmY)
                        + distanceTo(h.get(MIDDLE_TIP), palmX, palmY)
                        + distanceTo(h.get(RING_TIP), palmX, palmY)
                        + distanceTo(h.get(PINKY_TIP), palmX, palmY)
        ) / 5f;
        float indexReach = distanceTo(h.get(INDEX_TIP), palmX, palmY);
        boolean indexPresented = indexReach >= palmSize * 0.92f
                && angle(h.get(INDEX_MCP), h.get(INDEX_PIP), h.get(INDEX_TIP)) >= 55f
                && h.get(INDEX_TIP).distance2D(wrist)
                >= h.get(INDEX_PIP).distance2D(wrist) * 0.84f;
        float indexDx = h.get(INDEX_TIP).x - palmX;
        float indexDy = h.get(INDEX_TIP).y - palmY;
        boolean indexSideways = Math.abs(indexDx) >= palmSize * 0.42f
                && Math.abs(indexDx) >= Math.abs(indexDy) * 0.60f;
        boolean thumbAbovePalm = h.get(THUMB_TIP).y <= palmY - palmSize * 0.20f;
        float thumbIndexAngle = vectorAngle(
                h.get(THUMB_TIP).x - palmX,
                h.get(THUMB_TIP).y - palmY,
                h.get(INDEX_TIP).x - palmX,
                h.get(INDEX_TIP).y - palmY
        );
        return new Metrics(
                palmX,
                palmY,
                palmSize,
                tipDistance,
                extended,
                thumb,
                index,
                middle,
                ring,
                pinky,
                indexPresented,
                indexSideways,
                thumbAbovePalm,
                thumbIndexAngle
        );
    }

    private static boolean fingerExtended(
            List<LandmarkPoint> hand,
            int mcp,
            int pip,
            int dip,
            int tip,
            LandmarkPoint wrist
    ) {
        float firstAngle = angle(hand.get(mcp), hand.get(pip), hand.get(dip));
        float secondAngle = angle(hand.get(pip), hand.get(dip), hand.get(tip));
        float tipDistance = hand.get(tip).distance2D(wrist);
        float pipDistance = hand.get(pip).distance2D(wrist);
        return firstAngle >= 138f
                && secondAngle >= 132f
                && tipDistance >= pipDistance * 1.08f;
    }

    private static float vectorAngle(
            float firstX,
            float firstY,
            float secondX,
            float secondY
    ) {
        float denominator = (float) Math.sqrt(
                (firstX * firstX + firstY * firstY)
                        * (secondX * secondX + secondY * secondY)
        );
        if (denominator < 1e-6f) {
            return 0f;
        }
        float cosine = clamp(
                (firstX * secondX + firstY * secondY) / denominator,
                -1f,
                1f
        );
        return (float) Math.toDegrees(Math.acos(cosine));
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

    private void resetPalmTracking() {
        swipeTracker.reset();
        palmDwellStartedAt = 0L;
        palmDwellEmitted = false;
    }

    private void resetModeTracking() {
        modeDwellStartedAt = 0L;
        modeDwellEmitted = false;
    }

    private static float normalizeAngle(float value) {
        float normalized = value % 360f;
        if (normalized > 180f) {
            normalized -= 360f;
        } else if (normalized < -180f) {
            normalized += 360f;
        }
        return normalized;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    private static float distanceTo(LandmarkPoint point, float x, float y) {
        return (float) Math.hypot(point.x - x, point.y - y);
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

    static final class Metrics {
        final float palmX;
        final float palmY;
        final float palmSize;
        final float meanTipToPalm;
        final int extendedCount;
        final boolean thumbExtended;
        final boolean indexExtended;
        final boolean middleExtended;
        final boolean ringExtended;
        final boolean pinkyExtended;
        final boolean indexPresented;
        final boolean indexSideways;
        final boolean thumbAbovePalm;
        final float thumbIndexAngle;

        Metrics(
                float palmX,
                float palmY,
                float palmSize,
                float meanTipToPalm,
                int extendedCount,
                boolean thumbExtended,
                boolean indexExtended,
                boolean middleExtended,
                boolean ringExtended,
                boolean pinkyExtended,
                boolean indexPresented,
                boolean indexSideways,
                boolean thumbAbovePalm,
                float thumbIndexAngle
        ) {
            this.palmX = palmX;
            this.palmY = palmY;
            this.palmSize = palmSize;
            this.meanTipToPalm = meanTipToPalm;
            this.extendedCount = extendedCount;
            this.thumbExtended = thumbExtended;
            this.indexExtended = indexExtended;
            this.middleExtended = middleExtended;
            this.ringExtended = ringExtended;
            this.pinkyExtended = pinkyExtended;
            this.indexPresented = indexPresented;
            this.indexSideways = indexSideways;
            this.thumbAbovePalm = thumbAbovePalm;
            this.thumbIndexAngle = thumbIndexAngle;
        }
    }

}
