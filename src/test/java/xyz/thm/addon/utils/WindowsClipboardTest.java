/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD.SIZE_T;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class WindowsClipboardTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void dibPreservesColorsOrientationAndRowPadding(int width) throws Exception {
        BufferedImage original = new BufferedImage(width, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < width; x++) original.setRGB(x, y, 0x123456 + x * 0x201103 + y * 0x351921);
        }
        byte[] dib = WindowsClipboard.encodeDib(original);
        ByteBuffer bmp = ByteBuffer.allocate(14 + dib.length).order(ByteOrder.LITTLE_ENDIAN);
        bmp.put((byte) 'B').put((byte) 'M').putInt(bmp.capacity()).putInt(0).putInt(54).put(dib);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bmp.array()));
        assertNotNull(decoded);
        assertEquals(width, decoded.getWidth());
        assertEquals(2, decoded.getHeight());
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < width; x++) assertEquals(original.getRGB(x, y), decoded.getRGB(x, y));
        }
    }

    @Test
    void retriesBusyClipboardAndTransfersMemoryOwnership() throws Exception {
        try (FakeWindows api = new FakeWindows()) {
            api.busyAttempts = 2;
            byte[] dib = {1, 2, 3, 4};
            WindowsClipboard.copy(dib, 123, api, api);
            assertEquals(3, api.openAttempts);
            assertEquals(123, api.owner);
            assertEquals(0x0002, api.allocationFlags);
            assertEquals(8, api.format);
            assertArrayEquals(dib, api.memory.getByteArray(0, dib.length));
            assertEquals(1, api.unlocks);
            assertEquals(1, api.closes);
            assertEquals(0, api.frees);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GlobalAlloc", "GlobalLock", "OpenClipboard", "EmptyClipboard", "SetClipboardData"})
    void failedNativeCallsReleaseOnlyOwnedResources(String operation) throws Exception {
        try (FakeWindows api = new FakeWindows()) {
            api.failure = operation;
            IOException error = assertThrows(IOException.class, () -> WindowsClipboard.copy(new byte[4], 123, api, api));
            assertTrue(error.getMessage().startsWith(operation + " failed"));
            assertEquals(operation.equals("GlobalAlloc") ? 0 : 1, api.frees);
            assertEquals(operation.equals("EmptyClipboard") || operation.equals("SetClipboardData") ? 1 : 0, api.closes);
            if (operation.equals("OpenClipboard")) assertEquals(10, api.openAttempts);
        }
    }

    @Test
    void interruptedRetryFreesAllocation() throws Exception {
        try (FakeWindows api = new FakeWindows()) {
            api.busyAttempts = 1;
            Thread.currentThread().interrupt();
            try {
                assertThrows(InterruptedException.class, () -> WindowsClipboard.copy(new byte[4], 123, api, api));
            } finally {
                Thread.interrupted();
            }
            assertEquals(1, api.frees);
            assertEquals(0, api.closes);
        }
    }

    @Test
    void missingWindowDoesNotTouchClipboard() throws Exception {
        try (FakeWindows api = new FakeWindows()) {
            assertThrows(IOException.class, () -> WindowsClipboard.copy(new byte[4], 0, api, api));
            assertNull(api.memory);
            assertEquals(0, api.openAttempts);
        }
    }

    private static class FakeWindows implements WindowsClipboard.User32, WindowsClipboard.Kernel32, AutoCloseable {
        Memory memory;
        String failure = "";
        int busyAttempts, openAttempts, unlocks, closes, frees, allocationFlags, format;
        long owner;

        public Pointer GlobalAlloc(int flags, SIZE_T bytes) {
            allocationFlags = flags;
            if (failure.equals("GlobalAlloc")) return null;
            return memory = new Memory(bytes.longValue());
        }

        public Pointer GlobalLock(Pointer handle) {
            return failure.equals("GlobalLock") ? null : handle;
        }

        public boolean GlobalUnlock(Pointer handle) {
            unlocks++;
            return false; // The final unlock succeeds with a zero return value.
        }

        public Pointer GlobalFree(Pointer handle) {
            frees++;
            return null;
        }

        public boolean OpenClipboard(Pointer window) {
            owner = Pointer.nativeValue(window);
            openAttempts++;
            return !failure.equals("OpenClipboard") && openAttempts > busyAttempts;
        }

        public boolean EmptyClipboard() {
            return !failure.equals("EmptyClipboard");
        }

        public Pointer SetClipboardData(int clipboardFormat, Pointer handle) {
            format = clipboardFormat;
            return failure.equals("SetClipboardData") ? null : handle;
        }

        public boolean CloseClipboard() {
            closes++;
            return true;
        }

        public void close() {
            if (memory != null) memory.close();
        }
    }
}
