/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.GamePacketTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import xyz.thm.addon.utils.InventoryManager;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SpeedmineTest {
    @Test
    void renderReadsDoNotAdvanceMining() {
        var progress = new Speedmine.MiningProgress(1000, 0.1f);
        double initial = progress.fraction(0.7);
        for (int frame = 0; frame < 1000; frame++) assertEquals(initial, progress.fraction(0.7));
        progress.advance(1050, 20, 0.1f, false);
        assertEquals(0.2 / 0.7, progress.fraction(0.7), 1e-7);
    }

    @Test
    void speedChangesOnlyAffectNewMiningWork() {
        var progress = new Speedmine.MiningProgress(0, 0);
        progress.advance(100, 20, 0.1f, false);
        progress.advance(200, 20, 0.01f, false);
        assertEquals(0.22, progress.fraction(1), 1e-7, "Fatigue must not erase previously earned work.");
        progress.advance(250, 20, 0.5f, false);
        assertEquals(0.72, progress.fraction(1), 1e-7, "A faster tool must not multiply the whole mining history.");
    }

    @Test
    void pausedMiningDoesNotCatchUpOnResume() {
        var progress = new Speedmine.MiningProgress(0, 0.1f);
        progress.advance(1000, 20, 0.1f, true);
        assertEquals(0.1, progress.fraction(1), 1e-7);
        progress.advance(1050, 20, 0.1f, false);
        assertEquals(0.2, progress.fraction(1), 1e-7);
    }

    @Test
    void miningSurvivesTpsChangesAndClockJitter() {
        var progress = new Speedmine.MiningProgress(0, 0);
        progress.advance(100, 20, 0.1f, false);
        progress.advance(200, 10, 0.1f, false);
        progress.advance(150, 10, 0.1f, false);
        progress.advance(250, 10, 0.1f, false);
        assertEquals(0.35, progress.fraction(1), 1e-7);
        progress.advance(300, 0, 0.1f, false);
        assertEquals(0.35, progress.fraction(1), 1e-7);
    }

    @Test
    void primaryThresholdDoesNotPrematurelyCompleteDelayedSecondary() {
        var progress = new Speedmine.MiningProgress(0, 0.7f);
        assertEquals(1, progress.fraction(0.7), 1e-6);
        assertTrue(progress.fraction(1) < 1);
        progress.advance(50, 20, 0.3f, false);
        assertEquals(1, progress.fraction(1));
    }

    @Test
    void repeatedStrongAttemptsCannotPostponeRecoveryForever() {
        BlockPos target = new BlockPos(1, 64, 0), other = target.east();
        var first = new Speedmine.PendingBreak(target, 2, true, false, 2000);
        var second = new Speedmine.PendingBreak(other, 5, true, true, 5000);
        assertEquals(2000, Speedmine.pendingDeadline(List.of(first, second), target, 30000));
        assertEquals(1500, Speedmine.pendingDeadline(List.of(first, second), target, 1500));
        assertEquals(5000, Speedmine.pendingDeadline(List.of(first, second), other, 30000));
        assertEquals(30000, Speedmine.pendingDeadline(List.of(first, second), other.east(), 30000));
    }

    @ParameterizedTest
    @CsvSource({"1,20,0,2000", "0.01,20,0,6000", "0.01,10,0,11000", "1,20,5000,11050", "0,20,0,30000", "1,0,0,30000", "0.001,5,5000,30000"})
    void confirmationTimeoutIncludesServerSpeedAndRoundTrip(float delta, float tps, int latency, long expected) {
        assertEquals(expected, Speedmine.confirmationWaitMillis(delta, tps, latency), 1);
    }

    @Test
    void bedrockSwingCheckUsesPacketTypesFromTheLimiter() {
        var blocked = Set.of(GamePacketTypes.SERVERBOUND_SWING);
        assertTrue(Speedmine.miningPacketBlocked(60, blocked, GamePacketTypes.SERVERBOUND_SWING));
        assertFalse(Speedmine.miningPacketBlocked(60, blocked, GamePacketTypes.SERVERBOUND_PLAYER_ACTION));
        assertFalse(Speedmine.miningPacketBlocked(0, blocked, GamePacketTypes.SERVERBOUND_SWING));
    }

    @Test
    void autoMineCanIdentifyExplicitlyBlockedMiningActions() {
        assertTrue(Speedmine.miningPacketBlocked(60, Set.of(GamePacketTypes.SERVERBOUND_PLAYER_ACTION),
            GamePacketTypes.SERVERBOUND_PLAYER_ACTION));
        assertFalse(Speedmine.miningPacketBlocked(60, Set.of(), GamePacketTypes.SERVERBOUND_PLAYER_ACTION));
    }

    @ParameterizedTest
    @CsvSource({
        "false,false,false,false,false,0.5,1,true",
        "false,false,false,false,false,1,1,true",
        "false,false,false,false,false,1.01,1,false",
        "false,false,false,false,false,0,0,true",
        "true,false,false,false,false,0,1,false",
        "false,true,false,false,false,0,1,false",
        "false,true,true,false,false,0.5,1,true",
        "false,false,false,true,false,0,1,false",
        "false,false,false,false,true,0,1,false"
    })
    void airMiningOnlyClaimsWithinConfiguredMovementAndGameModes(boolean grounded, boolean gliding,
            boolean allowGliding, boolean creative, boolean spectator, double speed, double maximum, boolean expected) {
        assertEquals(expected, Speedmine.canClaimAir(grounded, gliding, allowGliding, creative, spectator, speed, maximum));
    }

    @ParameterizedTest
    @CsvSource({
        "false,false,false,0,false",
        "false,true,false,0,true",
        "false,false,true,10,true",
        "false,false,false,10,true",
        "true,true,false,0,false",
        "true,false,true,10,false",
        "true,true,true,10,false",
        "true,false,false,10,false"
    })
    void multitaskAllowsManualAndAutomaticItemUse(boolean multitask, boolean usingItem, boolean eating,
                                                int priority, boolean expectedPause) {
        assertEquals(expectedPause, Speedmine.shouldPauseMining(multitask, usingItem, eating, priority));
    }

    @Test
    void multitaskStillYieldsToOtherInventoryOwners() {
        for (int priority : new int[] {InventoryManager.Priority.TOTEM, InventoryManager.Priority.SURROUND,
            InventoryManager.Priority.PEARL_PHASE}) {
            assertTrue(Speedmine.shouldPauseMining(true, false, false, priority));
            assertTrue(Speedmine.shouldPauseMining(true, true, true, priority));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "false,true,0,false",
        "false,true,10,false",
        "true,false,0,false",
        "true,false,10,false",
        "true,true,0,true",
        "true,true,10,true"
    })
    void bedrockEatingRequiresItsToggleAndManualOrAutoEating(boolean enabled, boolean eating,
                                                           int priority, boolean expected) {
        assertEquals(expected, Speedmine.canMineBedrockWhileEating(enabled, eating, priority));
    }

    @Test
    void bedrockEatingStillYieldsToOtherInventoryOwners() {
        for (int priority : new int[] {InventoryManager.Priority.TOTEM, InventoryManager.Priority.SURROUND,
            InventoryManager.Priority.PEARL_PHASE}) {
            assertFalse(Speedmine.canMineBedrockWhileEating(true, true, priority));
        }
    }

    @Test
    void bedrockEatingDoesNotUnpauseThePacketMiner() {
        assertTrue(Speedmine.canMineBedrockWhileEating(true, true, InventoryManager.Priority.EATING));
        assertTrue(Speedmine.shouldPauseMining(false, true, true, InventoryManager.Priority.EATING));
    }

    @ParameterizedTest
    @EnumSource(value = Speedmine.RebreakMode.class, names = {"Off", "Strict", "Bypass"})
    void replacementsCannotRebreakBeforeInitialServerConfirmation(Speedmine.RebreakMode mode) {
        assertFalse(mode.canRebreak(false, false, false));
        assertFalse(mode.canRebreak(false, true, true));
    }

    @Test
    void strictWaitsForEachServerResponse() {
        var mode = Speedmine.RebreakMode.Strict;
        assertTrue(mode.canRebreak(true, false, false));
        assertFalse(mode.canRebreak(true, true, false));
        assertTrue(mode.canRebreak(true, false, true));
        assertFalse(mode.canRebreak(true, true, true));
    }

    @Test
    void strongCanRebreakConsecutiveReplacementPacketsWithoutAnAirResponse() {
        var mode = Speedmine.RebreakMode.Strong;
        assertTrue(mode.canRebreak(true, false, false));
        assertTrue(mode.canRebreak(true, true, false));
        assertTrue(mode.isStrong(false), "Strong must skip the TPS start delay immediately.");
    }

    @Test
    void strongRecoversWhenBreakAndPlacementOmitTheInitialAirUpdate() {
        var mode = Speedmine.RebreakMode.Strong;
        assertTrue(mode.canRebreak(false, false, false), "A completed STOP must arm Strong without an air update.");
        assertTrue(mode.canRebreak(false, true, false), "Another replacement must not wait for the prior response.");
        assertTrue(mode.canRebreak(true, true, false));
        assertFalse(Speedmine.RebreakMode.Strict.canRebreak(false, false, false));
        assertFalse(Speedmine.RebreakMode.Bypass.canRebreak(false, false, false));
    }

    @Test
    void strongRetriesCompletedTargetWithoutBypassingAnotherBlocksValidation() {
        BlockPos target = new BlockPos(1, 64, 0), other = target.east();
        var completed = new Speedmine.PendingBreak(target, 2, true, false, 10000);
        var secondary = new Speedmine.PendingBreak(other, 5, true, true, 10000);
        var instant = new Speedmine.PendingBreak(other, 7, false, false, 10000);
        assertTrue(Speedmine.hasPendingNormalBreak(List.of(completed), null), "Strict keeps its confirmation wait.");
        assertFalse(Speedmine.hasPendingNormalBreak(List.of(completed, instant), target));
        assertTrue(Speedmine.hasPendingNormalBreak(List.of(completed, secondary), target));
        assertTrue(Speedmine.hasPendingNormalBreak(List.of(secondary), target));
        assertEquals(5, Speedmine.validationToolSlot(-1, List.of(completed, secondary), -1));
    }

    @Test
    void bypassOnlyStopsWaitingAfterAConfirmedRebreak() {
        var mode = Speedmine.RebreakMode.Bypass;
        assertTrue(mode.canRebreak(true, false, false));
        assertFalse(mode.canRebreak(true, true, false));
        assertFalse(mode.isStrong(false));
        assertTrue(mode.canRebreak(true, true, true));
        assertTrue(mode.isStrong(true));
        assertFalse(mode.canRebreak(true, true, false), "Starting another target must restore Strict behavior.");
    }

    @Test
    void offNeverUsesTheInstantRebreakPath() {
        assertFalse(Speedmine.RebreakMode.Off.canRebreak(true, false, false));
        assertFalse(Speedmine.RebreakMode.Off.canRebreak(true, false, true));
    }

    @ParameterizedTest
    @CsvSource({"1000,20,20", "1000,10,10", "1000,5,5", "50,20,1", "100,10,1", "1000,0,0", "1000,-5,0", "1000,30,20", "-50,20,0"})
    void progressAndStartBudgetFollowServerTicks(long millis, float tps, double ticks) {
        assertEquals(ticks, Speedmine.serverTicks(millis, tps), 1e-9);
    }

    @Test
    void changingTpsKeepsAlreadyEarnedProgress() {
        assertEquals(15, Speedmine.serverTicks(500, 20) + Speedmine.serverTicks(500, 10), 1e-9);
    }

    @Test
    void invalidEstimateFallsBackToVanillaRate() {
        assertEquals(20, Speedmine.serverTicks(1000, Float.NaN), 1e-9);
    }

    @Test
    void fullTpsMatchesUnsyncedStartsEvenWithoutCredit() {
        for (double credit : new double[] {0, 0.98, 1}) {
            assertTrue(Speedmine.canStart(true, 20, credit));
            assertTrue(Speedmine.canStart(false, 10, credit));
        }
        assertTrue(Speedmine.canStart(true, Float.NaN, 0));
    }

    @ParameterizedTest
    @CsvSource({"20,100", "19.99,99", "10,50", "5,25", "0,0"})
    void instantStartRateTracksTpsAcrossUnevenClientTicks(float tps, int expectedStarts) {
        double credit = 0;
        int starts = 0;
        for (int tick = 0; tick < 100; tick++) {
            credit = Speedmine.replenishStartCredit(credit, tick % 2 == 0 ? 49 : 51, tps);
            if (Speedmine.canStart(true, tps, credit)) {
                starts++;
                credit = Math.max(0, credit - 1);
            }
        }
        assertEquals(expectedStarts, starts);
    }

    @Test
    void startCreditCannotAccumulateAnUnlimitedBurstDuringPause() {
        double credit = Speedmine.replenishStartCredit(0, 60000, 10);
        assertTrue(Speedmine.canStart(true, 10, credit));
        assertTrue(Speedmine.canStart(true, 10, credit - 1));
        assertFalse(Speedmine.canStart(true, 10, credit - 2));
    }

    @ParameterizedTest
    @CsvSource({"false,false,false,false,false", "false,true,false,false,true", "false,false,true,false,true", "false,false,false,true,false", "true,false,false,false,true", "false,true,true,true,true"})
    void migrateLocalRemovalToSingleToggle(boolean current, boolean instant, boolean unvalidated,
                                          boolean slow, boolean expected) {
        CompoundTag original = new CompoundTag();
        ListTag values = new ListTag();
        values.add(booleanSetting("client-prediction", current));
        values.add(booleanSetting("instant-client-remove", instant));
        values.add(booleanSetting("validate-break", !unvalidated));
        values.add(booleanSetting("remove-slow-blocks", slow));
        values.add(booleanSetting("tps-sync", true));
        CompoundTag general = new CompoundTag();
        general.putString("name", "General");
        general.put("settings", values);
        ListTag groups = new ListTag();
        groups.add(general);
        CompoundTag savedSettings = new CompoundTag();
        savedSettings.put("groups", groups);
        original.put("settings", savedSettings);
        original.putBoolean("active", true);

        CompoundTag migrated = Speedmine.migrateSettings(original);
        assertTrue(migrated.getBooleanOr("active", false));
        assertEquals(5, values.size(), "Loading must not modify the original profile.");
        var migratedGroup = (CompoundTag) migrated.getCompoundOrEmpty("settings").getListOrEmpty("groups").get(0);
        var migratedValues = migratedGroup.getListOrEmpty("settings");
        assertEquals(2, migratedValues.size());
        assertEquals(expected, ((CompoundTag) migratedValues.get(0)).getBooleanOr("value", false));
        assertTrue(((CompoundTag) migratedValues.get(1)).getBooleanOr("value", false));
        assertEquals(migrated, Speedmine.migrateSettings(migrated));
    }

    @Test
    void migrationHandlesUnchangedLegacyDefaultsWithoutSettings() {
        CompoundTag tag = new CompoundTag();
        assertEquals(tag, Speedmine.migrateSettings(tag));
    }

    @Test
    void migrationAddsPredictionForLegacyProfileWithoutNewToggle() {
        CompoundTag tag = new CompoundTag();
        ListTag values = new ListTag();
        values.add(booleanSetting("instant-client-remove", true));
        CompoundTag general = new CompoundTag();
        general.putString("name", "General");
        general.put("settings", values);
        ListTag groups = new ListTag();
        groups.add(general);
        CompoundTag settings = new CompoundTag();
        settings.put("groups", groups);
        tag.put("settings", settings);

        var migratedGroup = (CompoundTag) Speedmine.migrateSettings(tag)
            .getCompoundOrEmpty("settings").getListOrEmpty("groups").get(0);
        assertEquals(1, migratedGroup.getListOrEmpty("settings").size());
        var prediction = (CompoundTag) migratedGroup.getListOrEmpty("settings").get(0);
        assertEquals("client-prediction", prediction.getStringOr("name", ""));
        assertTrue(prediction.getBooleanOr("value", false));
    }

    @Test
    void sameTickRestoresImmediatelyWithoutWaitingForConfirmation() {
        assertTrue(Speedmine.SwapMode.SameTick.shouldRelease(0, true, true, true, 0));
    }

    @Test
    void endOfTickRestoresAtPostWithoutWaitingForServer() {
        assertTrue(Speedmine.SwapMode.EndOfTick.shouldRelease(0, true, true, true, 0));
    }

    @Test
    void keepSpansNextTickAndConsecutiveReplacements() {
        var mode = Speedmine.SwapMode.Keep;
        assertFalse(mode.shouldRelease(0, false, false, false, 0));
        assertFalse(mode.shouldRelease(1, false, true, false, 1));
        assertFalse(mode.shouldRelease(5, false, true, false, 3), "TPS throttling must not release a queued rebreak.");
        assertFalse(mode.shouldRelease(0, false, false, false, 0), "Another STOP renews the hold.");
        assertTrue(mode.shouldRelease(1, false, false, false, 1));
    }

    @Test
    void keepDoesNotWaitForServerButRetainsActiveMining() {
        var mode = Speedmine.SwapMode.Keep;
        assertTrue(mode.shouldRelease(1, false, false, true, 0));
        assertFalse(mode.shouldRelease(10, true, false, false, 0));
    }

    @Test
    void confirmationModePreservesServerWaitAndIdleGrace() {
        var mode = Speedmine.SwapMode.ToolHold;
        assertFalse(mode.shouldRelease(100, false, false, true, 3));
        assertFalse(mode.shouldRelease(100, true, false, false, 3));
        assertFalse(mode.shouldRelease(100, false, false, false, 2));
        assertTrue(mode.shouldRelease(100, false, false, false, 3));
    }

    @ParameterizedTest
    @EnumSource(Speedmine.SwapMode.class)
    void normalBreaksHoldThroughSecondaryValidationInEveryMode(Speedmine.SwapMode selected) {
        var normal = selected.forBreak(false, false);
        assertEquals(Speedmine.SwapMode.ToolHold, normal);
        assertFalse(normal.shouldRelease(20, true, false, false, 3), "The secondary still needs a server tick with its tool.");
        assertFalse(normal.shouldRelease(20, false, false, true, 3), "Client prediction is not confirmation.");
        assertTrue(normal.shouldRelease(20, false, false, false, 3));
    }

    @ParameterizedTest
    @EnumSource(Speedmine.SwapMode.class)
    void instantAndPrimedBreaksKeepSelectedSwapTiming(Speedmine.SwapMode selected) {
        assertEquals(selected, selected.forBreak(true, false));
        assertEquals(selected, selected.forBreak(false, true));
    }

    @Test
    void bothCompletedBlocksRetainTheDelayedBlocksTool() {
        var firstClicked = new Speedmine.PendingBreak(new BlockPos(1, 64, 0), 2, true, true, 10000);
        var secondClicked = new Speedmine.PendingBreak(new BlockPos(2, 64, 0), 5, true, false, 10000);
        assertEquals(2, Speedmine.validationToolSlot(-1, List.of(secondClicked, firstClicked), -1));
        assertEquals(2, Speedmine.validationToolSlot(-1, List.of(firstClicked), -1),
            "Confirming the second block must not release the first block's tool.");
        assertEquals(5, Speedmine.validationToolSlot(-1, List.of(secondClicked), -1),
            "Confirming the first block must preserve the second block's validation.");
        assertEquals(-1, Speedmine.validationToolSlot(-1, List.of(), -1));
    }

    @Test
    void finishingPrimaryRestoresTheStillMiningSecondaryTool() {
        var secondClicked = new Speedmine.PendingBreak(new BlockPos(2, 64, 0), 5, true, false, 10000);
        assertEquals(2, Speedmine.validationToolSlot(2, List.of(secondClicked), -1));
        assertEquals(2, Speedmine.validationToolSlot(2, List.of(), 5));
    }

    @Test
    void predictedSecondaryKeepsItsToolWhilePrimaryStillMines() {
        var firstClicked = new Speedmine.PendingBreak(new BlockPos(1, 64, 0), 2, true, true, 10000);
        assertEquals(2, Speedmine.validationToolSlot(-1, List.of(firstClicked), 5));
        assertEquals(5, Speedmine.validationToolSlot(-1, List.of(), 5));
    }

    @Test
    void primedRebreakHoldDoesNotOverrideNormalValidation() {
        var rebreak = new Speedmine.PendingBreak(new BlockPos(3, 64, 0), 7, false, true, 10000);
        assertEquals(5, Speedmine.validationToolSlot(-1, List.of(rebreak), 5));
        assertEquals(-1, Speedmine.validationToolSlot(-1, List.of(rebreak), -1));
    }

    @ParameterizedTest
    @CsvSource({"true,ToolHold", "false,SameTick"})
    void migrateLegacyToolHold(boolean hold, Speedmine.SwapMode expected) {
        CompoundTag original = moduleSettings(booleanSetting("tool-hold", hold));
        CompoundTag migrated = Speedmine.migrateSettings(original);
        var group = (CompoundTag) migrated.getCompoundOrEmpty("settings").getListOrEmpty("groups").get(0);
        assertEquals(1, group.getListOrEmpty("settings").size());
        var mode = (CompoundTag) group.getListOrEmpty("settings").get(0);
        assertEquals("swap-mode", mode.getStringOr("name", ""));
        assertEquals(expected.toString(), mode.getStringOr("value", ""));
        assertEquals(migrated, Speedmine.migrateSettings(migrated));
    }

    @Test
    void explicitSwapModeWinsOverLegacyToolHold() {
        CompoundTag mode = new CompoundTag();
        mode.putString("name", "swap-mode");
        mode.putString("value", Speedmine.SwapMode.Keep.toString());
        CompoundTag migrated = Speedmine.migrateSettings(moduleSettings(booleanSetting("tool-hold", true), mode));
        var group = (CompoundTag) migrated.getCompoundOrEmpty("settings").getListOrEmpty("groups").get(0);
        assertEquals(1, group.getListOrEmpty("settings").size());
        assertEquals(mode, group.getListOrEmpty("settings").get(0));
    }

    private static CompoundTag moduleSettings(CompoundTag... entries) {
        ListTag values = new ListTag();
        for (CompoundTag entry : entries) values.add(entry);
        CompoundTag general = new CompoundTag();
        general.putString("name", "General");
        general.put("settings", values);
        ListTag groups = new ListTag();
        groups.add(general);
        CompoundTag settings = new CompoundTag();
        settings.put("groups", groups);
        CompoundTag module = new CompoundTag();
        module.put("settings", settings);
        return module;
    }

    private static CompoundTag booleanSetting(String name, boolean value) {
        CompoundTag setting = new CompoundTag();
        setting.putString("name", name);
        setting.putBoolean("value", value);
        return setting;
    }

}
