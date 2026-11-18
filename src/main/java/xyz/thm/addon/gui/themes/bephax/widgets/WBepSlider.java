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
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;

import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepRender;
import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepSlider extends WSlider implements BepWidget {
    public WBepSlider(double value, double min, double max) {
        super(value, min, max);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        BepGuiTheme theme = this.theme();
        double valueWidth = this.valueWidth();
        double handle = this.handleSize();
        double th = theme.scale(4);
        double tx = this.x + handle / 2;
        double ty = this.y + this.height / 2 - th / 2;
        double tw = this.width - handle;
        BepRender.fill(renderer, tx, ty, tw, th, th / 2, theme.sliderRight.get());
        if (valueWidth > 0) {
            BepRender.fill(
                    renderer,
                    tx,
                    ty,
                    Math.min(valueWidth + th / 2, tw),
                    th,
                    th / 2,
                    theme.sliderLeft.get());
        }

        double hx = this.x + valueWidth;
        double hy = this.y + this.height / 2 - handle / 2;
        if (this.dragging || this.handleMouseOver) {
            double halo = handle * 1.6;
            BepRender.circle(
                    renderer,
                    hx + handle / 2 - halo / 2,
                    hy + handle / 2 - halo / 2,
                    halo,
                    BepRender.alpha(theme.accentColor.get(), this.dragging ? 0.35 : 0.2));
        }

        BepRender.circle(
                renderer,
                hx,
                hy,
                handle,
                theme.sliderHandle.get(this.dragging, this.handleMouseOver));
    }
}
