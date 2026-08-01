package com.mohnish.aircanvas.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PerformanceTelemetryTest {
    @Test
    public void smoothFramesProduceReadableHealthSnapshot() {
        PerformanceTelemetry telemetry = new PerformanceTelemetry();
        telemetry.record(1_000L, 35L, 1);
        PerformanceTelemetry.Snapshot snapshot = telemetry.record(1_042L, 38L, 1);
        assertTrue(snapshot.fps() > 20f);
        assertTrue(snapshot.averageInferenceMs() >= 35f);
        assertTrue(snapshot.compactLabel().contains("fps"));
        assertEquals(1, snapshot.handCount());
    }

    @Test
    public void resetClearsRollingState() {
        PerformanceTelemetry telemetry = new PerformanceTelemetry();
        telemetry.record(1_000L, 120L, 2);
        telemetry.record(1_100L, 120L, 2);
        telemetry.reset();
        assertEquals(0f, telemetry.snapshot().fps(), 0.001f);
        assertEquals(0f, telemetry.snapshot().averageInferenceMs(), 0.001f);
    }
}
