/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.mixin.meteor;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.Stack;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.Scissor;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import xyz.thm.addon.gui.ClickGuiAnimation;
import xyz.thm.addon.gui.themes.LiquidGlassTheme;

@Mixin(value = GuiRenderer.class, remap = false)
public abstract class GuiRendererAnimationMixin {
    @Shadow @Final private Stack<Scissor> scissorStack;
    @Shadow public GuiTheme theme;

    @Unique private static final Color thm$lightTextOutline = new Color(255, 255, 255);
    @Unique private static final Color thm$darkTextOutline = new Color(0, 0, 0);

    @WrapMethod(method = "text")
    private void thm$outlineGlassText(String text, double x, double y, Color color, boolean title, Operation<Void> original) {
        if (theme instanceof LiquidGlassTheme && color.a == 255 && !text.isEmpty()) {
            double brightness = 0.2126 * color.r * color.r + 0.7152 * color.g * color.g + 0.0722 * color.b * color.b;
            Color outline = brightness < 115 * 115 ? thm$lightTextOutline : thm$darkTextOutline;
            original.call(text, x - 1, y, outline, title);
            original.call(text, x + 1, y, outline, title);
            original.call(text, x, y - 1, outline, title);
            original.call(text, x, y + 1, outline, title);
        }
        original.call(text, x, y, color, title);
    }

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
