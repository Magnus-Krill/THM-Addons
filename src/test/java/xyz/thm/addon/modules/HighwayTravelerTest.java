/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.modules;

import org.junit.jupiter.api.Test;
import xyz.thm.addon.modules.HighwayTraveler.EndCheck;
import xyz.thm.addon.modules.HighwayTraveler.GapWait;
import xyz.thm.addon.modules.HighwayTraveler.RepairScan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighwayTravelerTest {
    @Test
    void paveEndRequiresEveryLaneInEveryRow() {
        assertEquals(EndCheck.UNLOADED, HighwayTraveler.checkEnd(15, 0, 1, 0, 3,
            (chunkX, chunkZ) -> chunkX == 0, (x, z) -> true));
        assertEquals(EndCheck.UNLOADED, HighwayTraveler.checkEnd(15, 16, 0, 1, 3,
            (chunkX, chunkZ) -> chunkX == 0, (x, z) -> true));
        assertEquals(EndCheck.PRESENT, HighwayTraveler.checkEnd(15, 0, 1, 0, 3,
            (chunkX, chunkZ) -> true, (x, z) -> true));
    }

    @Test
    void loadedPaveDisprovesTheEndEvenWithUnloadedSamples() {
        assertEquals(EndCheck.CONTINUES, HighwayTraveler.checkEnd(15, 0, 1, 0, 3,
            (chunkX, chunkZ) -> chunkX == 0 && chunkZ == 0, (x, z) -> x != 15 || z != 1));
    }

    @Test
    void brokenHighwayGoesToTheBuilderOnlyWithAutoRepair() {
        assertEquals(GapWait.HANDOFF, HighwayTraveler.gapWaitDecision(RepairScan.BROKEN, true, 0));
        assertEquals(GapWait.STOP, HighwayTraveler.gapWaitDecision(RepairScan.BROKEN, false, 0));
    }

    @Test
    void knownEndStopsWithoutWaiting() {
        assertEquals(GapWait.STOP, HighwayTraveler.gapWaitDecision(RepairScan.NONE, true, 0));
    }

    @Test
    void unloadedContinuationWaitsWithOrWithoutAutoRepair() {
        int ticks = 0;
        while (HighwayTraveler.gapWaitDecision(RepairScan.UNLOADED, false, ticks) == GapWait.WAIT) {
            assertEquals(GapWait.WAIT, HighwayTraveler.gapWaitDecision(RepairScan.UNLOADED, true, ticks));
            assertTrue(++ticks <= 20 * 60, "gap wait never ends");
        }
        assertTrue(ticks > 25, "wait must outlast the stuck detector it replaces");
        assertEquals(GapWait.STOP, HighwayTraveler.gapWaitDecision(RepairScan.UNLOADED, true, ticks));
    }

    @Test
    void holdingWithoutASurfaceStopsAtLaneHeight() {
        assertEquals(0.3, HighwayTraveler.holdDescent(0.5, 120.3, 120, Double.NaN), 1e-9);
        assertEquals(0.0, HighwayTraveler.holdDescent(0.01, 120.0, 120, Double.NaN), 1e-9);
        assertEquals(0.01, HighwayTraveler.holdDescent(0.01, 121.5, 120, Double.NaN), 1e-9);
    }

    @Test
    void holdingDescendsOntoAStepButStopsAboveADeepDrop() {
        assertEquals(0.5, HighwayTraveler.holdDescent(0.01, 120.0, 120, 119.0), 1e-9);
        assertEquals(0.0, HighwayTraveler.holdDescent(0.01, 120.0, 120, 117.0), 1e-9);
    }

    @Test
    void holdingBelowTheLaneOrWithoutOneKeepsFalling() {
        assertEquals(0.5, HighwayTraveler.holdDescent(0.5, 118.0, 120, Double.NaN), 1e-9);
        assertEquals(0.5, HighwayTraveler.holdDescent(0.5, 140.0, Integer.MIN_VALUE, Double.NaN), 1e-9);
    }
}
