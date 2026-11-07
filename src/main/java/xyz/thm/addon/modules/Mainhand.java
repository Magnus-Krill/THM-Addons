/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.DeepSwapManager;
import xyz.thm.addon.utils.InventoryManager;

import java.util.concurrent.TimeUnit;

/** Adapts Open Zio's Mainhand behavior to Meteor and the shared THM inventory managers. */
public class Mainhand extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup gapples = settings.createGroup("Gapple Bind");

    private final Setting<Integer> slot = general.add(new IntSetting.Builder().name("slot")
        .description("Hotbar slot to refill.").defaultValue(1).range(1, 9).sliderRange(1, 9).build());
    private final Setting<Integer> refillDelay = general.add(new IntSetting.Builder().name("refill-delay")
        .description("Minimum time between refills in milliseconds.").defaultValue(25).range(0, 500).sliderMax(500).build());
    private final Setting<Boolean> autoSelect = general.add(new BoolSetting.Builder().name("auto-select")
        .description("Select the totem slot when in danger.").defaultValue(false).build());
    private final Setting<Integer> selectDelay = general.add(new IntSetting.Builder().name("select-delay")
        .description("Minimum time between automatic selections in milliseconds.").defaultValue(25).range(0, 500).sliderMax(500)
        .visible(autoSelect::get).build());
    private final Setting<Double> selectHealth = general.add(new DoubleSetting.Builder().name("health")
        .description("Select the totem at or below this health.").defaultValue(8).range(0, 36).sliderMax(36)
        .visible(autoSelect::get).build());
    private final Setting<Boolean> lethal = general.add(new BoolSetting.Builder().name("lethal")
        .description("Select the totem if a nearby crystal could kill you.").defaultValue(true).visible(autoSelect::get).build());

    private final Setting<Boolean> gappleBind = gapples.add(new BoolSetting.Builder().name("gapple-bind")
        .description("Equip a golden apple while holding Use.").defaultValue(true).build());
    private final Setting<Double> safeHealth = gapples.add(new DoubleSetting.Builder().name("safe-health")
        .description("Minimum health for the gapple bind.").defaultValue(8).range(0, 36).sliderMax(36)
        .visible(gappleBind::get).build());
    private final Setting<Boolean> fallback = gapples.add(new BoolSetting.Builder().name("fallback")
        .description("Refill the slot with a golden apple when out of totems.").defaultValue(true).visible(gappleBind::get).build());
    private final Setting<Boolean> crapple = gapples.add(new BoolSetting.Builder().name("crapple")
        .description("Use normal golden apples.").defaultValue(false).visible(gappleBind::get).build());
    private final Setting<Boolean> hotbar = gapples.add(new BoolSetting.Builder().name("hotbar")
        .description("Temporarily select apples already in the hotbar.").defaultValue(false).visible(gappleBind::get).build());
    private final Setting<Boolean> weapon = gapples.add(new BoolSetting.Builder().name("weapon")
        .description("Use the hotbar bind while holding a sword or mace.").defaultValue(false)
        .visible(() -> gappleBind.get() && hotbar.get()).build());
    private final Setting<Boolean> totem = gapples.add(new BoolSetting.Builder().name("totem")
        .description("Use the hotbar bind while holding a totem.").defaultValue(false)
        .visible(() -> gappleBind.get() && hotbar.get()).build());

    private LocalPlayer player;
    private int previousSlot = -1, appleSlot = -1;
    private long nextRefill, nextSelect, lethalUntil;

    public Mainhand() {
        super(THMAddon.PVP, "mainhand", "Refills a hotbar slot with totems and equips golden apples on use.");
    }

    @Override
    public void onActivate() {
        player = null;
    }

    @Override
    public void onDeactivate() {
        if (player != null && player == mc.player && mc.getConnection() != null) restoreSelection();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) return;
        if (player != mc.player) {
            player = mc.player;
            previousSlot = appleSlot = -1;
            nextRefill = nextSelect = lethalUntil = Long.MIN_VALUE;
        }

        InventoryManager slots = InventoryManager.getInstance();
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (appleSlot >= 0 && selected != appleSlot) previousSlot = appleSlot = -1;
        if (!player.isAlive() || player.isSpectator() || player.getAbilities().instabuild
            || mc.gui.screen() != null || player.containerMenu != player.inventoryMenu
            || player.isUsingItem() || slots.isEating() || slots.getCurrentPriority() > InventoryManager.Priority.NORMAL) return;

        int configured = slot.get() - 1;
        DeepSwapManager swaps = DeepSwapManager.get();
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        float health = player.getHealth() + player.getAbsorptionAmount();
        if (autoSelect.get() && lethal.get() && health > selectHealth.get() && lethalCrystal(health)) lethalUntil = now + 750;
        boolean danger = autoSelect.get() && (health <= selectHealth.get() || lethal.get() && now < lethalUntil);
        Item apple = crapple.get() ? Items.GOLDEN_APPLE : Items.ENCHANTED_GOLDEN_APPLE;
        boolean bind = gappleBind.get() && !danger && health >= safeHealth.get() && canBindApple();

        if (appleSlot >= 0) {
            if (bind && hotbar.get() && inventory.getItem(appleSlot).is(apple)) return;
            if (!danger) {
                restoreSelection();
                return;
            }
        }
        if (swaps.reserved(configured)) {
            if (appleSlot >= 0) restoreSelection();
            return;
        }

        var held = player.getMainHandItem();
        if (bind && hotbar.get() && (weapon.get() && (held.is(ItemTags.SWORDS) || held.is(Items.MACE))
            || totem.get() && held.is(Items.TOTEM_OF_UNDYING))) {
            int source = find(apple, 0, 9, swaps);
            if (source >= 0 && slots.setClientSlot(source, InventoryManager.Priority.TOTEM)) {
                previousSlot = selected;
                appleSlot = source;
                return;
            }
        }

        boolean replaceWithApple = bind && selected == configured && (held.is(Items.TOTEM_OF_UNDYING) || held.is(apple));
        var configuredStack = inventory.getItem(configured);
        int totemSource = configuredStack.is(Items.TOTEM_OF_UNDYING) ? configured
            : findRefill(Items.TOTEM_OF_UNDYING, swaps);
        boolean hasTotem = totemSource >= 0 || held.is(Items.TOTEM_OF_UNDYING);
        Item desired = wantsApple(replaceWithApple, gappleBind.get() && fallback.get(), hasTotem) ? apple : Items.TOTEM_OF_UNDYING;
        if (!configuredStack.is(desired) && now >= nextRefill) {
            int source = desired == apple ? findRefill(apple, swaps) : totemSource;
            if (source >= 0 && swaps.swapIntoHotbar(source, configured)) nextRefill = now + refillDelay.get();
        }

        if (danger && inventory.getItem(configured).is(Items.TOTEM_OF_UNDYING) && (selected == configured || now >= nextSelect)) {
            if (slots.setClientSlot(configured, InventoryManager.Priority.TOTEM)) {
                previousSlot = appleSlot = -1;
                if (selected != configured) nextSelect = now + selectDelay.get();
            }
        } else if (danger && appleSlot >= 0) restoreSelection();
    }

    static boolean wantsApple(boolean bind, boolean fallback, boolean hasTotem) {
        return bind || fallback && !hasTotem;
    }

    private boolean canBindApple() {
        return mc.options.keyUse.isDown() && !player.getOffhandItem().has(DataComponents.FOOD)
            && (player.isShiftKeyDown() || !(mc.hitResult instanceof BlockHitResult hit)
                || !BlockUtils.isClickable(mc.level.getBlockState(hit.getBlockPos()).getBlock()));
    }

    private boolean lethalCrystal(float health) {
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal) || !crystal.isAlive() || player.distanceToSqr(crystal) > 144) continue;
            if (DamageUtils.crystalDamage(player, crystal.position()) >= health
                || DamageUtils.crystalDamage(player, crystal.position(), true, null) >= health) return true;
        }
        return false;
    }

    private int findRefill(Item item, DeepSwapManager swaps) {
        int source = find(item, 9, 36, swaps);
        return source >= 0 ? source : find(item, 0, 9, swaps);
    }

    private int find(Item item, int start, int end, DeepSwapManager swaps) {
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        for (int i = start; i < end; i++) {
            if (i != selected && inventory.getItem(i).is(item) && !swaps.reserved(i)) return i;
        }
        return -1;
    }

    private void restoreSelection() {
        if (appleSlot >= 0 && player.getInventory().getSelectedSlot() == appleSlot) {
            if (!InventoryManager.getInstance().setClientSlot(previousSlot, InventoryManager.Priority.TOTEM)) return;
        }
        previousSlot = appleSlot = -1;
    }
}
