package com.mohnish.aircanvas.export;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ExportSizingTest {
    @Test
    public void normalSceneKeepsRequestedWidthAndAspect() {
        ExportSizing.OutputSize size = ExportSizing.fit(1600f, 1000f, 2400);
        assertTrue(size.width() == 2400);
        assertTrue(size.height() == 1500);
    }

    @Test
    public void extremePortraitSceneStaysInsideMemoryAndDimensionCaps() {
        ExportSizing.OutputSize size = ExportSizing.fit(320f, 10_000f, 2400);
        assertTrue(size.width() <= ExportSizing.MAX_DIMENSION);
        assertTrue(size.height() <= ExportSizing.MAX_DIMENSION);
        assertTrue((long) size.width() * size.height() <= ExportSizing.MAX_PIXELS);
    }

    @Test
    public void nonFiniteDimensionsFallBackSafely() {
        ExportSizing.OutputSize size = ExportSizing.fit(
                Float.NaN,
                Float.POSITIVE_INFINITY,
                2400
        );
        assertTrue(size.width() > 0);
        assertTrue(size.height() > 0);
        assertTrue((long) size.width() * size.height() <= ExportSizing.MAX_PIXELS);
    }
}
