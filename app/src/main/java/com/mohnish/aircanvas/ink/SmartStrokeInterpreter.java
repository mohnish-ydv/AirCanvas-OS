package com.mohnish.aircanvas.ink;

import com.mohnish.aircanvas.model.Bounds;
import com.mohnish.aircanvas.model.CanvasElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fast, deterministic first-pass classifier for rough geometric ink.
 *
 * <p>It deliberately returns NONE for open, letter-like strokes. Those strokes
 * are handled by the handwriting recognizer instead of being forced into a
 * shape. This keeps an "A" or a connected word from becoming a triangle.</p>
 */
public final class SmartStrokeInterpreter {
    public enum Kind {
        NONE(null),
        LINE(CanvasElement.Type.LINE),
        RECTANGLE(CanvasElement.Type.RECTANGLE),
        ELLIPSE(CanvasElement.Type.ELLIPSE),
        DIAMOND(CanvasElement.Type.DIAMOND),
        TRIANGLE(CanvasElement.Type.TRIANGLE),
        HEXAGON(CanvasElement.Type.HEXAGON),
        STAR(CanvasElement.Type.STAR);

        public final CanvasElement.Type elementType;

        Kind(CanvasElement.Type elementType) {
            this.elementType = elementType;
        }
    }

    public record Result(Kind kind, float confidence, Bounds bounds) {
        public static Result none(Bounds bounds) {
            return new Result(Kind.NONE, 0f, bounds);
        }

        public boolean recognized() {
            return kind != Kind.NONE;
        }
    }

    private static final int SAMPLE_COUNT = 40;

    private SmartStrokeInterpreter() {
    }

    public static Result interpret(List<Float> rawPoints) {
        List<Point> points = clean(rawPoints);
        Bounds bounds = bounds(points);
        if (points.size() < 4) {
            return Result.none(bounds);
        }
        float diagonal = (float) Math.hypot(bounds.width(), bounds.height());
        float pathLength = pathLength(points);
        if (diagonal < 18f || pathLength < 24f) {
            return Result.none(bounds);
        }

        float endpointDistance = distance(points.get(0), points.get(points.size() - 1));
        float straightness = endpointDistance / Math.max(1f, pathLength);
        if (straightness >= 0.925f) {
            return new Result(
                    Kind.LINE,
                    clamp((straightness - 0.90f) / 0.10f, 0.72f, 0.99f),
                    bounds
            );
        }

        float closure = endpointDistance / Math.max(1f, diagonal);
        if (closure > 0.24f || pathLength < diagonal * 1.75f) {
            return Result.none(bounds);
        }

        List<Point> loop = new ArrayList<>(points);
        if (endpointDistance > 0.001f) {
            loop.add(points.get(0));
        }
        List<Point> samples = resample(loop, SAMPLE_COUNT, true);
        if (samples.size() < 12) {
            return Result.none(bounds);
        }

        List<Corner> corners = corners(samples, diagonal);
        float radialVariation = radialVariation(samples);
        float edgeCoverage = edgeCoverage(samples, bounds);
        float aspect = bounds.width() / Math.max(1f, bounds.height());
        int cornerCount = corners.size();

        if (cornerCount >= 3 && cornerCount <= 4 && edgeCoverage >= 0.60f) {
            if (cornerCount == 3) {
                return new Result(Kind.TRIANGLE, 0.88f, bounds);
            }
            boolean rectangle = rectangleCornerScore(corners, bounds) >= 3;
            return new Result(
                    rectangle ? Kind.RECTANGLE : Kind.DIAMOND,
                    rectangle ? 0.92f : 0.86f,
                    squareBoundsIfClose(bounds, rectangle)
            );
        }
        if (cornerCount >= 8 && cornerCount <= 12 && alternatingRadius(samples)) {
            return new Result(Kind.STAR, 0.84f, squareBoundsIfClose(bounds, true));
        }
        if (cornerCount >= 5 && cornerCount <= 7 && edgeCoverage >= 0.52f) {
            return new Result(Kind.HEXAGON, 0.80f, bounds);
        }
        if (aspect >= 0.42f
                && aspect <= 2.40f
                && radialVariation <= 0.26f
                && edgeCoverage < 0.78f) {
            float confidence = clamp(0.96f - radialVariation, 0.74f, 0.94f);
            return new Result(Kind.ELLIPSE, confidence, bounds);
        }
        if (cornerCount == 4) {
            boolean rectangle = rectangleCornerScore(corners, bounds) >= 3;
            return new Result(
                    rectangle ? Kind.RECTANGLE : Kind.DIAMOND,
                    0.76f,
                    squareBoundsIfClose(bounds, rectangle)
            );
        }
        return Result.none(bounds);
    }

