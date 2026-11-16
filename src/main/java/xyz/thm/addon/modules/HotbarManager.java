/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.screens.settings.ItemSettingScreen;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WItem;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.DeepSwapManager;
import xyz.thm.addon.utils.InventoryManager;

public class HotbarManager extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup hotbar = settings.createGroup("Hotbar");

    private final Setting<Integer> delay = general.add(new IntSetting.Builder().name("move-delay")
        .description("Ticks between item moves.").defaultValue(1).range(1, 20).sliderRange(1, 20).build());
    private final Setting<Boolean> replace = general.add(new BoolSetting.Builder().name("replace")
        .description("Replace items in occupied hotbar slots.").defaultValue(true).build());
    private final Setting<Boolean> onePass = general.add(new BoolSetting.Builder().name("toggle")
        .description("Turn off after sorting the hotbar.").defaultValue(false).build());

    private final SlotSetting[] slots = new SlotSetting[9];
    private double ticksLeft;

    static {
        SettingsWidgetFactory.registerCustomFactory(SlotSetting.class, theme -> (table, setting) ->
            fillSlotRow(theme, table, (SlotSetting) setting));
    }

    public HotbarManager() {
        super(THMAddon.MAIN, "hotbar-manager", "Keeps chosen items in your hotbar slots.");
        Item[] defaults = { Items.AIR, Items.AIR, Items.AIR, Items.AIR, Items.ENCHANTED_GOLDEN_APPLE,
            Items.NETHERRACK, Items.ENDER_CHEST, Items.NETHERITE_PICKAXE, Items.OBSIDIAN };
        for (int i = 0; i < slots.length; i++) slots[i] = hotbar.add(new SlotSetting(i + 1, defaults[i]));
    }

    public static HotbarManager active() {
        HotbarManager manager = Modules.get().get(HotbarManager.class);
        return manager != null && manager.isActive() ? manager : null;
    }

    public boolean managesSlot(int slot) {
        return Inventory.isHotbarSlot(slot) && slots[slot].get() != Items.AIR;
    }

    int findManagedSlot(Item item) {
        for (int i = 0; i < slots.length; i++) {
            if (managesSlot(i) && slots[i].get() == item) return i;
        }
        return -1;
    }

    @Override
    public void onActivate() {
        ticksLeft = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !mc.player.isAlive() || mc.player.isSpectator() || mc.gui.screen() != null
            || mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.containerMenu.getCarried().isEmpty()
            || mc.player.isUsingItem() || InventoryManager.getInstance().getCurrentPriority() > InventoryManager.Priority.NORMAL) return;

        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder != null && builder.ownsRestockInventory()) return;
        boolean building = builder != null && builder.isActive() && builder.manageHotbar.get();
        if ((ticksLeft -= TickRate.INSTANCE.getTickRate() / 20.0) > 0) return;

        Inventory inventory = mc.player.getInventory();
        DeepSwapManager swaps = DeepSwapManager.get();
        boolean pending = false;
        for (int target = 0; target < slots.length; target++) {
            if (!managesSlot(target)) continue;
            Item item = slots[target].get();
            ItemStack current = inventory.getItem(target);
            if (current.is(item) || !current.isEmpty() && !replace.get()) continue;

            int source = HighwayBuilderTHM.selectSupplySlot(36, i -> {
                ItemStack stack = inventory.getItem(i);
                return stack.is(item) && (!managesSlot(i) || slots[i].get() != item)
                    && (!building || builder.canSortHotbarStack(stack));
            }, swaps::temporarilyReserved);
            if (source == -1) continue;

            pending = true;
            if (swaps.swapManagedHotbar(source, target)) {
                ticksLeft = delay.get();
                return;
            }
        }
        if (!pending && onePass.get()) toggle();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton reset = theme.button("Reset Hotbar");
        reset.action = () -> {
            for (SlotSetting slot : slots) slot.reset();
            if (mc.gui.screen() instanceof WidgetScreen screen) screen.reload();
        };
        return reset;
    }

    private static void fillSlotRow(GuiTheme theme, WTable table, SlotSetting setting) {
        var list = table.add(theme.horizontalList()).expandX().widget();
        WItem icon = list.add(theme.item(setting.get().getDefaultInstance())).widget();
        Runnable refresh = () -> icon.set(setting.get().getDefaultInstance());

        list.add(theme.button("Select")).widget().action = () -> {
            var screen = new ItemSettingScreen(theme, setting);
            screen.onClosed(refresh);
            MeteorClient.mc.gui.setScreen(screen);
        };
        list.add(theme.button("Clear")).widget().action = () -> {
            setting.set(Items.AIR);
            refresh.run();
        };
        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.tooltip = "Reset";
        reset.action = () -> {
            setting.reset();
            refresh.run();
        };
    }

    private static class SlotSetting extends ItemSetting {
        SlotSetting(int slot, Item defaultValue) {
            super("slot-" + slot, "Item for hotbar slot " + slot + ".", defaultValue, null, null, null, null);
        }

        @Override
        public Item load(CompoundTag tag) {
            if (tag.getList("value").isPresent()) {
                Item item = parseImpl(tag.getListOrEmpty("value").getStringOr(0, "minecraft:air"));
                value = item == null ? Items.AIR : item;
                return get();
            }
            return super.load(tag);
        }
    }
}
