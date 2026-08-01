import com.mohnish.aircanvas.gesture.AdaptiveGestureProfile;
import com.mohnish.aircanvas.gesture.GestureEngine;
import com.mohnish.aircanvas.gesture.GestureEvent;
import com.mohnish.aircanvas.gesture.GestureFrame;
import com.mohnish.aircanvas.gesture.LandmarkPoint;
import com.mohnish.aircanvas.gesture.PaletteSelectionGate;
import com.mohnish.aircanvas.gesture.PinchDetector;

import java.util.ArrayList;
import java.util.List;

/** Regression cases modeled after real mobile-camera landmark failure modes. */
public final class PinchRealityHarness {
    private static int passed;

    public static void main(String[] args) {
        sideOfIndexContactIsAccepted();
        curledSupportFingersDoNotBlockPinch();
        foreshortenedHandIsAccepted();
        scaleAndRotationDoNotChangePinch();
        visibleGapNeverActivates();
        closedFistDoesNotMasqueradeAsPinch();
        activePinchSurvivesOneAmbiguousFrame();
        openingMotionReleasesBeforeWideOpenPalm();
        clearReleaseEndsOnFirstFrame();
        adaptiveLearningCannotMakePinchHarder();
        paletteLateArmsAfterPinchDown();
        System.out.println("Pinch reality harness: " + passed + "/11 passed");
    }

    private static void sideOfIndexContactIsAccepted() {
        require(startsWithinTwoFrames(sideContactPinch()),
                "thumb-to-index-pad contact was rejected");
        passed++;
    }


    private static void curledSupportFingersDoNotBlockPinch() {
        require(startsWithinTwoFrames(curledFingerPinch()),
                "natural pinch with three curled fingers was rejected");
        passed++;
    }

    private static void foreshortenedHandIsAccepted() {
        List<LandmarkPoint> foreshortened = transform(
                sideContactPinch(),
                0.72f,
                0.50f,
                0f
        );
        require(startsWithinTwoFrames(foreshortened),
                "foreshortened camera angle was rejected");
        passed++;
    }

    private static void scaleAndRotationDoNotChangePinch() {
        List<LandmarkPoint> base = bentTipPinch();
        require(startsWithinTwoFrames(transform(base, 0.62f, 0.62f, 68f)),
                "small rotated hand was rejected");
        require(startsWithinTwoFrames(transform(base, 1.28f, 1.28f, -41f)),
                "large rotated hand was rejected");
        passed++;
    }

    private static void visibleGapNeverActivates() {
        GestureEngine engine = new GestureEngine();
        for (int frame = 0; frame < 70; frame++) {
            float jitter = (frame % 5 - 2) * 0.0015f;
            GestureFrame result = engine.process(
                    List.of(nearPinch(0.060f + jitter)),
                    100L + frame * 42L
            );
            require(find(result, GestureEvent.Type.PINCH_START) == null,
                    "visible gap activated at frame " + frame);
        }
        passed++;
    }

    private static void closedFistDoesNotMasqueradeAsPinch() {
        List<LandmarkPoint> fist = new ArrayList<>(fistHand());
        LandmarkPoint indexTip = fist.get(8);
        fist.set(4, new LandmarkPoint(
                indexTip.x - 0.006f,
                indexTip.y + 0.004f,
                indexTip.z
        ));
        AdaptiveGestureProfile profile = new AdaptiveGestureProfile();
        PinchDetector.Observation observation = PinchDetector.evaluate(
                fist,
                profile.startThreshold(0.76f),
                profile.releaseThreshold(0.76f),
                false
        );
        require(!observation.isClosed(), "closed fist became a pinch");
        passed++;
    }

    private static void activePinchSurvivesOneAmbiguousFrame() {
        GestureEngine engine = activeEngine(bentTipPinch());
        GestureFrame ambiguous = engine.process(List.of(nearPinch(0.055f)), 220L);
        require(find(ambiguous, GestureEvent.Type.PINCH_END) == null,
                "one noisy borderline frame ended the pinch");
        GestureFrame closedAgain = engine.process(List.of(bentTipPinch()), 270L);
        require(find(closedAgain, GestureEvent.Type.PINCH_MOVE) != null,
                "pinch did not recover after a borderline frame");
        passed++;
    }

    private static void openingMotionReleasesBeforeWideOpenPalm() {
        GestureEngine engine = activeEngine(bentTipPinch());
        GestureFrame released = engine.process(List.of(nearPinch(0.065f)), 220L);
        require(find(released, GestureEvent.Type.PINCH_END) != null,
                "opening motion still required a fully open hand");
        passed++;
    }

