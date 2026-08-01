package com.mohnish.aircanvas.gesture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class GestureEngineTest {
    @Test
    public void openPalmIsClassifiedAcrossRotationIndependentGeometry() {
        GestureEngine engine = new GestureEngine();
        GestureFrame frame = engine.process(List.of(openHand(0f)), 100L);
        assertEquals(GestureEngine.Pose.OPEN_PALM.label, frame.pose);
    }

    @Test
    public void pinchStartUsesDebounceAndReleaseIsReliable() {
        GestureEngine engine = new GestureEngine();
        engine.configure(0.6f, 0f);

        GestureFrame first = engine.process(List.of(pinchedHand(0f)), 100L);
        assertTrue(first.events.stream().noneMatch(
                event -> event.type == GestureEvent.Type.PINCH_START
        ));
        GestureFrame second = engine.process(List.of(pinchedHand(0f)), 160L);
        assertTrue(second.events.stream().anyMatch(
                event -> event.type == GestureEvent.Type.PINCH_START
        ));

        boolean released = false;
        for (int index = 0; index < 8; index++) {
            GestureFrame frame = engine.process(List.of(openHand(0f)), 280L + index * 60L);
            released |= frame.events.stream().anyMatch(
                    event -> event.type == GestureEvent.Type.PINCH_END
            );
        }
        assertTrue(released);
    }

    @Test
    public void pinchReleaseIsImmediateAndUsesLastClosedPinchPosition() {
        GestureEngine engine = new GestureEngine();
        engine.configure(0.6f, 0.62f);
        engine.process(List.of(pinchedHand(0f)), 100L);
        GestureFrame started = engine.process(List.of(pinchedHand(0f)), 160L);
        GestureEvent start = started.events.stream()
                .filter(event -> event.type == GestureEvent.Type.PINCH_START)
                .findFirst()
                .orElseThrow();

        GestureFrame released = engine.process(
                List.of(shifted(releasedPinchHand(0f), 0.24f, 0.14f)),
                280L
        );
        GestureEvent end = released.events.stream()
                .filter(event -> event.type == GestureEvent.Type.PINCH_END)
                .findFirst()
                .orElseThrow();

        assertEquals(start.x, end.x, 0.03f);
        assertEquals(start.y, end.y, 0.03f);
    }

    @Test
    public void fistStartsAfterStableFrames() {
        GestureEngine engine = new GestureEngine();
        boolean started = false;
        for (int index = 0; index < 4; index++) {
            GestureFrame frame = engine.process(List.of(fistHand(0f)), 100L + index * 60L);
            started |= frame.events.stream().anyMatch(
                    event -> event.type == GestureEvent.Type.FIST_START
            );
        }
        assertTrue(started);
    }

    @Test
    public void openPalmHorizontalMotionEmitsSwipe() {
        GestureEngine engine = new GestureEngine();
        engine.configure(0.6f, 0f);
        boolean swipedRight = false;
        for (int index = 0; index < 7; index++) {
            float offset = -0.24f + index * 0.09f;
            GestureFrame frame = engine.process(
                    List.of(openHand(offset)),
                    100L + index * 60L
            );
            swipedRight |= frame.events.stream().anyMatch(
                    event -> event.type == GestureEvent.Type.SWIPE_RIGHT
            );
        }
        assertTrue(swipedRight);
    }

    @Test
    public void twoPinchesProduceScaleLifecycle() {
        GestureEngine engine = new GestureEngine();
        GestureFrame candidate = engine.process(
                List.of(pinchedHand(-0.20f), pinchedHand(0.20f)),
                100L
        );
        assertTrue(candidate.events.stream().noneMatch(
                event -> event.type == GestureEvent.Type.TWO_HAND_SCALE_START
        ));
        GestureFrame start = engine.process(
                List.of(pinchedHand(-0.20f), pinchedHand(0.20f)),
                160L
        );
        assertTrue(start.events.stream().anyMatch(
                event -> event.type == GestureEvent.Type.TWO_HAND_SCALE_START
        ));

        GestureFrame update = engine.process(
                List.of(pinchedHand(-0.30f), pinchedHand(0.30f)),
                220L
        );
        assertTrue(update.events.stream().anyMatch(
                event -> event.type == GestureEvent.Type.TWO_HAND_SCALE_UPDATE
                        && event.scale > 1f
        ));

        GestureFrame end = engine.process(List.of(openHand(0f)), 280L);
        assertTrue(end.events.stream().anyMatch(
                event -> event.type == GestureEvent.Type.TWO_HAND_SCALE_END
        ));
    }

    @Test
    public void twoHandTwistProducesRotationAlongsideScale() {
        GestureEngine engine = new GestureEngine();
        engine.configure(0.6f, 0f);
        List<LandmarkPoint> first = shifted(pinchedHand(0f), -0.20f, -0.10f);
        List<LandmarkPoint> second = shifted(pinchedHand(0f), 0.20f, 0.10f);
        engine.process(List.of(first, second), 100L);
        engine.process(List.of(first, second), 160L);
        GestureFrame update = engine.process(
                List.of(
                        shifted(pinchedHand(0f), -0.20f, 0.10f),
                        shifted(pinchedHand(0f), 0.20f, -0.10f)
                ),
                220L
        );
        assertTrue(update.events.stream().anyMatch(
                event -> event.type == GestureEvent.Type.TWO_HAND_SCALE_UPDATE
                        && Math.abs(event.rotation) > 20f
        ));
    }

    @Test
    public void detectorHandOrderSwapDoesNotCauseRotationJump() {
        GestureEngine engine = new GestureEngine();
        List<LandmarkPoint> left = pinchedHand(-0.20f);
        List<LandmarkPoint> right = pinchedHand(0.20f);
        engine.process(List.of(left, right), 100L);
        engine.process(List.of(left, right), 160L);

        GestureFrame update = engine.process(List.of(right, left), 220L);
        assertTrue(update.events.stream().anyMatch(
                event -> event.type == GestureEvent.Type.TWO_HAND_SCALE_UPDATE
                        && Math.abs(event.rotation) < 5f
                        && Math.abs(event.scale - 1f) < 0.08f
        ));
    }

    @Test
    public void openPalmVerticalMotionEmitsSwipeUp() {
        GestureEngine engine = new GestureEngine();
        engine.configure(0.6f, 0f);
        boolean swipedUp = false;
        for (int index = 0; index < 7; index++) {
            GestureFrame frame = engine.process(
                    List.of(shifted(openHand(0f), 0f, 0.20f - index * 0.07f)),
                    100L + index * 60L
            );
            swipedUp |= frame.events.stream().anyMatch(
                    event -> event.type == GestureEvent.Type.SWIPE_UP
            );
        }
        assertTrue(swipedUp);
    }

    @Test
    public void allFourSwipeDirectionsAreRecognizedWithDefaultSmoothing() {
        assertEquals(GestureEvent.Type.SWIPE_LEFT, swipe(-0.30f, 0f));
        assertEquals(GestureEvent.Type.SWIPE_RIGHT, swipe(0.30f, 0f));
        assertEquals(GestureEvent.Type.SWIPE_UP, swipe(0f, -0.30f));
        assertEquals(GestureEvent.Type.SWIPE_DOWN, swipe(0f, 0.30f));
    }

    @Test
    public void steadyTwoFingerPoseOpensModeMenuOnlyOnce() {
        GestureEngine engine = new GestureEngine();
        long modeEvents = 0L;
        for (int index = 0; index < 10; index++) {
            GestureFrame frame = engine.process(
                    List.of(twoFingerHand()),
                    100L + index * 100L
            );
            modeEvents += frame.events.stream().filter(
                    event -> event.type == GestureEvent.Type.MODE_MENU
            ).count();
        }
        assertEquals(1L, modeEvents);
    }

    @Test
    public void steadyOpenPalmEmitsOnlyOneDwellEvent() {
        GestureEngine engine = new GestureEngine();
        long dwellEvents = 0L;
        for (int index = 0; index < 10; index++) {
            GestureFrame frame = engine.process(
                    List.of(openHand(0f)),
                    100L + index * 100L
            );
            dwellEvents += frame.events.stream().filter(
                    event -> event.type == GestureEvent.Type.OPEN_PALM_DWELL
            ).count();
        }
        assertEquals(1L, dwellEvents);
    }

    @Test
    public void losingTheHandClosesAnActivePinch() {
        GestureEngine engine = new GestureEngine();
        engine.process(List.of(pinchedHand(0f)), 100L);
        engine.process(List.of(pinchedHand(0f)), 160L);
        engine.process(List.of(pinchedHand(0f)), 220L);

        GestureFrame lost = engine.process(List.of(), 280L);
        assertTrue(lost.events.stream().anyMatch(
                event -> event.type == GestureEvent.Type.PINCH_END
        ));
        assertTrue(lost.events.stream().anyMatch(
                event -> event.type == GestureEvent.Type.HAND_LOST
        ));
        assertEquals(GestureEngine.Pose.NONE.label, lost.pose);
    }

    @Test
    public void malformedLandmarksCannotReuseAStaleHand() {
        GestureEngine engine = new GestureEngine();
        engine.process(List.of(openHand(0f)), 100L);

        GestureFrame frame = engine.process(
                List.of(List.of(new LandmarkPoint(0.5f, 0.5f, 0f))),
                160L
        );
        assertTrue(frame.hands.isEmpty());
        assertEquals(GestureEngine.Pose.NONE.label, frame.pose);
    }

    @Test
    public void resetClearsAnInProgressGestureSession() {
        GestureEngine engine = new GestureEngine();
        engine.process(List.of(pinchedHand(0f)), 100L);
        engine.process(List.of(pinchedHand(0f)), 160L);
        engine.reset();

        GestureFrame firstAfterReset = engine.process(
                List.of(pinchedHand(0f)),
                220L
        );
        assertTrue(firstAfterReset.events.stream().noneMatch(
                event -> event.type == GestureEvent.Type.PINCH_MOVE
                        || event.type == GestureEvent.Type.PINCH_START
        ));
    }

    @Test
    public void nonFiniteConfigurationFallsBackToStableDefaults() {
        GestureEngine engine = new GestureEngine();
        engine.configure(Float.NaN, Float.POSITIVE_INFINITY);
        engine.process(List.of(pinchedHand(0f)), 100L);
        GestureFrame frame = engine.process(List.of(pinchedHand(0f)), 160L);

        assertTrue(frame.events.stream().anyMatch(
                event -> event.type == GestureEvent.Type.PINCH_START
        ));
    }

    private static List<LandmarkPoint> openHand(float offsetX) {
        float[][] values = {
                {0.50f, 0.92f}, {0.43f, 0.82f}, {0.36f, 0.72f},
                {0.28f, 0.64f}, {0.18f, 0.55f},
                {0.42f, 0.68f}, {0.42f, 0.48f}, {0.42f, 0.31f}, {0.42f, 0.14f},
                {0.50f, 0.67f}, {0.50f, 0.43f}, {0.50f, 0.25f}, {0.50f, 0.08f},
                {0.58f, 0.69f}, {0.59f, 0.48f}, {0.60f, 0.33f}, {0.61f, 0.18f},
                {0.66f, 0.72f}, {0.69f, 0.55f}, {0.71f, 0.43f}, {0.74f, 0.31f}
        };
        return points(values, offsetX);
    }

    private static List<LandmarkPoint> pinchedHand(float offsetX) {
        List<LandmarkPoint> points = new ArrayList<>(openHand(offsetX));
        LandmarkPoint indexTip = points.get(8);
        points.set(4, new LandmarkPoint(indexTip.x - 0.008f, indexTip.y + 0.006f, 0f));
        return points;
    }

    private static List<LandmarkPoint> releasedPinchHand(float offsetX) {
        List<LandmarkPoint> points = new ArrayList<>(openHand(offsetX));
        LandmarkPoint indexTip = points.get(8);
        points.set(4, new LandmarkPoint(indexTip.x - 0.14f, indexTip.y, 0f));
        return points;
    }

    private static List<LandmarkPoint> fistHand(float offsetX) {
        float[][] values = {
                {0.50f, 0.92f}, {0.43f, 0.80f}, {0.39f, 0.72f},
                {0.45f, 0.67f}, {0.50f, 0.65f},
                {0.42f, 0.67f}, {0.42f, 0.56f}, {0.48f, 0.59f}, {0.50f, 0.66f},
                {0.50f, 0.65f}, {0.50f, 0.54f}, {0.54f, 0.58f}, {0.54f, 0.66f},
                {0.58f, 0.67f}, {0.58f, 0.56f}, {0.61f, 0.60f}, {0.59f, 0.67f},
                {0.66f, 0.70f}, {0.65f, 0.60f}, {0.67f, 0.63f}, {0.64f, 0.70f}
        };
        return points(values, offsetX);
    }

    private static List<LandmarkPoint> twoFingerHand() {
        List<LandmarkPoint> result = new ArrayList<>(openHand(0f));
        List<LandmarkPoint> fist = fistHand(0f);
        for (int index : new int[]{14, 15, 16, 18, 19, 20}) {
            result.set(index, fist.get(index));
        }
        return result;
    }

    private static List<LandmarkPoint> shifted(
            List<LandmarkPoint> source,
            float offsetX,
            float offsetY
    ) {
        List<LandmarkPoint> result = new ArrayList<>(source.size());
        for (LandmarkPoint point : source) {
            result.add(new LandmarkPoint(
                    point.x + offsetX,
                    point.y + offsetY,
                    point.z
            ));
        }
        return result;
    }

    private static GestureEvent.Type swipe(float totalX, float totalY) {
        GestureEngine engine = new GestureEngine();
        engine.configure(0.6f, 0.62f);
        for (int index = 0; index < 6; index++) {
            float progress = index / 5f;
            GestureFrame frame = engine.process(
                    List.of(shifted(
                            openHand(0f),
                            totalX * (progress - 0.5f),
                            totalY * (progress - 0.5f)
                    )),
                    100L + index * 60L
            );
            for (GestureEvent event : frame.events) {
                if (event.type.name().startsWith("SWIPE_")) {
                    return event.type;
                }
            }
        }
        return null;
    }

    private static List<LandmarkPoint> points(float[][] values, float offsetX) {
        List<LandmarkPoint> result = new ArrayList<>(values.length);
        for (float[] value : values) {
            result.add(new LandmarkPoint(value[0] + offsetX, value[1], 0f));
        }
        return result;
    }
}
