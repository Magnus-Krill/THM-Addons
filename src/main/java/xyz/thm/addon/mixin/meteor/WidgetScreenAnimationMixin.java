/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.ClickGuiAnimation;
import xyz.thm.addon.system.THMSystem;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(value = WidgetScreen.class, remap = false)
public abstract class WidgetScreenAnimationMixin implements ClickGuiAnimation.ScatterSeed {
    @Shadow public double animProgress;
    @Shadow private boolean closing;
    @Unique private int thm$scatterSeed;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void thm$animateFromVanillaScreen(GuiTheme theme, String title, CallbackInfo ci) {
        ClickGuiAnimation animation = ClickGuiAnimation.forScreen((WidgetScreen) (Object) this);
        if (animation == ClickGuiAnimation.Scatter) thm$scatterSeed = ThreadLocalRandom.current().nextInt();
        if (animation != ClickGuiAnimation.Default && !(Minecraft.getInstance().gui.screen() instanceof WidgetScreen)) {
            animProgress = 0;
        }
    }

    @Inject(method = "onClose", at = @At(value = "FIELD",
        target = "Lmeteordevelopment/meteorclient/gui/WidgetScreen;closing:Z", opcode = Opcodes.PUTFIELD))
    private void thm$scatterOnClose(CallbackInfo ci) {
        // An interrupted opening reverses its current paths.
        if (!closing && animProgress == 1 && ClickGuiAnimation.forScreen((WidgetScreen) (Object) this) == ClickGuiAnimation.Scatter) {
            thm$scatterSeed += ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        }
    }

    @Override
    public int thm$scatterSeed() {
        return thm$scatterSeed;
    }

    @ModifyConstant(method = "renderCustom", constant = @Constant(floatValue = 14))
    private float thm$animationSpeed(float speed) {
        ClickGuiAnimation animation = ClickGuiAnimation.forScreen((WidgetScreen) (Object) this);
        if (animation == ClickGuiAnimation.Default) return speed;
        float baseSpeed = animation == ClickGuiAnimation.Fade ? 3.125f : 2.5f;
        return baseSpeed * (THMSystem.get().clickGuiAnimationSpeed.get() / 100f);
    }

    @ModifyExpressionValue(method = "renderCustom", at = @At(value = "FIELD",
        target = "Lmeteordevelopment/meteorclient/gui/WidgetScreen;parent:Lnet/minecraft/client/gui/screens/Screen;"))
    private Screen thm$finishAnimationBeforeReturningToParent(Screen parent) {
        return ClickGuiAnimation.forScreen((WidgetScreen) (Object) this) == ClickGuiAnimation.Default ? parent : null;
    }

    @WrapOperation(method = "renderCustom", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/gui/widgets/containers/WContainer;render(Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;DDD)Z"))
    private boolean thm$renderAnimation(WContainer root, GuiRenderer renderer, double mouseX, double mouseY,
                                       double delta, Operation<Boolean> original) {
        return (animProgress == 0 && ClickGuiAnimation.forScreen((WidgetScreen) (Object) this) != ClickGuiAnimation.Default)
            || original.call(root, renderer, mouseX, mouseY, delta);
    }

    @WrapOperation(method = "renderCustom", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;setAlpha(D)V", ordinal = 1))
    private void thm$keepAlphaUntilBatchesFlush(GuiRenderer renderer, double alpha, Operation<Void> original) {
        if (ClickGuiAnimation.forScreen((WidgetScreen) (Object) this) == ClickGuiAnimation.Default) {
            original.call(renderer, alpha);
        }
    }

    @WrapOperation(method = "renderCustom", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/gui/renderer/GuiRenderer;end()V"))
    private void thm$restoreAlpha(GuiRenderer renderer, Operation<Void> original) {
        try {
            original.call(renderer);
        } finally {
            renderer.setAlpha(1);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void thm$blockClicksWhileMoving(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (animProgress < 1 && ClickGuiAnimation.forScreen((WidgetScreen) (Object) this) != ClickGuiAnimation.Default) {
            cir.setReturnValue(true);
        }
    }
}
