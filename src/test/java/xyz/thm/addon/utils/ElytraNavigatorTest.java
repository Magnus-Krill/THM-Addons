/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;


import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraNavigatorTest {
    private interface Solid { boolean at(int x, int y, int z); }

    private static final class World implements ElytraPathPlanner.Terrain {
        Solid solid;
        double px, pz;
        float mineCost = -1f;

        @Override
        public float mineCost(int x, int y, int z) {
            return mineCost;
        }

        @Override
        public boolean blocked(int x, int y, int z) {
            if (y < 0 || y > 255) return true;
            if (!loaded(x, z)) return false;
            return solid.at(x, y, z);
        }

        @Override
        public boolean loaded(int x, int z) {
            return Math.abs(x - px) <= 112 && Math.abs(z - pz) <= 112;
        }
    }

    private static boolean box(World w, double x, double y, double z) {
        for (int ix = (int) Math.floor(x - 0.3); ix <= (int) Math.floor(x + 0.3); ix++)
            for (int iy = (int) Math.floor(y - 0.3); iy <= (int) Math.floor(y + 0.3); iy++)
                for (int iz = (int) Math.floor(z - 0.3); iz <= (int) Math.floor(z + 0.3); iz++)
                    if (w.blocked(ix, iy, iz)) return true;
        return false;
    }

    /** Flies toward (gx, gz) along the navigator's steering; returns whether the goal was reached. */
    private static boolean fly(Solid solid, int gx, int gz) {
        World w = new World();
        w.solid = solid;
        ElytraNavigator.Config c = new ElytraNavigator.Config();
        c.preferredY = 100;
        c.minY = 0;
        c.maxY = 255;
        ElytraNavigator nav = new ElytraNavigator(w, c);

        double x = 0, y = 100, z = 0, vx = 0, vz = 0;
        for (int tick = 0; tick < 4000; tick++) {
            if (Math.hypot(gx - x, gz - z) <= 6) return true;
            w.px = x;
            w.pz = z;
            ElytraNavigator.Steer s = nav.update(x, y, z, vx, vz, gx, gz, 6);
            if (s.mode() == ElytraNavigator.Mode.FAILED) return false;

            double dx = s.x() - x, dz = s.z() - z, h = Math.hypot(dx, dz);
            double mx = h < 1e-6 ? 0 : dx / h * 1.0, mz = h < 1e-6 ? 0 : dz / h * 1.0, my = s.vertical() * 0.8;
            if (!box(w, x + mx, y + my, z + mz)) {
                x += mx; y += my; z += mz;
            } else {
                if (!box(w, x + mx, y, z)) x += mx;
                if (!box(w, x, y + my, z)) y += my;
                if (!box(w, x, y, z + mz)) z += mz;
            }
            vx = mx;
            vz = mz;
        }
        return false;
    }

    @Test
    void openSkyGoesStraight() {
        assertTrue(fly((x, y, z) -> false, 0, 300));
    }

    @Test
    void routesAroundAWall() {
        // 60 wide wall across the path
        assertTrue(fly((x, y, z) -> z >= 100 && z <= 103 && x >= -30 && x <= 30, 0, 300));
    }

    @Test
    void routesAroundABox() {
        assertTrue(fly((x, y, z) -> z >= 90 && z <= 130 && x >= -20 && x <= 20 && y >= 60 && y <= 200, 0, 300));
    }

    @Test
    void flagsLineOfSightAcrossAWall() {
        World w = new World();
        w.solid = (x, y, z) -> z == 10;
        assertEquals(false, ElytraPathPlanner.lineClear(w, 0.5, 100.5, 0.5, 0.5, 100.5, 20.5, 0.3));
    }

    private static boolean planCrossesWall(float mineCost) {
        ElytraPathPlanner.Terrain terrain = new ElytraPathPlanner.Terrain() {
            @Override public boolean blocked(int x, int y, int z) { return y < 0 || y > 255 || (z >= 50 && z <= 53 && Math.abs(x) <= 30); }
            @Override public float mineCost(int x, int y, int z) { return mineCost; }
        };

        ElytraPathPlanner.Search search = new ElytraPathPlanner.Search(
            terrain, new ElytraPathPlanner.Pos(0, 100, 0), 0, 80, 4, 100, 96, 0, 255, 200_000, List.of());
        while (!search.step(600)) { /* keep searching */ }
        assertTrue(search.reachedGoal(), "planner must reach the goal");

        for (ElytraPathPlanner.Pos p : search.result()) {
            if (p.z() >= 50 && p.z() <= 53 && Math.abs(p.x()) <= 30) return true;
        }
        return false;
    }

    /** Steers toward z=300 with cheap mineable terrain; returns whether the navigator ever switched to digging. */
    private static boolean wantsToDig(Solid solid) {
        return wantsToDig(solid, 8_000);
    }

    private static boolean wantsToDig(Solid solid, int maxNodes) {
        World w = new World();
        w.solid = solid;
        w.mineCost = 0.1f;
        ElytraNavigator.Config c = new ElytraNavigator.Config();
        c.preferredY = 100;
        c.minY = 0;
        c.maxY = 255;
        c.maxNodes = maxNodes;
        ElytraNavigator nav = new ElytraNavigator(w, c);

        double x = 0, y = 100, z = 0;
        for (int tick = 0; tick < 600; tick++) {
            w.px = x;
            w.pz = z;
            ElytraNavigator.Steer s = nav.update(x, y, z, 0, 1, 0, 300, 6);
            if (nav.digging()) return true;
            if (Math.hypot(x, 300 - z) <= 6) return false;
            double dx = s.x() - x, dz = s.z() - z, h = Math.hypot(dx, dz);
            double mx = h < 1e-6 ? 0 : dx / h, mz = h < 1e-6 ? 0 : dz / h, my = s.vertical() * 0.8;
            if (!box(w, x + mx, y + my, z + mz)) {
                x += mx; y += my; z += mz;
            }
        }
        return false;
    }

    @Test
    void mineableWallWithAWayAroundIsFlownAround() {
        assertTrue(!wantsToDig((x, y, z) -> z >= 30 && z <= 33 && Math.abs(x) <= 12 && y >= 90 && y <= 110));
    }

    @Test
    void longDetourAroundAWideWallIsFlownInsteadOfDug() {
        // 81 blocks wide and the full world height: the only way is a long sideways detour.
        assertTrue(!wantsToDig((x, y, z) -> z >= 30 && z <= 33 && Math.abs(x) <= 40, new ElytraNavigator.Config().maxNodes));
    }

    @Test
    void digsOnlyWhenNoWayAroundExists() {
        assertTrue(wantsToDig((x, y, z) -> z >= 30 && z <= 33));
    }

    @Test
    void cheapBlocksAreDugThroughInsteadOfFlownAround() {
        assertTrue(planCrossesWall(0.1f));
    }

    @Test
    void unbreakableBlocksAreStillFlownAround() {
        assertTrue(!planCrossesWall(-1f));
    }
}
