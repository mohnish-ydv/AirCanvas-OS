package com.mohnish.aircanvas.export;

import static org.junit.Assert.assertTrue;

import com.mohnish.aircanvas.model.CanvasElement;
import com.mohnish.aircanvas.model.DesignDocument;

import org.junit.Test;

public final class SvgExporterTest {
    @Test
    public void svgContainsProjectedShapesTextAndConnectors() {
        DesignDocument document = new DesignDocument("Spatial & Vector");
        CanvasElement shape = CanvasElement.node(
                CanvasElement.Type.HEXAGON,
                500f,
                400f,
                260f,
                180f,
                "CORE <A>"
        );
        shape.rotationX = 24f;
        shape.rotationY = -18f;
        shape.rotationZ = 42f;
        document.elements.add(shape);
        document.elements.add(CanvasElement.line(620f, 400f, 900f, 520f));

        String svg = SvgExporter.encode(document);

        assertTrue(svg.startsWith("<?xml"));
        assertTrue(svg.contains("<polygon"));
        assertTrue(svg.contains("<line"));
        assertTrue(svg.contains("CORE &lt;A&gt;"));
        assertTrue(svg.contains("viewBox=\"0 0 1600.00 1000.00\""));
    }

    @Test
    public void svgExportsTrue3dCubeAsSixProjectedFaces() {
        DesignDocument document = new DesignDocument("3D");
        CanvasElement cube = CanvasElement.node(
                CanvasElement.Type.CUBE,
                500f,
                400f,
                240f,
                240f,
                ""
        );
        cube.depth = 220f;
        cube.setRotation(28f, 44f, 16f);
        document.elements.add(cube);

        String svg = SvgExporter.encode(document);
        int faces = svg.split("<polygon ", -1).length - 1;
        assertTrue(faces == 6);
    }
}