    private static void clearReleaseEndsOnFirstFrame() {
        GestureEngine engine = activeEngine(bentTipPinch());
        GestureFrame released = engine.process(List.of(openHand()), 220L);
        require(find(released, GestureEvent.Type.PINCH_END) != null,
                "clear open hand did not end on the first frame");
        passed++;
    }

    private static void adaptiveLearningCannotMakePinchHarder() {
        AdaptiveGestureProfile profile = new AdaptiveGestureProfile();
        float baseline = profile.startThreshold(0.76f);
        for (int sample = 0; sample < 24; sample++) {
            profile.observeConfirmedPinch(0.045f + (sample % 3) * 0.004f);
        }
        float learned = profile.startThreshold(0.76f);
        require(learned >= baseline - 0.009f,
                "tight samples trained the detector into a strict failure mode");
        passed++;
    }

    private static void paletteLateArmsAfterPinchDown() {
        PaletteSelectionGate gate = new PaletteSelectionGate();
        gate.open(10, 0L);
        gate.updateHover(6, 20L);
        require(!gate.beginPinch(35L), "opening transition armed too early");
        gate.updateHover(6, 72L);
        require(gate.armedIndex() == 6, "held pinch did not late-arm card");
        require(gate.commitPinch(92L) == 6, "late-armed card did not commit");
        passed++;
    }

    private static GestureEngine activeEngine(List<LandmarkPoint> hand) {
        GestureEngine engine = new GestureEngine();
        engine.process(List.of(hand), 100L);
        GestureFrame second = engine.process(List.of(hand), 150L);
        require(find(second, GestureEvent.Type.PINCH_START) != null,
                "test pinch could not start");
        return engine;
    }

    private static boolean startsWithinTwoFrames(List<LandmarkPoint> hand) {
        GestureEngine engine = new GestureEngine();
        GestureFrame first = engine.process(List.of(hand), 100L);
        GestureFrame second = engine.process(List.of(hand), 150L);
        return find(first, GestureEvent.Type.PINCH_START) != null
                || find(second, GestureEvent.Type.PINCH_START) != null;
    }

    private static GestureEvent find(GestureFrame frame, GestureEvent.Type type) {
        for (GestureEvent event : frame.events) {
            if (event.type == type) {
                return event;
            }
        }
        return null;
    }

    private static List<LandmarkPoint> sideContactPinch() {
        List<LandmarkPoint> result = new ArrayList<>(openHand());
        result.set(6, new LandmarkPoint(0.43f, 0.45f, 0f));
        result.set(7, new LandmarkPoint(0.47f, 0.31f, 0f));
        result.set(8, new LandmarkPoint(0.45f, 0.20f, 0f));
        result.set(4, new LandmarkPoint(0.46f, 0.255f, 0.01f));
        return result;
    }


    private static List<LandmarkPoint> curledFingerPinch() {
        List<LandmarkPoint> result = new ArrayList<>(sideContactPinch());
        List<LandmarkPoint> curled = fistHand();
        for (int index = 9; index < 21; index++) {
            result.set(index, curled.get(index));
        }
        return result;
    }

    private static List<LandmarkPoint> bentTipPinch() {
        List<LandmarkPoint> result = new ArrayList<>(openHand());
        result.set(6, new LandmarkPoint(0.43f, 0.45f, 0f));
        result.set(7, new LandmarkPoint(0.47f, 0.31f, 0f));
        result.set(8, new LandmarkPoint(0.45f, 0.20f, 0f));
        result.set(4, new LandmarkPoint(0.44f, 0.205f, 0.01f));
        return result;
    }

    private static List<LandmarkPoint> nearPinch(float gap) {
        List<LandmarkPoint> result = new ArrayList<>(openHand());
        LandmarkPoint index = result.get(8);
        result.set(4, new LandmarkPoint(index.x - gap, index.y, 0f));
        return result;
    }

    private static List<LandmarkPoint> transform(
            List<LandmarkPoint> source,
            float scaleX,
            float scaleY,
            float degrees
    ) {
        float radians = (float) Math.toRadians(degrees);
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);
        List<LandmarkPoint> result = new ArrayList<>(source.size());
        for (LandmarkPoint point : source) {
            float localX = (point.x - 0.5f) * scaleX;
            float localY = (point.y - 0.5f) * scaleY;
            result.add(new LandmarkPoint(
                    0.5f + localX * cosine - localY * sine,
                    0.5f + localX * sine + localY * cosine,
                    point.z + (1f - scaleY) * (point.y - 0.5f) * 0.45f
            ));
        }
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
