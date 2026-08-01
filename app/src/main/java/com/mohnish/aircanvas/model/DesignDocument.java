package com.mohnish.aircanvas.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class DesignDocument {
    public static final float DEFAULT_WIDTH = 1600f;
    public static final float DEFAULT_HEIGHT = 1000f;

    public String id;
    public String name;
    public String template;
    public float pageWidth;
    public float pageHeight;
    public long createdAt;
    public long updatedAt;
    public boolean spatialOverlay;
    public final List<CanvasElement> elements;

    public DesignDocument(String name) {
        this(
                UUID.randomUUID().toString(),
                name,
                "Blank",
                DEFAULT_WIDTH,
                DEFAULT_HEIGHT,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                true,
                new ArrayList<>()
        );
    }

    public DesignDocument(
            String id,
            String name,
            String template,
            float pageWidth,
            float pageHeight,
            long createdAt,
            long updatedAt,
            List<CanvasElement> elements
    ) {
        this(
                id,
                name,
                template,
                pageWidth,
                pageHeight,
                createdAt,
                updatedAt,
                true,
                elements
        );
    }

    public DesignDocument(
            String id,
            String name,
            String template,
            float pageWidth,
            float pageHeight,
            long createdAt,
            long updatedAt,
            boolean spatialOverlay,
            List<CanvasElement> elements
    ) {
        String safeId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.id = safeId.substring(0, Math.min(safeId.length(), 160));
        String safeName = name == null || name.isBlank() ? "Untitled Design" : name;
        this.name = safeName.substring(0, Math.min(safeName.length(), 160));
        String safeTemplate = template == null || template.isBlank() ? "Blank" : template;
        this.template = safeTemplate.substring(0, Math.min(safeTemplate.length(), 160));
        this.pageWidth = safeDimension(pageWidth, DEFAULT_WIDTH);
        this.pageHeight = safeDimension(pageHeight, DEFAULT_HEIGHT);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.spatialOverlay = spatialOverlay;
        this.elements = elements == null ? new ArrayList<>() : new ArrayList<>(elements);
    }

    public DesignDocument copy() {
        List<CanvasElement> copies = new ArrayList<>(elements.size());
        for (CanvasElement element : elements) {
            copies.add(element.copy());
        }
        return new DesignDocument(
                id,
                name,
                template,
                pageWidth,
                pageHeight,
                createdAt,
                updatedAt,
                spatialOverlay,
                copies
        );
    }

    public CanvasElement find(String elementId) {
        if (elementId == null) {
            return null;
        }
        for (CanvasElement element : elements) {
            if (elementId.equals(element.id)) {
                return element;
            }
        }
        return null;
    }

    public CanvasElement hitTest(float x, float y, float tolerance) {
        for (int i = elements.size() - 1; i >= 0; i--) {
            CanvasElement element = elements.get(i);
            if (element.hitTest(x, y, tolerance)) {
                return element;
            }
        }
        return null;
    }

    public List<CanvasElement> elementsInGroup(String groupId) {
        if (groupId == null) {
            return Collections.emptyList();
        }
        List<CanvasElement> result = new ArrayList<>();
        for (CanvasElement element : elements) {
            if (groupId.equals(element.groupId)) {
                result.add(element);
            }
        }
        return result;
    }

    public void touch() {
        updatedAt = System.currentTimeMillis();
    }

    private static float safeDimension(float value, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(320f, Math.min(10_000f, value));
    }
}
