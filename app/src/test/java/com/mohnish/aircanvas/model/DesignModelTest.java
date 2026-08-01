package com.mohnish.aircanvas.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.mohnish.aircanvas.data.DocumentCodec;
import com.mohnish.aircanvas.history.HistoryManager;

import org.json.JSONException;
import org.junit.Test;

public final class DesignModelTest {
    @Test
    public void everyCreatorTemplateBuildsAValidDocument() {
        for (TemplateFactory.Template template : TemplateFactory.Template.values()) {
            DesignDocument document = TemplateFactory.create(template);
            assertNotNull(document.id);
            assertEquals(template.label, document.template);
            assertTrue(document.pageWidth > 0f);
            assertTrue(document.pageHeight > 0f);
            if (template != TemplateFactory.Template.BLANK) {
                assertFalse(document.elements.isEmpty());
            }
        }
    }

    @Test
    public void documentCopyIsDeepAndIndependent() {
        DesignDocument source = TemplateFactory.create(TemplateFactory.Template.FLOWCHART);
        DesignDocument copy = source.copy();
        float original = source.elements.get(0).x1;
        copy.elements.get(0).move(100f, 0f);
        assertEquals(original, source.elements.get(0).x1, 0.001f);
        assertNotEquals(source.elements.get(0).x1, copy.elements.get(0).x1, 0.001f);
    }

    @Test
    public void jsonRoundTripPreservesEditableGeometry() throws Exception {
        DesignDocument source = TemplateFactory.create(TemplateFactory.Template.MIND_MAP);
        source.name = "Round Trip";
        source.spatialOverlay = true;
        source.elements.get(0).groupId = "group-a";
        source.elements.get(0).rotationX = 18f;
        source.elements.get(0).rotationY = -27f;
        source.elements.get(0).rotationZ = 145f;
        source.elements.get(0).opacity = 0.64f;
        source.elements.get(0).depth = 212f;
        source.elements.get(1).startAnchorId = source.elements.get(0).id;
        String encoded = DocumentCodec.encode(source);
        DesignDocument decoded = DocumentCodec.decode(encoded);

        assertEquals(source.id, decoded.id);
        assertEquals(source.name, decoded.name);
        assertEquals(source.template, decoded.template);
        assertEquals(source.elements.size(), decoded.elements.size());
        assertEquals(source.elements.get(0).type, decoded.elements.get(0).type);
        assertEquals(source.elements.get(0).groupId, decoded.elements.get(0).groupId);
        assertEquals(source.spatialOverlay, decoded.spatialOverlay);
        assertEquals(source.elements.get(0).rotationX, decoded.elements.get(0).rotationX, 0.001f);
        assertEquals(source.elements.get(0).rotationY, decoded.elements.get(0).rotationY, 0.001f);
        assertEquals(source.elements.get(0).rotationZ, decoded.elements.get(0).rotationZ, 0.001f);
        assertEquals(source.elements.get(0).opacity, decoded.elements.get(0).opacity, 0.001f);
        assertEquals(source.elements.get(0).depth, decoded.elements.get(0).depth, 0.001f);
        assertEquals(
                source.elements.get(1).startAnchorId,
                decoded.elements.get(1).startAnchorId
        );
    }

    @Test
    public void historySupportsUndoThenRedoWithoutAliasing() {
        HistoryManager history = new HistoryManager(10);
        DesignDocument document = new DesignDocument("History");
        history.checkpoint(document);
        document.elements.add(CanvasElement.node(
                CanvasElement.Type.RECTANGLE,
                500f,
                400f,
                200f,
                100f,
                "A"
        ));

        DesignDocument undone = history.undo(document);
        assertTrue(undone.elements.isEmpty());
        assertTrue(history.canRedo());

        DesignDocument redone = history.redo(undone);
        assertEquals(1, redone.elements.size());
        redone.elements.get(0).text = "Changed";
        assertEquals("A", document.elements.get(0).text);
    }

    @Test
    public void lineHitTestingUsesDistanceNotBoundingBoxOnly() {
        CanvasElement line = CanvasElement.line(0f, 0f, 100f, 100f);
        assertTrue(line.hitTest(50f, 52f, 4f));
        assertFalse(line.hitTest(50f, 80f, 4f));
    }

