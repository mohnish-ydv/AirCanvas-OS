package com.mohnish.aircanvas.model;

public enum Tool {
    SELECT("Select"),
    BLOCK("Shape"),
    LINE("Connect"),
    PEN("Draw"),
    SMART_INK("Smart Ink"),
    TEXT("Text"),
    TRANSFORM("True 3D"),
    ERASE("Erase"),
    PRESENT("Present");

    public final String label;

    Tool(String label) {
        this.label = label;
    }
}
