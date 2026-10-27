/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Baritone-style elytra route planner: weighted A* over free air cells in loaded terrain, ending at the
 * goal or (when the goal is out of reach/unloaded) at the explored node closest to it. Pure Java — no
 * Minecraft types — so it can be unit-tested. Replan often; the world changes as chunks load.
 */
public final class ElytraPathPlanner {
    private ElytraPathPlanner() {}

    public interface Terrain {
        /** True for solid, hazardous (lava, fire, cobweb, any fluid) or out-of-world cells; unloaded cells are free. */
        boolean blocked(int x, int y, int z);

        /** False when the column's chunk is not loaded; the search never expands into such a column. */
        default boolean loaded(int x, int z) {
            return true;
        }

        /** For a blocked cell the route may dig through: its cost in blocks of flight. Negative means it cannot be mined. */
        default float mineCost(int x, int y, int z) {
            return -1f;
        }
    }

    public record Pos(int x, int y, int z) {}

    /** Soft-avoid sphere, used for dead ends the route already flew into. */
    public record Zone(int x, int y, int z, int radius) {}

    private static final int[][] MOVES = buildMoves();
    private static final float WEIGHT = 1.8f;
    private static final float VERTICAL_FACTOR = 1.2f;
    private static final float NEAR_SOLID_PENALTY = 0.3f;
    private static final float ZONE_PENALTY = 25.0f;
    private static final float END_G_WEIGHT = 0.25f;
    private static final float END_Y_WEIGHT = 0.1f;
    private static final float Y_AWAY_PENALTY = 0.5f;
    /** Per-step cost for every block off the cruise Y, so paths climb back to it. */
    private static final float OFF_CRUISE_PENALTY = 0.04f;
    private static final int OFF_CRUISE_CAP = 16;

