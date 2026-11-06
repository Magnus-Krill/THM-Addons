/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolLatencyTest {
    @Test void weakerRecentlyHeldToolsExpireFromTheLatencyWindow() {
        var history = new ToolLatency();
        history.record(100, 0, 300);
        history.record(200, 5, 300);
        assertEquals(0, history.minimum(250, 300, 5));
        assertEquals(5, history.minimum(500, 300, 5));
        assertEquals(3, history.minimum(500, 300, 3));
        history.clear();
        assertEquals(5, history.minimum(500, 300, 5));
    }

    @Test void longSessionsAndClockRollbackDoNotRetainStaleEfficiency() {
        var history = new ToolLatency();
        for (int i = 0; i < 10_000; i++) history.record(i, i == 0 ? 0 : 5, 100);
        assertEquals(5, history.minimum(10_000, 100, 5));
        assertEquals(5, history.minimum(-1, 100, 5));
    }
}
