import com.mohnish.aircanvas.gesture.AdaptiveGestureProfile;
import com.mohnish.aircanvas.gesture.GestureEngine;
import com.mohnish.aircanvas.gesture.GestureEvent;
import com.mohnish.aircanvas.gesture.GestureFrame;
import com.mohnish.aircanvas.gesture.LandmarkPoint;
import com.mohnish.aircanvas.gesture.PaletteSelectionGate;
import com.mohnish.aircanvas.ink.SmartInkRequest;

import java.util.ArrayList;
import java.util.List;

public final class RecognitionReliabilityHarness {
    private static int passed;

    public static void main(String[] args) {
        visibleFingerGapNeverStartsPinch();
        movingNearPinchNeverStartsPinch();
        deliberatePinchNeedsTwoStableFrames();
        releaseIsImmediate();
        twoHandTransformNeedsStableConfirmation();
        adaptiveProfileStaysBounded();
        adaptiveSamplingIsSparse();
        paletteCommitsOnReleaseAfterStableHover();
        paletteToleratesOneCursorDropout();
        multiStrokeInkMergesWithoutLosingComponents();
        System.out.println("Recognition reliability harness: " + passed + "/10 passed");
    }

    private static void visibleFingerGapNeverStartsPinch() {
        GestureEngine engine = new GestureEngine();
        engine.configure(1f, 0.62f);
        for (int index = 0; index < 90; index++) {
            GestureFrame frame = engine.process(
                    List.of(nearPinchHand(0f, 0.060f)),
                    100L + index * 42L
            );
            require(find(frame, GestureEvent.Type.PINCH_START) == null,
                    "visible fingertip gap started a pinch at frame " + index);
        }
        passed++;
    }

    private static void movingNearPinchNeverStartsPinch() {
        GestureEngine engine = new GestureEngine();
        engine.configure(1f, 0.35f);
        for (int index = 0; index < 45; index++) {
            float dx = (float) Math.sin(index * 0.34f) * 0.14f;
            float gap = 0.057f + (index % 4) * 0.003f;
            GestureFrame frame = engine.process(
                    List.of(nearPinchHand(dx, gap)),
                    100L + index * 42L
            );
            require(find(frame, GestureEvent.Type.PINCH_START) == null,
                    "moving near-pinch became a pinch at frame " + index);
        }
        passed++;
    }

    private static void deliberatePinchNeedsTwoStableFrames() {
        GestureEngine engine = new GestureEngine();
        GestureFrame first = engine.process(List.of(pinchedHand(0f)), 100L);
        GestureFrame second = engine.process(List.of(pinchedHand(0f)), 150L);
        require(find(first, GestureEvent.Type.PINCH_START) == null,
                "pinch started from one noisy frame");
        require(find(second, GestureEvent.Type.PINCH_START) != null,
                "pinch did not start on the second stable frame");
        passed++;
    }

    private static void releaseIsImmediate() {
        GestureEngine engine = new GestureEngine();
        engine.process(List.of(pinchedHand(0f)), 100L);
        GestureFrame started = engine.process(List.of(pinchedHand(0f)), 150L);
        GestureEvent start = find(started, GestureEvent.Type.PINCH_START);
        require(start != null, "pinch precondition failed");
        GestureFrame released = engine.process(List.of(openHand(0.22f)), 250L);
        GestureEvent end = find(released, GestureEvent.Type.PINCH_END);
        require(end != null, "pinch was not released on first open frame");
        require(Math.abs(start.x - end.x) < 0.03f && Math.abs(start.y - end.y) < 0.03f,
                "release appended an unwanted cursor tail");
        passed++;
    }

    private static void twoHandTransformNeedsStableConfirmation() {
        GestureEngine engine = new GestureEngine();
        List<List<LandmarkPoint>> hands = List.of(pinchedHand(-0.20f), pinchedHand(0.20f));
        GestureFrame first = engine.process(hands, 100L);
        GestureFrame second = engine.process(hands, 150L);
        require(find(first, GestureEvent.Type.TWO_HAND_SCALE_START) == null,
                "two-hand transform started from one noisy frame");
        require(find(second, GestureEvent.Type.TWO_HAND_SCALE_START) != null,
                "two-hand transform did not start after confirmation");
        passed++;
    }

