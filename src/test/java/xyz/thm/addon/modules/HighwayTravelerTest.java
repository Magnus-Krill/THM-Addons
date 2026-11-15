/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import org.junit.jupiter.api.Test;

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
}
