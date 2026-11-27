/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.joml.Vector3d;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import xyz.thm.addon.utils.MotionExtrapolation;
import xyz.thm.addon.utils.THMUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.settings.PacketPlacementSettings;
import xyz.thm.addon.settings.DescribedOption;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.DeepSwapManager;
import xyz.thm.addon.utils.InventoryManager;
import xyz.thm.addon.utils.PingTracker;
import xyz.thm.addon.utils.PlacementUtils;
import xyz.thm.addon.utils.RenderUtilsTHM;
import xyz.thm.addon.utils.SwapManager;
import xyz.thm.addon.utils.ThmMembers;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Java/Meteor port of Lambda's CrystalAura at e8eb261c9d605cc2312ea05063d0cb74d005ce46, with additions from Open Zio (MIT). */
public class CrystalAura extends Module {
    private static final double FACE_PLACE_DAMAGE = 0.6;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Placement");
    private final SettingGroup sgBreak = settings.createGroup("Exploding");
    private final SettingGroup sgPrediction = settings.createGroup("Prediction");
    private final SettingGroup sgTarget = settings.createGroup("Targeting");
    private final SettingGroup sgRender = settings.createGroup("Rendering");
    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate").description("Rotate before crystal actions.").defaultValue(true).build());

    private final Setting<UpdateMode> updateMode = sgGeneral.add(new EnumSetting.Builder<UpdateMode>()
        .name("update-mode").description("When to evaluate crystal opportunities.").defaultValue(UpdateMode.Ticked).onChanged(mode -> updateScheduler()).build());

    private final Setting<Integer> updateDelay = sgGeneral.add(new IntSetting.Builder()
        .name("update-delay").description("Milliseconds between asynchronous evaluations.").defaultValue(25).min(5).sliderRange(5, 200).visible(() -> updateMode.get() == UpdateMode.Async).build());

    private final Setting<Integer> maxUpdatesPerFrame = sgGeneral.add(new IntSetting.Builder()
        .name("max-updates-per-frame").description("Limit queued evaluations each frame.").defaultValue(5).min(1).max(20).sliderRange(1, 20).visible(() -> updateMode.get() == UpdateMode.Async).build());

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug").description("Show confirmed crystal removals per second.").defaultValue(false).build());

    private final Setting<Boolean> multitask = sgGeneral.add(new BoolSetting.Builder()
        .name("multitask").description("Keep placing and attacking while using an offhand item.").defaultValue(false).build());

    private final Setting<Integer> rotationPriority = sgGeneral.add(new IntSetting.Builder()
        .name("rotation-priority").description("Priority for crystal rotations.").defaultValue(50).min(0).sliderRange(0, 100).visible(rotate::get).build());

    private final Setting<Boolean> skipFacing = sgGeneral.add(new BoolSetting.Builder()
        .name("skip-facing-rotations").description("Act immediately when the server rotation already faces the target.").defaultValue(true).visible(rotate::get).build());

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range").description("Maximum crystal placement distance.").defaultValue(4.6).min(1).max(7).sliderRange(1, 7).build());

    private final Setting<Double> placeWallRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-wall-range").description("Placement distance without line of sight.").defaultValue(7.0).min(0).max(7).sliderRange(0, 7).build());

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay").description("Milliseconds between placement attempts.").defaultValue(50).min(0).sliderRange(0, 1000).build());

    private final PacketPlacementSettings packet = new PacketPlacementSettings(sgPlace);

    private final Setting<Boolean> swap = sgPlace.add(new BoolSetting.Builder()
        .name("swap").description("Swap to crystals when needed.").defaultValue(true).build());

    private final Setting<SwapHand> swapHand = sgPlace.add(new EnumSetting.Builder<SwapHand>()
        .name("swap-hand").description("Preferred hand for crystal placement.").defaultValue(SwapHand.MainHand).visible(swap::get).build());

    private final Setting<Boolean> silentSwap = sgPlace.add(new BoolSetting.Builder()
        .name("silent-swap").description("Restore the previous slot after placing.").defaultValue(true).visible(() -> swap.get() && swapHand.get() == SwapHand.MainHand).build());

    private final Setting<Priority> priorityMode = sgPlace.add(new EnumSetting.Builder<Priority>()
        .name("crystal-priority").description("How to score crystal opportunities.").defaultValue(Priority.Damage).build());

    private final Setting<Double> minDamageAdvantage = sgPlace.add(new DoubleSetting.Builder()
        .name("min-damage-advantage").description("Minimum target damage minus self damage.").defaultValue(4.0).min(1).sliderRange(1, 10).visible(() -> priorityMode.get() == Priority.Advantage).build());

    private final Setting<Double> minTargetDamage = sgPlace.add(new DoubleSetting.Builder()
        .name("min-target-damage").description("Minimum damage to the target.").defaultValue(8.0).min(0).sliderRange(0, 20).build());

    private final Setting<Boolean> lethalFinisher = sgPlace.add(new BoolSetting.Builder()
        .name("lethal-finisher").description("Allow lethal hits below the minimum target damage or advantage.").defaultValue(true).build());

    private final Setting<Boolean> facePlace = sgPlace.add(new BoolSetting.Builder()
        .name("face-place").description("Accept low damage against weak or low-armor targets.").defaultValue(false).build());

    private final Setting<Double> facePlaceHealth = sgPlace.add(new DoubleSetting.Builder()
        .name("face-place-health").description("Target health that enables face placing.").defaultValue(4.0).min(0).sliderRange(0, 36).visible(facePlace::get).build());

    private final Setting<Integer> facePlaceArmor = sgPlace.add(new IntSetting.Builder()
        .name("face-place-armor").description("Armor durability in percent that enables face placing.").defaultValue(10).range(0, 100).sliderRange(0, 100).visible(facePlace::get).build());

    private final Setting<Double> maxSelfDamage = sgPlace.add(new DoubleSetting.Builder()
        .name("max-self-damage").description("Maximum damage to yourself.").defaultValue(8.0).min(0).sliderRange(0, 36).build());

    private final Setting<Double> minPlaceHealth = sgPlace.add(new DoubleSetting.Builder()
        .name("min-place-health").description("Health remaining after crystal damage.").defaultValue(5.0).min(0).sliderRange(0, 36).build());

    private final Setting<Boolean> preventDeath = sgPlace.add(new BoolSetting.Builder()
        .name("prevent-death").description("Reject lethal crystal damage.").defaultValue(true).build());

    private final Setting<Boolean> oldPlace = sgPlace.add(new BoolSetting.Builder()
        .name("1.12-placement").description("Require two air blocks above the base.").defaultValue(false).build());

    private final Setting<Boolean> support = sgPlace.add(new BoolSetting.Builder()
        .name("support").description("Place obsidian when no usable crystal base exists.").defaultValue(false).build());

    private final Setting<Integer> supportDelay = sgPlace.add(new IntSetting.Builder()
        .name("support-delay").description("Ticks before placing a crystal on new support.").defaultValue(1).min(0).sliderRange(0, 20).visible(support::get).build());

    private final Setting<Double> explodeRange = sgBreak.add(new DoubleSetting.Builder()
        .name("explode-range").description("Maximum crystal attack distance.").defaultValue(3.0).min(1).max(7).sliderRange(1, 7).build());