    private static void adaptiveProfileStaysBounded() {
        AdaptiveGestureProfile profile = new AdaptiveGestureProfile(
                new AdaptiveGestureProfile.Snapshot(-8f, 9_000, 99f, 9_000, -4L)
        );
        for (int index = 0; index < 400; index++) {
            profile.observeConfirmedPinch(0.12f + (index % 5) * 0.005f);
            profile.observeClearlyOpen(0.72f + (index % 5) * 0.02f);
        }
        AdaptiveGestureProfile.Snapshot snapshot = profile.snapshot();
        require(snapshot.pinchSamples() <= 96 && snapshot.openSamples() <= 96,
                "adaptive sample cap failed");
        float start = profile.startThreshold(1f);
        float release = profile.releaseThreshold(1f);
        require(start >= 0.175f && start <= 0.235f, "adaptive start escaped safe bounds");
        require(release >= 0.285f && release <= 0.355f && release > start,
                "adaptive release escaped safe bounds");
        passed++;
    }

    private static void adaptiveSamplingIsSparse() {
        GestureEngine engine = new GestureEngine();
        for (int index = 0; index < 120; index++) {
            engine.process(List.of(openHand(0f)), 100L + index * 60L);
        }
        AdaptiveGestureProfile.Snapshot snapshot = engine.adaptiveSnapshot();
        require(snapshot.openSamples() >= 4, "clearly-open usage was not learned");
        require(snapshot.openSamples() <= 7, "open-hand learning sampled every frame");
        require(snapshot.pinchSamples() == 0, "open hand polluted pinch learning");
        passed++;
    }

    private static void paletteCommitsOnReleaseAfterStableHover() {
        PaletteSelectionGate gate = new PaletteSelectionGate();
        gate.open(9, 0L);
        gate.updateHover(4, 20L);
        require(!gate.beginPinch(40L), "palette ignored opening guard");
        gate.updateHover(4, 80L);
        require(gate.armedIndex() == 4, "pinch-down card did not late-arm");
        require(gate.commitPinch(105L) == 4,
                "pinch release did not choose the late-armed command");
        passed++;
    }

    private static void paletteToleratesOneCursorDropout() {
        PaletteSelectionGate gate = new PaletteSelectionGate();
        gate.open(9, 0L);
        gate.updateHover(2, 20L);
        gate.updateHover(2, 100L);
        require(gate.beginPinch(105L), "palette could not arm before dropout");
        gate.updateHover(-1, 180L);
        require(gate.commitPinch(220L) == 2, "brief landmark dropout cancelled selection");
        passed++;
    }

    private static void multiStrokeInkMergesWithoutLosingComponents() {
        SmartInkRequest left = new SmartInkRequest(
                "left",
                List.of(10f, 100f, 40f, 20f, 70f, 100f),
                100L
        );
        SmartInkRequest bar = new SmartInkRequest(
                "bar",
                List.of(28f, 62f, 54f, 62f),
                260L
        );
        SmartInkRequest merged = SmartInkRequest.merge(List.of(left, bar));
        require(merged.strokeCount() == 2, "multi-stroke word request collapsed strokes");
        require(merged.elementIds.equals(List.of("left", "bar")), "stroke ids changed order");
        require(merged.bounds.left == 10f && merged.bounds.right == 70f,
                "merged handwriting bounds are wrong");
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

    private static List<LandmarkPoint> nearPinchHand(float offsetX, float gap) {
        List<LandmarkPoint> result = new ArrayList<>(openHand(offsetX));
        LandmarkPoint index = result.get(8);
        result.set(4, new LandmarkPoint(index.x - gap, index.y, 0f));
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
