/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.utils;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Predicts where an entity's hitbox will be by continuing its last observed motion. Adapted from Open Zio (MIT). */
public final class MotionExtrapolation {
    // Faster than this per tick is a teleport or a lag spike, not movement worth continuing.
    private static final double MAX_SPEED = 1.0;

    private MotionExtrapolation() {}

    /** Hitbox after {@code ticks} ticks, keeping horizontal input and applying gravity and block collisions. */
    public static AABB predict(Entity entity, int ticks) {
        AABB box = entity.getBoundingBox();
        double x = entity.getX() - entity.xo, y = entity.getY() - entity.yo, z = entity.getZ() - entity.zo;
        if (x * x + z * z > MAX_SPEED * MAX_SPEED || x * x + y * y + z * z < 1e-6) return box;
        boolean gliding = entity instanceof LivingEntity living && living.isFallFlying();
        for (int tick = 0; tick < ticks; tick++) {
            Vec3 moved = Entity.collideBoundingBox(entity, new Vec3(x, y, z), box, entity.level(), List.of());
            box = box.move(moved);
            if (gliding) continue;
            // Landing or a ceiling cancels vertical motion, like vanilla's collision response.
            y = moved.y != y ? 0 : (y - 0.08) * 0.98;
        }
        return box;
    }

    public static Vec3 feet(AABB box) {
        return new Vec3((box.minX + box.maxX) / 2, box.minY, (box.minZ + box.maxZ) / 2);
    }
}
