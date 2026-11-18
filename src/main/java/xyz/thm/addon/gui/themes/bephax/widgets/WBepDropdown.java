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
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;

import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepRender;
import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepDropdown<T> extends WDropdown<T> implements BepWidget {
    public WBepDropdown(T[] values, T value) {
        super(values, value);
    }

    @Override
    protected WDropdownRoot createRootWidget() {
        return new WRoot();
    }

    @Override
    protected WDropdownValue createValueWidget() {
        return new WValue();
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        BepGuiTheme theme = this.theme();
        double pad = this.pad();
        double s = theme.textHeight();
        int corners = this.animProgress > 0 ? 3 : 15;
        double r = theme.radius();
        BepRender.fill(
                renderer,
                this.x,
                this.y,
                this.width,
                this.height,
                r,
                corners,
                theme.widgetBackground.get(this.pressed, this.mouseOver));
        BepRender.outline(
                renderer,
                this.x,
                this.y,
                this.width,
                this.height,
                r,
                theme.scale(1),
                corners,
                theme.outlineColor.get(this.expanded || this.pressed, this.mouseOver));
        renderer.text(
                this.get().toString(), this.x + pad, this.y + pad, theme.textColor.get(), false);
        renderer.rotatedQuad(
                this.x + pad + this.maxValueWidth + pad,
                this.y + pad,
                s,
                s,
                this.animProgress * 180,
                GuiRenderer.TRIANGLE,
                theme.textSecondaryColor.get());
    }

    private static class WRoot extends WDropdownRoot implements BepWidget {
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
                    theme.outlineColor.get(true, false));
        }
    }

    private class WValue extends WDropdownValue implements BepWidget {
        @Override
        protected void onCalculateSize() {
            double pad = this.pad();
            this.width = pad + this.theme.textWidth(this.value.toString()) + pad;
            this.height = pad + this.theme.textHeight() + pad;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            BepGuiTheme theme = this.theme();
            if (this.pressed || this.mouseOver) {
                BepRender.fill(
                        renderer,
                        this.x,
                        this.y,
                        this.width,
                        this.height,
                        theme.smallRadius(),
                        theme.widgetBackground.get(this.pressed, this.mouseOver, true));
            }

            boolean current = this.value.equals(WBepDropdown.this.get());
            renderer.text(
                    this.value.toString(),
                    this.x + this.pad(),
                    this.y + this.pad(),
                    current ? theme.accentColor.get() : theme.textColor.get(),
                    false);
        }
    }
}
