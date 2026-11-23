/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.mixin;

import com.mojang.patchy.BlockedServers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockedServers.class)
public class BlockedServersMixin {

    @Inject(method = "isBlockedServerHostName", at = @At("RETURN"), cancellable = true, remap = false)
    public void isBlockedServerHostName(String server, CallbackInfoReturnable<Boolean> cir) {
            cir.setReturnValue(false);
    }
}
