/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







// BepHax 0.5.5 theme by Dekto, adapted for Meteor 26.2.
// https://bep.dek.to/changelog 
//To Dekto, if you want this removed then open a issue and ill remove it

package xyz.thm.addon.gui.themes.bephax.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedMinus;
import meteordevelopment.meteorclient.utils.render.color.Color;

import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepConfirmedMinus extends WConfirmedMinus implements BepWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        BepGuiTheme theme = this.theme();
        double inset = this.pad() + theme.scale(1);
        double s = theme.scale(2);
        Color outline = this.pressedOnce
                                ? theme.minusColor.get()
                                : theme.outlineColor.get(this.pressed, this.mouseOver);
        Color fg = this.pressedOnce ? theme.titleTextColor.get() : theme.minusColor.get();
        Color bg = this.pressedOnce
                                ? theme.minusColor.get()
                                : theme.widgetBackground.get(this.pressed, this.mouseOver);
        this.renderBackground(renderer, this, outline, bg);
        renderer.quad(
                this.x + inset, this.y + this.height / 2 - s / 2, this.width - inset * 2, s, fg);
    }
}
