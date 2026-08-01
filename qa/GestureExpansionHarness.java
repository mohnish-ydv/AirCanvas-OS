import com.mohnish.aircanvas.gesture.GestureEngine;
import com.mohnish.aircanvas.gesture.GestureEvent;
import com.mohnish.aircanvas.gesture.GestureFrame;
import com.mohnish.aircanvas.gesture.LandmarkPoint;

import java.util.ArrayList;
import java.util.List;

public final class GestureExpansionHarness {
    private static int passed;

    public static void main(String[] args) {
        bentIndexPinchStillStartsAndReleases();
        thumbsUpOpensShapePaletteOnce();
        orbitPoseStartsAndStopsSpin();
        System.out.println("Gesture expansion harness: " + passed + "/3 passed");
    }

    private static void bentIndexPinchStillStartsAndReleases() {
        GestureEngine engine = new GestureEngine();
        GestureEvent start = null;
        for (int frame = 0; frame < 3; frame++) {
            GestureFrame result = engine.process(
                    List.of(bentIndexPinch()),
                    100L + frame * 45L
            );
            GestureEvent candidate = find(result, GestureEvent.Type.PINCH_START);
            if (candidate != null) {
                start = candidate;
            }
        }
        require(start != null, "natural bent-index pinch was rejected");
        GestureFrame released = engine.process(List.of(openHand()), 250L);
        require(find(released, GestureEvent.Type.PINCH_END) != null,
                "pinch did not release on the first open frame");
        passed++;
    }

    private static void thumbsUpOpensShapePaletteOnce() {
        GestureEngine engine = new GestureEngine();
        long events = 0L;
        for (int frame = 0; frame < 10; frame++) {
            GestureFrame result = engine.process(
                    List.of(thumbsUp()),
                    100L + frame * 100L
            );
            events += result.events.stream()
                    .filter(event -> event.type == GestureEvent.Type.SHAPE_MENU)
                    .count();
        }
        require(events == 1L, "thumbs-up shape palette event count was " + events);
        passed++;
    }

    private static void orbitPoseStartsAndStopsSpin() {
        GestureEngine engine = new GestureEngine();
        GestureFrame third = null;
        for (int frame = 0; frame < 3; frame++) {
            third = engine.process(List.of(orbitPose()), 100L + frame * 50L);
        }
        require(find(third, GestureEvent.Type.AUTO_SPIN_START) != null,
                "orbit pose did not start auto-spin");
        engine.process(List.of(openHand()), 300L);
        GestureFrame released = engine.process(List.of(openHand()), 350L);
        require(find(released, GestureEvent.Type.AUTO_SPIN_END) != null,
                "orbit pose did not stop after release debounce");
        passed++;
    }

    private static GestureEvent find(GestureFrame frame, GestureEvent.Type type) {
        if (frame == null) {
            return null;
        }
        for (GestureEvent event : frame.events) {
            if (event.type == type) {
                return event;
            }
        }
        return null;
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

    private static List<LandmarkPoint> fist() {
        return points(new float[][]{
                {0.50f, 0.92f}, {0.43f, 0.80f}, {0.39f, 0.72f},
                {0.45f, 0.67f}, {0.50f, 0.65f},
                {0.42f, 0.67f}, {0.42f, 0.56f}, {0.48f, 0.59f}, {0.50f, 0.66f},
                {0.50f, 0.65f}, {0.50f, 0.54f}, {0.54f, 0.58f}, {0.54f, 0.66f},
                {0.58f, 0.67f}, {0.58f, 0.56f}, {0.61f, 0.60f}, {0.59f, 0.67f},
                {0.66f, 0.70f}, {0.65f, 0.60f}, {0.67f, 0.63f}, {0.64f, 0.70f}
        });
    }

    private static List<LandmarkPoint> thumbsUp() {
        List<LandmarkPoint> result = new ArrayList<>(fist());
        result.set(1, new LandmarkPoint(0.46f, 0.78f, 0f));
        result.set(2, new LandmarkPoint(0.45f, 0.63f, 0f));
        result.set(3, new LandmarkPoint(0.45f, 0.43f, 0f));
        result.set(4, new LandmarkPoint(0.45f, 0.21f, 0f));
        return result;
    }

    private static List<LandmarkPoint> orbitPose() {
        List<LandmarkPoint> result = new ArrayList<>(thumbsUp());
        result.set(5, new LandmarkPoint(0.42f, 0.67f, 0f));
        result.set(6, new LandmarkPoint(0.36f, 0.56f, 0f));
        result.set(7, new LandmarkPoint(0.26f, 0.48f, 0f));
        result.set(8, new LandmarkPoint(0.16f, 0.46f, 0f));
        return result;
    }

    private static List<LandmarkPoint> bentIndexPinch() {
        List<LandmarkPoint> result = new ArrayList<>(openHand());
        result.set(6, new LandmarkPoint(0.43f, 0.45f, 0f));
        result.set(7, new LandmarkPoint(0.47f, 0.31f, 0f));
        result.set(8, new LandmarkPoint(0.45f, 0.20f, 0f));
        result.set(4, new LandmarkPoint(0.44f, 0.205f, 0.01f));
        return result;
    }

    private static List<LandmarkPoint> points(float[][] values) {
        List<LandmarkPoint> result = new ArrayList<>(values.length);
        for (float[] value : values) {
            result.add(new LandmarkPoint(value[0], value[1], 0f));
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
