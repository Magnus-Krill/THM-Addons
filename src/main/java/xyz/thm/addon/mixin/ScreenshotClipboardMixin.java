/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.Screenshot;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFWNativeWin32;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.THMUtils;
import xyz.thm.addon.utils.WindowsClipboard;
import org.lwjgl.system.Platform;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
public class ScreenshotClipboardMixin {

    // Linux can use AWT if its headless state has not already been cached.
    static {
        if (Platform.get() == Platform.LINUX) System.setProperty("java.awt.headless", "false");
    }

    // InputStream representation so native apps (Discord, browsers) receive raw PNG bytes.
    private static final DataFlavor PNG_FLAVOR = new DataFlavor("image/png", "PNG Image");

    // Inject at TAIL so: (1) the file is fully written, (2) the "Saved screenshot" message
    // has already been sent — our clipboard message always appears after it.
    @Inject(method = "lambda$grab$3", at = @At("TAIL"))
    private static void onSaveScreenshotFile(NativeImage image, File file, Consumer<Component> messageReceiver, CallbackInfo ci) {
        if (!THMSystem.get().screenshotToClipboard.get()) return;

        THMUtils.async("screenshot-clipboard", () -> {
            try {
                if (Platform.get() == Platform.WINDOWS) {
                    WindowsClipboard.copyImage(file, GLFWNativeWin32.glfwGetWin32Window(Minecraft.getInstance().getWindow().handle()));
                } else if (Platform.get() == Platform.MACOSX) {
                    copyToClipboardMac(file);
                } else {
                    try {
                        copyToClipboardAWT(file);
                    } catch (HeadlessException | AWTError e) {
                        if (Platform.get() != Platform.LINUX) throw e;
                        try {
                            copyToClipboardNative(file);
                        } catch (Exception fallback) {
                            fallback.addSuppressed(e);
                            throw fallback;
                        }
                    }
                }
                ChatUtils.info("Successfully copied screenshot to clipboard");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                THMAddon.LOG.warn("[THM] Screenshot clipboard copy interrupted", e);
            } catch (Exception | AWTError | UnsatisfiedLinkError e) {
                THMAddon.LOG.warn("[THM] Failed to copy screenshot to clipboard", e);
            }
        });
    }

    // macOS: use osascript to write PNG bytes directly into NSPasteboard.
    // Java AWT clipboard does not reliably bridge to NSPasteboard for images on macOS.
    private static void copyToClipboardMac(File file) throws IOException, InterruptedException {
        String script = "set the clipboard to (read (POSIX file \"" + file.getAbsolutePath() + "\") as «class PNGf»)";
        Process proc = Runtime.getRuntime().exec(new String[]{"osascript", "-e", script});
        int exit = proc.waitFor();
        if (exit != 0) {
            throw new IOException("osascript exited with code " + exit);
        }
    }

    // Linux needs native tools when AWT's headless state was already cached.
    private static void copyToClipboardNative(File file) throws IOException, InterruptedException {
        IOException last = new IOException("no clipboard tool found (install wl-clipboard or xclip)");

        for (String[] cmd : new String[][]{
            {"wl-copy", "--type", "image/png"},
            {"xclip", "-selection", "clipboard", "-t", "image/png"}
        }) {
            try {
                Process proc = new ProcessBuilder(cmd).redirectInput(file).start();
                if (proc.waitFor() == 0) return;
                last = new IOException(cmd[0] + " exited with code " + proc.exitValue());
            } catch (IOException e) {
                last = e;
            }
        }

        throw last;
    }

    private static void copyToClipboardAWT(File file) throws IOException {
        BufferedImage buffered = ImageIO.read(file);
        if (buffered == null) {
            throw new IOException("Could not read screenshot file for clipboard");
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(buffered), null);
    }

    private record TransferableImage(BufferedImage image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{PNG_FLAVOR, DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return PNG_FLAVOR.equals(flavor) || DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (DataFlavor.imageFlavor.equals(flavor)) return image;
            if (PNG_FLAVOR.equals(flavor)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                return new ByteArrayInputStream(out.toByteArray());
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