    private static int[][] buildMoves() {
        List<int[]> moves = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) moves.add(new int[]{dx, dy, dz});
                }
            }
        }
        return moves.toArray(new int[0][]);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Open-addressing long to int map; avoids boxing in the hot loop. */
    static final class LongMap {
        private long[] keys = new long[1 << 12];
        private int[] vals = new int[1 << 12];
        private int mask = (1 << 12) - 1;
        private int count;

        LongMap() {
            Arrays.fill(vals, -1);
        }

        private static int hash(long k) {
            long h = k * 0x9E3779B97F4A7C15L;
            return (int) (h ^ (h >>> 32));
        }

        int get(long k) {
            int i = hash(k) & mask;
            while (vals[i] != -1) {
                if (keys[i] == k) return vals[i];
                i = (i + 1) & mask;
            }
            return -1;
        }

        void put(long k, int v) {
            if ((count + 1) * 2 > keys.length) grow();
            int i = hash(k) & mask;
            while (vals[i] != -1) {
                if (keys[i] == k) {
                    vals[i] = v;
                    return;
                }
                i = (i + 1) & mask;
            }
            keys[i] = k;
            vals[i] = v;
            count++;
        }

        private void grow() {
            long[] oldKeys = keys;
            int[] oldVals = vals;
            keys = new long[oldKeys.length * 2];
            vals = new int[oldKeys.length * 2];
            Arrays.fill(vals, -1);
            mask = keys.length - 1;
            count = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                if (oldVals[i] != -1) put(oldKeys[i], oldVals[i]);
            }
        }
    }

    public static final class Search {
        private final Terrain terrain;
        private final int goalX, goalZ;
        private final double goalRadius;
        private final int preferredY, range, minY, maxY, maxNodes;
        private final List<Zone> zones;
        private final int startX, startZ;

        private final LongMap nodeIndex = new LongMap();
        private final LongMap blockedCache = new LongMap();
        private final LongMap extraCache = new LongMap();

        private int size;
        private int[] nx = new int[1024], ny = new int[1024], nz = new int[1024], parent = new int[1024];
        private float[] ng = new float[1024], nh = new float[1024], nf = new float[1024];
        private boolean[] closed = new boolean[1024];

        private int[] heap = new int[1024];
        private int heapSize;

        private int best = -1;
        private float rootH;
        private int expanded;
        private boolean done;
        private boolean reachedGoal;
        private boolean exhausted;
        private List<Pos> result = List.of();

        public Search(Terrain terrain, Pos start, int goalX, int goalZ, double goalRadius, int preferredY,
                      int range, int minY, int maxY, int maxNodes, List<Zone> zones) {
            this.terrain = terrain;
            this.goalX = goalX;
            this.goalZ = goalZ;
            this.goalRadius = goalRadius;
            this.preferredY = preferredY;
            this.range = range;
            this.minY = minY;
            this.maxY = maxY;
            this.maxNodes = maxNodes;
            this.zones = zones == null ? List.of() : zones;
            this.startX = start.x();
            this.startZ = start.z();

            Pos usable = findUsableStart(start);
            if (usable == null) {
                done = true;
                return;
            }

            int root = addNode(usable.x(), usable.y(), usable.z(), 0f, heuristic(usable.x(), usable.z()), -1);
            nodeIndex.put(key(usable.x(), usable.y(), usable.z()), root);
            push(root);
            best = root;
            rootH = nh[root];
        }

        /** The player's hitbox can overlap a wall by a sliver, so accept a free neighbour as the start. */
        private Pos findUsableStart(Pos start) {
            if (!blocked(start.x(), start.y(), start.z())) return start;
            Pos bestPos = null;
            int bestDist = Integer.MAX_VALUE;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (blocked(start.x() + dx, start.y() + dy, start.z() + dz)) continue;
                        int d = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (d < bestDist) {
                            bestDist = d;
                            bestPos = new Pos(start.x() + dx, start.y() + dy, start.z() + dz);
                        }
                    }
                }
            }
            return bestPos;
        }

        private boolean blocked(int x, int y, int z) {
            long k = key(x, y, z);
            int cached = blockedCache.get(k);
            if (cached != -1) return cached == 1;
            boolean value = y < minY || y > maxY || (terrain.blocked(x, y, z) && terrain.mineCost(x, y, z) < 0f);
            blockedCache.put(k, value ? 1 : 0);
            return value;
        }

        /** Extra path cost for digging through this cell; 0 for free air. */
        private float mineExtra(int x, int y, int z) {
            long k = key(x, y, z);
            int cached = extraCache.get(k);
            if (cached != -1) return Float.intBitsToFloat(cached);

            float value = 0f;
            if (terrain.blocked(x, y, z)) {
                float cost = terrain.mineCost(x, y, z);
                if (cost > 0f) value = cost;
            }
            extraCache.put(k, Float.floatToIntBits(value));
            return value;
        }

        private float heuristic(int x, int z) {
            return (float) Math.max(0.0, Math.hypot(goalX - x, goalZ - z) - goalRadius);
        }

        private int addNode(int x, int y, int z, float g, float h, int par) {
            if (size == nx.length) {
                int cap = size * 2;
                nx = Arrays.copyOf(nx, cap);
                ny = Arrays.copyOf(ny, cap);
                nz = Arrays.copyOf(nz, cap);
                parent = Arrays.copyOf(parent, cap);
                ng = Arrays.copyOf(ng, cap);
                nh = Arrays.copyOf(nh, cap);
                nf = Arrays.copyOf(nf, cap);
                closed = Arrays.copyOf(closed, cap);
            }
            int i = size++;
            nx[i] = x;
            ny[i] = y;
            nz[i] = z;
            ng[i] = g;
            nh[i] = h;
            nf[i] = g + WEIGHT * h;
            parent[i] = par;
            return i;
        }

        private boolean less(int a, int b) {
            if (nf[a] != nf[b]) return nf[a] < nf[b];
            return nh[a] < nh[b];
        }

        private void push(int node) {
            if (heapSize == heap.length) heap = Arrays.copyOf(heap, heapSize * 2);
            int i = heapSize++;
            while (i > 0) {
                int p = (i - 1) >>> 1;
                if (!less(node, heap[p])) break;
                heap[i] = heap[p];
                i = p;
            }
            heap[i] = node;
        }

        private int pop() {
            int top = heap[0];
            int last = heap[--heapSize];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= heapSize) break;
                int r = l + 1;
                int c = (r < heapSize && less(heap[r], heap[l])) ? r : l;
                if (!less(heap[c], last)) break;
                heap[i] = heap[c];
                i = c;
            }
            if (heapSize > 0) heap[i] = last;
            return top;
        }

        /** A frontier node only ends the search if it is at least as close to the goal as the start. */
        private float frontierMaxH() {
            return size == 0 ? 0f : rootH;
        }

        public boolean isDone() {
            return done;
        }

        public boolean reachedGoal() {
            return reachedGoal;
        }

        public int expanded() {
            return expanded;
        }

        /** True when the node cap ended the search before it reached the goal or the loaded edge. */
        public boolean exhausted() {
            return exhausted;
        }

        /** Path from start to the end node; size <= 1 means no usable path. */
        public List<Pos> result() {
            return result;
        }

        /** Expands up to {@code budget} nodes. Returns true once finished. */
        public boolean step(int budget) {
            if (done) return true;

            int target = expanded + budget;
            while (expanded < target) {
                if (heapSize == 0) {
                    finish(best);
                    return true;
                }
                int current = pop();
                long k = key(nx[current], ny[current], nz[current]);
                if (closed[current] || nodeIndex.get(k) != current) continue;
                closed[current] = true;
                expanded++;

                if (nh[current] <= 0f) {
                    reachedGoal = true;
                    finish(current);
                    return true;
                }
                if (betterEndpoint(current)) best = current;
                if (expanded >= maxNodes) {
                    exhausted = true;
                    finish(best);
                    return true;
                }

                if (expand(current) && nh[current] < frontierMaxH() && !inZone(nx[current], ny[current], nz[current])) {
                    finish(current);
                    return true;
                }
            }
            return false;
        }

        private boolean isFrontierColumn(int x, int z) {
            return Math.abs(x - startX) > range || Math.abs(z - startZ) > range || !terrain.loaded(x, z);
        }

        /** Expands one node; returns true when it touches the search/loaded edge and the search can end here. */
        private boolean expand(int current) {
            boolean frontier = false;
            int cx = nx[current], cy = ny[current], cz = nz[current];
            for (int[] m : MOVES) {
                int x = cx + m[0];
                int y = cy + m[1];
                int z = cz + m[2];

                if (isFrontierColumn(x, z)) {
                    frontier = true;
                    continue;
                }
                if (blocked(x, y, z)) continue;
                if (!diagonalClear(cx, cy, cz, m)) continue;

                long k = key(x, y, z);
                int existing = nodeIndex.get(k);
                if (existing != -1 && closed[existing]) continue;

                float len = (float) Math.sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]);
                if (m[1] != 0) len *= VERTICAL_FACTOR;
                float g = ng[current] + len + stepPenalty(current, x, y, z) + mineExtra(x, y, z);

                if (existing != -1 && ng[existing] <= g) continue;

                int node = addNode(x, y, z, g, heuristic(x, z), current);
                nodeIndex.put(k, node);
                push(node);
            }
            return frontier;
        }

        /** No corner cutting: every cell the move brushes past must be free too. */
        private boolean diagonalClear(int x, int y, int z, int[] m) {
            int nonZero = (m[0] != 0 ? 1 : 0) | (m[1] != 0 ? 2 : 0) | (m[2] != 0 ? 4 : 0);
            if (Integer.bitCount(nonZero) < 2) return true;

            for (int mask = 1; mask < 8; mask++) {
                if ((mask & ~nonZero) != 0 || mask == nonZero) continue;
                int cx = x + ((mask & 1) != 0 ? m[0] : 0);
                int cy = y + ((mask & 2) != 0 ? m[1] : 0);
                int cz = z + ((mask & 4) != 0 ? m[2] : 0);
                if (blocked(cx, cy, cz)) return false;
            }
            return true;
        }

        private float stepPenalty(int from, int x, int y, int z) {
            float penalty = 0;

            int adjacent = 0;
            if (blocked(x + 1, y, z)) adjacent++;
            if (blocked(x - 1, y, z)) adjacent++;
            if (blocked(x, y + 1, z)) adjacent++;
            if (blocked(x, y - 1, z)) adjacent++;
            if (blocked(x, y, z + 1)) adjacent++;
            if (blocked(x, y, z - 1)) adjacent++;
            penalty += Math.min(adjacent, 3) * NEAR_SOLID_PENALTY;

            int awayFromCruise = Math.abs(y - preferredY) - Math.abs(ny[from] - preferredY);
            if (awayFromCruise > 0) penalty += awayFromCruise * Y_AWAY_PENALTY;
            penalty += Math.min(Math.abs(y - preferredY), OFF_CRUISE_CAP) * OFF_CRUISE_PENALTY;

            if (!zones.isEmpty() && inZone(x, y, z)) penalty += ZONE_PENALTY;

            return penalty;
        }

        private boolean inZone(int x, int y, int z) {
            for (Zone zone : zones) {
                long dx = x - zone.x();
                long dy = y - zone.y();
                long dz = z - zone.z();
                if (dx * dx + dy * dy + dz * dz <= (long) zone.radius() * zone.radius()) return true;
            }
            return false;
        }

        /** Lower is better. Dead-end zones are never valid endpoints; sliding along a wall beats climbing it. */
        private float endpointScore(int n) {
            return nh[n] + END_G_WEIGHT * ng[n] + END_Y_WEIGHT * Math.abs(ny[n] - preferredY);
        }

        private boolean betterEndpoint(int n) {
            if (inZone(nx[n], ny[n], nz[n])) return false;
            return best == -1 || inZone(nx[best], ny[best], nz[best]) || endpointScore(n) < endpointScore(best);
        }

        private void finish(int end) {
            done = true;
            if (end < 0) return;

            List<Pos> path = new ArrayList<>();
            for (int n = end; n != -1; n = parent[n]) path.add(new Pos(nx[n], ny[n], nz[n]));
            Collections.reverse(path);
            result = path;
        }
    }

    /** True when a box of the given half-size can slide from a to b without touching a blocked cell. */
    public static boolean lineClear(Terrain terrain, double ax, double ay, double az,
                                    double bx, double by, double bz, double radius) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(len / 0.4));
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            if (boxBlocked(terrain, ax + dx * f, ay + dy * f, az + dz * f, radius)) return false;
        }
        return true;
    }

    /** Distance travelled along a normalised direction before the box hits something, capped at max. */
    public static double rayDistance(Terrain terrain, double ox, double oy, double oz,
                                     double dirX, double dirY, double dirZ, double max, double radius) {
        for (double d = 0.0; d <= max; d += 0.4) {
            if (boxBlocked(terrain, ox + dirX * d, oy + dirY * d, oz + dirZ * d, radius)) return d;
        }
        return max;
    }

    public static boolean boxBlocked(Terrain terrain, double px, double py, double pz, double radius) {
        int minX = (int) Math.floor(px - radius), maxX = (int) Math.floor(px + radius);
        int minY = (int) Math.floor(py - radius), maxY = (int) Math.floor(py + radius);
        int minZ = (int) Math.floor(pz - radius), maxZ = (int) Math.floor(pz + radius);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (terrain.blocked(x, y, z)) return true;
                }
            }
        }
        return false;
    }
}
