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
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
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
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> netherDiamond = sgNether.add(new BoolSetting.Builder()
        .name("Render Diamond Highway")
        .description("Draw diamond highways")
        .defaultValue(true)
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

    public static String highwayType(double x, double z, boolean nether, HorizontalDirection direction) {
        String route = highwayRouteAt(x, z, nether, direction);
        if (route.startsWith("ring-")) return "ring";
        if (route.startsWith("diamond-")) return "diamond";
        return route.equals("main") ? "main" : "other";
    }

    static String highwayRoute(Vec3 start, Vec3 end, boolean nether, HorizontalDirection direction) {
        if (direction == null) return "other";
        double crossTrack = (end.x - start.x) * direction.offsetZ - (end.z - start.z) * direction.offsetX;
        if (Math.abs(crossTrack) >= 10) return "other";
        String route = highwayRouteAt(start.x, start.z, nether, direction);
        if (route.equals(highwayRouteAt(end.x, end.z, nether, direction))) return route;
        if (nether && isOnGridLine(start.x - 0.5, start.z - 0.5, direction)
            && isOnGridLine(end.x - 0.5, end.z - 0.5, direction)) return "grid";
        return "other";
    }

    // Build direction disambiguates roads where their mapped lines intersect.
    static String highwayRouteAt(double x, double z, boolean nether, HorizontalDirection direction) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || direction == null) return "other";
        x -= 0.5;
        z -= 0.5;
        double ax = Math.abs(x), az = Math.abs(z);
        if (Math.max(ax, az) >= 30_000_005) return "other";
        int dx = direction.offsetX, dz = direction.offsetZ;
        if (ax < 5 && dx == 0 || az < 5 && dz == 0
            || Math.abs(x - z) < 5 && dx == dz || Math.abs(x + z) < 5 && dx == -dz) return "main";
        if (!nether) return "other";
        for (int radius : RING_ROADS) {
            if (dx == 0 && Math.abs(ax - radius) < 5 && az < radius + 5
                || dz == 0 && Math.abs(az - radius) < 5 && ax < radius + 5) return "ring-" + radius;
        }
        for (int radius : DIAMONDS) {
            if (dx != 0 && dz != 0 && Math.abs(ax + az - radius) < 5
                && (ax < 5 || az < 5 || Math.signum(x) * dx + Math.signum(z) * dz == 0)) return "diamond-" + radius;
        }
        return isOnGridLine(x, z, direction) ? "grid" : "other";
    }

    private static boolean isOnGridLine(double x, double z, HorizontalDirection direction) {
        if (direction.diagonal) return false;
        double line = direction.offsetX == 0 ? x : z;
        double along = Math.abs(direction.offsetX == 0 ? z : x);
        return (Math.abs(line) < GRID_RADIUS + 5 && along < GRID_RADIUS + 5
                && Math.abs(line - Math.round(line / GRID_STEP) * GRID_STEP) < 5)
            || (Math.abs(Math.abs(line) - GRID_RADIUS) < 5 && along < EXTENSION_RADIUS + 5);
    }

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

        boolean nether = dimension == Dimension.Nether;
        visitHighwaySegments(offset, axisType.cardinals() || cardinals, axisType.diagonals() || diagonals,
            nether && netherRing.get(), nether && netherDiamond.get(), nether && netherGrid.get(), nether && netherExtensions.get(),
            (x1, z1, x2, z2) -> drawLine(event, x1, z1, x2, z2, y, lineColor));
    }

    @FunctionalInterface
    interface HighwaySegmentConsumer {
        void accept(double x1, double z1, double x2, double z2);
    }

    private static void visitHighwaySegments(double offset, boolean cardinals, boolean diagonals, boolean ring,
                                             boolean diamond, boolean grid, boolean extensions, HighwaySegmentConsumer segments) {
        if (cardinals) {
            segments.accept(-30_000_000, offset, 30_000_000, offset);
            segments.accept(offset, -30_000_000, offset, 30_000_000);
        }
        if (diagonals) {
            segments.accept(-30_000_000 + offset, -30_000_000 + offset,
                30_000_000 + offset, 30_000_000 + offset);
            segments.accept(-30_000_000, 30_000_000 + 2 * offset,
                30_000_000, -30_000_000 + 2 * offset);
        }
        if (ring) {
            for (int i = 0; i < RING_ROADS.size(); i++) {
                double r = RING_ROADS.getInt(i);
                double low = -r + offset, high = r + offset;
                segments.accept(low, low, high, low);
                segments.accept(low, high, high, high);
                segments.accept(low, low, low, high);
                segments.accept(high, low, high, high);
            }
        }
        if (diamond) {
            for (int i = 0; i < DIAMONDS.size(); i++) {
                double d = DIAMONDS.getInt(i);
                segments.accept(d + offset, offset, offset, d + offset);
                segments.accept(offset, d + offset, -d + offset, offset);
                segments.accept(-d + offset, offset, offset, -d + offset);
                segments.accept(offset, -d + offset, d + offset, offset);
            }
        }
        visitGridSegments(offset, grid, extensions, segments);
    }

    static void visitGridSegments(double offset, boolean grid, boolean extensions, HighwaySegmentConsumer segments) {
        if (grid) {
            for (int coordinate = -GRID_RADIUS; coordinate < GRID_RADIUS; coordinate += GRID_STEP) {
                if (coordinate == 0) continue;
                segments.accept(coordinate + offset, -GRID_RADIUS + offset,
                    coordinate + offset, GRID_RADIUS + offset);
                segments.accept(-GRID_RADIUS + offset, coordinate + offset,
                    GRID_RADIUS + offset, coordinate + offset);
            }
        }
        if (extensions) {
            for (int side = -1; side <= 1; side += 2) {
                for (int direction = -1; direction <= 1; direction += 2) {
                    double start = direction * GRID_RADIUS + offset;
                    double end = direction * EXTENSION_RADIUS + offset;
                    double fixed = side * GRID_RADIUS + offset;
                    segments.accept(start, fixed, end, fixed);
                    segments.accept(fixed, start, fixed, end);
                }
            }
        }
    }

    record StartAlignment(int x, int z, HorizontalDirection direction) {
        double crossTrackDistance(double playerX, double playerZ) {
            return Math.abs((playerX - x - 0.5) * direction.offsetZ - (playerZ - z - 0.5) * direction.offsetX)
                / Math.hypot(direction.offsetX, direction.offsetZ);
        }
    }

    static StartAlignment nearestStartAlignment(double x, double z, float yaw, boolean nether, boolean allowDiagonal) {
        return nearestStartAlignment(x, z, yaw, nether, allowDiagonal, null);
    }

    static StartAlignment nearestStartAlignment(double x, double z, float yaw, boolean nether, boolean allowDiagonal, HorizontalDirection requiredDirection) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Float.isFinite(yaw)) return null;
        var search = new HighwayAlignmentSearch(x, z, yaw, requiredDirection);
        visitHighwaySegments(0.5, true, allowDiagonal, nether, nether && allowDiagonal, nether, nether, search);
        return search.best;
    }

    private static final class HighwayAlignmentSearch implements HighwaySegmentConsumer {
        private final double x, z;
        private final float yaw;
        private final HorizontalDirection requiredDirection;
        private double bestDistance = Double.POSITIVE_INFINITY;
        private float bestYawDifference = Float.POSITIVE_INFINITY;
        private StartAlignment best;

        private HighwayAlignmentSearch(double x, double z, float yaw, HorizontalDirection requiredDirection) {
            this.x = x;
            this.z = z;
            this.yaw = yaw;
            this.requiredDirection = requiredDirection;
        }

        @Override
        public void accept(double x1, double z1, double x2, double z2) {
            double dx = x2 - x1, dz = z2 - z1;
            double projection = ((x - x1) * dx + (z - z1) * dz) / (dx * dx + dz * dz);
            double from = dx != 0 ? x1 : z1;
            double to = dx != 0 ? x2 : z2;
            // Round along one axis so both coordinates stay on the same diagonal.
            double firstCenter = Math.ceil(Math.min(from, to) - 0.5) + 0.5;
            double lastCenter = Math.floor(Math.max(from, to) - 0.5) + 0.5;
            double center = Mth.clamp(Math.floor(from + projection * (to - from)) + 0.5, firstCenter, lastCenter);
            double along = (center - from) / (to - from);
            int goalX = Mth.floor(x1 + dx * along);
            int goalZ = Mth.floor(z1 + dz * along);
            double offsetX = x - (goalX + 0.5), offsetZ = z - (goalZ + 0.5);
            double distance = offsetX * offsetX + offsetZ * offsetZ;
            if (distance > bestDistance) return;

            HorizontalDirection forward = HorizontalDirection.get((float) Math.toDegrees(Math.atan2(-dx, dz)));
            HorizontalDirection backward = forward.opposite();
            if (requiredDirection != null && requiredDirection != forward && requiredDirection != backward) return;
            float forwardTurn = Math.abs(Mth.wrapDegrees(yaw - forward.yaw));
            float backwardTurn = Math.abs(Mth.wrapDegrees(yaw - backward.yaw));
            boolean atStart = center == (from < to ? firstCenter : lastCenter);
            boolean atEnd = center == (from < to ? lastCenter : firstCenter);
            boolean useForward = requiredDirection != null ? requiredDirection == forward : atStart || !atEnd && forwardTurn <= backwardTurn;
            float turn = useForward ? forwardTurn : backwardTurn;
            if (distance == bestDistance && turn >= bestYawDifference) return;
            bestDistance = distance;
            bestYawDifference = turn;
            best = new StartAlignment(goalX, goalZ, useForward ? forward : backward);
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

