/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;

public final class ThmMixinConfigPlugin implements IMixinConfigPlugin {
    private static final Logger LOG = LogUtils.getLogger();

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.equals("xyz.thm.addon.mixin.BlockedServersMixin")) return true;

        try {
            // Probe bytecode without loading the target before Mixin can transform it.
            MixinService.getService().getBytecodeProvider().getClassNode(targetClassName, false);
            LOG.info("Enabled BlockedServersMixin for {}", targetClassName);
            return true;
        } catch (ClassNotFoundException e) {
            LOG.info("Skipped BlockedServersMixin because {} is missing", targetClassName);
        } catch (IOException e) {
            LOG.warn("Skipped BlockedServersMixin because {} could not be read", targetClassName, e);
        }
        return false;
    }
}
