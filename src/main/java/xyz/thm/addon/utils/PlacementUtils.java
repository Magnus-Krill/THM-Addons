/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.mixin.accessor.ClientPlayerInteractionManagerTHMAccessor;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.settings.PacketPlacementSettings.Mode;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import static meteordevelopment.meteorclient.MeteorClient.mc;
public class PlacementUtils {
    private static final PlacementPacer PACER = new PlacementPacer();
    private static WeakReference<Connection> pacedConnection = new WeakReference<>(null);

    public static FindItemResult findResistantBlock() {
        for (Block block : List.of(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.ENDER_CHEST,
            Blocks.RESPAWN_ANCHOR, Blocks.ENCHANTING_TABLE, Blocks.ANVIL)) {
            FindItemResult result = InvUtils.findInHotbar(block.asItem());
            if (result.found()) return result;
        }
        return InvUtils.findInHotbar(itemStack -> false);
    }

    private static PlacementPacer pacer(Connection connection) {
        if (connection != pacedConnection.get()) {
            pacedConnection = new WeakReference<>(connection);
            PACER.reset();
        }
        return PACER;
    }

    public static int placementsPerTick(Mode mode, int configured) {
        return mode == Mode.Pace ? PlacementPacer.LIMIT : configured;
    }

    public static int remainingPlacementPackets(Mode mode) {
        if (mode != Mode.Pace) return Integer.MAX_VALUE;
        var listener = mc.getConnection();
        return listener == null ? 0 : pacer(listener.getConnection()).remaining(System.nanoTime());
    }

    private static boolean sendWithMode(Connection connection, Mode mode, BooleanSupplier send) {
        if (mode != Mode.Pace) return send.getAsBoolean();
        return pacer(connection).send(mode, System::nanoTime, () -> {
            var modules = Modules.get();
            var highway = modules == null ? null : modules.get(HighwayBuilderTHM.class);
            return (highway == null || highway.releaseBeforePacedPlacement(connection)) && send.getAsBoolean();
        });
    }

    public static boolean sendPlacementPacket(ServerboundUseItemOnPacket packet, Mode mode) {
        var listener = mc.getConnection();
        if (listener == null) return false;
        var connection = listener.getConnection();
        return sendWithMode(connection, mode, () -> PacketPlaceTracker.trackUseOnSend(connection,
            packet.getHand(), packet.getHitResult(), () -> listener.send(packet)));
    }

