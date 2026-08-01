package com.mohnish.aircanvas.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CanvasElement {
    public enum Type {
        RECTANGLE,
        ELLIPSE,
        DIAMOND,
        TRIANGLE,
        HEXAGON,
        STAR,
        STICKY,
        LINE,
        STROKE,
        TEXT,
        FRAME,
        CUBE,
        SPHERE,
        CYLINDER,
        PYRAMID,
        CONE
    }

    public String id;
    public Type type;
    public float x1;
    public float y1;
    public float x2;
    public float y2;
    public String text;
    public int strokeColor;
    public int fillColor;
    public float strokeWidth;
    public String groupId;
    public boolean locked;
    public float rotationX;
    public float rotationY;
    public float rotationZ;
    public float depth;
    public float opacity;
    public String startAnchorId;
    public String endAnchorId;
    public final List<Float> points;
    private transient int meshProjectionSignature;
    private transient SpatialMesh.Projection meshProjectionCache;

    public CanvasElement(Type type, float x1, float y1, float x2, float y2) {
        this(UUID.randomUUID().toString(), type, x1, y1, x2, y2,
                "", 0xFF79E8F2, 0xCC14253D, 5f, null, false,
                0f, 0f, 0f, 1f, null, null, new ArrayList<>());
    }

    public CanvasElement(
            String id,
            Type type,
            float x1,
            float y1,
            float x2,
            float y2,
            String text,
            int strokeColor,
            int fillColor,
            float strokeWidth,
            String groupId,
            boolean locked,
            List<Float> points
    ) {
        this(
                id,
                type,
                x1,
                y1,
                x2,
                y2,
                text,
                strokeColor,
                fillColor,
                strokeWidth,
                groupId,
                locked,
                0f,
                0f,
                0f,
                1f,
                defaultDepth(x1, y1, x2, y2),
                null,
                null,
                points
        );
    }

    public CanvasElement(
            String id,
            Type type,
            float x1,
            float y1,
            float x2,
            float y2,
            String text,
            int strokeColor,
            int fillColor,
            float strokeWidth,
            String groupId,
            boolean locked,
            float rotationX,
            float rotationY,
            float rotationZ,
            float opacity,
            String startAnchorId,
            String endAnchorId,
            List<Float> points
    ) {
        this(
                id,
                type,
                x1,
                y1,
                x2,
                y2,
                text,
                strokeColor,
                fillColor,
                strokeWidth,
                groupId,
                locked,
                rotationX,
                rotationY,
                rotationZ,
                opacity,
                defaultDepth(x1, y1, x2, y2),
                startAnchorId,
                endAnchorId,
                points
        );
    }

    public CanvasElement(
            String id,
            Type type,
            float x1,
            float y1,
            float x2,
            float y2,
            String text,
            int strokeColor,
            int fillColor,
            float strokeWidth,
            String groupId,
            boolean locked,
            float rotationX,
            float rotationY,
            float rotationZ,
            float opacity,
            float depth,
            String startAnchorId,
            String endAnchorId,
            List<Float> points
    ) {
        this.id = id == null || id.isBlank()
                ? UUID.randomUUID().toString()
                : id.substring(0, Math.min(id.length(), 160));
        this.type = type == null ? Type.RECTANGLE : type;
        this.x1 = safeCoordinate(x1);
        this.y1 = safeCoordinate(y1);
        this.x2 = safeCoordinate(x2);
        this.y2 = safeCoordinate(y2);
        this.text = text == null
                ? ""
                : text.substring(0, Math.min(text.length(), 2000));
        this.strokeColor = strokeColor;
        this.fillColor = fillColor;
        this.strokeWidth = clamp(
                Float.isFinite(strokeWidth) ? strokeWidth : 5f,
                0.5f,
                100f
        );
        this.groupId = safeOptionalId(groupId);
        this.locked = locked;
        this.rotationX = normalizeDegrees(rotationX);
        this.rotationY = normalizeDegrees(rotationY);
        this.rotationZ = normalizeDegrees(rotationZ);
        this.depth = safeDepth(depth, x1, y1, x2, y2);
        this.opacity = clamp(Float.isFinite(opacity) ? opacity : 1f, 0.08f, 1f);
        this.startAnchorId = safeOptionalId(startAnchorId);
        this.endAnchorId = safeOptionalId(endAnchorId);
        List<Float> safePoints = points == null ? List.of() : points;
        this.points = new ArrayList<>(Math.min(safePoints.size(), 8192));
        for (int index = 0; index < Math.min(safePoints.size(), 8192); index++) {
            Float value = safePoints.get(index);
            this.points.add(safeCoordinate(value == null ? 0f : value));
        }
    }

    public static CanvasElement node(Type type, float cx, float cy, float width, float height, String text) {
        CanvasElement element = new CanvasElement(
                type,
                cx - width * 0.5f,
                cy - height * 0.5f,
                cx + width * 0.5f,
                cy + height * 0.5f
        );
        String safeText = text == null ? "" : text;
        element.text = safeText.substring(0, Math.min(safeText.length(), 2000));
        return element;
    }

    public static CanvasElement line(float x1, float y1, float x2, float y2) {
        CanvasElement element = new CanvasElement(Type.LINE, x1, y1, x2, y2);
        element.fillColor = 0x00000000;
        return element;
    }

    public static CanvasElement stroke(float x, float y) {
        CanvasElement element = new CanvasElement(Type.STROKE, x, y, x, y);
        element.fillColor = 0x00000000;
        element.points.add(x);
        element.points.add(y);
        return element;
    }

    public CanvasElement copy() {
        return new CanvasElement(
                id,
                type,
                x1,
                y1,
                x2,
                y2,
                text,
                strokeColor,
                fillColor,
                strokeWidth,
                groupId,
                locked,
                rotationX,
                rotationY,
                rotationZ,
                opacity,
                depth,
                startAnchorId,
                endAnchorId,
                points
        );
    }

    public CanvasElement duplicate() {
        return new CanvasElement(
                UUID.randomUUID().toString(),
                type,
                x1,
                y1,
                x2,
                y2,
                text,
                strokeColor,
                fillColor,
                strokeWidth,
                null,
                false,
                rotationX,
                rotationY,
                rotationZ,
                opacity,
                depth,
                null,
                null,
                points
        );
    }

    public Bounds bounds() {
        if (type != Type.STROKE || points.size() < 2) {
            return new Bounds(x1, y1, x2, y2);
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i + 1 < points.size(); i += 2) {
            float x = points.get(i);
            float y = points.get(i + 1);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    public Bounds visualBounds() {
        if (isSpatial3d()) {
            return meshProjection().bounds();
        }
        Bounds source = bounds();
        if (!hasTransform()) {
            return source;
        }
        float[] projected = new float[8];
        projectPoint(source.left, source.top, projected, 0);
        projectPoint(source.right, source.top, projected, 2);
        projectPoint(source.right, source.bottom, projected, 4);
        projectPoint(source.left, source.bottom, projected, 6);
        float minX = projected[0];
        float minY = projected[1];
        float maxX = projected[0];
        float maxY = projected[1];
        for (int index = 2; index < projected.length; index += 2) {
            minX = Math.min(minX, projected[index]);
            minY = Math.min(minY, projected[index + 1]);
            maxX = Math.max(maxX, projected[index]);
            maxY = Math.max(maxY, projected[index + 1]);
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    public boolean hasTransform() {
        return Math.abs(rotationX) > 0.01f
                || Math.abs(rotationY) > 0.01f
                || Math.abs(rotationZ) > 0.01f;
    }

    public boolean isSpatial3d() {
        return SpatialMesh.supports(type);
    }

    public SpatialMesh.Projection meshProjection() {
        if (!isSpatial3d()) {
            throw new IllegalStateException("Element is not a 3D primitive");
        }
        int signature = projectionSignature();
        if (meshProjectionCache == null || meshProjectionSignature != signature) {
            meshProjectionCache = SpatialMesh.project(this);
            meshProjectionSignature = signature;
        }
        return meshProjectionCache;
    }

    public void rotate(float deltaX, float deltaY, float deltaZ) {
        if (locked) {
            return;
        }
        rotationX = normalizeDegrees(rotationX + deltaX);
        rotationY = normalizeDegrees(rotationY + deltaY);
        rotationZ = normalizeDegrees(rotationZ + deltaZ);
    }

    public void setRotation(float xDegrees, float yDegrees, float zDegrees) {
        if (locked) {
            return;
        }
        rotationX = normalizeDegrees(xDegrees);
        rotationY = normalizeDegrees(yDegrees);
        rotationZ = normalizeDegrees(zDegrees);
    }

    public void projectPoint(float x, float y, float[] destination, int offset) {
        Bounds source = bounds();
        SpatialMesh.projectPlanarPoint(
                source,
                rotationX,
                rotationY,
                rotationZ,
                x,
                y,
                destination,
                offset
        );
    }

    public void addPoint(float x, float y) {
        if (points.size() >= 8192) {
            return;
        }
        points.add(safeCoordinate(x));
        points.add(safeCoordinate(y));
        Bounds bounds = bounds();
        x1 = bounds.left;
        y1 = bounds.top;
        x2 = bounds.right;
        y2 = bounds.bottom;
    }

    public void move(float dx, float dy) {
        if (locked || !Float.isFinite(dx) || !Float.isFinite(dy)) {
            return;
        }
        x1 = safeCoordinate(x1 + dx);
        x2 = safeCoordinate(x2 + dx);
        y1 = safeCoordinate(y1 + dy);
        y2 = safeCoordinate(y2 + dy);
        for (int i = 0; i + 1 < points.size(); i += 2) {
            points.set(i, safeCoordinate(points.get(i) + dx));
            points.set(i + 1, safeCoordinate(points.get(i + 1) + dy));
        }
    }

    public void scale(float factor, float pivotX, float pivotY) {
        if (locked
                || !Float.isFinite(factor)
                || !Float.isFinite(pivotX)
                || !Float.isFinite(pivotY)) {
            return;
        }
        factor = Math.max(0.2f, Math.min(5f, factor));
        x1 = safeCoordinate(pivotX + (x1 - pivotX) * factor);
        y1 = safeCoordinate(pivotY + (y1 - pivotY) * factor);
        x2 = safeCoordinate(pivotX + (x2 - pivotX) * factor);
        y2 = safeCoordinate(pivotY + (y2 - pivotY) * factor);
        for (int i = 0; i + 1 < points.size(); i += 2) {
            points.set(
                    i,
                    safeCoordinate(pivotX + (points.get(i) - pivotX) * factor)
            );
            points.set(
                    i + 1,
                    safeCoordinate(pivotY + (points.get(i + 1) - pivotY) * factor)
            );
        }
        strokeWidth = clamp(strokeWidth * factor, 1f, 100f);
        if (isSpatial3d()) {
            depth = clamp(depth * factor, 2f, 10_000f);
        }
    }

    public boolean hitTest(float x, float y, float tolerance) {
        if (isSpatial3d()) {
            return meshProjection().hitTest(x, y, tolerance);
        }
        if (type == Type.LINE) {
            float[] projected = new float[4];
            projectPoint(x1, y1, projected, 0);
            projectPoint(x2, y2, projected, 2);
            return distanceToSegment(
                    x,
                    y,
                    projected[0],
                    projected[1],
                    projected[2],
                    projected[3]
            ) <= tolerance;
        }
        if (type == Type.STROKE) {
            float[] projected = new float[4];
            for (int i = 0; i + 3 < points.size(); i += 2) {
                projectPoint(points.get(i), points.get(i + 1), projected, 0);
                projectPoint(points.get(i + 2), points.get(i + 3), projected, 2);
                if (distanceToSegment(
                        x,
                        y,
                        projected[0],
                        projected[1],
                        projected[2],
                        projected[3]
                ) <= tolerance) {
                    return true;
                }
            }
            return false;
        }
        Bounds b = bounds();
        if (!hasTransform()) {
            return x >= b.left - tolerance
                    && x <= b.right + tolerance
                    && y >= b.top - tolerance
                    && y <= b.bottom + tolerance;
        }
        float[] corners = new float[8];
        projectPoint(b.left, b.top, corners, 0);
        projectPoint(b.right, b.top, corners, 2);
        projectPoint(b.right, b.bottom, corners, 4);
        projectPoint(b.left, b.bottom, corners, 6);
        if (pointInQuadrilateral(x, y, corners)) {
            return true;
        }
        for (int index = 0; index < 4; index++) {
            int next = (index + 1) % 4;
            if (distanceToSegment(
                    x,
                    y,
                    corners[index * 2],
                    corners[index * 2 + 1],
                    corners[next * 2],
                    corners[next * 2 + 1]
            ) <= tolerance) {
                return true;
            }
        }
        return false;
    }

    private static boolean pointInQuadrilateral(float x, float y, float[] corners) {
        boolean positive = false;
        boolean negative = false;
        for (int index = 0; index < 4; index++) {
            int next = (index + 1) % 4;
            float cross = (corners[next * 2] - corners[index * 2])
                    * (y - corners[index * 2 + 1])
                    - (corners[next * 2 + 1] - corners[index * 2 + 1])
                    * (x - corners[index * 2]);
            positive |= cross > 0f;
            negative |= cross < 0f;
            if (positive && negative) {
                return false;
            }
        }
        return true;
    }

    private static float distanceToSegment(
            float px,
            float py,
            float ax,
            float ay,
            float bx,
            float by
    ) {
        float dx = bx - ax;
        float dy = by - ay;
        if (dx == 0f && dy == 0f) {
            return (float) Math.hypot(px - ax, py - ay);
        }
        float t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0f, Math.min(1f, t));
        float x = ax + t * dx;
        float y = ay + t * dy;
        return (float) Math.hypot(px - x, py - y);
    }

    private static float normalizeDegrees(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        float normalized = value % 360f;
        if (normalized > 180f) {
            normalized -= 360f;
        } else if (normalized < -180f) {
            normalized += 360f;
        }
        return normalized;
    }

    private int projectionSignature() {
        int result = type.ordinal();
        result = 31 * result + Float.floatToIntBits(x1);
        result = 31 * result + Float.floatToIntBits(y1);
        result = 31 * result + Float.floatToIntBits(x2);
        result = 31 * result + Float.floatToIntBits(y2);
        result = 31 * result + Float.floatToIntBits(depth);
        result = 31 * result + Float.floatToIntBits(rotationX);
        result = 31 * result + Float.floatToIntBits(rotationY);
        result = 31 * result + Float.floatToIntBits(rotationZ);
        return result;
    }

    private static float defaultDepth(float x1, float y1, float x2, float y2) {
        float width = Math.abs(x2 - x1);
        float height = Math.abs(y2 - y1);
        return clamp(Math.min(width, height) * 0.72f, 48f, 2_000f);
    }

    private static float safeDepth(
            float value,
            float x1,
            float y1,
            float x2,
            float y2
    ) {
        if (!Float.isFinite(value)) {
            return defaultDepth(x1, y1, x2, y2);
        }
        return clamp(value, 2f, 10_000f);
    }

    private static float safeCoordinate(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return clamp(value, -100_000f, 100_000f);
    }

    private static String safeOptionalId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.substring(0, Math.min(value.length(), 160));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
