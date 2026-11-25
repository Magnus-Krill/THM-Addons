/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.mixin.accessor;

import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = WSection.class, remap = false)
public interface WSectionAccessor {
    @Accessor("title")        String thm$getTitle();
    @Accessor("forcedHeight") void thm$setForcedHeight(double value);
    @Accessor("firstTime")    void thm$setFirstTime(boolean value);
    @Accessor("animProgress") double thm$getAnimProgress();
    @Accessor("animProgress") void thm$setAnimProgress(double value);
}
