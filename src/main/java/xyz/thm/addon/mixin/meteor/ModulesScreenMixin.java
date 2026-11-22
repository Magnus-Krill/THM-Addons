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
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.AdvancedGuiTheme;
import xyz.thm.addon.gui.themes.LiquidGlassTheme;

import java.util.List;

@Mixin(value = ModulesScreen.class, remap = false)
public abstract class ModulesScreenMixin {
    @WrapOperation(method = "initWidgets", at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/gui/GuiTheme;verticalList()Lmeteordevelopment/meteorclient/gui/widgets/containers/WVerticalList;"))
    private WVerticalList thm$moduleHelp(GuiTheme theme, Operation<WVerticalList> original) {
        return theme instanceof LiquidGlassTheme glass ? glass.moduleHelp() : original.call(theme);
    }

    @WrapOperation(method = {"createCategory", "createSearch", "createFavorites"}, at = @At(value = "INVOKE",
        target = "Lmeteordevelopment/meteorclient/gui/widgets/containers/WContainer;add(Lmeteordevelopment/meteorclient/gui/widgets/WWidget;)Lmeteordevelopment/meteorclient/gui/utils/Cell;"))
    private Cell<?> thm$glassCategoryIcon(WContainer container, WWidget widget, Operation<Cell<?>> original) {
        if (container.getTheme() instanceof LiquidGlassTheme glass && widget instanceof WWindow window && window.beforeHeaderInit != null) {
            window.beforeHeaderInit = header -> header.add(glass.categoryIcon(window.id)).pad(2);
        }
        return original.call(container, widget);
    }

    @Inject(method = "createCategory", at = @At("RETURN"))
    private void thm$enableCategoryScroll(WContainer c, Category category, List<Module> moduleList, CallbackInfoReturnable<WWindow> cir) {
        if (!(GuiThemes.get() instanceof AdvancedGuiTheme advTheme && advTheme.useInlineModuleSettings())) return;
        WWindow w = cir.getReturnValue();
        if (w != null) w.view.hasScrollBar = true;
    }
}
