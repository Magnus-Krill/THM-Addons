/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.settings.GuiSoundSettings;
import xyz.thm.addon.system.THMSystem;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

@Mixin(value = WWidget.class, remap = false)
public abstract class WWidgetSoundMixin {
    @Shadow public boolean mouseOver;
    @Shadow public boolean visible;

    @Unique private boolean thm$wasOver;

    @Inject(method = "mouseMoved", at = @At("TAIL"))
    private void thm$hoverSound(double mouseX, double mouseY, double lastMouseX, double lastMouseY, CallbackInfo ci) {
        boolean entered = mouseOver && !thm$wasOver;
        thm$wasOver = mouseOver;
        if (!entered || !visible || !GuiSoundSettings.isModuleWidget(this)) return;
        THMSystem system = THMSystem.get();
        if (system != null) system.guiSounds.hover.play();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void thm$clickSound(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!mouseOver || !visible || !((Object) this instanceof WPressable)) return;
        THMSystem system = THMSystem.get();
        if (system == null) return;
        boolean module = GuiSoundSettings.isModuleWidget(this);
        if (click.button() == GLFW_MOUSE_BUTTON_RIGHT && module) system.guiSounds.rightClick.play();
        // A module left click toggles it, which plays its own sound.
        else if (click.button() == GLFW_MOUSE_BUTTON_LEFT && !module) system.guiSounds.click.play();
    }
}
