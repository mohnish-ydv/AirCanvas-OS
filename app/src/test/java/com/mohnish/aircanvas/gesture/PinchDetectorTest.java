package com.mohnish.aircanvas.gesture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class PinchDetectorTest {
    @Test
    public void distalIndexPadContactCountsAsPinch() {
        AdaptiveGestureProfile profile = new AdaptiveGestureProfile();
        PinchDetector.Observation result = PinchDetector.evaluate(
                sideContactPinch(),
                profile.startThreshold(0.76f),
                profile.releaseThreshold(0.76f),
                false
        );
        assertTrue(result.isClosed());
        assertTrue(result.distalSegmentRatio() < result.tipRatio());
    }


    @Test
    public void curledSupportFingersStillAllowNaturalPinch() {
        List<LandmarkPoint> hand = new ArrayList<>(sideContactPinch());
        List<LandmarkPoint> curled = fistHand();
        for (int index = 9; index < 21; index++) {
            hand.set(index, curled.get(index));
        }
        AdaptiveGestureProfile profile = new AdaptiveGestureProfile();
        PinchDetector.Observation result = PinchDetector.evaluate(
                hand,
                profile.startThreshold(0.76f),
                profile.releaseThreshold(0.76f),
                false
        );
        assertTrue(result.isClosed());
    }

    @Test
    public void visibleGapIsNotClosed() {
        AdaptiveGestureProfile profile = new AdaptiveGestureProfile();
        PinchDetector.Observation result = PinchDetector.evaluate(
                nearPinch(0.060f),
                profile.startThreshold(1f),
                profile.releaseThreshold(1f),
                false
        );
        assertFalse(result.isClosed());
    }

    @Test
    public void fistContactIsRejected() {
        List<LandmarkPoint> fist = new ArrayList<>(fistHand());
        LandmarkPoint index = fist.get(8);
        fist.set(4, new LandmarkPoint(index.x - 0.006f, index.y + 0.004f, 0f));
        AdaptiveGestureProfile profile = new AdaptiveGestureProfile();
        PinchDetector.Observation result = PinchDetector.evaluate(
                fist,
                profile.startThreshold(0.76f),
                profile.releaseThreshold(0.76f),
                false
        );
        assertFalse(result.isClosed());
    }

    private static List<LandmarkPoint> sideContactPinch() {
        List<LandmarkPoint> result = new ArrayList<>(openHand());
        result.set(6, new LandmarkPoint(0.43f, 0.45f, 0f));
        result.set(7, new LandmarkPoint(0.47f, 0.31f, 0f));
        result.set(8, new LandmarkPoint(0.45f, 0.20f, 0f));
        result.set(4, new LandmarkPoint(0.46f, 0.255f, 0.01f));
        return result;
    }

    private static List<LandmarkPoint> nearPinch(float gap) {
        List<LandmarkPoint> result = new ArrayList<>(openHand());
        LandmarkPoint index = result.get(8);
        result.set(4, new LandmarkPoint(index.x - gap, index.y, 0f));
        return result;
    }

    private static List<LandmarkPoint> openHand() {
        return points(new float[][]{
                {0.50f, 0.92f}, {0.43f, 0.82f}, {0.36f, 0.72f},
                {0.28f, 0.64f}, {0.18f, 0.55f},
                {0.42f, 0.68f}, {0.42f, 0.48f}, {0.42f, 0.31f}, {0.42f, 0.14f},
                {0.50f, 0.67f}, {0.50f, 0.43f}, {0.50f, 0.25f}, {0.50f, 0.08f},
                {0.58f, 0.69f}, {0.59f, 0.48f}, {0.60f, 0.33f}, {0.61f, 0.18f},
                {0.66f, 0.72f}, {0.69f, 0.55f}, {0.71f, 0.43f}, {0.74f, 0.31f}
        });
    }

    private static List<LandmarkPoint> fistHand() {
        return points(new float[][]{
                {0.50f, 0.92f}, {0.43f, 0.80f}, {0.39f, 0.72f},
                {0.45f, 0.67f}, {0.50f, 0.65f},
                {0.42f, 0.67f}, {0.42f, 0.56f}, {0.48f, 0.59f}, {0.50f, 0.66f},
                {0.50f, 0.65f}, {0.50f, 0.54f}, {0.54f, 0.58f}, {0.54f, 0.66f},
                {0.58f, 0.67f}, {0.58f, 0.56f}, {0.61f, 0.60f}, {0.59f, 0.67f},
                {0.66f, 0.70f}, {0.65f, 0.60f}, {0.67f, 0.63f}, {0.64f, 0.70f}
        });
    }

    private static List<LandmarkPoint> points(float[][] values) {
        List<LandmarkPoint> result = new ArrayList<>(values.length);
        for (float[] value : values) {
            result.add(new LandmarkPoint(value[0], value[1], 0f));
        }
        return result;
    }
}
