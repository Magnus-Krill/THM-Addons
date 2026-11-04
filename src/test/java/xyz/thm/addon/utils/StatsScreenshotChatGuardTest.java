/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatsScreenshotChatGuardTest {
    @Test
    void slowRenderingWaitsForTwoCompletedVisibleFrames() {
        int[] captures = {0}, aborts = {0};
        var pending = new StatsScreenshotChatGuard.PendingScreenshot(false, 5000, () -> true,
            () -> captures[0]++, () -> aborts[0]++);
        assertFalse(pending.afterRender(1000));
        assertEquals(0, captures[0]);
        assertTrue(pending.afterRender(2000));
        assertEquals(1, captures[0]);
        assertTrue(pending.afterRender(3000));
        assertEquals(1, captures[0]);
        assertEquals(0, aborts[0]);
    }

    @Test
    void coveredSurfaceRestartsTheFrameWait() {
        boolean[] visible = {true};
        int[] captures = {0};
        var pending = new StatsScreenshotChatGuard.PendingScreenshot(false, 5000, () -> visible[0],
            () -> captures[0]++, () -> fail("Should not time out"));
        assertFalse(pending.afterRender(100));
        visible[0] = false;
        assertFalse(pending.afterRender(200));
        visible[0] = true;
        assertFalse(pending.afterRender(300));
        assertEquals(0, captures[0]);
        assertTrue(pending.afterRender(400));
        assertEquals(1, captures[0]);
    }

    @Test
    void disconnectWaitsForTheStatsScreenInsteadOfCapturingThePreviousScreen() {
        boolean[] statsScreen = {false};
        int[] captures = {0};
        var pending = new StatsScreenshotChatGuard.PendingScreenshot(true, 30000, () -> statsScreen[0],
            () -> captures[0]++, () -> fail("Should not time out"));
        assertFalse(pending.afterRender(250));
        assertFalse(pending.afterRender(1000));
        assertFalse(pending.afterRender(8000));
        assertEquals(0, captures[0]);
        statsScreen[0] = true;
        assertFalse(pending.afterRender(8100));
        assertEquals(0, captures[0]);
        assertTrue(pending.afterRender(8200));
        assertEquals(1, captures[0]);
    }

    @Test
    void timeoutReleasesTheCaptureWithoutTakingAnEarlyScreenshot() {
        int[] captures = {0}, aborts = {0};
        var pending = new StatsScreenshotChatGuard.PendingScreenshot(false, 5000, () -> false,
            () -> captures[0]++, () -> aborts[0]++);
        assertFalse(pending.afterRender(4999));
        assertTrue(pending.expire(5000));
        assertTrue(pending.afterRender(6000));
        pending.cancel();
        assertEquals(0, captures[0]);
        assertEquals(1, aborts[0]);
    }

    @Test
    void cancellationNeverCapturesAFrameAfterDisconnect() {
        int[] captures = {0}, aborts = {0};
        var pending = new StatsScreenshotChatGuard.PendingScreenshot(false, 5000, () -> true,
            () -> captures[0]++, () -> aborts[0]++);
        assertFalse(pending.afterRender(100));
        pending.cancel();
        assertTrue(pending.afterRender(200));
        assertEquals(0, captures[0]);
        assertEquals(1, aborts[0]);
    }
}
