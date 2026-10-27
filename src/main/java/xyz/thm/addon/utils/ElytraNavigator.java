/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import xyz.thm.addon.utils.ElytraPathPlanner.Pos;
import xyz.thm.addon.utils.ElytraPathPlanner.Terrain;
import xyz.thm.addon.utils.ElytraPathPlanner.Zone;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Baritone-style steering for Elytra Route: flies straight while the way is clear, otherwise plans around
 * obstacles (A*), follows the path by line-of-sight waypoints, replans when it goes stale, and escapes when
 * it stops making progress. Pure Java — the module only feeds positions in and applies the target out.
 */
public final class ElytraNavigator {
    public enum Mode { DIRECT, PATH, UNSTICK, FAILED }

    /** Where to fly next. {@code vertical}: +1 jump, -1 sneak, 0 none. */
    public record Steer(Mode mode, double x, double y, double z, int vertical) {}

    public static final class Config {
        public int preferredY = 130;
        public int minY = -64;
        public int maxY = 320;
        public int range = 96;
        public double lookahead = 24.0;
        public int maxNodes = 30_000;
        public int nodesPerTick = 800;
        public int verticalSpan = 16;
        public int replanTicks = 40;
        public int stuckTicks = 25;
        public int maxStuck = 8;
        public int noProgressTicks = 1800;
        public double radius = 0.35;
    }

    private record TimedZone(Zone zone, long expires) {}

