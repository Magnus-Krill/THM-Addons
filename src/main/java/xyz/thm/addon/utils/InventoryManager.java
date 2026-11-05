/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import xyz.thm.addon.mixin.accessor.InputAccessor;
import xyz.thm.addon.mixin.accessor.PlayerInventoryAccessor;
import xyz.thm.addon.mixin.accessor.UpdateSelectedSlotS2CPacketAccessor;

import java.util.Arrays;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.player.ChatUtils.info;

public class InventoryManager {
    private static InventoryManager INSTANCE;
    private volatile int serverSlot = -1;
    private boolean sendingPacket = false;
    private boolean isEating = false;
    private long lastSetbackTime = -1;
    private final int[] transactions = new int[4];
    private int transactionIndex = 0;
    private boolean isGrim = false;
    private int currentPriority = Priority.NORMAL;
    public static class Priority {
        public static final int NORMAL = 0;
        public static final int TOTEM = 5;
        public static final int EATING = 10;
        public static final int SURROUND = 20;
        public static final int PEARL_PHASE = 30;
    }

    private InventoryManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
        Arrays.fill(transactions, -1);
    }
    public static InventoryManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new InventoryManager();
        }
        return INSTANCE;
    }
    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (sendingPacket) return;
        if (event.packet instanceof ServerboundSetCarriedItemPacket packet) {
            final int packetSlot = packet.getSlot();
            if (!Inventory.isHotbarSlot(packetSlot) || serverSlot == packetSlot) {
                event.cancel();
                return;
            }
        }
    }
    @EventHandler
    public void onPacketSent(PacketEvent.Sent event) {
        if (mc.getConnection() != null && event.connection == mc.getConnection().getConnection()
            && event.packet instanceof ServerboundSetCarriedItemPacket packet) serverSlot = packet.getSlot();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundSetHeldSlotPacket packet) {
            if (mc.getConnection() != null && event.connection == mc.getConnection().getConnection()) {
                serverSlot = ((UpdateSelectedSlotS2CPacketAccessor) (Object) packet).getSlot();
            }
        }
        else if (event.packet instanceof ClientboundPingPacket packet) {
            if (transactionIndex > 3) {
                return;
            }
            final int uid = packet.getId();
            transactions[transactionIndex] = uid;
            ++transactionIndex;
            if (transactionIndex == 4) {
                grimCheck();
            }
        }
        else if (event.packet instanceof ClientboundPlayerPositionPacket) {
            lastSetbackTime = System.currentTimeMillis();
        }
    }
    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.player != null && serverSlot == -1) {
            serverSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        }
        if (!isEating && currentPriority > Priority.NORMAL) {
            currentPriority = Priority.NORMAL;
        }
    }
    @EventHandler
    public void onDisconnect(GameLeftEvent event) {
        serverSlot = -1;
        sendingPacket = false;
        isEating = false;
        currentPriority = Priority.NORMAL;
        Arrays.fill(transactions, -1);
        transactionIndex = 0;
        isGrim = false;
        lastSetbackTime = -1;
    }
    private void grimCheck() {
        for (int i = 0; i < 4; ++i) {
            if (transactions[i] != -i) {
                return;
            }
        }
        isGrim = true;
        info("Server is running GrimAC.");
    }
    public boolean isGrim() {
        return isGrim;
    }
    public boolean hasPassed(final long timeMS) {
        return lastSetbackTime != -1 && (System.currentTimeMillis() - lastSetbackTime) >= timeMS;
    }
    public void setSlot(final int barSlot) {
        setSlot(barSlot, Priority.NORMAL);
    }
    public void setSlot(final int barSlot, boolean highPriority) {
        setSlot(barSlot, highPriority ? Priority.SURROUND : Priority.NORMAL);
    }
    public void setSlot(final int barSlot, int priority) {
        if (mc.player == null || mc.getConnection() == null || priority < currentPriority || !Inventory.isHotbarSlot(barSlot)) return;
        setSlotForced(barSlot);
        if (getServerSlot() == barSlot) currentPriority = priority;
    }
    public void setClientSlot(final int barSlot) {
        setClientSlot(barSlot, Priority.NORMAL);
    }
    public void setClientSlot(final int barSlot, int priority) {
        if (mc.player == null || priority < currentPriority || !Inventory.isHotbarSlot(barSlot)) return;
        mc.player.getInventory().setSelectedSlot(barSlot);
        setSlotForced(barSlot);
        if (getServerSlot() == barSlot) currentPriority = priority;
    }
    public void setSlotForced(final int barSlot) {
        if (mc.getConnection() == null || !Inventory.isHotbarSlot(barSlot) || getServerSlot() == barSlot) return;
        sendingPacket = true;
        try {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(barSlot));
        } finally {
            sendingPacket = false;
        }
    }
    public void syncToClient() {
        if (mc.player == null) return;
        if (isDesynced()) {
            setSlotForced(((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot());
        }
    }
    public boolean isDesynced() {
        if (mc.player == null) return false;
        return ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot() != serverSlot;
    }
    public int getServerSlot() {
        if (mc.player == null) return -1;
        return serverSlot == -1 ? ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot() : serverSlot;
    }
    public int getClientSlot() {
        if (mc.player == null) return -1;
        return ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
    }
    public ItemStack getServerItem() {
        if (mc.player != null && getServerSlot() != -1) {
            return mc.player.getInventory().getItem(getServerSlot());
        }
        return ItemStack.EMPTY;
    }
    public void setEating(boolean eating) {
        this.isEating = eating;
        if (eating) {
            currentPriority = Priority.EATING;
        } else {
            currentPriority = Priority.NORMAL;
        }
    }
    public boolean isEating() {
        return isEating;
    }
    public int getCurrentPriority() {
        return currentPriority;
    }
    /**
     * Parks a stuck cursor stack in a free slot of whatever handler is open, so the next PICKUP picks up
     * instead of placing. Prefers the main inventory and hotbar, falls back to free container slots;
     * armor, offhand and the crafting grid are never used, and nothing is ever dropped.
     *
     * @return true if the cursor ended up empty
     */
    public static boolean parkCursor() {
        if (mc.player == null || mc.gameMode == null) return false;
        AbstractContainerMenu handler = mc.player.containerMenu;
        if (handler == null) return false;
        ItemStack cursor = handler.getCarried();
        if (cursor.isEmpty()) return true;

        boolean playerScreen = handler instanceof InventoryMenu;
        // main inventory + hotbar: in a container handler those are always the last 36 slots,
        // in the player handler they are 9..44 (the offhand sits behind them at 45).
        int invStart = playerScreen ? 9 : handler.slots.size() - 36;
        int invEnd = playerScreen ? 45 : handler.slots.size();

        if (!parkCursorIn(handler, cursor, invStart, invEnd) && !playerScreen) {
            parkCursorIn(handler, cursor, 0, Math.max(0, handler.slots.size() - 36));
        }

        return handler.getCarried().isEmpty();
    }

    private static boolean parkCursorIn(AbstractContainerMenu handler, ItemStack cursor, int start, int end) {
        for (int i = start; i < end; i++) {
            Slot slot = handler.getSlot(i);
            if (!slot.getItem().isEmpty() || !slot.mayPlace(cursor)) continue;
            mc.gameMode.handleContainerInput(handler.containerId, i, 0, ContainerInput.PICKUP, mc.player);
            return true;
        }
        return false;
    }

    public static int getBestWeaponSlot() {
        float bestDamage = 0.0f;
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            float damage = getWeaponDamage(stack);
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
    public static float getWeaponDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0.0f;
        Item item = stack.getItem();
        float baseDamage = 0.0f;
        if (item.toString().toLowerCase().contains("sword")) {
            baseDamage = 4.0f;
        } else if (item instanceof AxeItem axe) {
            baseDamage = 5.0f;
        } else if (item instanceof TridentItem) {
            baseDamage = TridentItem.BASE_DAMAGE;
        } else if (item instanceof MaceItem) {
            baseDamage = 5.0f;
        } else {
            return 0.0f;
        }
        int sharpnessLevel = meteordevelopment.meteorclient.utils.Utils.getEnchantmentLevel(stack, Enchantments.SHARPNESS);
        float sharpnessDamage = sharpnessLevel * 0.5f + 0.5f;
        return baseDamage + sharpnessDamage;
    }
    public static int getBestBreachMaceSlot() {
        int bestSlot = -1;
        int bestBreachLevel = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!(stack.getItem() instanceof MaceItem)) continue;
            int breachLevel = meteordevelopment.meteorclient.utils.Utils.getEnchantmentLevel(stack, Enchantments.BREACH);
            if (breachLevel > bestBreachLevel) {
                bestBreachLevel = breachLevel;
                bestSlot = i;
            }
        }
        return bestSlot;
    }
    public static boolean isHoldingWeapon() {
        ItemStack mainHand = mc.player.getMainHandItem();
        Item item = mainHand.getItem();
        return item.toString().toLowerCase().contains("sword") ||
               item instanceof AxeItem ||
               item instanceof TridentItem ||
               item instanceof MaceItem;
    }
    public static boolean isHoldingWeaponType(Class<? extends Item> weaponType) {
        return weaponType.isInstance(mc.player.getMainHandItem().getItem());
    }
    public static ItemStack getCurrentWeapon() {
        ItemStack mainHand = mc.player.getMainHandItem();
        return isHoldingWeapon() ? mainHand : ItemStack.EMPTY;
    }
    public static void swapToSlot(int slot) {
        if (slot >= 0 && slot < 9) {
            ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot( slot);
        }
    }

    public static boolean withItem(Object owner, int source, boolean silent, int priority, Runnable action) {
        return withItem(owner, source, silent, priority, false, action);
    }

    public static boolean withItem(Object owner, int source, boolean silent, int priority, boolean allowItemUse, Runnable action) {
        if (mc.player == null || source < 0 || source >= 36) return false;
        boolean staged = source >= 9;
        int slot = staged ? DeepSwapManager.get().stage(owner, source, allowItemUse) : source;
        if (slot < 0) return false;
        try {
            return SwapManager.get().withSlot(owner, slot, priority, silent, allowItemUse, action);
        } finally {
            if (staged) DeepSwapManager.get().restore(owner);
        }
    }

    public static int clickBudget(int requested) {
        var modules = meteordevelopment.meteorclient.systems.modules.Modules.get();
        var limiter = modules == null ? null : modules.get(xyz.thm.addon.modules.PaketLimiter.class);
        return limiter == null ? requested : Math.min(requested,
            limiter.available(net.minecraft.network.protocol.game.GamePacketTypes.SERVERBOUND_CONTAINER_CLICK));
    }

    public static double getAttackSpeed(ItemStack weapon) {
        if (weapon.isEmpty()) return 4.0;
        Item item = weapon.getItem();
        if (item.toString().toLowerCase().contains("sword")) return 1.6;
        if (item instanceof AxeItem) return 0.8;
        if (item instanceof TridentItem) return 1.1;
        if (item instanceof MaceItem) return 0.6;
        return 4.0;
    }
    public static int getAttackCooldownTicks(ItemStack weapon) {
        double attackSpeed = getAttackSpeed(weapon);
        return (int) Math.ceil(20.0 / attackSpeed);
    }
    public static boolean isHolding32k() {
        if (mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        return is32kWeapon(mainHand) || is32kWeapon(offHand);
    }
    private static boolean is32kWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        boolean isWeaponOrTool = item.toString().toLowerCase().contains("sword") ||
                                  item.toString().toLowerCase().contains("pickaxe") ||
                                  item.toString().toLowerCase().contains("axe") ||
                                  item.toString().toLowerCase().contains("shovel");
        if (!isWeaponOrTool) {
            return false;
        }
        return meteordevelopment.meteorclient.utils.Utils.getEnchantmentLevel(stack, Enchantments.SHARPNESS) > 1000 ||
               meteordevelopment.meteorclient.utils.Utils.getEnchantmentLevel(stack, Enchantments.SMITE) > 1000 ||
               meteordevelopment.meteorclient.utils.Utils.getEnchantmentLevel(stack, Enchantments.BANE_OF_ARTHROPODS) > 1000;
    }
    public static boolean isMovingInput() {
        if (mc.player == null) return false;
        InputAccessor inputAccessor = (InputAccessor) mc.player.input;
        return inputAccessor.thm$getMoveVector().y != 0.0f ||
               inputAccessor.thm$getMoveVector().x != 0.0f ||
               mc.options.keyJump.isDown() ||
               mc.options.keyShift.isDown();
    }
    public void withSilentTool(BlockState state, Runnable action) {
        int best = findBestHotbarSlot(state);
        if (best >= 0) SwapManager.get().withSlot(this, best, SwapManager.MINING, true, action);
    }

    public int findBestHotbarSlot(BlockState state) {
        if (mc.player == null) return -1;
        int   best      = -1;
        float bestSpeed = -1;
        for (int i = 0; i < 9; i++) {
            float s = mc.player.getInventory().getItem(i).getDestroySpeed(state);
            if (s > bestSpeed) { bestSpeed = s; best = i; }
        }
        return best;
    }

    public enum VelocityMode {
        NORMAL,
        WALLS,
        GRIM,
        GRIM_V3
    }
    public enum SwapMode {
        Normal,
        Silent
    }

}
