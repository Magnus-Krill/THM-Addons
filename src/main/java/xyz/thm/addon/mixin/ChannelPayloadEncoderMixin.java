/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import xyz.thm.addon.modules.ChannelLogger;

@Mixin(PacketEncoder.class)
public abstract class ChannelPayloadEncoderMixin {
    @Shadow @Final private ProtocolInfo<?> protocolInfo;

    @WrapOperation(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V"))
    private void thm$observePayload(StreamCodec<?, ?> codec, Object buffer, Object value, Operation<Void> original,
                                    @Local(argsOnly = true) ChannelHandlerContext context) {
        ByteBuf output = (ByteBuf) buffer;
        int start = output.writerIndex();
        original.call(codec, buffer, value);
        if (protocolInfo.flow() == PacketFlow.SERVERBOUND && value instanceof Packet<?> packet) {
            ChannelLogger.capture(context.channel(), protocolInfo.id(), packet, output, start, output.writerIndex());
        }
    }
}
