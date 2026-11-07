/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwapManagerTest {
    @Test
    void temporaryUseHandsTheSlotBackToMiningThenToTheOriginalSelection() {
        var state = new SwapManager.Ownership();
        Object miner = new Object(), aura = new Object();
        var mining = state.acquire(miner, 7, 10, 0, false);
        var crystal = state.acquire(aura, 8, 30, 7, true);
        assertNotNull(crystal);
        assertEquals(7, state.finish(crystal, 8));
        assertSame(mining, state.current);
        assertEquals(0, state.finish(mining, 7));
        assertNull(state.current);
    }

    @Test
    void equalOrLowerPriorityCannotStealAnotherOwnersHold() {
        var state = new SwapManager.Ownership();
        var owner = new Object();
        var held = state.acquire(owner, 7, 20, 0, false);
        assertNull(state.acquire(new Object(), 2, 20, 7, true));
        assertNull(state.acquire(new Object(), 2, 10, 7, false));
        assertSame(held, state.current);
    }

    @Test
    void manualOrServerSlotChangesCancelRestoration() {
        var state = new SwapManager.Ownership();
        var lease = state.acquire(this, 7, 10, 0, true);
        assertEquals(-1, state.finish(lease, 3));
        assertNull(state.current);
    }

    @Test
    void aCancelledBorrowKeepsTheExistingMiningHold() {
        var state = new SwapManager.Ownership();
        var mining = state.acquire(this, 7, 10, 0, false);
        var borrow = state.acquire(new Object(), 8, 30, 7, true);
        assertEquals(-1, state.finish(borrow, 7));
        assertSame(mining, state.current);
    }

    @Test
    void refreshingAHoldPreservesItsOriginalSelection() {
        var state = new SwapManager.Ownership();
        var first = state.acquire(this, 7, 10, 0, false);
        assertSame(first, state.acquire(this, 8, 10, 7, false));
        assertEquals(0, state.finish(first, 8));
    }

    @Test
    void cancelledHoldRefreshKeepsThePreviousServerSlotReserved() {
        var state = new SwapManager.Ownership();
        var mining = state.acquire(this, 7, 10, 0, false);
        var refresh = state.acquire(this, 8, 10, 7, false);
        state.cancelHold(refresh, mining, 7, 7);
        assertSame(mining, state.current);
        assertEquals(7, mining.slot);
        assertEquals(0, state.finish(mining, 7));
    }

    @Test
    void nestedTemporaryActionsRestoreInOrder() {
        var state = new SwapManager.Ownership();
        var first = state.acquire(this, 7, 10, 0, false);
        var second = state.acquire(new Object(), 8, 30, 7, true);
        var third = state.acquire(new Object(), 2, 40, 8, true);
        assertEquals(-1, state.finish(second, 2));
        assertEquals(8, state.finish(third, 2));
        assertEquals(7, state.finish(second, 8));
        assertSame(first, state.current);
    }
}