    private final Setting<Integer> explodeDelay = sgBreak.add(new IntSetting.Builder()
        .name("explode-delay").description("Milliseconds between crystal attacks.").defaultValue(10).min(0).sliderRange(0, 1000).build());

    private final Setting<Double> explodeWallRange = sgBreak.add(new DoubleSetting.Builder()
        .name("explode-wall-range").description("Attack distance without line of sight.").defaultValue(7.0).min(0).max(7).sliderRange(0, 7).build());

    private final Setting<Integer> ticksExisted = sgBreak.add(new IntSetting.Builder()
        .name("ticks-existed").description("Client ticks a crystal must exist before attacking it.").defaultValue(0).range(0, 20).sliderRange(0, 20).build());

    private final Setting<Integer> attackFactor = sgBreak.add(new IntSetting.Builder()
        .name("attack-factor").description("Also attack the next entity IDs after each crystal.").defaultValue(1).range(1, 5).sliderRange(1, 5).build());

    private final Setting<Integer> swapPenalty = sgBreak.add(new IntSetting.Builder()
        .name("swap-penalty").description("Ticks to wait after a slot change before attacking.").defaultValue(0).range(0, 10).sliderRange(0, 10).build());

    private final Setting<Boolean> iFrameSync = sgBreak.add(new BoolSetting.Builder()
        .name("i-frame-sync").description("Hold attacks that cannot beat the target's last hit while it is invulnerable.").defaultValue(false).build());

    private final Setting<Boolean> mineIgnore = sgBreak.add(new BoolSetting.Builder()
        .name("mine-ignore").description("Treat nearly mined blocks as air for damage.").defaultValue(false).build());

    private final Setting<Boolean> placeAfterBreak = sgBreak.add(new BoolSetting.Builder()
        .name("place-after-break").description("Place the next crystal in the same tick as the attack.").defaultValue(false).build());

    private final Setting<Boolean> antiWeakness = sgBreak.add(new BoolSetting.Builder()
        .name("anti-weakness").description("Attack with a hotbar weapon while weakened.").defaultValue(false).build());

    private final Setting<Boolean> extrapolation = sgPrediction.add(new BoolSetting.Builder()
        .name("extrapolation").description("Aim at where moving targets will be.").defaultValue(false).build());

    private final Setting<Integer> extrapolationTicks = sgPrediction.add(new IntSetting.Builder()
        .name("extrapolation-ticks").description("Ticks to predict ahead.").defaultValue(4).range(1, 8).sliderRange(1, 8).visible(extrapolation::get).build());

    private final Setting<Boolean> placeOnBreak = sgPrediction.add(new BoolSetting.Builder()
        .name("place-on-break").description("Place again in the same frame a crystal breaks.").defaultValue(false).build());

    private final Setting<Boolean> autoMineSync = sgPrediction.add(new BoolSetting.Builder()
        .name("auto-mine-sync").description("Coordinate crystal attacks with Speedmine's Auto Mine.")
        .defaultValue(false).onChanged(enabled -> this.miningOpenings.clear()).build());

    private final Setting<PredictionMode> prediction = sgPrediction.add(new EnumSetting.Builder<PredictionMode>()
        .name("prediction").description("When to predict unconfirmed crystal IDs.").defaultValue(PredictionMode.None).build());

    private final Setting<Integer> packetPredictions = sgPrediction.add(new IntSetting.Builder()
        .name("packet-predictions").description("Predicted attacks following a crystal spawn.").defaultValue(1).min(0).max(20).sliderRange(0, 20).visible(() -> prediction.get().onPacket).build());

    private final Setting<Boolean> explodeOnPacket = sgPrediction.add(new BoolSetting.Builder()
        .name("explode-on-packet").description("Attack a safe crystal as it spawns.").defaultValue(false).visible(() -> prediction.get() != PredictionMode.None).build());

    private final Setting<Boolean> placePostPause = sgPrediction.add(new BoolSetting.Builder()
        .name("place-post-pause").description("Restart placement delay after a crystal spawns.").defaultValue(false).visible(() -> prediction.get() != PredictionMode.None).build());

    private final Setting<Boolean> postPacketPlace = sgPrediction.add(new BoolSetting.Builder()
        .name("post-packet-place").description("Allow immediate placement after a crystal spawns.").defaultValue(true).visible(() -> prediction.get() == PredictionMode.Tick && !placePostPause.get()).build());

    private final Setting<Integer> placePredictions = sgPrediction.add(new IntSetting.Builder()
        .name("place-predictions").description("Predicted attacks after placing a crystal.").defaultValue(4).min(1).max(20).sliderRange(1, 20).visible(() -> prediction.get().onPlace).build());

    private final Setting<Integer> packetLifetime = sgPrediction.add(new IntSetting.Builder()
        .name("packet-lifetime").description("Milliseconds before the last entity ID expires.").defaultValue(500).min(50).sliderRange(50, 1000).visible(() -> prediction.get().onPlace).build());

    private final Setting<Double> targetRange = sgTarget.add(new DoubleSetting.Builder()
        .name("targeting-range").description("Maximum target distance.").defaultValue(10.0).min(1).max(16).sliderRange(1, 16).build());

    private final Setting<Set<EntityType<?>>> entityTypes = sgTarget.add(new EntityTypeListSetting.Builder()
        .name("targets").description("Entity types to attack.")
        .defaultValue(BuiltInRegistries.ENTITY_TYPE.stream().filter(type -> type == EntityTypes.PLAYER || type.getCategory() == MobCategory.MONSTER).collect(Collectors.toSet())).build());

    private final Setting<TargetPriority> targetPriority = sgTarget.add(new EnumSetting.Builder<TargetPriority>()
        .name("target-priority").description("How to select the current target.").defaultValue(TargetPriority.Distance).build());

    private final Setting<Integer> fov = sgTarget.add(new IntSetting.Builder()
        .name("fov-limit").description("Maximum angle from your crosshair.").defaultValue(180).min(5).max(180).sliderRange(5, 180).visible(() -> targetPriority.get() == TargetPriority.Fov).build());

    private final Setting<Boolean> targetNamed = sgTarget.add(new BoolSetting.Builder()
        .name("target-named-entities").description("Target named non-player entities.").defaultValue(false).build());

    private final Setting<Boolean> targetTamed = sgTarget.add(new BoolSetting.Builder()
        .name("target-tamed-entities").description("Target tamed entities.").defaultValue(false).build());

