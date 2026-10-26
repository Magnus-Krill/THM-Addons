/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PacketPlaceTrackerTest {
    @Test
    void unansweredLoadedPlacementsStayBlockedAndProbeInsteadOfResending() {
        var tracker = new PacketPlaceTracker();
        BlockPos pos = new BlockPos(5, 64, -2);
        tracker.markSent(pos.asLong(), 100, 20);
        assertFalse(tracker.canSend(pos.asLong()));
        assertTrue(tracker.tick(119, key -> true, key -> true, true).isEmpty());
        assertEquals(List.of(pos), tracker.tick(120, key -> true, key -> true, true));
        assertTrue(tracker.tick(121, key -> true, key -> true, true).isEmpty());
        assertEquals(List.of(pos), tracker.tick(100_000, key -> true, key -> true, true));
        assertFalse(tracker.canSend(pos.asLong()), "Silence must never expire a nearby placement.");
    }

    @Test
    void unloadingReleasesPendingPlacementWithoutReadingOrProbingTheChunk() {
        var tracker = new PacketPlaceTracker();
        long pos = new BlockPos(5, 64, -2).asLong();
        tracker.markSent(pos, 100, 20);
        assertTrue(tracker.tick(101, key -> false, key -> {
            fail("Unloaded chunks must not be queried");
            return true;
        }, true).isEmpty());
        assertTrue(tracker.canSend(pos));
        tracker.markSent(pos, 200, 40);
        assertTrue(tracker.tick(220, key -> true, key -> true, true).isEmpty());
        assertEquals(1, tracker.tick(240, key -> true, key -> true, true).size());
    }

    @Test
    void solidConfirmationAndMissingInertHandRetainTheirCleanupBehavior() {
        var tracker = new PacketPlaceTracker();
        long pos = BlockPos.ZERO.asLong();
        tracker.markSent(pos, 100, 20);
        assertTrue(tracker.tick(101, key -> true, key -> false, true).isEmpty());
        assertTrue(tracker.canSend(pos));
        tracker.markSent(pos, 200, 20);
        assertTrue(tracker.tick(219, key -> true, key -> true, false).isEmpty());
        assertFalse(tracker.canSend(pos));
        assertTrue(tracker.tick(220, key -> true, key -> true, false).isEmpty());
        assertTrue(tracker.canSend(pos));
    }

    @Test
    void travellingDoesNotRetainPreviouslyUnloadedPositions() {
        var tracker = new PacketPlaceTracker();
        for (int step = 0; step < 2_000; step++) {
            int currentX = step * 16;
            tracker.markSent(new BlockPos(currentX, 64, 0).asLong(), step, 20);
            tracker.tick(step, key -> Math.abs(BlockPos.of(key).getX() - currentX) <= 32, key -> true, true);
        }
        for (int step = 0; step < 1_997; step++) {
            assertTrue(tracker.canSend(new BlockPos(step * 16, 64, 0).asLong()));
        }
        assertFalse(tracker.canSend(new BlockPos(1_999 * 16, 64, 0).asLong()));
    }
}
