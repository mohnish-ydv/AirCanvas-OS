package com.mohnish.aircanvas.export;

public final class ExportSizing {
    public static final int MAX_DIMENSION = 4096;
    public static final long MAX_PIXELS = 6_000_000L;

    private ExportSizing() {
    }

    public static OutputSize fit(
            float pageWidth,
            float pageHeight,
            int requestedWidth
    ) {
        float safeWidth = Float.isFinite(pageWidth) && pageWidth > 0f ? pageWidth : 1600f;
        float safeHeight = Float.isFinite(pageHeight) && pageHeight > 0f ? pageHeight : 1000f;
        int width = clamp(requestedWidth, 320, MAX_DIMENSION);
        double aspect = safeHeight / (double) safeWidth;
        int height = Math.max(1, (int) Math.round(width * aspect));
        if (height > MAX_DIMENSION) {
            height = MAX_DIMENSION;
            width = Math.max(1, (int) Math.round(height / aspect));
        }
        long pixels = (long) width * height;
        if (pixels > MAX_PIXELS) {
            double factor = Math.sqrt(MAX_PIXELS / (double) pixels);
            width = Math.max(1, (int) Math.floor(width * factor));
            height = Math.max(1, (int) Math.floor(height * factor));
        }
        return new OutputSize(width, height);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record OutputSize(int width, int height) {
    }
}
