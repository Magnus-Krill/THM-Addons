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
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import xyz.thm.addon.modules.ChannelLogger;

@Mixin(PacketDecoder.class)
public abstract class ChannelPayloadDecoderMixin {
    @Shadow @Final private ProtocolInfo<?> protocolInfo;

    @WrapOperation(method = "decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V",
        at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object thm$observePayload(StreamCodec<?, ?> codec, Object buffer, Operation<Object> original,
                                      @Local(argsOnly = true) ChannelHandlerContext context) {
        ByteBuf input = (ByteBuf) buffer;
        int start = input.readerIndex();
        Object packet = original.call(codec, buffer);
        if (protocolInfo.flow() == PacketFlow.CLIENTBOUND && packet instanceof Packet<?> decoded) {
            ChannelLogger.capture(context.channel(), protocolInfo.id(), decoded, input, start, input.readerIndex());
        }
        return packet;
    }
}
