/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.system.THMSystem;

@Mixin(targets = "meteordevelopment.meteorclient.gui.tabs.builtin.GuiTab$GuiScreen", remap = false)
public abstract class GuiTabScreenMixin extends WindowTabScreen {
    @Unique private WVerticalList thm$settings;

    protected GuiTabScreenMixin(GuiTheme theme, Tab tab) {
        super(theme, tab);
    }

    @Inject(method = "initWidgets", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/gui/GuiTheme;settings(Lmeteordevelopment/meteorclient/settings/Settings;)Lmeteordevelopment/meteorclient/gui/widgets/WWidget;"))
    private void thm$addAnimationSetting(CallbackInfo ci) {
        THMSystem system = THMSystem.get();
        if (system == null) return;
        thm$settings = add(theme.verticalList()).expandX().widget();
        thm$settings.add(theme.settings(system.guiSettings)).expandX();
    }

    // GuiTab never ticks its settings, so visibility changes would otherwise wait for a reopen.
    @Override
    public void tick() {
        super.tick();
        THMSystem system = THMSystem.get();
        if (system != null) system.guiSettings.tick(thm$settings, theme);
    }
}
