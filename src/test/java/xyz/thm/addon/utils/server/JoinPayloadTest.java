/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.server;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JoinPayloadTest {
    @Test
    void vanillaCodecWritesOnlyTheAnarchyJoinChannelWithoutRegistration() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = JoinPayload.createPacket();
            assertInstanceOf(DiscardedPayload.class, packet.payload());
            ServerboundCustomPayloadPacket.STREAM_CODEC.encode(buf, packet);
            assertEquals("anarchymod:join", buf.readIdentifier().toString());
            assertEquals(0, buf.readableBytes(), "The join payload must have an empty body.");

            buf.readerIndex(0);
            var decoded = ServerboundCustomPayloadPacket.STREAM_CODEC.decode(buf);
            assertEquals(packet.payload().type(), decoded.payload().type());
            assertEquals(0, buf.readableBytes());
        } finally {
            buf.release();
        }
    }
}
