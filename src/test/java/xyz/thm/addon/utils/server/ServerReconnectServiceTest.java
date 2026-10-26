/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.server;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.thm.addon.utils.server.ServerReconnectService.ReconnectStage.*;
import static xyz.thm.addon.utils.server.ServerStatusHandler.ServerState;

class ServerReconnectServiceTest {
    @Test
    void transferAndReturnVisitsNeverSkipTheLobbyPath() {
        for (ServerState state : List.of(ServerState.TRANSFER_SERVER, ServerState.MAIN_LOBBY,
            ServerState.MAIN_SERVER, ServerState.TRANSFER_SERVER, ServerState.MAIN_LOBBY,
            ServerState.UNKNOWN, ServerState.CRACKED_LOBBY, ServerState.MAIN_LOBBY)) {
            var expected = switch (state) {
                case MAIN_LOBBY -> MAIN_LOBBY_PATH;
                case CRACKED_LOBBY -> CRACKED_LOBBY_PATH;
                case TRANSFER_SERVER -> TRANSFER_WAIT;
                case MAIN_SERVER -> MAIN_SERVER_READY;
                default -> AWAITING_RECONNECT;
            };
            assertEquals(expected, ServerReconnectService.stageForState(state, true));
        }
    }

    @ParameterizedTest
    @EnumSource(ServerState.class)
    void armingWhileConnectedDoesNotStartALobbyPathBeforeDisconnect(ServerState state) {
        assertEquals(AWAITING_RECONNECT, ServerReconnectService.stageForState(state, false));
    }

    @Test
    void nearestPortalUsesTheLobbyEntryPositionAndIncludesHeight() {
        Vec3 entry = new Vec3(15.9, 64, -0.1);
        BlockPos nearest = new BlockPos(16, 64, -1);
        BlockPos far = new BlockPos(-12, 64, 0);
        BlockPos upstairs = new BlockPos(15, 84, 0);
        assertEquals(nearest, ServerReconnectService.nearestPortal(entry, List.of(far, upstairs, nearest)));
        assertEquals(nearest, ServerReconnectService.nearestPortal(entry, List.of(nearest, far, upstairs)));
    }

    @Test
    void unloadedPortalsLeaveTheStageWaitingForChunks() {
        assertNull(ServerReconnectService.nearestPortal(Vec3.ZERO, List.of()));
    }
}
