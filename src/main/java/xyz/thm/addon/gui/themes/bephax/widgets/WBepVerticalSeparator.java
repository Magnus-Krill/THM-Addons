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
import meteordevelopment.meteorclient.gui.widgets.WVerticalSeparator;
import meteordevelopment.meteorclient.utils.render.color.Color;

import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepVerticalSeparator extends WVerticalSeparator implements BepWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        BepGuiTheme theme = this.theme();
        Color edges = theme.separatorEdges.get();
        Color center = theme.separatorCenter.get();
        double s = theme.scale(1);
        double x = this.x + Math.round((this.width - s) / 2);
        renderer.quad(x, this.y, s, this.height / 2, edges, edges, center, center);
        renderer.quad(
                x, this.y + this.height / 2, s, this.height / 2, center, center, edges, edges);
    }
}
