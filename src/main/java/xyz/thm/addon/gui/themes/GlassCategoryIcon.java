/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.gui.themes;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

final class GlassCategoryIcon extends WWidget {
    private final Glyph glyph;
    private final Color color;

    GlassCategoryIcon(String category, Color color) {
        this.glyph = switch (category) {
            case "Combat" -> Glyph.SWORD;
            case "Player" -> Glyph.PERSON;
            case "Movement" -> Glyph.CHEVRONS;
            case "Render" -> Glyph.EYE;
            case "World" -> Glyph.GLOBE;
            case "THM Highway" -> Glyph.ROAD;
            case "THM PVP" -> Glyph.SHIELD;
            case "search" -> Glyph.SEARCH;
            case "favorites" -> Glyph.STAR;
            default -> Glyph.GRID;
        };
        this.color = color;
    }

    @Override
    protected void onCalculateSize() {
        width = height = theme.scale(18);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double unit = width / 16;
        for (int[] path : glyph.paths) {
            for (int i = 2; i < path.length; i += 2) {
                double ax = x + path[i - 2] * unit, ay = y + path[i - 1] * unit;
                double bx = x + path[i] * unit, by = y + path[i + 1] * unit;
                double stroke = unit * 0.64 / Math.hypot(bx - ax, by - ay);
                double nx = (ay - by) * stroke, ny = (bx - ax) * stroke;
                renderer.triangle(ax - nx, ay - ny, ax + nx, ay + ny, bx + nx, by + ny, color);
                renderer.triangle(bx + nx, by + ny, bx - nx, by - ny, ax - nx, ay - ny, color);
            }
        }
    }

    private enum Glyph {
        SWORD(new int[]{5, 9, 11, 3, 14, 2, 13, 5, 7, 11}, new int[]{4, 8, 8, 12}, new int[]{6, 10, 2, 14}),
        PERSON(new int[]{8, 2, 10, 3, 11, 5, 10, 7, 8, 8, 6, 7, 5, 5, 6, 3, 8, 2},
            new int[]{2, 14, 3, 12, 5, 10, 11, 10, 13, 12, 14, 14}),
        CHEVRONS(new int[]{2, 4, 6, 8, 2, 12}, new int[]{7, 4, 11, 8, 7, 12}),
        EYE(new int[]{1, 8, 4, 5, 8, 4, 12, 5, 15, 8, 12, 11, 8, 12, 4, 11, 1, 8},
            new int[]{8, 6, 10, 8, 8, 10, 6, 8, 8, 6}),
        GLOBE(new int[]{8, 2, 12, 3, 14, 6, 14, 10, 12, 13, 8, 14, 4, 13, 2, 10, 2, 6, 4, 3, 8, 2},
            new int[]{8, 2, 6, 5, 5, 8, 6, 11, 8, 14, 10, 11, 11, 8, 10, 5, 8, 2}, new int[]{2, 8, 14, 8}),
        GRID(new int[]{3, 3, 6, 3, 6, 6, 3, 6, 3, 3}, new int[]{10, 3, 13, 3, 13, 6, 10, 6, 10, 3},
            new int[]{3, 10, 6, 10, 6, 13, 3, 13, 3, 10}, new int[]{10, 10, 13, 10, 13, 13, 10, 13, 10, 10}),
        ROAD(new int[]{5, 2, 2, 14}, new int[]{11, 2, 14, 14}, new int[]{8, 2, 8, 5}, new int[]{8, 8, 8, 13}),
        SHIELD(new int[]{8, 2, 13, 4, 13, 9, 11, 12, 8, 14, 5, 12, 3, 9, 3, 4, 8, 2},
            new int[]{6, 8, 8, 10, 11, 6}),
        SEARCH(new int[]{6, 2, 9, 3, 11, 5, 11, 8, 9, 10, 6, 11, 3, 10, 2, 8, 2, 5, 3, 3, 6, 2},
            new int[]{10, 10, 14, 14}),
        STAR(new int[]{8, 2, 10, 6, 14, 6, 11, 9, 12, 14, 8, 11, 4, 14, 5, 9, 2, 6, 6, 6, 8, 2});

        private final int[][] paths;

        Glyph(int[]... paths) {
            this.paths = paths;
        }
    }
}
