/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.Offhand;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.settings.DescribedOption;
import xyz.thm.addon.utils.InventoryManager;

import java.util.List;

/**
 * Offhand's swap is a two-click {@code PICKUP} pair, which only behaves if the cursor is empty when
 * it starts and gets emptied again when it ends. Meteor guarantees neither: with something already
 * on the cursor the first click *drops* it into the source slot instead of picking up (wrong item in
 * the wrong slot, old offhand item stranded on the cursor), and its own recovery in
 * {@code InvUtils.Action#run} is gated on the cursor having started empty, so that case is never
 * cleaned up. This clears the cursor on both ends instead of leaving it stuck.
 */
@Mixin(value = Offhand.class, remap = false)
public class OffhandMixin {
    @Shadow @Final private SettingGroup sgCombat;
    @Shadow public boolean locked;
    @Unique private Setting<HeldItemFilter> thm$heldItemFilter;
    @Unique private Setting<List<Item>> thm$heldItems;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$addHeldItemFilter(CallbackInfo ci) {
        thm$heldItemFilter = sgCombat.add(new EnumSetting.Builder<HeldItemFilter>()
            .name("held-item-filter")
            .description("Filters swaps by the main-hand item.")
            .defaultValue(HeldItemFilter.Off)
            .build());
        thm$heldItems = sgCombat.add(new ItemListSetting.Builder()
            .name("held-items")
            .description("Main-hand items matched by the filter.")
            .visible(() -> thm$heldItemFilter.get() != HeldItemFilter.Off)
            .build());
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void thm$clearCursorBefore(TickEvent.Pre event, CallbackInfo ci) {
        if (MeteorClient.mc.player == null) {
            ci.cancel();
            return;
        }
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder != null && builder.isActive() && builder.noSwapLoadout.get()) {
            ci.cancel();
            return;
        }
        if (thm$heldItemFilter.get() != HeldItemFilter.Off
            && !thm$heldItemFilter.get().allows(thm$heldItems.get().contains(MeteorClient.mc.player.getMainHandItem().getItem()))) {
            locked = false;
            ci.cancel();
            return;
        }
        if (MeteorClient.mc.player.containerMenu.getCarried().isEmpty()) return;

        // With a screen open the player is dragging that stack — theirs, don't touch it, just sit out.
        // Otherwise it is a leftover from a desynced swap: park it so the module isn't stuck forever.
        if (MeteorClient.mc.gui.screen() != null || !InventoryManager.parkCursor()) ci.cancel();
    }

    @Inject(method = "onTick", at = @At("RETURN"))
    private void thm$clearCursorAfter(TickEvent.Pre event, CallbackInfo ci) {
        // Cursor was empty above, so anything on it now is the swap's own leftover. Never ours to drop:
        // a failed park just leaves it for the next tick's HEAD to retry.
        if (!MeteorClient.mc.player.containerMenu.getCarried().isEmpty()) InventoryManager.parkCursor();
    }

    public enum HeldItemFilter implements DescribedOption {
        Off("Off", "Allows swaps with any main-hand item."),
        OnlyWhenHolding("Only when holding", "Allows swaps only while holding a listed item."),
        OnlyWhenNotHolding("Only when not holding", "Blocks swaps while holding a listed item.");

        private final String label, description;

        HeldItemFilter(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public boolean allows(boolean listed) {
            return switch (this) {
                case Off -> true;
                case OnlyWhenHolding -> listed;
                case OnlyWhenNotHolding -> !listed;
            };
        }

        @Override public String toString() { return label; }
        @Override public String description() { return description; }
    }
}
