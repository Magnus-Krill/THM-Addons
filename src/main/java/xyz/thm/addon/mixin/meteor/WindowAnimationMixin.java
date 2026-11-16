/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WRoot;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.utils.Utils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import xyz.thm.addon.gui.ClickGuiAnimation;

@Mixin(value = WWindow.class, remap = false)
public abstract class WindowAnimationMixin {
    @Shadow protected double animProgress;

    @WrapMethod(method = "render")
    private boolean thm$animatePanel(GuiRenderer renderer, double mouseX, double mouseY,
                                     double delta, Operation<Boolean> original) {
        WWindow window = (WWindow) (Object) this;
        WidgetScreen screen = ClickGuiAnimation.transitioningScreen();
        if (screen == null || window.id == null || window.parent == null || !(window.parent.parent instanceof WRoot)) {
            return original.call(renderer, mouseX, mouseY, delta);
        }

        ClickGuiAnimation animation = ClickGuiAnimation.forScreen(screen);
        if (animation == ClickGuiAnimation.Fade) return original.call(renderer, mouseX, mouseY, delta);

        int width = Utils.getWindowWidth();
        int height = Utils.getWindowHeight();
        double progress = animation.panelProgress(screen.animProgress, window.x, width);
        if (animation == ClickGuiAnimation.Unfold) {
            double saved = animProgress;
            animProgress *= progress;
            try {
                return original.call(renderer, -1.0, -1.0, 0.0);
            } finally {
                animProgress = saved;
            }
        }

        int seed = animation == ClickGuiAnimation.Scatter
            ? ClickGuiAnimation.scatterPanelSeed(((ClickGuiAnimation.ScatterSeed) screen).thm$scatterSeed(), window.id)
            : 0;
        long dx = Math.round((animation.startX(window.x, window.width, width, seed) - window.x) * (1 - progress));
        long dy = Math.round((animation.startY(window.y, window.height, height, seed) - window.y) * (1 - progress));
        if (dx == 0 && dy == 0) return original.call(renderer, mouseX, mouseY, delta);

        if (window.x + dx >= width || window.x + dx + window.width <= 0
            || window.y + dy >= height || window.y + dy + window.height <= 0) return true;

        window.move(dx, dy);
        // Flush queued icons before restoring widget coordinates.
        try {
            renderer.scissorStart(0, 0, width, height);
            try {
                return original.call(renderer, -1.0, -1.0, delta);
            } finally {
                renderer.scissorEnd();
            }
        } finally {
            window.move(-dx, -dy);
        }
    }
}
