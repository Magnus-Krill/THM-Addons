/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.ChatHudAccessor;

import java.util.*;
import java.util.function.BooleanSupplier;

public final class StatsScreenshotChatGuard {
    private static final int MAX_BUFFERED_MESSAGES = 1_000;
    private static final long CAPTURE_WATCHDOG_MS = 5_000L;

    private static volatile StatsScreenshotChatGuard INSTANCE;

    private final LinkedHashMap<Long, Long> captureDeadlines = new LinkedHashMap<>();
    private final ArrayDeque<BufferedMessage> bufferedMessages = new ArrayDeque<>();
    private final ArrayDeque<PendingScreenshot> pendingScreenshots = new ArrayDeque<>();

    private long nextCaptureToken = 1L;
    private int droppedMessages;

    private StatsScreenshotChatGuard() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static StatsScreenshotChatGuard getInstance() {
        if (INSTANCE == null) {
            synchronized (StatsScreenshotChatGuard.class) {
                if (INSTANCE == null) INSTANCE = new StatsScreenshotChatGuard();
            }
        }
        return INSTANCE;
    }

    public long beginCapture() {
        long token = nextCaptureToken++;
        if (nextCaptureToken <= 0L) nextCaptureToken = 1L;
        captureDeadlines.put(token, System.currentTimeMillis() + CAPTURE_WATCHDOG_MS);
        return token;
    }

    public void finishCapture(long token) {
        if (captureDeadlines.remove(token) == null) return;
        if (captureDeadlines.isEmpty()) replayBufferedMessages();
    }

    public void captureAfterRender(long token, boolean disconnect, BooleanSupplier ready, Runnable capture, Runnable abort) {
        if (pendingScreenshots.size() >= 16) {
            abort.run();
            return;
        }
        long deadline = System.currentTimeMillis() + (disconnect ? 30_000L : CAPTURE_WATCHDOG_MS);
        if (captureDeadlines.containsKey(token)) captureDeadlines.put(token, deadline);
        pendingScreenshots.addLast(new PendingScreenshot(disconnect, deadline, ready, capture, abort));
    }

    public static void afterGuiRender() {
        StatsScreenshotChatGuard guard = INSTANCE;
        if (guard == null) return;
        long now = System.currentTimeMillis();
        for (int count = guard.pendingScreenshots.size(); count > 0; count--) {
            PendingScreenshot pending = guard.pendingScreenshots.pollFirst();
            if (pending == null) break;
            if (!pending.afterRender(now)) guard.pendingScreenshots.addLast(pending);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (captureDeadlines.isEmpty() || event.isCancelled()) return;
        if (isLocalAddonOutput(event.getMessage())) return;

        Component message = event.getMessage();
        if (message != null && bufferedMessages.size() < MAX_BUFFERED_MESSAGES) {
            bufferedMessages.addLast(new BufferedMessage(message.copy(), event.getIndicator()));
        } else {
            droppedMessages++;
        }
        event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        long now = System.currentTimeMillis();
        pendingScreenshots.removeIf(pending -> pending.expire(now));
        if (captureDeadlines.isEmpty()) return;

        boolean expired = false;
        Iterator<Map.Entry<Long, Long>> iterator = captureDeadlines.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) {
                iterator.remove();
                expired = true;
            }
        }

        if (expired) THMAddon.LOG.warn("Stats screenshot chat guard watchdog released an expired capture.");
        if (captureDeadlines.isEmpty()) replayBufferedMessages();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        pendingScreenshots.removeIf(pending -> {
            if (pending.disconnect) return false;
            pending.cancel();
            return true;
        });
        captureDeadlines.clear();
        bufferedMessages.clear();
        droppedMessages = 0;
    }

    private void replayBufferedMessages() {
        Minecraft mc = Minecraft.getInstance();
        ChatComponent chatHud = mc.gui == null ? null : mc.gui.hud.getChat();

        if (chatHud != null) {
            int creationTick = mc.gui.hud.getGuiTicks();
            while (!bufferedMessages.isEmpty()) {
                BufferedMessage buffered = bufferedMessages.removeFirst();
                GuiMessage line = new GuiMessage(
                    creationTick,
                    buffered.message(),
                    null,
                    net.minecraft.client.multiplayer.chat.GuiMessageSource.SYSTEM_SERVER,
                    buffered.indicator()
                );
                ((ChatHudAccessor) chatHud).thm$addVisibleMessage(line);
                ((ChatHudAccessor) chatHud).thm$addMessage(line);
            }
        } else {
            bufferedMessages.clear();
        }

        int dropped = droppedMessages;
        droppedMessages = 0;
        if (dropped > 0) {
            ChatUtils.warningPrefix("THM", "Stats screenshot chat guard dropped %d excess server messages.", dropped);
        }
    }

    /** True for chat lines this addon printed itself — those aren't server output, so they don't need buffering. */
    private static boolean isLocalAddonOutput(Component message) {
        if (message == null) return false;

        String trimmed = message.getString().stripLeading();
        if (trimmed.isEmpty()) return false;

        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("[meteor]")
            || lower.startsWith("[thm]")
            || lower.startsWith("[kitbot frontend]")
            || lower.startsWith("[thm highwaybuilder]")
            || lower.startsWith("[thm hwy monitor]")
            || lower.startsWith("[obsidian farmer thm]")
            || lower.startsWith("[elytra route]");
    }

    private record BufferedMessage(Component message, GuiMessageTag indicator) {
    }

    static final class PendingScreenshot {
        private final boolean disconnect;
        private final long deadline;
        private final BooleanSupplier ready;
        private final Runnable capture, abort;
        private int renderedFrames;
        private boolean done;

        PendingScreenshot(boolean disconnect, long deadline, BooleanSupplier ready, Runnable capture, Runnable abort) {
            this.disconnect = disconnect;
            this.deadline = deadline;
            this.ready = ready;
            this.capture = capture;
            this.abort = abort;
        }

        boolean afterRender(long now) {
            if (expire(now)) return true;
            if (!ready.getAsBoolean()) {
                renderedFrames = 0;
                return false;
            }
            // The first frame may have extracted its GUI before the stats were queued.
            if (++renderedFrames < 2) return false;
            done = true;
            capture.run();
            return true;
        }

        boolean expire(long now) {
            if (!done && now >= deadline) cancel();
            return done;
        }

        void cancel() {
            if (done) return;
            done = true;
            abort.run();
        }
    }
}
