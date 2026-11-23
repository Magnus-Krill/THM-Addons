/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







import baritone.api.schematic.ISchematic;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import xyz.thm.addon.utils.BuildSchematic;
import java.util.*;

public class BuildSchematicCheck {
    static int checks;
    static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        checks++;
    }
    static ISchematic source(BlockState desired) {
        return new ISchematic() {
            public int widthX() { return 1; }
            public int heightY() { return 1; }
            public int lengthZ() { return 1; }
            public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> placeable) { return desired; }
        };
    }
    static BuildSchematic.Rules rules(Map<Block, Block> replacements, Map<Block, Set<Block>> accepted,
        Set<Block> skip, Set<Block> ignore, boolean grass, boolean existing, boolean direction, boolean strip) {
        return new BuildSchematic.Rules(replacements, accepted, skip, ignore, grass, existing, direction, strip);
    }
    static BlockState desired(BuildSchematic schematic, BlockState current, BlockState... placeable) {
        return schematic.desiredState(0, 0, 0, current, List.of(placeable));
    }
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), cobble = Blocks.COBBLESTONE.defaultBlockState();
        var empty = rules(Map.of(), Map.of(), Set.of(), Set.of(), false, false, false, false);
        BuildSchematic schematic = new BuildSchematic(source(stone), empty);
        check(desired(schematic, air).equals(stone), "default preserves schematic");
        schematic.rules = rules(Map.of(Blocks.STONE, Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.DIRT),
            Map.of(Blocks.STONE, Set.of(Blocks.ANDESITE)), Set.of(), Set.of(), false, false, false, false);
        check(desired(schematic, air).equals(cobble), "replacement is one pass");
        check(desired(schematic, Blocks.ANDESITE.defaultBlockState()).is(Blocks.ANDESITE), "acceptance uses original schematic block");
        check(desired(schematic, dirt).equals(cobble), "acceptance does not keep unrelated blocks");
        schematic.rules = rules(Map.of(Blocks.STONE, Blocks.COBBLESTONE), Map.of(), Set.of(Blocks.STONE), Set.of(), false, false, false, false);
        check(desired(schematic, air).isAir(), "skip retains empty positions before replacement");
        check(desired(schematic, dirt).equals(dirt), "skip retains existing positions");
        schematic.rules = rules(Map.of(), Map.of(), Set.of(), Set.of(), false, true, false, false);
        check(desired(schematic, dirt).equals(dirt), "ignore existing keeps blocks");
        check(desired(schematic, air).equals(stone), "ignore existing fills air");
        schematic = new BuildSchematic(source(air), rules(Map.of(), Map.of(), Set.of(), Set.of(Blocks.DIRT), false, false, false, false));
        check(desired(schematic, dirt).equals(dirt), "keep selected blocks in schematic air");
        check(desired(schematic, stone).isAir(), "keep in air does not keep unrelated blocks");
        for (BlockState requested : List.of(dirt, grass)) {
            schematic = new BuildSchematic(source(requested), rules(Map.of(), Map.of(), Set.of(), Set.of(), true, false, false, false));
            check(desired(schematic, dirt).equals(dirt), "dirt accepted");
            check(desired(schematic, grass).equals(grass), "grass accepted");
            check(desired(schematic, air).equals(dirt), "place dirt for dirt or grass");
        }
        BlockState rawX = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState strippedX = Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        schematic = new BuildSchematic(source(strippedX), rules(Map.of(), Map.of(), Set.of(), Set.of(), false, false, false, true));
        check(desired(schematic, air).equals(rawX), "request raw logs while inventory is empty");
        check(desired(schematic, air, rawX).equals(rawX), "place raw log with correct axis");
        check(desired(schematic, rawX, rawX).equals(strippedX), "placed raw log must still be stripped");
        check(desired(schematic, strippedX).equals(strippedX), "finished stripping stays correct");
        check(desired(schematic, air, strippedX).equals(strippedX), "use stripped item directly when available");
        schematic = new BuildSchematic(source(rawX), rules(Map.of(Blocks.OAK_LOG, Blocks.BIRCH_LOG), Map.of(), Set.of(), Set.of(), false, false, false, false));
        check(desired(schematic, air).is(Blocks.BIRCH_LOG) && desired(schematic, air).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "replacement preserves axis");
        schematic = new BuildSchematic(source(rawX), rules(Map.of(), Map.of(), Set.of(), Set.of(), false, false, true, false));
        check(desired(schematic, Blocks.OAK_LOG.defaultBlockState()).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y, "accept different existing log axis");
        check(BuildSchematic.unstripped(Blocks.STRIPPED_CRIMSON_STEM) == Blocks.CRIMSON_STEM, "nether stems supported");
        check(BuildSchematic.unstripped(Blocks.STRIPPED_BAMBOO_BLOCK) == Blocks.BAMBOO_BLOCK, "bamboo supported");
        check(BuildSchematic.unstripped(Blocks.STONE) == null, "non logs cannot strip");
        System.out.println("PASS: " + checks + " Minecraft block-state behavior checks");
    }
}
