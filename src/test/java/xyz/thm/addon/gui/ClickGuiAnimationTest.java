/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClickGuiAnimationTest {
    @Test
    void rippleLiftsLeftPanelsBeforeRightPanelsAndFinishesTogether() {
        ClickGuiAnimation ripple = ClickGuiAnimation.Ripple;
        assertTrue(ripple.panelProgress(0.25, 0, 1000) > 0);
        assertEquals(0, ripple.panelProgress(0.25, 900, 1000));
        assertEquals(1, ripple.panelProgress(1, 0, 1000));
        assertEquals(1, ripple.panelProgress(1, 900, 1000));
    }

    @Test
    void scatterStartsCompletelyBeyondOneOfTheFourEdges() {
        ClickGuiAnimation scatter = ClickGuiAnimation.Scatter;
        for (int edge = 0; edge < 4; edge++) {
            int seed = (edge << 30) | 0x2043ac62;
            double x = scatter.startX(150, 200, 1000, seed);
            double y = scatter.startY(0, 750, 800, seed);
            switch (edge) {
                case 0 -> assertTrue(x + 200 <= 0);
                case 1 -> assertTrue(x >= 1000);
                case 2 -> assertTrue(y + 750 <= 0);
                case 3 -> assertTrue(y >= 800);
            }
            if (edge < 2) assertTrue(y + 750 > 0 && y < 800);
            else assertTrue(x + 200 > 0 && x < 1000);
        }
    }

    @Test
    void scatterPathDependsOnTheTransitionAndThePanel() {
        int seed = ClickGuiAnimation.scatterPanelSeed(778, "Movement");
        assertEquals(seed, ClickGuiAnimation.scatterPanelSeed(778, "Movement"));
        assertNotEquals(seed, ClickGuiAnimation.scatterPanelSeed(779, "Movement"));
        assertNotEquals(seed, ClickGuiAnimation.scatterPanelSeed(778, "Render"));
    }

    @Test
    void dropAndSplitStartJustOutsideTheViewport() {
        assertEquals(-250, ClickGuiAnimation.Drop.startY(30, 250, 800, 0));
        assertEquals(-200, ClickGuiAnimation.Split.startX(100, 200, 1000, 0));
        assertEquals(1000, ClickGuiAnimation.Split.startX(700, 200, 1000, 0));
    }
}
