package com.mohnish.aircanvas.model;

import java.util.Objects;

public final class Bounds {
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    public Bounds(float left, float top, float right, float bottom) {
        this.left = Math.min(left, right);
        this.top = Math.min(top, bottom);
        this.right = Math.max(left, right);
        this.bottom = Math.max(top, bottom);
    }

    public float width() {
        return right - left;
    }

    public float height() {
        return bottom - top;
    }

    public float centerX() {
        return (left + right) * 0.5f;
    }

    public float centerY() {
        return (top + bottom) * 0.5f;
    }

    public boolean contains(float x, float y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    public Bounds union(Bounds other) {
        Objects.requireNonNull(other);
        return new Bounds(
                Math.min(left, other.left),
                Math.min(top, other.top),
                Math.max(right, other.right),
                Math.max(bottom, other.bottom)
        );
    }
}
