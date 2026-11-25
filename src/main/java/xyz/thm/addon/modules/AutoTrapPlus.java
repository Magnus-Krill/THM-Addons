/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







/*
 * Base module copied from Meteor Client AutoTrap
 * https://github.com/MeteorDevelopment/meteor-client/blob/master/src/main/java/meteordevelopment/meteorclient/systems/modules/combat/AutoTrap.java
 */
package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.LogoutSpots;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.settings.PacketPlacementSettings;
import xyz.thm.addon.interfaces.LogoutSpotsPlayers;
import xyz.thm.addon.interfaces.LogoutSpotsPoseData;
import xyz.thm.addon.settings.DescribedOption;
import xyz.thm.addon.utils.InventoryManager;
import xyz.thm.addon.utils.PacketPlaceTracker;
import xyz.thm.addon.utils.PlacementUtils;
import xyz.thm.addon.utils.RenderUtilsTHM;
import xyz.thm.addon.utils.SwapManager;

import java.util.*;

public class AutoTrapPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLogoutTrap = settings.createGroup("Logout Trap");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("whitelist")
        .description("Blocks to use for trapping.")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Range at which blocks can be placed.")
        .defaultValue(5.2)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> placeWallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("Range in which to place when behind blocks.")
        .defaultValue(5.2)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("How to select the player to target.")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Maximum distance to target players.")
        .defaultValue(6)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks between block placements.")
        .defaultValue(1)
        .visible(this::usesLocalPlacementRate)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place per tick.")
        .defaultValue(1)
        .min(1)
        .visible(this::usesLocalPlacementRate)
        .build()
    );

    private final Setting<TopMode> topPlacement = sgGeneral.add(new EnumSetting.Builder<TopMode>()
        .name("top-blocks")
        .description("Which blocks to place at head height.")
        .defaultValue(TopMode.Full)
        .build()
    );

    private final Setting<HeightMode> heightMode = sgGeneral.add(new EnumSetting.Builder<HeightMode>()
        .name("height-mode")
        .description("Where to build the trap: around feet or around eye height.")
        .defaultValue(HeightMode.Feet)
        .build()
    );

    private final Setting<Boolean> antiStep = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-step")
        .description("Add a second layer over the target.")
        .defaultValue(false)
        .build()
    );

    private final Setting<BuildOrder> buildOrder = sgGeneral.add(new EnumSetting.Builder<BuildOrder>()
        .name("build-order")
        .description("Order to build columns: bottom-to-top or top-to-bottom.")
        .defaultValue(BuildOrder.BottomToTop)
        .build()
    );

    private final Setting<Boolean> selfToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("self-toggle")
        .description("Toggle off after placing all blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate towards blocks when placing.")
        .defaultValue(false)
        .build()
    );
    private final PacketPlacementSettings packet = new PacketPlacementSettings(sgGeneral);

    private final Setting<Boolean> packetOnce = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-place-once")
        .description("Wait for the server before retrying a block.")
        .defaultValue(false)
        .visible(packet::enabled)
        .build()
    );

    private final Setting<GapSide> gapSide = sgGeneral.add(new EnumSetting.Builder<GapSide>()
        .name("Crystal/Anchor gap")
        .description("Leave one feet-level side as air for crystals/anchors.")
        .defaultValue(GapSide.None)
        .build()
    );

    private final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Place head walls first and allow unsupported placements.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> logoutTrap = sgLogoutTrap.add(new BoolSetting.Builder()
        .name("logout-trap")
        .description("Enables trapping of player logout spots.")
        .defaultValue(false)
        .build()
    );

    private final Setting<LogoutTrapMode> logoutTrapMode = sgLogoutTrap.add(new EnumSetting.Builder<LogoutTrapMode>()
        .name("mode")
        .description("How logout spots are trapped.")
        .defaultValue(LogoutTrapMode.Auto)
        .visible(logoutTrap::get)
        .build()
    );

    private final Setting<Keybind> logoutTrapKeybind = sgLogoutTrap.add(new KeybindSetting.Builder()
        .name("manual-keybind")
        .description("Queue known logout spots.")
        .defaultValue(Keybind.none())
        .visible(() -> logoutTrap.get() && logoutTrapMode.get() == LogoutTrapMode.Manual)
        .build()
    );

    private final Setting<Boolean> logoutOnly = sgLogoutTrap.add(new BoolSetting.Builder()
        .name("logout-only")
        .description("Only use logout-trapping logic and ignore normal auto-trap targets.")
        .defaultValue(false)
        .visible(logoutTrap::get)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders queued and recently placed blocks.")
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
        .description("The side color.")
        .defaultValue(new SettingColor(THMAddon.THMSideColor.r, THMAddon.THMSideColor.g, THMAddon.THMSideColor.b, THMAddon.THMSideColor.a))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color.")
        .defaultValue(new SettingColor(THMAddon.THMColor.r, THMAddon.THMColor.g, THMAddon.THMColor.b, THMAddon.THMColor.a))
        .visible(render::get)
        .build()
    );

    private final Setting<Boolean> fade = sgRender.add(new BoolSetting.Builder()
        .name("fade")
        .description("Fades recently placed blocks over time.")
        .defaultValue(true)
        .visible(render::get)
        .build()
    );

    private final Setting<Double> fadeTime = sgRender.add(new DoubleSetting.Builder()
        .name("fade-time")
        .description("How long the fade lasts in seconds.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderMax(2)
        .visible(() -> render.get() && fade.get())
        .build()
    );

    private final Set<BlockPos> placePositions = new LinkedHashSet<>();
    private final Set<BlockPos> scheduled = new HashSet<>();
    private final Map<BlockPos, Long> renderMap = new HashMap<>();
    private final Set<UUID> completedLogoutSpots = new HashSet<>();
    private final Set<UUID> manualLogoutSpots = new HashSet<>();
    private Player target;
    private ClientLevel level;
    private boolean placedAny;
    private boolean logoutManualPressed;
    private long tick;
    private long lastPlacementTick;
    private int batchLimit;

    public AutoTrapPlus() {
        super(THMAddon.PVP, "auto-trap+", "Builds traps around players and logout spots.");
    }

    @Override
    public void onActivate() {
        reset(mc.level);
    }

    @Override
    public void onDeactivate() {
        reset(null);
    }

    private void reset(ClientLevel world) {
        level = world;
        lastPlacementTick = ++tick;
        target = null;
        placedAny = false;
        logoutManualPressed = false;
        placePositions.clear();
        scheduled.clear();
        renderMap.clear();
        completedLogoutSpots.clear();
        manualLogoutSpots.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (level != mc.level) reset(mc.level);
        tick++;
        scheduled.clear();
        placePositions.clear();
        var mode = packet.mode();
        batchLimit = mode == PacketPlacementSettings.Mode.Pace || tick > lastPlacementTick + delay.get()
            ? Math.min(PlacementUtils.placementsPerTick(mode, blocksPerTick.get()), PlacementUtils.remainingPlacementPackets(mode)) : 0;

        FindItemResult block = InvUtils.findInHotbar(stack -> blocks.get().contains(Block.byItem(stack.getItem())));
        boolean logoutWork = logoutTrap.get() && runLogoutTraps(block);

        target = null;
        boolean liveWork = false;
        if (!logoutOnly.get()) {
            for (Player player : getTargets()) {
                if (target == null) target = player;
                AABB body = player.getBoundingBox();
                int eyeY = Mth.floor(player.getEyeY());
                liveWork |= runTrap(body, eyeY, gapPosition(body, eyeY), block);
            }
        }

        if (selfToggle.get() && placedAny && !liveWork && !logoutWork) toggle();
    }

    private boolean usesLocalPlacementRate() {
        return packet.mode() != PacketPlacementSettings.Mode.Pace;
    }

    private boolean runLogoutTraps(FindItemResult block) {
        LogoutSpots module = Modules.get().get(LogoutSpots.class);
        if (!(module instanceof LogoutSpotsPlayers accessor) || !module.isActive()) return false;

        Set<UUID> currentSpots = new HashSet<>();
        boolean manual = logoutTrapMode.get() == LogoutTrapMode.Manual;
        boolean pressed = manual && mc.gui.screen() == null && logoutTrapKeybind.get().isPressed();
        boolean queue = pressed && !logoutManualPressed;
        logoutManualPressed = pressed;
        boolean unfinished = false;

        for (Object value : accessor.thm$getPlayers()) {
            if (!(value instanceof LogoutSpotsPoseData spot)) continue;
            if (Friends.get().get(spot.thm$getName()) != null) continue;
            UUID id = spot.thm$getUuid();
            currentSpots.add(id);
            if (completedLogoutSpots.contains(id)) continue;
            if (queue) manualLogoutSpots.add(id);
            if (manual && !manualLogoutSpots.contains(id)) {
                unfinished = true;
                continue;
            }

            double x = spot.thm$getX(), y = spot.thm$getY(), z = spot.thm$getZ();
            AABB body = new AABB(x, y, z, x + spot.thm$getXWidth(), y + spot.thm$getHeight(), z + spot.thm$getZWidth());
            int eyeY = Mth.floor(body.maxY - 0.18);
            if (runTrap(body, eyeY, null, block)) unfinished = true;
            else {
                completedLogoutSpots.add(id);
                manualLogoutSpots.remove(id);
            }
        }

        completedLogoutSpots.retainAll(currentSpots);
        manualLogoutSpots.retainAll(currentSpots);
        return unfinished;
    }

    private List<Player> getTargets() {
        List<Entity> entities = new ArrayList<>();
        TargetUtils.getList(entities, entity -> {
            if (!(entity instanceof Player player) || entity == mc.player) return false;
            if (player.isSpectator() || !player.isAlive()) return false;
            if (!PlayerUtils.isWithin(entity, targetRange.get())) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (entity instanceof FakePlayerEntity fakePlayer) return !fakePlayer.noHit;
            return EntityUtils.getGameMode(player) == GameType.SURVIVAL;
        }, priority.get(), Integer.MAX_VALUE);

        List<Player> players = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            if (entity instanceof Player p) players.add(p);
        }
        return players;
    }

    private boolean runTrap(AABB body, int eyeY, BlockPos gap, FindItemResult block) {
        HeightMode height = heightMode.get();
        Set<BlockPos> shape = plan(body, eyeY, height, topPlacement.get(), antiStep.get());
        shape.remove(gap);
        boolean unfinished = false;
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : shape) {
            if (!level.getBlockState(pos).canBeReplaced()) continue;
            unfinished = true;
            if (!canPlace(pos) || !inRange(Vec3.atCenterOf(pos))) continue;
            candidates.add(pos);
        }

        boolean air = airPlace.get();
        int feetY = Mth.floor(body.minY + 1e-7);
        int roofY = Mth.ceil(body.maxY - 1e-7);
        int headWallY = height == HeightMode.Eye ? eyeY : Math.min(feetY + 1, roofY - 1);
        candidates.sort(placementOrder(mc.player.getEyePosition(), buildOrder.get(), air, headWallY, roofY));
        placePositions.addAll(candidates);

        if (!block.found() || scheduled.size() >= batchLimit) return unfinished;
        List<BlockPos> unsupported = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (scheduled.size() >= batchLimit) break;
            Direction side = PlacementUtils.getPlaceSide(pos);
            if (side == null && !air) unsupported.add(pos);
            else placeBlock(pos, side, block);
        }
        for (BlockPos pos : unsupported) {
            if (scheduled.size() >= batchLimit) break;
            Direction side = PlacementUtils.getPlaceSide(pos);
            if (side != null) {
                placeBlock(pos, side, block);
                continue;
            }
            Vec3 center = body.getCenter();
            Direction outward = Direction.getApproximateNearest(pos.getX() + 0.5 - center.x, 0, pos.getZ() + 0.5 - center.z);
            if (outward.getAxis().isVertical()) outward = Direction.NORTH;
            Direction[] sides = {outward, outward.getClockWise(), outward.getCounterClockWise(), outward.getOpposite(), Direction.DOWN, Direction.UP};
            BlockPos support = PlacementUtils.findSupportStep(pos, sides, 3, next ->
                (gap == null || !next.atY(gap.getY()).equals(gap))
                    && !body.intersects(new AABB(next)) && canPlace(next) && inRange(Vec3.atCenterOf(next)),
                next -> {
                    Direction supportSide = PlacementUtils.getPlaceSide(next);
                    return supportSide != null && inRange(PlacementUtils.packetPlacementHit(next, supportSide).getLocation());
                });
            if (support != null) placeBlock(support, PlacementUtils.getPlaceSide(support), block);
        }
        return unfinished;
    }

    private boolean canPlace(BlockPos pos) {
        return level.getWorldBorder().isWithinBounds(pos) && level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && BlockUtils.canPlace(pos);
    }

    private void placeBlock(BlockPos pos, Direction side, FindItemResult block) {
        var mode = packet.mode();
        boolean packetOnly = mode.isPacket();
        if (packetOnly && packetOnce.get() && !PacketPlaceTracker.canSend(pos)) return;
        BlockHitResult hit = PlacementUtils.packetPlacementHit(pos, side);
        if (!inRange(hit.getLocation()) || !scheduled.add(pos)) return;

        var player = mc.player;
        var world = level;
        long queuedTick = tick;
        InteractionHand hand = block.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        Runnable action = () -> {
            boolean[] sent = {false};
            try {
                if (!isActive() || queuedTick != tick || mc.player != player || mc.level != world
                    || mode != packet.mode() || PlacementUtils.remainingPlacementPackets(packet.mode()) == 0 || !canPlace(pos)
                    || PlacementUtils.getPlaceSide(pos) != side || side == null && !airPlace.get()
                    || !inRange(hit.getLocation()) || packetOnly && packetOnce.get() && !PacketPlaceTracker.canSend(pos)) return;

                Runnable send = () -> {
                    var stack = block.isOffhand() ? player.getOffhandItem() : player.getInventory().getItem(block.slot());
                    if (!blocks.get().contains(Block.byItem(stack.getItem()))) return;
                    if (packetOnly) {
                        sent[0] = PlacementUtils.sendPlacementPacket(new ServerboundUseItemOnPacket(hand, hit, 0), mode);
                        if (sent[0]) player.connection.send(new ServerboundSwingPacket(hand));
                    } else sent[0] = PlacementUtils.interactPlacement(pos, hit, hand, true);
                };
                if (hand == InteractionHand.OFF_HAND) send.run();
                else InventoryManager.withItem(this, block.slot(), packetOnly, SwapManager.SURROUND, send);
                if (!sent[0]) return;

                if (packetOnly && packetOnce.get()) PacketPlaceTracker.markSent(pos, PacketPlaceTracker.DEFAULT_RESEND_TICKS);
                placedAny = true;
                lastPlacementTick = tick;
                if (render.get()) renderMap.put(pos, System.currentTimeMillis());
            } finally {
                if (!sent[0] && queuedTick == tick) scheduled.remove(pos);
            }
        };
        if (rotate.get()) Rotations.rotate(Rotations.getYaw(hit.getLocation()), Rotations.getPitch(hit.getLocation()), 50, action);
        else action.run();
    }

    static Comparator<BlockPos> placementOrder(Vec3 eye, BuildOrder order, boolean air, int headWallY, int roofY) {
        Comparator<BlockPos> heightOrder = Comparator.comparingInt(BlockPos::getY);
        if (order == BuildOrder.TopToBottom) heightOrder = heightOrder.reversed();
        Comparator<BlockPos> columns = Comparator.<BlockPos>comparingDouble(pos ->
            Mth.square(pos.getX() + 0.5 - eye.x) + Mth.square(pos.getZ() + 0.5 - eye.z)).thenComparing(heightOrder);
        if (!air) return columns;
        return Comparator.<BlockPos>comparingInt(pos -> {
            if (pos.getY() == headWallY) return 0;
            if (pos.getY() == roofY) return 1;
            return 2;
        }).thenComparing(columns);
    }

    static Set<BlockPos> plan(AABB body, int eyeY, HeightMode height, TopMode top, boolean antiStep) {
        int feetY = Mth.floor(body.minY + 1e-7);
        int roofY = Mth.ceil(body.maxY - 1e-7);
        Set<BlockPos> feet = PlacementUtils.horizontalFootprint(body, feetY);
        Set<BlockPos> result = new LinkedHashSet<>();
        for (BlockPos foot : feet) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos edge = foot.relative(side);
                if (feet.contains(edge)) continue;
                if (height == HeightMode.Eye) {
                    result.add(edge.atY(eyeY));
                } else {
                    result.add(edge.below());
                    result.add(edge);
                    if (roofY > feetY + 1 && (top == TopMode.Full || top == TopMode.Face)) result.add(edge.above());
                }
            }
            if (height == HeightMode.Feet) result.add(foot.below());
            if (height == HeightMode.Eye || top == TopMode.Full || top == TopMode.Top) result.add(foot.atY(roofY));
            if (antiStep) result.add(foot.atY(roofY + 1));
        }
        return result;
    }

    private boolean inRange(Vec3 click) {
        Vec3 eye = mc.player.getEyePosition();
        double distance = eye.distanceToSqr(click);
        if (distance > Mth.square(placeRange.get())) return false;
        if (distance <= Mth.square(placeWallsRange.get())) return true;
        BlockHitResult trace = level.clip(new ClipContext(eye, click, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return trace.getType() == HitResult.Type.MISS || trace.getLocation().distanceToSqr(click) < 1e-6;
    }

    private BlockPos gapPosition(AABB body, int eyeY) {
        Vec3 center = body.getCenter();
        Vec3 offset = mc.player.position().subtract(center);
        Direction side = switch (gapSide.get()) {
            case None -> null;
            case North -> Direction.NORTH;
            case South -> Direction.SOUTH;
            case East -> Direction.EAST;
            case West -> Direction.WEST;
            case TowardPlayer -> Direction.getApproximateNearest(offset.x, 0, offset.z);
        };
        if (side == null || side.getAxis().isVertical()) return null;
        int y = heightMode.get() == HeightMode.Eye ? eyeY : Mth.floor(body.minY + 1e-7);
        return switch (side) {
            case NORTH -> new BlockPos(Mth.floor(center.x), y, Mth.floor(body.minZ + 1e-7) - 1);
            case SOUTH -> new BlockPos(Mth.floor(center.x), y, Mth.ceil(body.maxZ - 1e-7));
            case WEST -> new BlockPos(Mth.floor(body.minX + 1e-7) - 1, y, Mth.floor(center.z));
            case EAST -> new BlockPos(Mth.ceil(body.maxX - 1e-7), y, Mth.floor(center.z));
            default -> null;
        };
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        RenderUtilsTHM.renderBlocks(event, placePositions, sideColor.get(), lineColor.get(), shapeMode.get());

        if (renderMap.isEmpty()) return;

        renderMap.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > fadeTime.get() * 1000);
        renderMap.forEach((pos, time) -> {
            double progress = 1.0;
            if (fade.get()) {
                long alive = System.currentTimeMillis() - time;
                progress = 1.0 - Mth.clamp((double) alive / (fadeTime.get() * 1000), 0.0, 1.0);
            }

            RenderUtilsTHM.renderBlockFaded(event, pos, sideColor.get(), lineColor.get(), shapeMode.get(), progress);
        });
    }

    public enum TopMode implements DescribedOption {
        Full,
        Top,
        Face,
        None;

        @Override
        public String description() {
            return switch (this) {
                case Full -> "Place the roof and face-level walls.";
                case Top -> "Place only the roof above the target.";
                case Face -> "Place only face-level walls.";
                case None -> "Skip the roof and face-level walls.";
            };
        }
    }

    public enum HeightMode implements DescribedOption {
        Feet,
        Eye;

        @Override
        public String description() {
            return switch (this) {
                case Feet -> "Build the trap from the target's feet.";
                case Eye -> "Build the trap at the target's eye height.";
            };
        }
    }

    public enum GapSide implements DescribedOption {
        None,
        TowardPlayer,
        North,
        South,
        East,
        West;

        @Override
        public String description() {
            return switch (this) {
                case None -> "Leave no gap in the trap.";
                case TowardPlayer -> "Leave a gap facing you.";
                case North -> "Leave a gap to the north.";
                case South -> "Leave a gap to the south.";
                case East -> "Leave a gap to the east.";
                case West -> "Leave a gap to the west.";
            };
        }
    }

    public enum BuildOrder implements DescribedOption {
        BottomToTop,
        TopToBottom;

        @Override
        public String description() {
            return switch (this) {
                case BottomToTop -> "Place lower trap blocks first.";
                case TopToBottom -> "Place upper trap blocks first.";
            };
        }
    }

    public enum LogoutTrapMode implements DescribedOption {
        Auto,
        Manual;

        @Override
        public String description() {
            return switch (this) {
                case Auto -> "Trap logout spots automatically.";
                case Manual -> "Trap logout spots when the keybind is pressed.";
            };
        }
    }
}
