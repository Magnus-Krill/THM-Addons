/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorTopBar;
import meteordevelopment.meteorclient.gui.widgets.WTopBar;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.CompoundTag;
import xyz.thm.addon.gui.RecolorGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepRender;
import xyz.thm.addon.shaders.ShaderBackground;
import xyz.thm.addon.shaders.ShaderBackground.GlassPane;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class LiquidGlassTheme extends MeteorGuiTheme implements RecolorGuiTheme {
    private static final Color TAB_HIGHLIGHT = new Color(255, 255, 255, 100);
    private static final Color TAB_SELECTED = new Color(224, 241, 255, 170);
    private final Setting<SettingColor> glassTint = settings.getGroup("Background").add(new ColorSetting.Builder()
        .name("glass-tint")
        .description("Tint of the glass panes.")
        .defaultValue(new SettingColor(0x00EBF3FC))
        .build());
    private float opacity;
    private boolean sceneCaptured;

    @Override public String getName() { return "Liquid Glass"; }
    @Override public boolean getCategoryIcons() { return true; }
    @Override public SettingColor getTextColor() { return new SettingColor(0xFF142437); }
    @Override public SettingColor getTitleTextColor() { return getTextColor(); }
    @Override public SettingColor getTextSecondaryColor() { return new SettingColor(0xFF1A2633); }
    @Override public SettingColor getPlaceholderColor() { return getTextSecondaryColor(); }
    @Override public SettingColor getAccentColor() { return new SettingColor(0xFF245486); }
    @Override public SettingColor getCheckboxColor() { return getAccentColor(); }
    @Override public SettingColor getPlusColor() { return new SettingColor(0xFF246144); }
    @Override public SettingColor getMinusColor() { return new SettingColor(0xFF8A243E); }
    @Override public SettingColor getFavoriteColor() { return new SettingColor(0xFF795317); }
    @Override public SettingColor getLoggedInColor() { return getPlusColor(); }
    @Override public SettingColor getModuleBackground() { return new SettingColor(0xA9B4D4F3); }
    @Override public SettingColor getSeparatorText() { return getTextSecondaryColor(); }
    @Override public SettingColor getSeparatorCenter() { return new SettingColor(0xFF829BB3); }
    @Override public SettingColor getSeparatorEdges() { return new SettingColor(0x00829BB3); }
    @Override public SettingColor getSliderLeft() { return getAccentColor(); }
    @Override public SettingColor getSliderRight() { return new SettingColor(0xFFADC4DB); }
    @Override public SettingColor getStarscriptText() { return getTextColor(); }
    @Override public SettingColor getStarscriptBraces() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptParenthesis() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptDots() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptCommas() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptOperators() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptStrings() { return getPlusColor(); }
    @Override public SettingColor getStarscriptNumbers() { return getAccentColor(); }
    @Override public SettingColor getStarscriptKeywords() { return new SettingColor(0xFF75421C); }
    @Override public SettingColor getStarscriptAccessedObjects() { return new SettingColor(0xFF563576); }

    @Override public TriColorSetting getBackgroundColor() { return colors(0xEAF4F8FF, 0xF5FFFFFF, 0xFFD4E6F8); }
    @Override public TriColorSetting getOutlineColor() { return colors(0xA2FFFFFF, 0xD2FFFFFF, 0xFF6E94BA); }
    @Override public TriColorSetting getScrollbarColor() { return colors(0xFF7393B4, 0xFF436C94, 0xFF245486); }
    @Override public TriColorSetting getSliderHandle() { return colors(0xFF476E93, 0xFF335A82, 0xFF245486); }

    private static TriColorSetting colors(int normal, int hover, int pressed) {
        return new TriColorSetting(new SettingColor(normal), new SettingColor(hover), new SettingColor(pressed));
    }

    @Override
    public LiquidGlassTheme fromTag(CompoundTag tag) {
        super.fromTag(tag);
        int tint = glassTint.get().getPacked();
        if (tint == 0xB3EBF3FC || tint == 0x12EBF3FC) glassTint.reset();
        return this;
    }

    @Override
    public void beforeRender() {
        super.beforeRender();
        sceneCaptured = false;
        opacity = mc.gui.screen() instanceof WidgetScreen screen ? (float) screen.animProgress : 1;
    }

    public boolean renderedGlass() {
        return sceneCaptured;
    }

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new WWindow(icon, title) {
            @Override
            protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                double visibleHeight = header.height + (height - header.height) * animProgress;
                drawPane(renderer, x, y, width, visibleHeight, scale(17), 1.8f);
            }

            @Override
            protected WHeader header(WWidget icon) {
                return new WHeader(icon) {
                    @Override
                    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                        if (animProgress <= 0) return;
                        double inset = scale(17);
                        if (width > inset * 2) {
                            renderer.quad(x + inset, y + height - scale(0.5), width - inset * 2, scale(0.5), TAB_HIGHLIGHT);
                        }
                    }
                };
            }
        });
    }

    @Override
    public WTopBar topBar() {
        return w(new GlassTabBar());
    }

    public WVerticalList moduleHelp() {
        return w(new WVerticalList() {
            @Override
            protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                double inset = scale(4);
                drawPane(renderer, x - inset, y - inset, width + inset * 2, height + inset * 2, scale(14), 1.5f);
            }
        });
    }

    private final class GlassTabBar extends WMeteorTopBar {
        private GlassTabBar() {
            spacing = 3;
        }

        @Override
        public void init() {
            for (Tab tab : Tabs.get()) add(new GlassTab(tab)).padVertical(8);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double margin = scale(5);
            double paneHeight = height - 2 * margin;
            drawPane(renderer, x - margin, y + margin, width + 2 * margin, paneHeight, paneHeight / 2, 3.0f);
        }

        private final class GlassTab extends WTopBarButton {
            private final Tab tab;

            private GlassTab(Tab tab) {
                super(tab);
                this.tab = tab;
            }

            @Override
            protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                boolean selected = mc.gui.screen() instanceof TabScreen screen && screen.tab == tab;
                if (selected || mouseOver || pressed) {
                    double inset = scale(2);
                    BepRender.fill(renderer, x, y + inset, width, height - inset * 2, height / 2,
                        pressed || selected ? TAB_SELECTED : TAB_HIGHLIGHT);
                }
                renderer.text(tab.name, x + pad(), y + pad(), getTextColor(), false);
            }
        }
    }

    private void drawPane(GuiRenderer renderer, double x, double y, double width, double height, double radius, float lens) {
        if (width <= 0 || height <= 0) return;
        SettingColor tint = glassTint.get();
        if (ShaderBackground.renderGlass(new GlassPane(x, y, width, height, radius, lens, tint), opacity, !sceneCaptured)) {
            sceneCaptured = true;
            return;
        }
        if (tint.a > 0) BepRender.fill(renderer, x, y, width, height, radius, tint);
    }

    public void drawControl(GuiRenderer renderer, WWidget widget, Color border, Color fill) {
        double radius = Math.min(widget.height / 2, scale(10));
        BepRender.fill(renderer, widget.x, widget.y, widget.width, widget.height, radius, fill);
        BepRender.outline(renderer, widget.x, widget.y, widget.width, widget.height, radius, scale(1), border);
    }
}
