/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.mixin.meteor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.thm.addon.gui.RecolorGuiTheme;
import xyz.thm.addon.mixin.accessor.WSectionAccessor;
import xyz.thm.addon.modules.HighwayBuilderTHM;

/**
 * Credit to crosby for this mixin from <a href="https://github.com/RacoonDog/Tokyo-Client">Tokyo-Client</a>
 * Allows custom themes implementing RecolorGuiTheme to display their custom names.
 **/
@Mixin(value = GuiTheme.class, remap = false)
public abstract class GuiThemeMixin implements ISerializable<GuiTheme> {
    @Shadow @Final @Mutable public String name;

    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lmeteordevelopment/meteorclient/gui/GuiTheme;name:Ljava/lang/String;"))
    private void rename(GuiTheme instance, String value) {
        if (instance instanceof RecolorGuiTheme theme) name = theme.getName();
        else name = value;
    }

    @WrapOperation(method = "settings(Lmeteordevelopment/meteorclient/settings/Settings;Ljava/lang/String;)Lmeteordevelopment/meteorclient/gui/widgets/WWidget;",
        at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/gui/utils/SettingsWidgetFactory;create(Lmeteordevelopment/meteorclient/gui/GuiTheme;Lmeteordevelopment/meteorclient/settings/Settings;Ljava/lang/String;)Lmeteordevelopment/meteorclient/gui/widgets/WWidget;"))
    private WWidget thm$highwaySettings(SettingsWidgetFactory factory, GuiTheme theme, Settings settings, String filter, Operation<WWidget> original) {
        Modules modules = Modules.get();
        HighwayBuilderTHM builder = modules == null ? null : modules.get(HighwayBuilderTHM.class);
        if (builder == null || settings != builder.settings) return original.call(factory, theme, settings, filter);

        Settings view = builder.settingsView();
        WWidget widget = original.call(factory, theme, view, filter);
        if (view != settings && widget instanceof WVerticalList list) {
            var logging = settings.getGroup(HighwayBuilderTHM.LOGGING_GROUP);
            for (var cell : list.cells) {
                if (cell.widget() instanceof WSection section && logging.name.equals(((WSectionAccessor) section).thm$getTitle())) {
                    Runnable action = section.action;
                    section.action = () -> {
                        if (action != null) action.run();
                        logging.sectionExpanded = section.isExpanded();
                    };
                    break;
                }
            }
        }
        return widget;
    }
}
