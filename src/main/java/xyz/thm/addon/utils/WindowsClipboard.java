/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD.SIZE_T;
import com.sun.jna.win32.StdCallLibrary;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WindowsClipboard {
    private WindowsClipboard() {}

    public static void copyImage(File file, long window) throws IOException, InterruptedException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) throw new IOException("Could not read screenshot file for clipboard");
        copy(encodeDib(image), window, Native.load("user32", User32.class), Native.load("kernel32", Kernel32.class));
    }

    static byte[] encodeDib(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int stride = Math.addExact(Math.multiplyExact(width, 3), 3) & ~3;
        int size = Math.multiplyExact(stride, height);
        ByteBuffer dib = ByteBuffer.allocate(Math.addExact(40, size)).order(ByteOrder.LITTLE_ENDIAN);
        dib.putInt(40).putInt(width).putInt(height).putShort((short) 1).putShort((short) 24);
        dib.putInt(0).putInt(size);
        int[] row = new int[width];
        // CF_DIB uses bottom-up BGR rows padded to four bytes, without a BMP file header.
        for (int y = height - 1; y >= 0; y--) {
            image.getRGB(0, y, width, 1, row, 0, width);
            dib.position(40 + (height - 1 - y) * stride);
            for (int rgb : row) dib.put((byte) rgb).put((byte) (rgb >>> 8)).put((byte) (rgb >>> 16));
        }
        return dib.array();
    }

    static synchronized void copy(byte[] dib, long window, User32 user, Kernel32 kernel) throws IOException, InterruptedException {
        if (window == 0) throw new IOException("Screenshot clipboard needs a game window");
        Pointer memory = kernel.GlobalAlloc(0x0002, new SIZE_T(dib.length)); // GMEM_MOVEABLE
        if (memory == null) throw failure("GlobalAlloc");
        boolean opened = false;
        try {
            Pointer data = kernel.GlobalLock(memory);
            if (data == null) throw failure("GlobalLock");
            try {
                data.write(0, dib, 0, dib.length);
            } finally {
                kernel.GlobalUnlock(memory);
            }
            for (int attempt = 0; attempt < 10; attempt++) {
                opened = user.OpenClipboard(new Pointer(window));
                if (opened) break;
                if (attempt == 9) throw failure("OpenClipboard");
                Thread.sleep(100);
            }
            if (!user.EmptyClipboard()) throw failure("EmptyClipboard");
            if (user.SetClipboardData(8, memory) == null) throw failure("SetClipboardData"); // CF_DIB
            memory = null; // Windows owns the allocation after SetClipboardData succeeds.
        } finally {
            if (opened) user.CloseClipboard();
            if (memory != null) kernel.GlobalFree(memory);
        }
    }

    private static IOException failure(String operation) {
        return new IOException(operation + " failed copying screenshot (Windows error " + Native.getLastError() + ")");
    }

    public interface User32 extends StdCallLibrary {
        boolean OpenClipboard(Pointer window);
        boolean EmptyClipboard();
        Pointer SetClipboardData(int format, Pointer memory);
        boolean CloseClipboard();
    }

    public interface Kernel32 extends StdCallLibrary {
        Pointer GlobalAlloc(int flags, SIZE_T bytes);
        Pointer GlobalLock(Pointer memory);
        boolean GlobalUnlock(Pointer memory);
        Pointer GlobalFree(Pointer memory);
    }
}
