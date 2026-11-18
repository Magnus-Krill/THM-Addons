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
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.utils.render.color.Color;

import net.minecraft.util.Mth;

import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepRender;
import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepTextBox extends WTextBox implements BepWidget {
    private static final Color COMPLETION_SELECTED = new Color(255, 255, 255, 15);
    private boolean cursorVisible;
    private double cursorTimer;
    private double cursorAnim;
    private double focusAnim;

    public WBepTextBox(
            String text,
            String placeholder,
            CharFilter filter,
            Class<? extends WTextBox.Renderer> renderer) {
        super(text, placeholder, filter, renderer);
    }

    @Override
    protected WContainer createCompletionsRootWidget() {
        return new WCompletions();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T extends WWidget & WTextBox.ICompletionItem> T createCompletionsValueWidth(
            String completion, boolean selected) {
        return (T) (new CompletionItem(completion, selected));
    }

    @Override
    protected void onCursorChanged() {
        this.cursorVisible = true;
        this.cursorTimer = 0;
        this.cursorAnim = this.focused ? 1 : 0;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (this.cursorTimer >= 1) {
            this.cursorVisible = !this.cursorVisible;
            this.cursorTimer = 0;
        } else {
            this.cursorTimer += delta * 1.75;
        }

        BepGuiTheme theme = this.theme();
        double pad = this.pad();
        double overflowWidth = this.getOverflowWidthForRender();
        this.focusAnim = Mth.clamp(this.focusAnim + delta * 10 * (this.focused ? 1 : -1), 0, 1);
        Color outline =
                BepRender.lerp(
                        theme.outlineColor.get(false, this.mouseOver),
                        theme.outlineColor.get(true, false),
                        this.focusAnim);
        this.renderBackground(
                renderer, this, outline, theme.widgetBackground.get(false, this.mouseOver));
        renderer.scissorStart(
                this.x + pad, this.y + pad, this.width - pad * 2, this.height - pad * 2);
        if (!this.text.isEmpty()) {
            this.renderer.render(
                    renderer,
                    this.x + pad - overflowWidth,
                    this.y + pad,
                    this.text,
                    theme.textColor.get());
        } else if (this.placeholder != null) {
            this.renderer.render(
                    renderer,
                    this.x + pad - overflowWidth,
                    this.y + pad,
                    this.placeholder,
                    theme.placeholderColor.get());
        }

        if (this.focused
                && (this.cursor != this.selectionStart || this.cursor != this.selectionEnd)) {
            double selStart = this.x + pad + this.getTextWidth(this.selectionStart) - overflowWidth;
            double selEnd = this.x + pad + this.getTextWidth(this.selectionEnd) - overflowWidth;
            renderer.quad(
                    selStart,
                    this.y + pad,
                    selEnd - selStart,
                    theme.textHeight(),
                    theme.textHighlightColor.get());
        }

        this.cursorAnim =
                Mth.clamp(
                        this.cursorAnim
                                + delta * 10 * (this.focused && this.cursorVisible ? 1 : -1),
                        0,
                        1);
        if (this.focused && this.cursorVisible || this.cursorAnim > 0) {
            renderer.setAlpha(this.cursorAnim);
            renderer.quad(
                    this.x + pad + this.getTextWidth(this.cursor) - overflowWidth,
                    this.y + pad,
                    theme.scale(1),
                    theme.textHeight(),
                    theme.accentColor.get());
            renderer.setAlpha(1);
        }

        renderer.scissorEnd();
    }

    private static class WCompletions extends WVerticalList implements BepWidget {
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

    private static class CompletionItem extends WBepLabel implements WTextBox.ICompletionItem {
        private boolean selected;

        public CompletionItem(String text, boolean selected) {
            super(text, false);
            this.selected = selected;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            if (this.selected) {
                BepRender.fill(
                        renderer,
                        this.x,
                        this.y,
                        this.width,
                        this.height,
                        this.theme().smallRadius(),
                        WBepTextBox.COMPLETION_SELECTED);
            }

            super.onRender(renderer, mouseX, mouseY, delta);
        }

        public boolean isSelected() {
            return this.selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        public String getCompletion() {
            return this.text;
        }
    }
}