    private final Setting<Boolean> targetOwned = sgTarget.add(new BoolSetting.Builder()
        .name("owned").description("Include pets owned by you.").defaultValue(false).visible(targetTamed::get).build());

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("rendering").description("Draw recent crystal placements.").defaultValue(true).build());

    private final Setting<Boolean> renderPlacements = sgRender.add(new BoolSetting.Builder()
        .name("placement-rendering").description("Draw the targeted placement base.").defaultValue(true).visible(render::get).build());

    private final Setting<Double> animationSpeed = sgRender.add(new DoubleSetting.Builder()
        .name("animation-speed").description("How fast the box follows the target.").defaultValue(12).min(1).sliderRange(1, 30).visible(() -> render.get() && renderPlacements.get()).build());

    private final Setting<Boolean> renderDamage = sgRender.add(new BoolSetting.Builder()
        .name("render-damage").description("Show the expected target damage.").defaultValue(true).visible(() -> render.get() && renderPlacements.get()).build());

    private final Setting<SettingColor> primaryColorLine = sgRender.add(new ColorSetting.Builder()
        .name("primary-outline").description("Color at the top of the placement box.").defaultValue(new SettingColor(THMAddon.THMColor.getPacked())).visible(render::get).build());

    private final Setting<SettingColor> secondaryColorLine = sgRender.add(new ColorSetting.Builder()
        .name("secondary-outline").description("Color at the bottom of the placement box.").defaultValue(new SettingColor(THMAddon.THMColor.getPacked())).visible(render::get).build());

    private final Setting<SettingColor> primaryColor = sgRender.add(new ColorSetting.Builder()
        .name("primary-fill").description("Color at the top of the placement box.").defaultValue(new SettingColor(THMAddon.THMSideColor.getPacked())).visible(render::get).build());

    private final Setting<SettingColor> secondaryColor = sgRender.add(new ColorSetting.Builder()
        .name("secondary-fill").description("Color at the bottom of the placement box.").defaultValue(new SettingColor(THMAddon.THMSideColor.getPacked())).visible(render::get).build());

    private final Map<BlockPos, Opportunity> blueprint = new LinkedHashMap<>();
    private final Color fillTop = new Color(), fillBottom = new Color(), lineTop = new Color(), lineBottom = new Color();
    private final Vector3d damagePos = new Vector3d();
    private double boxX, boxY, boxZ, boxDamage;
    private float boxFade;
    private long lastFrameNs;
    private final SequencedSet<BlockPos> miningOpenings = new LinkedHashSet<>(2);
    private final Predicate<BlockPos> airAt = pos -> mc.level.getBlockState(pos).isAir();
    private final ArrayDeque<Long> removals = new ArrayDeque<>();
    private final AtomicInteger frameBudget = new AtomicInteger();
    private ScheduledExecutorService scheduler;
    private volatile long generation;
    private LivingEntity currentTarget;
    private AABB targetBox;
    private final Map<BlockPos, Long> othersMining = new HashMap<>();
    private final Set<BlockPos> mined = new HashSet<>();
    private LivingEntity lastHitTarget;
    private double lastHitDamage;
    private long lastHitMs, lastSwapMs;
    private boolean attacking;
    private Opportunity activeOpportunity;
    private BlockPos lastHit, lastPlace;
    private long lastPlaceMs, lastExplodeMs, lastSpawnMs, lastUpdateMs, lastDebugMs;
    private long supportRetryAfterMs;
    private int supportTicks;
    private int lastEntityId;
    private boolean waitingForCrystal, safeToPlaceInstantly, actionPending, sentRotation;
    private float sentYaw, sentPitch;
    private float holdYaw, holdPitch;
    private boolean holding, placeSpawned;
    private int lastAttackId;
    private long lastAttackMs;
    // Debug: entity ID -> {spawn ms, first attack ms}; sums and counts for place->spawn, spawn->attack, attack->removal.
    private final Map<Integer, long[]> debugTimes = new HashMap<>();
    private final long[] debugLagSum = new long[3];
    private final int[] debugLagCount = new int[3];
    private int debugPlaces, debugAttacks, debugWaits, debugIFrames, debugIdle;

    public CrystalAura() {
        super(THMAddon.PVP, "thmcrystal-aura", "Crystal placement and attack automation.");
    }

    @Override
    public void onActivate() {
        reset();
        for (Entity entity : mc.level.entitiesForRendering()) lastEntityId = Math.max(lastEntityId, entity.getId());
        updateScheduler();
    }

    @Override
    public void onDeactivate() {
        xyz.thm.addon.utils.DeepSwapManager.get().restore(this);
        xyz.thm.addon.utils.SwapManager.get().release(this);
        stopScheduler();
        reset();
    }

    private void reset() {
        blueprint.clear();
        miningOpenings.clear();
        removals.clear();
        currentTarget = null;
        targetBox = null;
        othersMining.clear();
        mined.clear();
        lastHitTarget = null;
        lastHitMs = lastSwapMs = 0;
        activeOpportunity = null;
        lastHit = lastPlace = null;
        lastPlaceMs = lastExplodeMs = lastSpawnMs = lastUpdateMs = 0;
        lastEntityId = 0;
        supportTicks = 0;
        supportRetryAfterMs = 0;
        waitingForCrystal = safeToPlaceInstantly = actionPending = sentRotation = holding = placeSpawned = false;
        lastAttackId = 0;
        debugTimes.clear();
        resetDebugCounters();
    }

    private void resetDebugCounters() {
        Arrays.fill(debugLagSum, 0);
        Arrays.fill(debugLagCount, 0);
        debugPlaces = debugAttacks = debugWaits = debugIFrames = debugIdle = 0;
    }

    private void debugLag(int stage, long ms) {
        debugLagSum[stage] += ms;
        debugLagCount[stage]++;
    }

    private long debugAverage(int stage) {
        return debugLagCount[stage] == 0 ? -1 : debugLagSum[stage] / debugLagCount[stage];
    }

    private void stopScheduler() {
        generation++;
        if (scheduler != null) scheduler.shutdownNow();
        scheduler = null;
        frameBudget.set(0);
    }

    private void updateScheduler() {
        stopScheduler();
        if (!isActive() || updateMode.get() != UpdateMode.Async) return;
        long epoch = generation;
        frameBudget.set(maxUpdatesPerFrame.get());
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "THM Crystal Aura");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> {
            // Only enqueue bounded work; world access stays on the client thread.
            if (frameBudget.getAndUpdate(value -> Math.max(0, value - 1)) == 0) return;
            mc.execute(() -> {
                if (generation == epoch && isActive() && updateMode.get() == UpdateMode.Async) tickAura();
            });
        }, 1, 1, TimeUnit.MILLISECONDS);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        stopScheduler();
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (supportTicks > 0) supportTicks--;
        if (updateMode.get() == UpdateMode.Async && scheduler == null) updateScheduler();
        if (updateMode.get() == UpdateMode.Ticked) tickAura();
        long now = System.currentTimeMillis();
        while (!removals.isEmpty() && now - removals.peekFirst() >= 3000) removals.removeFirst();
        if (debug.get() && now - lastDebugMs >= 1000) {
            info("Crystals/s %.1f | places %d attacks %d waits %d iframe %d idle %d | place>spawn %dms spawn>hit %dms hit>gone %dms | ping %dms",
                removals.size() / 3.0, debugPlaces, debugAttacks, debugWaits, debugIFrames, debugIdle, debugAverage(0), debugAverage(1), debugAverage(2), PingTracker.rtt());
            resetDebugCounters();
            lastDebugMs = now;
        }
    }

    @EventHandler
    private void onSpawn(EntityAddedEvent event) {
        lastEntityId = Math.max(lastEntityId, event.entity.getId());
        lastSpawnMs = System.currentTimeMillis();
        if (event.entity instanceof EndCrystal spawned && baseOf(spawned).equals(lastPlace)) placeSpawned = true;
        if (!(event.entity instanceof EndCrystal crystal) || !canAct()) return;
        BlockPos base = baseOf(crystal);
        if (debug.get() && base.equals(lastPlace)) {
            if (debugTimes.size() > 64) debugTimes.clear();
            debugTimes.put(crystal.getId(), new long[] {lastSpawnMs, 0});
            debugLag(0, lastSpawnMs - lastPlaceMs);
        }
        Opportunity opportunity = blueprint.get(base);
        if (opportunity == null) return;
        opportunity.crystal = crystal;
        if (prediction.get() == PredictionMode.None || !safeDamage(opportunity.position())) return;
        if (opportunity.priority < minDamageAdvantage.get() && lastPlace != null && base.distSqr(lastPlace) > 2.5) return;
        if (explodeOnPacket.get()) attackCrystal(opportunity);
        if (prediction.get().onPacket) predictAttacks(base, packetPredictions.get());
        if (placePostPause.get()) lastPlaceMs = lastSpawnMs;
        if (postPacketPlace.get() && prediction.get() == PredictionMode.Tick && !placePostPause.get()) safeToPlaceInstantly = true;
    }

    @EventHandler
    private void onRemoval(EntityRemovedEvent event) {
        if (!(event.entity instanceof EndCrystal crystal)) return;
        long[] times = debugTimes.remove(crystal.getId());
        if (times != null && times[1] != 0) debugLag(2, System.currentTimeMillis() - times[1]);
        Opportunity opportunity = blueprint.get(baseOf(crystal));
        if (opportunity != null && opportunity.crystal == crystal) {
            opportunity.crystal = null;
            // Meteor posts this before the crystal leaves the world, so the space check skips it.
            if (placeOnBreak.get() && canAct() && !actionPending && currentTarget != null) place(opportunity, crystal);
        }
        if (removals.size() == 10000) removals.removeFirst();
        removals.addLast(System.currentTimeMillis());
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!mineIgnore.get() || !(event.packet instanceof ClientboundBlockDestructionPacket crack)) return;
        BlockPos pos = crack.getPos().immutable();
        int stage = crack.getProgress();
        long now = System.currentTimeMillis();
        mc.execute(() -> {
            if (stage >= 8 && stage <= 9) othersMining.put(pos, now);
            else othersMining.remove(pos);
        });
    }

    /** Runs before Meteor's rotation handler and keeps the last aim, so the next action needs no new rotation. */
    @EventHandler(priority = EventPriority.HIGH)
    private void onSendMovement(SendMovementPacketsEvent.Pre event) {
        if (holding && rotate.get() && skipFacing.get() && !actionPending && currentTarget != null) {
            Rotations.rotate(holdYaw, holdPitch, rotationPriority.get(), null);
        }
    }

    @EventHandler
    private void onSent(PacketEvent.Sent event) {
        // Anti-weakness swaps right before its own attack, so those swaps cannot count.
        if (event.packet instanceof ServerboundSetCarriedItemPacket && !attacking) lastSwapMs = System.currentTimeMillis();
        if (event.packet instanceof ServerboundMovePlayerPacket move && move.hasRotation()) {
            sentYaw = move.getYRot(0);
            sentPitch = move.getXRot(0);
            sentRotation = true;
        }
    }

    private void updateMined() {
        mined.clear();
        if (!mineIgnore.get()) return;
        long now = System.currentTimeMillis();
        othersMining.values().removeIf(time -> now - time > 1000);
        BlockPos self = mc.player.blockPosition();
        for (BlockPos pos : othersMining.keySet()) if (pos.distSqr(self) <= 144) mined.add(pos);
        Speedmine speedmine = Modules.get().get(Speedmine.class);
        if (speedmine != null && speedmine.isActive()) speedmine.addNearlyBroken(0.8, mined);
    }

    private boolean canAct() {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) return false;
        InventoryManager inventory = InventoryManager.getInstance();
        if (multitask.get() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) return true;
        return !mc.player.isUsingItem() && !inventory.isEating()
            && inventory.getCurrentPriority() <= InventoryManager.Priority.NORMAL;
    }

    static CrystalAura activeMiningSync() {
        CrystalAura aura = Modules.get().get(CrystalAura.class);
        return aura != null && aura.isActive() && aura.autoMineSync.get() ? aura : null;
    }

    void awaitMiningOpening(BlockPos pos) {
        if (miningOpenings.add(pos.immutable()) && miningOpenings.size() > 2) miningOpenings.removeFirst();
    }

    void onMiningOpeningConfirmed(BlockPos pos) {
        if (miningOpenings.remove(pos)) lastUpdateMs = 0;
    }

    BlockPos findMiningOpening(List<BlockPos> candidates) {
        if (candidates.isEmpty() || !canAct()) return null;
        LivingEntity target = selectTarget();
        if (target == null) return null;
        boolean haveCrystal = mc.player.isHolding(Items.END_CRYSTAL) || swap.get() && InvUtils.find(Items.END_CRYSTAL).found();
        for (BlockPos opening : candidates) {
            if (mc.level.getBlockState(opening).is(Blocks.BEDROCK)) continue;
            BlockPos base = opening.below();
            if (improvesCrystalAttack(target, base, opening, haveCrystal)) return opening;
            if (oldPlace.get() && improvesCrystalAttack(target, base.below(), opening, haveCrystal)) return opening;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (improvesCrystalAttack(target, base.relative(side), opening, haveCrystal)) return opening;
            }
        }
        return null;
    }

    private boolean improvesCrystalAttack(LivingEntity target, BlockPos base, BlockPos opening, boolean haveCrystal) {
        if (!isCrystalBase(base)) return false;
        Vec3 position = crystalPosition(base);
        Vec3 eye = mc.player.getEyePosition();
        if (eye.distanceTo(position) > Math.max(placeRange.get(), explodeRange.get())) return false;

        var occupants = mc.level.getEntities((Entity) null, crystalBox(base), entity -> !entity.isSpectator());
        for (Entity entity : occupants) {
            if (entity instanceof EndCrystal crystal && crystal.isAlive() && base.equals(baseOf(crystal))
                && explodeReach(crystal.position()) && improvesDamage(target, crystal.position(), opening, false)) return true;
        }
        return haveCrystal && occupants.isEmpty() && placeReach(base)
            && explodeReach(position) && hasCrystalSpace(base, opening)
            && improvesDamage(target, position, opening, !hasCrystalSpace(base, null));
    }

    private boolean improvesDamage(LivingEntity target, Vec3 position, BlockPos opening, boolean opensPlacement) {
        double targetDamage = targetDamage(target, position, null, opening);
        if (!opensPlacement && targetDamage <= targetDamage(target, position, null, null)) return false;
        return acceptsDamage(target, targetDamage, damage(mc.player, position, null, opening));
    }

    private void tickAura() {
        if (!canAct() || actionPending) return;
        currentTarget = selectTarget();
        if (currentTarget == null) {
            blueprint.clear();
            holding = false;
            activeOpportunity = null;
            targetBox = null;
            return;
        }
        targetBox = extrapolation.get() ? MotionExtrapolation.predict(currentTarget, extrapolationTicks.get()) : null;
        updateMined();
        long now = System.currentTimeMillis();
        if (updateMode.get() == UpdateMode.Ticked || now - lastUpdateMs >= updateDelay.get()) {
            updateBlueprint();
            lastUpdateMs = now;
        }
        if (activeOpportunity != null) interact(activeOpportunity);
        else debugIdle++;
    }

    private LivingEntity selectTarget() {
        List<LivingEntity> targets = new ArrayList<>();
        THMSystem system = THMSystem.get();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == mc.player || !living.isAlive()
                || !entityTypes.get().contains(entity.getType()) || mc.player.distanceToSqr(entity) > targetRange.get() * targetRange.get()) continue;
            if (living instanceof Player player) {
                if (player.isSpectator() || player.getAbilities().invulnerable || Friends.get().isFriend(player)) continue;
                if (system != null && system.ignoreThmMembers.get() && ThmMembers.isThmMember(player)) continue;
            } else if (living.hasCustomName() && !targetNamed.get()) continue;
            if (living instanceof TamableAnimal pet && pet.isTame()
                && (!targetTamed.get() || (!targetOwned.get() && pet.isOwnedBy(mc.player)))) continue;
            if (targetPriority.get() == TargetPriority.Fov && angle(living) > fov.get()) continue;
            targets.add(living);
        }
        return targets.stream().min(Comparator.comparingDouble(entity -> switch (targetPriority.get()) {
            case Distance -> mc.player.distanceToSqr(entity);
            case Health -> entity.getHealth() + entity.getAbsorptionAmount();
            case Fov -> angle(entity);
        })).orElse(null);
    }

    private double angle(Entity entity) {
        double yaw = Math.abs(net.minecraft.util.Mth.wrapDegrees(Rotations.getYaw(entity.position()) - mc.player.getYRot()));
        double pitch = Math.abs(Rotations.getPitch(entity.position()) - mc.player.getXRot());
        return Math.hypot(yaw, pitch);
    }

    private void updateBlueprint() {
        blueprint.clear();
        boolean canSupport = support.get() && InvUtils.findInHotbar(Items.OBSIDIAN).found()
            && InvUtils.find(Items.END_CRYSTAL).found();
        double range = Math.max(placeRange.get(), explodeRange.get()) + 1;
        List<EndCrystal> crystals = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal) || !crystal.isAlive()) continue;
            crystals.add(crystal);
            if (!explodeReach(crystal.position())) continue;
            Opportunity opportunity = opportunity(baseOf(crystal), crystal.position(), crystal, null, false);
            if (opportunity != null) blueprint.put(opportunity.base, opportunity);
        }
        int radius = (int) Math.ceil(range);
        BlockPos center = mc.player.blockPosition().above();
        for (BlockPos mutable : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            BlockPos base = mutable.immutable();
            if (blueprint.containsKey(base) || !placeReach(base)) continue;
            boolean needsSupport = !isCrystalBase(base);
            if ((needsSupport && (!canSupport || !BlockUtils.canPlace(base) || !mc.level.getWorldBorder().isWithinBounds(base)))
                || !hasCrystalSpace(base, null)) continue;
            AABB box = crystalBox(base);
            if (!mc.level.getEntities((Entity) null, box, entity -> !(entity instanceof EndCrystal) && !entity.isSpectator()).isEmpty()) continue;
            EndCrystal crystal = null, blocker = null;
            for (EndCrystal candidate : crystals) {
                if (baseOf(candidate).equals(base)) crystal = candidate;
                else if (candidate.getBoundingBox().intersects(box)) blocker = candidate;
            }
            Opportunity opportunity = opportunity(base, crystalPosition(base), crystal, blocker, needsSupport);
            if (opportunity != null) blueprint.put(base, opportunity);
        }
        activeOpportunity = bestOpportunity(blueprint.values(), lastHit);
    }

    private Opportunity opportunity(BlockPos base, Vec3 position, EndCrystal crystal, EndCrystal blocker, boolean support) {
        BlockPos supportBase = support ? base : null;
        double target = targetDamage(currentTarget, position, supportBase, null), self = damage(mc.player, position, supportBase, null);
        if (!acceptsDamage(currentTarget, target, self)) return null;
        return new Opportunity(base, target, self, priorityMode.get().score(target, self), crystal, blocker, support);
    }

    private boolean acceptsDamage(LivingEntity target, double targetDamage, double self) {
        double lethalThreshold = lethalFinisher.get() ? target.getHealth() + target.getAbsorptionAmount() : Double.POSITIVE_INFINITY;
        boolean face = facePlaceable(target);
        return allowedDamage(targetDamage, self, mc.player.getHealth() + mc.player.getAbsorptionAmount(), minimumDamage(face),
            maxSelfDamage.get(), minPlaceHealth.get(), preventDeath.get(), face ? Priority.Damage : priorityMode.get(), minDamageAdvantage.get(),
            lethalThreshold);
    }

    private double minimumDamage(boolean face) {
        return face ? Math.min(FACE_PLACE_DAMAGE, minTargetDamage.get()) : minTargetDamage.get();
    }

    private boolean facePlaceable(LivingEntity target) {
        if (!facePlace.get()) return false;
        if (target.getHealth() + target.getAbsorptionAmount() <= facePlaceHealth.get()) return true;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack armor = target.getItemBySlot(slot);
            if (armor.isDamageableItem() && THMUtils.getDamage(armor) <= facePlaceArmor.get()) return true;
        }
        return false;
    }

    static boolean allowedDamage(double target, double self, double health, double minTarget, double maxSelf,
                                 double minHealth, boolean preventDeath, Priority priority, double advantage, double lethalThreshold) {
        return Double.isFinite(target) && Double.isFinite(self) && self <= maxSelf
            && health - self > minHealth && (!preventDeath || health - self > 0)
            && (target >= lethalThreshold || target >= minTarget && (priority != Priority.Advantage || priority.score(target, self) >= advantage));
    }

    private boolean safeDamage(Vec3 position) {
        return currentTarget != null && currentTarget.isAlive() && opportunity(BlockPos.containing(position).below(), position, null, null, false) != null;
    }

    private double targetDamage(LivingEntity target, Vec3 position, BlockPos supportBase, BlockPos opening) {
        if (target != currentTarget || targetBox == null) return damage(target, position, supportBase, opening);
        double predicted = damage(target, MotionExtrapolation.feet(targetBox), targetBox, position, supportBase, opening);
        // A predicted miss falls back to the current position, so slowing targets still get hit.
        return predicted > 2 && predicted < minimumDamage(facePlaceable(target))
            ? Math.max(predicted, damage(target, position, supportBase, opening)) : predicted;
    }

    private double damage(LivingEntity entity, Vec3 position, BlockPos supportBase, BlockPos opening) {
        return damage(entity, entity.position(), entity.getBoundingBox(), position, supportBase, opening);
    }

    private double damage(LivingEntity entity, Vec3 feet, AABB box, Vec3 position, BlockPos supportBase, BlockPos opening) {
        return DamageUtils.crystalDamage(entity, feet, box, position, (context, pos) -> {
            if (pos.equals(opening) || mined.contains(pos)) return null;
            var state = pos.equals(supportBase) ? Blocks.OBSIDIAN.defaultBlockState() : mc.level.getBlockState(pos);
            return state.getCollisionShape(mc.level, pos).clip(context.start(), context.end(), pos);
        });
    }

    static Opportunity bestOpportunity(Collection<Opportunity> opportunities, BlockPos lastHit) {
        Opportunity best = opportunities.stream().filter(op -> !op.blocked() || op.base.equals(lastHit))
            .max(Comparator.comparing((Opportunity op) -> !op.support).thenComparingDouble(op -> op.priority)).orElse(null);
        if (best != null) return best;
        Opportunity blocked = opportunities.stream().filter(Opportunity::blocked)
            .max(Comparator.comparingDouble(op -> op.priority)).orElse(null);
        return blocked == null ? null : opportunities.stream().filter(op -> op.crystal != null && op.crystal == blocked.blocker)
            .findFirst().orElse(null);
    }

    private void interact(Opportunity best) {
        if (best.blocked()) {
            Opportunity blocker = blueprint.get(baseOf(best.blocker));
            if (blocker != null) attackCrystal(blocker);
            return;
        }
        if (best.crystal != null && best.crystal.isAlive()) {
            attackCrystal(best);
            return;
        }
        for (Opportunity other : blueprint.values()) {
            if (other.crystal != null && other.crystal.isAlive() && other.crystal.getBoundingBox().intersects(crystalBox(best.base))) {
                attackCrystal(other);
                return;
            }
        }
        if (best.support) {
            placeSupport(best);
            return;
        }
        if (prediction.get().onTick && waitingForCrystal && System.currentTimeMillis() - lastExplodeMs >= explodeDelay.get()) {
            runRotated(best, crystalBox(best.base), () -> {
                predictAttacks(best.base, 1);
                waitingForCrystal = false;
                lastHit = best.base;
                lastExplodeMs = System.currentTimeMillis();
            });
        } else place(best);
    }

    private void runRotated(Opportunity opportunity, AABB face, Runnable action) {
        runRotated(opportunity, hitPosition(opportunity.base, placeSide(opportunity.base)), face, action);
    }

    /** {@code face} is the box the server rotation must hit for {@code action} to skip the rotation. */
    private void runRotated(Opportunity opportunity, Vec3 hit, AABB face, Runnable action) {
        long epoch = generation;
        Runnable checked = () -> {
            actionPending = false;
            if (!isActive() || generation != epoch || !canAct() || currentTarget == null || !currentTarget.isAlive()
                || opportunity(opportunity.base, opportunity.position(), null, null, opportunity.support) == null) return;
            action.run();
        };
        if (!rotate.get() || skipFacing.get() && facesSent(face)) checked.run();
        else {
            actionPending = true;
            debugWaits++;
            holdYaw = (float) Rotations.getYaw(hit);
            holdPitch = (float) Rotations.getPitch(hit);
            holding = true;
            Rotations.rotate(holdYaw, holdPitch, rotationPriority.get(), checked);
        }
    }

    private boolean facesSent(AABB box) {
        return sentRotation && faces(mc.player.getEyePosition(), sentYaw, sentPitch, box);
    }

    static boolean faces(Vec3 eye, float yaw, float pitch, AABB box) {
        return box.contains(eye) || box.clip(eye, eye.add(Vec3.directionFromRotation(pitch, yaw).scale(8))).isPresent();
    }

    private void attackCrystal(Opportunity opportunity) {
        EndCrystal crystal = opportunity.crystal;
        if (crystal == null || !crystal.isAlive() || crystal.tickCount < ticksExisted.get() || !explodeReach(crystal.position())
            || System.currentTimeMillis() - lastExplodeMs < explodeDelay.get() || swapPenaltyActive() || iFramesBlock(opportunity.target)) return;
        // Wait for the server to remove a crystal we already hit.
        if (crystal.getId() == lastAttackId && System.currentTimeMillis() - lastAttackMs < PingTracker.rtt() + 100) return;
        // From above, the base's top center lies on the crystal box, so place and break share one rotation.
        BlockPos base = baseOf(crystal);
        Vec3 aim = skipFacing.get() && placeSide(base) == Direction.UP ? crystalPosition(base) : crystal.getBoundingBox().getCenter();
        runRotated(opportunity, aim, crystal.getBoundingBox(), () -> {
            if (!crystal.isAlive() || !explodeReach(crystal.position())) return;
            attackId(crystal.getId());
            lastAttackId = crystal.getId();
            lastAttackMs = System.currentTimeMillis();
            // Pre-attack the IDs our next crystals will spawn with.
            for (int i = 1; i < attackFactor.get() && crystal.getId() + i > 0; i++) attackId(crystal.getId() + i);
            lastEntityId = Math.max(lastEntityId, crystal.getId());
            lastHit = opportunity.base;
            lastExplodeMs = System.currentTimeMillis();
            recordHit(opportunity.target);
            // The server handles the attack first, so the base is free for this placement.
            if (placeAfterBreak.get() && System.currentTimeMillis() - lastPlaceMs >= placeDelay.get()
                && (!rotate.get() || facesSent(new AABB(opportunity.base)))) placeCrystal(opportunity, crystal);
        });
    }

    private void predictAttacks(BlockPos base, int count) {
        if (lastEntityId <= 0 || swapPenaltyActive() || !safeDamage(crystalPosition(base)) || !explodeReach(crystalPosition(base))) return;
        for (int i = 0; i < count; i++) {
            if (lastEntityId == Integer.MAX_VALUE) break;
            attackId(++lastEntityId);
        }
        lastHit = base;
    }

    private void attackId(int id) {
        Entity known = mc.level.getEntity(id);
        if (known != null && !(known instanceof EndCrystal)) return;
        Runnable attack = () -> {
            mc.getConnection().send(new ServerboundAttackPacket(id));
            mc.player.swing(InteractionHand.MAIN_HAND);
            debugAttacks++;
            long[] times = debugTimes.get(id);
            if (times != null && times[1] == 0) {
                times[1] = System.currentTimeMillis();
                debugLag(1, times[1] - times[0]);
            }
        };
        int weapon = antiWeakness.get() ? weaponSlot() : -1;
        if (weapon < 0) attack.run();
        else {
            attacking = true;
            try {
                InventoryManager.withItem(this, weapon, true, SwapManager.CRYSTAL, attack);
            } finally {
                attacking = false;
            }
        }
    }

    private boolean swapPenaltyActive() {
        return System.currentTimeMillis() - lastSwapMs < swapPenalty.get() * 50L;
    }

    private void recordHit(double damage) {
        lastHitTarget = currentTarget;
        lastHitDamage = damage;
        lastHitMs = System.currentTimeMillis();
    }

    /** Vanilla only applies the excess over the last hit during the first 10 invulnerable ticks. */
    private boolean iFramesBlock(double damage) {
        boolean block = iFrameSync.get() && currentTarget != null && currentTarget == lastHitTarget && currentTarget.hurtTime > 0
            && System.currentTimeMillis() - lastHitMs < 500 && damage <= lastHitDamage;
        if (block) debugIFrames++;
        return block;
    }

    private boolean placeReach(BlockPos base) {
        Vec3 top = crystalPosition(base);
        return reach(top, top, base, placeRange.get(), placeWallRange.get());
    }

    private boolean explodeReach(Vec3 crystal) {
        return reach(crystal, crystal.add(0, 1, 0), null, explodeRange.get(), explodeWallRange.get());
    }

    private boolean reach(Vec3 point, Vec3 sight, BlockPos target, double range, double wallRange) {
        Vec3 eye = mc.player.getEyePosition();
        double distance = eye.distanceTo(point);
        if (distance > range) return false;
        if (distance <= wallRange) return true;
        BlockHitResult ray = mc.level.clip(new ClipContext(eye, sight, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return ray.getType() == HitResult.Type.MISS || ray.getBlockPos().equals(target);
    }

    /** Hotbar weapon to attack with when Weakness would make a bare hit deal no damage, or -1. */
    private int weaponSlot() {
        var weakness = mc.player.getEffect(MobEffects.WEAKNESS);
        if (weakness == null) return -1;
        var strength = mc.player.getEffect(MobEffects.STRENGTH);
        // Fist damage is 1 + 3 per Strength level - 4 per Weakness level; attacks need more than zero.
        if (1 + (strength == null ? 0 : 3 * (strength.getAmplifier() + 1)) - 4 * (weakness.getAmplifier() + 1) > 0) return -1;
        ItemStack held = mc.player.getMainHandItem();
        if (held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES)) return -1;
        for (var tag : List.of(ItemTags.SWORDS, ItemTags.AXES)) {
            for (int slot = 0; slot < 9; slot++) if (mc.player.getInventory().getItem(slot).is(tag)) return slot;
        }
        return -1;
    }

    private void place(Opportunity opportunity) {
        place(opportunity, null);
    }

    /** {@code broken} is a crystal leaving the world, so its space and the place delay are ignored. */
    private void place(Opportunity opportunity, Entity broken) {
        long now = System.currentTimeMillis();
        // Wait for the spawn of an unconfirmed placement on the same base.
        if (broken == null && !placeSpawned && opportunity.base.equals(lastPlace) && now - lastPlaceMs < PingTracker.rtt() + 100) return;
        if (supportTicks > 0 || (broken == null && now - lastPlaceMs < placeDelay.get()
            && !(postPacketPlace.get() && prediction.get() == PredictionMode.Tick && safeToPlaceInstantly && !placePostPause.get()))) return;
        runRotated(opportunity, new AABB(opportunity.base), () -> placeCrystal(opportunity, broken));
    }

    private void placeCrystal(Opportunity opportunity, Entity broken) {
        if (supportTicks > 0 || PlacementUtils.remainingPlacementPackets(packet.mode()) == 0 || !validPlacement(opportunity.base, broken)) return;
        InteractionHand hand = crystalHand();
        if (hand == null || !placeInternal(opportunity, hand)) return;
        debugPlaces++;
        safeToPlaceInstantly = false;
        lastHit = null;
        if (prediction.get().onPlace && System.currentTimeMillis() - lastSpawnMs <= packetLifetime.get()) {
            int previous = lastEntityId;
            predictAttacks(opportunity.base, placePredictions.get());
            lastEntityId = previous == Integer.MAX_VALUE ? previous : previous + 1;
            // The predicted attack already freed the base, so the next placement need not wait for the spawn.
            if (opportunity.base.equals(lastHit)) placeSpawned = true;
        }
    }

    private void placeSupport(Opportunity opportunity) {
        long now = System.currentTimeMillis();
        if (!support.get() || PlacementUtils.remainingPlacementPackets(packet.mode()) == 0 || now < supportRetryAfterMs || now - lastPlaceMs < placeDelay.get()) return;
        Direction side = PlacementUtils.getPlaceSide(opportunity.base);
        BlockHitResult placement = side == null
            ? new BlockHitResult(Vec3.atCenterOf(opportunity.base), Direction.DOWN, opportunity.base, false)
            : PlacementUtils.packetPlacementHit(opportunity.base, side);
        Vec3 hit = placement.getLocation();
        if (!reach(hit, hit, placement.getBlockPos(), placeRange.get(), placeWallRange.get())) return;
        runRotated(opportunity, hit, new AABB(placement.getBlockPos()), () -> {
            var item = InvUtils.findInHotbar(Items.OBSIDIAN);
            if (!support.get() || !item.found() || !BlockUtils.canPlace(opportunity.base)
                || PlacementUtils.getPlaceSide(opportunity.base) != side
                || !mc.level.getWorldBorder().isWithinBounds(opportunity.base)
                || !hasCrystalSpace(opportunity.base, null)
                || !mc.level.getEntities((Entity) null, crystalBox(opportunity.base), entity -> !entity.isSpectator()).isEmpty()
                || !reach(hit, hit, placement.getBlockPos(), placeRange.get(), placeWallRange.get())) return;
            InteractionHand hand = item.isOffhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            InventoryManager inventory = InventoryManager.getInstance();
            if (hand == InteractionHand.MAIN_HAND && !swap.get() && !mc.player.getMainHandItem().is(Items.OBSIDIAN)) return;
            if (hand == InteractionHand.MAIN_HAND && silentSwap.get()) {
                boolean[] sent = {false};
                if (!InventoryManager.withItem(this, item.slot(), false, SwapManager.CRYSTAL,
                    () -> sent[0] = PlacementUtils.sendPlacement(opportunity.base, placement, hand, packet.mode())) || !sent[0]) return;
            } else {
                if (hand == InteractionHand.MAIN_HAND && !inventory.setClientSlot(item.slot(), SwapManager.CRYSTAL)) return;
                if (!PlacementUtils.sendPlacement(opportunity.base, placement, hand, packet.mode())) return;
            }
            supportTicks = supportDelay.get();
            supportRetryAfterMs = System.currentTimeMillis() + 500;
            lastPlace = opportunity.base;
            lastPlaceMs = System.currentTimeMillis();
            if (supportTicks == 0) placeCrystal(opportunity, null);
        });
    }

    private boolean validPlacement(BlockPos base, Entity broken) {
        if (!isCrystalBase(base) || !hasCrystalSpace(base, null) || !placeReach(base)) return false;
        return mc.level.getEntities((Entity) null, crystalBox(base), entity -> !entity.isSpectator() && entity != broken).isEmpty();
    }

    private boolean isCrystalBase(BlockPos base) {
        var state = mc.level.getBlockState(base);
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    private boolean hasCrystalSpace(BlockPos base, BlockPos opening) {
        return hasCrystalSpace(base, opening, oldPlace.get(), airAt);
    }

    static boolean hasCrystalSpace(BlockPos base, BlockPos opening, boolean oldPlace, Predicate<BlockPos> isAir) {
        BlockPos lower = base.above();
        if (!lower.equals(opening) && !isAir.test(lower)) return false;
        BlockPos upper = lower.above();
        return !oldPlace || upper.equals(opening) || isAir.test(upper);
    }

    private InteractionHand crystalHand() {
        // Never swap over an offhand item in use.
        InteractionHand preferred = swapHand.get() == SwapHand.MainHand || mc.player.isUsingItem() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (mc.player.getItemInHand(preferred).is(Items.END_CRYSTAL)) return preferred;
        InteractionHand other = preferred == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (mc.player.getItemInHand(other).is(Items.END_CRYSTAL)) return other;
        if (!swap.get()) return null;
        if (preferred == InteractionHand.MAIN_HAND) return InteractionHand.MAIN_HAND;
        var item = InvUtils.find(Items.END_CRYSTAL);
        if (!item.found() || !DeepSwapManager.get().swapIntoOffhand(item.slot())) return null;
        return mc.player.getOffhandItem().is(Items.END_CRYSTAL) ? InteractionHand.OFF_HAND : null;
    }

    private boolean placeInternal(Opportunity opportunity, InteractionHand hand) {
        Direction side = placeSide(opportunity.base);
        BlockHitResult hit = new BlockHitResult(hitPosition(opportunity.base, side), side, opportunity.base, false);
        var mode = packet.mode();
        boolean sent;
        if (hand == InteractionHand.MAIN_HAND) {
            int slot = mc.player.getMainHandItem().is(Items.END_CRYSTAL)
                ? mc.player.getInventory().getSelectedSlot() : InvUtils.find(Items.END_CRYSTAL).slot();
            if (slot < 0 || slot >= 36) return false;
            if (silentSwap.get() || slot >= 9) {
                boolean[] placed = {false};
                InventoryManager.withItem(this, slot, mode.isPacket() && silentSwap.get(), SwapManager.CRYSTAL,
                    () -> placed[0] = PlacementUtils.sendPlacement(opportunity.base, hit, hand, mode));
                sent = placed[0];
            } else {
                sent = InventoryManager.getInstance().setClientSlot(slot, SwapManager.CRYSTAL)
                    && PlacementUtils.sendPlacement(opportunity.base, hit, hand, mode);
            }
        } else sent = PlacementUtils.sendPlacement(opportunity.base, hit, hand, mode);
        if (!sent) return false;
        lastPlace = opportunity.base;
        placeSpawned = false;
        lastPlaceMs = System.currentTimeMillis();
        waitingForCrystal = true;
        return true;
    }

    private Direction placeSide(BlockPos base) {
        Vec3 eyes = mc.player.getEyePosition();
        if (eyes.y > base.getY() + 1) return Direction.UP;
        Direction best = Direction.UP;
        double distance = Double.MAX_VALUE;
        for (Direction side : Direction.values()) {
            double outside = side.getAxis().choose(eyes.x, eyes.y, eyes.z) - side.getAxis().choose(base.getX(), base.getY(), base.getZ());
            if ((side.getAxisDirection() == Direction.AxisDirection.POSITIVE && outside <= 1)
                || (side.getAxisDirection() == Direction.AxisDirection.NEGATIVE && outside >= 0)) continue;
            double candidate = eyes.distanceToSqr(hitPosition(base, side));
            if (candidate < distance) { best = side; distance = candidate; }
        }
        return best;
    }

    private static Vec3 hitPosition(BlockPos base, Direction side) {
        return Vec3.atCenterOf(base).add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
    }

    static Vec3 crystalPosition(BlockPos base) { return new Vec3(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5); }
    static AABB crystalBox(BlockPos base) { return new AABB(base.getX(), base.getY() + 1, base.getZ(), base.getX() + 1, base.getY() + 3, base.getZ() + 1); }
    private static BlockPos baseOf(EndCrystal crystal) { return BlockPos.containing(crystal.position().add(0, -0.5, 0)); }

    @EventHandler
    private void onRender(Render3DEvent event) {
        frameBudget.set(maxUpdatesPerFrame.get());
        long now = System.nanoTime();
        double dt = lastFrameNs == 0 ? 0 : Math.min((now - lastFrameNs) / 1e9, 0.1);
        lastFrameNs = now;
        if (!render.get() || !renderPlacements.get()) return;
        Opportunity target = activeOpportunity;
        if (target != null) {
            if (boxFade == 0) { boxX = target.base.getX(); boxY = target.base.getY(); boxZ = target.base.getZ(); }
            double step = 1 - Math.exp(-animationSpeed.get() * dt);
            boxX += (target.base.getX() - boxX) * step;
            boxY += (target.base.getY() - boxY) * step;
            boxZ += (target.base.getZ() - boxZ) * step;
            boxDamage = target.target;
        }
        boxFade = (float) Math.clamp(boxFade + (target != null ? dt : -dt) * 5, 0, 1);
        if (boxFade == 0) return;
        RenderUtilsTHM.renderBoxGradient(event, boxX, boxY, boxZ, faded(fillBottom, secondaryColor.get()), faded(fillTop, primaryColor.get()),
            faded(lineBottom, secondaryColorLine.get()), faded(lineTop, primaryColorLine.get()));
    }

    private Color faded(Color scratch, Color color) {
        scratch.set(color);
        scratch.a = (int) (color.a * boxFade);
        return scratch;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!render.get() || !renderPlacements.get() || !renderDamage.get() || boxFade == 0) return;
        damagePos.set(boxX + 0.5, boxY + 0.5, boxZ + 0.5);
        if (!NametagUtils.to2D(damagePos, 1.5)) return;
        String text = String.format("%.1f", boxDamage);
        NametagUtils.begin(damagePos, event.graphics);
        TextRenderer.get().begin(event.graphics, 1.0, false, true);
        TextRenderer.get().render(text, -TextRenderer.get().getWidth(text) / 2.0, 0.0, faded(lineTop, Color.WHITE), true);
        TextRenderer.get().end();
        NametagUtils.end(event.graphics);
    }

    @Override public String getInfoString() { return currentTarget == null ? null : currentTarget.getName().getString(); }

    static final class Opportunity {
        final BlockPos base;
        final double target, self, priority;
        EndCrystal crystal;
        final EndCrystal blocker;
        final boolean support;
        Opportunity(BlockPos base, double target, double self, double priority, EndCrystal crystal, EndCrystal blocker, boolean support) {
            this.base = base.immutable(); this.target = target; this.self = self; this.priority = priority;
            this.crystal = crystal; this.blocker = blocker;
            this.support = support;
        }
        boolean blocked() { return crystal == null && blocker != null && blocker.isAlive(); }
        Vec3 position() { return crystal == null ? crystalPosition(base) : crystal.position(); }
    }

    public enum UpdateMode implements DescribedOption {
        Async, Ticked;
        @Override public String description() { return this == Async ? "Evaluate between ticks with bounded frame work." : "Evaluate once per client tick."; }
    }
    public enum SwapHand implements DescribedOption {
        MainHand, OffHand;
        @Override public String description() { return this == MainHand ? "Prefer crystals in your main hand." : "Prefer crystals in your offhand."; }
    }
    public enum Priority implements DescribedOption {
        Damage, Advantage;
        double score(double target, double self) { return this == Damage ? target : target - self; }
        @Override public String description() { return this == Damage ? "Prefer the highest target damage." : "Prefer target damage minus self damage."; }
    }
    public enum TargetPriority implements DescribedOption {
        Distance, Health, Fov;
        @Override public String description() { return switch (this) {
            case Distance -> "Target the nearest entity.";
            case Health -> "Target the entity with the lowest health.";
            case Fov -> "Target the entity closest to your crosshair.";
        }; }
    }
    public enum PredictionMode implements DescribedOption {
        None(false, false, false), Packet(true, false, false), Deferred(false, true, false), Tick(false, false, true), Mixed(true, true, false);
        final boolean onPacket, onPlace, onTick;
        PredictionMode(boolean onPacket, boolean onPlace, boolean onTick) { this.onPacket = onPacket; this.onPlace = onPlace; this.onTick = onTick; }
        @Override public String description() { return switch (this) {
            case None -> "Attack confirmed crystals only.";
            case Packet -> "Predict crystal IDs after spawn updates.";
            case Deferred -> "Predict crystal IDs after placement.";
            case Tick -> "Predict the next crystal when the attack delay expires.";
            case Mixed -> "Predict crystal IDs after spawns and placements.";
        }; }
    }
}
