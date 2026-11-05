/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

/** Bounds catch-up bursts without keeping a history beyond the pacing window. */
public final class TickPacer {
    private final long[] recent = new long[3];
    private int head, count;

    public void record(long now) {
        recent[head] = now;
        head = (head + 1) % recent.length;
        count = Math.min(count + 1, recent.length);
    }

    public int allow(int wanted, long now) {
        int inWindow = 0;
        for (int i = 0; i < count; i++) if (now - recent[i] >= 0 && now - recent[i] < 60_000_000L) inWindow++;
        return Math.max(0, Math.min(wanted, recent.length - inWindow));
    }

    public void clear() { head = count = 0; }
}
