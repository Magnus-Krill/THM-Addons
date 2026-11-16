/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import xyz.thm.addon.gui.ClickGuiAnimation;

@Mixin(GuiItemRenderState.class)
public abstract class GuiItemRenderStateMixin implements ClickGuiAnimation.ItemTint {
    @Unique private final int thm$itemTint = ClickGuiAnimation.itemTint;

    @Override
    public int thm$itemTint() {
        return thm$itemTint;
    }
}
