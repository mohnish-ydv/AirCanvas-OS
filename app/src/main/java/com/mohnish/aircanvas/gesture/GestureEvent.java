package com.mohnish.aircanvas.gesture;

public final class GestureEvent {
    public enum Type {
        CURSOR,
        POSE,
        PINCH_START,
        PINCH_MOVE,
        PINCH_END,
        FIST_START,
        FIST_MOVE,
        FIST_END,
        OPEN_PALM_DWELL,
        SWIPE_LEFT,
        SWIPE_RIGHT,
        SWIPE_UP,
        SWIPE_DOWN,
        MODE_MENU,
        SHAPE_MENU,
        AUTO_SPIN_START,
        AUTO_SPIN_END,
        TWO_HAND_SCALE_START,
        TWO_HAND_SCALE_UPDATE,
        TWO_HAND_SCALE_END,
        HAND_LOST
    }

    public final Type type;
    public final float x;
    public final float y;
    public final float dx;
    public final float dy;
    public final float scale;
    public final float rotation;
    public final String label;
    public final long timestampMs;

    private GestureEvent(
            Type type,
            float x,
            float y,
            float dx,
            float dy,
            float scale,
            float rotation,
            String label,
            long timestampMs
    ) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.scale = scale;
        this.rotation = rotation;
        this.label = label;
        this.timestampMs = timestampMs;
    }

    public static GestureEvent at(Type type, float x, float y, long timestampMs) {
        return new GestureEvent(type, x, y, 0f, 0f, 1f, 0f, "", timestampMs);
    }

    public static GestureEvent move(
            Type type,
            float x,
            float y,
            float dx,
            float dy,
            long timestampMs
    ) {
        return new GestureEvent(type, x, y, dx, dy, 1f, 0f, "", timestampMs);
    }

    public static GestureEvent scale(
            Type type,
            float x,
            float y,
            float scale,
            long timestampMs
    ) {
        return new GestureEvent(type, x, y, 0f, 0f, scale, 0f, "", timestampMs);
    }

    public static GestureEvent transform(
            Type type,
            float x,
            float y,
            float scale,
            float rotation,
            long timestampMs
    ) {
        return new GestureEvent(
                type,
                x,
                y,
                0f,
                0f,
                scale,
                rotation,
                "",
                timestampMs
        );
    }

    public static GestureEvent pose(String label, float x, float y, long timestampMs) {
        return new GestureEvent(Type.POSE, x, y, 0f, 0f, 1f, 0f, label, timestampMs);
    }
}
