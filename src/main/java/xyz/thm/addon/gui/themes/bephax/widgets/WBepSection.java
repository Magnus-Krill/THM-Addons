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
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.pressable.WTriangle;

import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepSection extends WSection {
    public WBepSection(String title, boolean expanded, WWidget headerWidget) {
        super(title, expanded, headerWidget);
    }

    protected WSection.WHeader createHeader() {
        return new WBepHeader(this.title);
    }

    protected class WBepHeader extends WSection.WHeader {
        private WTriangle triangle;

        public WBepHeader(String title) {
            super(title);
        }

        @Override
        public void init() {
            this.add(this.theme.horizontalSeparator(this.title)).expandX();
            if (WBepSection.this.headerWidget != null) {
                this.add(WBepSection.this.headerWidget);
            }

            this.triangle = new WHeaderTriangle();
            this.triangle.theme = this.theme;
            this.triangle.action = () -> this.onClick();
            this.add(this.triangle);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            this.triangle.rotation = (1 - WBepSection.this.animProgress) * -90;
        }
    }

    protected static class WHeaderTriangle extends WTriangle implements BepWidget {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            renderer.rotatedQuad(
                    this.x,
                    this.y,
                    this.width,
                    this.height,
                    this.rotation,
                    GuiRenderer.TRIANGLE,
                    this.mouseOver
                            ? this.theme().textColor.get()
                            : this.theme().textSecondaryColor.get());
        }
    }
}
