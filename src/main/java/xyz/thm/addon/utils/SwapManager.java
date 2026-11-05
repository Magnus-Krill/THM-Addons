/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.world.InteractionHand;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Shared ownership of silent hotbar holds and temporary item use. */
public final class SwapManager {
    public static final int MINING = 10, WEAPON = 20, SURROUND = 25, CRYSTAL = 30, PEARL = 40;
    private static SwapManager instance;
    private final Ownership ownership = new Ownership();
    private net.minecraft.client.player.LocalPlayer player;
    private net.minecraft.client.multiplayer.ClientLevel level;

    private SwapManager() { MeteorClient.EVENT_BUS.subscribe(this); }

    public static SwapManager get() {
        if (instance == null) instance = new SwapManager();
        return instance;
    }

    public boolean hold(Object owner, int slot, int priority, boolean allowItemUse) {
        if (!available(priority, allowItemUse) || slot < 0 || slot > 8) return false;
        checkDrift();
        Lease lease = ownership.acquire(owner, slot, priority, InventoryManager.getInstance().getServerSlot(), false);
        if (lease == null) return false;
        InventoryManager.getInstance().setSlotForced(slot);
        if (InventoryManager.getInstance().getServerSlot() == slot) return true;
        ownership.clear();
        return false;
    }

    public boolean withSlot(Object owner, int slot, int priority, boolean silent, Runnable action) {
        return withSlot(owner, slot, priority, silent, false, action);
    }

    public boolean withSlot(Object owner, int slot, int priority, boolean silent, boolean allowItemUse, Runnable action) {
        if (!available(priority, allowItemUse) || slot < 0 || slot > 8) return false;
        checkDrift();
        InventoryManager inventory = InventoryManager.getInstance();
        Lease lease = ownership.acquire(owner, slot, priority, inventory.getServerSlot(), true);
        if (lease == null) return false;
        var player = mc.player;
        var level = mc.level;
        int clientSlot = player.getInventory().getSelectedSlot();
        try {
            if (!silent) player.getInventory().setSelectedSlot(slot);
            inventory.setSlotForced(slot);
            if (inventory.getServerSlot() != slot) return false;
            action.run();
            return true;
        } finally {
            if (mc.player == player && mc.level == level) {
                int restore = ownership.finish(lease, inventory.getServerSlot());
                if (!silent && player.getInventory().getSelectedSlot() == slot) player.getInventory().setSelectedSlot(clientSlot);
                if (restore >= 0) inventory.setSlotForced(restore);
            } else ownership.clear();
        }
    }

    public void release(Object owner) {
        if (mc.player == null) { ownership.clear(); return; }
        checkDrift();
        Lease lease = ownership.current;
        if (lease == null || lease.owner != owner || lease.temporary) return;
        ownership.clear();
        InventoryManager.getInstance().setSlotForced(mc.player.getInventory().getSelectedSlot());
    }

    public void onUserAction() {
        checkDrift();
        Lease lease = ownership.current;
        if (lease != null && !lease.temporary) release(lease.owner);
        DeepSwapManager.get().onUserAction();
    }

    public boolean reserved(int slot) {
        for (Lease lease = ownership.current; lease != null; lease = lease.parent) if (lease.slot == slot) return true;
        return false;
    }

    private boolean available(int priority, boolean allowItemUse) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return false;
        InventoryManager inventory = InventoryManager.getInstance();
        return inventory.getCurrentPriority() <= priority && (allowItemUse
            || !inventory.isEating() && (!mc.player.isUsingItem() || mc.player.getUsedItemHand() != InteractionHand.MAIN_HAND));
    }

    private void checkDrift() {
        if (player != mc.player || level != mc.level || mc.player == null || ownership.current != null
            && InventoryManager.getInstance().getServerSlot() != ownership.current.slot) ownership.clear();
        player = mc.player;
        level = mc.level;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTick(TickEvent.Pre event) { checkDrift(); }

    @EventHandler
    private void onLeave(GameLeftEvent event) { ownership.clear(); player = null; level = null; }

    static final class Lease {
        final Object owner;
        final int priority, previous;
        final boolean temporary;
        final Lease parent;
        int slot;

        Lease(Object owner, int slot, int priority, int previous, boolean temporary, Lease parent) {
            this.owner = owner; this.slot = slot; this.priority = priority;
            this.previous = previous; this.temporary = temporary; this.parent = parent;
        }
    }

    static final class Ownership {
        Lease current;

        Lease acquire(Object owner, int slot, int priority, int previous, boolean temporary) {
            if (current != null && current.owner != owner && priority <= current.priority) return null;
            if (!temporary && current != null) {
                if (current.owner != owner || current.temporary) return null;
                current.slot = slot;
                return current;
            }
            return current = new Lease(owner, slot, priority, previous, temporary, current);
        }

        int finish(Lease lease, int serverSlot) {
            if (current != lease) return -1;
            if (serverSlot != lease.slot && serverSlot == lease.previous) { current = lease.parent; return -1; }
            if (serverSlot != lease.slot) { clear(); return -1; }
            current = lease.parent;
            return lease.previous;
        }

        void clear() { current = null; }
    }
}
