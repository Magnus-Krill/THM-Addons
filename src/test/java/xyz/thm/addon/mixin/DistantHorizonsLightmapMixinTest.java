/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DistantHorizonsLightmapMixinTest {
    @Test
    void bindsAndRefreshesBlazeLightmapWithoutAnOpenGlHandle() {
        var bridge = new Bridge();
        var first = new BlazeTexture();
        bridge.thm$bindLightmap(first);
        assertSame(first, bridge.texture);
        assertNull(bridge.glId);

        var replacement = new BlazeTexture();
        bridge.thm$bindLightmap(replacement);
        assertSame(replacement, bridge.texture);
        assertNull(bridge.glId);
    }

    @Test
    void bindsBothTextureAndHandleForNativeOpenGl() {
        var bridge = new Bridge();
        var texture = new GlTexture(GpuTexture.USAGE_TEXTURE_BINDING, "lightmap", GpuFormat.RGBA8_UNORM,
            16, 16, 1, 1, 37, null) {};
        bridge.thm$bindLightmap(texture);
        assertSame(texture, bridge.texture);
        assertEquals(37, bridge.glId);
    }

    private static final class Bridge extends DistantHorizonsLightmapMixin {
        private GpuTexture texture;
        private Integer glId;

        @Override public void setLightmapGpuTexture(GpuTexture texture) { this.texture = texture; }
        @Override public void setLightmapId(int id) { glId = id; }
    }

    private static final class BlazeTexture extends GpuTexture {
        private BlazeTexture() {
            super(USAGE_TEXTURE_BINDING, "lightmap", GpuFormat.RGBA8_UNORM, 16, 16, 1, 1);
        }

        @Override public void close() {}
        @Override public boolean isClosed() { return false; }
    }
}
