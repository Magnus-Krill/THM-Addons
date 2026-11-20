/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(targets = "meteordevelopment.meteorclient.gui.WidgetScreen$WFullScreenRoot", remap = false)
public abstract class WidgetScreenRootMixin {
    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/gui/WidgetScreen$WFullScreenRoot;mouseMoved(DDDD)V"))
    private void thm$layoutMouse(Args args, GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        // Render coordinates already account for Retina and display scaling.
        args.setAll(mouseX, mouseY, mouseX, mouseY);
    }
}
