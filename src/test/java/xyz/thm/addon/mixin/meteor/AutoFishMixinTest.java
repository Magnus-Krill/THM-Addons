/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class AutoFishMixinTest {
    @Test
    void leavingTheWorldClearsMovementAndLookState() throws Exception {
        var mixin = new AutoFishMixin();
        mixin.thm$startMovement(new Vec3(0.5, 64, 0.5), 0, 0.4);
        mixin.thm$startLook(10, 25, 100, -30);
        assertNotNull(mixin.thm$nextLook());
        var reset = AutoFishMixin.class.getDeclaredMethod("thm$reset", boolean.class);
        reset.setAccessible(true);
        reset.invoke(mixin, false);
        assertNull(mixin.thm$nextLook());
        assertSame(Vec3.ZERO, mixin.thm$movementStep(new Vec3(0.5, 64, 0.5)));
    }

    @Test
    void lookAroundReturnsToTheFishingAnglesBeforeFinishing() {
        var mixin = new AutoFishMixin();
        assertNull(mixin.thm$nextLook());
        mixin.thm$startLook(35, 25, 150, -40);
        Vec2 rotation = null;
        for (int tick = 1; tick <= 22; tick++) {
            rotation = mixin.thm$nextLook();
            assertNotNull(rotation);
            assertTrue(rotation.x >= 35 && rotation.x <= 150);
            assertTrue(rotation.y >= -40 && rotation.y <= 25);
            if (tick >= 8 && tick <= 14) {
                assertEquals(150, rotation.x);
                assertEquals(-40, rotation.y);
            }
        }
        assertEquals(35, rotation.x);
        assertEquals(25, rotation.y);
        assertNull(mixin.thm$nextLook());
    }

    @Test
    void lookingAcrossTheYawBoundaryUsesTheShortDirectionAndClampsPitch() {
        var mixin = new AutoFishMixin();
        mixin.thm$startLook(179, 0, -179, 150);
        for (int tick = 1; tick <= 22; tick++) {
            Vec2 rotation = mixin.thm$nextLook();
            assertTrue(rotation.x >= 179 && rotation.x <= 181);
            assertTrue(rotation.y >= 0 && rotation.y <= 90);
        }
        assertNull(mixin.thm$nextLook());
    }

    @ParameterizedTest
    @CsvSource({"0,0.2", "90,0.4", "180,0.6", "-90,0.35"})
    void movementWalksBackwardAndReturnsWithoutChangingHeight(float yaw, double distance) {
        var mixin = new AutoFishMixin();
        Vec3 origin = new Vec3(10.5, 64, -3.5), position = origin;
        mixin.thm$startMovement(origin, yaw, distance);
        double maximum = 0;
        boolean returned = false;
        for (int tick = 0; tick < 40; tick++) {
            Vec3 step = mixin.thm$movementStep(position);
            assertEquals(0, step.y);
            assertTrue(step.length() <= 0.060001, "Each step must remain smaller than normal walking speed.");
            position = position.add(step);
            maximum = Math.max(maximum, position.distanceTo(origin));
            if (tick == 0) {
                double radians = Math.toRadians(yaw);
                assertTrue(step.dot(new Vec3(Math.sin(radians), 0, -Math.cos(radians))) > 0);
            }
            if (tick > 0 && step == Vec3.ZERO) { returned = true; break; }
        }
        assertTrue(returned);
        assertEquals(distance, maximum, 0.025);
        assertTrue(position.distanceTo(origin) < 0.025);
        assertSame(Vec3.ZERO, mixin.thm$movementStep(position));
    }

    @Test
    void blockedMovementCannotKeepTheCycleRunningForever() {
        var mixin = new AutoFishMixin();
        Vec3 position = new Vec3(0.5, 64, 0.5);
        mixin.thm$startMovement(position, 0, 0.6);
        for (int tick = 0; tick < 39; tick++) assertTrue(mixin.thm$movementStep(position).length() > 0);
        assertSame(Vec3.ZERO, mixin.thm$movementStep(position));
        assertSame(Vec3.ZERO, mixin.thm$movementStep(position));
    }
}
