package com.mohnish.aircanvas.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SelectionOperations {
    public enum Alignment {
        LEFT("Align left"),
        CENTER_X("Center horizontally"),
        RIGHT("Align right"),
        TOP("Align top"),
        CENTER_Y("Center vertically"),
        BOTTOM("Align bottom");

        public final String label;

        Alignment(String label) {
            this.label = label;
        }
    }

    public enum Distribution {
        HORIZONTAL("Distribute horizontally"),
        VERTICAL("Distribute vertically");

        public final String label;

        Distribution(String label) {
            this.label = label;
        }
    }

    private SelectionOperations() {
    }

    public static int applyStyle(
            DesignDocument document,
            Set<String> selectedIds,
            StylePreset preset
    ) {
        if (document == null || selectedIds == null || preset == null) {
            return 0;
        }
        int changed = 0;
        for (CanvasElement element : selected(document, selectedIds, false)) {
            preset.apply(element);
            changed++;
        }
        return changed;
    }

    public static int setStrokeWidth(
            DesignDocument document,
            Set<String> selectedIds,
            float width
    ) {
        if (!Float.isFinite(width)) {
            return 0;
        }
        float safeWidth = Math.max(0.5f, Math.min(40f, width));
        int changed = 0;
        for (CanvasElement element : selected(document, selectedIds, false)) {
            element.strokeWidth = safeWidth;
            changed++;
        }
        return changed;
    }

    public static int setLocked(
            DesignDocument document,
            Set<String> selectedIds,
            boolean locked
    ) {
        int changed = 0;
        for (CanvasElement element : selected(document, selectedIds, true)) {
            if (element.locked != locked) {
                element.locked = locked;
                changed++;
            }
        }
        return changed;
    }

    public static int align(
            DesignDocument document,
            Set<String> selectedIds,
            Alignment alignment
    ) {
        List<CanvasElement> elements = selected(document, selectedIds, false);
        if (elements.size() < 2 || alignment == null) {
            return 0;
        }
        Bounds group = union(elements);
        for (CanvasElement element : elements) {
            Bounds bounds = element.bounds();
            float dx = 0f;
            float dy = 0f;
            switch (alignment) {
                case LEFT -> dx = group.left - bounds.left;
                case CENTER_X -> dx = group.centerX() - bounds.centerX();
                case RIGHT -> dx = group.right - bounds.right;
                case TOP -> dy = group.top - bounds.top;
                case CENTER_Y -> dy = group.centerY() - bounds.centerY();
                case BOTTOM -> dy = group.bottom - bounds.bottom;
            }
            element.move(dx, dy);
        }
        return elements.size();
    }

    public static int distribute(
            DesignDocument document,
            Set<String> selectedIds,
            Distribution distribution
    ) {
        List<CanvasElement> elements = selected(document, selectedIds, false);
        if (elements.size() < 3 || distribution == null) {
            return 0;
        }
        Comparator<CanvasElement> comparator = distribution == Distribution.HORIZONTAL
                ? Comparator.comparingDouble(element -> element.bounds().centerX())
                : Comparator.comparingDouble(element -> element.bounds().centerY());
        elements.sort(comparator);
        CanvasElement first = elements.get(0);
        CanvasElement last = elements.get(elements.size() - 1);
        float start = distribution == Distribution.HORIZONTAL
                ? first.bounds().centerX()
                : first.bounds().centerY();
        float end = distribution == Distribution.HORIZONTAL
                ? last.bounds().centerX()
                : last.bounds().centerY();
        float spacing = (end - start) / (elements.size() - 1f);
        for (int index = 1; index < elements.size() - 1; index++) {
            CanvasElement element = elements.get(index);
            Bounds bounds = element.bounds();
            float target = start + spacing * index;
            if (distribution == Distribution.HORIZONTAL) {
                element.move(target - bounds.centerX(), 0f);
            } else {
                element.move(0f, target - bounds.centerY());
            }
        }
        return elements.size();
    }

    public static boolean hasLocked(
            DesignDocument document,
            Set<String> selectedIds
    ) {
        for (CanvasElement element : selected(document, selectedIds, true)) {
            if (element.locked) {
                return true;
            }
        }
        return false;
    }

    private static List<CanvasElement> selected(
            DesignDocument document,
            Set<String> selectedIds,
            boolean includeLocked
    ) {
        List<CanvasElement> result = new ArrayList<>();
        if (document == null || selectedIds == null || selectedIds.isEmpty()) {
            return result;
        }
        Set<String> safeIds = new HashSet<>(selectedIds);
        for (CanvasElement element : document.elements) {
            if (safeIds.contains(element.id) && (includeLocked || !element.locked)) {
                result.add(element);
            }
        }
        return result;
    }

    private static Bounds union(List<CanvasElement> elements) {
        Bounds first = elements.get(0).bounds();
        float left = first.left;
        float top = first.top;
        float right = first.right;
        float bottom = first.bottom;
        for (int index = 1; index < elements.size(); index++) {
            Bounds bounds = elements.get(index).bounds();
            left = Math.min(left, bounds.left);
            top = Math.min(top, bounds.top);
            right = Math.max(right, bounds.right);
            bottom = Math.max(bottom, bounds.bottom);
        }
        return new Bounds(left, top, right, bottom);
    }
}