    private static final double REACH = 1.7;
    private static final double DEVIATION = 5.0;
    private static final double PROGRESS = 1.5;
    private static final double JOIN_DISTANCE = 14.0;
    private static final int DIRECT_DIG_LIMIT = 6;
    private static final int DIRECT_RECHECK_TICKS = 10;
    private static final int[][] PAD = {{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static final int DIG_WINDOW_TICKS = 200;
    private static final double AIR_MIN_PROGRESS = 8.0;
    private static final int WIDE_SEARCH_FACTOR = 3;

    private final Terrain terrain;
    private final Config cfg;
    private final Terrain padded;
    private final Terrain air;
    private final Terrain airPadded;
    // Digging is allowed only for a while after an air-only search found no way around.
    private long digUntil;
    private boolean searchAir;
    private boolean wideAirSearch;

    private long tick;
    private long holdUntil;
    private long cautionUntil;
    private int deadEnds;
    private double bestDist;
    private long bestDistTick;
    private int lastGoalX = Integer.MIN_VALUE, lastGoalZ = Integer.MIN_VALUE;
    private final List<TimedZone> zones = new ArrayList<>();

    private List<Pos> path;
    private int pathIndex;
    private boolean pathPartial;
    private long plannedTick;
    private ElytraPathPlanner.Search search;
    private boolean searchFresh;
    private boolean searchPadded;
    private boolean paddedPlans;
    private boolean retryUnpadded;

    private Pos lastPlanEnd;
    private double lastPlanEndH;

    private double anchorX, anchorY, anchorZ;
    private long anchorTick;
    private int stuckLevel;
    private long lastStuckTick;
    private int unstickLeft;
    private double unstickX, unstickY, unstickZ;
    private double provX, provY, provZ;
    private double slideX, slideY, slideZ;
    private int provLeft;

    public ElytraNavigator(Terrain terrain, Config cfg) {
        this.terrain = terrain;
        this.cfg = cfg;
        this.padded = pad(terrain);
        this.air = new Terrain() {
            @Override
            public boolean blocked(int x, int y, int z) {
                return terrain.blocked(x, y, z);
            }

            @Override
            public boolean loaded(int x, int z) {
                return terrain.loaded(x, z);
            }
        };
        this.airPadded = pad(air);
        reset();
    }

    private static Terrain pad(Terrain terrain) {
        return new Terrain() {
            @Override
            public boolean blocked(int x, int y, int z) {
                return terrain.blocked(x, y, z)
                    || terrain.blocked(x + 1, y, z) || terrain.blocked(x - 1, y, z)
                    || terrain.blocked(x, y + 1, z) || terrain.blocked(x, y - 1, z)
                    || terrain.blocked(x, y, z + 1) || terrain.blocked(x, y, z - 1);
            }

            @Override
            public boolean loaded(int x, int z) {
                return terrain.loaded(x, z);
            }

            @Override
            public float mineCost(int x, int y, int z) {
                float worst = -1f;
                for (int[] d : PAD) {
                    int cx = x + d[0], cy = y + d[1], cz = z + d[2];
                    if (!terrain.blocked(cx, cy, cz)) continue;
                    float c = terrain.mineCost(cx, cy, cz);
                    if (c < 0f) return -1f;
                    worst = Math.max(worst, c);
                }
                return worst;
            }
        };
    }

    private boolean digAllowed() {
        return tick < digUntil;
    }

    /** No usable air route: retry once with a wider search, since a big detour can need more nodes; then allow digging. */
    private void airSearchFailed() {
        if (!wideAirSearch) {
            wideAirSearch = true;
            zones.clear();
            return;
        }
        digUntil = tick + DIG_WINDOW_TICKS;
        wideAirSearch = false;
    }

    /** True while the route may dig because no way through the air was found. */
    public boolean digging() {
        return digAllowed();
    }

    public void reset() {
        zones.clear();
        path = null;
        pathIndex = 0;
        pathPartial = false;
        plannedTick = 0;
        search = null;
        paddedPlans = false;
        retryUnpadded = false;
        lastPlanEnd = null;
        stuckLevel = 0;
        lastStuckTick = 0;
        cautionUntil = 0;
        deadEnds = 0;
        bestDist = Double.MAX_VALUE;
        bestDistTick = 0;
        unstickLeft = 0;
        provLeft = 0;
        anchorTick = -1;
        holdUntil = 0;
        digUntil = 0;
        wideAirSearch = false;
    }

    /** Call while the route is digging: being held up by a block that is being mined does not count as stuck. */
    public void hold(int ticks) {
        holdUntil = Math.max(holdUntil, tick + ticks);
        // A block that is already being broken keeps the dig window open.
        if (digAllowed()) digUntil = Math.max(digUntil, tick + ticks + 40);
    }

    public int stuckLevel() {
        return stuckLevel;
    }

    public boolean searching() {
        return search != null;
    }

    public int searchExpanded() {
        return search == null ? 0 : search.expanded();
    }

    /** Waypoints still ahead of the player, for drawing. */
    public List<Pos> remainingPath() {
        if (path == null) return List.of();
        return path.subList(Math.min(pathIndex, path.size()), path.size());
    }

    public int pathSize() {
        return path == null ? 0 : path.size();
    }

    /** Call once per tick with the player's hitbox centre and horizontal velocity. */
    public Steer update(double px, double py, double pz, double vx, double vz, int goalX, int goalZ, double goalRadius) {
        tick++;
        zones.removeIf(z -> z.expires() <= tick);
        if (stuckLevel > 0 && tick - lastStuckTick > 400) {
            stuckLevel = 0;
            paddedPlans = false;
        }
        if (deadEnds > 0 && zones.isEmpty()) deadEnds = 0;

        double gx = goalX + 0.5, gz = goalZ + 0.5;
        double dist = Math.hypot(gx - px, gz - pz);

        // A new goal starts its own progress count; a goal moved further away is not a lack of progress.
        if (goalX != lastGoalX || goalZ != lastGoalZ) {
            lastGoalX = goalX;
            lastGoalZ = goalZ;
            bestDistTick = 0;
        }
        double speed = Math.hypot(vx, vz);
        double look = Math.min(Math.max(cfg.lookahead, speed * 12.0), Math.max(dist, 1.0));

        if (dist < bestDist - 10.0 || bestDistTick == 0) {
            bestDist = dist;
            bestDistTick = tick;
        } else if (tick < holdUntil) {
            bestDistTick = tick;
        } else if (tick - bestDistTick > cfg.noProgressTicks) {
            return new Steer(Mode.FAILED, px, py, pz, 0);
        }

        if (anchorTick < 0 || tick < holdUntil || dist3(anchorX, anchorY, anchorZ, px, py, pz) >= PROGRESS) {
            anchorX = px;
            anchorY = py;
            anchorZ = pz;
            anchorTick = tick;
        }

        if (unstickLeft > 0) {
            unstickLeft--;
            if (unstickLeft > 0 && dist3(px, py, pz, unstickX, unstickY, unstickZ) > REACH) {
                return new Steer(Mode.UNSTICK, unstickX, unstickY, unstickZ, vertical(py, unstickY));
            }
            unstickLeft = 0;
        } else if (tick - anchorTick >= cfg.stuckTicks) {
            return beginUnstick(px, py, pz, gx, gz);
        }

        if (stuckLevel > cfg.maxStuck) return new Steer(Mode.FAILED, px, py, pz, 0);

        if (path != null && (pathIndex >= path.size() - 1 || isStale(px, py, pz))) path = null;

        // A detour is dropped as soon as the straight line opens up again.
        if (path != null && tick - plannedTick >= DIRECT_RECHECK_TICKS && tick % DIRECT_RECHECK_TICKS == 0) {
            Steer direct = tryDirect(px, py, pz, gx, gz, dist, look);
            if (direct != null) {
                path = null;
                search = null;
                return direct;
            }
        }

        if (path == null) {
            Steer direct = tryDirect(px, py, pz, gx, gz, dist, look);
            if (direct != null) {
                search = null;
                return direct;
            }
        } else if (search == null && pathPartial && tick - plannedTick >= cfg.replanTicks) {
            startSearch(px, py, pz, goalX, goalZ, goalRadius);
        }

        if (search == null && path == null) startSearch(px, py, pz, goalX, goalZ, goalRadius);

        if (search != null) {
            int budget = searchFresh || (searchAir && wideAirSearch) ? cfg.nodesPerTick * 2 : cfg.nodesPerTick;
            searchFresh = false;
            if (search.step(budget)) {
                Steer failure = adoptSearch(px, py, pz, gx, gz);
                if (failure != null) return failure;
            }
        }

        if (path == null) return slide(px, py, pz, gx, gz);

        return follow(px, py, pz, speed, look);
    }

    private Steer tryDirect(double px, double py, double pz, double gx, double gz, double dist, double look) {
        if (dist < 1.0) return new Steer(Mode.DIRECT, px, py, pz, 0);

        if (tick < cautionUntil) look = Math.min(look * 2.0, Math.max(dist, 1.0));
        double ax = px + (gx - px) / dist * look;
        double az = pz + (gz - pz) / dist * look;
        double pref = cfg.preferredY;
        int vertical = 0;
        if (py < pref - 0.7) vertical = 1;
        else if (py > pref + 1.3) vertical = -1;

        if (vertical != 0 && ElytraPathPlanner.boxBlocked(terrain, px, py + vertical, pz, cfg.radius)) vertical = 0;

        // Blocked slope: keep climbing step by step on a level line instead of giving up on the cruise Y.
        double dy = vertical == 0 ? 0.0 : vertical * Math.min(Math.abs(pref - py), look);
        if (vertical != 0 && !lineDiggable(px, py, pz, ax, py + dy, az)) dy = 0.0;

        if (!lineDiggable(px, py, pz, ax, py + dy, az)
            || lineHitsZone(px, py, pz, ax, py + dy, az)) {
            cautionUntil = tick + 100;
            return null;
        }
        return new Steer(Mode.DIRECT, ax, py + dy, az, vertical);
    }

    /** Like lineClear, but while digging is allowed a few mineable cells may stand in the way. */
    private boolean lineDiggable(double ax, double ay, double az, double bx, double by, double bz) {
        if (ElytraPathPlanner.lineClear(terrain, ax, ay, az, bx, by, bz, cfg.radius)) return true;
        if (!digAllowed()) return false;

        Set<Long> dig = new HashSet<>();
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.4));
        double r = cfg.radius;
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            double x = ax + dx * f, y = ay + dy * f, z = az + dz * f;
            for (int cx = floor(x - r); cx <= floor(x + r); cx++) {
                for (int cy = floor(y - r); cy <= floor(y + r); cy++) {
                    for (int cz = floor(z - r); cz <= floor(z + r); cz++) {
                        if (!terrain.blocked(cx, cy, cz)) continue;
                        if (terrain.mineCost(cx, cy, cz) < 0f) return false;
                        dig.add(((long) (cx & 0x3FFFFFF) << 38) | ((long) (cz & 0x3FFFFFF) << 12) | (cy & 0xFFF));
                        if (dig.size() > DIRECT_DIG_LIMIT) return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean lineHitsZone(double ax, double ay, double az, double bx, double by, double bz) {
        if (zones.isEmpty()) return false;
        double len = dist3(ax, ay, az, bx, by, bz);
        int steps = Math.max(1, (int) Math.ceil(len / 3.0));
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            double x = ax + (bx - ax) * f, y = ay + (by - ay) * f, z = az + (bz - az) * f;
            for (TimedZone t : zones) {
                Zone zone = t.zone();
                if (dist3(x, y, z, zone.x() + 0.5, zone.y() + 0.5, zone.z() + 0.5) <= zone.radius()) return true;
            }
        }
        return false;
    }

    private void startSearch(double px, double py, double pz, int goalX, int goalZ, double goalRadius) {
        Pos start = new Pos(floor(px), floor(py), floor(pz));
        int span = Math.min(64, cfg.verticalSpan * (1 + deadEnds));
        int low = (int) Math.floor(py) - span;
        int high = (int) Math.ceil(py) + span;
        if (cfg.preferredY > py) high += span;
        else low -= span;
        List<Zone> active = new ArrayList<>(zones.size());
        for (TimedZone z : zones) active.add(z.zone());

        searchPadded = paddedPlans && !retryUnpadded;
        retryUnpadded = false;
        searchAir = !digAllowed();
        Terrain t = searchAir ? (searchPadded ? airPadded : air) : (searchPadded ? padded : terrain);
        search = new ElytraPathPlanner.Search(
            t, start, goalX, goalZ, goalRadius, cfg.preferredY,
            cfg.range, Math.max(cfg.minY, low), Math.min(cfg.maxY, high),
            searchAir && wideAirSearch ? cfg.maxNodes * WIDE_SEARCH_FACTOR : cfg.maxNodes, active
        );
        searchFresh = true;
    }

    /** Takes a finished search; returns a steer only when the route is hopeless and the caller must stop. */
    private Steer adoptSearch(double px, double py, double pz, double gx, double gz) {
        List<Pos> result = search.result();
        boolean reached = search.reachedGoal();
        boolean exhausted = search.exhausted();
        boolean wasPadded = searchPadded;
        boolean wasAir = searchAir;
        search = null;

        if (result.size() <= 1) {
            if (wasPadded) {
                retryUnpadded = true;
                return null;
            }
            if (wasAir && !reached) {
                airSearchFailed();
                return null;
            }
            return beginUnstick(px, py, pz, gx, gz);
        }

        // The air search ran out without getting closer: plan again, this time through mineable blocks.
        if (wasAir && !reached && exhausted) {
            Pos last = result.get(result.size() - 1);
            double gained = Math.hypot(gx - px, gz - pz) - Math.hypot(gx - (last.x() + 0.5), gz - (last.z() + 0.5));
            // A long detour along a wall gains nothing yet but still leads somewhere new.
            double reach = dist3(px, py, pz, last.x() + 0.5, last.y() + 0.5, last.z() + 0.5);
            if (gained < AIR_MIN_PROGRESS && reach < AIR_MIN_PROGRESS) {
                airSearchFailed();
                return null;
            }
        }
        if (reached || !exhausted) wideAirSearch = false;

        int join = 0;
        double joinDist = Double.MAX_VALUE;
        for (int i = 0; i < result.size(); i++) {
            Pos c = result.get(i);
            double d = dist3(px, py, pz, c.x() + 0.5, c.y() + 0.5, c.z() + 0.5);
            if (d < joinDist) {
                joinDist = d;
                join = i;
            }
        }
        if (joinDist > JOIN_DISTANCE) return null;

        path = result;
        pathIndex = join;
        pathPartial = !reached;
        plannedTick = tick;
        provLeft = 0;

        Pos end = result.get(result.size() - 1);
        double endH = Math.hypot(gx - (end.x() + 0.5), gz - (end.z() + 0.5));
        if (!reached && lastPlanEnd != null
            && dist3(px, py, pz, lastPlanEnd.x() + 0.5, lastPlanEnd.y() + 0.5, lastPlanEnd.z() + 0.5) < 6.0
            && endH > lastPlanEndH - 3.0) {
            zones.add(new TimedZone(new Zone(end.x(), end.y(), end.z(), 20), tick + 6000));
            deadEnds++;
        }
        if (exhausted) {
            zones.add(new TimedZone(new Zone(end.x(), end.y(), end.z(), 20), tick + 6000));
            deadEnds++;
        }
        lastPlanEnd = reached ? null : end;
        lastPlanEndH = endH;
        return null;
    }

    private Steer follow(double px, double py, double pz, double speed, double look) {
        double reach = Math.max(REACH, speed * 1.2);
        while (pathIndex < path.size() - 1 && near(path.get(pathIndex), px, py, pz, reach)) pathIndex++;

        int last = pathIndex;
        for (int i = pathIndex; i < path.size(); i++) {
            Pos p = path.get(i);
            if (dist3(px, py, pz, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5) > look) break;
            last = i;
        }

        int pick = Math.min(path.size() - 1, pathIndex + 1);
        int checks = 0;
        for (int i = last; i > pick && checks < 10; i--, checks++) {
            Pos p = path.get(i);
            if (ElytraPathPlanner.lineClear(terrain, px, py, pz, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5, cfg.radius)) {
                pick = i;
                break;
            }
        }
        pathIndex = Math.max(pathIndex, pick - 1);

        Pos t = path.get(pick);
        double tx = t.x() + 0.5, ty = t.y() + 0.5, tz = t.z() + 0.5;
        return new Steer(Mode.PATH, tx, ty, tz, vertical(py, ty));
    }

    /** While the search runs, glide along the most open direction instead of into the obstacle. */
    private Steer slide(double px, double py, double pz, double gx, double gz) {
        if (provLeft > 0) {
            double len = Math.max(dist3(px, py, pz, provX, provY, provZ), 1e-6);
            double ux = (provX - px) / len, uy = (provY - py) / len, uz = (provZ - pz) / len;
            if (len > REACH && ElytraPathPlanner.rayDistance(terrain, px, py, pz, ux, uy, uz, 4.0, cfg.radius) >= 4.0) {
                provLeft--;
                return new Steer(Mode.PATH, provX, provY, provZ, vertical(py, provY));
            }
        }

        double[] dir = openDirection(px, py, pz, gx, gz, 0.0, slideX, slideY, slideZ);
        slideX = dir[0];
        slideY = dir[1];
        slideZ = dir[2];
        double step = Math.max(2.0, Math.min(dir[3], 6.0));
        provX = px + dir[0] * step;
        provY = py + dir[1] * step;
        provZ = pz + dir[2] * step;
        provLeft = 8;
        return new Steer(Mode.PATH, provX, provY, provZ, vertical(py, provY));
    }

    /** Unit direction with the longest free ray, nudged toward the goal; returns {x, y, z, ray}. */
    private double[] openDirection(double px, double py, double pz, double gx, double gz, double upBias,
                                   double momX, double momY, double momZ) {
        double gd = Math.max(1.0, Math.hypot(gx - px, gz - pz));
        double goalX = (gx - px) / gd, goalZ = (gz - pz) / gd;

        double bestScore = -1e9;
        double[] best = {0, 1, 0, 0};
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double ux = dx / n, uy = dy / n, uz = dz / n;
                    double ray = ElytraPathPlanner.rayDistance(terrain, px, py, pz, ux, uy, uz, 10.0, cfg.radius);
                    double score = ray - 3.0 * Math.abs(uy) + 3.0 * (ux * goalX + uz * goalZ) + upBias * uy + 4.0 * (ux * momX + uy * momY + uz * momZ);
                    if (score > bestScore) {
                        bestScore = score;
                        best = new double[]{ux, uy, uz, ray};
                    }
                }
            }
        }
        return best;
    }

