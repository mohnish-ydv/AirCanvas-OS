package com.mohnish.aircanvas.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AdaptiveFrameGovernorTest {
    @Test
    public void slowInferenceRaisesCadenceWithoutBuildingAQueue() {
        AdaptiveFrameGovernor governor = new AdaptiveFrameGovernor(42L);
        for (int index = 0; index < 8; index++) {
            governor.recordInference(96L);
        }
        assertTrue(governor.intervalMs() >= 96L);
        assertTrue(governor.intervalMs() <= 140L);
    }

    @Test
    public void cadenceRelaxesBackTowardTheSelectedProfile() {
        AdaptiveFrameGovernor governor = new AdaptiveFrameGovernor(66L);
        governor.recordInference(130L);
        long overloaded = governor.intervalMs();
        for (int index = 0; index < 40; index++) {
            governor.recordInference(30L);
        }
        assertTrue(governor.intervalMs() < overloaded);
        assertEquals(66L, governor.intervalMs());
    }
}
