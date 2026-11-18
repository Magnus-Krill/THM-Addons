/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Modules.class, remap = false)
public abstract class ModulesMixin {
    @ModifyVariable(method = "fromTag(Lnet/minecraft/nbt/CompoundTag;)Lmeteordevelopment/meteorclient/systems/modules/Modules;",
        at = @At("HEAD"), argsOnly = true)
    private CompoundTag thm$loadHighwayUtilities(CompoundTag tag) {
        return migrateHighwayUtilities(tag);
    }

    private static CompoundTag migrateHighwayUtilities(CompoundTag source) {
        ListTag modules = source.getListOrEmpty("modules");
        CompoundTag pacing = find(modules, "catch-up-pacing");
        CompoundTag antiDrop = find(modules, "AntiDrop");
        boolean migratePacing = pacing != null && pacing.getBooleanOr("active", false);
        if (!migratePacing && antiDrop == null) return source;

        CompoundTag migrated = source.copy();
        ListTag migratedModules = migrated.getListOrEmpty("modules");
        CompoundTag builder = find(migratedModules, "THM-HighwayBuilder");
        if (builder == null) {
            builder = new CompoundTag();
            builder.putString("name", "THM-HighwayBuilder");
            migratedModules.add(builder);
        }

        CompoundTag settings = builder.getCompoundOrEmpty("settings");
        ListTag groups = settings.getListOrEmpty("groups");
        builder.put("settings", settings);
        settings.put("groups", groups);
        CompoundTag enabled = new CompoundTag();
        enabled.putBoolean("value", true);
        if (migratePacing) transfer(groups, "General", "catch-up-pacing", enabled);
        if (antiDrop != null) {
            if (antiDrop.getBooleanOr("active", false)) transfer(groups, "Inventory", "Anti-drop", enabled);
            CompoundTag general = find(antiDrop.getCompoundOrEmpty("settings").getListOrEmpty("groups"), "General");
            if (general != null) {
                ListTag oldSettings = general.getListOrEmpty("settings");
                for (String name : new String[] {"all-items", "check-shulkers", "items"}) {
                    CompoundTag oldSetting = find(oldSettings, name);
                    if (oldSetting != null) transfer(groups, "Inventory", "anti-drop-" + name, oldSetting);
                }
            }
        }
        return migrated;
    }

    private static void transfer(ListTag groups, String groupName, String settingName, CompoundTag previous) {
        CompoundTag group = find(groups, groupName);
        if (group == null) {
            group = new CompoundTag();
            group.putString("name", groupName);
            group.putBoolean("sectionExpanded", true);
            groups.add(group);
        }
        ListTag settings = group.getListOrEmpty("settings");
        if (find(settings, settingName) != null) return;
        CompoundTag setting = previous.copy();
        setting.putString("name", settingName);
        settings.add(setting);
        group.put("settings", settings);
    }

    private static CompoundTag find(ListTag entries, String name) {
        for (var entry : entries) {
            if (entry instanceof CompoundTag tag && name.equals(tag.getStringOr("name", ""))) return tag;
        }
        return null;
    }
}
