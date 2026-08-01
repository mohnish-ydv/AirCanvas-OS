package com.mohnish.aircanvas.model;

public enum ShapeKind {
    RECTANGLE("Rectangle", CanvasElement.Type.RECTANGLE),
    ELLIPSE("Ellipse", CanvasElement.Type.ELLIPSE),
    DIAMOND("Diamond", CanvasElement.Type.DIAMOND),
    TRIANGLE("Triangle", CanvasElement.Type.TRIANGLE),
    HEXAGON("Hexagon", CanvasElement.Type.HEXAGON),
    STAR("Star", CanvasElement.Type.STAR),
    STICKY("Sticky note", CanvasElement.Type.STICKY),
    FRAME("Boundary", CanvasElement.Type.FRAME),
    CUBE("3D cube", CanvasElement.Type.CUBE),
    SPHERE("3D sphere", CanvasElement.Type.SPHERE),
    CYLINDER("3D cylinder", CanvasElement.Type.CYLINDER),
    PYRAMID("3D pyramid", CanvasElement.Type.PYRAMID),
    CONE("3D cone", CanvasElement.Type.CONE);

    public final String label;
    public final CanvasElement.Type elementType;

    ShapeKind(String label, CanvasElement.Type elementType) {
        this.label = label;
        this.elementType = elementType;
    }

    public ShapeKind next(int direction) {
        ShapeKind[] values = values();
        int step = direction < 0 ? -1 : 1;
        int index = (ordinal() + step + values.length) % values.length;
        return values[index];
    }
}
