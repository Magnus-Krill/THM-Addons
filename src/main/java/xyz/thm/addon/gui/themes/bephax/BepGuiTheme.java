/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







// BepHax 0.5.5 theme by Dekto, adapted for Meteor 26.2.
// https://bep.dek.to/changelog
//To Dekto, if you want this removed then open a issue and ill remove it

package xyz.thm.addon.gui.themes.bephax;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.DefaultSettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.utils.AlignmentX;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.WAccount;
import meteordevelopment.meteorclient.gui.widgets.WHorizontalSeparator;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WQuad;
import meteordevelopment.meteorclient.gui.widgets.WTooltip;
import meteordevelopment.meteorclient.gui.widgets.WTopBar;
import meteordevelopment.meteorclient.gui.widgets.WVerticalSeparator;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WFavorite;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WTriangle;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import net.minecraft.client.gui.screens.Screen;

import xyz.thm.addon.gui.themes.bephax.widgets.WBepAccount;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepButton;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepCheckbox;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepConfirmedButton;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepConfirmedMinus;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepDropdown;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepFavorite;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepHorizontalSeparator;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepLabel;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepMinus;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepModule;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepMultiLabel;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepPlus;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepQuad;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepSection;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepSlider;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepTextBox;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepTooltip;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepTopBar;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepTriangle;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepVerticalSeparator;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepView;
import xyz.thm.addon.gui.themes.bephax.widgets.WBepWindow;
import xyz.thm.addon.settings.DescribedOption;

public class BepGuiTheme extends GuiTheme {
    public static final String Bep = "BepHax";
    public static final String Propaganda = "THM-Addon";
    private final SettingGroup sgGeneral;
    private final SettingGroup sgColors;
    private final SettingGroup sgText;
    private final SettingGroup sgBackground;
    private final SettingGroup sgOutline;
    private final SettingGroup sgSeparator;
    private final SettingGroup sgScrollbar;
    private final SettingGroup sgSlider;
    private final SettingGroup sgStarscript;
    public final Setting<Double> scale;
    public final Setting<Integer> cornerRadius;
    public final Setting<Boolean> windowShadow;
    public final Setting<AlignmentX> moduleAlignment;
    public final Setting<ActiveIndicator> activeIndicator;
    public final Setting<Boolean> showKeybinds;
    public final Setting<Boolean> categoryIcons;
    public final Setting<Boolean> modulesHelpText;
    public final Setting<Boolean> hideHUD;
    public final Setting<SettingColor> accentColor;
    public final Setting<SettingColor> accentSecondaryColor;
    public final Setting<SettingColor> checkboxColor;
    public final Setting<SettingColor> checkboxOffColor;
    public final Setting<SettingColor> plusColor;
    public final Setting<SettingColor> minusColor;
    public final Setting<SettingColor> favoriteColor;
    public final Setting<SettingColor> textColor;
    public final Setting<SettingColor> textSecondaryColor;
    public final Setting<SettingColor> textHighlightColor;
    public final Setting<SettingColor> titleTextColor;
    public final Setting<SettingColor> loggedInColor;
    public final Setting<SettingColor> placeholderColor;
    public final Setting<SettingColor> windowBackground;
    public final Setting<SettingColor> headerBackground;
    public final StateColors widgetBackground;
    public final Setting<SettingColor> moduleHovered;
    public final Setting<SettingColor> moduleActive;
    public final Setting<SettingColor> shadowColor;
    public final StateColors outlineColor;
    public final Setting<SettingColor> separatorText;
    public final Setting<SettingColor> separatorCenter;
    public final Setting<SettingColor> separatorEdges;
    public final StateColors scrollbarColor;
    public final StateColors sliderHandle;
    public final Setting<SettingColor> sliderLeft;
    public final Setting<SettingColor> sliderRight;
    private final Setting<SettingColor> starscriptText;
    private final Setting<SettingColor> starscriptBraces;
    private final Setting<SettingColor> starscriptParenthesis;
    private final Setting<SettingColor> starscriptDots;
    private final Setting<SettingColor> starscriptCommas;
    private final Setting<SettingColor> starscriptOperators;
    private final Setting<SettingColor> starscriptStrings;
    private final Setting<SettingColor> starscriptNumbers;
    private final Setting<SettingColor> starscriptKeywords;
    private final Setting<SettingColor> starscriptAccessedObjects;

