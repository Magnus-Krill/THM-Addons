/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import xyz.thm.addon.modules.HighwayBuilderTHM;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Checked, owner-specific staging of main-inventory items in spare hotbar slots. */
public final class DeepSwapManager {
    private static DeepSwapManager instance;
    private final List<Session> sessions = new ArrayList<>(3);
    private boolean clicking, clickSent;

    private DeepSwapManager() { MeteorClient.EVENT_BUS.subscribe(this); }

    public static DeepSwapManager get() {
        if (instance == null) instance = new DeepSwapManager();
        return instance;
    }

    public int stage(Object owner, int source) {
        return stage(owner, source, false);
    }

    public int stage(Object owner, int source, boolean allowItemUse) {
        if (!canClick(allowItemUse) || source < 9 || source >= 36 || InventoryManager.clickBudget(1) == 0) return -1;
        for (Session session : sessions) if (session.owner == owner) return session.source == source && valid(session) ? session.hotbar : -1;
        if (sessions.size() >= 3 || sessions.stream().anyMatch(s -> s.source == source)) return -1;
        ItemStack sourceStack = mc.player.getInventory().getItem(source);
        if (sourceStack.isEmpty()) return -1;
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        int buffer = -1;
        for (int i = 8; i >= 0; i--) {
            int slot = i;
            if (slot == mc.player.getInventory().getSelectedSlot() || SwapManager.get().reserved(slot)
                || sessions.stream().anyMatch(s -> s.hotbar == slot)
                || builder != null && builder.isActive() && builder.manageHotbar.get() && slot >= 4) continue;
            if (buffer < 0 || mc.player.getInventory().getItem(slot).isEmpty()) buffer = slot;
            if (mc.player.getInventory().getItem(slot).isEmpty()) break;
        }
        if (buffer < 0) return -1;
        Session session = new Session(owner, source, buffer, mc.player.getInventory().getItem(buffer).copy(), sourceStack.copy());
        sessions.add(session);
        if (!click(source, buffer)) { sessions.remove(session); return -1; }
        if (!valid(session)) { sessions.remove(session); return -1; }
        return buffer;
    }

    public void restore(Object owner) {
        for (Session session : sessions) if (session.owner == owner) session.restoring = true;
        tickRestores();
    }

    public void onUserAction() {
        if (mc.player == null) return;
        for (Session session : sessions) if (session.hotbar == mc.player.getInventory().getSelectedSlot()) session.restoring = true;
        tickRestores();
    }

    private boolean canClick(boolean allowItemUse) {
        return mc.player != null && mc.level != null && mc.gameMode != null && mc.getConnection() != null
            && mc.player.containerMenu == mc.player.inventoryMenu && mc.player.containerMenu.getCarried().isEmpty()
            && (allowItemUse || !mc.player.isUsingItem());
    }

    private boolean valid(Session session) {
        if (mc.player == null || mc.player != session.player || mc.level != session.level) return false;
        ItemStack staged = mc.player.getInventory().getItem(session.hotbar);
        return (staged.isEmpty() || ItemSignature.matches(session.staged, staged))
            && ItemStack.matches(mc.player.getInventory().getItem(session.source), session.original);
    }

    private void tickRestores() {
        if (!canClick(false)) return;
        var iterator = sessions.iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (!valid(session)) { iterator.remove(); continue; }
            if (!session.restoring || SwapManager.get().reserved(session.hotbar) || InventoryManager.clickBudget(1) == 0) continue;
            if (click(session.source, session.hotbar)) iterator.remove();
            break;
        }
    }

    private boolean click(int source, int hotbar) {
        var inventory = mc.player.getInventory();
        ItemStack beforeSource = inventory.getItem(source).copy(), beforeHotbar = inventory.getItem(hotbar).copy();
        clicking = true;
        clickSent = false;
        try { mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, source, hotbar, ContainerInput.SWAP, mc.player); }
        finally { clicking = false; }
        if (!clickSent && ItemStack.matches(inventory.getItem(source), beforeHotbar) && ItemStack.matches(inventory.getItem(hotbar), beforeSource)) {
            inventory.setItem(source, beforeSource);
            inventory.setItem(hotbar, beforeHotbar);
        }
        return clickSent;
    }

    @EventHandler
    private void onSent(PacketEvent.Sent event) {
        if (clicking && event.packet instanceof net.minecraft.network.protocol.game.ServerboundContainerClickPacket
            && mc.getConnection() != null && event.connection == mc.getConnection().getConnection()) clickSent = true;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) { tickRestores(); }

    @EventHandler
    private void onLeave(GameLeftEvent event) { sessions.clear(); }

    private static final class Session {
        final net.minecraft.client.player.LocalPlayer player = mc.player;
        final net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        final Object owner;
        final int source, hotbar;
        final ItemStack original, staged;
        boolean restoring;
        Session(Object owner, int source, int hotbar, ItemStack original, ItemStack staged) {
            this.owner = owner; this.source = source; this.hotbar = hotbar; this.original = original; this.staged = staged;
        }
    }
}
