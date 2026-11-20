/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorWidget;
import meteordevelopment.meteorclient.gui.utils.BaseWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.ThmChrome;
import xyz.thm.addon.gui.themes.ThmTheme;

/**
 * Restyles every Meteor widget that draws itself through MeteorWidget#renderBackground (buttons,
 * checkboxes, textboxes, dropdowns, plus/minus, ...) into the THM main-menu window chrome - flat
 * translucent body + purple gradient border - but only while the active theme is ThmTheme, so
 * every other Meteor theme is left exactly as-is.
 *
 * Interface mixin: the drawing lives in the interface's default methods, so this targets those
 * directly. The boolean overload (pressed/mouseOver) draws pressables with the window's brighter
 * button fill; the Color overload catches the widgets (plus/minus) that pass their own fill.
 * Cancelling the boolean overload stops it from calling the Color overload, so nothing double-draws.
 */
@Mixin(value = MeteorWidget.class, remap = false)
public interface ThmMeteorWidgetMixin extends BaseWidget {
    @Inject(method = "renderBackground(Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;Lmeteordevelopment/meteorclient/gui/widgets/WWidget;ZZ)V", at = @At("HEAD"), cancellable = true)
    private void thm$stateBackground(GuiRenderer renderer, WWidget widget, boolean pressed, boolean mouseOver, CallbackInfo ci) {
        GuiTheme theme = getTheme();
        if (!(theme instanceof ThmTheme meteor)) return;

        double s = meteor.scale(2);
        Color fill = widget instanceof WPressable
            ? (mouseOver ? ThmChrome.buttonFillHover() : ThmChrome.buttonFill())
            : meteor.backgroundColor.get(pressed, mouseOver);

        ThmChrome.panel(renderer, widget.x, widget.y, widget.width, widget.height, s, fill);
        ci.cancel();
    }

    @Inject(method = "renderBackground(Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;Lmeteordevelopment/meteorclient/gui/widgets/WWidget;Lmeteordevelopment/meteorclient/utils/render/color/Color;Lmeteordevelopment/meteorclient/utils/render/color/Color;)V", at = @At("HEAD"), cancellable = true)
    private void thm$colorBackground(GuiRenderer renderer, WWidget widget, Color outlineColor, Color backgroundColor, CallbackInfo ci) {
        GuiTheme theme = getTheme();
        if (!(theme instanceof MeteorGuiTheme meteor) || !(theme instanceof ThmTheme)) return;

        double s = meteor.scale(2);
        ThmChrome.panel(renderer, widget.x, widget.y, widget.width, widget.height, s, backgroundColor);
        ci.cancel();
    }
}
