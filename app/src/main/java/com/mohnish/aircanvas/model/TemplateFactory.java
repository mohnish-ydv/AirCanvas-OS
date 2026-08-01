package com.mohnish.aircanvas.model;

public final class TemplateFactory {
    public enum Template {
        BLANK("Blank"),
        FLOWCHART("Flowchart"),
        WIREFRAME("UI Wireframe"),
        MIND_MAP("Mind Map"),
        BOUNDARY("Boundary Plan"),
        SPATIAL_SYSTEM("Spatial System"),
        TRUE_3D_LAB("True 3D Lab"),
        STORYBOARD("Storyboard"),
        PORTFOLIO_SHOWCASE("Portfolio Showcase");

        public final String label;

        Template(String label) {
            this.label = label;
        }
    }

    private TemplateFactory() {
    }

    public static DesignDocument create(Template template) {
        DesignDocument document = new DesignDocument(template.label);
        document.template = template.label;
        switch (template) {
            case FLOWCHART -> buildFlowchart(document);
            case WIREFRAME -> buildWireframe(document);
            case MIND_MAP -> buildMindMap(document);
            case BOUNDARY -> buildBoundary(document);
            case SPATIAL_SYSTEM -> buildSpatialSystem(document);
            case TRUE_3D_LAB -> buildTrue3dLab(document);
            case STORYBOARD -> buildStoryboard(document);
            case PORTFOLIO_SHOWCASE -> buildPortfolioShowcase(document);
            case BLANK -> {
                // Intentionally empty.
            }
        }
        document.touch();
        return document;
    }

