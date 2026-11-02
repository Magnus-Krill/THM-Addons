/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/** Last observed container contents, bounded independently of play time. */
public final class BuildMaterialIndex {
    public static final int MAX_CONTAINERS = 2048;
    private final Map<BlockPos, Storage> entries = new LinkedHashMap<>();

    public record Storage(BlockPos pos, String block, Map<String, Integer> items) {
        public Storage {
            pos = pos.immutable();
            items = Map.copyOf(items);
        }
    }

    public Collection<Storage> entries() { return java.util.Collections.unmodifiableCollection(entries.values()); }
    public Storage get(BlockPos pos) { return entries.get(pos); }
    public void remove(BlockPos pos) { entries.remove(pos); }
    public void clear() { entries.clear(); }

    public boolean update(BlockPos pos, String block, Map<String, Integer> items) {
        Storage next = new Storage(pos, block, items);
        if (next.equals(entries.get(pos))) return false;
        if (!entries.containsKey(pos) && entries.size() >= MAX_CONTAINERS)
            throw new IllegalStateException("Storage index is full; remove unused containers.");
        entries.put(next.pos(), next);
        return true;
    }

    public Storage nearest(BlockPos from, Set<String> needed, Set<BlockPos> excluded) {
        return entries.values().stream()
            .filter(storage -> !excluded.contains(storage.pos()))
            .filter(storage -> needed.stream().anyMatch(item -> storage.items().getOrDefault(item, 0) > 0))
            .min(Comparator.comparingDouble(storage -> storage.pos().distSqr(from))).orElse(null);
    }

    public void load(Path path) throws IOException {
        clear();
        if (!Files.exists(path)) return;
        if (Files.size(path) > 8 * 1024 * 1024) throw new IOException("Storage index is too large.");
        Map<BlockPos, Storage> loaded = new LinkedHashMap<>();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IOException("Unsupported storage index version.");
            JsonArray containers = root.getAsJsonArray("containers");
            if (containers.size() > MAX_CONTAINERS) throw new IOException("Too many indexed containers.");
            for (var element : containers) {
                JsonObject container = element.getAsJsonObject();
                BlockPos pos = new BlockPos(container.get("x").getAsInt(), container.get("y").getAsInt(), container.get("z").getAsInt());
                Map<String, Integer> items = new LinkedHashMap<>();
                for (var entry : container.getAsJsonObject("items").entrySet()) {
                    int count = entry.getValue().getAsInt();
                    if (count < 1 || count > 54 * 99) throw new IOException("Invalid storage item count.");
                    items.put(entry.getKey(), count);
                }
                loaded.put(pos, new Storage(pos, container.get("block").getAsString(), items));
            }
        } catch (RuntimeException e) {
            throw new IOException("Invalid storage index.", e);
        }
        entries.putAll(loaded);
    }

    public void save(Path path) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray containers = new JsonArray();
        for (Storage storage : entries.values()) {
            JsonObject container = new JsonObject();
            container.addProperty("x", storage.pos().getX());
            container.addProperty("y", storage.pos().getY());
            container.addProperty("z", storage.pos().getZ());
            container.addProperty("block", storage.block());
            container.add("items", new Gson().toJsonTree(storage.items()));
            containers.add(container);
        }
        root.add("containers", containers);
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "storage-", ".tmp");
        try {
            Files.writeString(temporary, new Gson().toJson(root));
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
