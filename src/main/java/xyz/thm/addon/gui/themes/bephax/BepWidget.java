/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







// BepHax 0.5.5 theme by Dekto, adapted for Meteor 26.2.
// https://bep.dek.to/changelog 
//To Dekto, if you want this removed then open a issue and ill remove it

package xyz.thm.addon.gui.themes.bephax;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.BaseWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public interface BepWidget extends BaseWidget {
    default BepGuiTheme theme() {
        return (BepGuiTheme) this.getTheme();
    }

    default void renderBackground(
            GuiRenderer renderer, WWidget widget, Color outlineColor, Color backgroundColor) {
        BepGuiTheme theme = this.theme();
        double radius = theme.radius();
        BepRender.fill(
                renderer, widget.x, widget.y, widget.width, widget.height, radius, backgroundColor);
        BepRender.outline(
                renderer,
                widget.x,
                widget.y,
                widget.width,
                widget.height,
                radius,
                theme.scale(1),
                outlineColor);
    }

    default void renderBackground(
            GuiRenderer renderer, WWidget widget, boolean pressed, boolean mouseOver) {
        BepGuiTheme theme = this.theme();
        this.renderBackground(
                renderer,
                widget,
                theme.outlineColor.get(pressed, mouseOver),
                theme.widgetBackground.get(pressed, mouseOver));
    }
}