    @Test
    public void strokeCopyMoveAndScaleRemainIndependent() {
        CanvasElement stroke = CanvasElement.stroke(10f, 20f);
        stroke.addPoint(30f, 40f);
        CanvasElement copy = stroke.copy();

        copy.move(10f, 5f);
        copy.scale(2f, 20f, 25f);

        assertEquals(10f, stroke.points.get(0), 0.001f);
        assertEquals(20f, stroke.points.get(1), 0.001f);
        assertNotEquals(stroke.points.get(0), copy.points.get(0));
    }

    @Test(expected = JSONException.class)
    public void futureJsonSchemaIsRejected() throws Exception {
        DocumentCodec.decode("{\"schemaVersion\":999,\"elements\":[]}");
    }

    @Test(expected = JSONException.class)
    public void duplicateImportedElementIdsAreRejected() throws Exception {
        DesignDocument source = new DesignDocument("Duplicate IDs");
        CanvasElement first = CanvasElement.node(
                CanvasElement.Type.RECTANGLE,
                200f,
                200f,
                100f,
                100f,
                "A"
        );
        CanvasElement second = first.copy();
        second.move(200f, 0f);
        source.elements.add(first);
        source.elements.add(second);
        DocumentCodec.decode(DocumentCodec.encode(source));
    }

    @Test
    public void historyCapacityDropsTheOldestCheckpoint() {
        HistoryManager history = new HistoryManager(2);
        DesignDocument document = new DesignDocument("Capacity");
        history.checkpoint(document);
        document.name = "One";
        history.checkpoint(document);
        document.name = "Two";
        history.checkpoint(document);
        document.name = "Three";

        DesignDocument firstUndo = history.undo(document);
        DesignDocument secondUndo = history.undo(firstUndo);
        assertEquals("Two", firstUndo.name);
        assertEquals("One", secondUndo.name);
        assertFalse(history.canUndo());
    }

    @Test
    public void historyMemoryBudgetDropsHeavyOldSnapshots() {
        HistoryManager history = new HistoryManager(20, 512L * 1024L);
        DesignDocument document = new DesignDocument("Heavy history");
        CanvasElement stroke = CanvasElement.stroke(0f, 0f);
        for (int index = 0; index < 4095; index++) {
            stroke.addPoint(index, index);
        }
        document.elements.add(stroke);
        for (int index = 0; index < 8; index++) {
            document.name = "State " + index;
            history.checkpoint(document);
        }

        int available = 0;
        while (history.canUndo()) {
            document = history.undo(document);
            available++;
        }
        assertTrue(available < 8);
        assertTrue(available >= 1);
    }

    @Test
    public void projectedRotationChangesVisualBoundsAndRemainsSelectable() {
        CanvasElement element = CanvasElement.node(
                CanvasElement.Type.RECTANGLE,
                500f,
                400f,
                300f,
                140f,
                "3D"
        );
        Bounds before = element.visualBounds();
        element.setRotation(34f, 42f, 28f);
        Bounds after = element.visualBounds();
        float[] projectedCenter = new float[2];
        element.projectPoint(before.centerX(), before.centerY(), projectedCenter, 0);

        assertNotEquals(before.width(), after.width(), 0.01f);
        assertTrue(element.hitTest(projectedCenter[0], projectedCenter[1], 2f));
    }

    @Test
    public void magneticMoveSnapFindsCenterAlignment() {
        DesignDocument document = new DesignDocument("Snap");
        CanvasElement moving = CanvasElement.node(
                CanvasElement.Type.RECTANGLE,
                300f,
                300f,
                180f,
                100f,
                ""
        );
        CanvasElement target = CanvasElement.node(
                CanvasElement.Type.RECTANGLE,
                700f,
                500f,
                180f,
                100f,
                ""
        );
        document.elements.add(moving);
        document.elements.add(target);
        SpatialSnapEngine.MoveSnap snap = SpatialSnapEngine.snapMove(
                document,
                java.util.Set.of(moving.id),
                moving.visualBounds(),
                396f,
                197f,
                8f
        );

        assertEquals(400f, snap.dx(), 0.001f);
        assertEquals(200f, snap.dy(), 0.001f);
        assertTrue(snap.hasGuideX());
        assertTrue(snap.hasGuideY());
    }

