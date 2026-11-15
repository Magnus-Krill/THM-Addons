/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LocalClientBridgeTest {
    @Test
    void readsOneBoundedJsonRequestAndRequiresStringArguments() throws Exception {
        var request = LocalClientBridge.readRequest(channel("{\"method\":\"chat\",\"message\":\"Bereit\"}\nignored"));
        assertEquals("chat", LocalClientBridge.requiredString(request, "method"));
        assertEquals("Bereit", LocalClientBridge.requiredString(request, "message"));
        assertThrows(IllegalArgumentException.class, () -> LocalClientBridge.requiredString(request, "missing"));
        assertThrows(IllegalArgumentException.class, () -> LocalClientBridge.readRequest(channel("[]\n")));
        assertThrows(IllegalArgumentException.class, () -> LocalClientBridge.readRequest(channel("{}")));
        assertThrows(IllegalArgumentException.class, () -> LocalClientBridge.readRequest(channel(" ".repeat(2048) + "{}\n")));
    }

    private static java.nio.channels.ReadableByteChannel channel(String text) {
        return Channels.newChannel(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
}
