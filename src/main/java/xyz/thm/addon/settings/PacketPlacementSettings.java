/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.settings;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.nbt.CompoundTag;

public final class PacketPlacementSettings {
    public enum Mode {
        Normal, Pace, Direct;

        public boolean isPacket() {
            return this != Normal;
        }
    }

    private final Setting<Boolean> packet;
    private final Setting<String> mode;

    public PacketPlacementSettings(SettingGroup group) {
        this(group, () -> true);
    }

    public PacketPlacementSettings(SettingGroup group, IVisible visible) {
        packet = group.add(new BoolSetting.Builder()
            .name("packet").description("Place without client prediction.")
            .defaultValue(false).visible(visible).build());
        mode = group.add(new ProvidedStringSetting.Builder()
            .name("packet-mode").description("Choose how to send placement packets.")
            .defaultValue("Pace").supplier(() -> new String[]{"Pace", "Direct"})
            .visible(() -> visible.isVisible() && enabled()).build());
    }

    public boolean enabled() {
        return packet.get();
    }

    public Mode mode() {
        if (!enabled()) return Mode.Normal;
        return "Direct".equals(mode.get()) ? Mode.Direct : Mode.Pace;
    }

    public static CompoundTag migrateToggle(CompoundTag source, String legacyName) {
        boolean legacy = false;
        for (var entry : source.getListOrEmpty("groups")) {
            if (!(entry instanceof CompoundTag group)) continue;
            for (var value : group.getListOrEmpty("settings")) {
                if (!(value instanceof CompoundTag setting)) continue;
                String name = setting.getStringOr("name", "");
                if (name.equals("packet")) return source;
                if (name.equals(legacyName)) legacy = true;
            }
        }
        if (!legacy) return source;

        CompoundTag updated = source.copy();
        for (var entry : updated.getListOrEmpty("groups")) {
            if (!(entry instanceof CompoundTag group)) continue;
            for (var value : group.getListOrEmpty("settings")) {
                if (value instanceof CompoundTag setting && setting.getStringOr("name", "").equals(legacyName)) {
                    setting.putString("name", "packet");
                }
            }
        }
        return updated;
    }
}
