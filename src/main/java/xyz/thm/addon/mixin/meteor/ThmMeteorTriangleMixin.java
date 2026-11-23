/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.WMeteorTriangle;
import meteordevelopment.meteorclient.gui.utils.BaseWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.ThmChrome;
import xyz.thm.addon.gui.themes.ThmTheme;

/**
 * On the single-window settings/tab screens, the header's collapse arrow is hidden while ThmTheme
 * is active - the bare "_" glyph drawn by ThmMeteorHeaderMixin replaces it (clicking the header
 * still collapses, exactly like the arrow did). The clickgui's category-list windows keep their
 * normal arrow (they get no title-bar controls).
 */
@Mixin(value = WMeteorTriangle.class, remap = false)
public abstract class ThmMeteorTriangleMixin {
    @Inject(method = "onRender", at = @At("HEAD"), cancellable = true)
    private void thm$hideArrow(GuiRenderer renderer, double mouseX, double mouseY, double delta, CallbackInfo ci) {
        GuiTheme theme = ((BaseWidget) this).getTheme();
        if (theme instanceof ThmTheme && ThmChrome.settingsWindow()) ci.cancel();
    }
}
