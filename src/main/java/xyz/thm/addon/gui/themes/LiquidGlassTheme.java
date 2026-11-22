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
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorModule;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorTopBar;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorView;
import meteordevelopment.meteorclient.gui.utils.AlignmentX;
import meteordevelopment.meteorclient.gui.widgets.WTopBar;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WTriangle;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import xyz.thm.addon.gui.RecolorGuiTheme;
import xyz.thm.addon.gui.themes.bephax.BepRender;
import xyz.thm.addon.shaders.ShaderBackground;
import xyz.thm.addon.shaders.ShaderBackground.GlassPane;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class LiquidGlassTheme extends MeteorGuiTheme implements RecolorGuiTheme {
    private static final Color FALLBACK = new Color(24, 30, 43);
    private static final Color HAIRLINE = new Color(209, 226, 255, 36);
    private static final Color HOVER = new Color(191, 216, 247, 29);
    private static final Color TAB_SELECTED = new Color(142, 182, 240, 62);
    private static final Color FIELD = new Color(10, 17, 30, 145);
    private static final Color FIELD_FOCUS = new Color(135, 181, 246, 182);
    private static final Color POPUP = new Color(27, 36, 52, 248);
    private static final Color SWITCH_OFF = new Color(80, 97, 122, 200);
    private final Setting<SettingColor> glassTint = settings.getGroup("Background").add(new ColorSetting.Builder()
        .name("glass-tint")
        .description("Tint of the glass panes.")
        .defaultValue(new SettingColor(0x00EBF3FC))
        .build());
    private float opacity;
    private boolean sceneCaptured;

    @Override public String getName() { return "Liquid Glass"; }
    @Override public boolean getCategoryIcons() { return true; }
    @Override public SettingColor getTextColor() { return new SettingColor(0xFFF6F9FF); }
    @Override public SettingColor getTitleTextColor() { return getTextColor(); }
    @Override public SettingColor getTextSecondaryColor() { return new SettingColor(0xFFD5DFEE); }
    @Override public SettingColor getPlaceholderColor() { return getTextSecondaryColor(); }
    @Override public SettingColor getAccentColor() { return new SettingColor(0xFFA5CAFF); }
    @Override public SettingColor getCheckboxColor() { return new SettingColor(0xFF426CAA); }
    @Override public SettingColor getPlusColor() { return new SettingColor(0xFFABDFBF); }
    @Override public SettingColor getMinusColor() { return new SettingColor(0xFFF9B8C4); }
    @Override public SettingColor getFavoriteColor() { return new SettingColor(0xFFFFD895); }
    @Override public SettingColor getLoggedInColor() { return getPlusColor(); }
    @Override public SettingColor getModuleBackground() { return new SettingColor(0x634778BF); }
    @Override public SettingColor getSeparatorText() { return getTextSecondaryColor(); }
    @Override public SettingColor getSeparatorCenter() { return new SettingColor(0x7098B5D9); }
    @Override public SettingColor getSeparatorEdges() { return new SettingColor(0x0098B5D9); }
    @Override public SettingColor getSliderLeft() { return getAccentColor(); }
    @Override public SettingColor getSliderRight() { return new SettingColor(0xFF465770); }
    @Override public SettingColor getStarscriptText() { return getTextColor(); }
    @Override public SettingColor getStarscriptBraces() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptParenthesis() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptDots() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptCommas() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptOperators() { return getTextSecondaryColor(); }
    @Override public SettingColor getStarscriptStrings() { return getPlusColor(); }
    @Override public SettingColor getStarscriptNumbers() { return getAccentColor(); }
    @Override public SettingColor getStarscriptKeywords() { return new SettingColor(0xFFFFCE9D); }
    @Override public SettingColor getStarscriptAccessedObjects() { return new SettingColor(0xFFDCC7FA); }

    @Override public TriColorSetting getBackgroundColor() { return colors(0xA8232E41, 0xBF354963, 0xCC273D5D); }
    @Override public TriColorSetting getOutlineColor() { return colors(0x309DB6DA, 0x629CBDDF, 0xA091B9F2); }
    @Override public TriColorSetting getScrollbarColor() { return colors(0xFF667D9D, 0xFF8FACD2, 0xFFA5CAFF); }
    @Override public TriColorSetting getSliderHandle() { return colors(0xFF9FB6D6, 0xFFBCD7FA, 0xFFD5E6FF); }

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

    public WWidget categoryIcon(String category) {
        return w(new GlassCategoryIcon(category, textSecondaryColor.get()));
    }

    @Override
    public WView view() {
        return w(new GlassView());
    }

    private final class GlassView extends WMeteorView {
        private double stretch;

        @Override
        protected void onCalculateSize() {
            super.onCalculateSize();
            if (!canScroll) stretch = 0;
        }

        @Override
        public boolean onMouseScrolled(double amount) {
            if (amount == 0 || !canScroll || (scrollOnlyWhenMouseOver && !mouseOver)) {
                return super.onMouseScrolled(amount);
            }
            if (parent instanceof WWindow && atEdge(amount)) {
                if (parent.getView() instanceof GlassView outer && outer.canScroll && !outer.atEdge(amount)) return false;
                double limit = scale(14);
                stretch = Math.clamp(stretch - amount * scale(10), -limit, limit);
                return true;
            }
            stretch = 0;
            return super.onMouseScrolled(amount);
        }

        private boolean atEdge(double amount) {
            double handleOffset = handleY() - y;
            return amount > 0 ? handleOffset <= 1e-6 : handleOffset >= height - handleHeight() - 1e-6;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            stretch *= Math.exp(-14 * delta);
            if (Math.abs(stretch) < 0.25) stretch = 0;
            super.onRender(renderer, mouseX, mouseY, delta);
        }
    }

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new WWindow(icon, title) {
            @Override
            protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                double visibleHeight = header.height + (height - header.height) * animProgress;
                double stretch = expanded && animProgress == 1 && view instanceof GlassView glass ? glass.stretch : 0;
                drawPane(renderer, x, y + Math.min(stretch, 0), width, visibleHeight + Math.abs(stretch), scale(17), 1.8f);
            }

            @Override
            protected WHeader header(WWidget icon) {
                return new WHeader(icon) {
                    @Override
                    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                        if (animProgress <= 0) return;
                        double inset = scale(17);
                        if (width > inset * 2) {
                            renderer.quad(x + inset, y + height - scale(0.75), width - inset * 2, scale(0.75), HAIRLINE);
                        }
                    }
                };
            }
        });
    }

    @Override
    public WWidget module(Module module, String title) {
        return w(new WMeteorModule(module, title) {
            private final Color highlight = new Color();
            private double enabledProgress = module.isActive() ? 1 : 0;
            private double hoverProgress;
            private double labelWidth;

            @Override
            protected void onCalculateSize() {
                super.onCalculateSize();
                labelWidth = textWidth(title);
            }

            @Override
            protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                boolean enabled = module.isActive();
                enabledProgress = Math.clamp(enabledProgress + (enabled ? 1 : -1) * delta * 12, 0, 1);
                hoverProgress = Math.clamp(hoverProgress + (mouseOver && !disableHoverColor ? 1 : -1) * delta * 12, 0, 1);
                double enabledEase = Mth.smoothstep(enabledProgress);
                double hoverEase = Mth.smoothstep(hoverProgress);
                Color fill = BepRender.lerp(HOVER, moduleBackground.get(), enabledEase);
                double alpha = fill.a * Math.max(enabledEase, hoverEase) + 20 * enabledEase * hoverEase;
                highlight.set(fill.r, fill.g, fill.b, (int) Math.round(alpha) + (pressed ? 16 : 0));
                double inset = scale(3);
                BepRender.fill(renderer, x + inset, y + scale(1), width - 2 * inset, height - scale(2), scale(8), highlight);

                double labelX = x + pad();
                AlignmentX alignment = moduleAlignment.get();
                if (alignment == AlignmentX.Center) labelX = x + (width - labelWidth) / 2;
                else if (alignment == AlignmentX.Right) labelX = x + width - pad() - labelWidth;
                renderer.text(title, labelX, y + pad(), textColor.get(), false);
            }
        });
    }

    @Override
    public WTriangle triangle() {
        return w(new WTriangle() {
            @Override
            protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                Color color = pressed || mouseOver ? accentColor.get() : textSecondaryColor.get();
                renderer.rotatedQuad(x, y, width, height, rotation, GuiRenderer.TRIANGLE, color);
            }
        });
    }

    @Override
    public <T> WDropdown<T> dropdown(T[] values, T value) {
        return w(new GlassDropdown<>(values, value));
    }

    private final class GlassDropdown<T> extends WDropdown<T> {
        private GlassDropdown(T[] values, T value) {
            super(values, value);
        }

        @Override
        protected void onCalculateWidgetPositions() {
            root.width = width;
            super.onCalculateWidgetPositions();
        }

        @Override
        protected WDropdownRoot createRootWidget() {
            return new WDropdownRoot() {
                @Override
                protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                    BepRender.fill(renderer, x, y, width, height, scale(10), 12, POPUP);
                    BepRender.outline(renderer, x, y, width, height, scale(10), scale(1), 12, HAIRLINE);
                }
            };
        }

        @Override
        protected WDropdownValue createValueWidget() {
            return new WDropdownValue() {
                @Override
                protected void onCalculateSize() {
                    width = textWidth(value.toString()) + 2 * pad();
                    height = textHeight() + 2 * pad();
                }

                @Override
                protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                    boolean current = value.equals(GlassDropdown.this.get());
                    if (current || mouseOver || pressed) {
                        BepRender.fill(renderer, x + scale(2), y + scale(1), width - scale(4), height - scale(2),
                            scale(6), pressed || current ? TAB_SELECTED : HOVER);
                    }
                    renderer.text(value.toString(), x + pad() + scale(2), y + pad(), textColor.get(), false);
                }
            };
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double pad = pad();
            double radius = Math.min(height / 2, scale(10));
            int corners = animProgress > 0 ? 3 : 15;
            BepRender.fill(renderer, x, y, width, height, radius, corners, backgroundColor.get(pressed, mouseOver));
            BepRender.outline(renderer, x, y, width, height, radius, scale(1), corners,
                outlineColor.get(expanded || pressed, mouseOver));
            renderer.text(get().toString(), x + pad, y + pad, textColor.get(), false);

            double size = textHeight();
            double angle = 180 * Mth.smoothstep(animProgress);
            renderer.rotatedQuad(x + width - pad - size, y + pad, size, size, angle,
                GuiRenderer.TRIANGLE, expanded ? accentColor.get() : textSecondaryColor.get());
        }
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return w(new WCheckbox(checked) {
            private double progress = checked ? 1 : 0;

            @Override
            protected void onCalculateSize() {
                super.onCalculateSize();
                width += Math.round(textHeight() * 0.8);
            }

            @Override
            protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                progress = Math.clamp(progress + (this.checked ? 1 : -1) * delta * 12, 0, 1);
                double eased = Mth.smoothstep(progress);
                double pad = pad();
                double size = textHeight();
                double trackX = x + pad;
                double trackY = y + (height - size) / 2;
                double trackWidth = width - 2 * pad;
                BepRender.fill(renderer, trackX, trackY, trackWidth, size, size / 2,
                    BepRender.lerp(SWITCH_OFF, checkboxColor.get(), eased));
                BepRender.outline(renderer, trackX, trackY, trackWidth, size, size / 2, scale(1),
                    pressed || mouseOver ? FIELD_FOCUS : HAIRLINE);
                double inset = scale(2);
                double diameter = size - inset * 2;
                double knobX = trackX + inset + (trackWidth - size) * eased;
                BepRender.circle(renderer, knobX, trackY + inset, diameter, textColor.get());
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
            private double emphasis;

            private GlassTab(Tab tab) {
                super(tab);
                this.tab = tab;
            }

            @Override
            protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
                boolean selected = mc.gui.screen() instanceof TabScreen screen && screen.tab == tab;
                emphasis = Math.clamp(emphasis + (selected || mouseOver || pressed ? 1 : -1) * delta * 14, 0, 1);
                if (emphasis > 0) {
                    double inset = scale(2);
                    BepRender.fill(renderer, x, y + inset, width, height - inset * 2, height / 2,
                        BepRender.alpha(pressed || selected ? TAB_SELECTED : HOVER, Mth.smoothstep(emphasis)));
                }
                renderer.text(tab.name, x + pad(), y + pad(), textColor.get(), false);
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
        BepRender.fill(renderer, x, y, width, height, radius, FALLBACK);
    }

    public void drawControl(GuiRenderer renderer, WWidget widget, Color border, Color fill) {
        double radius = Math.min(widget.height / 2, scale(10));
        boolean field = widget instanceof WTextBox;
        BepRender.fill(renderer, widget.x, widget.y, widget.width, widget.height, radius, field ? FIELD : fill);
        Color outline = field && widget.focused ? FIELD_FOCUS : border;
        BepRender.outline(renderer, widget.x, widget.y, widget.width, widget.height, radius, scale(1), outline);
    }
}