    public BepGuiTheme() {
        super(Bep);
        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgColors = this.settings.createGroup("Colors");
        this.sgText = this.settings.createGroup("Text");
        this.sgBackground = this.settings.createGroup("Background");
        this.sgOutline = this.settings.createGroup("Outline");
        this.sgSeparator = this.settings.createGroup("Separator");
        this.sgScrollbar = this.settings.createGroup("Scrollbar");
        this.sgSlider = this.settings.createGroup("Slider");
        this.sgStarscript = this.settings.createGroup("Starscript");
        scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale")
            .description("Scales the GUI.")
            .defaultValue(1)
            .min(0.75)
            .sliderRange(0.75, 4)
            .onSliderRelease()
            .onChanged(_ -> {
                if (MeteorClient.mc.gui.screen() instanceof WidgetScreen screen) screen.invalidate();
            })
            .build());
        cornerRadius = sgGeneral.add(new IntSetting.Builder()
            .name("corner-radius")
            .description("Rounds window and control corners.")
            .defaultValue(6)
            .min(0)
            .sliderRange(0, 12)
            .build());
        windowShadow = sgGeneral.add(new BoolSetting.Builder()
            .name("window-shadow")
            .description("Draws shadows under windows.")
            .defaultValue(true)
            .build());
        moduleAlignment = sgGeneral.add(new EnumSetting.Builder<AlignmentX>()
            .name("module-alignment")
            .description("Aligns module titles.")
            .defaultValue(AlignmentX.Left)
            .build());
        activeIndicator = sgGeneral.add(new EnumSetting.Builder<ActiveIndicator>()
            .name("active-indicator")
            .description("Marks enabled modules.")
            .defaultValue(ActiveIndicator.Both)
            .build());
        showKeybinds = sgGeneral.add(new BoolSetting.Builder()
            .name("show-keybinds")
            .description("Shows keybinds next to modules.")
            .defaultValue(true)
            .build());
        categoryIcons = sgGeneral.add(new BoolSetting.Builder()
            .name("category-icons")
            .description("Shows category icons.")
            .defaultValue(true)
            .build());
        modulesHelpText = sgGeneral.add(new BoolSetting.Builder()
            .name("modules-help-text")
            .description("Shows mouse controls on the Modules screen.")
            .defaultValue(false)
            .build());
        hideHUD = sgGeneral.add(new BoolSetting.Builder()
            .name("hide-HUD")
            .description("Hides the HUD while the GUI is open.")
            .defaultValue(false)
            .build());

