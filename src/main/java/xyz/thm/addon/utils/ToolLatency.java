/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import java.util.ArrayDeque;

/** Recent server-held tool efficiency, bounded independently of connection age. */
public final class ToolLatency {
    private record Sample(long at, int efficiency) {}
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();

    public void record(long now, int efficiency, long window) {
        while (samples.size() > 1 && now - samples.peekFirst().at > window) samples.removeFirst();
        while (samples.size() >= 64) samples.removeFirst();
        samples.addLast(new Sample(now, efficiency));
    }

    public int minimum(long now, long window, int current) {
        for (Sample sample : samples) if (now >= sample.at && now - sample.at <= window) current = Math.min(current, sample.efficiency);
        return current;
    }

    public void clear() { samples.clear(); }
}
