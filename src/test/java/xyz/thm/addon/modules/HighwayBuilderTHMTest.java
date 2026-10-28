/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import org.junit.jupiter.api.Test;
import xyz.thm.addon.system.THMSystem;

import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HighwayBuilderTHMTest {
    @Test
    void blockRatesUseElapsedSecondsAndCountedWork() {
        var sample = new HighwayBuilderTHM.BlockRateSample(1_000_000_000L, 100, 100, 200, 0, 0, 0, 0);
        assertSame(sample, sample.sample(1_999_999_999L, 119, 130, 230));

        sample = sample.sample(2_000_000_000L, 120, 130, 230);
        assertEquals(30.0, sample.dugPerSecond());
        assertEquals(30.0, sample.pavedPerSecond());
        assertEquals(1.5, sample.dugPerTick());
        assertEquals(1.5, sample.pavedPerTick());

        sample = sample.sample(4_000_000_000L, 130, 150, 270);
        assertEquals(10.0, sample.dugPerSecond());
        assertEquals(20.0, sample.pavedPerSecond());
        assertEquals(2.0, sample.dugPerTick());
        assertEquals(4.0, sample.pavedPerTick());
    }

    @Test
    void idleBlockRateWindowDropsBothRatesToZero() {
        var sample = new HighwayBuilderTHM.BlockRateSample(0, 0, 100, 200, 10, 2, 0.5, 0.1);
        sample = sample.sample(1_000_000_000L, 20, 100, 200);
        assertEquals(0.0, sample.dugPerSecond());
        assertEquals(0.0, sample.pavedPerSecond());
        assertEquals(0.0, sample.dugPerTick());
        assertEquals(0.0, sample.pavedPerTick());
    }

    @Test
    void resettingStatisticsStartsANewBlockRateWindow() {
        var sample = new HighwayBuilderTHM.BlockRateSample(0, 100, 100, 200, 10, 2, 0.5, 0.1);
        sample = sample.sample(1_000_000_000L, 0, 0, 0);
        assertEquals(0.0, sample.dugPerSecond());
        assertEquals(0.0, sample.pavedPerSecond());
        assertSame(sample, sample.sample(1_500_000_000L, 10, 20, 5));

        sample = sample.sample(2_000_000_000L, 20, 20, 5);
        assertEquals(20.0, sample.dugPerSecond());
        assertEquals(5.0, sample.pavedPerSecond());
    }

    @Test
    void packetPlacementAttemptsCountOnlyOnceAfterTheBlockAppears() {
        var pending = new HighwayBuilderTHM.PendingBlockPlacements();
        var counted = new ArrayList<Long>();
        for (int i = 0; i < 100; i++) pending.sent(42, i);
        pending.confirm(100, key -> true, key -> false, counted::add);
        assertTrue(counted.isEmpty());

        pending.confirm(200, key -> true, key -> true, counted::add);
        pending.confirm(300, key -> true, key -> true, counted::add);
        assertEquals(List.of(42L), counted);
    }

    @Test
    void rejectedAndUnloadedPacketPlacementsExpireWithoutCounting() {
        var pending = new HighwayBuilderTHM.PendingBlockPlacements();
        var counted = new ArrayList<Long>();
        pending.sent(1, 0);
        pending.sent(2, 0);
        pending.confirm(1_000_000_000L, key -> key != 2, key -> false, counted::add);
        pending.confirm(5_000_000_000L, key -> true, key -> key == 2, counted::add);
        pending.confirm(6_000_000_000L, key -> true, key -> true, counted::add);
        assertTrue(counted.isEmpty());
    }

    @Test
    void apiProfileModeIsOptionalForLegacyCompatibility() {
        assertEquals(":pave", HighwayBuilderTHM.apiModeSuffix(THMSystem.Mode.HighwayBuilding));
        assertEquals(":dig", HighwayBuilderTHM.apiModeSuffix(THMSystem.Mode.HighwayDigging));
        assertEquals("", HighwayBuilderTHM.apiModeSuffix(THMSystem.Mode.None));
    }

    @Test
    void adventureBackupVisitPreservesTheMainServerCheckpoint() {
        var mainPosition = new Vec3(100000, 120, -100000);
        var continuity = new HighwayBuilderTHM.StatsContinuity(mainPosition, "test", "minecraft:the_nether", true);
        var inBackup = continuity.observe(Vec3.ZERO, "test", "minecraft:overworld", GameType.ADVENTURE);
        assertSame(continuity, inBackup);
        inBackup = inBackup.observe(new Vec3(10, 65, 10), "test", "minecraft:overworld", GameType.ADVENTURE);
        assertSame(continuity, inBackup);
        assertTrue(inBackup.observe(mainPosition, "test", "minecraft:the_nether", GameType.SURVIVAL).valid());
        assertFalse(inBackup.observe(mainPosition.add(1000, 0, 0), "test", "minecraft:the_nether", GameType.SURVIVAL).valid());
    }

    @Test
    void backupAndUnknownGameModesDoNotRestoreInvalidStatistics() {
        var invalid = new HighwayBuilderTHM.StatsContinuity(Vec3.ZERO, "test", "minecraft:the_nether", false);
        assertSame(invalid, invalid.observe(new Vec3(1000, 65, 1000), "test", "minecraft:overworld", GameType.ADVENTURE));
        assertSame(invalid, invalid.observe(Vec3.ZERO, "test", "minecraft:the_nether", null));
        assertFalse(invalid.observe(Vec3.ZERO, "test", "minecraft:the_nether", GameType.SURVIVAL).valid());
    }

    @Test
    void statisticsAllowLongJourneysMadeWithContinuousMovement() {
        var continuity = new HighwayBuilderTHM.StatsContinuity(Vec3.ZERO, "test", "minecraft:the_nether", true);
        for (int x = 1; x <= 1000; x++) {
            continuity = continuity.observe(new Vec3(x, 0, 0), "test", "minecraft:the_nether", GameType.SURVIVAL);
        }
        assertTrue(continuity.valid());
    }

    @Test
    void teleportInvalidationSurvivesReturningToTheOldPosition() {
        var continuity = new HighwayBuilderTHM.StatsContinuity(Vec3.ZERO, "test", "minecraft:the_nether", true);
        continuity = continuity.observe(new Vec3(10000, 0, 0), "test", "minecraft:the_nether", GameType.SURVIVAL);
        assertFalse(continuity.valid());
        continuity = continuity.observe(Vec3.ZERO, "test", "minecraft:the_nether", GameType.SURVIVAL);
        assertFalse(continuity.valid());
        assertFalse(continuity.observe(new Vec3(1, 0, 0), "test", "minecraft:the_nether", GameType.SURVIVAL).valid());
    }

    @Test
    void statisticsJumpLimitIncludesHeightAndDiagonalDistance() {
        var continuity = new HighwayBuilderTHM.StatsContinuity(Vec3.ZERO, "test", "minecraft:the_nether", true);
        assertTrue(continuity.observe(new Vec3(64, 0, 0), "test", "minecraft:the_nether", GameType.SURVIVAL).valid());
        assertFalse(continuity.observe(new Vec3(64.01, 0, 0), "test", "minecraft:the_nether", GameType.SURVIVAL).valid());
        assertFalse(continuity.observe(new Vec3(0, 65, 0), "test", "minecraft:the_nether", GameType.SURVIVAL).valid());
        assertFalse(continuity.observe(new Vec3(46, 0, 46), "test", "minecraft:the_nether", GameType.SURVIVAL).valid());
    }

    @Test
    void savedStatisticsCannotCrossWorldsOrDimensionsAtMatchingCoordinates() {
        var continuity = new HighwayBuilderTHM.StatsContinuity(Vec3.ZERO, "test", "minecraft:the_nether", true);
        assertFalse(continuity.observe(Vec3.ZERO, "other", "minecraft:the_nether", GameType.SURVIVAL).valid());
        assertFalse(continuity.observe(Vec3.ZERO, "test", "minecraft:overworld", GameType.SURVIVAL).valid());
    }

    @Test
    void restoredInvalidStatisticsAndUnknownCoordinatesStayInvalid() {
        var restored = new HighwayBuilderTHM.StatsContinuity(Vec3.ZERO, "test", "minecraft:the_nether", false);
        assertFalse(restored.observe(Vec3.ZERO, "test", "minecraft:the_nether", GameType.SURVIVAL).valid());
        var unknown = new HighwayBuilderTHM.StatsContinuity(new Vec3(Double.NaN, 0, 0), "test", "minecraft:the_nether", true);
        assertFalse(unknown.valid());
        assertFalse(unknown.observe(Vec3.ZERO, "test", "minecraft:the_nether", GameType.SURVIVAL).valid());
    }

    @Test
    void encryptedArtifactPayloadRetainsTheLastPositionAndInvalidation() {
        var continuity = new HighwayBuilderTHM.StatsContinuity(new Vec3(-100.5, 120, 22.5), "world|one\n two", "minecraft:the_nether", false);
        StringBuilder payload = new StringBuilder();
        continuity.write(payload);
        Map<String, String> meta = new HashMap<>();
        for (String line : payload.toString().split("\n")) {
            String[] parts = line.split("\\|", 3);
            meta.put(parts[1], parts[2]);
        }
        var restored = HighwayBuilderTHM.StatsContinuity.read(meta);
        assertEquals(continuity, restored);
        assertFalse(restored.observe(continuity.position(), continuity.world(), continuity.dimension(), GameType.SURVIVAL).valid());
        assertNull(HighwayBuilderTHM.StatsContinuity.read(Map.of()));
        meta.remove("continuityX");
        assertFalse(HighwayBuilderTHM.StatsContinuity.read(meta).valid());
    }

    @Test
    void pausedReturnRequiresTheSavedHeightAndPositionBeforeResuming() {
        var pause = new HighwayBuilderTHM.ManualPause(new Vec3(-16.9, 119.875, 23.9),
            HorizontalDirection.North, 20, "test", "minecraft:the_nether", false, true);
        assertFalse(pause.mainHighway());
        assertTrue(pause.mappedHighway(), "a paused ring-road build keeps its network eligibility");
        assertTrue(pause.arrived(pause.position()));
        assertTrue(pause.arrived(new Vec3(-16.5, 120, 23.5)));
        assertFalse(pause.arrived(new Vec3(-16.9, 120.875, 23.9)));
        assertFalse(pause.arrived(new Vec3(-15.9, 119.875, 23.9)));
        assertFalse(pause.arrived(new Vec3(-16.2, 119.875, 24.6)));
    }

    @Test
    void hotbarSupplyKeepsTheCurrentStackAndRefillsFromInventoryWhenEmpty() {
        var stock = new HashSet<>(Set.of(2, 6, 15));
        assertEquals(6, HighwayBuilderTHM.selectHotbarSupplySlot(36, 6, stock::contains));
        stock.remove(6);
        assertEquals(15, HighwayBuilderTHM.selectHotbarSupplySlot(36, 6, stock::contains));
        stock.remove(15);
        assertEquals(2, HighwayBuilderTHM.selectHotbarSupplySlot(36, 6, stock::contains));
        stock.clear();
        assertEquals(-1, HighwayBuilderTHM.selectHotbarSupplySlot(36, 6, stock::contains));
    }

    @Test
    void hotbarSupplyDoesNotAlternateEquivalentPickaxesAfterSwapping() {
        var stock = new HashSet<>(Set.of(1, 15));
        for (int tick = 0; tick < 100; tick++) {
            int source = HighwayBuilderTHM.selectHotbarSupplySlot(36, 7, stock::contains);
            if (tick == 0) {
                assertEquals(15, source);
                stock.remove(source);
                stock.add(7);
            } else {
                assertEquals(7, source);
            }
        }
    }

    @Test
    void oneTrashStackMeansOneAcrossTheHotbarAndInventory() {
        Set<Integer> trash = Set.of(0, 3, 5, 20);
        var keep = HighwayBuilderTHM.selectTrashKeepSlots(36, 1, trash::contains, Comparator.naturalOrder());
        assertEquals(Set.of(0), keep);
        assertEquals(3, trash.stream().filter(i -> !keep.contains(i)).count());
        assertEquals(Set.of(0, 3), HighwayBuilderTHM.selectTrashKeepSlots(36, 2, trash::contains, Comparator.naturalOrder()));
    }

    @Test
    void trashReserveKeepsPreferredSuppliesAndCapsAtAvailableStacks() {
        Set<Integer> trash = Set.of(5, 20);
        assertEquals(Set.of(20), HighwayBuilderTHM.selectTrashKeepSlots(36, 1, trash::contains, Comparator.reverseOrder()));
        assertEquals(trash, HighwayBuilderTHM.selectTrashKeepSlots(36, 10, trash::contains, Comparator.naturalOrder()));
        assertTrue(HighwayBuilderTHM.selectTrashKeepSlots(36, 0, trash::contains, Comparator.naturalOrder()).isEmpty());
        assertTrue(HighwayBuilderTHM.selectTrashKeepSlots(36, 1, i -> false, Comparator.naturalOrder()).isEmpty());
    }

    @Test
    void offhandRefillUsesSpareInventoryBeforeManagedOrUnmanagedHotbarStacks() {
        var stock = new HashSet<>(Arrays.asList(0, 6, 15));
        assertEquals(15, HighwayBuilderTHM.selectOffhandSupplySlot(36, stock::contains, i -> i == 6));
        stock.remove(15);
        assertEquals(0, HighwayBuilderTHM.selectOffhandSupplySlot(36, stock::contains, i -> i == 6));
        stock.remove(0);
        assertEquals(6, HighwayBuilderTHM.selectOffhandSupplySlot(36, stock::contains, i -> i == 6));
        stock.clear();
        assertEquals(-1, HighwayBuilderTHM.selectOffhandSupplySlot(36, stock::contains, i -> i == 6));
    }

    @Test
    void offhandMiningLeavesTheChestReserveOutsideTheConsumableHand() {
        int source = 64;
        int otherChests = 4;
        int offhand = 0;
        int reserve = 11;
        int keep = HighwayBuilderTHM.offhandSupplyReserve(otherChests, reserve, true);
        while (source > keep) {
            source--;
            offhand++;
        }
        assertEquals(7, source);
        assertEquals(reserve, source + otherChests);
        assertEquals(57, offhand);
        // Even consuming every packet-only placement leaves the inventory reserve intact.
        offhand = 0;
        assertEquals(reserve, source + otherChests + offhand);
    }

    @Test
    void offhandRefillKeepsOneItemInManagedSlotsWhenChestReserveIsElsewhere() {
        assertEquals(1, HighwayBuilderTHM.offhandSupplyReserve(64, 11, true));
        assertEquals(1, HighwayBuilderTHM.offhandSupplyReserve(0, 0, true));
        assertEquals(0, HighwayBuilderTHM.offhandSupplyReserve(64, 11, false));
        assertEquals(64, HighwayBuilderTHM.offhandSupplyReserve(0, 64, false));
    }

    @Test
    void restockSourceAnchorMatchesBlockadeHeightOnSlabsAndChestTops() {
        assertEquals(new BlockPos(-17, 120, 23), HighwayBuilderTHM.restockAnchor(-16.5, 119.875, 23.5));
        assertEquals(new BlockPos(-17, 120, 23), HighwayBuilderTHM.restockAnchor(-16.5, 119.5, 23.5));
        assertEquals(new BlockPos(-17, 120, 23), HighwayBuilderTHM.restockAnchor(-16.5, 120, 23.5));
    }

    @Test
    void equalPickaxesStopMovingAfterTheFirstManagedSlotSwap() {
        String[] inventory = new String[36];
        inventory[1] = "pickaxe-a";
        inventory[7] = "pickaxe-b";
        int selected = 4;
        int swaps = 0;
        for (int tick = 0; tick < 100; tick++) {
            int best = HighwayBuilderTHM.selectBestMiningTool(inventory.length, selected,
                i -> inventory[i] == null ? -1 : 8);
            if (best != 7) {
                String displaced = inventory[7];
                inventory[7] = inventory[best];
                inventory[best] = displaced;
                swaps++;
            }
            selected = 7;
        }
        assertEquals(1, swaps);
        assertEquals("pickaxe-a", inventory[7]);
    }

    @Test
    void strongerToolStillReplacesHeldTool() {
        double[] scores = new double[36];
        Arrays.fill(scores, -1);
        scores[7] = 8;
        scores[12] = 10;
        assertEquals(12, HighwayBuilderTHM.selectBestMiningTool(scores.length, 7, i -> scores[i]));
    }

    @Test
    void excludedHeldToolDoesNotWinAndNoEligibleToolReturnsMissing() {
        double[] scores = new double[36];
        Arrays.fill(scores, -1);
        assertEquals(-1, HighwayBuilderTHM.selectBestMiningTool(scores.length, 7, i -> scores[i]));
        scores[12] = 8;
        assertEquals(12, HighwayBuilderTHM.selectBestMiningTool(scores.length, 7, i -> scores[i]));
    }

    @Test
    void safetyEnclosureCoversAllFourSidesAtFeetAndHeadWithoutTouchingFloor() {
        BlockPos anchor = new BlockPos(-17, 119, 23);
        var targets = HighwayBuilderTHM.tpsSafetyTargets(anchor);
        assertEquals(8, targets.size());
        assertEquals(8, new HashSet<>(targets).size());
        for (BlockPos target : targets) {
            assertEquals(1, Math.abs(target.getX() - anchor.getX()) + Math.abs(target.getZ() - anchor.getZ()));
            assertTrue(target.getY() == anchor.getY() || target.getY() == anchor.getY() + 1);
        }
        assertFalse(targets.contains(anchor));
        assertFalse(targets.contains(anchor.below()));
    }
}
