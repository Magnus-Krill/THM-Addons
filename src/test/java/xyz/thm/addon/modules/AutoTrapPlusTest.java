/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import xyz.thm.addon.utils.PlacementUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static xyz.thm.addon.modules.AutoTrapPlus.BuildOrder.BottomToTop;
import static xyz.thm.addon.modules.AutoTrapPlus.BuildOrder.TopToBottom;
import static xyz.thm.addon.modules.AutoTrapPlus.HeightMode.Eye;
import static xyz.thm.addon.modules.AutoTrapPlus.HeightMode.Feet;
import static xyz.thm.addon.modules.AutoTrapPlus.TopMode.Full;
import static xyz.thm.addon.modules.AutoTrapPlus.TopMode.Top;

class AutoTrapPlusTest {
    @Test
    void straddlingTargetGetsOnePerimeterAndRoofAcrossItsWholeFootprint() {
        AABB body = new AABB(-0.4, 64, 0.2, 0.2, 65.8, 0.8);
        BlockPos left = new BlockPos(-1, 64, 0);
        BlockPos right = left.east();
        Set<BlockPos> trap = AutoTrapPlus.plan(body, 65, Feet, Full, false);

        for (BlockPos foot : Set.of(left, right)) {
            assertFalse(trap.contains(foot));
            assertFalse(trap.contains(foot.above()));
            assertTrue(trap.contains(foot.above(2)));
            assertTrue(trap.contains(foot.below()));
        }
        assertTrue(trap.contains(left.west().above()));
        assertTrue(trap.contains(right.east().above()));
        assertEquals(22, trap.size());
    }

    @Test
    void roofClearsTheCurrentPoseAndAntiStepAddsOneLayer() {
        var body = new AABB(0.2, 64, 0.2, 0.8, 64.6, 0.8);
        var feet = new BlockPos(0, 64, 0);
        Set<BlockPos> eyeTrap = AutoTrapPlus.plan(body, 64, Eye, Top, false);
        assertEquals(Set.of(feet.north(), feet.south(), feet.west(), feet.east(), feet.above()), eyeTrap);

        Set<BlockPos> tallerTrap = AutoTrapPlus.plan(body.move(0, 0.5, 0), 64, Feet, Top, true);
        assertFalse(tallerTrap.contains(feet.above()));
        assertTrue(tallerTrap.contains(feet.above(2)));
        assertTrue(tallerTrap.contains(feet.above(3)));
    }

    @Test
    void roofCanAttachToAColumnReachedAroundTheTarget() {
        BlockPos roof = new BlockPos(0, 66, 0);
        BlockPos corner = roof.east();
        BlockPos support = corner.below();
        assertEquals(support, PlacementUtils.findSupportStep(roof, Direction.values(), 2,
            pos -> pos.equals(corner) || pos.equals(support), support::equals));
        assertNull(PlacementUtils.findSupportStep(roof, Direction.values(), 2, support::equals, support::equals));
    }

    @Test
    void airPlaceClosesHeadWallsAcrossColumnsBeforeRoofAndLowerBlocks() {
        BlockPos feet = new BlockPos(0, 64, 0);
        BlockPos nearWall = feet.west().above();
        BlockPos farWall = feet.east().above();
        BlockPos roof = feet.above(2);
        BlockPos nearLeg = nearWall.below();
        Vec3 eye = Vec3.atCenterOf(nearWall.west());
        var candidates = List.of(nearLeg, roof, farWall, nearWall);
        for (var order : List.of(BottomToTop, TopToBottom)) {
            assertEquals(List.of(nearWall, farWall, roof, nearLeg),
                candidates.stream().sorted(AutoTrapPlus.placementOrder(eye, order, true, 65, 66)).toList());
        }
        assertEquals(List.of(nearLeg, nearWall, roof, farWall),
            candidates.stream().sorted(AutoTrapPlus.placementOrder(eye, BottomToTop, false, 65, 66)).toList());
    }
}
