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
import meteordevelopment.meteorclient.gui.widgets.WLabel;

import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepLabel extends WLabel implements BepWidget {
    public WBepLabel(String text, boolean title) {
        super(text, title);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (!this.text.isEmpty()) {
            renderer.text(
                    this.text,
                    this.x,
                    this.y,
                    this.color != null
                            ? this.color
                            : (this.title
                                    ? this.theme().titleTextColor.get()
                                    : this.theme().textColor.get()),
                    this.title);
        }
    }
}
