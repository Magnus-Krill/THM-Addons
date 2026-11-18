/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

// BepHax 0.5.5 theme by Dekto, adapted for Meteor 26.2.
// https://bep.dek.to/changelog 
//To Dekto, if you want this removed then open a issue and ill remove it

package xyz.thm.addon.gui.themes.bephax.widgets;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.AlignmentX;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.systems.modules.Module;

import net.minecraft.util.Mth;

import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepRender;
import xyz.thm.addon.gui.themes.bephax.BepWidget;

public class WBepModule extends WPressable implements BepWidget {
    private final Module module;
    private final String title;
    private double titleWidth;
    private String hint;
    private double hintWidth;
    private double hoverAnim;
    private double activeAnim;

    public WBepModule(Module module, String title) {
        this.module = module;
        this.title = title;
        this.tooltip = module.description;
        this.activeAnim = module.isActive() ? 1 : 0;
    }

    public double pad() {
        return this.theme.scale(4);
    }

    private double barSpace() {
        return this.theme().activeIndicator.get() == BepGuiTheme.ActiveIndicator.Dot
                ? 0
                : this.theme.scale(3);
    }

    private double dotSize() {
        return this.theme.scale(6);
    }

    @Override
    protected void onCalculateSize() {
        BepGuiTheme theme = this.theme();
        double pad = this.pad();
        this.titleWidth = theme.textWidth(this.title);
        this.hint =
                theme.showKeybinds.get() && this.module.keybind.isSet()
                        ? this.module.keybind.toString()
                        : null;
        this.hintWidth = this.hint == null ? 0 : theme.textWidth(this.hint);
        this.width = pad + this.barSpace() + this.titleWidth + pad;
        if (this.hint != null) {
            this.width += this.hintWidth + pad;
        }

        if (theme.activeIndicator.get() != BepGuiTheme.ActiveIndicator.Bar) {
            this.width += this.dotSize() + pad;
        }

        this.width = Math.ceil(this.width);
        this.height = pad + theme.textHeight() + pad;
    }

    @Override
    protected void onPressed(int button) {
        if (button == 0) {
            this.module.toggle();
        } else if (button == 1) {
            MeteorClient.mc.gui.setScreen(this.theme.moduleScreen(this.module));
        }
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        BepGuiTheme theme = this.theme();
        double pad = this.pad();
        boolean hovered = this.mouseOver && !theme.disableHoverColor;
        this.hoverAnim = Mth.clamp(this.hoverAnim + delta * 10 * (hovered ? 1 : -1), 0, 1);
        this.activeAnim =
                Mth.clamp(this.activeAnim + delta * 6 * (this.module.isActive() ? 1 : -1), 0, 1);
        double r = theme.smallRadius();
        if (this.hoverAnim > 0) {
            BepRender.fill(
                    renderer,
                    this.x,
                    this.y,
                    this.width,
                    this.height,
                    r,
                    BepRender.alpha(theme.moduleHovered.get(), this.hoverAnim));
        }

        if (this.activeAnim > 0) {
            BepRender.fill(
                    renderer,
                    this.x,
                    this.y,
                    this.width,
                    this.height,
                    r,
                    BepRender.alpha(theme.moduleActive.get(), this.activeAnim));
        }

        BepGuiTheme.ActiveIndicator indicator = theme.activeIndicator.get();
        double left = this.x + pad + this.barSpace();
        double right = this.x + this.width - pad;
        if (indicator != BepGuiTheme.ActiveIndicator.Dot && this.activeAnim > 0) {
            double bw = theme.scale(2);
            double bh = (this.height - pad * 2) * this.activeAnim;
            BepRender.fill(
                    renderer,
                    this.x + theme.scale(2),
                    this.y + this.height / 2 - bh / 2,
                    bw,
                    bh,
                    bw / 2,
                    theme.accentColor.get());
        }

        if (indicator != BepGuiTheme.ActiveIndicator.Bar) {
            double d = this.dotSize();
            right -= d;
            BepRender.circle(
                    renderer,
                    right,
                    this.y + this.height / 2 - d / 2,
                    d,
                    BepRender.lerp(
                            theme.outlineColor.get(), theme.accentColor.get(), this.activeAnim));
            right -= pad;
        }

        if (this.hint != null && right - this.hintWidth >= left + this.titleWidth + pad) {
            right -= this.hintWidth;
            renderer.text(this.hint, right, this.y + pad, theme.textSecondaryColor.get(), false);
            right -= pad;
        }

        double tx = left;
        double available = right - left;
        if (theme.moduleAlignment.get() == AlignmentX.Center) {
            tx = left + (available / 2 - this.titleWidth / 2);
        } else if (theme.moduleAlignment.get() == AlignmentX.Right) {
            tx = left + (available - this.titleWidth);
        }

        renderer.text(this.title, tx, this.y + pad, theme.textColor.get(), false);
    }
}
