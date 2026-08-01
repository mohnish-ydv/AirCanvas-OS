package com.mohnish.aircanvas.model;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public record ProjectInsights(
        int objects,
        int spatialObjects,
        int connectors,
        int strokes,
        int textObjects,
        int words,
        int groups,
        int lockedObjects
) {
    public static ProjectInsights analyze(DesignDocument document) {
        if (document == null) {
            return new ProjectInsights(0, 0, 0, 0, 0, 0, 0, 0);
        }
        int spatial = 0;
        int connectors = 0;
        int strokes = 0;
        int textObjects = 0;
        int words = 0;
        int locked = 0;
        Set<String> groupIds = new HashSet<>();
        for (CanvasElement element : document.elements) {
            if (element.isSpatial3d()) {
                spatial++;
            }
            if (element.type == CanvasElement.Type.LINE) {
                connectors++;
            }
            if (element.type == CanvasElement.Type.STROKE) {
                strokes++;
            }
            if (element.locked) {
                locked++;
            }
            if (element.groupId != null && !element.groupId.isBlank()) {
                groupIds.add(element.groupId);
            }
            String text = element.text == null ? "" : element.text.trim();
            if (!text.isEmpty()) {
                textObjects++;
                words += text.split("\\s+").length;
            }
        }
        return new ProjectInsights(
                document.elements.size(),
                spatial,
                connectors,
                strokes,
                textObjects,
                words,
                groupIds.size(),
                locked
        );
    }

    public String summary() {
        return String.format(
                Locale.US,
                "%d objects • %d true-3D • %d connectors • %d words",
                objects,
                spatialObjects,
                connectors,
                words
        );
    }
}
