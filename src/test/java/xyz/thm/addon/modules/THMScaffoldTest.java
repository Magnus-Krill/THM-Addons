/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class THMScaffoldTest {
    @ParameterizedTest
    @CsvSource({"0,1", "0.5,1", "1,5", "1.5,9", "2,13", "6,113"})
    void radiusUsesHorizontalDistanceOnOnePlane(double radius, int count) {
        BlockPos center = new BlockPos(-14, 119, -21);
        var targets = THMScaffold.placementTargets(center, radius, Vec3.atCenterOf(center).add(0, 1.5, 0));
        assertEquals(count, targets.size());
        assertEquals(count, new HashSet<>(targets).size());
        assertEquals(center, targets.getFirst());
        for (BlockPos target : targets) {
            assertEquals(center.getY(), target.getY());
            assertTrue(target.distSqr(center) <= radius * radius);
        }
    }

    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void radiusPrioritizesTheApproachingEdge(Direction direction) {
        BlockPos center = new BlockPos(-2, 119, -3);
        Vec3 origin = Vec3.atCenterOf(center).add(direction.getStepX() * 0.49, 1.5, direction.getStepZ() * 0.49);
        var targets = THMScaffold.placementTargets(center, 1, origin);
        assertEquals(center, targets.getFirst());
        assertEquals(center.relative(direction), targets.get(1), "A filled center must not starve the next block ahead.");
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void supportingFacesPlaceAtTheTargetInsteadOfOnTopOfIt(Direction support) {
        BlockPos target = new BlockPos(8, 119, -5);
        var hit = THMScaffold.placementHit(target, support);
        assertEquals(target.relative(support), hit.getBlockPos());
        assertEquals(target, hit.getBlockPos().relative(hit.getDirection()));
        assertEquals(Vec3.atCenterOf(target).add(support.getStepX() * 0.5, support.getStepY() * 0.5,
            support.getStepZ() * 0.5), hit.getLocation());
    }

    @Test
    void supportedRadiusExtendsAnExistingPlatformWithoutAirClicks() {
        BlockPos center = new BlockPos(0, 119, 0);
        var platform = new HashSet<BlockPos>();
        platform.add(center);
        for (BlockPos target : THMScaffold.placementTargets(center, 1, Vec3.atCenterOf(center))) {
            if (platform.contains(target)) continue;
            Direction support = null;
            for (Direction direction : Direction.values()) {
                if (platform.contains(target.relative(direction))) { support = direction; break; }
            }
            assertNotNull(support);
            var hit = THMScaffold.placementHit(target, support);
            BlockPos placed = hit.getBlockPos().relative(hit.getDirection());
            assertEquals(119, placed.getY());
            platform.add(placed);
        }
        assertEquals(5, platform.size());
    }

    @Test
    void acceptedAirPlaceStaysBlockedUntilTheServerReportsAirAgain() {
        BlockPos target = new BlockPos(0, 119, 0);
        var hit = THMScaffold.placementHit(target, null);
        assertEquals(target, hit.getBlockPos());
        var state = new THMScaffold.PacketState();
        state.markSent(target.asLong(), 1);
        state.answer(target.asLong(), false);
        for (int tick = 0; tick < 100; tick++) {
            state.beginTick();
            assertTrue(state.isPending(target.asLong()), "A second air click would place above the accepted block.");
            assertTrue(state.isConfirmed(target.asLong()));
        }
        state.answer(target.asLong(), true);
        state.beginTick();
        assertFalse(state.isPending(target.asLong()));
        assertFalse(state.isConfirmed(target.asLong()));
        assertTrue(state.snapshot().isEmpty());
    }

    @Test
    void aSameTickReplacementKeepsTheLatestServerState() {
        var state = new THMScaffold.PacketState();
        state.markSent(123, 1);
        state.answer(123, true);
        state.answer(123, false);
        state.beginTick();
        assertTrue(state.isPending(123));
        assertTrue(state.isConfirmed(123));
        state.forget(123);
        assertFalse(state.isConfirmed(123));
        assertTrue(state.snapshot().isEmpty());
    }

    @Test
    void budgetReservesWholeSwapTransactionsBeforeScheduling() {
        var state = new THMScaffold.PacketState();
        assertTrue(state.reserve(true, 9, 5)); // Rotation, two slot changes, use and swing.
        assertFalse(state.reserve(true, 9, 5));
        assertTrue(state.reserve(true, 9, 4), "A rejected transaction must not consume budget.");
        assertFalse(state.reserve(true, 9, 1));
        state.beginTick();
        assertTrue(state.reserve(true, 9, 5));
    }

    @Test
    void budgetStillAccountsForPacketsWhileDisabled() {
        var state = new THMScaffold.PacketState();
        assertTrue(state.reserve(false, 1, 5));
        assertFalse(state.reserve(true, 5, 1));
        state.beginTick();
        assertFalse(state.reserve(true, 1, 2));
        assertTrue(state.reserve(true, 1, 1));
    }

    @Test
    void onlyServerAnswersReleasePendingPlacements() throws Exception {
        var state = new THMScaffold.PacketState();
        state.markSent(123, 1);
        for (int tick = 0; tick < 100; tick++) state.beginTick();
        assertTrue(state.isPending(123));
        Thread network = new Thread(() -> {
            state.answer(123, true);
            for (long unrelated = 1000; unrelated < 100_000; unrelated++) state.answer(unrelated, true);
        });
        network.start();
        network.join();
        assertTrue(state.isPending(123), "Receive events must wait for the client tick.");
        assertEquals(1, state.snapshot().size());
        state.beginTick();
        assertFalse(state.isPending(123));
        assertTrue(state.snapshot().isEmpty());
    }

    @Test
    void resetAndRejectedSendsReleasePendingState() {
        var state = new THMScaffold.PacketState();
        state.markSent(123, 1);
        state.answer(123, true);
        state.forget(123);
        state.markSent(123, 2);
        state.beginTick();
        assertTrue(state.isPending(123), "A cancelled send must not retain an old answer.");
        state.reserve(true, 5, 5);
        state.clear();
        assertFalse(state.isPending(123));
        assertTrue(state.reserve(true, 5, 5));
    }

    @Test
    void migrationUsesPacketAirPlaceOnlyForOldPacketProfiles() {
        CompoundTag source = profile(true, false, true);
        CompoundTag migrated = THMScaffold.thm$migrateSettings(source);
        assertTrue(setting(migrated, "air-place").getBooleanOr("value", false));
        assertFalse(setting(source, "air-place").getBooleanOr("value", true));
        assertSame(migrated, THMScaffold.thm$migrateSettings(migrated));
        setting(migrated, "air-place").putBoolean("value", false);
        assertFalse(setting(THMScaffold.thm$migrateSettings(migrated), "air-place").getBooleanOr("value", true));
        assertFalse(setting(THMScaffold.thm$migrateSettings(profile(false, false, true)), "air-place").getBooleanOr("value", true));
        assertFalse(setting(THMScaffold.thm$migrateSettings(profile(true, true, false)), "air-place").getBooleanOr("value", true));
    }

    @Test
    void migrationKeepsEnabledPredictionRendering() {
        CompoundTag source = profile(false, false, false);
        ListTag settings = source.getCompoundOrEmpty("settings").getListOrEmpty("groups").getCompoundOrEmpty(0).getListOrEmpty("settings");
        settings.add(flag("render", false));
        settings.add(flag("interpolate", true));
        settings.add(flag("interpolate-render", true));
        CompoundTag shape = new CompoundTag();
        shape.putString("name", "shape-mode");
        shape.putString("value", "Both");
        settings.add(shape);
        CompoundTag oldShape = shape.copy();
        oldShape.putString("name", "interp-shape-mode");
        oldShape.putString("value", "Lines");
        settings.add(oldShape);
        CompoundTag migrated = THMScaffold.thm$migrateSettings(source);
        assertTrue(setting(migrated, "render").getBooleanOr("value", false));
        assertEquals("Lines", setting(migrated, "shape-mode").getStringOr("value", ""));
        assertEquals("Both", setting(source, "shape-mode").getStringOr("value", ""));
    }

    private static CompoundTag profile(boolean packet, boolean air, boolean packetAir) {
        ListTag values = new ListTag();
        values.add(flag("packet-place", packet));
        values.add(flag("air-place", air));
        values.add(flag("packet-air-place", packetAir));
        CompoundTag group = new CompoundTag();
        group.putString("name", "General");
        group.put("settings", values);
        ListTag groups = new ListTag();
        groups.add(group);
        CompoundTag settings = new CompoundTag();
        settings.put("groups", groups);
        CompoundTag profile = new CompoundTag();
        profile.put("settings", settings);
        return profile;
    }

    private static CompoundTag flag(String name, boolean value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putBoolean("value", value);
        return tag;
    }

    private static CompoundTag setting(CompoundTag profile, String name) {
        for (var entry : profile.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            if (!(entry instanceof CompoundTag group)) continue;
            for (var value : group.getListOrEmpty("settings")) {
                if (value instanceof CompoundTag setting && setting.getStringOr("name", "").equals(name)) return setting;
            }
        }
        throw new AssertionError("Missing setting: " + name);
    }
}
