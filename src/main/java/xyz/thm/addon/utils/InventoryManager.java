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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.player.ChatUtils.info;

public class InventoryManager {
    private static InventoryManager INSTANCE;
    private final List<PreSwapData> swapData = new CopyOnWriteArrayList<>();
    private volatile int serverSlot = -1;
    private boolean sendingPacket = false;
    private int pendingRestoreSlot = -1;
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
    private int lastSwapOriginalSlot = -1;
    private int lastSwapBufferSlot = -1;
    private int lastSwapOriginalServerSlot = -1;
    private int lastSwapOriginalClientSlot = -1;

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
        if (mc.getConnection() == null || event.connection != mc.getConnection().getConnection()) return;
        if (event.packet instanceof ServerboundSetCarriedItemPacket packet && Inventory.isHotbarSlot(packet.getSlot())) {
            serverSlot = packet.getSlot();
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ClientboundSetHeldSlotPacket packet) {
            int slot = ((UpdateSelectedSlotS2CPacketAccessor) (Object) packet).getSlot();
            mc.execute(() -> {
                if (mc.getConnection() != null && event.connection == mc.getConnection().getConnection()
                    && Inventory.isHotbarSlot(slot)) serverSlot = slot;
            });
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
        swapData.removeIf(PreSwapData::isExpired);
        if (!isEating && currentPriority > Priority.NORMAL) {
            currentPriority = Priority.NORMAL;
        }
        if (pendingRestoreSlot != -1 && currentPriority == Priority.NORMAL && !isEating) {
            int slot = pendingRestoreSlot;
            pendingRestoreSlot = -1;
            restoreSilentSlot(slot);
        }
    }
    @EventHandler
    public void onDisconnect(GameLeftEvent event) {
        serverSlot = -1;
        pendingRestoreSlot = -1;
        sendingPacket = false;
        isEating = false;
        currentPriority = Priority.NORMAL;
        swapData.clear();
        lastSwapOriginalSlot = -1;
        lastSwapBufferSlot = -1;
        lastSwapOriginalServerSlot = -1;
        lastSwapOriginalClientSlot = -1;
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
        if (mc.player == null || mc.getConnection() == null) return;
        if (priority < currentPriority) return;
        if (serverSlot == -1) {
            serverSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        }
        if (serverSlot != barSlot && Inventory.isHotbarSlot(barSlot)) {
            int previous = serverSlot;
            setSlotForced(barSlot);
            if (serverSlot != barSlot) return;
            final ItemStack[] hotbarCopy = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                hotbarCopy[i] = mc.player.getInventory().getItem(i);
            }
            swapData.add(new PreSwapData(hotbarCopy, previous, barSlot));
            currentPriority = priority;
        }
    }
    public void setClientSlot(final int barSlot) {
        setClientSlot(barSlot, Priority.NORMAL);
    }
    public void setClientSlot(final int barSlot, int priority) {
        if (mc.player == null) return;
        if (priority < currentPriority) return;
        if (((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot() != barSlot && Inventory.isHotbarSlot(barSlot)) {
            ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot( barSlot);
            setSlotForced(barSlot);
            currentPriority = priority;
        }
    }
    public void setSlotForced(final int barSlot) {
        if (mc.getConnection() == null || !Inventory.isHotbarSlot(barSlot)) return;
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
            for (PreSwapData data : swapData) {
                data.beginClear();
            }
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

    /**
     * Finds the item in inventory and swaps to it. Call {@link #swapBack} afterward to restore.
     *
     * @param item      item to search for (searches hotbar 0–8 if inventory=false, full 0–35 if true)
     * @param silent    true = server-side only (held item on client is unchanged); false = client + server both update
     * @param inventory true = uses SWAP-action into a buffer hotbar slot so the visually held item does not change;
     *                  false = plain UpdateSelectedSlot (hotbar only)
     */
    public static void swapTo(Item item, boolean silent, boolean inventory) {
        if (mc.player == null || mc.getConnection() == null) return;
        int limit = inventory ? 36 : 9;
        int slot = -1;
        for (int i = 0; i < limit; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) {
                slot = i;
                break;
            }
        }
        if (slot == -1) return;
        swapTo(slot, silent, inventory);
    }

    /**
     * Swaps to the given inventory slot. Call {@link #swapBack} afterward to restore.
     *
     * For main-inventory slots (9–35) uses a single SlotActionType.SWAP packet — no cursor involved,
     * one state-revision increment, and calling the same SWAP again in swapBack reverses it exactly.
     *   silent=false (InventoryNormal)  → target = current client hotbar slot; held item visibly changes.
     *   silent=true  (InventorySilent)  → target = buffer hotbar slot (8 or 7); held item does not change.
     *
     * @param slot      0–8 hotbar, 9–35 main inventory
     * @param silent    true = server-side only (held item on client unchanged); false = client + server update
     * @param inventory true = full-inventory SWAP; false = plain UpdateSelectedSlot (hotbar only)
     */
    public static void swapTo(int slot, boolean silent, boolean inventory) {
        InventoryManager mgr = getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        mgr.lastSwapOriginalSlot = -1;

        if (inventory) {
            int clientSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
            mgr.lastSwapOriginalServerSlot = mgr.getServerSlot();
            mgr.lastSwapOriginalClientSlot = clientSlot;

            if (slot < 9) {
                // Item is already in hotbar — just change selection, no inventory packets needed
                mgr.lastSwapOriginalSlot = slot;
                mgr.lastSwapBufferSlot = -1;
                if (silent) {
                    mgr.setSlotForced(slot);
                } else {
                    ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
                    mgr.setSlotForced(slot);
                }
            } else {
                // Silent: swap into buffer slot so the visually held item does not change.
                // Normal: swap into the currently held slot so the item is visibly in hand.
                int targetHotbar = silent ? (clientSlot == 8 ? 7 : 8) : clientSlot;
                mgr.lastSwapOriginalSlot = slot;
                mgr.lastSwapBufferSlot = targetHotbar;

                // Single SWAP packet: swaps screen-handler slot `slot` with hotbar slot `targetHotbar` (0-8).
                // Calling the same packet again in swapBack self-reverses the swap.
                int syncId = mc.player.containerMenu.containerId;
                mc.gameMode.handleContainerInput(syncId, slot, targetHotbar, ContainerInput.SWAP, mc.player);

                if (silent) {
                    mgr.setSlotForced(targetHotbar);
                } else {
                    ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(targetHotbar);
                    mgr.setSlotForced(targetHotbar);
                }
            }
        } else if (silent) {
            mgr.setSlot(slot);
        } else {
            if (!Inventory.isHotbarSlot(slot)) return;
            ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
            mgr.setSlotForced(slot);
        }
    }

    /**
     * Restores the state saved by the last {@link #swapTo} call that used inventory=true.
     * No-op if no inventory swap is pending.
     *
     * @param silent true = only restore server slot; false = restore client + server
     */
    public static void swapBack(boolean silent) {
        InventoryManager mgr = getInstance();
        if (mc.player == null || mc.getConnection() == null || mgr.lastSwapOriginalSlot == -1) return;

        if (mgr.lastSwapBufferSlot != -1) {
            // Same SWAP packet as swapTo — calling it again reverses the swap exactly.
            int syncId = mc.player.containerMenu.containerId;
            mc.gameMode.handleContainerInput(syncId, mgr.lastSwapOriginalSlot, mgr.lastSwapBufferSlot, ContainerInput.SWAP, mc.player);
        }

        if (silent) {
            mgr.setSlotForced(mgr.lastSwapOriginalServerSlot);
        } else {
            ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(mgr.lastSwapOriginalClientSlot);
            mgr.setSlotForced(mgr.lastSwapOriginalClientSlot);
        }

        mgr.lastSwapOriginalSlot = -1;
        mgr.lastSwapBufferSlot = -1;
        mgr.lastSwapOriginalServerSlot = -1;
        mgr.lastSwapOriginalClientSlot = -1;
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
    public static class PreSwapData {
        private final ItemStack[] preHotbar;
        private final int starting;
        private final int swapTo;
        private long clearTime = System.currentTimeMillis();
        public PreSwapData(ItemStack[] preHotbar, int start, int swapTo) {
            this.preHotbar = preHotbar;
            this.starting = start;
            this.swapTo = swapTo;
        }
        public void beginClear() {
            clearTime = System.currentTimeMillis();
        }
        public boolean isExpired() {
            return clearTime != -1 && System.currentTimeMillis() - clearTime > 300;
        }
        public ItemStack getPreHolding(int i) {
            return preHotbar[i];
        }
        public int getStarting() {
            return starting;
        }
        public int getSlot() {
            return swapTo;
        }
    }

    /** Preserves an existing silent hold and restores only while this action still owns the slot. */
    public boolean withSilentSlot(int slot, Runnable action) {
        if (mc.player == null || mc.getConnection() == null || !Inventory.isHotbarSlot(slot)) return false;
        var player = mc.player;
        var connection = mc.getConnection();
        int previous = getServerSlot(), clientSlot = getClientSlot();
        int priority = currentPriority;
        if (previous != slot) setSlotForced(slot);
        if (getServerSlot() != slot) return false;
        try {
            action.run();
            return true;
        } finally {
            if (mc.player == player && mc.getConnection() == connection) {
                if (currentPriority > priority) restoreSilentSlot(slot);
                else if (getServerSlot() == slot) {
                    int restore = getClientSlot() == clientSlot ? previous : getClientSlot();
                    if (restore != slot) setSlotForced(restore);
                }
            }
        }
    }

    public void restoreSilentSlot(int ownedSlot) {
        if (mc.player == null || ownedSlot < 0) return;
        if (currentPriority > Priority.NORMAL || isEating) {
            pendingRestoreSlot = ownedSlot;
            return;
        }
        int restore = restorationSlot(ownedSlot, getServerSlot(), getClientSlot());
        if (restore != -1 && restore != getServerSlot()) setSlotForced(restore);
    }

    public void cancelSilentRestore(int ownedSlot) {
        if (pendingRestoreSlot == ownedSlot) pendingRestoreSlot = -1;
    }

    static int restorationSlot(int ownedSlot, int actualSlot, int clientSlot) {
        return Inventory.isHotbarSlot(ownedSlot) && actualSlot == ownedSlot && Inventory.isHotbarSlot(clientSlot) ? clientSlot : -1;
    }

    public void withSilentTool(BlockState state, Runnable action) {
        withSilentSlot(findBestHotbarSlot(state), action);
    }

    public void sendSequencedUpdateSlot(int slot) {
        if (getServerSlot() != slot) setSlotForced(slot);
    }

    public int findBestHotbarSlot(BlockState state) {
        if (mc.player == null) return -1;
        int best = getServerSlot();
        int preferred = best;
        float bestSpeed = miningToolScore(mc.player.getInventory().getItem(best), state);
        for (int i = 0; i < 9; i++) {
            if (i == preferred) continue;
            float score = miningToolScore(mc.player.getInventory().getItem(i), state);
            if (score > bestSpeed) { bestSpeed = score; best = i; }
        }
        return best;
    }

    private static float miningToolScore(ItemStack stack, BlockState state) {
        return miningToolSpeed(stack, state) / (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state) ? 100 : 30);
    }

    public static float miningToolSpeed(ItemStack stack, BlockState state) {
        float speed = stack.getDestroySpeed(state);
        if (speed <= 1) return speed;
        int efficiency = meteordevelopment.meteorclient.utils.Utils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
        return speed > 1 && efficiency > 0 ? speed + efficiency * efficiency + 1 : speed;
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
