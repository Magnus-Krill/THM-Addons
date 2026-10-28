/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import it.unimi.dsi.fastutil.ints.IntList;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.settings.DescribedOption;
import xyz.thm.addon.utils.RenderUtilsTHM;

public class AxisViewer extends Module {
    private static final int GRID_RADIUS = 50_000;
    private static final int GRID_STEP = 5_000;
    private static final int EXTENSION_RADIUS = 125_000;
    private final SettingGroup sgOverworld = settings.createGroup("Overworld");
    private final SettingGroup sgNether = settings.createGroup("Nether");
    private final SettingGroup sgEnd = settings.createGroup("End");
    private final double[] clippedLine = new double[4];
    private double minX, minZ, maxX, maxZ;

    // Overworld

    private final Setting<AxisType> overworldAxisTypes = sgOverworld.add(new EnumSetting.Builder<AxisType>()
        .name("render")
        .description("Which axes to display.")
        .defaultValue(AxisType.Both)
        .build()
    );

    private final Setting<Integer> overworldY = sgOverworld.add(new IntSetting.Builder()
        .name("height")
        .description("Y position of the line.")
        .defaultValue(63)
        .sliderMin(-64)
        .sliderMax(319)
        .visible(() -> overworldAxisTypes.get() != AxisType.None)
        .build()
    );

    private final Setting<SettingColor> overworldColor = sgOverworld.add(new ColorSetting.Builder()
        .name("color")
        .description("The line color.")
        .defaultValue(new SettingColor(25, 25, 225, 255))
        .visible(() -> overworldAxisTypes.get() != AxisType.None)
        .build()
    );

    // Nether

