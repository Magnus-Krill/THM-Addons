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
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepButton extends WButton implements BepWidget {
    public WBepButton(String text, GuiTexture texture) {
        super(text, texture);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        BepGuiTheme theme = this.theme();
        double pad = this.pad();
        this.renderBackground(renderer, this, this.pressed, this.mouseOver);
        if (this.text != null) {
            renderer.text(
                    this.text,
                    this.x + this.width / 2 - this.textWidth / 2,
                    this.y + pad,
                    theme.textColor.get(),
                    false);
        } else {
            double ts = theme.textHeight();
            renderer.quad(
                    this.x + this.width / 2 - ts / 2,
                    this.y + pad,
                    ts,
                    ts,
                    this.texture,
                    theme.textColor.get());
        }
    }
}
