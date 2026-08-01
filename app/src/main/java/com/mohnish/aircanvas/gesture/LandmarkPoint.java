package com.mohnish.aircanvas.gesture;

public final class LandmarkPoint {
    public final float x;
    public final float y;
    public final float z;

    public LandmarkPoint(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public LandmarkPoint mix(LandmarkPoint other, float alpha) {
        float inverse = 1f - alpha;
        return new LandmarkPoint(
                x * inverse + other.x * alpha,
                y * inverse + other.y * alpha,
                z * inverse + other.z * alpha
        );
    }

    public float distance2D(LandmarkPoint other) {
        return (float) Math.hypot(x - other.x, y - other.y);
    }
}
