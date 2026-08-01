package com.mohnish.aircanvas.model;

public enum StylePreset {
    NEON_GLASS("Neon Glass", 0xFF5EE7F7, 0x7A14253D, 5.5f, 0.96f),
    HOLOGRAM("Hologram", 0xFFA889FF, 0x665263C7, 4.5f, 0.88f),
    BLUEPRINT("Blueprint", 0xFF8BD8FF, 0xB20A2442, 3.5f, 1f),
    SUNSET_SIGNAL("Sunset Signal", 0xFFFFB36B, 0xC0502038, 5f, 0.98f),
    MONO_PRO("Mono Pro", 0xFFF4F8FF, 0xB3182535, 3f, 1f),
    WARNING("Warning", 0xFFFFD45E, 0xCC3A2507, 6f, 1f);

    public final String label;
    public final int strokeColor;
    public final int fillColor;
    public final float strokeWidth;
    public final float opacity;

    StylePreset(
            String label,
            int strokeColor,
            int fillColor,
            float strokeWidth,
            float opacity
    ) {
        this.label = label;
        this.strokeColor = strokeColor;
        this.fillColor = fillColor;
        this.strokeWidth = strokeWidth;
        this.opacity = opacity;
    }

    public void apply(CanvasElement element) {
        if (element == null || element.locked) {
            return;
        }
        element.strokeColor = strokeColor;
        element.strokeWidth = strokeWidth;
        element.opacity = opacity;
        if (element.type == CanvasElement.Type.LINE
                || element.type == CanvasElement.Type.STROKE
                || element.type == CanvasElement.Type.TEXT) {
            element.fillColor = 0x00000000;
        } else {
            element.fillColor = fillColor;
        }
    }
}
