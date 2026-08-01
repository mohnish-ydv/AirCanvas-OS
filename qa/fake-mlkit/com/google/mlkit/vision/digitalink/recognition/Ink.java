package com.google.mlkit.vision.digitalink.recognition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Ink {
    private final List<Stroke> strokes;

    private Ink(List<Stroke> strokes) {
        this.strokes = Collections.unmodifiableList(new ArrayList<>(strokes));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Stroke> getStrokes() {
        return strokes;
    }

    public static final class Builder {
        private final List<Stroke> strokes = new ArrayList<>();

        public Builder addStroke(Stroke stroke) {
            strokes.add(stroke);
            return this;
        }

        public Ink build() {
            return new Ink(strokes);
        }
    }

    public static final class Stroke {
        private final List<Point> points;

        private Stroke(List<Point> points) {
            this.points = Collections.unmodifiableList(new ArrayList<>(points));
        }

        public static Builder builder() {
            return new Builder();
        }

        public List<Point> getPoints() {
            return points;
        }

        public static final class Builder {
            private final List<Point> points = new ArrayList<>();

            public Builder addPoint(Point point) {
                points.add(point);
                return this;
            }

            public Stroke build() {
                return new Stroke(points);
            }
        }
    }

    public static final class Point {
        public final float x;
        public final float y;
        public final long timestamp;

        private Point(float x, float y, long timestamp) {
            this.x = x;
            this.y = y;
            this.timestamp = timestamp;
        }

        public static Point create(float x, float y, long timestamp) {
            return new Point(x, y, timestamp);
        }
    }
}
