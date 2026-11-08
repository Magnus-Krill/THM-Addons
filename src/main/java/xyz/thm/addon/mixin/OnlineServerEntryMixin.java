/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.accessor.ServerFolderView;
import xyz.thm.addon.system.ServerFolders;
import xyz.thm.addon.system.THMSystem;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class OnlineServerEntryMixin implements ServerFolderView.EntryIcon {
    @Shadow @Final private JoinMultiplayerScreen screen;
    @Shadow @Final private FaviconTexture icon;

    @Override
    public Identifier thm$iconLocation() {
        return icon.textureLocation();
    }

    @ModifyExpressionValue(method = {"extractContent", "getNarration"}, at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/multiplayer/ServerData;name:Ljava/lang/String;"))
    private String thm$displayAlias(String name) {
        THMSystem system = THMSystem.get();
        if (system == null) return name;
        var entry = (ServerSelectionList.OnlineServerEntry) (Object) this;
        String alias = system.serverFolders.details(entry.getServerData().ip).alias();
        return alias.isEmpty() ? name : alias;
    }

    @Inject(method = "swap", at = @At("HEAD"), cancellable = true)
    private void thm$swapVisibleServers(int oldIndex, int newIndex, CallbackInfo ci) {
        var view = (ServerFolderView) screen;
        if (view.thm$folder().equals(ServerFolders.ALL) && view.thm$serverQuery().isEmpty()) return;
        ci.cancel();
        ServerSelectionList list = view.thm$serverList();
        if (newIndex < 0 || newIndex >= list.children().size()
            || !(list.children().get(newIndex) instanceof ServerSelectionList.OnlineServerEntry other)) return;
        var entry = (ServerSelectionList.OnlineServerEntry) (Object) this;
        var servers = screen.getServers();
        int from = -1;
        int to = -1;
        for (int i = 0; i < servers.size(); i++) {
            var server = servers.get(i);
            if (server == entry.getServerData()) from = i;
            if (server == other.getServerData()) to = i;
        }
        // Visible row indices differ from servers.dat indices in a filtered list.
        if (from >= 0 && to >= 0) {
            servers.swap(from, to);
            list.updateOnlineServers(servers);
        }
    }
}
