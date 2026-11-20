/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.modules;

import org.junit.jupiter.api.Test;
import xyz.thm.addon.modules.HighwayTraveler.GapWait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighwayTravelerTest {
    @Test
    void paveEndRequiresAllThreeLanesToBeLoadedAndUnpaved() {
        assertFalse(HighwayTraveler.isLoadedUnpavedRow(15, 16, 1, -1,
            (chunkX, chunkZ) -> chunkX == 0, (x, z) -> false));
        assertFalse(HighwayTraveler.isLoadedUnpavedRow(15, 16, 1, -1,
            (chunkX, chunkZ) -> true, (x, z) -> x == 16 && z == 15));
        assertTrue(HighwayTraveler.isLoadedUnpavedRow(15, 16, 1, -1,
            (chunkX, chunkZ) -> true, (x, z) -> false));
    }

    @Test
    void brokenHighwayGoesToTheBuilderRightAway() {
        assertEquals(GapWait.HANDOFF, HighwayTraveler.gapWaitDecision(true, true, 0));
        assertEquals(GapWait.HANDOFF, HighwayTraveler.gapWaitDecision(true, false, 0));
    }

    @Test
    void knownEndStopsWithoutWaiting() {
        assertEquals(GapWait.STOP, HighwayTraveler.gapWaitDecision(false, false, 0));
    }

    @Test
    void gapIntoUnloadedChunksWaitsButNotForever() {
        int ticks = 0;
        while (HighwayTraveler.gapWaitDecision(false, true, ticks) == GapWait.WAIT) {
            ticks++;
            assertTrue(ticks <= 20 * 60, "gap wait never ends");
        }
        assertTrue(ticks > 25, "wait must outlast the stuck detector it replaces");
        assertEquals(GapWait.STOP, HighwayTraveler.gapWaitDecision(false, true, ticks));
    }

    @Test
    void holdingNeverSinksBelowTheLane() {
        assertEquals(0.3, HighwayTraveler.holdFall(0.5, 120.3, 120), 1e-9);
        assertEquals(0.0, HighwayTraveler.holdFall(0.01, 120.0, 120), 1e-9);
        assertEquals(0.01, HighwayTraveler.holdFall(0.01, 121.5, 120), 1e-9);
    }

    @Test
    void holdingDescendsOntoALowerLaneFloor() {
        assertEquals(0.5, HighwayTraveler.holdFall(0.5, 120.0, 119), 1e-9);
    }

    @Test
    void holdingBelowTheLaneOrWithoutOneKeepsTheFallSpeed() {
        assertEquals(0.5, HighwayTraveler.holdFall(0.5, 118.0, 120), 1e-9);
        assertEquals(0.5, HighwayTraveler.holdFall(0.5, 140.0, Integer.MIN_VALUE), 1e-9);
    }
}
