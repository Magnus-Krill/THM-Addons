/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.thm.addon.settings.PacketPlacementSettings.Mode.*;

class PlacementPacerTest {
    @Test
    void paceReplacesLowerAndHigherLocalTickCaps() {
        assertEquals(9, PlacementUtils.placementsPerTick(Pace, 1));
        assertEquals(9, PlacementUtils.placementsPerTick(Pace, 30));
        assertEquals(1, PlacementUtils.placementsPerTick(Normal, 1));
        assertEquals(30, PlacementUtils.placementsPerTick(Direct, 30));
    }

    @Test
    void onlyPacedSendsShareNineSlotsAcrossModules() {
        PlacementPacer pacer = new PlacementPacer();
        long start = 40_000_000L;
        for (int i = 0; i < 4; i++) assertTrue(pacer.send(Pace, () -> start, () -> true));
        assertEquals(5, pacer.remaining(start));
        for (int i = 0; i < 12; i++) assertTrue(pacer.send(Direct, () -> start, () -> true));
        assertEquals(5, pacer.remaining(start));

        for (int i = 0; i < 5; i++) assertTrue(pacer.send(Pace, () -> start + 10_000_000, () -> true));
        assertFalse(pacer.send(Pace, () -> start + 309_999_999, () -> fail("Tenth paced placement dispatched")));
        assertTrue(pacer.send(Direct, () -> start + 309_999_999, () -> true));
        assertTrue(pacer.send(Normal, () -> start + 309_999_999, () -> true));
        assertEquals(0, pacer.remaining(start + 309_999_999));
        assertTrue(pacer.send(Pace, () -> start + 310_000_000, () -> true));
        assertEquals(8, pacer.remaining(start + 310_000_000));
    }

    @Test
    void rejectedSendsDoNotStartTheWindowOrSpendAPlacement() {
        PlacementPacer pacer = new PlacementPacer();
        assertFalse(pacer.send(Pace, () -> 10, () -> false));
        long first = 100_000_000L;
        assertTrue(pacer.send(Pace, () -> first, () -> true));
        assertFalse(pacer.send(Pace, () -> first + 10, () -> false));
        assertEquals(8, pacer.remaining(first + 309_999_999));
        pacer.reset();
        assertEquals(9, pacer.remaining(first + 310_000_000));
    }
}
