/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds an "Open verification link" button to 6b6t's anti-bot kick. Adapted from Powie69's 6Bees (GPL-3.0). */
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin {
    @Shadow @Final private DisconnectionDetails details;
    @Shadow @Final private LinearLayout layout;

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/LinearLayout;arrangeElements()V"))
    private void thm$verifyButton(CallbackInfo ci) {
        String reason = details.reason().getString();
        if (!reason.startsWith("6b6t Anti Bot Protection")) return;
        Matcher m = Pattern.compile("https://verify\\.6b6t\\.org/[^\\s`)\\]]+").matcher(reason);
        if (!m.find()) return;
        layout.addChild(Button.builder(Component.literal("Open verification link"),
            ConfirmLinkScreen.confirmLink((DisconnectedScreen) (Object) this, m.group(), false)).width(200).build());
    }
}
