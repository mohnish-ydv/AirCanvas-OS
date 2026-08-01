import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer;
import com.mohnish.aircanvas.ink.OnDeviceInkRecognizer;
import com.mohnish.aircanvas.ink.SmartInkRequest;

import java.util.List;

public final class AutoTextReflectionHarness {
    private static int passed;

    public static void main(String[] args) {
        SmartInkRequest first = new SmartInkRequest(
                "first",
                List.of(10f, 100f, 30f, 20f, 50f, 100f),
                1_000L
        );
        SmartInkRequest second = new SmartInkRequest(
                "second",
                List.of(18f, 64f, 43f, 64f),
                1_150L
        );
        SmartInkRequest merged = SmartInkRequest.merge(List.of(first, second));
        Capture capture = new Capture();

        try (OnDeviceInkRecognizer recognizer = new OnDeviceInkRecognizer()) {
            recognizer.recognize(merged, "hello previous context", capture);
        }

        require(capture.failure == null, "reflection bridge failed: " + capture.failure);
        require("AIR CANVAS".equals(capture.text), "recognized text was not returned");
        require(capture.request == merged, "callback request identity changed");
        require(DigitalInkRecognizer.lastInk != null, "recognizer did not receive Ink");
        require(DigitalInkRecognizer.lastInk.getStrokes().size() == 2,
                "multi-stroke Ink was not constructed");
        require(DigitalInkRecognizer.lastInk.getStrokes().get(0).getPoints().size() == 3,
                "first stroke points were lost");
        require(DigitalInkRecognizer.lastContext != null
                        && "llo previous context".equals(
                        DigitalInkRecognizer.lastContext.preContext),
                "recognition context was not forwarded");
        require(DigitalInkRecognizer.closed, "recognizer was not closed");
        System.out.println("Auto Text reflection harness: " + passed + "/8 passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        passed++;
    }

    private static final class Capture implements OnDeviceInkRecognizer.Callback {
        SmartInkRequest request;
        String text;
        String failure;

        @Override
        public void onStatus(String status) {
            // Status transitions are informational; recognition result is asserted below.
        }

        @Override
        public void onRecognized(SmartInkRequest request, String text) {
            this.request = request;
            this.text = text;
        }

        @Override
        public void onFailure(SmartInkRequest request, String message) {
            this.request = request;
            failure = message;
        }
    }
}
