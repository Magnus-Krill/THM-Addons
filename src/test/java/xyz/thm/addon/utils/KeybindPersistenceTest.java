/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeybindPersistenceTest {
    @Test
    void freshAndMissingModuleBindsStayUnbound() {
        Keybind bind = Keybind.none();
        assertFalse(bind.isSet());
        bind.fromTag(new CompoundTag().getCompoundOrEmpty("keybind"));
        assertFalse(bind.isSet());
        for (int button = 0; button < 8; button++) {
            assertFalse(bind.matches(false, button, 0));
        }
    }

    @Test
    void emptyBindClearsPreviousMouseAssignment() {
        Keybind bind = Keybind.fromButton(4);
        bind.fromTag(new CompoundTag());
        assertFalse(bind.isSet());
    }

    @Test
    void legacyUnboundSurvivesLoadingAndSaving() {
        Keybind bind = Keybind.none().fromTag(legacy(true, -1, 0));
        assertFalse(bind.isSet());
        assertFalse(Keybind.fromButton(4).fromTag(bind.toTag()).isSet());
    }

    @Test
    void legacyKeyboardModifiersAndSideButtonSurviveLoadingAndSaving() {
        Keybind keyboard = Keybind.none().fromTag(legacy(true, 75, 2));
        assertTrue(keyboard.matches(true, 75, 2));
        assertFalse(keyboard.matches(true, 75, 0));
        assertEquals(keyboard, Keybind.none().fromTag(keyboard.toTag()));

        Keybind mouse = Keybind.none().fromTag(legacy(false, 4, 0));
        assertTrue(mouse.matches(false, 4, 0));
        assertFalse(mouse.matches(true, 4, 0));
        assertEquals(mouse, Keybind.none().fromTag(mouse.toTag()));
    }

    @Test
    void explicitlySavedLeftClickStillLoadsAsLeftClick() {
        // Upgrading cannot distinguish a saved bad bind from an intentional one.
        Keybind bind = Keybind.none().fromTag(legacy(false, 0, 0));
        assertTrue(bind.isSet());
        assertTrue(bind.matches(false, 0, 0));
        assertTrue(Keybind.none().fromTag(bind.toTag()).matches(false, 0, 0));
    }

    private static CompoundTag legacy(boolean isKey, int value, int modifiers) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isKey", isKey);
        tag.putInt("value", value);
        tag.putInt("modifiers", modifiers);
        return tag;
    }
}
