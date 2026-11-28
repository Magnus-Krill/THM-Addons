/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.utils;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildMaterialIndexTest {
    @TempDir Path temporary;

    @Test void routesToNearestSupplierAndFallsBackAfterEmptyOrUnreachableStorage() {
        BuildMaterialIndex index = new BuildMaterialIndex();
        BlockPos near = new BlockPos(1, 64, 0), far = new BlockPos(10, 64, 0), unrelated = new BlockPos(0, 64, 0);
        index.update(near, "minecraft:chest", Map.of("minecraft:stone", 64));
        index.update(far, "minecraft:barrel", Map.of("minecraft:stone", 128));
        index.update(unrelated, "minecraft:chest", Map.of("minecraft:dirt", 64));
        Set<String> needed = Set.of("minecraft:stone");
        assertEquals(near, index.nearest(unrelated, needed, Set.of()).pos());
        assertEquals(far, index.nearest(unrelated, needed, Set.of(near)).pos());
        index.update(near, "minecraft:chest", Map.of());
        assertEquals(far, index.nearest(unrelated, needed, Set.of()).pos());
        index.remove(far);
        assertNull(index.nearest(unrelated, needed, Set.of()));
    }

    @Test void savesSnapshotsAndKeepsWorldFilesIndependent() throws IOException {
        Path overworld = temporary.resolve("server-a/overworld.json");
        Path nether = temporary.resolve("server-a/nether.json");
        BlockPos pos = new BlockPos(-120, 64, 400);
        BuildMaterialIndex index = new BuildMaterialIndex();
        Map<String, Integer> contents = new HashMap<>(Map.of("minecraft:oak_log", 17));
        assertTrue(index.update(pos, "minecraft:chest", contents));
        contents.clear();
        assertFalse(index.update(pos, "minecraft:chest", Map.of("minecraft:oak_log", 17)));
        index.save(overworld);
        index.update(pos, "minecraft:barrel", Map.of("minecraft:obsidian", 64));
        index.save(nether);
        index.load(overworld);
        assertEquals(Map.of("minecraft:oak_log", 17), index.get(pos).items());
        assertEquals("minecraft:chest", index.get(pos).block());
        index.load(nether);
        assertEquals(Map.of("minecraft:obsidian", 64), index.get(pos).items());
        index.load(temporary.resolve("another-server.json"));
        assertTrue(index.entries().isEmpty());
    }

    @Test void keepsOwnedShulkersAcrossRefreshesAndRestartsUntilReplaced() throws IOException {
        Path path = temporary.resolve("owned.json");
        BlockPos pos = new BlockPos(0, 318, 0);
        BuildMaterialIndex index = new BuildMaterialIndex();
        index.update(pos, "minecraft:shulker_box", Map.of("minecraft:black_concrete", 640), true);
        index.update(pos, "minecraft:shulker_box", Map.of("minecraft:black_concrete", 64));
        index.save(path);
        index.load(path);
        assertTrue(index.get(pos).owned());
        index.update(pos, "minecraft:chest", Map.of());
        assertFalse(index.get(pos).owned());
    }

    @Test void neverPartiallyLoadsCorruptSnapshots() throws IOException {
        Path path = temporary.resolve("broken.json");
        Files.writeString(path, """
            {"version":1,"containers":[
              {"x":0,"y":64,"z":0,"block":"minecraft:chest","items":{"minecraft:stone":64}},
              {"x":1,"y":64,"z":0,"block":"minecraft:chest","items":{"minecraft:stone":-1}}
            ]}
            """);
        BuildMaterialIndex index = new BuildMaterialIndex();
        assertThrows(IOException.class, () -> index.load(path));
        assertTrue(index.entries().isEmpty());
        assertTrue(Files.readString(path).contains("-1"));
    }

    @Test void capsHistoryButStillAllowsRefreshingKnownStorage() {
        BuildMaterialIndex index = new BuildMaterialIndex();
        for (int i = 0; i < BuildMaterialIndex.MAX_CONTAINERS; i++)
            index.update(new BlockPos(i, 64, 0), "minecraft:chest", Map.of());
        assertThrows(IllegalStateException.class, () -> index.update(new BlockPos(-1, 64, 0), "minecraft:chest", Map.of()));
        assertTrue(index.update(new BlockPos(0, 64, 0), "minecraft:chest", Map.of("minecraft:dirt", 1)));
        assertEquals(BuildMaterialIndex.MAX_CONTAINERS, index.entries().size());
    }
}
