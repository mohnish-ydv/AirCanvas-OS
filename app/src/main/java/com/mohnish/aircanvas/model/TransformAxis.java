package com.mohnish.aircanvas.model;

public enum TransformAxis {
    FREE("XYZ"),
    X("X axis"),
    Y("Y axis"),
    Z("Z axis");

    public final String label;

    TransformAxis(String label) {
        this.label = label;
    }

    public TransformAxis next(int direction) {
        TransformAxis[] values = values();
        int step = direction < 0 ? -1 : 1;
        int index = (ordinal() + step + values.length) % values.length;
        return values[index];
    }
}
