/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.Stack;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.Scissor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import xyz.thm.addon.gui.ClickGuiAnimation;

@Mixin(value = GuiRenderer.class, remap = false)
public abstract class GuiRendererAnimationMixin {
    @Shadow @Final private Stack<Scissor> scissorStack;

    @WrapMethod(method = "scissorStart")
    private void thm$clipTransitionScissor(double x, double y, double width, double height, Operation<Void> original) {
        if (!scissorStack.isEmpty() && ClickGuiAnimation.transitioningScreen() != null) {
            Scissor parent = scissorStack.top();
            double left = Math.clamp(x, parent.x, parent.x + parent.width);
            double top = Math.clamp(y, parent.y, parent.y + parent.height);
            double right = Math.clamp(x + width, left, parent.x + parent.width);
            double bottom = Math.clamp(y + height, top, parent.y + parent.height);
            original.call(left, top, right - left, bottom - top);
        } else {
            original.call(x, y, width, height);
        }
    }

    @WrapMethod(method = "item")
    private void thm$fadeItem(ItemStack stack, int x, int y, float scale, boolean overlay, Operation<Void> original) {
        WidgetScreen screen = ClickGuiAnimation.transitioningScreen();
        if (screen == null) {
            original.call(stack, x, y, scale, overlay);
            return;
        }

        int previous = ClickGuiAnimation.itemTint;
        ClickGuiAnimation.itemTint = (int) Math.round(255 * screen.animProgress) * 0x01010101;
        try {
            original.call(stack, x, y, scale, overlay);
        } finally {
            ClickGuiAnimation.itemTint = previous;
        }
    }
}
