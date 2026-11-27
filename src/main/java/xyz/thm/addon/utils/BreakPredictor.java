/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.utils;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks when to send placements so one lands in the first server tick after a block breaks.
 * Times are local nanoTime; {@code lead} is the round trip a sent packet needs to reach the server
 * relative to the delayed state we see.
 */
public final class BreakPredictor {
    static final long TICK = 50_000_000L;
    static final long LATE_MARGIN = 150_000_000L;
    static final long REBREAK_WINDOW = 300_000_000L;
    static final long STALE = 5_000_000_000L;

    private final Map<BlockPos, Entry> entries = new HashMap<>();

    private static final class Entry {
        int firstStage;
        long firstTime, start, end, lastSent = Long.MIN_VALUE / 2;
        boolean rebreak;
        Entry(int stage, long now) { firstStage = stage; firstTime = now; }
    }

    /** Records a broadcast mining stage; anything outside 0-9 means mining stopped. */
    public void stage(BlockPos pos, int stage, long now) {
        Entry entry = entries.get(pos);
        if (stage < 0 || stage > 9) {
            // The break effect arrives before the closing -1, so keep its rebreak window.
            if (entry != null && !entry.rebreak) entries.remove(pos);
            return;
        }
        if (entry == null || entry.rebreak || stage < entry.firstStage) {
            Entry fresh = new Entry(stage, now);
            if (entry != null) fresh.lastSent = entry.lastSent;
            entries.put(pos, fresh);
            return;
        }
        if (stage == entry.firstStage) return;
        double perStage = (now - entry.firstTime) / (double) (stage - entry.firstStage);
        // Stages go out as progress crosses each tenth; vanilla accepts STOP from 70% progress.
        entry.start = now + (long) ((7 - stage) * perStage);
        entry.end = now + (long) ((10 - stage) * perStage) + LATE_MARGIN;
    }

    /** Opens a window for an instant rebreak right after {@code pos} broke; the caller has just sent once. */
    public void broken(BlockPos pos, long now, long lead) {
        Entry entry = new Entry(0, now);
        entry.rebreak = true;
        entry.start = now;
        entry.end = now + lead + REBREAK_WINDOW;
        entry.lastSent = now;
        entries.put(pos, entry);
    }

    /** Positions to send one placement to this tick; marks them sent and drops finished windows. */
    public List<BlockPos> due(long now, long lead) {
        List<BlockPos> result = new ArrayList<>();
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            var mapped = iterator.next();
            Entry entry = mapped.getValue();
            if (entry.end == 0) {
                if (now - entry.firstTime > STALE) iterator.remove();
                continue;
            }
            long arrival = now + lead;
            if (arrival > entry.end) { iterator.remove(); continue; }
            // One early packet is harmless: the server rejects it while the block still stands.
            if (arrival + TICK < entry.start || now - entry.lastSent < TICK * 9 / 10) continue;
            entry.lastSent = now;
            result.add(mapped.getKey());
        }
        return result;
    }

    public boolean tracks(BlockPos pos) { return entries.containsKey(pos); }

    public void clear() { entries.clear(); }

    /** Round trip plus a manual offset, in nanoseconds. */
    public static long lead(int pingMs, int extraMs) {
        return Math.max(0, pingMs + extraMs) * 1_000_000L;
    }
}
