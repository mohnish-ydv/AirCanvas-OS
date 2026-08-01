package com.mohnish.aircanvas.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Small software 3D pipeline used by the interactive canvas and exporters.
 *
 * <p>Vertices are transformed in real three-dimensional space, perspective
 * projected, then painter-sorted. It is intentionally CPU-light: primitive
 * topology is static and CanvasElement caches the final projection until its
 * geometry or orientation changes.</p>
 */
public final class SpatialMesh {
    public record Face(float[] points, float depth, float light) {
    }

    public static final class Projection {
        private final List<Face> faces;
        private final Bounds bounds;

        Projection(List<Face> faces, Bounds bounds) {
            this.faces = List.copyOf(faces);
            this.bounds = bounds;
        }

        public List<Face> faces() {
            return faces;
        }

        public Bounds bounds() {
            return bounds;
        }

        public boolean hitTest(float x, float y, float tolerance) {
            for (int faceIndex = faces.size() - 1; faceIndex >= 0; faceIndex--) {
                float[] polygon = faces.get(faceIndex).points;
                if (pointInPolygon(x, y, polygon)) {
                    return true;
                }
                for (int index = 0; index + 1 < polygon.length; index += 2) {
                    int next = (index + 2) % polygon.length;
                    if (distanceToSegment(
                            x,
                            y,
                            polygon[index],
                            polygon[index + 1],
                            polygon[next],
                            polygon[next + 1]
                    ) <= tolerance) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private record Mesh(float[][] vertices, int[][] faces) {
    }

    private static final Mesh CUBE = new Mesh(
            new float[][]{
                    {-0.5f, -0.5f, -0.5f},
                    {0.5f, -0.5f, -0.5f},
                    {0.5f, 0.5f, -0.5f},
                    {-0.5f, 0.5f, -0.5f},
                    {-0.5f, -0.5f, 0.5f},
                    {0.5f, -0.5f, 0.5f},
                    {0.5f, 0.5f, 0.5f},
                    {-0.5f, 0.5f, 0.5f}
            },
            new int[][]{
                    {1, 0, 3, 2},
                    {4, 5, 6, 7},
                    {0, 4, 7, 3},
                    {5, 1, 2, 6},
                    {0, 1, 5, 4},
                    {7, 6, 2, 3}
            }
    );

    private static final Mesh PYRAMID = new Mesh(
            new float[][]{
                    {-0.5f, 0.5f, -0.5f},
                    {0.5f, 0.5f, -0.5f},
                    {0.5f, 0.5f, 0.5f},
                    {-0.5f, 0.5f, 0.5f},
                    {0f, -0.5f, 0f}
            },
            new int[][]{
                    {0, 1, 2, 3},
                    {0, 4, 1},
                    {1, 4, 2},
                    {2, 4, 3},
                    {3, 4, 0}
            }
    );

    private static final Mesh CYLINDER = radialMesh(16, false);
    private static final Mesh CONE = radialMesh(16, true);
    private static final Mesh SPHERE = sphereMesh(12, 7);

    private SpatialMesh() {
    }

    public static boolean supports(CanvasElement.Type type) {
        return type == CanvasElement.Type.CUBE
                || type == CanvasElement.Type.SPHERE
                || type == CanvasElement.Type.CYLINDER
                || type == CanvasElement.Type.PYRAMID
                || type == CanvasElement.Type.CONE;
    }

    public static Projection project(CanvasElement element) {
        Mesh mesh = meshFor(element.type);
        Bounds bounds = element.bounds();
        float width = Math.max(2f, bounds.width());
        float height = Math.max(2f, bounds.height());
        float depth = Math.max(2f, element.depth);
        float[][] transformed = new float[mesh.vertices.length][3];
        float[][] projected = new float[mesh.vertices.length][2];
        float cameraDistance = Math.max(
                760f,
                Math.max(width, Math.max(height, depth)) * 4.8f
        );
        for (int index = 0; index < mesh.vertices.length; index++) {
            float[] vertex = mesh.vertices[index];
            float[] rotated = rotate(
                    vertex[0] * width,
                    vertex[1] * height,
                    vertex[2] * depth,
                    element.rotationX,
                    element.rotationY,
                    element.rotationZ
            );
            transformed[index] = rotated;
            float perspective = clamp(
                    cameraDistance / Math.max(cameraDistance * 0.25f, cameraDistance - rotated[2]),
                    0.35f,
                    2.8f
            );
            projected[index][0] = bounds.centerX() + rotated[0] * perspective;
            projected[index][1] = bounds.centerY() + rotated[1] * perspective;
        }

        List<Face> result = new ArrayList<>(mesh.faces.length);
        for (int[] face : mesh.faces) {
            float[] points = new float[face.length * 2];
            float averageDepth = 0f;
            for (int index = 0; index < face.length; index++) {
                int vertexIndex = face[index];
                points[index * 2] = projected[vertexIndex][0];
                points[index * 2 + 1] = projected[vertexIndex][1];
                averageDepth += transformed[vertexIndex][2];
            }
            averageDepth /= face.length;
            float light = faceLight(transformed, face);
            result.add(new Face(points, averageDepth, light));
        }
        result.sort(Comparator.comparingDouble(Face::depth));
        return new Projection(result, projectedBounds(projected));
    }

    public static void projectPlanarPoint(
            Bounds source,
            float rotationX,
            float rotationY,
            float rotationZ,
            float x,
            float y,
            float[] destination,
            int offset
    ) {
        float localX = x - source.centerX();
        float localY = y - source.centerY();
        float[] rotated = rotate(localX, localY, 0f, rotationX, rotationY, rotationZ);
        float cameraDistance = Math.max(
                860f,
                Math.max(source.width(), source.height()) * 4.8f
        );
        float perspective = clamp(
                cameraDistance / Math.max(cameraDistance * 0.28f, cameraDistance - rotated[2]),
                0.38f,
                2.6f
        );
        destination[offset] = source.centerX() + rotated[0] * perspective;
        destination[offset + 1] = source.centerY() + rotated[1] * perspective;
    }

    private static Mesh meshFor(CanvasElement.Type type) {
        return switch (type) {
            case CUBE -> CUBE;
            case SPHERE -> SPHERE;
            case CYLINDER -> CYLINDER;
            case PYRAMID -> PYRAMID;
            case CONE -> CONE;
            default -> throw new IllegalArgumentException("Not a 3D primitive: " + type);
        };
    }

    private static float[] rotate(
            float x,
            float y,
            float z,
            float degreesX,
            float degreesY,
            float degreesZ
    ) {
        double xRadians = Math.toRadians(degreesX);
        double yRadians = Math.toRadians(degreesY);
        double zRadians = Math.toRadians(degreesZ);

        float y1 = (float) (y * Math.cos(xRadians) - z * Math.sin(xRadians));
        float z1 = (float) (y * Math.sin(xRadians) + z * Math.cos(xRadians));
        float x2 = (float) (x * Math.cos(yRadians) + z1 * Math.sin(yRadians));
        float z2 = (float) (-x * Math.sin(yRadians) + z1 * Math.cos(yRadians));
        float x3 = (float) (x2 * Math.cos(zRadians) - y1 * Math.sin(zRadians));
        float y3 = (float) (x2 * Math.sin(zRadians) + y1 * Math.cos(zRadians));
        return new float[]{x3, y3, z2};
    }

    private static float faceLight(float[][] vertices, int[] face) {
        if (face.length < 3) {
            return 0.65f;
        }
        float[] a = vertices[face[0]];
        float[] b = vertices[face[1]];
        float[] c = vertices[face[2]];
        float abX = b[0] - a[0];
        float abY = b[1] - a[1];
        float abZ = b[2] - a[2];
        float acX = c[0] - a[0];
        float acY = c[1] - a[1];
        float acZ = c[2] - a[2];
        float normalX = abY * acZ - abZ * acY;
        float normalY = abZ * acX - abX * acZ;
        float normalZ = abX * acY - abY * acX;
        float length = (float) Math.sqrt(
                normalX * normalX + normalY * normalY + normalZ * normalZ
        );
        if (length < 0.001f) {
            return 0.5f;
        }
        normalX /= length;
        normalY /= length;
        normalZ /= length;
        float dot = Math.abs(normalX * -0.35f + normalY * -0.45f + normalZ * 0.82f);
        return clamp(0.30f + dot * 0.70f, 0.30f, 1f);
    }

    private static Bounds projectedBounds(float[][] points) {
        float minX = points[0][0];
        float minY = points[0][1];
        float maxX = minX;
        float maxY = minY;
        for (float[] point : points) {
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static Mesh radialMesh(int segments, boolean cone) {
        int ringVertices = cone ? segments : segments * 2;
        int extra = cone ? 2 : 2;
        float[][] vertices = new float[ringVertices + extra][3];
        for (int index = 0; index < segments; index++) {
            double angle = index * Math.PI * 2d / segments;
            float x = (float) Math.cos(angle) * 0.5f;
            float z = (float) Math.sin(angle) * 0.5f;
            if (cone) {
                vertices[index] = new float[]{x, 0.5f, z};
            } else {
                vertices[index] = new float[]{x, -0.5f, z};
                vertices[index + segments] = new float[]{x, 0.5f, z};
            }
        }
        List<int[]> faces = new ArrayList<>();
        if (cone) {
            int apex = segments;
            int center = segments + 1;
            vertices[apex] = new float[]{0f, -0.5f, 0f};
            vertices[center] = new float[]{0f, 0.5f, 0f};
            for (int index = 0; index < segments; index++) {
                int next = (index + 1) % segments;
                faces.add(new int[]{index, next, apex});
                faces.add(new int[]{center, next, index});
            }
        } else {
            int topCenter = segments * 2;
            int bottomCenter = topCenter + 1;
            vertices[topCenter] = new float[]{0f, -0.5f, 0f};
            vertices[bottomCenter] = new float[]{0f, 0.5f, 0f};
            for (int index = 0; index < segments; index++) {
                int next = (index + 1) % segments;
                faces.add(new int[]{index, next, next + segments, index + segments});
                faces.add(new int[]{topCenter, next, index});
                faces.add(new int[]{bottomCenter, index + segments, next + segments});
            }
        }
        return new Mesh(vertices, faces.toArray(new int[0][]));
    }

    private static Mesh sphereMesh(int longitudeCount, int latitudeBands) {
        List<float[]> vertices = new ArrayList<>();
        vertices.add(new float[]{0f, -0.5f, 0f});
        for (int latitude = 1; latitude < latitudeBands; latitude++) {
            double phi = -Math.PI * 0.5d + latitude * Math.PI / latitudeBands;
            float y = (float) Math.sin(phi) * 0.5f;
            float radius = (float) Math.cos(phi) * 0.5f;
            for (int longitude = 0; longitude < longitudeCount; longitude++) {
                double theta = longitude * Math.PI * 2d / longitudeCount;
                vertices.add(new float[]{
                        (float) Math.cos(theta) * radius,
                        y,
                        (float) Math.sin(theta) * radius
                });
            }
        }
        int bottom = vertices.size();
        vertices.add(new float[]{0f, 0.5f, 0f});

        List<int[]> faces = new ArrayList<>();
        for (int longitude = 0; longitude < longitudeCount; longitude++) {
            int next = (longitude + 1) % longitudeCount;
            faces.add(new int[]{0, 1 + longitude, 1 + next});
        }
        int ringCount = latitudeBands - 1;
        for (int ring = 0; ring < ringCount - 1; ring++) {
            int firstRing = 1 + ring * longitudeCount;
            int secondRing = firstRing + longitudeCount;
            for (int longitude = 0; longitude < longitudeCount; longitude++) {
                int next = (longitude + 1) % longitudeCount;
                faces.add(new int[]{
                        firstRing + longitude,
                        secondRing + longitude,
                        secondRing + next,
                        firstRing + next
                });
            }
        }
        int lastRing = 1 + (ringCount - 1) * longitudeCount;
        for (int longitude = 0; longitude < longitudeCount; longitude++) {
            int next = (longitude + 1) % longitudeCount;
            faces.add(new int[]{bottom, lastRing + next, lastRing + longitude});
        }
        return new Mesh(
                vertices.toArray(new float[0][]),
                faces.toArray(new int[0][])
        );
    }

    private static boolean pointInPolygon(float x, float y, float[] polygon) {
        boolean inside = false;
        int count = polygon.length / 2;
        for (int current = 0, previous = count - 1; current < count; previous = current++) {
            float currentX = polygon[current * 2];
            float currentY = polygon[current * 2 + 1];
            float previousX = polygon[previous * 2];
            float previousY = polygon[previous * 2 + 1];
            boolean crosses = (currentY > y) != (previousY > y)
                    && x < (previousX - currentX) * (y - currentY)
                    / (previousY - currentY) + currentX;
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
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
        if (Math.abs(dx) < 0.00001f && Math.abs(dy) < 0.00001f) {
            return (float) Math.hypot(px - ax, py - ay);
        }
        float t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = clamp(t, 0f, 1f);
        return (float) Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
