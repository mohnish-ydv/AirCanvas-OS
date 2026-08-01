import com.mohnish.aircanvas.model.Bounds;
import com.mohnish.aircanvas.model.CanvasElement;
import com.mohnish.aircanvas.model.DesignDocument;
import com.mohnish.aircanvas.model.ProjectInsights;
import com.mohnish.aircanvas.model.SelectionOperations;
import com.mohnish.aircanvas.model.StylePreset;
import com.mohnish.aircanvas.model.TemplateFactory;
import com.mohnish.aircanvas.vision.PerformanceTelemetry;

import java.util.LinkedHashSet;
import java.util.Set;

public final class PortfolioEditionHarness {
    private static int passed;

    public static void main(String[] args) {
        testShowcaseTemplate();
        testStyleAndProtection();
        testAlignmentAndDistribution();
        testInsights();
        testTelemetry();
        System.out.println("Portfolio edition regression: " + passed + "/5 passed");
    }

    private static void testShowcaseTemplate() {
        DesignDocument document = TemplateFactory.create(
                TemplateFactory.Template.PORTFOLIO_SHOWCASE
        );
        ProjectInsights insights = ProjectInsights.analyze(document);
        check("Portfolio Showcase".equals(document.template));
        check(insights.objects() >= 12);
        check(insights.spatialObjects() >= 3);
        passed++;
    }

    private static void testStyleAndProtection() {
        DesignDocument document = new DesignDocument("Style Test");
        CanvasElement first = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 100, 100, 80, 60, "A"
        );
        CanvasElement second = CanvasElement.node(
                CanvasElement.Type.ELLIPSE, 260, 100, 80, 60, "B"
        );
        document.elements.add(first);
        document.elements.add(second);
        Set<String> ids = new LinkedHashSet<>(Set.of(first.id, second.id));
        check(SelectionOperations.applyStyle(document, ids, StylePreset.NEON_GLASS) == 2);
        check(first.strokeColor == StylePreset.NEON_GLASS.strokeColor);
        check(SelectionOperations.setLocked(document, ids, true) == 2);
        check(SelectionOperations.applyStyle(document, ids, StylePreset.WARNING) == 0);
        check(SelectionOperations.setLocked(document, ids, false) == 2);
        passed++;
    }

    private static void testAlignmentAndDistribution() {
        DesignDocument document = new DesignDocument("Arrange Test");
        CanvasElement first = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 100, 100, 40, 40, "1"
        );
        CanvasElement second = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 260, 180, 40, 40, "2"
        );
        CanvasElement third = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 500, 260, 40, 40, "3"
        );
        document.elements.add(first);
        document.elements.add(second);
        document.elements.add(third);
        Set<String> ids = new LinkedHashSet<>(Set.of(first.id, second.id, third.id));
        check(SelectionOperations.align(
                document,
                ids,
                SelectionOperations.Alignment.TOP
        ) == 3);
        check(close(first.bounds().top, second.bounds().top));
        check(close(second.bounds().top, third.bounds().top));
        check(SelectionOperations.distribute(
                document,
                ids,
                SelectionOperations.Distribution.HORIZONTAL
        ) == 3);
        float firstGap = second.bounds().centerX() - first.bounds().centerX();
        float secondGap = third.bounds().centerX() - second.bounds().centerX();
        check(close(firstGap, secondGap));
        passed++;
    }

    private static void testInsights() {
        DesignDocument document = TemplateFactory.create(
                TemplateFactory.Template.TRUE_3D_LAB
        );
        ProjectInsights insights = ProjectInsights.analyze(document);
        check(insights.objects() == 5);
        check(insights.spatialObjects() == 5);
        check(insights.words() == 5);
        passed++;
    }

    private static void testTelemetry() {
        PerformanceTelemetry telemetry = new PerformanceTelemetry();
        telemetry.record(1000L, 35L, 1);
        PerformanceTelemetry.Snapshot snapshot = telemetry.record(1042L, 37L, 1);
        check(snapshot.fps() > 20f);
        check(snapshot.averageInferenceMs() >= 35f);
        check(!snapshot.compactLabel().isBlank());
        telemetry.reset();
        check(telemetry.snapshot().fps() == 0f);
        passed++;
    }

    private static boolean close(float left, float right) {
        return Math.abs(left - right) < 0.001f;
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Portfolio edition regression failed");
        }
    }
}
