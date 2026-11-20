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
import meteordevelopment.meteorclient.gui.widgets.WHorizontalSeparator;

import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepHorizontalSeparator extends WHorizontalSeparator implements BepWidget {
    public WBepHorizontalSeparator(String text) {
        super(text);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        BepGuiTheme theme = this.theme();
        double s = theme.scale(1);
        if (this.text == null) {
            double w = this.width / 2;
            double y = this.y + Math.round((this.height - s) / 2);
            renderer.quad(this.x, y, w, s, theme.separatorEdges.get(), theme.separatorCenter.get());
            renderer.quad(
                    this.x + w, y, w, s, theme.separatorCenter.get(), theme.separatorEdges.get());
        } else {
            double gap = theme.scale(4);
            double textStart = Math.round(this.width / 2 - this.textWidth / 2 - gap);
            double textEnd = gap + textStart + this.textWidth + gap;
            double y = this.y + Math.round((this.height - s) / 2);
            renderer.quad(
                    this.x,
                    y,
                    textStart,
                    s,
                    theme.separatorEdges.get(),
                    theme.separatorCenter.get());
            renderer.text(
                    this.text, this.x + textStart + gap, this.y, theme.separatorText.get(), false);
            renderer.quad(
                    this.x + textEnd,
                    y,
                    this.width - textEnd,
                    s,
                    theme.separatorCenter.get(),
                    theme.separatorEdges.get());
        }
    }
}
