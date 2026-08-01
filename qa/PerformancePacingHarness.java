import com.mohnish.aircanvas.vision.AdaptiveFrameGovernor;

public final class PerformancePacingHarness {
    private static int passed;

    public static void main(String[] args) {
        smoothProfileStartsAtThirtyMilliseconds();
        heavyInferenceRaisesTheIntervalWithoutRunawayBacklog();
        recoveredInferenceDecaysBackToTheRequestedProfile();
        System.out.println("Performance pacing harness: " + passed + "/3 passed");
    }

    private static void smoothProfileStartsAtThirtyMilliseconds() {
        AdaptiveFrameGovernor governor = new AdaptiveFrameGovernor(30L);
        require(governor.intervalMs() == 30L,
                "Smooth base interval was " + governor.intervalMs());
        passed++;
    }

    private static void heavyInferenceRaisesTheIntervalWithoutRunawayBacklog() {
        AdaptiveFrameGovernor governor = new AdaptiveFrameGovernor(30L);
        governor.recordInference(100L);
        require(governor.intervalMs() == 100L,
                "100 ms inference should not be scheduled faster than 100 ms");
        governor.recordInference(500L);
        require(governor.intervalMs() <= 140L,
                "adaptive interval exceeded its hard cap");
        passed++;
    }

    private static void recoveredInferenceDecaysBackToTheRequestedProfile() {
        AdaptiveFrameGovernor governor = new AdaptiveFrameGovernor(30L);
        governor.recordInference(120L);
        for (int frame = 0; frame < 40; frame++) {
            governor.recordInference(10L);
        }
        require(governor.intervalMs() == 30L,
                "recovered inference did not return to the Smooth base");
        passed++;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
