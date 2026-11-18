/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

// BepHax 0.5.5 theme by Dekto, adapted for Meteor 26.2.
// https://bep.dek.to/changelog 
//To Dekto, if you want this removed then open a issue and ill remove it

package xyz.thm.addon.gui.themes.bephax;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

import net.minecraft.util.Mth;

import java.util.Objects;

public final class BepRender {
    public static final int TOP_LEFT = 1;
    public static final int TOP_RIGHT = 2;
    public static final int BOTTOM_LEFT = 4;
    public static final int BOTTOM_RIGHT = 8;
    public static final int TOP = 3;
    public static final int BOTTOM = 12;
    public static final int ALL = 15;
    private static final Color PX = new Color();
    private static final Color ALPHA = new Color();
    private static final Color LERP = new Color();

    private BepRender() {}

    public static void fill(
            GuiRenderer r, double x, double y, double w, double h, double radius, Color color) {
        Objects.requireNonNull(r);
        fill(r::quad, x, y, w, h, radius, 15, color);
    }

    public static void fill(
            GuiRenderer r,
            double x,
            double y,
            double w,
            double h,
            double radius,
            int corners,
            Color color) {
        Objects.requireNonNull(r);
        fill(r::quad, x, y, w, h, radius, corners, color);
    }

    public static void fill(
            QuadSink r,
            double x,
            double y,
            double w,
            double h,
            double radius,
            int corners,
            Color color) {
        if (!(w <= 0) && !(h <= 0) && color.a > 0) {
            int rad = radius(radius, w, h);
            if (rad != 0 && corners != 0) {
                double cw = w - (2 * rad);
                double mh = h - (2 * rad);
                if (mh > 0) {
                    r.quad(x, y + rad, w, mh, color);
                }

                if (cw > 0) {
                    r.quad(x + rad, y, cw, rad, color);
                    r.quad(x + rad, y + h - rad, cw, rad, color);
                }

                corner(r, x, y, rad, 0, false, false, (corners & 1) != 0, color);
                corner(r, x + w - rad, y, rad, 0, true, false, (corners & 2) != 0, color);
                corner(r, x, y + h - rad, rad, 0, false, true, (corners & 4) != 0, color);
                corner(r, x + w - rad, y + h - rad, rad, 0, true, true, (corners & 8) != 0, color);
            } else {
                r.quad(x, y, w, h, color);
            }
        }
    }

    public static void outline(
            GuiRenderer r,
            double x,
            double y,
            double w,
            double h,
            double radius,
            double thickness,
            Color color) {
        Objects.requireNonNull(r);
        outline(r::quad, x, y, w, h, radius, thickness, 15, color);
    }

    public static void outline(
            GuiRenderer r,
            double x,
            double y,
            double w,
            double h,
            double radius,
            double thickness,
            int corners,
            Color color) {
        Objects.requireNonNull(r);
        outline(r::quad, x, y, w, h, radius, thickness, corners, color);
    }

    public static void outline(
            QuadSink r,
            double x,
            double y,
            double w,
            double h,
            double radius,
            double thickness,
            int corners,
            Color color) {
        if (!(w <= 0) && !(h <= 0) && color.a > 0 && !(thickness <= 0)) {
            int rad = radius(radius, w, h);
            double t = Math.min(thickness, Math.min(w, h) / 2);
            if (rad != 0 && corners != 0) {
                t = Math.min(t, rad);
                double cw = w - (2 * rad);
                double mh = h - (2 * rad);
                if (cw > 0) {
                    r.quad(x + rad, y, cw, t, color);
                    r.quad(x + rad, y + h - t, cw, t, color);
                }

                if (mh > 0) {
                    r.quad(x, y + rad, t, mh, color);
                    r.quad(x + w - t, y + rad, t, mh, color);
                }

                corner(r, x, y, rad, t, false, false, (corners & 1) != 0, color);
                corner(r, x + w - rad, y, rad, t, true, false, (corners & 2) != 0, color);
                corner(r, x, y + h - rad, rad, t, false, true, (corners & 4) != 0, color);
                corner(r, x + w - rad, y + h - rad, rad, t, true, true, (corners & 8) != 0, color);
            } else {
                box(r, x, y, w, h, t, color);
            }
        }
    }

    public static void circle(GuiRenderer r, double x, double y, double diameter, Color color) {
        if (!(diameter <= 0) && color.a > 0) {
            r.quad(x, y, diameter, diameter, GuiRenderer.CIRCLE, color);
        }
    }

    public static Color alpha(Color color, double mul) {
        return ALPHA.set(
                color.r, color.g, color.b, (int) Math.round(color.a * Mth.clamp(mul, 0, 1)));
    }

