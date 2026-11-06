/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighwayPresetManagerTest {
    @Test
    void namedPresetsKeepTheDisabledLegacyAlignmentPreference() throws IOException {
        CompoundTag decoded = HighwayPresetManager.decode("""
            {"version":1,"settings":{"groups":[{"name":"General","settings":[{"name":"align-first","value":false}]}]}}
            """);
        CompoundTag group = (CompoundTag) decoded.getListOrEmpty("groups").getFirst();
        CompoundTag setting = (CompoundTag) group.getListOrEmpty("settings").getFirst();
        assertEquals("auto-align", setting.getStringOr("name", ""));
        assertFalse(setting.getBooleanOr("value", true));
        assertEquals(decoded, HighwayPresetManager.decode(HighwayPresetManager.encode(decoded)));
    }

    @Test
    void presetJsonRoundTripsSettings() throws IOException {
        CompoundTag settings = new CompoundTag();
        var groups = new ListTag();
        for (boolean expanded : new boolean[]{true, false}) {
            var group = new CompoundTag();
            group.putString("name", expanded ? "Paving" : "General");
            group.putBoolean("sectionExpanded", expanded);
            groups.add(group);
        }
        settings.put("groups", groups);

        CompoundTag decoded = HighwayPresetManager.decode(HighwayPresetManager.encode(settings));

        assertEquals(settings, decoded);
    }

    @Test
    void groupExpansionAcceptsJsonBooleansAndRejectsOtherValues() throws IOException {
        String preset = """
            {"version":1,"settings":{"groups":[{"name":"Paving","sectionExpanded":%s}]}}
            """;
        var group = (CompoundTag) HighwayPresetManager.decode(preset.formatted("true")).getListOrEmpty("groups").getFirst();
        assertTrue(group.getBooleanOr("sectionExpanded", false));
        group = (CompoundTag) HighwayPresetManager.decode(preset.formatted("false")).getListOrEmpty("groups").getFirst();
        assertFalse(group.getBooleanOr("sectionExpanded", true));
        assertThrows(IOException.class, () -> HighwayPresetManager.decode(preset.formatted("2")));
        assertThrows(IOException.class, () -> HighwayPresetManager.decode(preset.formatted("\"1\"")));
    }

    @Test
    void presetNameCannotEscapeItsDirectory() {
        assertEquals("MyPreset", HighwayPresetManager.safeName(" My/Preset "));
        assertEquals("", HighwayPresetManager.safeName(".."));
    }

    @Test
    void movedSettingsFromOlderPresetsKeepTheirValues(@TempDir Path temp) throws IOException {
        String old = """
            {"version":1,"settings":{"groups":[
              {"name":"Experimental","settings":[
                {"name":"mine-lookahead","value":0},
                {"name":"predictive-echest-replace","value":1},
                {"name":"packet-budget","value":0}
              ]},
              {"name":"Digging","settings":[]},
              {"name":"Ender Chests","settings":[]}
            ]}}
            """;

        Path preset = temp.resolve("old.json");
        Files.writeString(preset, old);
        HighwayPresetManager.persistMigratedPreset(preset, HighwayPresetManager.decode(old));
        assertEquals(old, Files.readString(temp.resolve("old.json.pre-migration.bak")));

        JsonArray groups = JsonParser.parseString(Files.readString(preset))
            .getAsJsonObject().getAsJsonObject("settings").getAsJsonArray("groups");

        assertEquals(0, groups.get(0).getAsJsonObject().getAsJsonArray("settings").size());
        assertEquals("mine-lookahead", groups.get(1).getAsJsonObject().getAsJsonArray("settings")
            .get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(0, groups.get(1).getAsJsonObject().getAsJsonArray("settings")
            .get(0).getAsJsonObject().get("value").getAsInt());
        assertEquals("predictive-echest-replace", groups.get(2).getAsJsonObject().getAsJsonArray("settings")
            .get(0).getAsJsonObject().get("name").getAsString());
        var general = groups.get(3).getAsJsonObject();
        assertEquals("General", general.get("name").getAsString());
        var budget = general.getAsJsonArray("settings").get(0).getAsJsonObject();
        assertEquals("packet-budget", budget.get("name").getAsString());
        assertEquals(0, budget.get("value").getAsInt());
    }

    @Test
    void untrustedJsonRejectsAmbiguousOrDeepDocuments() {
        assertThrows(IOException.class, () -> HighwayPresetManager.decode(
            "{\"version\":1,\"version\":1,\"settings\":{}}"
        ));
        assertThrows(IOException.class, () -> HighwayPresetManager.decode(
            "{\"version\":1,\"settings\":{},\"extra\":true}"
        ));

        String nested = "[".repeat(40) + "]".repeat(40);
        assertThrows(IOException.class, () -> HighwayPresetManager.decode(
            "{\"version\":1,\"settings\":{\"groups\":" + nested + "}}"
        ));
    }
}
