/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorWindow;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.gui.RecolorGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepRender;

public final class VioletGlassTheme extends MeteorGuiTheme implements RecolorGuiTheme {
    private final Setting<SettingColor> headerColor = settings.getGroup("Background").add(new ColorSetting.Builder()
        .name("header-color")
        .description("Color of window headers.")
        .defaultValue(new SettingColor(0xF05D3262))
        .build());

    @Override public String getName() { return "Violet Glass"; }
    @Override public boolean getCategoryIcons() { return true; }
    @Override public SettingColor getAccentColor() { return new SettingColor(0xFFF3B6E0); }
    @Override public SettingColor getCheckboxColor() { return getAccentColor(); }
    @Override public SettingColor getTextColor() { return new SettingColor(0xFFFDF5FD); }
    @Override public SettingColor getTitleTextColor() { return getTextColor(); }
    @Override public SettingColor getTextSecondaryColor() { return new SettingColor(0xFFF2DFEC); }
    @Override public SettingColor getPlaceholderColor() { return getTextSecondaryColor(); }
    @Override public SettingColor getTextHighlightColor() { return new SettingColor(0x30F3B6E0); }
    @Override public SettingColor getPlusColor() { return new SettingColor(0xFF9FE8BC); }
    @Override public SettingColor getMinusColor() { return new SettingColor(0xFFFFA5B3); }
    @Override public SettingColor getFavoriteColor() { return new SettingColor(0xFFFFDC9A); }
    @Override public SettingColor getLoggedInColor() { return getPlusColor(); }
    @Override public SettingColor getSeparatorText() { return getTextSecondaryColor(); }
    @Override public SettingColor getSeparatorCenter() { return new SettingColor(0xFFD599C8); }
    @Override public SettingColor getSeparatorEdges() { return new SettingColor(0xE8281835); }
    @Override public SettingColor getModuleBackground() { return new SettingColor(0xFF593460); }
    @Override public SettingColor getSliderLeft() { return getAccentColor(); }
    @Override public SettingColor getSliderRight() { return new SettingColor(0xF5472850); }
    @Override public SettingColor getStarscriptText() { return getTextColor(); }
    @Override public SettingColor getStarscriptBraces() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptParenthesis() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptDots() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptCommas() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptOperators() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptStrings() { return new SettingColor(0xFFB5E0BD); }
    @Override public SettingColor getStarscriptNumbers() { return new SettingColor(0xFFB2D8FF); }
    @Override public SettingColor getStarscriptKeywords() { return new SettingColor(0xFFFFCCA2); }
    @Override public SettingColor getStarscriptAccessedObjects() { return new SettingColor(0xFFE3C6FF); }

    @Override
    public TriColorSetting getBackgroundColor() {
        return colors(0xE8281835, 0xF5472850, 0xFF593460);
    }

    @Override
    public TriColorSetting getOutlineColor() {
        return colors(0xFFD599C8, 0xFFF3B6E0, 0xFFF3B6E0);
    }

    @Override
    public TriColorSetting getScrollbarColor() {
        return colors(0xFFD599C8, 0xFFF3B6E0, 0xFFF2DFEC);
    }

    @Override
    public TriColorSetting getSliderHandle() {
        return colors(0xFFF2DFEC, 0xFFF3B6E0, 0xFFF3B6E0);
    }

    private static TriColorSetting colors(int normal, int hover, int pressed) {
        return new TriColorSetting(new SettingColor(normal), new SettingColor(hover), new SettingColor(pressed));
    }

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new ThemeWindow(icon, title));
    }

    private final class ThemeWindow extends WMeteorWindow {
        private ThemeWindow(WWidget icon, String title) {
            super(icon, title);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            if (!expanded && animProgress <= 0) return;

            double top = y + header.height;
            double bodyHeight = height - header.height;
            double radius = scale(8);
            BepRender.fill(renderer, x, top, width, bodyHeight, radius, BepRender.BOTTOM, backgroundColor.get());
            BepRender.outline(renderer, x, top, width, bodyHeight, radius, scale(1), BepRender.BOTTOM, outlineColor.get());
        }

        @Override
        protected WHeader header(WWidget icon) {
            return new WHeader(icon) {
                @Override
                protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                    drawHeader(renderer, this, expanded || animProgress > 0);
                }
            };
        }
    }

    private void drawHeader(GuiRenderer renderer, WWidget header, boolean open) {
        double radius = scale(8);
        int corners = open ? BepRender.TOP : BepRender.ALL;
        BepRender.fill(renderer, header.x, header.y, header.width, header.height, radius, corners, headerColor.get());
        BepRender.outline(renderer, header.x, header.y, header.width, header.height, radius, scale(1), corners, accentColor.get());
    }

    public void drawControl(GuiRenderer renderer, WWidget widget, Color border, Color fill) {
        double radius = Math.min(widget.height / 2, scale(9));
        BepRender.fill(renderer, widget.x, widget.y, widget.width, widget.height, radius, fill);
        BepRender.outline(renderer, widget.x, widget.y, widget.width, widget.height, radius, scale(1), border);
    }
}
