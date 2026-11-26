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
    private static final double HALF_STROKE = 0.66;
    private static final double[][][] SWORD = {
        {{5, 9}, {11, 3}, {14, 2}, {13, 5}, {7, 11}, {5, 9}}, {{4, 8}, {8, 12}}, {{6.5, 9.5}, {2, 14}}};
    private static final double[][][] PERSON = {circle(8, 5, 2.7), arc(8, 15, 5.6, 5.6, 180, 360)};
    private static final double[][][] CHEVRONS = {{{2.5, 4}, {6.5, 8}, {2.5, 12}}, {{8, 4}, {12, 8}, {8, 12}}};
    private static final double[][][] EYE = {
        join(bezier(1, 8, 8, 0.5, 15, 8), bezier(15, 8, 8, 15.5, 1, 8)), circle(8, 8, 2.1)};
    private static final double[][][] GLOBE = {circle(8, 8, 6.2), arc(8, 8, 2.9, 6.2, 0, 360), {{1.8, 8}, {14.2, 8}}};
    private static final double[][][] GRID = {square(2.5, 2.5), square(9.5, 2.5), square(2.5, 9.5), square(9.5, 9.5)};
    private static final double[][][] ROAD = {{{5, 2}, {2, 14}}, {{11, 2}, {14, 14}}, {{8, 2}, {8, 5}}, {{8, 8}, {8, 13}}};
    private static final double[][][] SHIELD = {
        join(new double[][]{{8, 1.8}, {13.2, 3.8}, {13.2, 8}}, bezier(13.2, 8, 13, 12, 8, 14.3),
            bezier(8, 14.3, 3, 12, 2.8, 8), new double[][]{{2.8, 3.8}, {8, 1.8}}),
        {{5.6, 8}, {7.5, 10}, {10.7, 5.8}}};
    private static final double[][][] SEARCH = {circle(6.6, 6.6, 4.6), {{10, 10}, {14, 14}}};
    private static final double[][][] STAR = {star(8, 8.6, 6.4, 2.9)};

    private final double[][][] paths;
    private final Color color;
    private final Color soft = new Color();

    GlassCategoryIcon(String category, Color color) {
        this.paths = switch (category) {
            case "Combat" -> SWORD;
            case "Player" -> PERSON;
            case "Movement" -> CHEVRONS;
            case "Render" -> EYE;
            case "World" -> GLOBE;
            case "THM Highway" -> ROAD;
            case "THM PVP" -> SHIELD;
            case "search" -> SEARCH;
            case "favorites" -> STAR;
            default -> GRID;
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
        // Faint wider pass feathers the edge, since GUI triangles are not anti-aliased.
        soft.set(color.r, color.g, color.b, (int) (color.a * 0.28));
        double feather = 0.7 / unit;
        stroke(renderer, unit, HALF_STROKE + feather, soft);
        stroke(renderer, unit, HALF_STROKE, color);
    }

    private void stroke(GuiRenderer renderer, double unit, double half, Color c) {
        double h = half * unit;
        for (double[][] path : paths) {
            for (int i = 0; i < path.length; i++) {
                double ax = x + path[i][0] * unit, ay = y + path[i][1] * unit;
                cap(renderer, ax, ay, h, c);
                if (i == 0) continue;
                double bx = x + path[i - 1][0] * unit, by = y + path[i - 1][1] * unit;
                double len = Math.hypot(ax - bx, ay - by);
                if (len < 1e-6) continue;
                double nx = (by - ay) / len * h, ny = (ax - bx) / len * h;
                renderer.triangle(bx - nx, by - ny, bx + nx, by + ny, ax + nx, ay + ny, c);
                renderer.triangle(ax + nx, ay + ny, ax - nx, ay - ny, bx - nx, by - ny, c);
            }
        }
    }

    /** Round joint, so corners and ends do not show gaps or spikes. */
    private static void cap(GuiRenderer renderer, double cx, double cy, double r, Color c) {
        final int sides = 10;
        for (int i = 0; i < sides; i++) {
            double a = Math.PI * 2 * i / sides, b = Math.PI * 2 * (i + 1) / sides;
            renderer.triangle(cx, cy, cx + Math.cos(a) * r, cy + Math.sin(a) * r, cx + Math.cos(b) * r, cy + Math.sin(b) * r, c);
        }
    }

    private static double[][] arc(double cx, double cy, double rx, double ry, double from, double to) {
        int steps = Math.max(8, (int) Math.ceil(Math.abs(to - from) / 12));
        double[][] out = new double[steps + 1][];
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(from + (to - from) * i / steps);
            out[i] = new double[]{cx + Math.cos(a) * rx, cy + Math.sin(a) * ry};
        }
        return out;
    }

    private static double[][] circle(double cx, double cy, double r) {
        return arc(cx, cy, r, r, 0, 360);
    }

    private static double[][] bezier(double x0, double y0, double cx, double cy, double x1, double y1) {
        double[][] out = new double[17][];
        for (int i = 0; i <= 16; i++) {
            double t = i / 16.0, u = 1 - t;
            out[i] = new double[]{u * u * x0 + 2 * u * t * cx + t * t * x1, u * u * y0 + 2 * u * t * cy + t * t * y1};
        }
        return out;
    }

    private static double[][] join(double[][]... parts) {
        return java.util.Arrays.stream(parts).flatMap(java.util.Arrays::stream).toArray(double[][]::new);
    }

    private static double[][] square(double x, double y) {
        return new double[][]{{x, y}, {x + 4, y}, {x + 4, y + 4}, {x, y + 4}, {x, y}};
    }

    private static double[][] star(double cx, double cy, double outer, double inner) {
        double[][] out = new double[11][];
        for (int i = 0; i < 11; i++) {
            double r = i % 2 == 0 ? outer : inner, a = -Math.PI / 2 + Math.PI * (i % 10) / 5;
            out[i] = new double[]{cx + Math.cos(a) * r, cy + Math.sin(a) * r};
        }
        return out;
    }
}
