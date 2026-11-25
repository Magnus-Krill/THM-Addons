/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.utils;

import xyz.thm.addon.settings.PacketPlacementSettings.Mode;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

final class PlacementPacer {
    private static final long WINDOW_NANOS = 310_000_000L;
    static final int LIMIT = 9;

    private long startedAt;
    private int sent;
    private int inFlight;

    boolean send(Mode mode, LongSupplier clock, BooleanSupplier dispatch) {
        if (mode != Mode.Pace) return dispatch.getAsBoolean();
        if (remaining(clock.getAsLong()) == 0) return false;
        inFlight++;
        try {
            if (!dispatch.getAsBoolean()) return false;
            recordSend(clock.getAsLong());
            return true;
        } finally {
            inFlight--;
        }
    }

    int remaining(long now) {
        if (sent != 0 && now - startedAt >= WINDOW_NANOS) reset();
        return LIMIT - sent - inFlight;
    }

    private void recordSend(long now) {
        if (sent == 0 || now - startedAt >= WINDOW_NANOS) {
            startedAt = now;
            sent = 0;
        }
        sent++;
    }

    void reset() {
        sent = 0;
    }
}
