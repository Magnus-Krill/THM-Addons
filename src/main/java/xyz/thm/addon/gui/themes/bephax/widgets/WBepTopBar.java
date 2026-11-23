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

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WTopBar;
import meteordevelopment.meteorclient.utils.render.color.Color;

import net.minecraft.util.Mth;

import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepRender;
import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepTopBar extends WTopBar implements BepWidget {
    public WBepTopBar() {
        this.spacing = 2;
    }

    @Override
    public void init() {
        for (Tab tab : Tabs.get()) {
            this.add(new WBepTopBarButton(tab));
        }
    }

    @Override
    protected Color getButtonColor(boolean pressed, boolean hovered) {
        return this.theme().widgetBackground.get(pressed, hovered);
    }

    @Override
    protected Color getNameColor() {
        return this.theme().textColor.get();
    }

    @Override
    protected void onCalculateSize() {
        super.onCalculateSize();
        double p = this.theme().topBarPad();
        this.width += p * 2 + this.theme.scale(2);
        this.height += p * 2;
    }

    @Override
    protected void onCalculateWidgetPositions() {
        double p = this.theme().topBarPad();
        double x = this.x + p + this.theme.scale(1);
        double y = this.y + p;

        for (Cell<?> cell : this.cells) {
            cell.x = x;
            cell.y = y;
            cell.width = cell.widget().width;
            cell.height = this.height - p * 2;
            cell.alignWidget();
            x += cell.width + this.spacing();
        }
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        BepGuiTheme theme = this.theme();
        double r = theme.radius();
        BepRender.fill(
                renderer,
                this.x,
                this.y,
                this.width,
                this.height,
                r,
                12,
                theme.headerBackground.get());
        BepRender.outline(
                renderer,
                this.x,
                this.y,
                this.width,
                this.height,
                r,
                theme.scale(1),
                12,
                theme.outlineColor.get());
    }

    private class WBepTopBarButton extends WTopBar.WTopBarButton {
        private final Tab tab;
        private double anim;

        public WBepTopBarButton(Tab tab) {
            super(tab);
            this.tab = tab;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            BepGuiTheme theme = WBepTopBar.this.theme();
            double pad = this.pad();
            double r = theme.smallRadius();
            boolean selected =
                    MeteorClient.mc.gui.screen() instanceof TabScreen screen
                            && screen.tab == this.tab;
            this.anim = Mth.clamp(this.anim + delta * 10 * (selected ? 1 : -1), 0, 1);
            if (this.pressed || this.mouseOver && !theme.disableHoverColor) {
                BepRender.fill(
                        renderer,
                        this.x,
                        this.y,
                        this.width,
                        this.height,
                        r,
                        theme.widgetBackground.get(this.pressed, true));
            }

            if (this.anim > 0) {
                BepRender.fill(
                        renderer,
                        this.x,
                        this.y,
                        this.width,
                        this.height,
                        r,
                        BepRender.alpha(theme.moduleActive.get(), this.anim));
                double lw = (this.width - pad * 2) * this.anim;
                double lh = theme.scale(2);
                renderer.quad(
                        this.x + this.width / 2 - lw / 2,
                        this.y + this.height - lh,
                        lw,
                        lh,
                        theme.accentColor.get(),
                        theme.accentSecondaryColor.get());
            }

            renderer.text(
                    this.tab.name,
                    this.x + pad,
                    this.y + pad,
                    selected ? theme.titleTextColor.get() : theme.textColor.get(),
                    false);
        }
    }
}
