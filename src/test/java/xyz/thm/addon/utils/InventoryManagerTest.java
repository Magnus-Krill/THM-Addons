/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryManagerTest {
    @Test
    void silentRestorationUsesCurrentClientSelection() {
        assertEquals(4, InventoryManager.restorationSlot(2, 2, 4));
        assertEquals(7, InventoryManager.restorationSlot(2, 2, 7), "Scrolling during a hold must not restore an old selection.");
    }

    @Test
    void silentRestorationCannotTakeAnotherOwnersSlot() {
        assertEquals(-1, InventoryManager.restorationSlot(2, 5, 4));
        assertEquals(-1, InventoryManager.restorationSlot(-1, -1, 4));
        assertEquals(-1, InventoryManager.restorationSlot(2, 2, 9));
        assertEquals(-1, InventoryManager.restorationSlot(9, 9, 4));
    }
}
