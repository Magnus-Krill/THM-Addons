/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.util.Util;

import java.util.Arrays;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Raw network round trip from ping requests. The server answers them on its network thread,
 * so samples skip both tick waits and the tab list's 30-second smoothed latency.
 */
public final class PingTracker {
    private static final PingTracker INSTANCE = new PingTracker();
    private static final int SAMPLES = 5;
    private static final long INTERVAL_MS = 500, STALE_MS = 10_000;

    private final long[] samples = new long[SAMPLES];
    private int count, next;
    private long lastRequest, lastSample;

    private PingTracker() {}

    public static void init() {
        MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    /** Median of recent round trips in ms, falling back to the tab list. Call each tick while timing matters. */
    public static int rtt() {
        return INSTANCE.current();
    }

    private synchronized int current() {
        long now = Util.getMillis();
        var connection = mc.getConnection();
        if (connection == null) return 0;
        if (now - lastRequest >= INTERVAL_MS) {
            lastRequest = now;
            connection.send(new ServerboundPingRequestPacket(now));
        }
        if (count > 0 && now - lastSample < STALE_MS) return (int) median(Arrays.copyOf(samples, count));
        var info = mc.player == null ? null : connection.getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    static long median(long[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    // Runs on the network thread, before the client queues the packet behind its tick.
    @EventHandler(priority = EventPriority.HIGHEST)
    private synchronized void onReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundPongResponsePacket pong)) return;
        long now = Util.getMillis(), rtt = now - pong.time();
        // F3's network chart sends its own requests with the same clock, so those samples count too.
        if (rtt < 0 || rtt > STALE_MS) return;
        samples[next] = rtt;
        next = (next + 1) % SAMPLES;
        count = Math.min(count + 1, SAMPLES);
        lastSample = now;
    }

    @EventHandler
    private synchronized void onGameLeft(GameLeftEvent event) {
        count = 0;
        next = 0;
        lastSample = 0;
    }
}
