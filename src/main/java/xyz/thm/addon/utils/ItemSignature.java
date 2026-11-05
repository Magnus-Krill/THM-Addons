/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/** Loadout identity ignores wear but preserves enchantments, names and container contents. */
public final class ItemSignature {
    private ItemSignature() {}

    public static boolean matches(ItemStack expected, ItemStack actual) {
        if (expected.isEmpty() || actual.isEmpty()) return expected.isEmpty() && actual.isEmpty();
        if (expected.getItem() != actual.getItem()) return false;
        ItemStack left = expected.copyWithCount(1), right = actual.copyWithCount(1);
        left.remove(DataComponents.DAMAGE);
        right.remove(DataComponents.DAMAGE);
        return ItemStack.isSameItemSameComponents(left, right);
    }
}
