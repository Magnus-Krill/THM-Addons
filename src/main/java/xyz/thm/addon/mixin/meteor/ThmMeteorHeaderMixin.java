/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.utils.BaseWidget;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.ThmChrome;
import xyz.thm.addon.gui.themes.ThmTheme;

/**
 * Replaces the Meteor window header's flat accent-colored bar with the THM main-menu window's
 * horizontal title-bar gradient (TITLEBAR_LEFT -> TITLEBAR_RIGHT), while ThmTheme is active. Inset
 * by the border thickness so the window border (drawn by ThmMeteorWindowMixin) stays visible
 * around it. On the single-window settings/tab screens it also draws the bare "_"/"x" title-bar
 * controls (see ThmChrome) - matching the main-menu window. Targets WMeteorWindow's private
 * WMeteorHeader inner class.
 */
@Mixin(targets = "meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorWindow$WMeteorHeader", remap = false)
public abstract class ThmMeteorHeaderMixin {
    @Inject(method = "onRender", at = @At("HEAD"), cancellable = true)
    private void thm$titleBar(GuiRenderer renderer, double mouseX, double mouseY, double delta, CallbackInfo ci) {
        GuiTheme theme = ((BaseWidget) this).getTheme();
        if (!(theme instanceof ThmTheme)) return;

        MeteorGuiTheme meteor = (MeteorGuiTheme) theme;
        WWidget self = (WWidget) (Object) this;
        double s = meteor.scale(2);
        renderer.quad(self.x + s, self.y + s, self.width - s * 2, self.height - s, ThmChrome.titlebarLeft(), ThmChrome.titlebarRight());

        if (ThmChrome.settingsWindow()) ThmChrome.headerGlyphs(renderer, self, meteor);
        ci.cancel();
    }
}
