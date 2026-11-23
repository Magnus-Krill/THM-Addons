/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.mixin.meteor;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WRoot;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import xyz.thm.addon.gui.ClickGuiAnimation;

@Mixin(value = WWindow.class, remap = false)
public abstract class WindowAnimationMixin {
    @Shadow protected double animProgress;

    @WrapMethod(method = "render")
    private boolean thm$unfoldPanelHeader(GuiRenderer renderer, double mouseX, double mouseY,
                                         double delta, Operation<Boolean> original) {
        WWindow window = (WWindow) (Object) this;
        WidgetScreen screen = ClickGuiAnimation.transitioningScreen();
        if (screen == null || ClickGuiAnimation.forScreen(screen) != ClickGuiAnimation.Unfold
            || window.id == null || window.parent == null || !(window.parent.parent instanceof WRoot)) {
            return original.call(renderer, mouseX, mouseY, delta);
        }

        double saved = animProgress;
        animProgress *= ClickGuiAnimation.Unfold.panelProgress(screen.animProgress, 0, 0);
        try {
            return original.call(renderer, mouseX, mouseY, 0.0);
        } finally {
            animProgress = saved;
        }
    }
}
