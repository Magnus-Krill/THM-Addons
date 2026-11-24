/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.mixin.meteor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.renderer.Texture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xyz.thm.addon.utils.MeteorTextureImages;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@Mixin(value = Texture.class, remap = false)
abstract class TexturePngMixin {
    @WrapOperation(method = "readResource", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/platform/TextureUtil;readResource(Ljava/io/InputStream;)Ljava/nio/ByteBuffer;"))
    private static ByteBuffer thm$readPng(InputStream input, Operation<ByteBuffer> original) throws IOException {
        return MeteorTextureImages.read(input, false);
    }

    @WrapOperation(method = "readResource", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/stb/STBImage;stbi_load_from_memory(Ljava/nio/ByteBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;Ljava/nio/IntBuffer;I)Ljava/nio/ByteBuffer;"))
    private static ByteBuffer thm$decodeImage(ByteBuffer data, IntBuffer width, IntBuffer height, IntBuffer components,
                                              int channels, Operation<ByteBuffer> original) throws IOException {
        return MeteorTextureImages.decode(data, width, height, components, channels, original);
    }
}
