/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;
import xyz.thm.addon.utils.StashTracker.Counts;
import xyz.thm.addon.utils.StashTracker.Stash;

import static org.junit.jupiter.api.Assertions.*;

class StashTrackerTest {
    /** {@code doubles} double chests (two halves each) centred in the chunk. */
    private static Counts chests(int cx, int cz, int doubles) {
        int halves = doubles * 2;
        long x = cx * 16L + 8, z = cz * 16L + 8;
        return new Counts(halves, 0, 0, 0, 0, x * halves, 64L * halves, z * halves, halves);
    }

    @Test
    void reportsOnceThresholdIsReachedAcrossChunks() {
        StashTracker t = new StashTracker();
        assertNull(t.update(100, 100, chests(100, 100, 20), 2, 30, 256));
        Stash stash = t.update(101, 100, chests(101, 100, 15), 2, 30, 256);
        assertNotNull(stash);
        assertEquals(35, stash.doubleChests());
        assertEquals(64, stash.y());
    }

    @Test
    void doesNotReportTheSameStashTwice() {
        StashTracker t = new StashTracker();
        assertNotNull(t.update(0, 0, chests(0, 0, 40), 1, 30, 256));
        assertNull(t.update(1, 0, chests(1, 0, 5), 1, 30, 256));
    }

    @Test
    void farChunksAreNotAddedUp() {
        StashTracker t = new StashTracker();
        assertNull(t.update(0, 0, chests(0, 0, 20), 2, 30, 256));
        assertNull(t.update(10, 0, chests(10, 0, 20), 2, 30, 256));
    }

    @Test
    void emptyChunkClearsItsCounts() {
        StashTracker t = new StashTracker();
        t.update(0, 0, chests(0, 0, 20), 2, 30, 256);
        t.update(0, 0, Counts.EMPTY, 2, 30, 256);
        assertEquals(0, t.chunkCount());
    }

    @Test
    void pruneDropsOnlyFarChunks() {
        StashTracker t = new StashTracker();
        t.update(-3, 2, chests(-3, 2, 1), 0, 30, 256);
        t.update(-20, 0, chests(-20, 0, 1), 0, 30, 256);
        t.update(0, -9, chests(0, -9, 1), 0, 30, 256);
        t.pruneChunks(0, 0, 5);
        assertEquals(1, t.chunkCount());
    }

    @Test
    void spiralWalksOutwardCorners() {
        assertArrayEquals(new int[]{0, 0}, StashTracker.spiralWaypoint(0, 0, 100, 0));
        assertArrayEquals(new int[]{100, 0}, StashTracker.spiralWaypoint(0, 0, 100, 1));
        assertArrayEquals(new int[]{100, 100}, StashTracker.spiralWaypoint(0, 0, 100, 2));
        assertArrayEquals(new int[]{-100, 100}, StashTracker.spiralWaypoint(0, 0, 100, 3));
        assertArrayEquals(new int[]{-100, -100}, StashTracker.spiralWaypoint(0, 0, 100, 4));
        assertArrayEquals(new int[]{200, -100}, StashTracker.spiralWaypoint(0, 0, 100, 5));
    }
}
