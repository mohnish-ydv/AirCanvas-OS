import com.mohnish.aircanvas.gesture.GestureEngine;
import com.mohnish.aircanvas.gesture.GestureEvent;
import com.mohnish.aircanvas.gesture.GestureFrame;
import com.mohnish.aircanvas.gesture.LandmarkPoint;
import com.mohnish.aircanvas.ink.SmartStrokeInterpreter;
import com.mohnish.aircanvas.model.Bounds;
import com.mohnish.aircanvas.model.CanvasElement;
import com.mohnish.aircanvas.model.SpatialMesh;

import java.util.ArrayList;
import java.util.List;

public final class CoreRegressionHarness {
    private static int passed;

    public static void main(String[] args) {
        testInstantPinchRelease();
        testFourDirectionSwipes();
        testRoughSquare();
        testLetterIsNotAForcedShape();
        testTrue3dProjection();
        System.out.println("Core regression harness: " + passed + "/5 passed");
    }

    private static void testInstantPinchRelease() {
        GestureEngine engine = new GestureEngine();
        engine.configure(0.58f, 0.62f);
        GestureEvent startEvent = null;
        for (int frame = 0; frame < 3; frame++) {
            GestureFrame start = engine.process(
                    List.of(pinchedHand(0f)),
                    100L + frame * 60L
            );
            GestureEvent candidate = find(start, GestureEvent.Type.PINCH_START);
            if (candidate != null) {
                startEvent = candidate;
            }
        }
        require(startEvent != null, "pinch did not start within two stable frames");

        GestureFrame release = engine.process(
                List.of(shifted(releasedPinchHand(0f), 0.24f, 0.14f)),
                280L
        );
        GestureEvent end = find(release, GestureEvent.Type.PINCH_END);
        require(end != null, "pinch release was not immediate");
        require(Math.abs(end.x - startEvent.x) < 0.03f, "release appended an x jump");
        require(Math.abs(end.y - startEvent.y) < 0.03f, "release appended a y jump");
        passed++;
    }

    private static void testFourDirectionSwipes() {
        require(swipe(-0.30f, 0f) == GestureEvent.Type.SWIPE_LEFT, "left swipe");
        require(swipe(0.30f, 0f) == GestureEvent.Type.SWIPE_RIGHT, "right swipe");
        require(swipe(0f, -0.30f) == GestureEvent.Type.SWIPE_UP, "up swipe");
        require(swipe(0f, 0.30f) == GestureEvent.Type.SWIPE_DOWN, "down swipe");
        passed++;
    }

    private static GestureEvent.Type swipe(float totalX, float totalY) {
        GestureEngine engine = new GestureEngine();
        engine.configure(0.58f, 0.62f);
        GestureEvent.Type result = null;
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
                    result = event.type;
                }
            }
        }
        return result;
    }

    private static void testRoughSquare() {
        List<Float> square = points(
                10, 15, 35, 10, 72, 13, 105, 18,
                108, 48, 103, 88, 96, 110,
                62, 106, 30, 110, 8, 101,
                11, 70, 7, 35, 10, 15
        );
        SmartStrokeInterpreter.Result result = SmartStrokeInterpreter.interpret(square);
        require(
                result.kind() == SmartStrokeInterpreter.Kind.RECTANGLE,
                "rough square became " + result.kind()
        );
        Bounds bounds = result.bounds();
        require(Math.abs(bounds.width() - bounds.height()) < 12f, "square was not regularized");
        passed++;
    }

    private static void testLetterIsNotAForcedShape() {
        List<Float> letterA = points(
                10, 110, 25, 75, 45, 32, 60, 10,
                74, 40, 90, 78, 108, 112,
                91, 78, 75, 48, 38, 52, 83, 52
        );
        SmartStrokeInterpreter.Result result = SmartStrokeInterpreter.interpret(letterA);
        require(result.kind() == SmartStrokeInterpreter.Kind.NONE, "A became a shape");
        passed++;
    }

    private static void testTrue3dProjection() {
        CanvasElement cube = CanvasElement.node(
                CanvasElement.Type.CUBE,
                400f,
                300f,
                240f,
                240f,
                ""
        );
        cube.depth = 240f;
        SpatialMesh.Projection front = cube.meshProjection();
        cube.setRotation(90f, 90f, 90f);
        SpatialMesh.Projection rotated = cube.meshProjection();
        require(front.faces().size() == 6, "cube face count");
        require(rotated.faces().size() == 6, "rotated cube face count");
        require(front != rotated, "mesh projection cache did not invalidate");
        cube.setRotation(360f, 360f, 360f);
        Bounds fullTurn = cube.meshProjection().bounds();
        require(
                Math.abs(front.bounds().width() - fullTurn.width()) < 0.05f,
                "360 degree X/Y/Z turn did not return to origin"
        );
        passed++;
    }

    private static GestureEvent find(GestureFrame frame, GestureEvent.Type type) {
        for (GestureEvent event : frame.events) {
            if (event.type == type) {
                return event;
            }
        }
        return null;
    }

    private static List<Float> points(float... values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
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
        List<LandmarkPoint> result = new ArrayList<>(values.length);
        for (float[] point : values) {
            result.add(new LandmarkPoint(point[0] + offsetX, point[1], 0f));
        }
        return result;
    }

    private static List<LandmarkPoint> pinchedHand(float offsetX) {
        List<LandmarkPoint> result = new ArrayList<>(openHand(offsetX));
        LandmarkPoint index = result.get(8);
        result.set(4, new LandmarkPoint(index.x - 0.008f, index.y + 0.006f, 0f));
        return result;
    }

    private static List<LandmarkPoint> releasedPinchHand(float offsetX) {
        List<LandmarkPoint> result = new ArrayList<>(openHand(offsetX));
        LandmarkPoint index = result.get(8);
        result.set(4, new LandmarkPoint(index.x - 0.14f, index.y, 0f));
        return result;
    }

    private static List<LandmarkPoint> shifted(
            List<LandmarkPoint> source,
            float dx,
            float dy
    ) {
        List<LandmarkPoint> result = new ArrayList<>(source.size());
        for (LandmarkPoint point : source) {
            result.add(new LandmarkPoint(point.x + dx, point.y + dy, point.z));
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
