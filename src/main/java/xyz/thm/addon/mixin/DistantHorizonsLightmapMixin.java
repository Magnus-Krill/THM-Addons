/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper", remap = false)
public abstract class DistantHorizonsLightmapMixin {
    @Shadow public abstract void setLightmapGpuTexture(GpuTexture texture);
    @Shadow public abstract void setLightmapId(int id);

    @Inject(method = "getLightmapWrapper", at = @At("HEAD"))
    private void thm$bindCurrentLightmap(CallbackInfoReturnable<?> cir) {
        // Meteor Gamma cancels Lightmap.render before DH's RETURN hook runs.
        thm$bindLightmap(Minecraft.getInstance().gameRenderer.levelLightmap().texture());
    }

    @Unique
    void thm$bindLightmap(GpuTexture texture) {
        if (texture instanceof GlTexture glTexture) setLightmapId(glTexture.glId());
        setLightmapGpuTexture(texture);
    }
}
