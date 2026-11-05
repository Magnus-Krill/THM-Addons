/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.combat.AutoArmor;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(value = AutoArmor.class, remap = false)
public abstract class AutoArmorMixin {
    @Shadow @Final private Setting<Boolean> ignoreElytra;
    @Shadow @Final private Setting<Set<ResourceKey<Enchantment>>> avoidedEnchantments;
    @Unique private Setting<Boolean> thm$mending;
    @Unique private Setting<Double> thm$threshold;
    @Unique private Setting<Integer> thm$delay;
    @Unique private int thm$cooldown;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$settings(CallbackInfo ci) {
        var group = ((AutoArmor) (Object) this).settings.createGroup("THM Mending");
        thm$mending = group.add(new BoolSetting.Builder().name("mending-rotation")
            .description("Rotates repaired armor for damaged mending pieces.").defaultValue(false).build());
        thm$threshold = group.add(new DoubleSetting.Builder().name("mending-threshold")
            .description("Durability percentage before rotating equipment.").defaultValue(100).range(50, 100).sliderRange(50, 100).visible(thm$mending::get).build());
        thm$delay = group.add(new IntSetting.Builder().name("mending-swap-delay")
            .description("Ticks between mending equipment swaps.").defaultValue(10).range(1, 40).sliderRange(1, 40).visible(thm$mending::get).build());
    }

    @Inject(method = "onPreTick", at = @At("HEAD"), cancellable = true)
    private void thm$rotateArmor(TickEvent.Pre event, CallbackInfo ci) {
        if (!thm$mending.get()) return;
        ci.cancel();
        if (mc.player == null || mc.gameMode == null || mc.player.containerMenu != mc.player.inventoryMenu
            || xyz.thm.addon.utils.InventoryManager.clickBudget(3) < 3
            || !mc.player.containerMenu.getCarried().isEmpty() || mc.player.isUsingItem()) return;
        if (thm$cooldown > 0) { thm$cooldown--; return; }
        for (EquipmentSlot equipment : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = mc.player.getItemBySlot(equipment);
            if (!worn.isEmpty() && (thm$binding(worn) || worn.has(DataComponents.GLIDER) && ignoreElytra.get()
                || thm$durability(worn) < thm$threshold.get())) continue;
            int best = -1;
            double durability = worn.isEmpty() ? 101 : thm$durability(worn);
            for (int i = 0; i < 36; i++) {
                ItemStack candidate = mc.player.getInventory().getItem(i);
                var equippable = candidate.get(DataComponents.EQUIPPABLE);
                if (equippable == null || equippable.slot() != equipment || !candidate.isDamageableItem()
                    || !EnchantmentHelper.has(candidate, EnchantmentEffectComponents.REPAIR_WITH_XP)
                    || thm$binding(candidate) || !worn.isEmpty() && candidate.getItem() != worn.getItem()
                    || candidate.getEnchantments().keySet().stream().anyMatch(holder -> holder.is(avoidedEnchantments.get()::contains))) continue;
                double value = thm$durability(candidate);
                if (value < durability) { durability = value; best = i; }
            }
            if (best >= 0) {
                InvUtils.move().fromId(SlotUtils.indexToId(best)).toId(8 - equipment.getIndex());
                thm$cooldown = thm$delay.get();
                return;
            }
        }
    }

    @Unique private static double thm$durability(ItemStack stack) { return stack.getMaxDamage() <= 0 ? 100 : 100.0 * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage(); }
    @Unique private static boolean thm$binding(ItemStack stack) { return stack.getEnchantments().keySet().stream().anyMatch(holder -> holder.is(Enchantments.BINDING_CURSE)); }
}
