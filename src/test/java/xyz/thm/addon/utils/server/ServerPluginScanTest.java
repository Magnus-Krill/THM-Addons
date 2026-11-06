/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerPluginScanTest {
    @Test
    void rawServerSnapshotDoesNotAcquireLaterClientCommands() {
        var scan = new ServerPluginScan();
        var root = new ArrayList<>(List.of("minecraft:help", "servermod:status"));
        scan.setCommands(root);
        root.add("clientmod:config");
        assertEquals(List.of("servermod"), scan.names());
        assertEquals(List.of("minecraft:help", "servermod:status"), scan.commands());
    }

    @Test
    void serverModsAreNotExcludedJustBecauseTheirNamesCouldAlsoExistOnTheClient() {
        var scan = new ServerPluginScan();
        scan.setCommands(List.of("fabric:help", "carpet:tick", "minecraft:help"));
        assertEquals(List.of("carpet", "fabric"), scan.names());
    }

    @Test
    void versionCompletionAddsNamesAndIgnoresUnrelatedReplies() {
        var scan = new ServerPluginScan();
        scan.setCommands(List.of("bukkit:version", "version", "worldedit:wand"));
        var request = scan.start(0);
        assertTrue(request.id() < 0);
        assertEquals("bukkit:version ", request.command());
        assertNull(scan.start(1), "A repeated invocation must not create another scan.");
        assertNull(scan.accept(42, List.of("ClientOnlyMod"), 2));
        assertTrue(scan.running());
        scan.accept(request.id(), List.of("WorldEdit", "LuckPerms", "luckperms"), 3);
        assertFalse(scan.running());
        assertEquals(List.of("LuckPerms", "worldedit"), scan.names());
    }

    @Test
    void emptyVersionResultsFallBackToServerRootCompletionsWithANewId() {
        var scan = new ServerPluginScan();
        scan.setCommands(List.of("about"));
        var first = scan.start(0);
        var second = scan.accept(first.id(), List.of(), 1);
        assertEquals("/", second.command());
        assertNotEquals(first.id(), second.id());
        scan.accept(first.id(), List.of("LatePlugin"), 2);
        assertTrue(scan.running());
        scan.accept(second.id(), List.of("/carpet:tick", "fabric:help", "minecraft:help", "bukkit:plugins", "SomePlayer", "player: chat text"), 3);
        assertFalse(scan.running());
        assertEquals(List.of("carpet", "fabric"), scan.names());
    }

    @Test
    void missingVersionAliasStartsAtRootAndDoesNotInventPluginsFromBareCommands() {
        var scan = new ServerPluginScan();
        scan.setCommands(List.of("help", "warp"));
        var request = scan.start(0);
        assertEquals("/", request.command());
        scan.accept(request.id(), List.of("help", "warp", "Steve"), 1);
        assertFalse(scan.running());
        assertEquals(List.of(), scan.names());
    }

    @Test
    void timeoutIsBoundedToTwoRequestsAndCanBeRetried() {
        var scan = new ServerPluginScan();
        scan.setCommands(List.of("version"));
        var first = scan.start(0);
        assertNull(scan.tick(4_999_999_999L));
        var second = scan.tick(5_000_000_000L);
        assertNotNull(second);
        assertEquals("/", second.command());
        assertNull(scan.tick(10_000_000_000L));
        assertFalse(scan.running());
        assertTrue(scan.timedOut());
        assertNull(scan.tick(15_000_000_000L));
        assertNotEquals(first.id(), scan.start(15_000_000_001L).id());
        assertFalse(scan.timedOut());
    }

    @Test
    void reconnectClearsResultsAndRejectsThePreviousConnectionsRequest() {
        var scan = new ServerPluginScan();
        scan.setCommands(List.of("oldserver:home"));
        var old = scan.start(0);
        scan.reset();
        assertTrue(scan.names().isEmpty());
        assertTrue(scan.commands().isEmpty());
        var current = scan.start(1);
        scan.accept(old.id(), List.of("oldserver:home"), 2);
        assertTrue(scan.names().isEmpty());
        assertTrue(scan.running());
        scan.accept(current.id(), List.of("newserver:home"), 3);
        assertEquals(List.of("newserver"), scan.names());
    }

    @Test
    void malformedVersionSuggestionsFallBackInsteadOfBecomingPluginNames() {
        var scan = new ServerPluginScan();
        scan.setCommands(List.of("version"));
        var first = scan.start(0);
        var second = scan.accept(first.id(), List.of("", "/op", "plugin\nInjected chat", "https://example.org"), 1);
        assertNotNull(second);
        assertTrue(scan.names().isEmpty());
    }
}
