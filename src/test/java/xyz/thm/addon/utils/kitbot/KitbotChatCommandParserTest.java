/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.kitbot;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import xyz.thm.addon.modules.KitbotFrontend;
import xyz.thm.addon.utils.kitbot.KitbotChatCommandParser.CommandType;
import xyz.thm.addon.utils.kitbot.KitbotChatCommandParser.FollowUpKind;
import xyz.thm.addon.utils.kitbot.KitbotChatCommandParser.ParseResult;
import xyz.thm.addon.utils.kitbot.KitbotChatCommandParser.ParseStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KitbotChatCommandParserTest {
    private static final List<KitbotKitCatalog.Kit> KITS = KitbotKitCatalog.parse("""
        {"success":true,"kits":[{"name":"pvp","price":0.5},{"name":"totems","price":2},
        {"name":"echest","price":0.2},{"name":"future-kit","price":0}]}
        """);
    private static final List<String> ONLINE = List.of("Leonn170709", "DaReapsta");

    private static ParseResult parse(String text) {
        return KitbotChatCommandParser.parse(text, ONLINE, KITS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hello", "goto N", "$", "$   ", "$unknown", "$gotoN"})
    void ignoresNonCommands(String text) {
        assertEquals(ParseStatus.UNRECOGNIZED, parse(text).status());
    }

    @Test
    void nullIsUnrecognized() {
        assertFalse(parse(null).isRecognized());
    }

    @ParameterizedTest
    @CsvSource({
        "$goto N, North",
        "$goto n, North",
        "$GOTO dugNE, DugNorthEast",
        "  $goto   dugsw  , DugSouthWest",
        "$ goto W, West",
    })
    void parsesGotoDirections(String text, KitbotFrontend.Direction expected) {
        ParseResult r = parse(text);
        assertTrue(r.isValid(), r.errorMessage());
        assertEquals(CommandType.Goto, r.request().type());
        assertEquals(expected, r.request().direction());
        assertEquals(FollowUpKind.TPA, r.request().defaultFollowUpKind());
    }

    @Test
    void everyDirectionRoundTrips() {
        for (KitbotFrontend.Direction d : KitbotFrontend.Direction.values()) {
            ParseResult r = parse("$update " + d.command);
            assertTrue(r.isValid(), d.command);
            assertEquals(d, r.request().direction());
            assertEquals(FollowUpKind.TPY, r.request().defaultFollowUpKind());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"$goto", "$goto X", "$goto N S", "$update", "$update north"})
    void rejectsBadDirections(String text) {
        ParseResult r = parse(text);
        assertEquals(ParseStatus.INVALID, r.status());
        assertTrue(r.errorMessage().startsWith("Usage:"), r.errorMessage());
    }

    @ParameterizedTest
    @CsvSource({
        "$kit pvp, pvp, 1",
        "$kit PvP 16, pvp, 16",
        "$kit totems 3, totems, 3",
        "$kit Echest 1, echest, 1",
    })
    void parsesKits(String text, String kit, int amount) {
        ParseResult r = parse(text);
        assertTrue(r.isValid(), r.errorMessage());
        assertEquals(kit, r.request().kit().name());
        assertEquals(amount, r.request().amount());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "$kit nope | Unknown kit",
        "$kit pvp 0 | Amount must be between 1 and 16.",
        "$kit pvp 17 | Amount must be between 1 and 16.",
        "$kit pvp -1 | Amount must be between 1 and 16.",
        "$kit pvp two | Amount must be between 1 and 16.",
        "$kit | Usage:",
        "$kit pvp 1 extra | Usage:",
    })
    void rejectsBadKits(String text, String errorPrefix) {
        ParseResult r = parse(text);
        assertEquals(ParseStatus.INVALID, r.status());
        assertTrue(r.errorMessage().startsWith(errorPrefix), r.errorMessage());
    }

    @Test
    void sendNeedsExactOnlineName() {
        ParseResult ok = parse("$send DaReapsta pvp 2");
        assertTrue(ok.isValid());
        assertEquals("DaReapsta", ok.request().playerName());
        assertEquals(2, ok.request().amount());
        assertEquals(FollowUpKind.NONE, ok.request().defaultFollowUpKind());

        assertFalse(parse("$send dareapsta pvp").isValid());
        assertFalse(parse("$send Nobody pvp").isValid());
        assertFalse(KitbotChatCommandParser.parse("$send DaReapsta pvp", null, KITS).isValid());
    }

    @Test
    void newCatalogKitsWorkInOrdersSendsAndSuggestions() {
        ParseResult order = parse("$kit FUTURE-KIT 2");
        assertTrue(order.isValid(), order.errorMessage());
        assertEquals("future-kit", order.request().kit().name());
        assertTrue(parse("$send DaReapsta future-kit").isValid());
        assertEquals(List.of("future-kit"), texts(KitbotChatCommandParser.buildSuggestions("$kit fu", 7, ONLINE, KITS)));
        assertEquals(List.of("future-kit"), texts(KitbotChatCommandParser.buildSuggestions("$send DaReapsta fu", 17, ONLINE, KITS)));
        assertFalse(parse("$kit refill").isValid());
    }

    @Test
    void missingCatalogDoesNotFallBackToOldKitNames() {
        ParseResult result = KitbotChatCommandParser.parse("$kit pvp", ONLINE, List.of());
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("catalog unavailable"));
        assertTrue(KitbotChatCommandParser.buildSuggestions("$kit ", 5, ONLINE, List.of()).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"$token, Token", "$claim, Claim"})
    void parsesNoArgCommands(String text, CommandType type) {
        assertTrue(parse(text).isValid());
        assertEquals(type, parse(text).request().type());
        assertEquals(ParseStatus.INVALID, parse(text + " x").status());
    }

    @Test
    void suggestsCommandsByPrefix() {
        assertEquals(List.of("goto"), texts(KitbotChatCommandParser.buildSuggestions("$go", 3, ONLINE, KITS)));
        assertTrue(texts(KitbotChatCommandParser.buildSuggestions("$", 1, ONLINE, KITS)).containsAll(List.of("goto", "update", "kit", "send", "token", "claim")));
    }

    @Test
    void suggestionsReplaceCurrentTokenOnly() {
        Suggestions s = KitbotChatCommandParser.buildSuggestions("$goto du", 8, ONLINE, KITS);
        assertFalse(s.isEmpty());
        for (Suggestion suggestion : s.getList()) {
            assertEquals(6, suggestion.getRange().getStart());
            assertTrue(suggestion.getText().startsWith("dug"), suggestion.getText());
        }
    }

    @Test
    void noSuggestionsOutsideCommands() {
        assertTrue(KitbotChatCommandParser.buildSuggestions("hello", 5, ONLINE, KITS).isEmpty());
        assertTrue(KitbotChatCommandParser.buildSuggestions("", 0, ONLINE, KITS).isEmpty());
        assertTrue(KitbotChatCommandParser.buildSuggestions(null, 0, ONLINE, KITS).isEmpty());
        assertTrue(KitbotChatCommandParser.buildSuggestions("$token ", 7, ONLINE, KITS).isEmpty());
    }

    @Test
    void cursorBeyondTextIsClamped() {
        assertDoesNotThrow(() -> KitbotChatCommandParser.buildSuggestions("$kit", 999, ONLINE, KITS));
        assertDoesNotThrow(() -> KitbotChatCommandParser.buildSuggestions("$kit", -5, ONLINE, KITS));
    }

    private static List<String> texts(Suggestions s) {
        return s.getList().stream().map(Suggestion::getText).toList();
    }
}
