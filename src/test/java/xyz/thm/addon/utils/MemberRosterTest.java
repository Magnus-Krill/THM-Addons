/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberRosterTest {
    @Test
    void keepsLinkedAccountsInOneMemberAndUsesMinecraftNameForPublicRows() {
        var members = APIUtils.parseMembers("""
            [
              {"usernames":["MainName","AltName","invalid name"],"rank":"Major","branch":"Main","discordname":"linked-member"},
              {"usernames":["PublicName"],"rank":"Apprentice","branch":"Main"},
              {"usernames":{}}
            ]
            """);

        assertEquals(2, members.size());
        assertArrayEquals(new String[]{"MainName", "AltName"}, members.getFirst().mcNames);
        assertEquals("linked-member", members.getFirst().discordName);
        assertEquals("PublicName", members.getLast().discordName);
    }
}
