import com.mohnish.aircanvas.vision.FrameOrientation;

public final class CameraOrientationHarness {
    private static int passed;

    public static void main(String[] args) {
        dimensions();
        rotations();
        frontMirror();
        matrixMatchesNormalizedMapping();
        normalization();
        System.out.println("Camera orientation harness: " + passed + "/5 passed");
    }

    private static void dimensions() {
        FrameOrientation portrait = new FrameOrientation(640, 480, 90, false);
        require(portrait.outputWidth() == 480, "quarter-turn width");
        require(portrait.outputHeight() == 640, "quarter-turn height");
        passed++;
    }

    private static void rotations() {
        assertPoint(new FrameOrientation(640, 480, 90, false), 0f, 0f, 1f, 0f);
        assertPoint(new FrameOrientation(640, 480, 180, false), 0f, 0f, 1f, 1f);
        assertPoint(new FrameOrientation(640, 480, 270, false), 0f, 0f, 0f, 1f);
        passed++;
    }

    private static void frontMirror() {
        FrameOrientation front = new FrameOrientation(640, 480, 90, true);
        assertPoint(front, 0f, 0f, 0f, 0f);
        assertPoint(front, 1f, 1f, 1f, 1f);
        passed++;
    }


    private static void matrixMatchesNormalizedMapping() {
        for (int rotation : new int[]{0, 90, 180, 270}) {
            for (boolean mirror : new boolean[]{false, true}) {
                FrameOrientation orientation = new FrameOrientation(640, 480, rotation, mirror);
                for (float[] point : new float[][]{
                        {0f, 0f}, {1f, 0f}, {0f, 1f}, {1f, 1f}, {0.31f, 0.72f}
                }) {
                    float[] expected = orientation.mapNormalized(point[0], point[1]);
                    float[] matrix = orientation.matrixValues();
                    float pixelX = point[0] * orientation.sourceWidth;
                    float pixelY = point[1] * orientation.sourceHeight;
                    float mappedX = (matrix[0] * pixelX + matrix[1] * pixelY + matrix[2])
                            / orientation.outputWidth();
                    float mappedY = (matrix[3] * pixelX + matrix[4] * pixelY + matrix[5])
                            / orientation.outputHeight();
                    require(Math.abs(mappedX - expected[0]) < 0.0001f, "matrix x");
                    require(Math.abs(mappedY - expected[1]) < 0.0001f, "matrix y");
                }
            }
        }
        passed++;
    }

    private static void normalization() {
        require(FrameOrientation.normalizeRightAngle(-90) == 270, "negative rotation");
        require(FrameOrientation.normalizeRightAngle(360) == 0, "full rotation");
        require(FrameOrientation.normalizeRightAngle(91) == 90, "rotation snap");
        passed++;
    }

    private static void assertPoint(
            FrameOrientation orientation,
            float x,
            float y,
            float expectedX,
            float expectedY
    ) {
        float[] point = orientation.mapNormalized(x, y);
        require(Math.abs(point[0] - expectedX) < 0.0001f, "x mapping");
        require(Math.abs(point[1] - expectedY) < 0.0001f, "y mapping");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
