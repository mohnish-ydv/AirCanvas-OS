package com.mohnish.aircanvas.ink;

import com.mohnish.aircanvas.model.Bounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable one-or-more-stroke handwriting request. */
public final class SmartInkRequest {
    /** Legacy aliases for the first component stroke. */
    public final String elementId;
    public final int fingerprint;
    public final List<Float> points;

    public final List<String> elementIds;
    public final List<Integer> fingerprints;
    public final List<List<Float>> strokes;
    public final Bounds bounds;
    public final long completedAtMs;

    public SmartInkRequest(String elementId, List<Float> points, long completedAtMs) {
        this(
                List.of(elementId == null ? "" : elementId),
                List.of(copyPoints(points)),
                completedAtMs
        );
    }

    private SmartInkRequest(
            List<String> elementIds,
            List<List<Float>> strokes,
            long completedAtMs
    ) {
        List<String> safeIds = new ArrayList<>();
        List<List<Float>> safeStrokes = new ArrayList<>();
        List<Integer> safeFingerprints = new ArrayList<>();
        int count = Math.min(elementIds == null ? 0 : elementIds.size(),
                strokes == null ? 0 : strokes.size());
        for (int index = 0; index < count; index++) {
            String id = elementIds.get(index);
            List<Float> stroke = copyPoints(strokes.get(index));
            if (id == null || id.isBlank() || stroke.size() < 4) {
                continue;
            }
            safeIds.add(id);
            safeStrokes.add(Collections.unmodifiableList(stroke));
            safeFingerprints.add(fingerprint(stroke));
        }
        if (safeIds.isEmpty()) {
            safeIds.add("");
            safeStrokes.add(List.of());
            safeFingerprints.add(1);
        }
        this.elementIds = Collections.unmodifiableList(safeIds);
        this.strokes = Collections.unmodifiableList(safeStrokes);
        this.fingerprints = Collections.unmodifiableList(safeFingerprints);
        this.elementId = this.elementIds.get(0);
        this.points = this.strokes.get(0);
        this.fingerprint = this.fingerprints.get(0);
        this.bounds = bounds(this.strokes);
        this.completedAtMs = completedAtMs;
    }

    public static SmartInkRequest merge(List<SmartInkRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return new SmartInkRequest("", List.of(), System.currentTimeMillis());
        }
        List<String> ids = new ArrayList<>();
        List<List<Float>> strokes = new ArrayList<>();
        long completed = 0L;
        for (SmartInkRequest request : requests) {
            if (request == null) {
                continue;
            }
            ids.addAll(request.elementIds);
            strokes.addAll(request.strokes);
            completed = Math.max(completed, request.completedAtMs);
        }
        return new SmartInkRequest(ids, strokes, completed);
    }

    public int strokeCount() {
        return strokes.size();
    }

    public static int fingerprint(List<Float> points) {
        int hash = 1;
        if (points == null) {
            return hash;
        }
        for (Float point : points) {
            hash = 31 * hash + Float.floatToIntBits(point == null ? 0f : point);
        }
        return hash;
    }

    private static List<Float> copyPoints(List<Float> points) {
        List<Float> copy = points == null ? new ArrayList<>() : new ArrayList<>(points);
        if (copy.size() % 2 != 0) {
            copy.remove(copy.size() - 1);
        }
        for (int index = copy.size() - 2; index >= 0; index -= 2) {
            Float x = copy.get(index);
            Float y = copy.get(index + 1);
            if (x == null || y == null || !Float.isFinite(x) || !Float.isFinite(y)) {
                copy.remove(index + 1);
                copy.remove(index);
            }
        }
        return copy;
    }

    private static Bounds bounds(List<List<Float>> strokes) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        boolean found = false;
        for (List<Float> points : strokes) {
            for (int index = 0; index + 1 < points.size(); index += 2) {
                float x = points.get(index);
                float y = points.get(index + 1);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                found = true;
            }
        }
        return found
                ? new Bounds(minX, minY, maxX, maxY)
                : new Bounds(0f, 0f, 0f, 0f);
    }
}