    @Test
    public void connectorsStayAttachedWhenTheirObjectsMove() {
        DesignDocument document = new DesignDocument("Connectors");
        CanvasElement first = CanvasElement.node(
                CanvasElement.Type.RECTANGLE,
                250f,
                300f,
                200f,
                120f,
                "A"
        );
        CanvasElement second = CanvasElement.node(
                CanvasElement.Type.ELLIPSE,
                750f,
                300f,
                200f,
                120f,
                "B"
        );
        CanvasElement line = CanvasElement.line(350f, 300f, 650f, 300f);
        line.startAnchorId = first.id;
        line.endAnchorId = second.id;
        document.elements.add(first);
        document.elements.add(line);
        document.elements.add(second);
        SpatialSnapEngine.refreshConnectors(document);
        float oldEnd = line.x2;
        second.move(120f, 0f);
        SpatialSnapEngine.refreshConnectors(document);

        assertTrue(line.x2 > oldEnd);
    }

    @Test
    public void oldSchemaMigratesIntoSpatialOverlay() throws Exception {
        DesignDocument migrated = DocumentCodec.decode(
                "{\"schemaVersion\":1,\"name\":\"Legacy\",\"elements\":[]}"
        );
        assertTrue(migrated.spatialOverlay);
    }

    @Test
    public void nonFiniteTransformsCannotPoisonSceneGeometry() {
        CanvasElement element = new CanvasElement(
                "safe",
                CanvasElement.Type.RECTANGLE,
                10f,
                20f,
                110f,
                80f,
                "",
                0xFFFFFFFF,
                0xFF000000,
                5f,
                null,
                false,
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.NaN,
                null,
                null,
                java.util.List.of()
        );
        element.move(Float.NaN, 20f);
        element.scale(Float.POSITIVE_INFINITY, 0f, 0f);

        assertEquals(10f, element.x1, 0.001f);
        assertEquals(20f, element.y1, 0.001f);
        assertEquals(0f, element.rotationX, 0.001f);
        assertEquals(0f, element.rotationY, 0.001f);
        assertEquals(0f, element.rotationZ, 0.001f);
        assertEquals(1f, element.opacity, 0.001f);
    }

    @Test
    public void nonFinitePageDimensionsUseTheCorrectAspectFallbacks() {
        DesignDocument document = new DesignDocument(
                null,
                "Safe dimensions",
                "Blank",
                Float.NaN,
                Float.POSITIVE_INFINITY,
                0L,
                0L,
                true,
                java.util.List.of()
        );
        assertEquals(DesignDocument.DEFAULT_WIDTH, document.pageWidth, 0.001f);
        assertEquals(DesignDocument.DEFAULT_HEIGHT, document.pageHeight, 0.001f);
    }

    @Test
    public void freehandPointBufferIsBounded() {
        CanvasElement stroke = CanvasElement.stroke(0f, 0f);
        for (int index = 0; index < 5000; index++) {
            stroke.addPoint(index, index);
        }
        assertEquals(8192, stroke.points.size());
    }

    @Test
    public void connectorRefreshHandlesDenseScenesConsistently() {
        DesignDocument document = new DesignDocument("Dense");
        CanvasElement previous = null;
        for (int index = 0; index < 120; index++) {
            CanvasElement node = CanvasElement.node(
                    CanvasElement.Type.RECTANGLE,
                    100f + index * 18f,
                    300f + (index % 5) * 100f,
                    120f,
                    70f,
                    "N" + index
            );
            document.elements.add(node);
            if (previous != null) {
                CanvasElement connector = CanvasElement.line(
                        previous.x2,
                        previous.y2,
                        node.x1,
                        node.y1
                );
                connector.startAnchorId = previous.id;
                connector.endAnchorId = node.id;
                document.elements.add(connector);
            }
            previous = node;
        }

        SpatialSnapEngine.refreshConnectors(document);
        long attached = document.elements.stream()
                .filter(element -> element.type == CanvasElement.Type.LINE)
                .filter(element -> element.startAnchorId != null && element.endAnchorId != null)
                .count();
        assertEquals(119L, attached);
    }
}