    private static void buildFlowchart(DesignDocument d) {
        CanvasElement start = CanvasElement.node(
                CanvasElement.Type.ELLIPSE, 300, 500, 230, 110, "START"
        );
        CanvasElement process = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 700, 500, 300, 150, "PROCESS"
        );
        CanvasElement decision = CanvasElement.node(
                CanvasElement.Type.DIAMOND, 1120, 500, 260, 190, "DECISION?"
        );
        d.elements.add(start);
        d.elements.add(CanvasElement.line(415, 500, 550, 500));
        d.elements.add(process);
        d.elements.add(CanvasElement.line(850, 500, 990, 500));
        d.elements.add(decision);
    }

    private static void buildWireframe(DesignDocument d) {
        CanvasElement frame = CanvasElement.node(
                CanvasElement.Type.FRAME, 800, 500, 620, 820, ""
        );
        frame.strokeColor = 0xFFA889FF;
        frame.fillColor = 0xA60B1626;
        frame.strokeWidth = 8f;
        frame.locked = true;

        CanvasElement header = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 800, 210, 520, 110, "APP HEADER"
        );
        CanvasElement hero = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 800, 410, 520, 220, "HERO / CONTENT"
        );
        CanvasElement cta = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 800, 710, 300, 100, "PRIMARY ACTION"
        );
        d.elements.add(frame);
        d.elements.add(header);
        d.elements.add(hero);
        d.elements.add(cta);
    }

    private static void buildMindMap(DesignDocument d) {
        CanvasElement center = CanvasElement.node(
                CanvasElement.Type.ELLIPSE, 800, 500, 300, 170, "CORE IDEA"
        );
        center.strokeColor = 0xFFA889FF;
        CanvasElement a = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 350, 270, 260, 130, "BRANCH A"
        );
        CanvasElement b = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 1250, 270, 260, 130, "BRANCH B"
        );
        CanvasElement c = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 350, 730, 260, 130, "BRANCH C"
        );
        CanvasElement e = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 1250, 730, 260, 130, "BRANCH D"
        );
        d.elements.add(CanvasElement.line(650, 440, 480, 330));
        d.elements.add(CanvasElement.line(950, 440, 1120, 330));
        d.elements.add(CanvasElement.line(650, 560, 480, 670));
        d.elements.add(CanvasElement.line(950, 560, 1120, 670));
        d.elements.add(center);
        d.elements.add(a);
        d.elements.add(b);
        d.elements.add(c);
        d.elements.add(e);
    }

    private static void buildBoundary(DesignDocument d) {
        CanvasElement outer = CanvasElement.node(
                CanvasElement.Type.FRAME, 800, 500, 1200, 760, ""
        );
        outer.strokeColor = 0xFFA889FF;
        outer.fillColor = 0x8F0B1626;
        outer.strokeWidth = 10f;
        outer.locked = true;

        CanvasElement roomA = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 510, 390, 520, 420, "ZONE A"
        );
        CanvasElement roomB = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 1090, 390, 520, 420, "ZONE B"
        );
        CanvasElement roomC = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 800, 735, 1100, 170, "SHARED BOUNDARY"
        );
        d.elements.add(outer);
        d.elements.add(roomA);
        d.elements.add(roomB);
        d.elements.add(roomC);
    }

    private static void buildSpatialSystem(DesignDocument d) {
        CanvasElement core = CanvasElement.node(
                CanvasElement.Type.HEXAGON, 800, 500, 330, 220, "CORE"
        );
        core.rotationX = -10f;
        core.rotationY = 18f;
        core.strokeColor = 0xFFA889FF;
        CanvasElement input = CanvasElement.node(
                CanvasElement.Type.STICKY, 330, 310, 280, 170, "INPUT"
        );
        input.fillColor = 0xD9F6C85F;
        input.rotationY = -22f;
        CanvasElement output = CanvasElement.node(
                CanvasElement.Type.ELLIPSE, 1270, 690, 290, 170, "OUTPUT"
        );
        output.rotationX = 14f;
        CanvasElement first = CanvasElement.line(470, 350, 640, 455);
        first.startAnchorId = input.id;
        first.endAnchorId = core.id;
        CanvasElement second = CanvasElement.line(960, 545, 1125, 650);
        second.startAnchorId = core.id;
        second.endAnchorId = output.id;
        d.elements.add(input);
        d.elements.add(first);
        d.elements.add(core);
        d.elements.add(second);
        d.elements.add(output);
        SpatialSnapEngine.refreshConnectors(d);
    }

    private static void buildStoryboard(DesignDocument d) {
        for (int index = 0; index < 3; index++) {
            CanvasElement frame = CanvasElement.node(
                    CanvasElement.Type.FRAME,
                    330f + index * 470f,
                    430f,
                    390f,
                    440f,
                    "SCENE " + (index + 1)
            );
            frame.fillColor = 0x66101D31;
            frame.rotationY = (index - 1) * 10f;
            d.elements.add(frame);
            CanvasElement note = CanvasElement.node(
                    CanvasElement.Type.STICKY,
                    330f + index * 470f,
                    760f,
                    330f,
                    130f,
                    "ACTION / NOTE"
            );
            note.fillColor = 0xD9F6C85F;
            d.elements.add(note);
        }
    }

    private static void buildTrue3dLab(DesignDocument d) {
        CanvasElement.Type[] types = {
                CanvasElement.Type.CUBE,
                CanvasElement.Type.SPHERE,
                CanvasElement.Type.CYLINDER,
                CanvasElement.Type.PYRAMID,
                CanvasElement.Type.CONE
        };
        String[] labels = {"CUBE", "SPHERE", "CYLINDER", "PYRAMID", "CONE"};
        for (int index = 0; index < types.length; index++) {
            CanvasElement solid = CanvasElement.node(
                    types[index],
                    190f + index * 305f,
                    500f,
                    215f,
                    250f,
                    labels[index]
            );
            solid.depth = 190f;
            solid.rotationX = -18f + index * 7f;
            solid.rotationY = 28f - index * 9f;
            solid.rotationZ = index * 5f;
            solid.fillColor = 0xC05263C7;
            solid.strokeColor = 0xFFE8E5FF;
            solid.strokeWidth = 4f;
            d.elements.add(solid);
        }
    }
    private static void buildPortfolioShowcase(DesignDocument d) {
        d.name = "AirCanvas Interaction Engine";

        CanvasElement backdrop = CanvasElement.node(
                CanvasElement.Type.FRAME, 800, 500, 1500, 900, ""
        );
        backdrop.fillColor = 0xE60A1424;
        backdrop.strokeColor = 0x665EE7F7;
        backdrop.strokeWidth = 4f;
        backdrop.locked = true;
        d.elements.add(backdrop);

        CanvasElement title = CanvasElement.node(
                CanvasElement.Type.TEXT, 800, 95, 1180, 90,
                "AIR INTERACTION ENGINE • ANDROID NATIVE"
        );
        title.strokeColor = 0x00000000;
        title.fillColor = 0x00000000;
        title.locked = true;
        d.elements.add(title);

        CanvasElement core = CanvasElement.node(
                CanvasElement.Type.CUBE, 800, 500, 320, 330, "GESTURE CORE"
        );
        core.depth = 290f;
        core.rotationX = -20f;
        core.rotationY = 30f;
        core.rotationZ = 8f;
        core.fillColor = 0xD05263C7;
        core.strokeColor = 0xFFF4F8FF;
        core.strokeWidth = 5f;
        d.elements.add(core);

        CanvasElement vision = showcaseNode(
                CanvasElement.Type.HEXAGON,
                315,
                285,
                300,
                160,
                "CAMERAX + MEDIAPIPE",
                0xC0123A52,
                0xFF5EE7F7
        );
        vision.rotationY = -18f;
        CanvasElement ink = showcaseNode(
                CanvasElement.Type.STICKY,
                1285,
                285,
                300,
                160,
                "SMART INK\nROUGH → CLEAN",
                0xD9543A12,
                0xFFFFD45E
        );
        ink.rotationY = 18f;
        CanvasElement spatial = showcaseNode(
                CanvasElement.Type.CYLINDER,
                315,
                715,
                280,
                220,
                "TRUE 3D",
                0xCF12344E,
                0xFF8BD8FF
        );
        spatial.depth = 230f;
        spatial.rotationX = -16f;
        spatial.rotationY = -24f;
        CanvasElement exports = showcaseNode(
                CanvasElement.Type.PYRAMID,
                1285,
                715,
                280,
                220,
                "PNG • PDF • SVG • JSON",
                0xD64B1F50,
                0xFFFF8DD8
        );
        exports.depth = 230f;
        exports.rotationX = -16f;
        exports.rotationY = 24f;

        d.elements.add(vision);
        d.elements.add(ink);
        d.elements.add(spatial);
        d.elements.add(exports);

        connect(d, vision, core, 465, 335, 650, 445, 0xFF5EE7F7);
        connect(d, ink, core, 1135, 335, 950, 445, 0xFFFFD45E);
        connect(d, spatial, core, 455, 650, 650, 555, 0xFF8BD8FF);
        connect(d, exports, core, 1145, 650, 950, 555, 0xFFFF8DD8);

        CanvasElement privacy = showcaseNode(
                CanvasElement.Type.RECTANGLE,
                520,
                885,
                460,
                92,
                "ON-DEVICE • PRIVATE • OFFLINE-FIRST",
                0xA514253D,
                0xFF5EE7F7
        );
        CanvasElement developer = showcaseNode(
                CanvasElement.Type.RECTANGLE,
                1080,
                885,
                460,
                92,
                "DEVELOPER • MOHNISH RAJ",
                0xA52B1E43,
                0xFFA889FF
        );
        privacy.locked = true;
        developer.locked = true;
        d.elements.add(privacy);
        d.elements.add(developer);
        SpatialSnapEngine.refreshConnectors(d);
    }

    private static CanvasElement showcaseNode(
            CanvasElement.Type type,
            float cx,
            float cy,
            float width,
            float height,
            String text,
            int fill,
            int stroke
    ) {
        CanvasElement element = CanvasElement.node(type, cx, cy, width, height, text);
        element.fillColor = fill;
        element.strokeColor = stroke;
        element.strokeWidth = 5f;
        return element;
    }

    private static void connect(
            DesignDocument document,
            CanvasElement start,
            CanvasElement end,
            float x1,
            float y1,
            float x2,
            float y2,
            int color
    ) {
        CanvasElement line = CanvasElement.line(x1, y1, x2, y2);
        line.startAnchorId = start.id;
        line.endAnchorId = end.id;
        line.strokeColor = color;
        line.strokeWidth = 5f;
        document.elements.add(line);
    }

}
