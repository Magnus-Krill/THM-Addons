/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WTriangle;
import net.minecraft.client.input.CharacterEvent;
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
    @Shadow public boolean focused;
    @Shadow public boolean visible;

    @Unique private boolean thm$wasOver;
    @Unique private double thm$sliderValue = Double.NaN;

    @Unique
    private static GuiSoundSettings thm$sounds() {
        THMSystem system = THMSystem.get();
        return system == null ? null : system.guiSounds;
    }

    @Inject(method = "mouseMoved", at = @At("TAIL"))
    private void thm$hoverSound(double mouseX, double mouseY, double lastMouseX, double lastMouseY, CallbackInfo ci) {
        thm$sliderSound();
        boolean entered = mouseOver && !thm$wasOver;
        thm$wasOver = mouseOver;
        Object self = this;
        if (!entered || !visible) return;
        GuiSoundSettings sounds = thm$sounds();
        if (sounds == null) return;
        if (GuiSoundSettings.isModuleWidget(this)) sounds.hover.play();
        else if (self instanceof WPressable || self instanceof WSlider || self instanceof WTextBox) sounds.optionHover.play();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void thm$clickSound(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (!mouseOver || !visible || !(self instanceof WPressable)) return;
        boolean left = click.button() == GLFW_MOUSE_BUTTON_LEFT, right = click.button() == GLFW_MOUSE_BUTTON_RIGHT;
        GuiSoundSettings sounds = thm$sounds();
        if (sounds == null || !(left || right)) return;
        // A press always toggles these on release, so the current state decides the sound.
        if (self instanceof WCheckbox checkbox) (checkbox.checked ? sounds.checkOff : sounds.checkOn).play();
        else if (self instanceof WDropdown<?>) sounds.dropdown.play();
        else if (GuiSoundSettings.isModuleWidget(this)) {
            // A module left click toggles it, which plays its own sound.
            if (right) sounds.rightClick.play();
        }
        // Section triangles play the expand sound.
        else if (left && !(self instanceof WTriangle)) sounds.click.play();
    }

    @Inject(method = {"mouseClicked", "mouseScrolled"}, at = @At("RETURN"))
    private void thm$sliderInput(CallbackInfoReturnable<Boolean> cir) {
        thm$sliderSound();
    }

    @Inject(method = "charTyped", at = @At("RETURN"))
    private void thm$typingSound(CharacterEvent input, CallbackInfoReturnable<Boolean> cir) {
        GuiSoundSettings sounds;
        if ((Object) this instanceof WTextBox && cir.getReturnValueZ() && (sounds = thm$sounds()) != null) sounds.typing.play();
    }

    @Unique
    private void thm$sliderSound() {
        if (!((Object) this instanceof WSlider slider)) return;
        double value = slider.get();
        // Only user input moves a hovered or dragged slider; the first sample just records the value.
        if (value != thm$sliderValue && !Double.isNaN(thm$sliderValue) && (mouseOver || focused)) {
            GuiSoundSettings sounds = thm$sounds();
            if (sounds != null) sounds.slider.play();
        }
        thm$sliderValue = value;
    }
}
