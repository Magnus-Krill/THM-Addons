/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.RenderUtilsTHM;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ShulkerOverview extends Module {
    private final Setting<Integer> size = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("icon-size").description("Size of the contents icon.").defaultValue(8).range(4, 12).sliderRange(4, 12).build());
    private final Map<ItemContainerContents, ItemStack> cache = new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ItemContainerContents, ItemStack> entry) { return size() > 256; }
    };
    private boolean rendering;

    public ShulkerOverview() { super(THMAddon.MAIN, "shulker-overview", "Shows the main contents on shulker icons."); }
    @Override public void onDeactivate() { cache.clear(); }
    @EventHandler private void onLeave(GameLeftEvent event) { cache.clear(); }

    public void render(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (rendering || !isActive() || !(stack.getItem() instanceof BlockItem block) || !(block.getBlock() instanceof ShulkerBoxBlock)) return;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return;
        ItemStack icon = cache.computeIfAbsent(contents, ShulkerOverview::dominantItem);
        if (icon.isEmpty()) return;
        rendering = true;
        try { RenderUtilsTHM.renderItemBadge(graphics, icon, x + 16 - size.get(), y + 16 - size.get(), size.get()); }
        finally { rendering = false; }
    }

    public static ItemStack dominantItem(ItemContainerContents contents) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        contents.nonEmptyItemCopyStream().forEach(stack -> counts.merge(stack.getItem(), stack.getCount(), Integer::sum));
        Item best = null;
        int most = 0;
        for (var entry : counts.entrySet()) if (entry.getValue() > most) { best = entry.getKey(); most = entry.getValue(); }
        return best == null ? ItemStack.EMPTY : new ItemStack(best);
    }
}
