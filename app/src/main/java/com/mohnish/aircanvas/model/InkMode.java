package com.mohnish.aircanvas.model;

public enum InkMode {
    AUTO("Auto"),
    SHAPE("Shape"),
    TEXT("Text");

    public final String label;

    InkMode(String label) {
        this.label = label;
    }

    public InkMode next(int direction) {
        InkMode[] values = values();
        int step = direction < 0 ? -1 : 1;
        int index = (ordinal() + step + values.length) % values.length;
        return values[index];
    }
}
