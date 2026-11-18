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
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;

import net.minecraft.util.Mth;

import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepRender;
import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepCheckbox extends WCheckbox implements BepWidget {
    private double anim;

    public WBepCheckbox(boolean checked) {
        super(checked);
        this.anim = checked ? 1 : 0;
    }

    @Override
    protected void onCalculateSize() {
        double pad = this.pad();
        double s = this.theme.textHeight();
        this.width = pad + Math.round(s * 1.9) + pad;
        this.height = pad + s + pad;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        BepGuiTheme theme = this.theme();
        this.anim = Mth.clamp(this.anim + (this.checked ? 1 : -1) * delta * 12, 0, 1);
        double pad = this.pad();
        double th = theme.textHeight();
        double tw = this.width - pad * 2;
        double tx = this.x + pad;
        double ty = this.y + pad;
        BepRender.fill(
                renderer,
                tx,
                ty,
                tw,
                th,
                th / 2,
                BepRender.lerp(theme.checkboxOffColor.get(), theme.checkboxColor.get(), this.anim));
        if (this.pressed || this.mouseOver && !theme.disableHoverColor) {
            BepRender.outline(
                    renderer,
                    tx,
                    ty,
                    tw,
                    th,
                    th / 2,
                    theme.scale(1),
                    theme.outlineColor.get(this.pressed, true));
        }

        double inset = theme.scale(2);
        double kd = th - inset * 2;
        double kx = tx + inset + (tw - inset * 2 - kd) * this.anim;
        BepRender.circle(
                renderer, kx, ty + inset, kd, theme.sliderHandle.get(this.pressed, this.mouseOver));
    }
}
