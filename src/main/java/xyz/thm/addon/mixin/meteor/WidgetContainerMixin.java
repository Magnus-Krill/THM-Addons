/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WRoot;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.utils.Utils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import xyz.thm.addon.gui.ClickGuiAnimation;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = WContainer.class, remap = false)
public abstract class WidgetContainerMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/gui/widgets/containers/WContainer;renderWidget(Lmeteordevelopment/meteorclient/gui/widgets/WWidget;Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;DDD)V"))
    private void thm$animatePanel(WContainer container, WWidget widget, GuiRenderer renderer, double mouseX, double mouseY,
                                  double delta, Operation<Void> original) {
        if (!(widget instanceof WWindow window) || window.id == null || !(container.parent instanceof WRoot)) {
            original.call(container, widget, renderer, mouseX, mouseY, delta);
            return;
        }
        WidgetScreen screen = ClickGuiAnimation.transitioningScreen();
        ClickGuiAnimation animation = screen == null ? ClickGuiAnimation.Default : ClickGuiAnimation.forScreen(screen);
        if (animation == ClickGuiAnimation.Default || animation == ClickGuiAnimation.Fade) {
            original.call(container, widget, renderer, mouseX, mouseY, delta);
            return;
        }

        int width = Utils.getWindowWidth();
        int height = Utils.getWindowHeight();
        double progress = animation.panelProgress(screen.animProgress, window.x, width);
        if (animation == ClickGuiAnimation.Unfold) {
            double headerHeight = window.cells.getFirst().widget().height;
            double bottom = window.y + headerHeight + (window.height - headerHeight) * progress;
            renderer.scissorStart(0, 0, width, Math.max(0, bottom));
            try {
                original.call(container, widget, renderer, -1.0, -1.0, delta);
            } finally {
                renderer.scissorEnd();
            }
            return;
        }

        int seed = animation == ClickGuiAnimation.Scatter
            ? ClickGuiAnimation.scatterPanelSeed(((ClickGuiAnimation.ScatterSeed) screen).thm$scatterSeed(), window.id)
            : 0;
        long dx = Math.round((animation.startX(window.x, window.width, width, seed) - window.x) * (1 - progress));
        long dy = Math.round((animation.startY(window.y, window.height, height, seed) - window.y) * (1 - progress));
        if (window.x + dx >= width || window.x + dx + window.width <= 0
            || window.y + dy >= height || window.y + dy + window.height <= 0) return;

        // Move before dispatch so theme-specific backgrounds follow the panel.
        window.move(dx, dy);
        try {
            renderer.scissorStart(0, 0, width, height);
            try {
                original.call(container, widget, renderer, -1.0, -1.0, delta);
            } finally {
                renderer.scissorEnd();
            }
        } finally {
            window.move(-dx, -dy);
        }
    }

    @ModifyArgs(method = "moveCells", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/gui/widgets/WWidget;mouseMoved(DDDD)V"))
    private void thm$scrollMouse(Args args) {
        var window = mc.getWindow();
        double mouseX = mc.mouseHandler.getScaledXPos(window) * window.getGuiScale();
        double mouseY = mc.mouseHandler.getScaledYPos(window) * window.getGuiScale();
        // Scrolling must use the same coordinates as WidgetScreen input.
        args.setAll(mouseX, mouseY, mouseX, mouseY);
    }
}