    private boolean isStale(double px, double py, double pz) {
        int end = Math.min(path.size(), pathIndex + 30);
        double nearest = Double.MAX_VALUE;
        for (int i = pathIndex; i < end; i++) {
            Pos p = path.get(i);
            if (terrain.blocked(p.x(), p.y(), p.z()) && terrain.mineCost(p.x(), p.y(), p.z()) < 0f) return true;
            if (i < pathIndex + 9) {
                nearest = Math.min(nearest, dist3(px, py, pz, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5));
            }
        }
        return nearest > DEVIATION;
    }

    private Steer beginUnstick(double px, double py, double pz, double gx, double gz) {
        stuckLevel++;
        lastStuckTick = tick;
        anchorX = px;
        anchorY = py;
        anchorZ = pz;
        anchorTick = tick;
        path = null;
        provLeft = 0;
        paddedPlans = stuckLevel >= 2;

        if (stuckLevel > cfg.maxStuck) return new Steer(Mode.FAILED, px, py, pz, 0);

        zones.add(new TimedZone(new Zone(floor(px), floor(py), floor(pz), 2 + stuckLevel), tick + 900));

        if (stuckLevel % 3 == 0) {
            double up = ElytraPathPlanner.rayDistance(terrain, px, py, pz, 0, 1, 0, 30.0, cfg.radius);
            if (up >= 6.0) {
                unstickX = px;
                unstickY = py + up;
                unstickZ = pz;
                unstickLeft = (int) Math.ceil(up / 0.8) + 4;
                return new Steer(Mode.UNSTICK, unstickX, unstickY, unstickZ, 1);
            }
        }

        double[] dir = openDirection(px, py, pz, gx, gz, 0.5, 0.0, 0.0, 0.0);
        double step = Math.max(3.0, Math.min(dir[3], 8.0));
        unstickX = px + dir[0] * step;
        unstickY = py + dir[1] * step;
        unstickZ = pz + dir[2] * step;
        unstickLeft = 6 + 2 * stuckLevel;
        return new Steer(Mode.UNSTICK, unstickX, unstickY, unstickZ, vertical(py, unstickY));
    }

    private static int vertical(double py, double ty) {
        double d = ty - py;
        return d > 0.5 ? 1 : d < -0.5 ? -1 : 0;
    }

    private static boolean near(Pos p, double x, double y, double z, double r) {
        return dist3(x, y, z, p.x() + 0.5, p.y() + 0.5, p.z() + 0.5) < r;
    }

    private static double dist3(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
