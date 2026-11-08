/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.system;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ServerFolders {
    public static final String ALL = "*";
    public static final String ROOT = "";

    public record Folder(String id, String name, String parent) {}
    public record Details(String folder, String alias) {}

    private static final Details UNFILED = new Details(ROOT, "");

    private final Map<String, Folder> folders = new LinkedHashMap<>();
    private final Map<String, Details> servers = new LinkedHashMap<>();

    public List<Folder> children(String parent) {
        return folders.values().stream().filter(folder -> folder.parent().equals(parent)).toList();
    }

    public Folder get(String id) {
        return folders.get(id);
    }

    public List<String> destinations(String editedFolder) {
        List<String> result = new ArrayList<>();
        result.add(ROOT);
        for (Folder folder : folders.values()) {
            if (!within(folder.id(), editedFolder)) result.add(folder.id());
        }
        return result;
    }

    public String path(String id) {
        Folder folder = folders.get(id);
        if (folder == null) return "Unfiled";
        List<String> parts = new ArrayList<>();
        for (; folder != null; folder = folders.get(folder.parent())) parts.add(folder.name());
        return String.join(" / ", parts.reversed());
    }

    public String putFolder(String id, String name, String parent) {
        name = name.strip();
        if (name.isEmpty() || within(parent, id)) return id;
        if (id.isEmpty()) id = UUID.randomUUID().toString();
        folders.put(id, new Folder(id, name, validFolder(parent)));
        return id;
    }

    public void removeFolder(String id) {
        Folder removed = folders.remove(id);
        if (removed == null) return;
        folders.replaceAll((key, folder) -> folder.parent().equals(id)
            ? new Folder(key, folder.name(), removed.parent()) : folder);
        servers.replaceAll((key, details) -> details.folder().equals(id)
            ? new Details(ROOT, details.alias()) : details);
        servers.values().removeIf(details -> details.folder().isEmpty() && details.alias().isEmpty());
    }

    public boolean moveFolder(String id, int direction) {
        Folder folder = folders.get(id);
        if (folder == null) return false;
        List<Folder> siblings = children(folder.parent());
        int target = siblings.indexOf(folder) + direction;
        if (target < 0 || target >= siblings.size()) return false;
        List<Folder> order = new ArrayList<>(folders.values());
        Collections.swap(order, order.indexOf(folder), order.indexOf(siblings.get(target)));
        folders.clear();
        for (Folder item : order) folders.put(item.id(), item);
        return true;
    }

    public Details details(String address) {
        return servers.getOrDefault(addressKey(address), UNFILED);
    }

    public void setDetails(String address, String folder, String alias) {
        Details details = new Details(validFolder(folder), alias.strip());
        if (details.folder().isEmpty() && details.alias().isEmpty()) servers.remove(addressKey(address));
        else servers.put(addressKey(address), details);
    }

    public boolean changeAddress(String oldAddress, String newAddress, boolean keepOld) {
        String oldKey = addressKey(oldAddress);
        String newKey = addressKey(newAddress);
        if (oldKey.equals(newKey) || !servers.containsKey(oldKey)) return false;
        servers.putIfAbsent(newKey, servers.get(oldKey));
        if (!keepOld) servers.remove(oldKey);
        return true;
    }

    public boolean shows(String address, String name, String folder, String query) {
        Details details = details(address);
        if (query.isEmpty()) return folder.equals(ALL) || details.folder().equals(folder);
        return addressKey(address).contains(query) || name.toLowerCase(Locale.ROOT).contains(query)
            || details.alias().toLowerCase(Locale.ROOT).contains(query);
    }

    public CompoundTag toTag() {
        ListTag folderTags = new ListTag();
        for (Folder folder : folders.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", folder.id());
            tag.putString("name", folder.name());
            tag.putString("parent", folder.parent());
            folderTags.add(tag);
        }

        ListTag serverTags = new ListTag();
        servers.forEach((address, details) -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("address", address);
            tag.putString("folder", details.folder());
            tag.putString("alias", details.alias());
            serverTags.add(tag);
        });

        CompoundTag tag = new CompoundTag();
        tag.put("folders", folderTags);
        tag.put("servers", serverTags);
        return tag;
    }

    public void fromTag(CompoundTag tag) {
        folders.clear();
        servers.clear();
        ListTag folderTags = tag.getListOrEmpty("folders");
        for (var entry : folderTags) {
            if (!(entry instanceof CompoundTag saved)) continue;
            String id = saved.getStringOr("id", "");
            String name = saved.getStringOr("name", "").strip();
            if (!id.isEmpty() && !id.equals(ALL) && !name.isEmpty()) folders.put(id, new Folder(id, name, ROOT));
        }
        for (var entry : folderTags) {
            if (!(entry instanceof CompoundTag saved)) continue;
            String id = saved.getStringOr("id", "");
            Folder folder = folders.get(id);
            if (folder != null) putFolder(id, folder.name(), saved.getStringOr("parent", ROOT));
        }
        for (var entry : tag.getListOrEmpty("servers")) {
            if (!(entry instanceof CompoundTag saved)) continue;
            String address = saved.getStringOr("address", "");
            if (!address.isBlank()) setDetails(address, saved.getStringOr("folder", ROOT), saved.getStringOr("alias", ""));
        }
    }

    private boolean within(String id, String ancestor) {
        for (Folder folder = folders.get(id); folder != null; folder = folders.get(folder.parent())) {
            if (folder.id().equals(ancestor)) return true;
        }
        return false;
    }

    private String validFolder(String id) {
        return folders.containsKey(id) ? id : ROOT;
    }

    private static String addressKey(String address) {
        return address.strip().toLowerCase(Locale.ROOT);
    }
}
