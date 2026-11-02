/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockedServersMixinTest {
    @Test
    void blocked6b6tHostIsAllowedByTheExistingBypass() {
        var result = new CallbackInfoReturnable<Boolean>("isBlockedServerHostName", true, true);
        new BlockedServersMixin().isBlockedServerHostName("6b6t.org", result);
        assertFalse(result.getReturnValue());
        assertTrue(result.isCancelled());
    }
}
