/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.THMUtils;
import xyz.thm.addon.utils.server.JoinPayload;

// Adapted from 6b6t/AnarchyMod (MIT); see licenses/anarchymod.txt.
@Mixin(ClientPacketListener.class)
public abstract class AnarchyJoinMixin {
    @Inject(method = "handleLogin", at = @At("RETURN"))
    private void thm$notifyAnarchyJoin(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (FabricLoader.getInstance().isModLoaded("anarchymod")) return;
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server == null || !THMUtils.isAnarchyServer(server.ip)) return;

        try {
            ((ClientPacketListener) (Object) this).getConnection().send(JoinPayload.createPacket());
            THMAddon.LOG.info("[THM/Join] Sent anarchymod:join.");
        } catch (RuntimeException error) {
            THMAddon.LOG.warn("[THM/Join] Failed to send anarchymod:join.", error);
        }
    }
}
