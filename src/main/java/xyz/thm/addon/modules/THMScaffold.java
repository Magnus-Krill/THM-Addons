/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.misc.ListMode;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.AABB;
import xyz.thm.addon.THMAddon;
import meteordevelopment.meteorclient.events.entity.player.ClipAtLedgeEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IMultiPlayerGameMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Scaffold placement adapted from Meteor Client (copyright Meteor Development). */
public class THMScaffold extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Selected blocks.")
        .build()
    );

    private final Setting<ListMode> blocksFilter = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("blocks-filter")
        .description("How to use the block list setting")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    private final Setting<Boolean> fastTower = sgGeneral.add(new BoolSetting.Builder()
        .name("fast-tower")
        .description("Whether or not to scaffold upwards faster.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> towerSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("tower-speed")
        .description("The speed at which to tower.")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(1)
        .visible(fastTower::get)
        .build()
    );

    private final Setting<Boolean> whileMoving = sgGeneral.add(new BoolSetting.Builder()
        .name("while-moving")
        .description("Allows you to tower while moving.")
        .defaultValue(false)
        .visible(fastTower::get)
        .build()
    );

    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-click")
        .description("Only places blocks when holding right click.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> renderSwing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Renders your client-side swing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Automatically swaps to a block before placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the blocks being placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allow air place. This also allows you to modify scaffold radius.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> aheadDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("ahead-distance")
        .description("How far ahead to place blocks.")
        .defaultValue(0)
        .min(0)
        .sliderMax(1)
        .visible(() -> !airPlace.get())
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("closest-block-range")
        .description("How far can scaffold place blocks when you are in air.")
        .defaultValue(4)
        .min(0)
        .sliderMax(8)
        .visible(() -> !airPlace.get())
        .build()
    );

    private final Setting<Double> radius = sgGeneral.add(new DoubleSetting.Builder()
        .name("radius")
        .description("Scaffold radius.")
        .defaultValue(0)
        .min(0)
        .max(6)
        .visible(airPlace::get)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place in one tick.")
        .defaultValue(3)
        .min(1)
        .build()
    );

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Whether to render blocks that have been placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color of the target block rendering.")
        .defaultValue(new SettingColor(197, 137, 232, 10))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color of the target block rendering.")
        .defaultValue(new SettingColor(197, 137, 232))
        .visible(render::get)
        .build()
    );

    private final BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
    private int placementsThisTick;
    private Setting<Boolean> thm$packetPlace;
    private Setting<Boolean> thm$packetPlaceOnce;
    private Setting<Boolean> thm$packetLimiter;
    private Setting<Integer> thm$packetLimit;
    private Setting<Boolean> thm$safeMove;
    private Setting<Boolean> thm$keepY;
    private Setting<Boolean> thm$interpolate;
    private Setting<Boolean> thm$lookahead;
    private Setting<Integer> thm$lookaheadTicks;

    private final PacketState thm$packets = new PacketState();
    private final LongOpenHashSet thm$queued = new LongOpenHashSet();
    private ClientLevel thm$level;
    private BlockPos thm$lastScaffoldBp;
    private int thm$lockedY = Integer.MIN_VALUE;
    private long thm$epoch;
    private long thm$tick;
    private ServerboundUseItemOnPacket thm$sending;
    private boolean thm$sent;
    private int thm$syncSlot = -1;
    private boolean thm$slotSent;

    public THMScaffold() {
        super(THMAddon.MAIN, "THMScaffold", "Scaffolds with silent swaps and a built-in packet budget.");
        thm$packetPlace = sgGeneral.add(new BoolSetting.Builder()
            .name("packet-place").description("Places blocks without client prediction.")
            .defaultValue(false).build());
        thm$packetPlaceOnce = sgGeneral.add(new BoolSetting.Builder()
            .name("packet-place-once").description("Waits for a server block update before retrying.")
            .defaultValue(false).visible(thm$packetPlace::get).build());
        thm$packetLimiter = sgGeneral.add(new BoolSetting.Builder()
            .name("packet-limiter").description("Limits Scaffold packets per tick.")
            .defaultValue(false).build());
        thm$packetLimit = sgGeneral.add(new IntSetting.Builder()
            .name("packet-limit").description("Scaffold packet budget per tick, including slot swaps and swings.")
            .defaultValue(23).min(1).sliderRange(1, 100).visible(thm$packetLimiter::get).build());
        thm$safeMove = sgGeneral.add(new BoolSetting.Builder()
            .name("safe-move").description("Prevents walking off edges while scaffolding.")
            .defaultValue(false).build());
        thm$keepY = sgGeneral.add(new BoolSetting.Builder()
            .name("keep-y").description("Keeps placement at the activation height.")
            .defaultValue(false).build());
        thm$interpolate = sgGeneral.add(new BoolSetting.Builder()
            .name("interpolate").description("Fills gaps between tick positions.")
            .defaultValue(false).build());
        thm$lookahead = sgGeneral.add(new BoolSetting.Builder()
            .name("lookahead").description("Places ahead along your movement.")
            .defaultValue(false).build());
        thm$lookaheadTicks = sgGeneral.add(new IntSetting.Builder()
            .name("lookahead-ticks").description("Ticks of movement to predict.")
            .defaultValue(5).range(1, 20).sliderRange(1, 20).visible(thm$lookahead::get).build());
    }

    @Override
    public void onActivate() {
        thm$reset();
        thm$level = mc.level;
        if (mc.player != null) thm$lockedY = mc.player.getBlockY() - 1;
    }

    @Override
    public void onDeactivate() {
        thm$reset();
    }

    private void thm$reset() {
        thm$packets.clear();
        thm$queued.clear();
        thm$level = null;
        thm$lastScaffoldBp = null;
        thm$lockedY = Integer.MIN_VALUE;
        thm$epoch++;
    }

    @Override
    public Module fromTag(CompoundTag tag) {
        return super.fromTag(thm$migrateSettings(tag));
    }

    static CompoundTag thm$migrateSettings(CompoundTag source) {
        if (source.getBooleanOr("scaffold-compact-settings", false)) return source;
        CompoundTag tag = source.copy();
        Map<String, CompoundTag> values = new HashMap<>();
        for (var entry : tag.getCompoundOrEmpty("settings").getListOrEmpty("groups")) {
            if (!(entry instanceof CompoundTag group)) continue;
            for (var value : group.getListOrEmpty("settings")) {
                if (value instanceof CompoundTag setting) values.put(setting.getStringOr("name", ""), setting);
            }
        }
        if (values.containsKey("air-place") && values.containsKey("packet-place")
            && values.get("packet-place").getBooleanOr("value", false)) {
            CompoundTag legacy = values.get("packet-air-place");
            values.get("air-place").putBoolean("value", legacy == null || legacy.getBooleanOr("value", true));
        }
        CompoundTag render = values.get("render");
        if (render != null && !render.getBooleanOr("value", true)) {
            for (String prefix : new String[] { "interp", "lookahead" }) {
                String toggle = prefix.equals("interp") ? "interpolate" : prefix;
                if (!values.containsKey(toggle) || !values.get(toggle).getBooleanOr("value", false)) continue;
                CompoundTag legacyRender = values.get(toggle + "-render");
                if (legacyRender != null && !legacyRender.getBooleanOr("value", true)) continue;
                render.putBoolean("value", true);
                for (String name : new String[] { "shape-mode", "side-color", "line-color" }) {
                    CompoundTag legacy = values.get(prefix + "-" + name);
                    if (legacy != null && values.containsKey(name)) values.get(name).put("value", legacy.get("value").copy());
                }
                break;
            }
        }
        tag.putBoolean("scaffold-compact-settings", true);
        return tag;
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();
        if (tag != null) tag.putBoolean("scaffold-compact-settings", true);
        return tag;
    }

    @EventHandler
    private void thm$onGameLeft(GameLeftEvent event) {
        thm$reset();
    }

    @EventHandler
    private void thm$onClipAtLedge(ClipAtLedgeEvent event) {
        if (thm$safeMove.get()) event.setClip(true);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        thm$beginTick();
        tickScaffold();
        thm$extendScaffold();
    }

    private void thm$beginTick() {
        placementsThisTick = 0;
        if (mc.level != thm$level) {
            thm$reset();
            thm$level = mc.level;
        }
        thm$epoch++;
        thm$tick++;
        thm$queued.clear();
        thm$packets.beginTick();
        if (mc.player == null || mc.level == null) return;
        if (thm$keepY.get() && thm$lockedY == Integer.MIN_VALUE) thm$lockedY = mc.player.getBlockY() - 1;
        if (!thm$keepY.get()) thm$lockedY = Integer.MIN_VALUE;
        if (onlyOnClick.get() && !mc.options.keyUse.isDown()) thm$lastScaffoldBp = null;
        if (!thm$packetPlace.get() || !thm$packetPlaceOnce.get()) {
            thm$packets.clearPending();
            return;
        }
        for (var entry : thm$packets.snapshot().entrySet()) {
            BlockPos pos = BlockPos.of(entry.getKey());
            if (mc.level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null || !mc.level.getBlockState(pos).canBeReplaced()
                || mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 36) {
                thm$packets.forget(entry.getKey());
                continue;
            }
            if (thm$tick - entry.getValue() < 20) continue;
            InteractionHand hand = thm$inertHand();
            if (hand == null || !thm$packets.reserve(thm$packetLimiter.get(), thm$packetLimit.get(), 1)) continue;
            mc.getConnection().send(new ServerboundUseItemOnPacket(hand,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false), 0));
            thm$packets.markSent(entry.getKey(), thm$tick);
        }
    }

    @EventHandler
    private void thm$onReceive(PacketEvent.Receive event) {
        if (mc.getConnection() == null || event.connection != mc.getConnection().getConnection()) return;
        if (event.packet instanceof ClientboundBlockUpdatePacket update) {
            thm$packets.answer(update.getPos().asLong());
        } else if (event.packet instanceof ClientboundSectionBlocksUpdatePacket update) {
            update.runUpdates((pos, state) -> thm$packets.answer(pos.asLong()));
        }
    }

    @EventHandler
    private void thm$onSent(PacketEvent.Sent event) {
        if (event.packet == thm$sending) thm$sent = true;
        if (event.packet instanceof ServerboundSetCarriedItemPacket slot && slot.getSlot() == thm$syncSlot) thm$slotSent = true;
    }

    private void thm$extendScaffold() {
        if (mc.player == null || mc.level == null || (onlyOnClick.get() && !mc.options.keyUse.isDown())) return;
        BlockPos current = thm$applyKeepY(bp).immutable();
        if (thm$interpolate.get() && thm$lastScaffoldBp != null) {
            int dx = current.getX() - thm$lastScaffoldBp.getX();
            int dz = current.getZ() - thm$lastScaffoldBp.getZ();
            int steps = Math.max(Math.abs(dx), Math.abs(dz));
            // Teleports must not turn into an unbounded placement loop.
            if (steps <= 64) for (int i = 1; i < steps; i++) {
                float t = (float) i / steps;
                place(new BlockPos(thm$lastScaffoldBp.getX() + Math.round(dx * t), current.getY(),
                    thm$lastScaffoldBp.getZ() + Math.round(dz * t)));
            }
        }
        thm$lastScaffoldBp = thm$interpolate.get() ? current : null;
        if (thm$lookahead.get()) {
            Vec3 velocity = mc.player.getDeltaMovement();
            if (velocity.horizontalDistance() > 0.05) {
                for (int i = 1; i <= thm$lookaheadTicks.get(); i++) {
                    place(thm$applyKeepY(BlockPos.containing(mc.player.getX() + velocity.x * i,
                        mc.player.getY() - 1, mc.player.getZ() + velocity.z * i)));
                }
            }
        }
    }

    private boolean thm$place(BlockPos pos, FindItemResult item, boolean rotate, int priority, boolean swing, boolean checkEntities) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || !item.found()
            || placementsThisTick >= blocksPerTick.get()) return false;
        BlockPos target = thm$applyKeepY(pos).immutable();
        if (!thm$canPlace(target, item, checkEntities) || !thm$queued.add(target.asLong())) return false;
        Direction side = thm$placeSide(target);
        if (side == null && !airPlace.get()) {
            thm$queued.remove(target.asLong());
            return false;
        }
        if (thm$packetPlace.get() && thm$packetPlaceOnce.get() && thm$packets.isPending(target.asLong())) {
            thm$queued.remove(target.asLong());
            return false;
        }
        Vec3 hitPos = Vec3.atCenterOf(target);
        if (side != null) hitPos = hitPos.add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, side == null ? Direction.UP : side.getOpposite(),
            side == null ? target : target.relative(side), false);
        int swapCost = item.isOffhand() ? 0 : 2;
        if (!thm$packets.reserve(thm$packetLimiter.get(), thm$packetLimit.get(), 2 + swapCost + (rotate ? 1 : 0))) {
            thm$queued.remove(target.asLong());
            return false;
        }
        placementsThisTick++;
        long epoch = thm$epoch;
        var player = mc.player;
        var level = mc.level;
        boolean packet = thm$packetPlace.get();
        Runnable action = () -> {
            try {
                if (!isActive() || epoch != thm$epoch || mc.player != player || mc.level != level
                    || packet != thm$packetPlace.get() || (onlyOnClick.get() && !mc.options.keyUse.isDown()) || !thm$canPlace(target, item, checkEntities)) return;
                if (side != null && level.getBlockState(hit.getBlockPos()).canBeReplaced()) return;
                int previousSlot = player.getInventory().getSelectedSlot();
                InteractionHand hand = item.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                try {
                    if (!item.isOffhand()) thm$selectSlot(item.slot());
                    if (packet) {
                        thm$sending = new ServerboundUseItemOnPacket(hand, hit, 0);
                        thm$sent = false;
                        boolean once = thm$packetPlaceOnce.get();
                        if (once) thm$packets.markSent(target.asLong(), thm$tick);
                        player.connection.send(thm$sending);
                        if (thm$sent) {
                            if (swing) player.swing(hand);
                            else player.connection.send(new ServerboundSwingPacket(hand));
                        }
                    } else {
                        BlockUtils.interact(hit, hand, swing);
                    }
                } finally {
                    if (thm$sending != null && !thm$sent) thm$packets.forget(target.asLong());
                    thm$sending = null;
                    if (!item.isOffhand() && mc.player == player && mc.level == level) thm$selectSlot(previousSlot);
                }
            } finally {
                if (epoch == thm$epoch) thm$queued.remove(target.asLong());
            }
        };
        if (rotate) Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos), priority, action);
        else action.run();
        return true;
    }

    private void thm$selectSlot(int slot) {
        thm$syncSlot = slot;
        thm$slotSent = false;
        mc.player.getInventory().setSelectedSlot(slot);
        ((IMultiPlayerGameMode) mc.gameMode).meteor$syncSelected();
        // Another module's silent hold can bypass the game-mode slot cache.
        if (!thm$slotSent) mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        thm$syncSlot = -1;
    }

    private boolean thm$canPlace(BlockPos pos, FindItemResult item, boolean checkEntities) {
        if (!item.isOffhand() && !item.isHotbar()) return false;
        ItemStack stack = item.isOffhand() ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(item.slot());
        return validItem(stack, pos) && stack.getItem() instanceof BlockItem block
            && mc.level.getWorldBorder().isWithinBounds(pos)
            && mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= 36
            && BlockUtils.canPlaceBlock(pos, checkEntities, block.getBlock());
    }

    private Direction thm$placeSide(BlockPos pos) {
        for (Direction side : Direction.values()) {
            var state = mc.level.getBlockState(pos.relative(side));
            if (!state.canBeReplaced() && state.getFluidState().isEmpty() && !BlockUtils.isClickable(state.getBlock())) return side;
        }
        return null;
    }

    private InteractionHand thm$inertHand() {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = mc.player.getItemInHand(hand);
            if (stack.isEmpty() || stack.is(ItemTags.PICKAXES) || stack.is(Items.TOTEM_OF_UNDYING)) return hand;
        }
        return null;
    }

    private BlockPos thm$applyKeepY(BlockPos pos) {
        return thm$keepY.get() && thm$lockedY != Integer.MIN_VALUE
            ? new BlockPos(pos.getX(), thm$lockedY, pos.getZ()) : pos;
    }

    private void tickScaffold() {
        if (onlyOnClick.get() && !mc.options.keyUse.isDown()) return;

        Vec3 vec = mc.player.position().add(mc.player.getDeltaMovement()).add(0, -0.75, 0);
        if (airPlace.get()) {
            bp.set(vec.x(), vec.y(), vec.z());
        } else {
            Vec3 pos = mc.player.position();
            if (aheadDistance.get() != 0 && !towering() && !mc.level.getBlockState(mc.player.blockPosition().below()).getCollisionShape(mc.level, mc.player.blockPosition()).isEmpty()) {
                Vec3 dir = Vec3.directionFromRotation(0, mc.player.getYRot()).multiply(aheadDistance.get(), 0, aheadDistance.get());
                if (mc.options.keyUp.isDown()) pos = pos.add(dir.x, 0, dir.z);
                if (mc.options.keyDown.isDown()) pos = pos.add(-dir.x, 0, -dir.z);
                if (mc.options.keyLeft.isDown()) pos = pos.add(dir.z, 0, -dir.x);
                if (mc.options.keyRight.isDown()) pos = pos.add(-dir.z, 0, dir.x);
            }
            bp.set(pos.x, vec.y, pos.z);
        }
        if (mc.options.keyShift.isDown() && !mc.options.keyJump.isDown() && mc.player.getY() + vec.y > -1) {
            bp.setY(bp.getY() - 1);
        }
        if (bp.getY() >= mc.player.blockPosition().getY()) {
            bp.setY(mc.player.blockPosition().getY() - 1);
        }
        bp.set(thm$applyKeepY(bp));
        BlockPos targetBlock = bp.immutable();

        if (!airPlace.get() && (BlockUtils.getPlaceSide(bp) == null)) {
            Vec3 pos = mc.player.position();
            pos = pos.add(0, -0.98f, 0);
            pos.add(mc.player.getDeltaMovement());

            List<BlockPos> blockPosArray = new ArrayList<>();
            for (int x = (int) (mc.player.getX() - placeRange.get()); x < mc.player.getX() + placeRange.get(); x++) {
                for (int z = (int) (mc.player.getZ() - placeRange.get()); z < mc.player.getZ() + placeRange.get(); z++) {
                    for (int y = (int) Math.max(mc.level.getMinY(), mc.player.getY() - placeRange.get()); y < Math.min(mc.level.getHeight(), mc.player.getY() + placeRange.get()); y++) {
                        bp.set(x, y, z);
                        if (BlockUtils.getPlaceSide(bp) == null) continue;
                        if (!BlockUtils.canPlace(bp)) continue;
                        if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(bp.relative(BlockUtils.getClosestPlaceSide(bp)))) > 36)
                            continue;
                        blockPosArray.add(new BlockPos(bp));
                    }
                }
            }
            if (blockPosArray.isEmpty()) return;

            blockPosArray.sort(Comparator.comparingDouble(blockPos -> blockPos.distSqr(targetBlock)));

            bp.set(blockPosArray.getFirst());
        }

        if (airPlace.get()) {
            List<BlockPos> blocks = new ArrayList<>();
            for (int x = (int) (bp.getX() - radius.get()); x <= bp.getX() + radius.get(); x++) {
                for (int z = (int) (bp.getZ() - radius.get()); z <= bp.getZ() + radius.get(); z++) {
                    BlockPos blockPos = BlockPos.containing(x, bp.getY(), z);
                    if (mc.player.position().distanceTo(Vec3.atCenterOf(blockPos)) <= radius.get() || (x == bp.getX() && z == bp.getZ())) {
                        blocks.add(blockPos);
                    }
                }
            }

            if (!blocks.isEmpty()) {
                blocks.sort(Comparator.comparingDouble(PlayerUtils::squaredDistanceTo));
                int counter = 0;
                for (BlockPos block : blocks) {
                    if (place(block)) {
                        counter++;
                    }

                    if (counter >= blocksPerTick.get()) {
                        break;
                    }
                }
            }
        } else {
            place(bp);
        }

        FindItemResult result = InvUtils.findInHotbar(itemStack -> validItem(itemStack, bp));
        if (fastTower.get() && mc.options.keyJump.isDown() && !mc.options.keyShift.isDown() && result.found() && (autoSwitch.get() || result.getHand() != null)) {
            Vec3 velocity = mc.player.getDeltaMovement();
            AABB playerBox = mc.player.getBoundingBox();
            if (!mc.level.getBlockCollisions(mc.player, playerBox.move(0, 1, 0)).iterator().hasNext()) {
                // If there is no block above the player: move the player up, so he can place another block
                if (whileMoving.get() || !PlayerUtils.isMoving()) {
                    velocity = new Vec3(velocity.x, towerSpeed.get(), velocity.z);
                }
                mc.player.setDeltaMovement(velocity);
            } else {
                // If there is a block above the player: move the player down, so he's on top of the placed block
                mc.player.setDeltaMovement(velocity.x, Math.ceil(mc.player.getY()) - mc.player.getY(), velocity.z);
                mc.player.setOnGround(true);
            }
        }
    }

    public boolean scaffolding() {
        return isActive() && (!onlyOnClick.get() || (onlyOnClick.get() && mc.options.keyUse.isDown()));
    }

    public boolean towering() {
        FindItemResult result = InvUtils.findInHotbar(itemStack -> validItem(itemStack, bp));
        return scaffolding() && fastTower.get() && mc.options.keyJump.isDown() && !mc.options.keyShift.isDown() &&
            (whileMoving.get() || !PlayerUtils.isMoving()) && result.found() && (autoSwitch.get() || result.getHand() != null);
    }

    private boolean validItem(ItemStack itemStack, BlockPos pos) {
        if (!(itemStack.getItem() instanceof BlockItem)) return false;

        Block block = ((BlockItem) itemStack.getItem()).getBlock();

        if (!blocksFilter.get().allows(blocks.get().contains(block))) return false;

        if (!Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(mc.level, pos))) return false;
        return !(block instanceof FallingBlock) || !FallingBlock.isFree(mc.level.getBlockState(pos));
    }

    private boolean place(BlockPos bp) {
        bp = thm$applyKeepY(bp).immutable();
        BlockPos target = bp;
        FindItemResult item = InvUtils.findInHotbar(itemStack -> validItem(itemStack, target));
        if (!item.found()) return false;

        if (item.getHand() == null && !autoSwitch.get()) return false;

        if (thm$place(bp, item, rotate.get(), 50, renderSwing.get(), true)) {
            // Render block if was placed
            if (render.get())
                RenderUtils.renderTickingBlock(bp.immutable(), sideColor.get(), lineColor.get(), shapeMode.get(), 0, 8, true, false);
            return true;
        }
        return false;
    }

    /** Bounded acknowledgements keep Netty threads away from world state. */
    public static final class PacketState {
        private final Map<Long, Long> pending = new HashMap<>();
        private final Set<Long> answered = new HashSet<>();
        private int used;

        public synchronized void beginTick() {
            used = 0;
            answered.forEach(pending::remove);
            answered.clear();
        }

        public synchronized boolean reserve(boolean enabled, int limit, int cost) {
            if (enabled && cost > Math.max(0, limit - used)) return false;
            used = (int) Math.min(Integer.MAX_VALUE, (long) used + cost);
            return true;
        }

        public synchronized boolean isPending(long pos) { return pending.containsKey(pos); }
        public synchronized void markSent(long pos, long tick) { pending.put(pos, tick); }
        public synchronized void answer(long pos) { if (pending.containsKey(pos)) answered.add(pos); }
        public synchronized void forget(long pos) { pending.remove(pos); answered.remove(pos); }
        public synchronized Map<Long, Long> snapshot() { return Map.copyOf(pending); }
        public synchronized void clearPending() { pending.clear(); answered.clear(); }
        public synchronized void clear() { clearPending(); used = 0; }
    }
}
