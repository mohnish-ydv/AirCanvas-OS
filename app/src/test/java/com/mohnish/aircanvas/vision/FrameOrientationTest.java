package com.mohnish.aircanvas.vision;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class FrameOrientationTest {
    @Test
    public void dimensionsSwapOnlyForQuarterTurns() {
        assertEquals(640, new FrameOrientation(640, 480, 0, false).outputWidth());
        assertEquals(480, new FrameOrientation(640, 480, 0, false).outputHeight());
        assertEquals(480, new FrameOrientation(640, 480, 90, false).outputWidth());
        assertEquals(640, new FrameOrientation(640, 480, 90, false).outputHeight());
        assertEquals(480, new FrameOrientation(640, 480, 270, true).outputWidth());
        assertEquals(640, new FrameOrientation(640, 480, 270, true).outputHeight());
    }

    @Test
    public void clockwiseRotationMapsCornersCorrectly() {
        assertArrayEquals(
                new float[]{1f, 0f},
                new FrameOrientation(640, 480, 90, false).mapNormalized(0f, 0f),
                0.0001f
        );
        assertArrayEquals(
                new float[]{1f, 1f},
                new FrameOrientation(640, 480, 180, false).mapNormalized(0f, 0f),
                0.0001f
        );
        assertArrayEquals(
                new float[]{0f, 1f},
                new FrameOrientation(640, 480, 270, false).mapNormalized(0f, 0f),
                0.0001f
        );
    }

    @Test
    public void frontMirrorMatchesNaturalPreview() {
        FrameOrientation orientation = new FrameOrientation(640, 480, 90, true);
        assertArrayEquals(
                new float[]{0f, 0f},
                orientation.mapNormalized(0f, 0f),
                0.0001f
        );
        assertArrayEquals(
                new float[]{1f, 1f},
                orientation.mapNormalized(1f, 1f),
                0.0001f
        );
    }

    @Test
    public void rotationIsSnappedAndNormalized() {
        assertEquals(0, FrameOrientation.normalizeRightAngle(360));
        assertEquals(270, FrameOrientation.normalizeRightAngle(-90));
        assertEquals(90, FrameOrientation.normalizeRightAngle(91));
    }
}
