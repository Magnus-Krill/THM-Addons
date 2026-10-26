/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.thm.addon.mixin.meteor.OffhandMixin.HeldItemFilter.*;

class OffhandFilterTest {
    @Test
    void whitelistAndBlocklistInvertWhichHeldItemsAllowSwaps() {
        assertTrue(Off.allows(false));
        assertTrue(Off.allows(true));
        assertFalse(OnlyWhenHolding.allows(false));
        assertTrue(OnlyWhenHolding.allows(true));
        assertTrue(OnlyWhenNotHolding.allows(false));
        assertFalse(OnlyWhenNotHolding.allows(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void oldToggleMigratesWithoutChangingTheSourceOrItemList(boolean enabled) {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("name", "only-when-holding");
        legacy.putBoolean("value", enabled);
        CompoundTag items = new CompoundTag();
        items.putString("name", "held-items");
        ListTag selected = new ListTag();
        selected.add(net.minecraft.nbt.StringTag.valueOf("minecraft:diamond_sword"));
        items.put("value", selected);
        CompoundTag source = moduleTag(legacy, items);

        CompoundTag migrated = ModuleMixin.thm$migrateOffhandFilter(source);
        CompoundTag filter = (CompoundTag) settings(migrated).get(0);
        assertEquals("held-item-filter", filter.getStringOr("name", ""));
        assertEquals((enabled ? OnlyWhenHolding : Off).toString(), filter.getStringOr("value", ""));
        assertEquals(items, settings(migrated).get(1));
        assertEquals(legacy, settings(source).get(0));
        assertSame(migrated, ModuleMixin.thm$migrateOffhandFilter(migrated));
    }

    @Test
    void explicitNewModeTakesPriorityOverTheOldToggle() {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("name", "only-when-holding");
        legacy.putBoolean("value", true);
        CompoundTag mode = new CompoundTag();
        mode.putString("name", "held-item-filter");
        mode.putString("value", OnlyWhenNotHolding.toString());
        CompoundTag source = moduleTag(legacy, mode);
        assertSame(source, ModuleMixin.thm$migrateOffhandFilter(source));
        CompoundTag empty = new CompoundTag();
        assertSame(empty, ModuleMixin.thm$migrateOffhandFilter(empty));
    }

    private static CompoundTag moduleTag(CompoundTag... values) {
        ListTag settings = new ListTag();
        for (CompoundTag setting : values) settings.add(setting);
        CompoundTag combat = new CompoundTag();
        combat.putString("name", "Combat");
        combat.put("settings", settings);
        ListTag groups = new ListTag();
        groups.add(combat);
        CompoundTag settingsTag = new CompoundTag();
        settingsTag.put("groups", groups);
        CompoundTag module = new CompoundTag();
        module.put("settings", settingsTag);
        return module;
    }

    private static ListTag settings(CompoundTag module) {
        CompoundTag group = (CompoundTag) module.getCompoundOrEmpty("settings").getListOrEmpty("groups").get(0);
        return group.getListOrEmpty("settings");
    }
}
