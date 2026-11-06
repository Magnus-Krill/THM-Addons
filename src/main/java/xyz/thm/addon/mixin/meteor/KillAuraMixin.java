/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ThmMembers;
import xyz.thm.addon.utils.SwapManager;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(value = KillAura.class, remap = false)
public abstract class KillAuraMixin {
    @WrapOperation(method = "attack", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V"))
    private void thm$borrowWeapon(MultiPlayerGameMode gameMode, Player player, Entity target, Operation<Void> original) {
        SwapManager.get().withSlot(this, player.getInventory().getSelectedSlot(), SwapManager.WEAPON, true,
            () -> original.call(gameMode, player, target));
    }

    @Inject(method = "entityCheck", at = @At("HEAD"), cancellable = true)
    private void thm$ignoreThmMembers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player player)) return;

        THMSystem system = THMSystem.get();
        if (system != null && system.ignoreThmMembers.get() && ThmMembers.isThmMember(player)) cir.setReturnValue(false);
    }
}
