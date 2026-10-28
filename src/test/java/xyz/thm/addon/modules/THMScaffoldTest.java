/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class THMScaffoldTest {
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
            state.answer(123);
            for (long unrelated = 1000; unrelated < 100_000; unrelated++) state.answer(unrelated);
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
        state.answer(123);
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