    public static boolean sendPlacement(BlockPos target, BlockHitResult hit, InteractionHand hand, Mode mode) {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null) return false;
        if (mode.isPacket()) {
            if (!sendSequencedPlacement(hand, hit, mode)) return false;
            player.swing(hand);
            return true;
        }
        boolean sneaking = player.isShiftKeyDown();
        InteractionResult[] result = {InteractionResult.PASS};
        player.setShiftKeyDown(false);
        boolean sent;
        try {
            sent = predictPlacement(target, hit, hand,
                () -> result[0] = gameMode.useItemOn(player, hand, hit));
        } finally {
            player.setShiftKeyDown(sneaking);
        }
        if (sent && result[0].consumesAction()) player.swing(hand);
        return sent;
    }

    public static boolean interactPlacement(BlockPos target, BlockHitResult hit, InteractionHand hand, boolean swing) {
        return predictPlacement(target, hit, hand, () -> BlockUtils.interact(hit, hand, swing));
    }

    private static boolean predictPlacement(BlockPos target, BlockHitResult hit, InteractionHand hand, Runnable use) {
        var player = mc.player;
        var level = mc.level;
        var connection = mc.getConnection();
        if (player == null || level == null || mc.gameMode == null || connection == null) return false;
        BlockState before = level.getBlockState(target);
        ItemStack stack = player.getItemInHand(hand);
        int count = stack.getCount();
        boolean sent = PacketPlaceTracker.trackUseOnSend(connection.getConnection(), hand, hit, use);
        if (!sent && mc.level == level && mc.player == player) {
            // Vanilla predicts before the packet limiter sees the use-on packet.
            if (level.getBlockState(target) != before) level.setBlock(target, before, 19);
            if (player.getItemInHand(hand) == stack && stack.getCount() == count - 1) stack.setCount(count);
        }
        return sent;
    }

    public static boolean place(BlockPos pos, FindItemResult item, boolean rotate, int priority, Mode mode) {
        return place(pos, item, rotate, priority, true, true, true, mode);
    }

    public static boolean place(BlockPos pos, FindItemResult item, boolean rotate, int priority, boolean checkEntities, Mode mode) {
        return place(pos, item, rotate, priority, true, checkEntities, true, mode);
    }

    public static boolean place(BlockPos pos, FindItemResult item, boolean rotate, int priority,
                                boolean swing, boolean checkEntities, boolean swapBack, Mode mode) {
        return place(pos, item.slot(), rotate, priority, swing, checkEntities, swapBack, mode);
    }

    public static boolean place(BlockPos pos, int slot, boolean rotate, int priority,
                                boolean swing, boolean checkEntities, boolean swapBack, Mode mode) {
        return place(pos, slot, rotate, priority, swing, checkEntities, swapBack, mode, null);
    }

    public static boolean place(BlockPos pos, int slot, boolean rotate, int priority,
                                boolean swing, boolean checkEntities, boolean swapBack, Mode mode, Runnable afterPlace) {
        InteractionHand hand = placementHand(slot);
        if (hand == null || mc.getConnection() == null || remainingPlacementPackets(mode) == 0) return false;
        if (mode.isPacket() && !PacketPlaceTracker.canSend(pos)) return false;
        boolean offhand = hand == InteractionHand.OFF_HAND;
        var player = mc.player;
        var level = mc.level;
        ItemStack stack = offhand ? player.getOffhandItem() : player.getInventory().getItem(slot);
        Block block = stack.getItem() instanceof BlockItem item ? item.getBlock() : Blocks.OBSIDIAN;
        if (!BlockUtils.canPlaceBlock(pos, checkEntities, block)) return false;

        BlockPos target = pos.immutable();
        Direction side = BlockUtils.getPlaceSide(target);
        BlockHitResult hit = side == null ? new BlockHitResult(Vec3.atCenterOf(target), Direction.DOWN, target, false)
            : packetPlacementHit(target, side);
        BooleanSupplier place = () -> {
            if (mc.player != player || mc.level != level || remainingPlacementPackets(mode) == 0
                || mode.isPacket() && !PacketPlaceTracker.canSend(target) || !BlockUtils.canPlaceBlock(target, checkEntities, block) || BlockUtils.getPlaceSide(target) != side) return false;
            ItemStack current = offhand ? player.getOffhandItem() : player.getInventory().getItem(slot);
            if (current.isEmpty() || !current.is(stack.getItem())) return false;
            if (offhand) return placeAtHit(target, hit, hand, swing, mode);
            if (!InvUtils.swap(slot, swapBack)) return false;
            try {
                return placeAtHit(target, hit, hand, swing, mode);
            } finally {
                if (swapBack) InvUtils.swapBack();
            }
        };
        BooleanSupplier placeAndContinue = afterPlace == null ? place : () -> {
            if (!place.getAsBoolean()) return false;
            afterPlace.run();
            return true;
        };
        if (!rotate) return placeAndContinue.getAsBoolean();
        Rotations.rotate(Rotations.getYaw(hit.getLocation()), Rotations.getPitch(hit.getLocation()), priority, placeAndContinue::getAsBoolean);
        return true;
    }

    public static boolean placeAtHit(BlockPos target, BlockHitResult hit, InteractionHand hand, boolean swing, Mode mode) {
        if (!mode.isPacket()) return interactPlacement(target, hit, hand, swing);
        if (!PacketPlaceTracker.canSend(target) || !sendSequencedPlacement(hand, hit, mode)) return false;
        PacketPlaceTracker.markSent(target, PacketPlaceTracker.DEFAULT_RESEND_TICKS);
        if (swing) mc.player.swing(hand);
        else mc.getConnection().send(new ServerboundSwingPacket(hand));
        return true;
    }

    static InteractionHand placementHand(int slot) {
        if (slot == SlotUtils.OFFHAND) return InteractionHand.OFF_HAND;
        return SlotUtils.isHotbar(slot) ? InteractionHand.MAIN_HAND : null;
    }

    public static boolean placeBlock(BlockPos pos, FindItemResult block, boolean rotate, boolean swing,
                                     boolean strictDirection, Mode mode) {
        if (!block.found() || remainingPlacementPackets(mode) == 0 || !canPlace(pos, strictDirection)) return false;
        Direction side = getPlaceSide(pos);
        if (side == null) return false;
        BlockPos neighbor = pos.relative(side);
        Direction opposite = side.getOpposite();
        Vec3 hitPos = Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(opposite.getUnitVec3i()).scale(0.5));
        if (rotate) {
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos));
        }
        if (block.getHand() == null && !InvUtils.swap(block.slot(), false)) return false;
        BlockHitResult hitResult = new BlockHitResult(hitPos, opposite, neighbor, false);
        InteractionHand hand = block.getHand() != null ? block.getHand() : InteractionHand.MAIN_HAND;
        if (!mode.isPacket()) return interactPlacement(pos, hitResult, hand, swing);
        if (!sendPlacementPacket(new ServerboundUseItemOnPacket(hand, hitResult, 0), mode)) return false;
        if (swing) {
            if (hand == InteractionHand.MAIN_HAND) {
                mc.player.swing(InteractionHand.MAIN_HAND);
            } else {
                mc.getConnection().send(new ServerboundSwingPacket(hand));
            }
        }
        return true;
    }
    public static boolean canPlace(BlockPos pos, boolean strictDirection) {
        if (!mc.level.getBlockState(pos).canBeReplaced()) return false;
        if (!mc.level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) return false;
        AABB checkBox = new AABB(pos);
        List<net.minecraft.world.entity.Entity> entities = mc.level.getEntities(null, checkBox);
        for (net.minecraft.world.entity.Entity entity : entities) {
            if (!entity.isSpectator() && entity.isAlive()) {
                return false;
            }
        }
        return !strictDirection || getPlaceSide(pos) != null;
    }
    /**
     * A neighbour face that can actually be clicked to place at {@code pos}.
     *
     * Meteor's own {@code BlockUtils.getPlaceSide} only rejects <i>air</i> neighbours, so it happily
     * returns a snow layer, grass or any other replaceable block — and clicking a replaceable block
     * makes vanilla place into <i>that</i> block instead of offsetting to {@code pos}, so the block
     * lands in the wrong spot. This one requires a real, non-replaceable face (which also covers air
     * and fluids, both replaceable) and skips blocks whose right-click opens a GUI.
     */
    public static Direction getPlaceSide(BlockPos pos) {
        if (isSolidFace(pos.below())) return Direction.DOWN;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (isSolidFace(pos.relative(side))) return side;
        }
        if (isSolidFace(pos.above())) return Direction.UP;
        return null;
    }

    private static boolean isSolidFace(BlockPos pos) {
        return isSolidFace(mc.level.getBlockState(pos));
    }

    private static boolean isSolidFace(BlockState state) {
        return !state.canBeReplaced() && !BlockUtils.isClickable(state.getBlock());
    }

    public static boolean isSupportBlock(BlockState state) {
        return state.getFluidState().isEmpty() && isSolidFace(state);
    }

    public static BlockHitResult findSupportHit(BlockPos target, boolean strict) {
        Vec3 eye = mc.player.getEyePosition();
        double reach = mc.player.blockInteractionRange();
        for (Direction side : Direction.values()) {
            BlockPos support = target.relative(side);
            if (!isSupportBlock(mc.level.getBlockState(support))) continue;
            if (!strict) return packetPlacementHit(target, side);
            BlockHitResult hit = visibleFaceHit(support, side.getOpposite(), point -> {
                BlockHitResult ray = mc.level.clip(new ClipContext(eye, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
                return eye.distanceToSqr(ray.getLocation()) <= reach * reach ? ray : null;
            });
            if (hit != null) return hit;
        }
        return null;
    }

    public static BlockHitResult visibleFaceHit(BlockPos support, Direction face, Function<Vec3, BlockHitResult> trace) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        Vec3 center = Vec3.atCenterOf(support).add(normal.scale(0.4999));
        Vec3 first = face.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 0.3) : new Vec3(0.3, 0, 0);
        Vec3 second = face.getAxis() == Direction.Axis.Y ? new Vec3(0, 0, 0.3) : new Vec3(0, 0.3, 0);
        BlockHitResult hit = trace.apply(center);
        if (hitsFace(hit, support, face)) return hit;
        for (int a = -1; a <= 1; a += 2) {
            for (int b = -1; b <= 1; b += 2) {
                hit = trace.apply(center.add(first.scale(a)).add(second.scale(b)));
                if (hitsFace(hit, support, face)) return hit;
            }
        }
        return null;
    }

    private static boolean hitsFace(BlockHitResult hit, BlockPos support, Direction face) {
        return hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support) && hit.getDirection() == face;
    }

    public static BlockPos findSupportStep(BlockPos target, Direction[] sides, int maxDistance,
                                           Predicate<BlockPos> canFill, Predicate<BlockPos> hasSupport) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(target);
        visited.add(target);
        for (int distance = 1; distance <= maxDistance && !queue.isEmpty(); distance++) {
            for (int remaining = queue.size(); remaining > 0; remaining--) {
                BlockPos previous = queue.removeFirst();
                for (Direction side : sides) {
                    BlockPos step = previous.relative(side);
                    if (!visited.add(step) || !canFill.test(step)) continue;
                    if (hasSupport.test(step)) return step;
                    queue.addLast(step);
                }
            }
        }
        return null;
    }

    public static Set<BlockPos> horizontalFootprint(AABB bounds, int y) {
        Set<BlockPos> feet = new LinkedHashSet<>();
        for (int x = Mth.floor(bounds.minX + 1e-7); x <= Mth.floor(bounds.maxX - 1e-7); x++) {
            for (int z = Mth.floor(bounds.minZ + 1e-7); z <= Mth.floor(bounds.maxZ - 1e-7); z++) {
                feet.add(new BlockPos(x, y, z));
            }
        }
        return feet;
    }

    public static BlockPos getDirectionalPlacement(float yaw, BlockPos basePos) {
        float normalizedYaw = yaw % 360.0f;
        if (normalizedYaw < 0.0f) normalizedYaw += 360.0f;
        if (normalizedYaw >= 22.5 && normalizedYaw < 67.5) return basePos.south().west();
        else if (normalizedYaw >= 67.5 && normalizedYaw < 112.5) return basePos.west();
        else if (normalizedYaw >= 112.5 && normalizedYaw < 157.5) return basePos.north().west();
        else if (normalizedYaw >= 157.5 && normalizedYaw < 202.5) return basePos.north();
        else if (normalizedYaw >= 202.5 && normalizedYaw < 247.5) return basePos.north().east();
        else if (normalizedYaw >= 247.5 && normalizedYaw < 292.5) return basePos.east();
        else if (normalizedYaw >= 292.5 && normalizedYaw < 337.5) return basePos.south().east();
        else return basePos.south();
    }
    public static boolean isPhasing() {
        if (mc.player == null) return false;
        AABB bb = mc.player.getBoundingBox();
        int minX = net.minecraft.util.Mth.floor(bb.minX);
        int maxX = net.minecraft.util.Mth.floor(bb.maxX) + 1;
        int minY = net.minecraft.util.Mth.floor(bb.minY);
        int maxY = net.minecraft.util.Mth.floor(bb.maxY) + 1;
        int minZ = net.minecraft.util.Mth.floor(bb.minZ);
        int maxZ = net.minecraft.util.Mth.floor(bb.maxZ) + 1;
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) {
                        AABB blockBox = new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0);
                        if (bb.intersects(blockBox)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    public static int getEnderPearlSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 45; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == net.minecraft.world.item.Items.ENDER_PEARL) {
                return i;
            }
        }
        return -1;
    }
    public static void clickSlot(int slot, net.minecraft.world.inventory.ContainerInput actionType) {
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleContainerInput(0, slot, 0, actionType, mc.player);
        }
    }
    public static boolean isPhased() {
        return isPhasing();
    }
    public static boolean isDoublePhased() {
        if (mc.player == null || mc.level == null) return false;
        AABB playerBox = mc.player.getBoundingBox();
        boolean feetBlocked = false;
        boolean headBlocked = false;
        for (int x = (int) Math.floor(playerBox.minX); x <= Math.floor(playerBox.maxX); x++) {
            for (int z = (int) Math.floor(playerBox.minZ); z <= Math.floor(playerBox.maxZ); z++) {
                BlockPos feetPos = new BlockPos(x, (int) Math.floor(playerBox.minY), z);
                if (!mc.level.getBlockState(feetPos).getCollisionShape(mc.level, feetPos).isEmpty()) {
                    feetBlocked = true;
                }
                BlockPos headPos = new BlockPos(x, (int) Math.floor(playerBox.maxY), z);
                if (!mc.level.getBlockState(headPos).getCollisionShape(mc.level, headPos).isEmpty()) {
                    headBlocked = true;
                }
                if (feetBlocked && headBlocked) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @param swapBack When false, the hotbar selection is kept after placing (only swaps if the slot needs to change).
     *                 Use false for packet-build highway placing to minimise UpdateSelectedSlot packets.
     *                 Use true (default) for PvP modules that need the hand restored after each place.
     */
    public static boolean placeBlockPacket(BlockPos pos, FindItemResult item, boolean rotate, int rotateTicks, boolean swapBack, Mode mode) {
        if (remainingPlacementPackets(mode) == 0) return false;
        Direction side = getPlaceSide(pos);
        return placeBlockPacket(pos, item, rotate, rotateTicks, swapBack, packetPlacementHit(pos, side), packet -> sendPlacementPacket(packet, mode));
    }

    /** Clicks {@code pos} on the face toward {@code toward}; a filled target then spills into the player's own space, which is blocked. */
    public static BlockHitResult towardHit(BlockPos pos, Vec3 toward) {
        Vec3 center = Vec3.atCenterOf(pos);
        Direction face = Direction.getApproximateNearest(toward.x - center.x, toward.y - center.y, toward.z - center.z);
        return new BlockHitResult(center.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5), face, pos, false);
    }

    public static BlockHitResult packetPlacementHit(BlockPos pos, Direction side) {
        BlockPos neighbour = side == null ? pos : pos.relative(side);
        Direction hitSide = side == null ? Direction.UP : side.getOpposite();
        Vec3 hitPos = Vec3.atCenterOf(pos);
        if (side != null) {
            hitPos = hitPos.add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
        }
        return new BlockHitResult(hitPos, hitSide, neighbour, false);
    }

    public static boolean placeBlockPacket(BlockPos pos, FindItemResult item, boolean rotate, int rotateTicks, boolean swapBack,
                                           BlockHitResult hit, Predicate<ServerboundUseItemOnPacket> send) {
        InteractionHand hand = placementHand(item.slot());
        if (hand == null || !BlockUtils.canPlace(pos) || !mc.level.getWorldBorder().isWithinBounds(pos)) return false;
        var player = mc.player;
        var level = mc.level;
        boolean offhand = hand == InteractionHand.OFF_HAND;
        var expected = (offhand ? player.getOffhandItem() : player.getInventory().getItem(item.slot())).getItem();
        BooleanSupplier place = () -> {
            if (mc.player != player || mc.level != level) return false;
            ItemStack stack = offhand ? player.getOffhandItem() : player.getInventory().getItem(item.slot());
            if (stack.isEmpty() || !stack.is(expected)) return false;
            boolean swapped = false;
            if (!offhand) {
                if (swapBack) {
                    if (!InvUtils.swap(item.slot(), true)) return false;
                    swapped = true;
                } else if (player.getInventory().getSelectedSlot() != item.slot()) {
                    if (!InvUtils.swap(item.slot(), false)) return false;
                }
            }

            try {
                if (!send.test(new ServerboundUseItemOnPacket(hand, hit, 0))) return false;
                player.connection.send(new ServerboundSwingPacket(hand));
                return true;
            } finally {
                if (swapped) InvUtils.swapBack();
            }
        };

        if (rotate) {
            Rotations.rotate(Rotations.getYaw(hit.getLocation()), Rotations.getPitch(hit.getLocation()), rotateTicks, place::getAsBoolean);
            return true;
        }
        return place.getAsBoolean();
    }

    public static boolean sendSequencedPlacement(InteractionHand hand, BlockHitResult hit, Mode mode) {
        var level = mc.level;
        var gameMode = mc.gameMode;
        var connection = mc.getConnection();
        if (level == null || gameMode == null || connection == null) return false;
        return sendWithMode(connection.getConnection(), mode, () ->
            PacketPlaceTracker.trackUseOnSend(connection.getConnection(), hand, hit, () ->
            ((ClientPlayerInteractionManagerTHMAccessor) gameMode).thm$sendSequencedPacket(level,
                sequence -> new ServerboundUseItemOnPacket(hand, hit, sequence))));
    }

    public static boolean placeWithItem(InteractionHand hand, Vec3 aim, Mode mode) {
        var player = mc.player;
        var level = mc.level;
        var gameMode = mc.gameMode;
        var listener = mc.getConnection();
        if (player == null || level == null || gameMode == null || listener == null) return false;

        float yaw = (float) Rotations.getYaw(aim), pitch = (float) Rotations.getPitch(aim);
        if (!mode.isPacket()) {
            float previousYaw = player.getYRot(), previousPitch = player.getXRot();
            player.setYRot(yaw);
            player.setXRot(pitch);
            try {
                if (!gameMode.useItem(player, hand).consumesAction()) return false;
            } finally {
                player.setYRot(previousYaw);
                player.setXRot(previousPitch);
            }
        } else {
            var connection = listener.getConnection();
            if (!sendWithMode(connection, mode, () ->
                PacketPlaceTracker.trackItemUseSend(connection, hand, () ->
                    ((ClientPlayerInteractionManagerTHMAccessor) gameMode).thm$sendSequencedPacket(level,
                        sequence -> new ServerboundUseItemPacket(hand, sequence, yaw, pitch))))) return false;
        }
        player.swing(hand);
        return true;
    }
}
