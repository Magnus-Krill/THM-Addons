/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import xyz.thm.addon.modules.Loadouts;
import xyz.thm.addon.modules.ShulkerOverview;
import xyz.thm.addon.utils.ItemSignature;
import xyz.thm.addon.utils.PlacementUtils;

import java.util.List;

public class InventoryUtilitiesCheck {
    private static int checks;
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = VanillaRegistries.createLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        var efficiency = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        tool.enchant(efficiency, 5);
        ItemStack damaged = tool.copy();
        damaged.setDamageValue(100);
        check(ItemSignature.matches(tool, damaged), "tool wear does not change loadout identity");
        check(!ItemSignature.matches(tool, new ItemStack(Items.NETHERITE_PICKAXE)), "enchantments distinguish tools");
        ItemStack named = tool.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Mining pick"));
        check(!ItemSignature.matches(tool, named), "custom names distinguish loadout items");
        var legacy = new Loadouts.SavedSlot(new ItemStack(Items.NETHERITE_PICKAXE), false);
        check(legacy.matches(named), "legacy item-only saves still accept enchanted and named items");
        check(legacy.count() == 1, "legacy saves do not invent stack quantities");
        ItemStack supplies = new ItemStack(Items.OBSIDIAN, 16);
        var saved = new Loadouts.SavedSlot(supplies, true);
        check(saved.count() == 16, "new saves retain quantities");
        check(saved.matches(new ItemStack(Items.OBSIDIAN, 64)), "quantity does not change item identity");
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        JsonElement encoded = ItemStack.CODEC.encodeStart(ops, named).getOrThrow();
        ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        check(ItemStack.matches(named, decoded), "26.2 registry codec preserves named enchanted tools");
        var contents = ItemContainerContents.fromItems(List.of(new ItemStack(Items.ENDER_CHEST, 2), new ItemStack(Items.OBSIDIAN, 64), new ItemStack(Items.ENDER_CHEST, 64)));
        check(ShulkerOverview.dominantItem(contents).is(Items.ENDER_CHEST), "overview aggregates quantities across stacks");
        check(ShulkerOverview.dominantItem(ItemContainerContents.EMPTY).isEmpty(), "empty shulkers have no contents icon");
        ItemStack firstBox = new ItemStack(Items.SHULKER_BOX), otherBox = firstBox.copy();
        firstBox.set(DataComponents.CONTAINER, contents);
        otherBox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.TOTEM_OF_UNDYING))));
        check(!ItemSignature.matches(firstBox, otherBox), "different shulker contents stay distinct");
        JsonElement encodedBox = ItemStack.CODEC.encodeStart(ops, firstBox).getOrThrow();
        check(ItemSignature.matches(firstBox, ItemStack.CODEC.parse(ops, encodedBox).getOrThrow()), "26.2 container components round-trip");
        var first = new net.minecraft.core.BlockPos(1, 0, 0);
        var second = new net.minecraft.core.BlockPos(2, 0, 0);
        var world = new net.minecraft.world.level.BlockGetter() {
            public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(net.minecraft.core.BlockPos pos) { return null; }
            public net.minecraft.world.level.block.state.BlockState getBlockState(net.minecraft.core.BlockPos pos) {
                return (pos.equals(first) || pos.equals(second) ? net.minecraft.world.level.block.Blocks.STONE : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState();
            }
            public net.minecraft.world.level.material.FluidState getFluidState(net.minecraft.core.BlockPos pos) { return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState(); }
            public int getHeight() { return 256; }
            public int getMinY() { return 0; }
        };
        var from = new net.minecraft.world.phys.Vec3(.5, .5, .5);
        var to = new net.minecraft.world.phys.Vec3(4.5, .5, .5);
        var ray = new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.OUTLINE,
            net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty());
        check(xyz.thm.addon.utils.RangeUtils.clipSkipping(world, ray, pos -> false).getBlockPos().equals(first), "normal queue ray hits the first solid block");
        check(xyz.thm.addon.utils.RangeUtils.clipSkipping(world, ray, first::equals).getBlockPos().equals(second), "queue-through skips an already queued block");
        check(xyz.thm.addon.utils.RangeUtils.clipSkipping(world, ray, pos -> true) == null, "queue-through cannot invent an out-of-range target");
        var edge = PlacementUtils.visibleFaceHit(first, Direction.WEST, point -> point.y < 0.4
            ? new net.minecraft.world.phys.BlockHitResult(point, Direction.WEST, first, false)
            : new net.minecraft.world.phys.BlockHitResult(point, Direction.WEST, second, false));
        check(edge != null && edge.getLocation().y < 0.4, "a visible edge can be clicked when the face center is occluded");
        check(PlacementUtils.visibleFaceHit(first, Direction.WEST,
            point -> new net.minecraft.world.phys.BlockHitResult(point, Direction.UP, first, false)) == null,
            "hitting the right block on the wrong face does not support placement");
        var directions = new Direction[]{Direction.EAST, Direction.WEST};
        check(PlacementUtils.findSupportStep(first, directions, 2, pos -> true,
            pos -> pos.equals(first.east(2)) || pos.equals(first.west())).equals(first.west()), "prefer a shorter support path");
        check(PlacementUtils.findSupportStep(first, directions, 2, pos -> !pos.equals(second),
            pos -> pos.equals(first.east(2))) == null, "do not build through a blocked support path");
        System.out.println("Inventory utility checks passed: " + checks);
    }
}
