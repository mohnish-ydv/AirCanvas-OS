package com.mohnish.aircanvas.export;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ExportManagerTest {
    @Test
    public void safeBaseNameRemovesUnsafePathCharacters() {
        assertEquals(
                "My-AirCanvas-Design",
                ExportManager.safeBaseName("  My / AirCanvas : Design  ")
        );
        assertEquals("AirCanvas-Design", ExportManager.safeBaseName("///"));
    }
}
