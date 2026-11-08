/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class THMHwyMonitorTest {
    @Test
    void disconnectKeepsMonitorOwnedPauseButDoesNotResumeAnIdleBuilder() {
        assertTrue(THMHwyMonitor.shouldResumeBuilderAfterDisconnect(true, false));
        assertTrue(THMHwyMonitor.shouldResumeBuilderAfterDisconnect(false, true));
        assertFalse(THMHwyMonitor.shouldResumeBuilderAfterDisconnect(false, false));
    }

    @Test
    void groundedMisalignmentUsesTheSafetyGatedRecoveryCause() {
        var none = THMHwyMonitor.RecoveryCause.None;
        var misalignment = THMHwyMonitor.RecoveryCause.Misalignment;
        assertEquals(misalignment, THMHwyMonitor.selectRecoveryCause(none, 7.78, 99.94, true));
        assertEquals(misalignment, THMHwyMonitor.selectRecoveryCause(none, 0, 0.77, true));
        assertEquals(none, THMHwyMonitor.selectRecoveryCause(none, 0, 0.39, true));
        assertEquals(none, THMHwyMonitor.selectRecoveryCause(none, 0, 0.77, false));
        assertEquals(THMHwyMonitor.RecoveryCause.CenterStall,
            THMHwyMonitor.selectRecoveryCause(THMHwyMonitor.RecoveryCause.CenterStall, 0, 0, false));
        assertEquals(THMHwyMonitor.RecoveryCause.ForwardStall,
            THMHwyMonitor.selectRecoveryCause(THMHwyMonitor.RecoveryCause.ForwardStall, 0, 0, true));
    }

    @ParameterizedTest
    @CsvSource({"123.5,3131.5,-90,E,Ring", "123.5,3500.5,-90,E,Ring", "123.5,6250.5,-90,E,Ring",
        "123.5,12500.5,-90,E,Ring", "123.5,30000000.5,-90,E,Ring", "3456.5,6544.5,-135,NE,Diamond"})
    void mappedNetherRoadsDoNotTriggerMisalignment(double x, double z, float yaw, String direction, String highway) {
        var target = THMHwyMonitor.determineRecoveryTarget(x, z, yaw, 120, true, true, null, direction);

        assertNotNull(target);
        assertEquals(highway, target.highway());
        assertEquals(direction, target.travelDirection());
        assertEquals(THMHwyMonitor.RecoveryCause.None,
            THMHwyMonitor.selectRecoveryCause(THMHwyMonitor.RecoveryCause.None, target.distance(), 0, true));
    }

    @ParameterizedTest
    @CsvSource({"1,0", "-1,0", "0,1", "0,-1", "1,1", "1,-1", "-1,1", "-1,-1"})
    void retreatClearsServerTrackingAndReturnsWithinTheOriginalDistance(int dx, int dz) {
        for (int radius : new int[] {2, 4, 6, 16, 32}) {
            for (BlockPos start : new BlockPos[] {new BlockPos(-1, 120, -17), new BlockPos(15, 120, 32)}) {
                ChunkPos original = ChunkPos.containing(start);
                BlockPos first = THMHwyMonitor.chunkBackstepGoal(start, dx, dz, 1);
                BlockPos far = THMHwyMonitor.chunkRefreshFarGoal(start, dx, dz, radius);
                ChunkPos firstChunk = ChunkPos.containing(first);
                ChunkPos farChunk = ChunkPos.containing(far);
                assertEquals(original.x() - dx, firstChunk.x());
                assertEquals(original.z() - dz, firstChunk.z());
                assertEquals(start.getY(), far.getY());
                assertTrue(ChunkTrackingView.of(firstChunk, radius).contains(original));
                var dipped = ChunkTrackingView.of(farChunk, radius);
                assertFalse(dipped.contains(original));
                for (int x = -8; x <= 8; x++) {
                    for (int z = -8; z <= 8; z++) {
                        if (x * dx + z * dz >= 0) assertFalse(dipped.contains(original.x() + x, original.z() + z));
                    }
                }
            }
        }
    }

    @Test void fourChunksDoNotClearAFixedSixChunkServerRadius() {
        BlockPos start = new BlockPos(15, 120, 32);
        ChunkPos original = ChunkPos.containing(start);
        ChunkPos oldGoal = ChunkPos.containing(THMHwyMonitor.chunkBackstepGoal(start, 1, 0, 4));
        assertTrue(ChunkTrackingView.of(oldGoal, 6).contains(original));
        ChunkPos goal = ChunkPos.containing(THMHwyMonitor.chunkRefreshFarGoal(start, 1, 0, 6));
        assertFalse(ChunkTrackingView.of(goal, 6).contains(original));
    }

    @Test void replacementBeforeTheNextTickDoesNotLoseUnloadConfirmation() {
        assertTrue(THMHwyMonitor.canReturnFromChunkRefresh(true, true, true));
        assertTrue(THMHwyMonitor.canReturnFromChunkRefresh(true, false, false));
        assertFalse(THMHwyMonitor.canReturnFromChunkRefresh(true, false, true));
        assertFalse(THMHwyMonitor.canReturnFromChunkRefresh(false, false, false));
        assertFalse(THMHwyMonitor.canReturnFromChunkRefresh(false, true, true));
    }

    @Test void longerRetreatsAndReturnsHaveTimeToCompleteAtWalkingSpeed() {
        assertEquals(20 * 20, THMHwyMonitor.chunkRefreshTravelTimeoutTicks(16));
        for (double distance : new double[] {128, 256, 544}) {
            assertTrue(THMHwyMonitor.chunkRefreshTravelTimeoutTicks(distance) > distance / 3 * 20);
        }
    }
}