    private final Setting<Boolean> netherCardinal = sgNether.add(new BoolSetting.Builder()
        .name("Render Cardinal Highway")
        .description("Draw cardinal highways")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> netherDiagonal = sgNether.add(new BoolSetting.Builder()
        .name("Render Diagonal Highway")
        .description("Draw diagonal highways")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> netherRing = sgNether.add(new BoolSetting.Builder()
        .name("Render Ring Highway")
        .description("Draw ring highways")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> netherDiamond = sgNether.add(new BoolSetting.Builder()
        .name("Render Diamond Highway")
        .description("Draw diamond highways")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> netherGrid = sgNether.add(new BoolSetting.Builder()
        .name("Render 50k Grid")
        .description("Draw highways every 5,000 blocks within the 50,000-block grid.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> netherExtensions = sgNether.add(new BoolSetting.Builder()
        .name("Render 50k Extensions")
        .description("Extend the 50,000-block roads out to 125,000 blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> netherColor = sgNether.add(new ColorSetting.Builder()
        .name("color")
        .description("The line color.")
        .defaultValue(new SettingColor(225, 25, 25, 255))
        .build()
    );

    private final Setting<Boolean> highwayCenterMode = sgNether.add(new BoolSetting.Builder()
        .name("Draw True Center: ")
        .description("Draw lines through block centers.")
        .defaultValue(true)
        .build()
    );

    // End

    private final Setting<AxisType> endAxisTypes = sgEnd.add(new EnumSetting.Builder<AxisType>()
        .name("render")
        .description("Which axes to display.")
        .defaultValue(AxisType.Both)
        .build()
    );

    private final Setting<Integer> endY = sgEnd.add(new IntSetting.Builder()
        .name("height")
        .description("Y position of the line.")
        .defaultValue(64)
        .sliderMin(0)
        .sliderMax(255)
        .visible(() -> endAxisTypes.get() != AxisType.None)
        .build()
    );

    private final Setting<SettingColor> endColor = sgEnd.add(new ColorSetting.Builder()
        .name("color")
        .description("The line color.")
        .defaultValue(new SettingColor(225, 25, 25, 255))
        .visible(() -> endAxisTypes.get() != AxisType.None)
        .build()
    );

    public AxisViewer() {
        super(THMAddon.MAIN, "axis-viewer", "Displays world axes and Nether highways.");
    }

    // Original rings and diamonds: https://github.com/1exmc.
    // Additional highway data: rfresh2/XaeroPlus (MIT); see licenses/xaeroplus.txt.
    static final IntList RING_ROADS = IntList.of(
        200,
        500,
        750,
        1000,
        1500,
        2000,
        2500,
        3131,
        3500,
        5000,
        6250,
        7500,
        10000,
        12500,
        15000,
        20000,
        25000,
        50000,
        55000,
        62500,
        75000,
        100000,
        125000,
        250000,
        500000,
        750000,
        1000000,
        1250000,
        1568852,
        1875000,
        2500000,
        3750000,
        30000000
    );

    static final IntList DIAMONDS = IntList.of(
        1000,
        2000,
        2500,
        5000,
        10000,
        25000,
        50000,
        125000,
        250000,
        500000,
        3750000
    );

    // Eligibility uses the full network, independent of rendering toggles and camera position.
    public static boolean isOnHighway(double x, double z, boolean nether) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) return false;
        x -= 0.5;
        z -= 0.5;
        double ax = Math.abs(x), az = Math.abs(z);
        if (Math.max(ax, az) >= 30_000_005) return false;
        if (ax < 5 || az < 5 || Math.abs(ax - az) < 5) return true;
        if (!nether) return false;
        for (int radius : RING_ROADS) {
            if (Math.abs(Math.max(ax, az) - radius) < 5) return true;
        }
        for (int radius : DIAMONDS) {
            if (Math.abs(ax + az - radius) < 5) return true;
        }
        if (ax < GRID_RADIUS + 5 && az < GRID_RADIUS + 5
            && (Math.abs(x - Math.round(x / GRID_STEP) * GRID_STEP) < 5
                || Math.abs(z - Math.round(z / GRID_STEP) * GRID_STEP) < 5)) return true;
        return (Math.abs(ax - GRID_RADIUS) < 5 && az < EXTENSION_RADIUS + 5)
            || (Math.abs(az - GRID_RADIUS) < 5 && ax < EXTENSION_RADIUS + 5);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.gui.hud.isHidden()) return;

        Dimension dimension = PlayerUtils.getDimension();
        AxisType axisType;
        int y;
        Color lineColor;
        boolean cardinals = false;
        boolean diagonals = false;
        switch (dimension) {
            case Overworld -> {
                axisType = overworldAxisTypes.get();
                y = overworldY.get();
                lineColor = overworldColor.get();
            }
            case Nether -> {
                axisType = AxisType.None;
                cardinals = netherCardinal.get();
                diagonals = netherDiagonal.get();
                y = 120;
                lineColor = netherColor.get();
            }
            case End -> {
                axisType = endAxisTypes.get();
                y = endY.get();
                lineColor = endColor.get();
            }
            default -> throw new IllegalStateException("Unexpected dimension: " + dimension);
        }
        if (axisType == AxisType.None && dimension != Dimension.Nether) return;

        // Clip around the render camera so Freecam follows the same visible roads.
        double radius = mc.options.getEffectiveRenderDistance() * 16.0;
        minX = event.offsetX - radius;
        minZ = event.offsetZ - radius;
        maxX = event.offsetX + radius;
        maxZ = event.offsetZ + radius;
        double offset = highwayCenterMode.get() ? 0.5 : 0.0;

        if (axisType.cardinals() || cardinals) {
            drawLine(event, -30_000_000, offset, 30_000_000, offset, y, lineColor);
            drawLine(event, offset, -30_000_000, offset, 30_000_000, y, lineColor);
        }
        if (axisType.diagonals() || diagonals) {
            drawLine(event, -30_000_000 + offset, -30_000_000 + offset,
                30_000_000 + offset, 30_000_000 + offset, y, lineColor);
            drawLine(event, -30_000_000, 30_000_000 + 2 * offset,
                30_000_000, -30_000_000 + 2 * offset, y, lineColor);
        }
        if (dimension != Dimension.Nether) return;

        if (netherRing.get()) {
            for (int i = 0; i < RING_ROADS.size(); i++) {
                double r = RING_ROADS.getInt(i);
                double low = -r + offset, high = r + offset;
                drawLine(event, low, low, high, low, y, lineColor);
                drawLine(event, low, high, high, high, y, lineColor);
                drawLine(event, low, low, low, high, y, lineColor);
                drawLine(event, high, low, high, high, y, lineColor);
            }
        }
        if (netherDiamond.get()) {
            for (int i = 0; i < DIAMONDS.size(); i++) {
                double d = DIAMONDS.getInt(i);
                drawLine(event, d + offset, offset, offset, d + offset, y, lineColor);
                drawLine(event, offset, d + offset, -d + offset, offset, y, lineColor);
                drawLine(event, -d + offset, offset, offset, -d + offset, y, lineColor);
                drawLine(event, offset, -d + offset, d + offset, offset, y, lineColor);
            }
        }
        if (netherGrid.get()) {
            for (int coordinate = -GRID_RADIUS; coordinate < GRID_RADIUS; coordinate += GRID_STEP) {
                if (coordinate == 0) continue;
                drawLine(event, coordinate + offset, -GRID_RADIUS + offset,
                    coordinate + offset, GRID_RADIUS + offset, y, lineColor);
                drawLine(event, -GRID_RADIUS + offset, coordinate + offset,
                    GRID_RADIUS + offset, coordinate + offset, y, lineColor);
            }
        }
        if (netherExtensions.get()) {
            for (int side = -1; side <= 1; side += 2) {
                for (int direction = -1; direction <= 1; direction += 2) {
                    double start = direction * GRID_RADIUS + offset;
                    double end = direction * EXTENSION_RADIUS + offset;
                    double fixed = side * GRID_RADIUS + offset;
                    drawLine(event, start, fixed, end, fixed, y, lineColor);
                    drawLine(event, fixed, start, fixed, end, y, lineColor);
                }
            }
        }
    }

    private void drawLine(Render3DEvent event, double x1, double z1, double x2, double z2,
                          double y, Color color) {
        if (!clipLine(x1, z1, x2, z2, minX, minZ, maxX, maxZ, clippedLine)) return;
        RenderUtilsTHM.renderLine(event, clippedLine[0], y, clippedLine[1],
            clippedLine[2], y, clippedLine[3], color);
    }

    static boolean clipLine(double x1, double z1, double x2, double z2,
                            double minX, double minZ, double maxX, double maxZ, double[] output) {
        double dx = x2 - x1, dz = z2 - z1;
        double from = 0, to = 1;
        if (dx == 0) {
            if (x1 < minX || x1 > maxX) return false;
        } else {
            double a = (minX - x1) / dx, b = (maxX - x1) / dx;
            from = Math.max(from, Math.min(a, b));
            to = Math.min(to, Math.max(a, b));
        }
        if (dz == 0) {
            if (z1 < minZ || z1 > maxZ) return false;
        } else {
            double a = (minZ - z1) / dz, b = (maxZ - z1) / dz;
            from = Math.max(from, Math.min(a, b));
            to = Math.min(to, Math.max(a, b));
        }
        if (from > to) return false;
        output[0] = x1 + from * dx;
        output[1] = z1 + from * dz;
        output[2] = x1 + to * dx;
        output[3] = z1 + to * dz;
        return true;
    }

    public enum AxisType implements DescribedOption {
        Both,
        Cardinals,
        Diagonals,
        None;

        boolean cardinals() {
            return this == Both || this == Cardinals;
        }

        boolean diagonals() {
            return this == Both || this == Diagonals;
        }

        @Override
        public String description() {
            return switch (this) {
                case Both -> "Show cardinal and diagonal axes.";
                case Cardinals -> "Show north, south, east, and west axes.";
                case Diagonals -> "Show diagonal axes.";
                case None -> "Hide axes in this dimension.";
            };
        }
    }
}

