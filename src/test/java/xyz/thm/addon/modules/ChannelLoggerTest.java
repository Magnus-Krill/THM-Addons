/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.VarInt;
import org.junit.jupiter.api.Test;
import xyz.thm.addon.utils.ChannelPayloadText;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChannelLoggerTest {
    @Test
    void customOnlyExcludesVanillaChannelsAndRespectsTheAllowlist() {
        Set<String> channels = Set.of("minecraft:brand", "example:*");
        var all = new ChannelLogger.Options(channels, false, true, true, true);
        var customOnly = new ChannelLogger.Options(channels, true, true, true, true);

        assertTrue(all.matches("minecraft:brand"));
        assertFalse(customOnly.matches("minecraft:brand"));
        assertTrue(customOnly.matches("example:unknown"));
        assertFalse(customOnly.matches("other:unknown"));
    }

    @Test
    void extractsExactConfigurationAndPlayPayloadWithoutChangingThePacketBuffer() {
        byte[] payload = {(byte) 0xff, 0, 1, (byte) 0x80};
        ByteBuf frame = Unpooled.buffer();
        try {
            frame.writeByte(99);
            int start = frame.writerIndex();
            VarInt.write(frame, 300);
            writeString(frame, "example:unknown");
            frame.writeBytes(payload);
            int end = frame.writerIndex();
            frame.writeByte(99);
            frame.readerIndex(end);

            for (ConnectionProtocol phase : new ConnectionProtocol[]{ConnectionProtocol.CONFIGURATION, ConnectionProtocol.PLAY}) {
                assertArrayEquals(payload, ByteBufUtil.getBytes(ChannelLogger.payloadBody(frame, start, end, phase, true)));
                assertArrayEquals(payload, ByteBufUtil.getBytes(ChannelLogger.payloadBody(frame, start, end, phase, false)));
                assertEquals(end, frame.readerIndex());
                assertEquals(end + 1, frame.writerIndex());
                assertEquals(1, frame.refCnt());
            }
        } finally {
            frame.release();
        }
    }

    @Test
    void skipsLoginQueryIdsAndTheResponsePresenceFlag() {
        ByteBuf frame = Unpooled.buffer();
        try {
            byte[] payload = {3, 2, 1};
            VarInt.write(frame, 4);
            VarInt.write(frame, 500);
            writeString(frame, "example:login");
            frame.writeBytes(payload);
            assertArrayEquals(payload, ByteBufUtil.getBytes(
                ChannelLogger.payloadBody(frame, 0, frame.writerIndex(), ConnectionProtocol.LOGIN, true)));

            frame.clear();
            VarInt.write(frame, 2);
            VarInt.write(frame, 500);
            frame.writeBoolean(false);
            assertFalse(ChannelLogger.payloadBody(frame, 0, frame.writerIndex(), ConnectionProtocol.LOGIN, false).isReadable());
            frame.setBoolean(frame.writerIndex() - 1, true);
            frame.writeBytes(payload);
            assertArrayEquals(payload, ByteBufUtil.getBytes(
                ChannelLogger.payloadBody(frame, 0, frame.writerIndex(), ConnectionProtocol.LOGIN, false)));
        } finally {
            frame.release();
        }
    }

    @Test
    void decodesBrandRegistrationAndCommonProtocolMessages() {
        assertEquals("Paper", ChannelPayloadText.decode("minecraft:brand", new byte[]{5, 'P', 'a', 'p', 'e', 'r'}));
        byte[] registration = "example:one\0example:two".getBytes(StandardCharsets.UTF_8);
        assertEquals("REGISTER: example:one, example:two", ChannelPayloadText.decode("minecraft:register", registration));
        assertEquals("UNREGISTER: example:one, example:two", ChannelPayloadText.decode("minecraft:unregister", registration));
        ByteBuf body = Unpooled.buffer();
        try {
            VarInt.write(body, 1);
            writeString(body, "configuration");
            VarInt.write(body, 1);
            writeString(body, "example:one");
            assertEquals("REGISTER (v1, configuration): example:one",
                ChannelPayloadText.decode("c:register", ByteBufUtil.getBytes(body)));
        } finally {
            body.release();
        }
        assertEquals("Protocol versions: 1, 300", ChannelPayloadText.decode("c:version", new byte[]{2, 1, (byte) 0xac, 2}));
    }

    @Test
    void distinguishesCompleteValuesFromUnknownBinaryAndRejectsMalformedUtf8() {
        assertEquals("Possible VarInt: 300", ChannelPayloadText.decode("example:test", new byte[]{(byte) 0xac, 2}));
        assertEquals("NBT: {x:42}", ChannelPayloadText.decode("example:test", new byte[]{10, 3, 0, 1, 'x', 0, 0, 0, 42, 0}));
        assertEquals("Unknown binary payload", ChannelPayloadText.decode("example:test", new byte[]{2, (byte) 0xc3, 0x28}));
        assertNull(ChannelPayloadText.utf8(new byte[]{(byte) 0xc3, 0x28}, 0, 2));
        assertEquals("line\\nnext", ChannelPayloadText.escape("line\nnext"));
    }

    @Test
    void recognizesCredentialChannelsAndPayloads() {
        assertTrue(ChannelPayloadText.sensitiveChannel("example:login/token"));
        assertTrue(ChannelPayloadText.sensitiveContent("{\"password\":\"example\"}", "", ""));
        assertTrue(ChannelPayloadText.sensitiveContent("prefix#configured-secret", "configured-secret", ""));
        assertFalse(ChannelPayloadText.sensitiveContent("Paper", "", ""));
        assertFalse(ChannelPayloadText.sensitiveChannel("minecraft:register"));
    }

    private static void writeString(ByteBuf buffer, String text) {
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        VarInt.write(buffer, utf8.length);
        buffer.writeBytes(utf8);
    }
}
