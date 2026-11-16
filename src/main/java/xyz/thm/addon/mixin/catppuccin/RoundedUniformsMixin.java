/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.catppuccin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import xyz.thm.addon.gui.ClickGuiAnimation;

@Pseudo
@Mixin(targets = "me.pindour.catppuccin.renderer.rounded.modern.RoundedUniforms", remap = false)
public abstract class RoundedUniformsMixin {
    @ModifyExpressionValue(method = "setColor", at = @At(value = "FIELD",
        target = "Lmeteordevelopment/meteorclient/utils/render/color/Color;a:I"), require = 0)
    private static int thm$fadeRoundedChrome(int alpha) {
        WidgetScreen screen = ClickGuiAnimation.transitioningScreen();
        return screen == null ? alpha : (int) Math.round(alpha * screen.animProgress);
    }
}