    public static Color lerp(Color a, Color b, double t) {
        t = Mth.clamp(t, 0, 1);
        return LERP.set(
                (int) Math.round(a.r + (b.r - a.r) * t),
                (int) Math.round(a.g + (b.g - a.g) * t),
                (int) Math.round(a.b + (b.b - a.b) * t),
                (int) Math.round(a.a + (b.a - a.a) * t));
    }

    private static int radius(double radius, double w, double h) {
        return (int) Math.max(0, Math.min(Math.round(radius), Math.floor(Math.min(w, h) / 2)));
    }

    private static void box(
            QuadSink r, double x, double y, double w, double h, double t, Color color) {
        r.quad(x, y, w, t, color);
        r.quad(x, y + h - t, w, t, color);
        r.quad(x, y + t, t, h - 2 * t, color);
        r.quad(x + w - t, y + t, t, h - 2 * t, color);
    }

    private static void corner(
            QuadSink r,
            double cx,
            double cy,
            int rad,
            double t,
            boolean right,
            boolean bottom,
            boolean round,
            Color color) {
        if (!round) {
            if (t <= 0) {
                r.quad(cx, cy, rad, rad, color);
            } else {
                px(r, cx, cy, rad, right, bottom, 0, 0, rad, t, color, 1);
                px(r, cx, cy, rad, right, bottom, 0, t, t, rad - t, color, 1);
            }

        } else {
            double ri = t <= 0 ? 0 : Math.max(0, rad - t);
            double rr = rad * rad;
            double rri = ri * ri;
            int split = (int) Math.ceil(rad - rad / Math.sqrt(2));

            for (int j = split; j < rad; ++j) {
                double d = rad - (j + 0.5);
                double a = rad - Math.sqrt(rr - d * d);
                double b = d < ri ? rad - Math.sqrt(rri - d * d) : rad;
                run(r, cx, cy, rad, right, bottom, j, a, b, color, true);
            }

            for (int i = 0; i < rad; ++i) {
                double d = rad - (i + 0.5);
                double a = rad - Math.sqrt(rr - d * d);
                if (!(a >= split)) {
                    double b = Math.min(split, d < ri ? rad - Math.sqrt(rri - d * d) : rad);
                    run(r, cx, cy, rad, right, bottom, i, a, b, color, false);
                }
            }
        }
    }

    private static void run(
            QuadSink r,
            double cx,
            double cy,
            int rad,
            boolean right,
            boolean bottom,
            int line,
            double a,
            double b,
            Color color,
            boolean horizontal) {
        if (!(b - a <= 0)) {
            int i0 = (int) Math.ceil(a);
            int i1 = (int) Math.floor(b);
            if (i0 > i1) {
                cell(r, cx, cy, rad, right, bottom, line, i0 - 1, 1, color, b - a, horizontal);
            } else {
                double c0 = i0 - a;
                double c1 = b - i1;
                if (c0 > 0.002) {
                    cell(r, cx, cy, rad, right, bottom, line, i0 - 1, 1, color, c0, horizontal);
                }

                if (i1 > i0) {
                    cell(r, cx, cy, rad, right, bottom, line, i0, i1 - i0, color, 1, horizontal);
                }

                if (c1 > 0.002 && i1 < rad) {
                    cell(r, cx, cy, rad, right, bottom, line, i1, 1, color, c1, horizontal);
                }
            }
        }
    }

    private static void cell(
            QuadSink r,
            double cx,
            double cy,
            int rad,
            boolean right,
            boolean bottom,
            int line,
            int start,
            int length,
            Color color,
            double cov,
            boolean horizontal) {
        if (horizontal) {
            px(r, cx, cy, rad, right, bottom, start, line, length, 1, color, cov);
        } else {
            px(r, cx, cy, rad, right, bottom, line, start, 1, length, color, cov);
        }
    }

    private static void px(
            QuadSink r,
            double cx,
            double cy,
            int rad,
            boolean right,
            boolean bottom,
            double u,
            double v,
            double uw,
            double vh,
            Color color,
            double cov) {
        if (!(uw <= 0) && !(vh <= 0)) {
            double sx = right ? cx + rad - u - uw : cx + u;
            double sy = bottom ? cy + rad - v - vh : cy + v;
            Color c = color;
            if (cov < 1) {
                c = PX.set(color.r, color.g, color.b, (int) Math.round(color.a * cov));
            }

            r.quad(sx, sy, uw, vh, c);
        }
    }

    public interface QuadSink {
        void quad(double x, double y, double width, double height, Color color);
    }
}
