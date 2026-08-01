import com.mohnish.aircanvas.model.Bounds;
import com.mohnish.aircanvas.model.CanvasElement;
import com.mohnish.aircanvas.model.DesignDocument;
import com.mohnish.aircanvas.model.ShapeKind;
import com.mohnish.aircanvas.model.TemplateFactory;
import com.mohnish.aircanvas.model.Tool;

public final class LandscapeReadinessHarness {
    private static int passed;

    public static void main(String[] args) {
        defaultDocumentIsWide();
        everyTemplateFitsWideDocument();
        everyShapeCreatesUsableObject();
        everyTrue3dSolidProjectsInLandscape();
        everyToolRemainsAvailable();
        System.out.println("Landscape readiness harness: " + passed + "/5 passed");
    }

    private static void defaultDocumentIsWide() {
        DesignDocument document = new DesignDocument("Landscape");
        require(document.pageWidth > document.pageHeight, "default document is not landscape");
        require(document.pageWidth / document.pageHeight >= 1.5f,
                "default document does not use a useful wide aspect ratio");
        passed++;
    }

    private static void everyTemplateFitsWideDocument() {
        for (TemplateFactory.Template template : TemplateFactory.Template.values()) {
            DesignDocument document = TemplateFactory.create(template);
            require(document.pageWidth > document.pageHeight,
                    template + " template is not landscape");
            Bounds page = new Bounds(0f, 0f, document.pageWidth, document.pageHeight);
            for (CanvasElement element : document.elements) {
                Bounds bounds = element.bounds();
                require(bounds.right >= page.left && bounds.left <= page.right,
                        template + " has an object outside horizontal workspace");
                require(bounds.bottom >= page.top && bounds.top <= page.bottom,
                        template + " has an object outside vertical workspace");
            }
        }
        passed++;
    }

    private static void everyShapeCreatesUsableObject() {
        for (ShapeKind kind : ShapeKind.values()) {
            CanvasElement element = CanvasElement.node(
                    kind.elementType,
                    800f,
                    500f,
                    260f,
                    190f,
                    kind.label
            );
            Bounds bounds = element.bounds();
            require(bounds.width() > 0f && bounds.height() > 0f,
                    kind + " has invalid bounds");
            require(element.hitTest(800f, 500f, 8f),
                    kind + " cannot be selected at its center");
        }
        passed++;
    }

    private static void everyTrue3dSolidProjectsInLandscape() {
        CanvasElement.Type[] solids = {
                CanvasElement.Type.CUBE,
                CanvasElement.Type.SPHERE,
                CanvasElement.Type.CYLINDER,
                CanvasElement.Type.PYRAMID,
                CanvasElement.Type.CONE
        };
        for (CanvasElement.Type type : solids) {
            CanvasElement element = CanvasElement.node(type, 800f, 500f, 280f, 220f, type.name());
            element.depth = 240f;
            element.setRotation(37f, 143f, 281f);
            Bounds bounds = element.meshProjection().bounds();
            require(bounds.width() > 12f && bounds.height() > 12f,
                    type + " projection collapsed");
            element.setRotation(397f, 503f, 641f);
            require(element.meshProjection().faces().size() > 0,
                    type + " lost faces after full-axis rotation");
        }
        passed++;
    }

    private static void everyToolRemainsAvailable() {
        require(Tool.values().length == 9, "tool count changed unexpectedly");
        require(Tool.valueOf("SMART_INK") == Tool.SMART_INK, "Smart Ink tool missing");
        require(Tool.valueOf("TRANSFORM") == Tool.TRANSFORM, "True 3D tool missing");
        require(Tool.valueOf("PRESENT") == Tool.PRESENT, "presentation tool missing");
        passed++;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
