/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container counts per chunk for Stash Finder. Sums the chunks around a freshly scanned one and reports a
 * stash once the double chests there reach the threshold. Pure Java so it can be unit-tested.
 */
public final class StashTracker {
    /** Containers in one chunk. Double chests are stored as halves because a stash can span chunk borders. */
    public record Counts(int doubleHalves, int singles, int shulkers, int barrels, int other,
                         long sumX, long sumY, long sumZ, int blocks) {
        public static final Counts EMPTY = new Counts(0, 0, 0, 0, 0, 0, 0, 0, 0);

        public boolean isEmpty() {
            return blocks == 0;
        }
    }

    public record Stash(int x, int y, int z, int doubleChests, int singleChests, int shulkers, int barrels, int other) {}

    private final Map<Long, Counts> chunks = new HashMap<>();
    private final List<int[]> reported = new ArrayList<>();

    public void clearChunks() {
        chunks.clear();
    }

    public void clearReported() {
        reported.clear();
    }

    public void addReported(int x, int z) {
        reported.add(new int[]{x, z});
    }

    /** Drops chunks more than {@code radius} chunks (Chebyshev) from the centre. */
    public void pruneChunks(int centerX, int centerZ, int radius) {
        chunks.keySet().removeIf(key -> {
            int cx = (int) (key >> 32), cz = (int) (long) key;
            return Math.max(Math.abs(cx - centerX), Math.abs(cz - centerZ)) > radius;
        });
    }

    public int chunkCount() {
        return chunks.size();
    }

    /**
     * Stores a chunk's counts and returns a new stash when the chunks within {@code radius} chunks now hold at
     * least {@code minDoubles} double chests and nothing was reported within {@code spacing} blocks of it.
     */
    public Stash update(int cx, int cz, Counts counts, int radius, int minDoubles, int spacing) {
        long key = key(cx, cz);
        if (counts.isEmpty()) {
            chunks.remove(key);
            return null;
        }
        chunks.put(key, counts);

        int halves = 0, singles = 0, shulkers = 0, barrels = 0, other = 0, blocks = 0;
        long sx = 0, sy = 0, sz = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Counts c = chunks.get(key(cx + dx, cz + dz));
                if (c == null) continue;
                halves += c.doubleHalves();
                singles += c.singles();
                shulkers += c.shulkers();
                barrels += c.barrels();
                other += c.other();
                sx += c.sumX();
                sy += c.sumY();
                sz += c.sumZ();
                blocks += c.blocks();
            }
        }

        int doubles = halves / 2;
        if (doubles < minDoubles || blocks == 0) return null;

        int x = (int) (sx / blocks), y = (int) (sy / blocks), z = (int) (sz / blocks);
        long spacingSq = (long) spacing * spacing;
        for (int[] r : reported) {
            long ddx = r[0] - x, ddz = r[1] - z;
            if (ddx * ddx + ddz * ddz <= spacingSq) return null;
        }

        reported.add(new int[]{x, z});
        return new Stash(x, y, z, doubles, singles, shulkers, barrels, other);
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    /** Corner {@code index} of a square spiral around the centre; index 0 is the centre itself. */
    public static int[] spiralWaypoint(int centerX, int centerZ, int spacing, int index) {
        int x = 0, z = 0;
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (int leg = 0; leg < index; leg++) {
            int len = leg / 2 + 1;
            x += dirs[leg % 4][0] * len;
            z += dirs[leg % 4][1] * len;
        }
        return new int[]{centerX + x * spacing, centerZ + z * spacing};
    }
}
