/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.utils;

import baritone.api.schematic.ISchematic;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies this module's rules without changing global Baritone settings. */
public final class BuildSchematic implements ISchematic {
    private static final Set<String> ORIENTATION_PROPERTIES = Set.of("axis", "facing", "rotation");
    private final ISchematic source;
    public volatile Rules rules;

    public record Rules(Map<Block, Block> replacements, Map<Block, Set<Block>> accepted,
                        Set<Block> skipped, Set<Block> ignored, boolean grassAsDirt,
                        boolean ignoreExisting, boolean ignoreDirection, boolean stripLogs) {}

    public BuildSchematic(ISchematic source, Rules rules) {
        this.source = source;
        this.rules = rules;
    }

    @Override public int widthX() { return source.widthX(); }
    @Override public int heightY() { return source.heightY(); }
    @Override public int lengthZ() { return source.lengthZ(); }
    @Override public void reset() { source.reset(); }
    @Override public boolean inSchematic(int x, int y, int z, BlockState current) {
        return source.inSchematic(x, y, z, current);
    }

    public BlockState target(int x, int y, int z, BlockState current, List<BlockState> placeable) {
        return replace(source.desiredState(x, y, z, current, placeable), rules);
    }

    private static BlockState replace(BlockState desired, Rules config) {
        if (desired == null) return null;
        Block replacement = config.replacements().get(desired.getBlock());
        return replacement == null ? desired : withBlock(desired, replacement);
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> placeable) {
        Rules config = rules;
        BlockState requested = source.desiredState(x, y, z, current, placeable);
        BlockState desired = replace(requested, config);
        if (desired == null || current == null) return desired;
        if (config.skipped().contains(requested.getBlock())
            || (config.ignoreExisting() && !current.isAir())
            || (desired.isAir() && config.ignored().contains(current.getBlock()))
            || config.accepted().getOrDefault(requested.getBlock(), Set.of()).contains(current.getBlock())) return current;
        if (config.grassAsDirt() && isDirt(desired.getBlock())) {
            if (isDirt(current.getBlock())) return current;
            desired = Blocks.DIRT.defaultBlockState();
        }
        if (config.stripLogs() && !current.is(desired.getBlock())) {
            Block raw = unstripped(desired.getBlock());
            Block stripped = desired.getBlock();
            if (raw != null && !current.is(raw)
                && placeable.stream().noneMatch(state -> state.is(stripped)))
                desired = withBlock(desired, raw);
        }
        if (config.ignoreDirection() && current.is(desired.getBlock())) {
            for (Property<?> property : desired.getProperties()) {
                if (ORIENTATION_PROPERTIES.contains(property.getName()))
                    desired = copyProperty(current, desired, property);
            }
        }
        return desired;
    }

    public static Block unstripped(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (!id.getPath().startsWith("stripped_")) return null;
        Block raw = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().substring(9)));
        return raw == null || raw == Blocks.AIR ? null : raw;
    }

    private static boolean isDirt(Block block) { return block == Blocks.DIRT || block == Blocks.GRASS_BLOCK; }

    public static BlockState withBlock(BlockState state, Block block) {
        BlockState result = block.defaultBlockState();
        for (Property<?> property : state.getProperties()) result = copyProperty(state, result, property);
        return result;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> property) {
        if (to.hasProperty(property) && property.getPossibleValues().contains(from.getValue(property)))
            return to.setValue(property, from.getValue(property));
        return to;
    }
}
