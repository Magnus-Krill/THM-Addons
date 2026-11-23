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
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;

import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepRender;
import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepWindow extends WWindow implements BepWidget {
    public WBepWindow(WWidget icon, String title) {
        super(icon, title);
    }

    protected WWindow.WHeader header(WWidget icon) {
        return new WBepHeader(icon);
    }

    @Override
    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (this.visible && this.theme().windowShadow.get()) {
            BepGuiTheme theme = this.theme();
            double s = theme.scale(3);
            double h = (this.height - this.header.height) * this.animProgress + this.header.height;
            BepRender.fill(
                    renderer,
                    this.x - s,
                    this.y,
                    this.width + s * 2,
                    h + s * 2,
                    theme.radius() + s,
                    theme.shadowColor.get());
        }

        return super.render(renderer, mouseX, mouseY, delta);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        BepGuiTheme theme = this.theme();
        double r = theme.radius();
        if (this.expanded || this.animProgress > 0) {
            BepRender.fill(
                    renderer,
                    this.x,
                    this.y + this.header.height,
                    this.width,
                    this.height - this.header.height,
                    r,
                    12,
                    theme.windowBackground.get());
        }
    }

    private class WBepHeader extends WWindow.WHeader {
        public WBepHeader(WWidget icon) {
            super(icon);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            BepGuiTheme theme = WBepWindow.this.theme();
            double r = theme.radius();
            boolean open = WBepWindow.this.expanded || WBepWindow.this.animProgress > 0;
            BepRender.fill(
                    renderer,
                    this.x,
                    this.y,
                    this.width,
                    this.height,
                    r,
                    open ? 3 : 15,
                    theme.headerBackground.get());
            if (open) {
                double s = theme.scale(1);
                renderer.quad(
                        this.x + r,
                        this.y + this.height - s,
                        this.width - r * 2,
                        s,
                        theme.accentColor.get(),
                        theme.accentSecondaryColor.get());
            }
        }
    }
}
