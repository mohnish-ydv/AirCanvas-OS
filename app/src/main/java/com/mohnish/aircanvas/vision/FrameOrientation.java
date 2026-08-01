package com.mohnish.aircanvas.vision;

/**
 * Immutable description of the transform required to align an ImageAnalysis
 * frame with the visible CameraX preview.
 *
 * <p>Camera sensor buffers are not guaranteed to arrive in display orientation.
 * Front-camera previews are also mirrored for natural interaction. Keeping this
 * transform explicit prevents the preview, landmarks, cursor and swipe axes from
 * silently drifting apart on OEM camera implementations.</p>
 */
public final class FrameOrientation {
    public final int sourceWidth;
    public final int sourceHeight;
    public final int rotationDegrees;
    public final boolean mirrorHorizontally;

    public FrameOrientation(
            int sourceWidth,
            int sourceHeight,
            int rotationDegrees,
            boolean mirrorHorizontally
    ) {
        this.sourceWidth = Math.max(1, sourceWidth);
        this.sourceHeight = Math.max(1, sourceHeight);
        this.rotationDegrees = normalizeRightAngle(rotationDegrees);
        this.mirrorHorizontally = mirrorHorizontally;
    }

    public int outputWidth() {
        return swapsDimensions() ? sourceHeight : sourceWidth;
    }

    public int outputHeight() {
        return swapsDimensions() ? sourceWidth : sourceHeight;
    }

    public boolean swapsDimensions() {
        return rotationDegrees == 90 || rotationDegrees == 270;
    }

    /**
     * Maps a normalized point from the raw sensor buffer into the normalized
     * preview-aligned frame. Used by deterministic tests and diagnostics.
     */
    public float[] mapNormalized(float x, float y) {
        float mappedX;
        float mappedY;
        switch (rotationDegrees) {
            case 90 -> {
                mappedX = 1f - y;
                mappedY = x;
            }
            case 180 -> {
                mappedX = 1f - x;
                mappedY = 1f - y;
            }
            case 270 -> {
                mappedX = y;
                mappedY = 1f - x;
            }
            default -> {
                mappedX = x;
                mappedY = y;
            }
        }
        if (mirrorHorizontally) {
            mappedX = 1f - mappedX;
        }
        return new float[]{clamp01(mappedX), clamp01(mappedY)};
    }


    /** Returns an Android Matrix-compatible 3x3 pixel transform. */
    public float[] matrixValues() {
        float scaleX;
        float skewX;
        float translateX;
        float skewY;
        float scaleY;
        float translateY;
        switch (rotationDegrees) {
            case 90 -> {
                scaleX = 0f;
                skewX = -1f;
                translateX = sourceHeight;
                skewY = 1f;
                scaleY = 0f;
                translateY = 0f;
            }
            case 180 -> {
                scaleX = -1f;
                skewX = 0f;
                translateX = sourceWidth;
                skewY = 0f;
                scaleY = -1f;
                translateY = sourceHeight;
            }
            case 270 -> {
                scaleX = 0f;
                skewX = 1f;
                translateX = 0f;
                skewY = -1f;
                scaleY = 0f;
                translateY = sourceWidth;
            }
            default -> {
                scaleX = 1f;
                skewX = 0f;
                translateX = 0f;
                skewY = 0f;
                scaleY = 1f;
                translateY = 0f;
            }
        }
        if (mirrorHorizontally) {
            scaleX = -scaleX;
            skewX = -skewX;
            translateX = outputWidth() - translateX;
        }
        return new float[]{
                scaleX, skewX, translateX,
                skewY, scaleY, translateY,
                0f, 0f, 1f
        };
    }

    public static int normalizeRightAngle(int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        int snapped = ((normalized + 45) / 90) * 90;
        return snapped == 360 ? 0 : snapped;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
