/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.accessor;

public interface StuckEatingRetryBridge {
    boolean thm$isActivelyEating();

    boolean thm$stillNeedsToEat();

    boolean thm$hasValidCurrentEatingItem();

    long thm$beginWatchdogRecovery();

    void thm$endWatchdogRecovery(long token);

    void thm$forceStopEating(long token);

    StuckEatingRetryResult thm$forceRestartEating(long token);
}
