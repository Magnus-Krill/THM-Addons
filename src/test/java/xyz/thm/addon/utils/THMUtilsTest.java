/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class THMUtilsTest {
    private static final Set<String> DOMAINS = Set.of("6b6t.org", "6b6t.cc", "6b6t.me");

    @Test
    void currentHighwayNamesTheOutboundSideRegardlessOfBuildDirection() {
        assertEquals("N", THMUtils.getCurrentHighway(0.5, -20000.5, true, HorizontalDirection.South));
        assertEquals("NE", THMUtils.getCurrentHighway(20000.5, -19999.5, true, HorizontalDirection.SouthWest));
        assertNull(THMUtils.getCurrentHighway(0.5, 62500.5, true, HorizontalDirection.East));
        assertNull(THMUtils.getCurrentHighway(0.5, 0.5, true, HorizontalDirection.East));
    }

    @Test
    void roadTypeUsesDirectionAtCrossingsAndDistinguishesTheGrid() {
        assertEquals("main", THMUtils.getCurrentHighwayType(0.5, 62500.5, true, HorizontalDirection.South));
        assertEquals("ring", THMUtils.getCurrentHighwayType(0.5, 62500.5, true, HorizontalDirection.East));
        assertEquals("diamond", THMUtils.getCurrentHighwayType(12500.5, 12500.5, true, HorizontalDirection.SouthWest));
        assertEquals("grid", THMUtils.getCurrentHighwayType(5000.5, 7500.5, true, HorizontalDirection.South));
        assertEquals("other", THMUtils.getCurrentHighwayType(5000.5, 7500.5, false, HorizontalDirection.South));
    }

    @ParameterizedTest
    @ValueSource(strings = {"6b6t.org", "play.6b6t.org", "6b6t.cc", "6b6t.me", " PLAY.6B6T.ORG.:25565 "})
    void recognizesRootsAndSubdomainsWithPortsAndTrailingDots(String address) {
        assertTrue(THMUtils.matchesAnarchyServer(address, DOMAINS));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fake6b6t.org", "6b6t.org.example.com", "example.com", "", "6b6t.org:invalid", "6b6t.org:0", "6b6t.org:65536", "[::1]:25565"})
    void rejectsUnrelatedHostsAndInvalidPorts(String address) {
        assertFalse(THMUtils.matchesAnarchyServer(address, DOMAINS));
    }

    @Test
    void unknownAddressDoesNotTriggerTheJoinNotification() {
        assertFalse(THMUtils.matchesAnarchyServer(null, DOMAINS));
    }
}
