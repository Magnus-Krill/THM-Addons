/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.settings.GuiSoundSettings;
import xyz.thm.addon.system.THMSystem;

/** Expand and collapse sounds for category windows and setting sections. */
@Mixin(value = {WWindow.class, WSection.class}, remap = false)
public abstract class WSectionSoundMixin {
    @Shadow protected boolean expanded;

    @Inject(method = "setExpanded", at = @At("HEAD"))
    private void thm$expandSound(boolean expanded, CallbackInfo ci) {
        THMSystem system = THMSystem.get();
        if (expanded == this.expanded || system == null || !GuiSoundSettings.guiOpen()) return;
        (expanded ? system.guiSounds.expand : system.guiSounds.collapse).play();
    }
}
