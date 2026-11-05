/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.*;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.systems.modules.combat.Offhand;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = AutoTotem.class, remap = false)
public abstract class AutoTotemMixin {
    @Shadow public boolean locked;
    @Unique private Setting<Boolean> thm$atomicSwap, thm$popRefill;
    @Unique private int thm$popUntil;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$settings(CallbackInfo ci) {
        var group = ((AutoTotem) (Object) this).settings.createGroup("THM Swaps");
        thm$atomicSwap = group.add(new BoolSetting.Builder().name("atomic-swap")
            .description("Equips totems with one offhand swap click.").defaultValue(true).build());
        thm$popRefill = group.add(new BoolSetting.Builder().name("instant-pop-refill")
            .description("Refills after a pop without the tick delay.").defaultValue(false).build());
    }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void thm$filter(TickEvent.Pre event, CallbackInfo ci) {
        if (!thm$allowed()) { locked = false; thm$popUntil = 0; ci.cancel(); }
    }

    @WrapOperation(method = "onTick", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/utils/player/InvUtils$Action;toOffhand()V"))
    private void thm$atomicSwap(InvUtils.Action action, Operation<Void> original, @Local FindItemResult result) {
        if (thm$atomicSwap.get()) thm$swap(result.slot());
        else original.call(action);
    }

    @Unique private boolean thm$allowed() {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) return false;
        Offhand offhand = Modules.get().get(Offhand.class);
        if (offhand == null || !offhand.isActive()) return true;
        var filter = offhand.settings.get("held-item-filter");
        var items = offhand.settings.get("held-items");
        if (filter == null || items == null || !(filter.get() instanceof OffhandMixin.HeldItemFilter mode)
            || !(items.get() instanceof java.util.List<?> list)) return true;
        return mode.allows(list.contains(mc.player.getMainHandItem().getItem()));
    }

    @Unique private boolean thm$swap(int source) {
        if (!thm$allowed() || source < 0 || source >= 36 || !mc.player.containerMenu.getCarried().isEmpty()
            || xyz.thm.addon.utils.InventoryManager.clickBudget(1) == 0
            || mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) return false;
        for (var slot : mc.player.containerMenu.slots) {
            if (slot.container != mc.player.getInventory() || slot.getContainerSlot() != source) continue;
            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot.index, 40, ContainerInput.SWAP, mc.player);
            return mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        }
        return false;
    }

    @Unique @EventHandler
    private void thm$pop(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundEntityEventPacket packet) || packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) return;
        var connection = event.connection;
        mc.execute(() -> {
            if (!thm$popRefill.get() || !((AutoTotem) (Object) this).isActive() || !thm$allowed()
                || mc.getConnection().getConnection() != connection || packet.getEntity(mc.level) != mc.player) return;
            thm$popUntil = mc.player.tickCount + 20;
        });
    }

    @Unique @EventHandler
    private void thm$refill(TickEvent.Post event) {
        if (mc.player == null || thm$popUntil == 0 || mc.player.tickCount > thm$popUntil || !thm$popRefill.get() || !locked || !thm$allowed()) return;
        if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING) && thm$swap(i)) { thm$popUntil = 0; break; }
        }
    }

    @Unique @EventHandler
    private void thm$leave(meteordevelopment.meteorclient.events.game.GameLeftEvent event) { thm$popUntil = 0; }
}
