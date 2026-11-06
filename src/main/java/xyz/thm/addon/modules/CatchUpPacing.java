/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.DeltaTracker;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.DeltaTrackerTimerAccessor;
import xyz.thm.addon.utils.TickPacer;

public final class CatchUpPacing extends Module {
    private final TickPacer pacer = new TickPacer();

    public CatchUpPacing() { super(THMAddon.MAIN, "catch-up-pacing", "Limits catch-up bursts after client freezes."); }
    @Override public void onActivate() { pacer.clear(); }
    @Override public void onDeactivate() { pacer.clear(); }
    @EventHandler private void onLeave(GameLeftEvent event) { pacer.clear(); }
    @EventHandler private void onTick(TickEvent.Pre event) { if (mc.level != null) pacer.record(System.nanoTime()); }

    public static int limit(int wanted, DeltaTracker.Timer timer) {
        Modules modules = Modules.get();
        CatchUpPacing module = modules == null ? null : modules.get(CatchUpPacing.class);
        if (module == null || !module.isActive() || module.mc.level == null) return wanted;
        int requested = Math.min(10, wanted);
        int allowed = module.pacer.allow(requested, System.nanoTime());
        if (allowed < requested) {
            var access = (DeltaTrackerTimerAccessor) timer;
            access.thm$setResidual(access.thm$getResidual() + requested - allowed);
        }
        return allowed;
    }
}
