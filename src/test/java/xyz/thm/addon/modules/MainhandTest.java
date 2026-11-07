/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainhandTest {
    @Test
    void appleBindOverridesTotemAndFallbackOnlyAppliesWithoutOne() {
        assertTrue(Mainhand.wantsApple(true, false, true));
        assertTrue(Mainhand.wantsApple(false, true, false));
        assertFalse(Mainhand.wantsApple(false, true, true));
        assertFalse(Mainhand.wantsApple(false, false, false));
    }
}
