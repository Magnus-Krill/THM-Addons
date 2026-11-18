/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

// BepHax 0.5.5 theme by Dekto, adapted for Meteor 26.2.
// https://bep.dek.to/changelog 
//To Dekto, if you want this removed then open a issue and ill remove it

package xyz.thm.addon.gui.themes.bephax.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedButton;
import meteordevelopment.meteorclient.utils.render.color.Color;

import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepConfirmedButton extends WConfirmedButton implements BepWidget {
    public WBepConfirmedButton(String text, String confirmText, GuiTexture texture) {
        super(text, confirmText, texture);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        BepGuiTheme theme = this.theme();
        double pad = this.pad();
        Color outline = this.pressedOnce
                                ? theme.minusColor.get()
                                : theme.outlineColor.get(this.pressed, this.mouseOver);
        Color fg = this.pressedOnce ? theme.titleTextColor.get() : theme.textColor.get();
        Color bg = this.pressedOnce
                                ? theme.minusColor.get()
                                : theme.widgetBackground.get(this.pressed, this.mouseOver);
        this.renderBackground(renderer, this, outline, bg);
        String text = this.getText();
        if (text != null) {
            renderer.text(
                    text, this.x + this.width / 2 - this.textWidth / 2, this.y + pad, fg, false);
        } else {
            double ts = theme.textHeight();
            renderer.quad(this.x + this.width / 2 - ts / 2, this.y + pad, ts, ts, this.texture, fg);
        }
    }
}
