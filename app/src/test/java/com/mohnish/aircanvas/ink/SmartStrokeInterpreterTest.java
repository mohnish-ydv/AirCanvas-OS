package com.mohnish.aircanvas.ink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class SmartStrokeInterpreterTest {
    @Test
    public void roughSquareBecomesARegularRectangle() {
        SmartStrokeInterpreter.Result result = SmartStrokeInterpreter.interpret(points(
                10, 15, 35, 10, 72, 13, 105, 18,
                108, 48, 103, 88, 96, 110,
                62, 106, 30, 110, 8, 101,
                11, 70, 7, 35, 10, 15
        ));

        assertEquals(SmartStrokeInterpreter.Kind.RECTANGLE, result.kind());
        assertTrue(Math.abs(result.bounds().width() - result.bounds().height()) < 12f);
    }

    @Test
    public void openLetterAIsReservedForHandwritingRecognition() {
        SmartStrokeInterpreter.Result result = SmartStrokeInterpreter.interpret(points(
                10, 110, 25, 75, 45, 32, 60, 10,
                74, 40, 90, 78, 108, 112,
                91, 78, 75, 48, 38, 52, 83, 52
        ));

        assertEquals(SmartStrokeInterpreter.Kind.NONE, result.kind());
    }

    @Test
    public void straightRoughStrokeBecomesALine() {
        SmartStrokeInterpreter.Result result = SmartStrokeInterpreter.interpret(points(
                10, 30, 30, 31, 55, 29, 80, 30, 110, 31
        ));

        assertEquals(SmartStrokeInterpreter.Kind.LINE, result.kind());
    }

    @Test
    public void smartInkRequestOwnsStablePointsBoundsAndFingerprint() {
        List<Float> source = points(10, 20, 40, 60, 80, 35);
        SmartInkRequest request = new SmartInkRequest("stroke", source, 100L);
        source.set(0, 999f);

        assertEquals(10f, request.points.get(0), 0.001f);
        assertEquals(10f, request.bounds.left, 0.001f);
        assertEquals(80f, request.bounds.right, 0.001f);
        assertEquals(request.fingerprint, SmartInkRequest.fingerprint(request.points));
    }

    private static List<Float> points(float... values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }
}
