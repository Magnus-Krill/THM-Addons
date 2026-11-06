/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.ItemSignature;
import xyz.thm.addon.utils.InventoryManager;

import java.nio.file.*;
import java.util.*;

public class Loadouts extends Module {
    public static final String LOADOUTS_FILE = "meteor-client/loadouts.json";
    public final Setting<Boolean> quickLoadout = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("quick-loadout-buttons").description("Adds Save and Load buttons to your inventory.").defaultValue(true).build());
    private final Setting<Boolean> chatNotify = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("chat-notify").description("Reports saved and restored loadouts.").defaultValue(false).build());
    private final Setting<Boolean> regear = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("regear").description("Refills saved quantities from open containers.").defaultValue(false).build());
    private final Setting<Integer> actionsPerTick = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("actions-per-tick").description("Maximum inventory clicks per tick.").range(3, 64).sliderRange(3, 64).defaultValue(64).build());
    private final Setting<Integer> syncDelay = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("sync-delay").description("Ticks between inventory verification passes.").range(0, 20).sliderRange(0, 10).defaultValue(2).build());
    private final Map<String, Map<Integer, SavedSlot>> loadouts = new LinkedHashMap<>();
    private String activeLoadoutKey = "quicksave";
    private int cooldown, retries;
    private boolean readable;
    public boolean isSorted = true;

    public Loadouts() { super(THMAddon.PVP, "Loadouts", "Save and restore inventory slots, components and quantities."); }

    @Override public void onActivate() { read(); }
    @Override public void onDeactivate() { isSorted = true; cooldown = retries = 0; save(); }
    public boolean noLoadout(String name) { if (!readable) read(); return !loadouts.containsKey(name); }
    public void clearLoadouts() { if (!readable) read(); if (readable) { loadouts.clear(); save(); } }
    public void deleteLoadout(String name) { if (!readable) read(); if (readable) { loadouts.remove(name); save(); } }

    private void read() {
        if (mc.level == null) return;
        try {
            Path path = Path.of(LOADOUTS_FILE);
            String text = Files.exists(path) ? Files.readString(path) : "{}";
            JsonObject root = text.isBlank() ? new JsonObject() : JsonParser.parseString(text).getAsJsonObject();
            var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            Map<String, Map<Integer, SavedSlot>> parsed = new LinkedHashMap<>();
            for (var profile : root.entrySet()) {
                Map<Integer, SavedSlot> slots = new TreeMap<>();
                for (var entry : profile.getValue().getAsJsonObject().entrySet()) {
                    int slot = Integer.parseInt(entry.getKey());
                    if (slot < InventoryMenu.ARMOR_SLOT_START || slot > InventoryMenu.SHIELD_SLOT) throw new IllegalArgumentException("Invalid saved slot");
                    JsonElement value = entry.getValue();
                    if (value.isJsonPrimitive()) {
                        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(value.getAsString()));
                        ItemStack stack = new ItemStack(item);
                        if (stack.isEmpty()) throw new IllegalArgumentException("Unknown loadout item");
                        slots.put(slot, new SavedSlot(stack, false));
                    } else slots.put(slot, new SavedSlot(ItemStack.CODEC.parse(ops, value).getOrThrow(), true));
                }
                parsed.put(profile.getKey(), slots);
            }
            loadouts.clear(); loadouts.putAll(parsed); readable = true;
        } catch (Exception e) { readable = false; warning("Could not read loadouts; the file was preserved: %s", e.getMessage()); }
    }

    private void save() {
        if (!readable || mc.level == null) return;
        Path temporary = null;
        try {
            var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            JsonObject root = new JsonObject();
            for (var profile : loadouts.entrySet()) {
                JsonObject slots = new JsonObject();
                for (var entry : profile.getValue().entrySet()) {
                    SavedSlot saved = entry.getValue();
                    slots.add(entry.getKey().toString(), saved.precise
                        ? ItemStack.CODEC.encodeStart(ops, saved.stack).getOrThrow()
                        : new JsonPrimitive(BuiltInRegistries.ITEM.getKey(saved.stack.getItem()).toString()));
                }
                root.add(profile.getKey(), slots);
            }
            Path path = Path.of(LOADOUTS_FILE).toAbsolutePath();
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), "loadouts-", ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) { warning("Could not save loadouts: %s", e.getMessage()); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (Exception ignored) {} }
    }

    public void saveLoadout(String name) {
        if (mc.player == null || mc.player.containerMenu != mc.player.inventoryMenu) return;
        if (!readable) read();
        if (!readable) return;
        Map<Integer, SavedSlot> saved = new TreeMap<>();
        for (int slot = InventoryMenu.ARMOR_SLOT_START; slot <= InventoryMenu.SHIELD_SLOT; slot++) {
            ItemStack stack = mc.player.inventoryMenu.getSlot(slot).getItem();
            if (!stack.isEmpty()) saved.put(slot, new SavedSlot(stack.copy(), true));
        }
        loadouts.put(name, saved); save();
        if (chatNotify.get()) info("Saved loadout %s.", name);
    }

    public void loadLoadout(String name) {
        if (!readable) read();
        if (!loadouts.containsKey(name)) return;
        activeLoadoutKey = name; retries = cooldown = 0; isSorted = false;
    }

    @EventHandler private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || mc.level == null) return;
        if (cooldown > 0) { cooldown--; return; }
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (!menu.getCarried().isEmpty()) return;
        boolean ownInventory = menu == mc.player.inventoryMenu;
        if (ownInventory ? isSorted : !regear.get()) return;
        Map<Integer, SavedSlot> saved = loadouts.get(activeLoadoutKey);
        if (saved == null) return;
        int budget = InventoryManager.clickBudget(actionsPerTick.get());
        if (budget < 3) return;
        int initialBudget = budget;
        boolean missing = false;
        for (var entry : saved.entrySet()) {
            int destination = ownInventory ? entry.getKey() : containerSlot(menu, entry.getKey());
            if (destination < 0) continue;
            SavedSlot wanted = entry.getValue();
            ItemStack current = menu.getSlot(destination).getItem();
            if (wanted.matches(current) && current.getCount() >= wanted.count()) continue;
            missing = true;
            for (Slot source : menu.slots) {
                current = menu.getSlot(destination).getItem();
                if (wanted.matches(current) && current.getCount() >= wanted.count()) break;
                if (source.index == destination || !wanted.matches(source.getItem())) continue;
                if (!ownInventory && source.container == mc.player.getInventory()) continue;
                SavedSlot reserved = ownInventory ? saved.get(source.index) : null;
                int available = source.getItem().getCount();
                if (reserved != null && reserved.matches(source.getItem())) available -= reserved.count();
                if (available <= 0) continue;
                int needed = wanted.count() - (wanted.matches(current) ? current.getCount() : 0);
                int spent = move(menu, source, menu.getSlot(destination), Math.min(available, needed), budget);
                if (spent > 0) { budget -= spent; if (budget < 3) break; }
            }
            if (budget < 3) break;
        }
        cooldown = syncDelay.get();
        if (budget < initialBudget) { retries = 0; return; }
        if (!ownInventory) { cooldown = Math.max(10, cooldown); return; }
        if (missing && retries++ < 2) return;
        isSorted = true;
        if (missing) warning("Loadout %s is incomplete; matching supplies are missing.", activeLoadoutKey);
        else if (chatNotify.get()) info("Restored loadout %s.", activeLoadoutKey);
    }

    private int move(AbstractContainerMenu menu, Slot source, Slot target, int amount, int budget) {
        if (!source.mayPickup(mc.player) || !target.mayPlace(source.getItem()) || amount <= 0) return 0;
        ItemStack from = source.getItem(), to = target.getItem();
        if (!to.isEmpty() && !ItemStack.isSameItemSameComponents(from, to)) {
            if (budget < 3 || !source.mayPlace(to) || !target.mayPickup(mc.player)) return 0;
            click(menu, source.index, 0); click(menu, target.index, 0); click(menu, source.index, 0);
            return 3;
        }
        amount = Math.min(amount, target.getMaxStackSize(from) - to.getCount());
        if (amount <= 0) return 0;
        if (amount == from.getCount()) {
            if (budget < 2) return 0;
            click(menu, source.index, 0); click(menu, target.index, 0);
            return 2;
        }
        amount = Math.min(amount, budget - 2);
        if (amount <= 0) return 0;
        click(menu, source.index, 0);
        for (int i = 0; i < amount; i++) click(menu, target.index, 1);
        click(menu, source.index, 0);
        return amount + 2;
    }

    private void click(AbstractContainerMenu menu, int slot, int button) {
        mc.gameMode.handleContainerInput(menu.containerId, slot, button, ContainerInput.PICKUP, mc.player);
    }

    private int containerSlot(AbstractContainerMenu menu, int saved) {
        int inventorySlot = saved >= 36 && saved <= 44 ? saved - 36 : saved >= 9 && saved <= 35 ? saved : -1;
        if (inventorySlot < 0) return -1;
        for (Slot slot : menu.slots) if (slot.container == mc.player.getInventory() && slot.getContainerSlot() == inventorySlot) return slot.index;
        return -1;
    }

    public record SavedSlot(ItemStack stack, boolean precise) {
        public int count() { return precise ? Math.clamp(stack.getCount(), 1, stack.getMaxStackSize()) : 1; }
        public boolean matches(ItemStack actual) { return precise ? ItemSignature.matches(stack, actual) : actual.is(stack.getItem()); }
    }
}