    private static List<Point> clean(List<Float> raw) {
        List<Point> points = new ArrayList<>();
        if (raw == null) {
            return points;
        }
        Point last = null;
        for (int index = 0; index + 1 < raw.size(); index += 2) {
            Float rawX = raw.get(index);
            Float rawY = raw.get(index + 1);
            if (rawX == null || rawY == null
                    || !Float.isFinite(rawX) || !Float.isFinite(rawY)) {
                continue;
            }
            Point next = new Point(rawX, rawY);
            if (last == null || distance(last, next) >= 1.5f) {
                points.add(next);
                last = next;
            }
        }
        return points;
    }

    private static Bounds bounds(List<Point> points) {
        if (points.isEmpty()) {
            return new Bounds(0f, 0f, 0f, 0f);
        }
        float minX = points.get(0).x;
        float maxX = minX;
        float minY = points.get(0).y;
        float maxY = minY;
        for (Point point : points) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static float pathLength(List<Point> points) {
        float length = 0f;
        for (int index = 1; index < points.size(); index++) {
            length += distance(points.get(index - 1), points.get(index));
        }
        return length;
    }

    private static List<Point> resample(
            List<Point> source,
            int count,
            boolean closed
    ) {
        List<Point> result = new ArrayList<>(count);
        float total = pathLength(source);
        if (total < 0.001f) {
            return result;
        }
        float step = total / (closed ? count : Math.max(1, count - 1));
        float target = 0f;
        float traversed = 0f;
        int segment = 1;
        Point start = source.get(0);
        Point end = source.get(1);
        float segmentLength = distance(start, end);
        for (int sample = 0; sample < count; sample++) {
            while (segment < source.size() - 1
                    && traversed + segmentLength < target) {
                traversed += segmentLength;
                segment++;
                start = source.get(segment - 1);
                end = source.get(segment);
                segmentLength = distance(start, end);
            }
            float t = segmentLength < 0.001f
                    ? 0f
                    : clamp((target - traversed) / segmentLength, 0f, 1f);
            result.add(new Point(
                    start.x + (end.x - start.x) * t,
                    start.y + (end.y - start.y) * t
            ));
            target += step;
        }
        return result;
    }

    private static List<Corner> corners(List<Point> samples, float diagonal) {
        List<Corner> candidates = new ArrayList<>();
        int size = samples.size();
        int window = 2;
        for (int index = 0; index < size; index++) {
            Point before = samples.get((index - window + size) % size);
            Point current = samples.get(index);
            Point after = samples.get((index + window) % size);
            float turn = turn(before, current, after);
            if (turn >= 0.46f) {
                candidates.add(new Corner(index, current, turn));
            }
        }
        candidates.sort(Comparator.comparingDouble(Corner::strength).reversed());
        List<Corner> kept = new ArrayList<>();
        int separation = 4;
        for (Corner candidate : candidates) {
            boolean near = false;
            for (Corner existing : kept) {
                int delta = Math.abs(candidate.index - existing.index);
                delta = Math.min(delta, size - delta);
                if (delta < separation
                        || distance(candidate.point, existing.point) < diagonal * 0.12f) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                kept.add(candidate);
            }
        }
        kept.sort(Comparator.comparingInt(Corner::index));
        return kept;
    }

    private static float turn(Point before, Point current, Point after) {
        float ax = current.x - before.x;
        float ay = current.y - before.y;
        float bx = after.x - current.x;
        float by = after.y - current.y;
        float denominator = (float) Math.sqrt(
                (ax * ax + ay * ay) * (bx * bx + by * by)
        );
        if (denominator < 0.001f) {
            return 0f;
        }
        float cosine = clamp((ax * bx + ay * by) / denominator, -1f, 1f);
        return (float) Math.acos(cosine);
    }

    private static float radialVariation(List<Point> samples) {
        float centerX = 0f;
        float centerY = 0f;
        for (Point point : samples) {
            centerX += point.x;
            centerY += point.y;
        }
        centerX /= samples.size();
        centerY /= samples.size();
        float mean = 0f;
        for (Point point : samples) {
            mean += (float) Math.hypot(point.x - centerX, point.y - centerY);
        }
        mean /= samples.size();
        if (mean < 0.001f) {
            return 1f;
        }
        float variance = 0f;
        for (Point point : samples) {
            float radius = (float) Math.hypot(point.x - centerX, point.y - centerY);
            float delta = radius - mean;
            variance += delta * delta;
        }
        return (float) Math.sqrt(variance / samples.size()) / mean;
    }

    private static float edgeCoverage(List<Point> samples, Bounds bounds) {
        float threshold = Math.max(4f, Math.min(bounds.width(), bounds.height()) * 0.14f);
        int onEdge = 0;
        for (Point point : samples) {
            float distance = Math.min(
                    Math.min(Math.abs(point.x - bounds.left), Math.abs(point.x - bounds.right)),
                    Math.min(Math.abs(point.y - bounds.top), Math.abs(point.y - bounds.bottom))
            );
            if (distance <= threshold) {
                onEdge++;
            }
        }
        return onEdge / (float) samples.size();
    }

    private static int rectangleCornerScore(List<Corner> corners, Bounds bounds) {
        float xTolerance = Math.max(4f, bounds.width() * 0.24f);
        float yTolerance = Math.max(4f, bounds.height() * 0.24f);
        int score = 0;
        for (Corner corner : corners) {
            Point point = corner.point;
            boolean nearX = Math.abs(point.x - bounds.left) <= xTolerance
                    || Math.abs(point.x - bounds.right) <= xTolerance;
            boolean nearY = Math.abs(point.y - bounds.top) <= yTolerance
                    || Math.abs(point.y - bounds.bottom) <= yTolerance;
            if (nearX && nearY) {
                score++;
            }
        }
        return score;
    }

    private static boolean alternatingRadius(List<Point> samples) {
        float centerX = 0f;
        float centerY = 0f;
        for (Point point : samples) {
            centerX += point.x;
            centerY += point.y;
        }
        centerX /= samples.size();
        centerY /= samples.size();
        List<Float> radii = new ArrayList<>();
        float mean = 0f;
        float minimum = Float.MAX_VALUE;
        float maximum = 0f;
        for (int index = 0; index < samples.size(); index += 2) {
            Point point = samples.get(index);
            float radius = (float) Math.hypot(point.x - centerX, point.y - centerY);
            radii.add(radius);
            mean += radius;
            minimum = Math.min(minimum, radius);
            maximum = Math.max(maximum, radius);
        }
        if (radii.size() < 8 || minimum < 0.001f || maximum / minimum < 1.28f) {
            return false;
        }
        mean /= radii.size();
        int switches = 0;
        boolean previousOuter = radii.get(0) >= mean;
        for (int index = 1; index < radii.size(); index++) {
            boolean outer = radii.get(index) >= mean;
            if (outer != previousOuter) {
                switches++;
            }
            previousOuter = outer;
        }
        if ((radii.get(radii.size() - 1) >= mean) != (radii.get(0) >= mean)) {
            switches++;
        }
        return switches >= 6;
    }

    private static Bounds squareBoundsIfClose(Bounds bounds, boolean enabled) {
        if (!enabled) {
            return bounds;
        }
        float aspect = bounds.width() / Math.max(1f, bounds.height());
        if (aspect < 0.72f || aspect > 1.38f) {
            return bounds;
        }
        float size = (bounds.width() + bounds.height()) * 0.5f;
        return new Bounds(
                bounds.centerX() - size * 0.5f,
                bounds.centerY() - size * 0.5f,
                bounds.centerX() + size * 0.5f,
                bounds.centerY() + size * 0.5f
        );
    }

    private static float distance(Point first, Point second) {
        return (float) Math.hypot(second.x - first.x, second.y - first.y);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record Point(float x, float y) {
    }

    private record Corner(int index, Point point, float strength) {
    }
}
