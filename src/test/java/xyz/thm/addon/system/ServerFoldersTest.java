/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.system;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerFoldersTest {
    @Test
    void foldersAndAliasesRoundTripAndSearchAcrossFolders() {
        ServerFolders folders = new ServerFolders();
        String parent = folders.putFolder("", "Anarchy", ServerFolders.ROOT);
        String child = folders.putFolder("", "Events", parent);
        folders.setDetails("Play.Example.org:25565", child, "Weekend");

        ServerFolders loaded = new ServerFolders();
        loaded.fromTag(folders.toTag());
        assertEquals("Anarchy / Events", loaded.path(child));
        assertFalse(loaded.shows("play.example.org:25565", "Saved name", parent, ""));
        assertTrue(loaded.shows("play.example.org:25565", "Saved name", parent, "weekend"));
        assertTrue(loaded.shows("play.example.org:25565", "Saved name", child, ""));
        assertTrue(loaded.shows("play.example.org:25565", "Saved name", parent, ":25565"));
    }

    @Test
    void folderEditsKeepServersAndPreventCycles() {
        ServerFolders folders = new ServerFolders();
        String parent = folders.putFolder("", "Anarchy", ServerFolders.ROOT);
        String sibling = folders.putFolder("", "Other", ServerFolders.ROOT);
        String child = folders.putFolder("", "Events", parent);
        folders.setDetails("play.example.org", parent, "Weekend");

        assertEquals(List.of(ServerFolders.ROOT, sibling), folders.destinations(parent));
        folders.putFolder(parent, "Anarchy", child);
        assertEquals("Anarchy / Events", folders.path(child));
        assertTrue(folders.moveFolder(sibling, -1));
        assertEquals(sibling, folders.children(ServerFolders.ROOT).getFirst().id());

        folders.removeFolder(parent);
        assertEquals("Events", folders.path(child));
        assertEquals(new ServerFolders.Details(ServerFolders.ROOT, "Weekend"), folders.details("play.example.org"));
    }

    @Test
    void addressChangesKeepOtherEntriesAndTheirAliases() {
        ServerFolders folders = new ServerFolders();
        String folder = folders.putFolder("", "Anarchy", ServerFolders.ROOT);
        folders.setDetails("first.invalid", folder, "First");
        assertTrue(folders.changeAddress("first.invalid", "second.invalid", true));
        assertEquals(folders.details("first.invalid"), folders.details("second.invalid"));

        folders.setDetails("first.invalid", folder, "Original");
        assertTrue(folders.changeAddress("first.invalid", "second.invalid", false));
        assertEquals("First", folders.details("second.invalid").alias());
        assertEquals(ServerFolders.ROOT, folders.details("first.invalid").folder());
    }
}
