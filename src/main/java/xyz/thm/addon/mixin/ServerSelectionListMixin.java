/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.accessor.ServerFolderView;
import xyz.thm.addon.system.THMSystem;

import java.util.Collection;

@Mixin(ServerSelectionList.class)
public abstract class ServerSelectionListMixin {
    @Shadow @Final private JoinMultiplayerScreen screen;

    @ModifyArg(method = "refreshEntries", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/multiplayer/ServerSelectionList;replaceEntries(Ljava/util/Collection;)V"))
    private Collection<ServerSelectionList.Entry> thm$filterServers(Collection<ServerSelectionList.Entry> entries) {
        THMSystem system = THMSystem.get();
        if (system == null) return entries;
        var view = (ServerFolderView) screen;
        return entries.stream().filter(entry -> !(entry instanceof ServerSelectionList.OnlineServerEntry online)
            || system.serverFolders.shows(online.getServerData().ip, online.getServerData().name,
                view.thm$folder(), view.thm$serverQuery())).toList();
    }

    @Inject(method = "getRowWidth", at = @At("RETURN"), cancellable = true)
    private void thm$fitServerRows(CallbackInfoReturnable<Integer> cir) {
        ServerSelectionList list = (ServerSelectionList) (Object) this;
        cir.setReturnValue(Math.max(1, Math.min(cir.getReturnValueI(), list.getWidth() - 12)));
    }
}
