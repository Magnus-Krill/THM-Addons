/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.AdvancedGuiTheme;

import java.util.List;

@Mixin(value = ModulesScreen.class, remap = false)
public abstract class ModulesScreenMixin {
    @Inject(method = "createCategory", at = @At("RETURN"))
    private void thm$enableCategoryScroll(WContainer c, Category category, List<Module> moduleList, CallbackInfoReturnable<WWindow> cir) {
        if (!(GuiThemes.get() instanceof AdvancedGuiTheme advTheme && advTheme.useInlineModuleSettings())) return;
        WWindow w = cir.getReturnValue();
        if (w != null) w.view.hasScrollBar = true;
    }
}