        accentColor = this.color("accent", "Main color of the GUI.", new SettingColor(6, 182, 212));
        accentSecondaryColor = this.color("accent-secondary", "Second color of accent gradients.", new SettingColor(34, 211, 238));
        checkboxColor = this.color("checkbox", "Color of a switched-on checkbox.", new SettingColor(6, 182, 212));
        checkboxOffColor = this.color("checkbox-off", "Color of a switched-off checkbox.", new SettingColor(60, 60, 74));
        plusColor = this.color("plus", "Color of plus button.", new SettingColor(52, 211, 153));
        minusColor = this.color("minus", "Color of minus button.", new SettingColor(239, 68, 68));
        favoriteColor = this.color("favorite", "Color of checked favorite button.", new SettingColor(245, 158, 11));
        textColor = this.color(this.sgText, "text", "Color of text.", new SettingColor(235, 235, 240));
        textSecondaryColor = this.color(this.sgText, "text-secondary-text", "Color of secondary text.", new SettingColor(150, 150, 162));
        textHighlightColor = this.color(this.sgText, "text-highlight", "Color of text highlighting.", new SettingColor(6, 182, 212, 90));
        titleTextColor = this.color(this.sgText, "title-text", "Color of title text.", new SettingColor(255, 255, 255));
        loggedInColor = this.color(this.sgText, "logged-in-text", "Color of logged in account name.", new SettingColor(52, 211, 153));
        placeholderColor = this.color(this.sgText, "placeholder", "Color of placeholder text.", new SettingColor(255, 255, 255, 45));
        windowBackground = this.color(this.sgBackground, "window-background", "Body color of windows.", new SettingColor(17, 17, 22, 235));
        headerBackground = this.color(this.sgBackground, "header-background", "Color of window headers, the tab bar and tooltips.", new SettingColor(24, 24, 31, 250));
        this.widgetBackground =
                new StateColors(
                        this.sgBackground,
                        "background",
                        new SettingColor(32, 32, 40),
                        new SettingColor(42, 42, 52),
                        new SettingColor(52, 52, 64));
        moduleHovered = this.color(this.sgBackground, "module-hovered", "Color of a module row under the mouse.", new SettingColor(255, 255, 255, 14));
        moduleActive = this.color(this.sgBackground, "module-active", "Color of an enabled module row.", new SettingColor(6, 182, 212, 36));
        shadowColor = this.color(this.sgBackground, "shadow", "Color of window shadows.", new SettingColor(0, 0, 0, 110));
        this.outlineColor =
                new StateColors(
                        this.sgOutline,
                        "outline",
                        new SettingColor(50, 50, 62),
                        new SettingColor(74, 74, 90),
                        new SettingColor(6, 182, 212));
        separatorText = this.color(this.sgSeparator, "separator-text", "Color of separator text.", new SettingColor(150, 150, 162));
        separatorCenter = this.color(this.sgSeparator, "separator-center", "Center color of separators.", new SettingColor(255, 255, 255, 70));
        separatorEdges = this.color(this.sgSeparator, "separator-edges", "Color of separator edges.", new SettingColor(255, 255, 255, 0));
        this.scrollbarColor =
                new StateColors(
                        this.sgScrollbar,
                        "scrollbar",
                        new SettingColor(255, 255, 255, 40),
                        new SettingColor(255, 255, 255, 70),
                        new SettingColor(255, 255, 255, 100));
        this.sliderHandle =
                new StateColors(
                        this.sgSlider,
                        "slider-handle",
                        new SettingColor(240, 240, 245),
                        new SettingColor(255, 255, 255),
                        new SettingColor(6, 182, 212));
        sliderLeft = this.color(this.sgSlider, "slider-left", "Color of slider left part.", new SettingColor(6, 182, 212));
        sliderRight = this.color(this.sgSlider, "slider-right", "Color of slider right part.", new SettingColor(52, 52, 64));
        starscriptText = this.color(this.sgStarscript, "starscript-text", "Color of text in Starscript code.", new SettingColor(169, 183, 198));
        starscriptBraces = this.color(this.sgStarscript, "starscript-braces", "Color of braces in Starscript code.", new SettingColor(150, 150, 150));
        starscriptParenthesis = this.color(this.sgStarscript, "starscript-parenthesis", "Color of parenthesis in Starscript code.", new SettingColor(169, 183, 198));
        starscriptDots = this.color(this.sgStarscript, "starscript-dots", "Color of dots in starscript code.", new SettingColor(169, 183, 198));
        starscriptCommas = this.color(this.sgStarscript, "starscript-commas", "Color of commas in starscript code.", new SettingColor(169, 183, 198));
        starscriptOperators = this.color(this.sgStarscript, "starscript-operators", "Color of operators in Starscript code.", new SettingColor(169, 183, 198));
        starscriptStrings = this.color(this.sgStarscript, "starscript-strings", "Color of strings in Starscript code.", new SettingColor(106, 135, 89));
        starscriptNumbers = this.color(this.sgStarscript, "starscript-numbers", "Color of numbers in Starscript code.", new SettingColor(104, 141, 187));
        starscriptKeywords = this.color(this.sgStarscript, "starscript-keywords", "Color of keywords in Starscript code.", new SettingColor(204, 120, 50));
        starscriptAccessedObjects = this.color(this.sgStarscript, "starscript-accessed-objects", "Color of accessed objects (before a dot) in Starscript code.", new SettingColor(152, 118, 170));
        this.settingsFactory = new DefaultSettingsWidgetFactory(this);
    }

    private Setting<SettingColor> color(
            SettingGroup group, String name, String description, SettingColor color) {
        return group.add(
                new ColorSetting.Builder()
                        .name(name + "-color")
                        .description(description)
                        .defaultValue(color)
                        .build());
    }

    private Setting<SettingColor> color(String name, String description, SettingColor color) {
        return this.color(this.sgColors, name, description, color);
    }

    public double radius() {
        return this.scale(this.cornerRadius.get());
    }

    public double smallRadius() {
        return Math.min(this.radius(), this.scale(4));
    }

    public double topBarPad() {
        return this.scale(3);
    }

    public double topBarHeight() {
        return this.topBarPad() * 2 + this.pad() * 2 + this.textHeight();
    }

    @Override
    public WWindow window(WWidget icon, String title) {
        return this.w(new WBepWindow(icon, title));
    }

    @Override
    public WLabel label(String text, boolean title, double maxWidth) {
        return maxWidth == 0 && !text.contains("\n")
                ? this.w(new WBepLabel(text, title))
                : this.w(new WBepMultiLabel(text, title, maxWidth));
    }

    @Override
    public WHorizontalSeparator horizontalSeparator(String text) {
        return this.w(new WBepHorizontalSeparator(text));
    }

    @Override
    public WVerticalSeparator verticalSeparator() {
        return this.w(new WBepVerticalSeparator());
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return this.w(new WBepButton(text, texture));
    }

    @Override
    protected WConfirmedButton confirmedButton(
            String text, String confirmText, GuiTexture texture) {
        return this.w(new WBepConfirmedButton(text, confirmText, texture));
    }

    @Override
    public WMinus minus() {
        return this.w(new WBepMinus());
    }

    @Override
    public WConfirmedMinus confirmedMinus() {
        return this.w(new WBepConfirmedMinus());
    }

    @Override
    public WPlus plus() {
        return this.w(new WBepPlus());
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return this.w(new WBepCheckbox(checked));
    }

    @Override
    public WSlider slider(double value, double min, double max) {
        return this.w(new WBepSlider(value, min, max));
    }

    @Override
    public WTextBox textBox(
            String text,
            String placeholder,
            CharFilter filter,
            Class<? extends WTextBox.Renderer> renderer) {
        return this.w(new WBepTextBox(text, placeholder, filter, renderer));
    }

    @Override
    public <T> WDropdown<T> dropdown(T[] values, T value) {
        return this.w(new WBepDropdown<>(values, value));
    }

    @Override
    public WTriangle triangle() {
        return this.w(new WBepTriangle());
    }

    @Override
    public WTooltip tooltip(String text) {
        return this.w(new WBepTooltip(text));
    }

    @Override
    public WView view() {
        return this.w(new WBepView());
    }

    @Override
    public WSection section(String title, boolean expanded, WWidget headerWidget) {
        return this.w(new WBepSection(title, expanded, headerWidget));
    }

    @Override
    public WAccount account(WidgetScreen screen, Account<?> account) {
        return this.w(new WBepAccount(screen, account));
    }

    @Override
    public WWidget module(Module module, String title) {
        return this.w(new WBepModule(module, title));
    }

    @Override
    public WQuad quad(Color color) {
        return this.w(new WBepQuad(color));
    }

    @Override
    public WTopBar topBar() {
        return this.w(new WBepTopBar());
    }

    @Override
    public WFavorite favorite(boolean checked) {
        return this.w(new WBepFavorite(checked));
    }

    @Override
    public TabScreen modulesScreen() {
        return new BepModulesScreen(this);
    }

    @Override
    public boolean isModulesScreen(Screen screen) {
        return screen instanceof BepModulesScreen;
    }

    @Override
    public Color textColor() {
        return this.textColor.get();
    }

    @Override
    public Color textSecondaryColor() {
        return this.textSecondaryColor.get();
    }

    @Override
    public Color starscriptTextColor() {
        return this.starscriptText.get();
    }

    @Override
    public Color starscriptBraceColor() {
        return this.starscriptBraces.get();
    }

    @Override
    public Color starscriptParenthesisColor() {
        return this.starscriptParenthesis.get();
    }

    @Override
    public Color starscriptDotColor() {
        return this.starscriptDots.get();
    }

    @Override
    public Color starscriptCommaColor() {
        return this.starscriptCommas.get();
    }

    @Override
    public Color starscriptOperatorColor() {
        return this.starscriptOperators.get();
    }

    @Override
    public Color starscriptStringColor() {
        return this.starscriptStrings.get();
    }

    @Override
    public Color starscriptNumberColor() {
        return this.starscriptNumbers.get();
    }

    @Override
    public Color starscriptKeywordColor() {
        return this.starscriptKeywords.get();
    }

    @Override
    public Color starscriptAccessedObjectColor() {
        return this.starscriptAccessedObjects.get();
    }

    @Override
    public TextRenderer textRenderer() {
        return TextRenderer.get();
    }

    @Override
    public double scale(double value) {
        return value * this.scale.get();
    }

    @Override
    public boolean categoryIcons() {
        return this.categoryIcons.get();
    }

    @Override
    public boolean modulesHelpText() {
        return this.modulesHelpText.get();
    }

    @Override
    public boolean hideHUD() {
        return this.hideHUD.get();
    }

    public enum ActiveIndicator implements DescribedOption {
        Bar,
        Dot,
        Both;

        @Override
        public String description() {
            return switch (this) {
                case Bar -> "Mark enabled modules with a bar.";
                case Dot -> "Mark enabled modules with a dot.";
                case Both -> "Mark enabled modules with a bar and dot.";
            };
        }
    }

    public class StateColors {
        private final Setting<SettingColor> normal;
        private final Setting<SettingColor> hovered;
        private final Setting<SettingColor> pressed;

        public StateColors(
                SettingGroup group,
                String name,
                SettingColor c1,
                SettingColor c2,
                SettingColor c3) {
            this.normal = BepGuiTheme.this.color(group, name, "Color of " + name + ".", c1);
            this.hovered =
                    BepGuiTheme.this.color(
                            group, "hovered-" + name, "Color of " + name + " when hovered.", c2);
            this.pressed =
                    BepGuiTheme.this.color(
                            group, "pressed-" + name, "Color of " + name + " when pressed.", c3);
        }

        public SettingColor get() {
            return this.normal.get();
        }

        public SettingColor get(boolean pressed, boolean hovered, boolean bypassDisableHoverColor) {
            if (pressed) {
                return this.pressed.get();
            } else {
                return !hovered || !bypassDisableHoverColor && BepGuiTheme.this.disableHoverColor
                        ? this.normal.get()
                        : this.hovered.get();
            }
        }

        public SettingColor get(boolean pressed, boolean hovered) {
            return this.get(pressed, hovered, false);
        }
    }
}
