/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TickPacerTest {
    @Test void catchUpNeverRunsMoreThanThreeTicksWithinTheWindow() {
        var pacer = new TickPacer();
        assertEquals(3, pacer.allow(10, 0));
        pacer.record(0); pacer.record(1); pacer.record(2);
        assertEquals(0, pacer.allow(10, 50_000_000));
        assertEquals(1, pacer.allow(10, 60_000_000));
        assertEquals(3, pacer.allow(10, 60_000_002));
    }

    @Test void normalCadenceAndSessionResetDoNotLoseTicks() {
        var pacer = new TickPacer();
        for (int i = 0; i < 100; i++) {
            long now = i * 50_000_000L;
            assertEquals(1, pacer.allow(1, now));
            pacer.record(now);
        }
        pacer.clear();
        assertEquals(3, pacer.allow(10, 5_000_000_000L));
        assertEquals(0, pacer.allow(-1, 5_000_000_000L));
    }
}
