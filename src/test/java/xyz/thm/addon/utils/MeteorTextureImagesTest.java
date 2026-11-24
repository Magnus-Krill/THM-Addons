/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.utils;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeteorTextureImagesTest {
    @Test
    void readsPngAndJpegAndRequiresTheExpectedFormat() throws IOException {
        byte[] png = imageBytes("png", 1);
        byte[] jpeg = imageBytes("jpg", 1);
        assertArrayEquals(png, readBytes(png, false));
        assertArrayEquals(jpeg, readBytes(jpeg, true));
        assertThrows(IOException.class, () -> readBytes(png, true));
        assertThrows(IOException.class, () -> readBytes(jpeg, false));
    }

    @Test
    void boundsImageBytesAndDimensions() throws IOException {
        var oversized = new ByteArrayInputStream(new byte[MeteorTextureImages.MAX_FILE_BYTES + 2]);
        assertThrows(IOException.class, () -> MeteorTextureImages.read(oversized, false));
        assertEquals(1, oversized.available());

        byte[] widePng = imageBytes("png", 8193);
        byte[] wideJpeg = imageBytes("jpg", 8193);
        assertThrows(IOException.class, () -> readBytes(widePng, false));
        assertThrows(IOException.class, () -> readBytes(wideJpeg, true));
        assertDoesNotThrow(() -> MeteorTextureImages.checkDimensions(4096, 4096));
        assertThrows(IOException.class, () -> MeteorTextureImages.checkDimensions(8192, 8192));
        assertThrows(IOException.class, () -> MeteorTextureImages.checkDimensions(0, 1));
    }

    @Test
    void convertsDecoderFailureToAnIoException() throws IOException {
        ByteBuffer input = ByteBuffer.allocate(1);
        IntBuffer dimensions = IntBuffer.allocate(1);
        ByteBuffer pixels = ByteBuffer.allocate(4);
        assertSame(pixels, MeteorTextureImages.decode(input, dimensions, dimensions, dimensions, 4, args -> pixels));
        assertThrows(IOException.class,
            () -> MeteorTextureImages.decode(input, dimensions, dimensions, dimensions, 4, args -> null));
    }

    private static byte[] readBytes(byte[] data, boolean jpeg) throws IOException {
        ByteBuffer buffer = MeteorTextureImages.read(new ByteArrayInputStream(data), jpeg);
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    private static byte[] imageBytes(String format, int width) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, 1, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }
}
