/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.utils;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.PngInfo;
import org.lwjgl.stb.STBImage;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class MeteorTextureImages {
    static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    static final int MAX_DIMENSION = 8192;
    static final int MAX_PIXELS = 16 * 1024 * 1024;

    private MeteorTextureImages() {}

    public static ByteBuffer read(InputStream input, boolean jpeg) throws IOException {
        byte[] data = input.readNBytes(MAX_FILE_BYTES + 1);
        if (data.length > MAX_FILE_BYTES) throw new IOException("Meteor texture exceeds the file size limit");

        if (jpeg) {
            checkJpegDimensions(data);
        } else {
            // Use the same PNG header rules as Meteor's upstream fix (#6821).
            PngInfo png = PngInfo.fromBytes(data);
            checkDimensions(png.width(), png.height());
        }

        return ByteBuffer.allocateDirect(data.length).put(data).flip();
    }

    private static void checkJpegDimensions(byte[] data) throws IOException {
        if (data.length < 3 || (data[0] & 0xff) != 0xff || (data[1] & 0xff) != 0xd8 || (data[2] & 0xff) != 0xff) {
            throw new IOException("Expected a JPEG texture");
        }

        var reader = ImageIO.getImageReadersByFormatName("JPEG").next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            reader.setInput(input, true, true);
            checkDimensions(reader.getWidth(0), reader.getHeight(0));
        } finally {
            reader.dispose();
        }
    }

    static void checkDimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
            || (long) width * height > MAX_PIXELS) {
            throw new IOException("Meteor texture dimensions exceed the limit");
        }
    }

    public static ByteBuffer decode(ByteBuffer data, IntBuffer width, IntBuffer height, IntBuffer components,
                                    int channels, Operation<ByteBuffer> original) throws IOException {
        try {
            ByteBuffer pixels = original.call(data, width, height, components, channels);
            if (pixels == null) throw new IOException("Could not decode Meteor texture");
            return pixels;
        } finally {
            STBImage.stbi_set_flip_vertically_on_load(false);
        }
    }
}
