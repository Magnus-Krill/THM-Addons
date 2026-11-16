/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import net.minecraft.client.Minecraft;
import xyz.thm.addon.settings.DescribedOption;
import xyz.thm.addon.system.THMSystem;

public enum ClickGuiAnimation implements DescribedOption {
    Default("Use Meteor's original transition."),
    Fade("Fade the Modules screen in and out."),
    Scatter("Fly the panels in from random points beyond the screen edges."),
    Drop("Slide the panels in from above."),
    Unfold("Expand the panels from their headers."),
    Split("Slide the panels in from both sides."),
    Ripple("Lift the panels into place from left to right.");

    // Vanilla extracts Meteor's item icons now and composites them later.
    public static int itemTint = -1;

    public interface ItemTint {
        int thm$itemTint();
    }

    public interface ScatterSeed {
        int thm$scatterSeed();
    }

    private final String description;

    ClickGuiAnimation(String description) {
        this.description = description;
    }

    @Override
    public String description() {
        return description;
    }

    public static ClickGuiAnimation forScreen(WidgetScreen screen) {
        GuiTheme theme = GuiThemes.get();
        if (theme == null || !theme.isModulesScreen(screen)) return Default;
        THMSystem system = THMSystem.get();
        return system == null ? Default : system.clickGuiAnimation.get();
    }

    public static WidgetScreen transitioningScreen() {
        return Minecraft.getInstance().gui.screen() instanceof WidgetScreen screen
            && screen.animProgress < 1 && forScreen(screen) != Default ? screen : null;
    }

    public double panelProgress(double progress, double x, double viewportWidth) {
        if (this == Ripple) {
            double delay = 0.34 * Math.clamp(x / Math.max(1, viewportWidth), 0, 1);
            progress = Math.clamp((progress - delay) / (1 - delay), 0, 1);
        }
        return progress * progress * (3 - 2 * progress);
    }

    public double startX(double x, double width, double viewportWidth, int seed) {
        return switch (this) {
            case Scatter -> switch (seed >>> 30) {
                case 0 -> -width;
                case 1 -> viewportWidth;
                default -> scatterPosition(seed, viewportWidth, width);
            };
            case Split -> x + width / 2 < viewportWidth / 2 ? -width : viewportWidth;
            default -> x;
        };
    }

    public double startY(double y, double height, double viewportHeight, int seed) {
        return switch (this) {
            case Scatter -> switch (seed >>> 30) {
                case 2 -> -height;
                case 3 -> viewportHeight;
                default -> scatterPosition(seed, viewportHeight, height);
            };
            case Drop -> -height;
            case Ripple -> y + viewportHeight * 0.10;
            default -> y;
        };
    }

    public static int scatterPanelSeed(int transitionSeed, String windowId) {
        int mixed = (transitionSeed ^ windowId.hashCode()) * 0x9e3779b9;
        return mixed ^ (mixed >>> 16);
    }

    private static double scatterPosition(int seed, double viewportSize, double panelSize) {
        double fraction = Integer.toUnsignedLong(seed << 2) * 0x1.0p-32;
        return viewportSize * (0.1 + 0.8 * fraction) - panelSize / 2;
    }
}
