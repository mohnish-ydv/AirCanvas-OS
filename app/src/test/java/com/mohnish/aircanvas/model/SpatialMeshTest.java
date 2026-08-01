package com.mohnish.aircanvas.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SpatialMeshTest {
    @Test
    public void cubeProjectsSixDepthSortedFacesAndInvalidatesOnRotation() {
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

        assertEquals(6, front.faces().size());
        assertEquals(6, rotated.faces().size());
        assertNotSame(front, rotated);
        for (int index = 1; index < rotated.faces().size(); index++) {
            assertTrue(
                    rotated.faces().get(index - 1).depth()
                            <= rotated.faces().get(index).depth()
            );
        }
    }

    @Test
    public void completeTurnOnEveryAxisReturnsToTheOriginalProjection() {
        CanvasElement solid = CanvasElement.node(
                CanvasElement.Type.PYRAMID,
                500f,
                400f,
                260f,
                220f,
                ""
        );
        Bounds original = solid.meshProjection().bounds();
        solid.setRotation(360f, 360f, 360f);
        Bounds fullTurn = solid.meshProjection().bounds();

        assertEquals(original.left, fullTurn.left, 0.05f);
        assertEquals(original.top, fullTurn.top, 0.05f);
        assertEquals(original.right, fullTurn.right, 0.05f);
        assertEquals(original.bottom, fullTurn.bottom, 0.05f);
    }

    @Test
    public void copyAndScalePreserveRealDepth() {
        CanvasElement cylinder = CanvasElement.node(
                CanvasElement.Type.CYLINDER,
                400f,
                300f,
                180f,
                260f,
                ""
        );
        cylinder.depth = 150f;
        CanvasElement copy = cylinder.copy();
        copy.scale(2f, 400f, 300f);

        assertEquals(150f, cylinder.depth, 0.001f);
        assertEquals(300f, copy.depth, 0.001f);
    }
}
