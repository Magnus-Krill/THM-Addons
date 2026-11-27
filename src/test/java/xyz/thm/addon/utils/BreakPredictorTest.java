/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.utils;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BreakPredictorTest {
    private static final BlockPos POS = new BlockPos(1, 2, 3);
    private static final long MS = 1_000_000L;

    @Test
    void sendsEveryTickFromSeventyPercentUntilAfterFullProgress() {
        BreakPredictor predictor = new BreakPredictor();
        long lead = 300 * MS;
        predictor.stage(POS, 0, 0);
        assertTrue(predictor.due(0, lead).isEmpty(), "one sample gives no rate");
        predictor.stage(POS, 1, 100 * MS); // 70% at 700 ms, 100% at 1000 ms

        assertTrue(predictor.due(349 * MS, lead).isEmpty());
        assertEquals(List.of(POS), predictor.due(350 * MS, lead), "first packet may arrive one tick early");
        assertTrue(predictor.due(360 * MS, lead).isEmpty(), "at most one per tick");
        assertEquals(List.of(POS), predictor.due(400 * MS, lead));
        assertEquals(List.of(POS), predictor.due(850 * MS, lead), "still covers a late full-progress break");
        assertTrue(predictor.due(900 * MS, lead).isEmpty());
        assertFalse(predictor.tracks(POS));
    }

    @Test
    void abortedMiningStopsButRebreakWindowSurvivesTheClosingProgressPacket() {
        BreakPredictor predictor = new BreakPredictor();
        predictor.stage(POS, 0, 0);
        predictor.stage(POS, -1, 10 * MS);
        assertFalse(predictor.tracks(POS));

        predictor.broken(POS, 0, 100 * MS);
        predictor.stage(POS, -1, MS);
        assertTrue(predictor.due(10 * MS, 100 * MS).isEmpty(), "the break itself already sent");
        assertEquals(List.of(POS), predictor.due(50 * MS, 100 * MS));
        assertEquals(List.of(POS), predictor.due(300 * MS, 100 * MS));
        assertTrue(predictor.due(301 * MS, 100 * MS).isEmpty());
    }
}
