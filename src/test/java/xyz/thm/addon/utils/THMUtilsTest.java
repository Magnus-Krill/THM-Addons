/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class THMUtilsTest {
    private static final Set<String> DOMAINS = Set.of("6b6t.org", "6b6t.cc", "6b6t.me");

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
