/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalXZ;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.systems.modules.player.Rotation;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.ElytraNavigator;
import xyz.thm.addon.utils.ElytraPathPlanner;
import xyz.thm.addon.utils.InventoryManager;
import xyz.thm.addon.utils.RenderUtilsTHM;
import xyz.thm.addon.utils.THMUtils;
import xyz.thm.addon.utils.server.ServerReconnectService;
import xyz.thm.addon.utils.server.ServerStatusHandler;
import xyz.thm.addon.utils.server.ServerStatusHandler.ServerState;

import java.util.*;
import java.util.function.Predicate;

public class ElytraRoute extends Module {
    public static ElytraRoute INSTANCE;

    private static final int CHEST_SLOT_INDEX = 38;
    private static final int PLAYER_INVENTORY_SLOTS = 36;
    private static final int TAKEOFF_SECOND_JUMP_DELAY = 4;
    private static final int TAKEOFF_GLIDE_TIMEOUT = 20;
    private static final int TAKEOFF_MAX_FAILURES = 3;
    private static final int SHULKER_PICKUP_TIMEOUT = 120;
    private static final int SHULKER_PICKUP_REACQUIRE_INTERVAL = 20;
    private static final double SHULKER_PICKUP_SEARCH_RADIUS = 8.0;
    private static final int PAD_SEARCH_CHECKS_PER_TICK = 512;
    private static final String RECONNECT_RESUME_LISTENER_KEY = "elytra-route-resume";
    private static final String RECONNECT_FAILURE_LISTENER_KEY = "elytra-route-failure";
    private static final int MAIN_SERVER_GATE_UNKNOWN_GRACE_TICKS = 40;
    private static final int MAIN_SERVER_RESUME_DELAY_TICKS = 120;
    private static final long MAIN_SERVER_GATE_WARNING_INTERVAL_MS = 5_000L;
    private static final int LANDING_CHUNK_LOAD_RADIUS = 1;
    private static final long LANDING_CHUNK_WARNING_INTERVAL_MS = 5_000L;
    private static final int RESTOCK_INVALID_SOURCE_RETRY_LIMIT = PLAYER_INVENTORY_SLOTS;
    private static final int SOURCE_READY_MAX_RETRIES = 3;

    private final SettingGroup sgRoute = settings.getDefaultGroup();
    private final SettingGroup sgPath = settings.createGroup("Pathing");
    private final SettingGroup sgRepair = settings.createGroup("Repair");
    private final SettingGroup sgRestock = settings.createGroup("Restock");
    private final SettingGroup sgFood = settings.createGroup("Food");
    private final SettingGroup sgHome = settings.createGroup("Home");
    private final SettingGroup sgReconnect = settings.createGroup("Reconnect");
    private final SettingGroup sgMining = settings.createGroup("Mining");

    private final Setting<Integer> destinationX = sgRoute.add(new IntSetting.Builder()
        .name("destination-x")
        .description("Target X coordinate.")
        .defaultValue(0)
        .build()
    );

    private final Setting<Integer> destinationZ = sgRoute.add(new IntSetting.Builder()
        .name("destination-z")
        .description("Target Z coordinate.")
        .defaultValue(0)
        .build()
    );

    private final Setting<Double> arrivalRadius = sgRoute.add(new DoubleSetting.Builder()
        .name("arrival-radius")
        .description("Horizontal distance where the route is considered complete.")
        .defaultValue(64)
        .min(1)
        .sliderRange(1, 256)
        .build()
    );

    private final Setting<Integer> flyYLevel = sgRoute.add(new IntSetting.Builder()
        .name("fly-y-level")
        .description("Cruise Y level while gliding.")
        .defaultValue(130)
        .range(-64, 320)
        .sliderRange(64, 256)
        .build()
    );

    private final Setting<Boolean> autoFreeLook = sgRoute.add(new BoolSetting.Builder()
        .name("auto-freelook")
        .description("Enable Meteor FreeLook while the route module runs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> togglePerspective = sgRoute.add(new BoolSetting.Builder()
        .name("toggle-perspective")
        .description("Switches to third person while active, then restores your perspective.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useBounceMode = sgRoute.add(new BoolSetting.Builder()
        .name("use-bounce-mode")
        .description("Use ElytraFly's Bounce mode while routing, then restore your old mode.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoReconnect = sgReconnect.add(new BoolSetting.Builder()
        .name("auto-reconnect")
        .description("Keeps the route alive through disconnects and resumes after reconnecting.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> restartRejoinDelayMinutes = sgReconnect.add(new IntSetting.Builder()
        .name("restart-rejoin-delay-minutes")
        .description("Delay in minutes applied to Meteor AutoReconnect.")
        .defaultValue(15)
        .range(1, 240)
        .sliderRange(1, 60)
        .visible(autoReconnect::get)
        .build()
    );

    private final Setting<Boolean> smartPathing = sgPath.add(new BoolSetting.Builder()
        .name("smart-pathing")
        .description("Plan around obstacles instead of flying straight at the target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> pathRange = sgPath.add(new IntSetting.Builder()
        .name("path-range")
        .description("How far around you the planner searches.")
        .defaultValue(96)
        .range(32, 192)
        .sliderRange(32, 192)
        .visible(smartPathing::get)
        .build()
    );

    private final Setting<Integer> pathLookahead = sgPath.add(new IntSetting.Builder()
        .name("path-lookahead")
        .description("How far ahead the route checks for obstacles.")
        .defaultValue(24)
        .range(8, 64)
        .sliderRange(8, 64)
        .visible(smartPathing::get)
        .build()
    );

    private final Setting<Integer> pathBudget = sgPath.add(new IntSetting.Builder()
        .name("path-budget")
        .description("Planner nodes per tick. Lower is smoother, higher plans faster.")
        .defaultValue(600)
        .range(100, 3000)
        .sliderRange(100, 3000)
        .visible(smartPathing::get)
        .build()
    );

    private final Setting<Integer> stuckTicks = sgPath.add(new IntSetting.Builder()
        .name("stuck-ticks")
        .description("Ticks without progress before the route escapes and replans.")
        .defaultValue(25)
        .range(10, 100)
        .sliderRange(10, 100)
        .visible(smartPathing::get)
        .build()
    );

    private final Setting<Boolean> renderPath = sgRoute.add(new BoolSetting.Builder()
        .name("render-path")
        .description("Draws the planned way around obstacles.")
        .defaultValue(true)
        .visible(smartPathing::get)
        .build()
    );

    private final Setting<SettingColor> pathColor = sgRoute.add(new ColorSetting.Builder()
        .name("path-color")
        .description("Color of the planned path.")
        .defaultValue(new SettingColor(0, 200, 255, 255))
        .visible(() -> smartPathing.get() && renderPath.get())
        .build()
    );

    private final Setting<Boolean> pathTracer = sgRoute.add(new BoolSetting.Builder()
        .name("path-tracer")
        .description("Line from the screen centre to the next path point.")
        .defaultValue(true)
        .visible(() -> smartPathing.get() && renderPath.get())
        .build()
    );

    private final Setting<Boolean> repairElytras = sgRepair.add(new BoolSetting.Builder()
        .name("repair-elytras")
        .description("Mend nearly broken elytras with XP bottles.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> repairBelow = sgRepair.add(new IntSetting.Builder()
        .name("repair-below")
        .description("Repair once every elytra has at most this much durability left.")
        .defaultValue(40)
        .range(1, 400)
        .sliderRange(1, 200)
        .visible(repairElytras::get)
        .build()
    );

    private final Setting<Integer> repairTarget = sgRepair.add(new IntSetting.Builder()
        .name("repair-target")
        .description("Mend elytras up to this durability percent.")
        .defaultValue(100)
        .range(10, 100)
        .sliderRange(10, 100)
        .visible(repairElytras::get)
        .build()
    );

    private final Setting<Boolean> enderChestXp = sgRepair.add(new BoolSetting.Builder()
        .name("ender-chest-xp")
        .description("Fetch an XP shulker from your Ender Chest when you have no bottles.")
        .defaultValue(true)
        .visible(repairElytras::get)
        .build()
    );

    private final Setting<Boolean> returnShulker = sgRepair.add(new BoolSetting.Builder()
        .name("return-shulker")
        .description("Put the XP shulker back into the Ender Chest afterwards.")
        .defaultValue(true)
        .visible(() -> repairElytras.get() && enderChestXp.get())
        .build()
    );

    private final Setting<Integer> repairThrowDelay = sgRepair.add(new IntSetting.Builder()
        .name("throw-delay")
        .description("Ticks between thrown XP bottles.")
        .defaultValue(2)
        .range(1, 10)
        .sliderRange(1, 10)
        .visible(repairElytras::get)
        .build()
    );

    private final Setting<Boolean> restockElytras = sgRestock.add(new BoolSetting.Builder()
        .name("restock-elytras")
        .description("Restock elytras from inventory shulkers when you run out of flight-ready elytras.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fixedElytraRestockAmount = sgRestock.add(new BoolSetting.Builder()
        .name("fixed-elytra-restock-amount")
        .description("Use a fixed loose-elytra restock target instead of filling all allowed inventory space.")
        .defaultValue(true)
        .visible(restockElytras::get)
        .build()
    );

    private final Setting<Integer> elytraRestockAmount = sgRestock.add(new IntSetting.Builder()
        .name("elytra-restock-amount")
        .description("How many good loose elytras to keep after a restock pass.")
        .defaultValue(2)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(() -> restockElytras.get() && fixedElytraRestockAmount.get())
        .build()
    );

    private final Setting<Integer> minimumEmptySlots = sgRestock.add(new IntSetting.Builder()
        .name("minimum-empty-slots")
        .description("Minimum empty loose inventory slots to preserve during restock math.")
        .defaultValue(3)
        .range(3, 10)
        .sliderRange(3, 10)
        .build()
    );

    private final Setting<Integer> inventoryDelay = sgRestock.add(new IntSetting.Builder()
        .name("inventory-delay")
        .description("Ticks to wait between inventory interactions.")
        .defaultValue(3)
        .range(0, 20)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> foodRestock = sgFood.add(new BoolSetting.Builder()
        .name("food-restock")
        .description("Restocks one configured food stack when your valid food count drops to the saved amount.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Item>> foodTypes = sgFood.add(new ItemListSetting.Builder()
        .name("food-types")
        .description("Which food item counts as restock food. Maximum 1 food type.")
        .defaultValue()
        .visible(foodRestock::get)
        .onChanged(this::handleFoodTypesChanged)
        .build()
    );

    private final Setting<Integer> saveFood = sgFood.add(new IntSetting.Builder()
        .name("save-food")
        .description("Restock food when your total configured food count is at or below this value.")
        .defaultValue(16)
        .range(1, 32)
        .sliderRange(1, 32)
        .visible(foodRestock::get)
        .build()
    );

    private final Setting<Boolean> periodicHomeSave = sgHome.add(new BoolSetting.Builder()
        .name("periodic-home-save")
        .description("Periodically run /sethome while the route is active.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> homeSaveIntervalMinutes = sgHome.add(new IntSetting.Builder()
        .name("home-save-interval-minutes")
        .description("Minutes between home saves.")
        .defaultValue(10)
        .range(1, 60)
        .sliderRange(1, 60)
        .visible(periodicHomeSave::get)
        .build()
    );

    private final Setting<String> homeName = sgHome.add(new StringSetting.Builder()
        .name("home-name")
        .description("Name passed to /sethome.")
        .defaultValue("")
        .visible(periodicHomeSave::get)
        .build()
    );

    private final Setting<String> homeName2 = sgHome.add(new StringSetting.Builder()
        .name("second-home-name")
        .description("Optional second home used in turns with the first instead of overwriting it.")
        .defaultValue("")
        .visible(periodicHomeSave::get)
        .build()
    );

    private final Setting<Integer> homeCommandCooldown = sgHome.add(new IntSetting.Builder()
        .name("home-command-cooldown")
        .description("Server cooldown in minutes between home commands.")
        .defaultValue(10)
        .range(0, 30)
        .sliderRange(0, 30)
        .visible(() -> periodicHomeSave.get() && !homeName2.get().isBlank())
        .build()
    );

    // Persisted so a restart continues the delete/set cycle instead of deleting the newest home.
    private final Setting<Integer> homeCycleStep = sgHome.add(new IntSetting.Builder()
        .name("home-cycle-step")
        .description("Next step of the two-home cycle.")
        .defaultValue(0)
        .range(0, 3)
        .visible(() -> false)
        .build()
    );

    private final Setting<Boolean> mineObstacles = sgMining.add(new BoolSetting.Builder()
        .name("mine-obstacles")
        .description("Breaks blocks in the way with a pickaxe when there is no way to fly around them.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> digCost = sgMining.add(new DoubleSetting.Builder()
        .name("dig-cost")
        .description("Detour length in blocks that one tick of digging is worth. Lower digs more, higher flies around more.")
        .defaultValue(0.1)
        .range(0.01, 2.0)
        .sliderRange(0.02, 0.5)
        .visible(mineObstacles::get)
        .build()
    );

    private final Setting<Integer> maxBlockTicks = sgMining.add(new IntSetting.Builder()
        .name("max-block-ticks")
        .description("Blocks that take longer than this to break are treated as walls.")
        .defaultValue(60)
        .range(5, 400)
        .sliderRange(10, 200)
        .visible(mineObstacles::get)
        .build()
    );

    private final Setting<Double> brakeDistance = sgMining.add(new DoubleSetting.Builder()
        .name("brake-distance")
        .description("Holds position this close to a block that is still being broken.")
        .defaultValue(2.5)
        .range(1.0, 4.0)
        .sliderRange(1.0, 4.0)
        .visible(mineObstacles::get)
        .build()
    );

    private final Setting<Boolean> debugRoute = sgMining.add(new BoolSetting.Builder()
        .name("debug")
        .description("Prints navigator and mining state to chat every second.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> mineRotate = sgMining.add(new BoolSetting.Builder()
        .name("mine-rotate")
        .description("Looks at blocks while breaking them.")
        .defaultValue(true)
        .visible(mineObstacles::get)
        .build()
    );

    private final Setting<Boolean> mineAvoidContainers = sgMining.add(new BoolSetting.Builder()
        .name("avoid-containers")
        .description("Never breaks chests, spawners and other block entities.")
        .defaultValue(true)
        .visible(mineObstacles::get)
        .build()
    );

    private final Setting<Integer> mineMinDurability = sgMining.add(new IntSetting.Builder()
        .name("min-pickaxe-durability")
        .description("Pickaxes with less durability left are not used.")
        .defaultValue(20)
        .range(1, 500)
        .sliderRange(1, 100)
        .visible(mineObstacles::get)
        .build()
    );

    private final Setting<Boolean> baritoneFallback = sgMining.add(new BoolSetting.Builder()
        .name("baritone-fallback")
        .description("When no way through the air exists, lands and lets Baritone walk ahead on the ground, then flies on.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> fallbackDistance = sgMining.add(new IntSetting.Builder()
        .name("fallback-distance")
        .description("How far Baritone walks toward the destination before the route tries to fly again.")
        .defaultValue(64)
        .range(16, 512)
        .sliderRange(16, 256)
        .visible(baritoneFallback::get)
        .build()
    );

    private final Setting<Boolean> fallbackBreak = sgMining.add(new BoolSetting.Builder()
        .name("fallback-break-blocks")
        .description("Lets Baritone break blocks on the way.")
        .defaultValue(true)
        .visible(baritoneFallback::get)
        .build()
    );

    private final Setting<Boolean> fallbackPlace = sgMining.add(new BoolSetting.Builder()
        .name("fallback-place-blocks")
        .description("Lets Baritone place blocks on the way.")
        .defaultValue(false)
        .visible(baritoneFallback::get)
        .build()
    );

    private final Setting<Integer> fallbackTimeout = sgMining.add(new IntSetting.Builder()
        .name("fallback-timeout")
        .description("Seconds without getting closer before Baritone gives up.")
        .defaultValue(30)
        .range(5, 300)
        .sliderRange(10, 120)
        .visible(baritoneFallback::get)
        .build()
    );

    private enum State {
        TAKEOFF_AWAIT_GROUND,
        TAKEOFF_FIRST_JUMP,
        TAKEOFF_SECOND_JUMP_WAIT,
        TAKEOFF_WAIT_GLIDE,
        FLIGHT,
        LANDING,
        RESTOCK_MOVE_TO_PAD,
        RESTOCK_PLACE,
        RESTOCK_OPEN,
        RESTOCK_LOOT,
        RESTOCK_CLOSE,
        RESTOCK_BREAK,
        RESTOCK_PICKUP,
        REPAIR,
        ARRIVED_WAIT,
        OUT_OF_ELYTRAS_WAIT,
        MINER
    }

    private enum LandingReason {
        RESTOCK,
        ARRIVAL,
        OUT_OF_ELYTRAS,
        REPAIR,
        BLOCKED,
        MINER
    }

    private enum RestockKind {
        NONE,
        FOOD,
        ELYTRA
    }

    private enum RestockSourcePhase {
        SELECT_SOURCE,
        EVICT_HOTBAR,
        MOVE_SOURCE,
        VERIFY_SOURCE,
        PLACE
    }

    private enum RepairStep {
        PLAN, MOVE, CHEST_PLACE, CHEST_OPEN, CHEST_TAKE, CHEST_CLOSE,
        SHULKER_PLACE, SHULKER_OPEN, SHULKER_TAKE, SHULKER_CLOSE, SHULKER_BREAK, SHULKER_PICKUP,
        MEND, RETURN_OPEN, RETURN_PUT, RETURN_CLOSE, CHEST_BREAK, CHEST_PICKUP, CLEANUP, DONE
    }

    private enum ResumeState {
        ROUTE_FLIGHT,
        ARRIVED_WAIT,
        OUT_OF_ELYTRAS_WAIT
    }

    private enum RestockFailureReason {
        NO_MATCHING_SHULKER,
        PLACE_FAILED,
        OPEN_FAILED,
        LOOT_TIMEOUT,
        TARGET_UNMET,
        NO_FLIGHT_READY_ELYTRA,
        PICKUP_FAILED
    }

    private record RestockPad(BlockPos playerBase, BlockPos containerBase) {}

    private record ShulkerCandidate(int slot, int foodCount, int flightReadyElytras) {}

    private State state;
    private LandingReason landingReason;
    private RestockKind activeRestockKind;
    private ResumeState resumeStateAfterRestock;
    private int stateTicks;
    private int takeoffFailures;
    private int actionDelayTicks;
    private boolean queuedFoodRestock;
    private boolean queuedElytraRestock;
    private boolean foodRestockSuppressed;
    private long homeSaveNextAtMs;
    private static final long HOME_COOLDOWN_MARGIN_MS = 15_000L;
    // Not reset with the route: the server cooldown keeps running across toggles.
    private long lastHomeCommandMs;
    private long lastHomeSetMs;
    private static final long HOME_OVERRIDE_WINDOW_MS = 5_000L;
    private long homeOverrideUntilMs;
    private String homeOverrideName = "";
    private boolean finalHomeSaveSent;
    private long routeStartMs;
    private double routeStartDistance;
    private boolean clampingFoodTypes;

    private FreeLook freeLookModule;
    private boolean managedFreeLook;
    private CameraType previousPerspective;
    private boolean perspectiveChanged;
    private long lastErrorMs;
    private static final long STOPPED_BY_ERROR_WINDOW_MS = 3_000L;

    private Rotation rotationModule;
    private Setting<Rotation.LockMode> rotationYawLockMode;
    private Setting<Double> rotationYawAngle;
    private Setting<Rotation.LockMode> rotationPitchLockMode;
    private Setting<Double> rotationPitchAngle;
    private boolean rotationSnapshotTaken;
    private boolean rotationWasActive;
    private Rotation.LockMode rotationPrevYawLockMode;
    private double rotationPrevYawAngle;
    private Rotation.LockMode rotationPrevPitchLockMode;
    private double rotationPrevPitchAngle;
    private boolean routeRotationOwned;

    private boolean bounceSettingsSnapshotTaken;
    private boolean bounceElytraFlyPaused;
    private ElytraFlightModes bouncePrevFlightMode;
    private Rotation.LockMode bouncePrevYawLockMode;
    private double bouncePrevYaw;
    private boolean bouncePrevAutoJump;
    private boolean bouncePrevManualTakeoff;
    private boolean bouncePrevRestart;

    private boolean reconnectListenersRegistered;
    private boolean previousAutoReconnectToggleState;
    private boolean routeStartPending;
    private boolean reconnectPaused;
    private boolean reconnectResumeDelayStarted;
    private boolean reconnectArmAnnounced;
    private boolean reconnectCycleOwnedByRoute;
    private boolean reconnectPreflightWarningAnnounced;
    private long activeReconnectCycleId;
    private int reconnectResumeDelayTicks;
    private int mainServerGateTicks;
    private long lastMainServerGateWarningAtMs;
    private long lastLandingChunkWarningAtMs;

    private RestockPad restockPad;
    private BlockPos restockPadSearchAnchor;
    private int restockPadSearchRadius;
    private int restockPadSearchX;
    private int restockPadSearchZ;
    private BlockPos placedShulkerPos;
    private RestockSourcePhase restockSourcePhase;
    private int restockSourceInventorySlot;
    private int restockSourceHotbarSlot;
    private RestockKind selectedRestockSourceKind;
    private Predicate<ItemStack> selectedRestockSourcePredicate;
    private String selectedRestockSourceLabel;
    private String selectedRestockSourceFingerprint;
    private int sourceReadyRetries;
    private int hotbarReadyRetries;
    private int pickupTicks;
    private ItemEntity pickupTarget;
    private int currentSourceFailureTicks;
    private int expectedShulkerCountAfterPickup;
    private int invalidRestockSourceAttempts;
    private boolean restockOpenedSourceValidated;
    private boolean openedInvalidRestockSource;
    private String invalidOpenedSourceFingerprint;
    private int invalidSourceOriginalHotbarSlot;
    private int invalidSourceParkingHotbarSlot;
    private boolean invalidSourceParkingPending;
    private ItemStack[] invalidSourcePreBreakSnapshot;
    private ItemStack[] invalidSourcePreParkingSnapshot;
    private RestockFailureReason deferredRestockFailureReason;
    private String deferredRestockFailureMessage;
    private final Set<Integer> elytraQuarantinedSlots = new HashSet<>();
    private final Set<String> elytraQuarantinedFingerprints = new HashSet<>();

    private final ElytraNavigator.Config navConfig = new ElytraNavigator.Config();
    private final WorldTerrain worldTerrain = new WorldTerrain();
    private final ElytraNavigator navigator = new ElytraNavigator(worldTerrain, navConfig);
    private ElytraNavigator.Steer navSteer;

    private final SettingColor mineSideColor = new SettingColor(255, 140, 0, 50);
    private static final double MINE_REACH = 4.4;
    private static final double MINE_LOOKAHEAD = 5.0;
    private static final int MINE_TARGET_TIMEOUT = 240;
    private boolean miningActive;
    private int debugTicks;
    private boolean miningHover;
    private int hoverClimb;
    private boolean miningSwapped;
    private double hoverY;
    private ItemStack mineTool = ItemStack.EMPTY;
    private int mineToolSlot = -1;
    private int mineToolCheckTick = -100;
    private BlockPos mineTarget;
    private int mineTargetTicks;
    private final Set<Long> mineFailed = new HashSet<>();
    private boolean minerStarted;
    private int minerRuns;
    private int takeoffClearTicks;
    private BlockPos fallbackGoal;
    private double fallbackBestDist;
    private long fallbackProgressMs;
    private boolean fallbackStopped;
    // Baritone settings changed for the fallback, restored when it ends.
    private boolean baritoneSettingsSaved;
    private boolean savedAllowBreak, savedAllowPlace;
    private static final double RESUME_FLIGHT_CHECK_DISTANCE = 16.0;
    private static final double RESUME_OPEN_HEIGHT = 4.0;
    private static final int TAKEOFF_CLEAR_TIMEOUT = 200;
    private static final double FALLBACK_GOAL_REACHED = 3.0;
    private static final int TAKEOFF_HEADROOM = 4;

    private RepairStep repairStep = RepairStep.PLAN;
    private boolean repairSuppressed;
    private boolean repairFailed;
    private boolean repairChestPlaced;
    private BlockPos repairChestPos;
    private BlockPos repairShulkerPos;
    private String repairShulkerKey;
    private int repairTicks;
    private int repairShulkerCount;
    private int repairChestCount;
    private int repairBottleCount;
    private int repairLastDamage;
    private int repairThrowsWithoutProgress;
    private boolean repairActionIssued;

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!smartPathing.get() || !renderPath.get() || mc.player == null) return;

        if (mineTarget != null) {
            RenderUtilsTHM.renderBlocks(event, List.of(mineTarget), mineSideColor, pathColor.get(), ShapeMode.Both);
        }

        List<ElytraPathPlanner.Pos> points = navigator.remainingPath();
        if (points.isEmpty()) return;

        int end = Math.min(points.size(), 200);
        for (int i = 0; i + 1 < end; i++) {
            ElytraPathPlanner.Pos a = points.get(i);
            ElytraPathPlanner.Pos b = points.get(i + 1);
            RenderUtilsTHM.renderLine(event, a.x() + 0.5, a.y() + 0.5, a.z() + 0.5, b.x() + 0.5, b.y() + 0.5, b.z() + 0.5, pathColor.get());
        }

        if (pathTracer.get()) {
            ElytraPathPlanner.Pos next = points.get(Math.min(1, points.size() - 1));
            RenderUtilsTHM.renderTracerToVec(event, new Vec3(next.x() + 0.5, next.y() + 0.5, next.z() + 0.5), pathColor.get());
        }
    }

    public int getDestinationX() {
        return destinationX.get();
    }

    public int getDestinationZ() {
        return destinationZ.get();
    }

    public double getArrivalRadius() {
        return arrivalRadius.get();
    }

    /** Retargets the route, e.g. to Stash Finder's next search corner. */
    public void setDestination(int x, int z) {
        destinationX.set(x);
        destinationZ.set(z);
        navigator.reset();
        navSteer = null;
    }

    public ElytraRoute() {
        super(THMAddon.MAIN, "elytra-route", "Flies to a target bearing, restocks from inventory shulkers, and manages periodic home saves.");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        resetRuntime();

        if (mc.player == null || mc.level == null) {
            error("Player or world was not available.");
            toggle();
            return;
        }

        if (!keybind.isSet()) {
            error("Set a module keybind before enabling Elytra Route.");
            toggle();
            return;
        }

        if (periodicHomeSave.get() && homeName.get().isBlank()) {
            error("Enter a home name before enabling periodic home save.");
            toggle();
            return;
        }

        if (periodicHomeSave.get() && homeName2.get().trim().equalsIgnoreCase(homeName.get().trim())) {
            error("The second home name must differ from the first.");
            toggle();
            return;
        }

        homeSaveNextAtMs = System.currentTimeMillis() + (homeSaveIntervalMinutes.get() * 60_000L);

        registerReconnectServiceListeners();
        if (!allowMainServerRouteActions("activate")) return;

        startRouteFromCurrentMainServerState("activate", true);
    }

    @Override
    public void onDeactivate() {
        releaseMovement();
        restoreRotationSnapshot();
        stopMining();
        mineFailed.clear();
        if (minerStarted) stopBaritoneFallback();
        minerStarted = false;
        minerRuns = 0;

        if (managedFreeLook && freeLookModule != null && freeLookModule.isActive()) {
            freeLookModule.toggle();
        }
        managedFreeLook = false;
        if (perspectiveChanged) {
            mc.options.setCameraType(previousPerspective);
            perspectiveChanged = false;
        }

        closeHandledRestockScreenIfOpen();
        clearSelectedRestockSource();
        clearInvalidSourceTracking();
        clearRestockQuarantine();

        if (!routeStartPending || bounceSettingsSnapshotTaken) {
            stopBounceElytraFlyForRouteStop();
            restoreBounceRouteSettings();
        }
        disarmOwnedReconnectIfNeeded("deactivate");
        unregisterReconnectServiceListeners();
        clearReconnectRuntime(true);
    }

    @EventHandler(priority = 1000)
    private void onTickPreForceFlightMovement(TickEvent.Pre event) {
        if (!shouldForceRouteFlightMovement()) return;
        forceRouteFlightMovement();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        handleAutoReconnectToggleTransitions();
        clearStaleDisconnectedScreenIfLiveConnected();

        if (mc.player == null || mc.level == null) {
            releaseMovement();
            return;
        }

        tickHomeOverride();
        if (!allowMainServerRouteActions("tick")) return;

        if (routeStartPending) {
            startRouteFromCurrentMainServerState("main-server-gate", true);
            return;
        }

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (!bounceElytraFlyPaused) configureBounceRouteMode(elytraFly, computeBearingYaw());

        if (!isElytraFlyCompatible(elytraFly, false)) {
            error("ElytraFly became inactive or incompatible while the route was running.");
            toggle();
            return;
        }

        if (actionDelayTicks > 0) actionDelayTicks--;
        if (foodRestockSuppressed && countConfiguredFoodItemsInInventory() > saveFood.get()) {
            foodRestockSuppressed = false;
        }

        if (state != State.FLIGHT && shouldPauseForEating()) {
            releaseMovement();
            restoreRotationSnapshot();
            return;
        }

        if (state == State.FLIGHT && periodicHomeSave.get() && isHomeSaveDue()) sendHomeSave();

        if (isRestockState(state)) {
            updateQueuedRestocksWhileRestocking();
        }

        if (state != State.FLIGHT) {
            navSteer = null;
            if (miningSwapped || miningHover) stopMining();
        }

        switch (state) {
            case TAKEOFF_AWAIT_GROUND -> tickTakeoffAwaitGround();
            case TAKEOFF_FIRST_JUMP -> tickTakeoffFirstJump();
            case TAKEOFF_SECOND_JUMP_WAIT -> tickTakeoffSecondJumpWait();
            case TAKEOFF_WAIT_GLIDE -> tickTakeoffWaitGlide();
            case FLIGHT -> tickFlight();
            case LANDING -> tickLanding();
            case RESTOCK_MOVE_TO_PAD -> tickRestockMoveToPad();
            case RESTOCK_PLACE -> tickRestockPlace();
            case RESTOCK_OPEN -> tickRestockOpen();
            case RESTOCK_LOOT -> tickRestockLoot();
            case RESTOCK_CLOSE -> tickRestockClose();
            case RESTOCK_BREAK -> tickRestockBreak();
            case RESTOCK_PICKUP -> tickRestockPickup();
            case REPAIR -> tickRepair();
            case ARRIVED_WAIT -> tickArrivedWait();
            case OUT_OF_ELYTRAS_WAIT -> tickOutOfElytrasWait();
            case MINER -> tickMinerFallback();
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (!shouldForceRouteFlightMovement()) return;
        forceRouteFlightMovement();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!isActive()) return;

        releaseMovement();

        if (autoReconnect.get()) {
            beginRouteReconnectPause("game-left", true);
        } else {
            disable();
        }
    }

    private boolean shouldForceRouteFlightMovement() {
        if (mc.player == null || mc.level == null) return false;
        if (!hasLiveServerConnection()) return false;
        if (routeStartPending || reconnectPaused || state != State.FLIGHT) return false;
        if (ServerStatusHandler.getInstance().getCommittedState() != ServerState.MAIN_SERVER) return false;
        return !isInsideArrivalRadius() && !needsFoodRestock() && !needsElytraRestock();
    }

    private void forceRouteFlightMovement() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (miningHover) {
            holdForMining();
            return;
        }

        mc.options.keyUp.setDown(true);

        if (shouldUseRouteBounceMode(elytraFly) && !mc.player.isFallFlying() && !mc.player.onGround()) {
            mc.options.keyJump.setDown(true);
            return;
        }

        if (!isBounceElytraFly(elytraFly)) applyFlightAltitudeControl(false);
    }

    public int getHudUsableElytraCount() {
        return countHudUsableElytras();
    }

    public long getHudFlightTimeSeconds() {
        return getTotalFlightTimeSeconds();
    }

    public double getHudEtaSeconds() {
        if (!isActive()) return -1;
        double remaining = getRemainingDistance();
        double progressed = Math.max(0, routeStartDistance - remaining);
        if (progressed < 5) return -1;

        long elapsedMs = System.currentTimeMillis() - routeStartMs;
        if (elapsedMs <= 0) return -1;

        double rate = progressed / elapsedMs;
        return rate <= 0 ? -1 : remaining / (rate * 1000.0);
    }

    public double getRemainingDistance() {
        if (mc.player == null) return 0;
        return Math.hypot(destinationX.get() - mc.player.getX(), destinationZ.get() - mc.player.getZ());
    }

    private void startRouteFromCurrentMainServerState(String source, boolean initialActivation) {
        if (mc.player == null || mc.level == null) return;

        routeStartPending = false;
        reconnectPaused = false;
        reconnectResumeDelayStarted = false;
        reconnectResumeDelayTicks = 0;
        mainServerGateTicks = 0;
        lastMainServerGateWarningAtMs = 0L;

        routeStartMs = System.currentTimeMillis();
        routeStartDistance = getRemainingDistance();

        initializeRouteSupportModules();
        if (autoReconnect.get()) armRouteReconnectIfNeeded(source + "-main-server");

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        configureBounceRouteMode(elytraFly, computeBearingYaw());
        if (!isElytraFlyCompatible(elytraFly, true)) {
            toggle();
            return;
        }

        if (getRemainingDistance() <= arrivalRadius.get()) {
            if (initialActivation) {
                error("Destination is already inside arrival radius.");
                toggle();
            } else {
                state = State.ARRIVED_WAIT;
                stateTicks = 0;
            }
            return;
        }

        if (needsFoodRestock()) {
            ResumeState resumeState = countTotalFlightReadyElytras() <= 0 && !restockElytras.get()
                ? ResumeState.OUT_OF_ELYTRAS_WAIT
                : ResumeState.ROUTE_FLIGHT;
            startRestockInterrupt(RestockKind.FOOD, resumeState);
            return;
        }

        if (needsRepair()) {
            beginRepair(mc.player.isFallFlying());
            return;
        }

        if (countTotalFlightReadyElytras() <= 0) {
            if (restockElytras.get()) startRestockInterrupt(RestockKind.ELYTRA, ResumeState.ROUTE_FLIGHT);
            else beginLanding(LandingReason.OUT_OF_ELYTRAS);
            return;
        }

        if (mc.player.isFallFlying()) {
            enterFlightState();
        } else {
            state = State.TAKEOFF_AWAIT_GROUND;
            stateTicks = 0;
        }
    }

    private void initializeRouteSupportModules() {
        freeLookModule = Modules.get().get(FreeLook.class);
        managedFreeLook = autoFreeLook.get() && freeLookModule != null;
        if (managedFreeLook && !freeLookModule.isActive()) {
            freeLookModule.toggle();
        }

        if (togglePerspective.get() && !perspectiveChanged) {
            previousPerspective = mc.options.getCameraType();
            if (previousPerspective != CameraType.THIRD_PERSON_BACK) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                perspectiveChanged = true;
            }
        }

        rotationModule = Modules.get().get(Rotation.class);
        cacheRotationSettings();
    }

    private boolean allowMainServerRouteActions(String source) {
        ServerState serverState = ServerStatusHandler.getInstance().getCommittedState();
        boolean liveConnection = hasLiveServerConnection();
        if (liveConnection && serverState == ServerState.MAIN_SERVER) {
            mainServerGateTicks = 0;
            lastMainServerGateWarningAtMs = 0L;

            if (reconnectPaused) {
                if (activeReconnectCycleId != 0L && !reconnectResumeDelayStarted) {
                    scheduleReconnectMainServerResume(activeReconnectCycleId, source);
                }

                if (reconnectResumeDelayTicks > 0) {
                    releaseMovement();
                    restoreRotationSnapshot();
                    reconnectResumeDelayTicks--;
                    return false;
                }

                resumeRouteAfterReconnect(source);
                return false;
            }

            return true;
        }

        // Release the route's keys once; doing it every tick locks the player in place in the lobby.
        if (++mainServerGateTicks == 1) {
            releaseMovement();
            restoreRotationSnapshot();
            closeHandledRestockScreenIfOpen();
            if (mc.player != null && !routeStartPending) pauseBounceElytraFlyForGroundControl();
        }

        if (serverState == ServerState.UNKNOWN
            && mainServerGateTicks < MAIN_SERVER_GATE_UNKNOWN_GRACE_TICKS
            && !reconnectPaused) {
            return false;
        }

        reconnectPaused = true;
        if (autoReconnect.get()) {
            String reconnectSource = liveConnection
                ? source + "-" + serverState.name().toLowerCase()
                : source + "-disconnected";
            armRouteReconnectIfNeeded(reconnectSource);
        } else {
            warnMainServerGate(serverState);
        }

        return false;
    }

    private void beginRouteReconnectPause(String source, boolean armReconnect) {
        releaseMovement();
        restoreRotationSnapshot();
        closeHandledRestockScreenIfOpen();
        if (mc.player != null && !routeStartPending) pauseBounceElytraFlyForGroundControl();

        reconnectPaused = true;
        reconnectResumeDelayStarted = false;
        reconnectResumeDelayTicks = 0;
        if (armReconnect && autoReconnect.get()) armRouteReconnectIfNeeded(source);
    }

    private void resumeRouteAfterReconnect(String source) {
        if (mc.player == null || mc.level == null) return;

        if (state == State.REPAIR) {
            warning("Repair was interrupted by the reconnect; any blocks placed for it were left where they are.");
            resetRepairRuntime();
        }

        // Restores Baritone's settings and drops its old goal; the next fallback starts fresh.
        if (minerStarted || baritoneSettingsSaved) stopBaritoneFallback();
        minerStarted = false;

        if (placedShulkerPos != null) {
            reconnectPaused = false;
            reconnectResumeDelayStarted = false;
            reconnectResumeDelayTicks = 0;
            state = State.RESTOCK_BREAK;
            stateTicks = 0;
            pickupTicks = 0;
            pickupTarget = null;
            currentSourceFailureTicks = 0;
            info("MAIN_SERVER restored after reconnect. Cleaning up placed restock shulker before resuming route.");
            return;
        }

        clearRestockRuntimeForReconnectResume();
        info("MAIN_SERVER restored after reconnect. Resuming Elytra Route.");
        startRouteFromCurrentMainServerState(source + "-resume", false);
    }

    private void scheduleReconnectMainServerResume(long cycleId, String context) {
        if (reconnectResumeDelayStarted && reconnectResumeDelayTicks > 0) return;

        reconnectPaused = true;
        reconnectResumeDelayStarted = true;
        reconnectResumeDelayTicks = MAIN_SERVER_RESUME_DELAY_TICKS;
        info(
            "Reconnect service reached MAIN_SERVER (%s). Waiting 6.0s before Elytra Route resume (cycle %d).",
            context == null ? "unknown" : context,
            cycleId
        );
    }

    private void warnMainServerGate(ServerState serverState) {
        long now = System.currentTimeMillis();
        if (now - lastMainServerGateWarningAtMs < MAIN_SERVER_GATE_WARNING_INTERVAL_MS) return;
        lastMainServerGateWarningAtMs = now;
        warning(
            "Elytra Route paused until MAIN_SERVER. Current server state is %s; enable auto-reconnect to recover automatically.",
            serverState.name()
        );
    }

    private void closeHandledRestockScreenIfOpen() {
        if (!isRestockState(state) && state != State.REPAIR) return;
        if (mc.gui.screen() instanceof AbstractContainerScreen<?>) mc.gui.screen().onClose();
    }

    private void handleAutoReconnectToggleTransitions() {
        boolean currentToggle = autoReconnect.get();
        if (currentToggle == previousAutoReconnectToggleState) return;

        if (currentToggle) {
            armRouteReconnectIfNeeded("toggle-on");
            info("Auto-reconnect enabled for Elytra Route.");
        } else {
            disarmOwnedReconnectIfNeeded("toggle-off");
            activeReconnectCycleId = 0L;
            reconnectCycleOwnedByRoute = false;
            reconnectArmAnnounced = false;
            reconnectPreflightWarningAnnounced = false;
            info("Auto-reconnect disabled for Elytra Route. Route reconnect state was cleared.");
        }

        previousAutoReconnectToggleState = currentToggle;
    }

    private boolean hasLiveServerConnection() {
        return mc != null
            && mc.player != null
            && mc.level != null
            && mc.getConnection() != null
            && mc.getConnection().getConnection() != null
            && mc.getConnection().getConnection().isConnected();
    }

    private void clearStaleDisconnectedScreenIfLiveConnected() {
        if (!hasLiveServerConnection()) return;
        if (!(mc.gui.screen() instanceof DisconnectedScreen)) return;
        info("Clearing stale DisconnectedScreen while Elytra Route is live in-world.");
        mc.gui.setScreen(null);
    }

    @Override
    public void error(String message, Object... args) {
        lastErrorMs = System.currentTimeMillis();
        super.error(message, args);
    }

    /** True when the route switched itself off because of an error just now, not because the player stopped it. */
    public boolean stoppedByError() {
        return !isActive() && System.currentTimeMillis() - lastErrorMs < STOPPED_BY_ERROR_WINDOW_MS;
    }

    private ServerReconnectService reconnectService() {
        return ServerReconnectService.getInstance();
    }

    private void registerReconnectServiceListeners() {
        if (reconnectListenersRegistered) return;
        reconnectService().registerResumeListener(RECONNECT_RESUME_LISTENER_KEY, this::onReconnectMainServerReady);
        reconnectService().registerFailureListener(RECONNECT_FAILURE_LISTENER_KEY, this::onReconnectFailure);
        reconnectListenersRegistered = true;
    }

    private void unregisterReconnectServiceListeners() {
        if (!reconnectListenersRegistered) return;
        reconnectService().unregisterResumeListener(RECONNECT_RESUME_LISTENER_KEY);
        reconnectService().unregisterFailureListener(RECONNECT_FAILURE_LISTENER_KEY);
        reconnectListenersRegistered = false;
    }

    private void onReconnectMainServerReady(long cycleId, String contextTag, long armedAtMs, long detectedAtMs) {
        if (mc != null && !mc.isSameThread()) {
            mc.execute(() -> onReconnectMainServerReady(cycleId, contextTag, armedAtMs, detectedAtMs));
            return;
        }

        if (!isActive()) return;
        if (activeReconnectCycleId == 0L || cycleId != activeReconnectCycleId) return;
        scheduleReconnectMainServerResume(cycleId, contextTag);
    }

    private void onReconnectFailure(
        long cycleId,
        ServerReconnectService.FailureReason reason,
        String detail,
        String contextTag,
        long armedAtMs,
        long failedAtMs
    ) {
        if (mc != null && !mc.isSameThread()) {
            mc.execute(() -> onReconnectFailure(cycleId, reason, detail, contextTag, armedAtMs, failedAtMs));
            return;
        }

        if (!isActive()) return;
        if (activeReconnectCycleId == 0L || cycleId != activeReconnectCycleId) return;

        warning("Elytra Route reconnect failed (%s): %s", reason.name(), detail == null ? "" : detail);
        clearReconnectRuntime(true);
        toggle();
    }

    private void armRouteReconnectIfNeeded(String source) {
        ServerReconnectService.ReconnectPreflight preflight = reconnectService().getReconnectPreflight();
        if (activeReconnectCycleId != 0L && preflight.serviceArmed() && preflight.cycleId() == activeReconnectCycleId) return;

        long cycleId;
        boolean owned;
        if (preflight.serviceArmed() && preflight.cycleId() > 0L) {
            cycleId = preflight.cycleId();
            owned = false;
        } else {
            cycleId = reconnectService().armReconnect(restartRejoinDelayMinutes.get(), "ElytraRoute:" + source);
            owned = true;
        }

        if (activeReconnectCycleId != cycleId) {
            reconnectArmAnnounced = false;
            reconnectPreflightWarningAnnounced = false;
        }
        activeReconnectCycleId = cycleId;
        reconnectCycleOwnedByRoute = owned;
        validateRouteReconnectPreflight(cycleId, source);

        if (!reconnectArmAnnounced) {
            info("Elytra Route AutoReconnect handling armed through ServerReconnectService (cycle %d).", cycleId);
            reconnectArmAnnounced = true;
        }
    }

    private void validateRouteReconnectPreflight(long cycleId, String source) {
        ServerReconnectService.ReconnectPreflight preflight = reconnectService().getReconnectPreflight();
        boolean valid = preflight.serviceArmed()
            && preflight.cycleId() == cycleId
            && preflight.autoReconnectModulePresent()
            && preflight.autoReconnectActive()
            && preflight.autoReconnectSettingDelaySeconds() != null
            && Math.abs(preflight.autoReconnectSettingDelaySeconds() - preflight.effectiveDelaySeconds()) <= 0.5
            && preflight.lastServerConnectionPresent();

        if (valid) {
            reconnectPreflightWarningAnnounced = false;
            return;
        }

        if (reconnectPreflightWarningAnnounced) return;
        reconnectPreflightWarningAnnounced = true;

        if (!preflight.serviceArmed() || preflight.cycleId() != cycleId) {
            warning("Elytra Route reconnect preflight issue (%s): reconnect service did not arm expected cycle %d.", source, cycleId);
        } else if (!preflight.autoReconnectModulePresent()) {
            warning("Elytra Route reconnect preflight issue (%s): Meteor AutoReconnect module was not found.", source);
        } else if (!preflight.autoReconnectActive()) {
            warning("Elytra Route reconnect preflight issue (%s): Meteor AutoReconnect is not active after arming.", source);
        } else if (preflight.autoReconnectSettingDelaySeconds() == null) {
            warning("Elytra Route reconnect preflight issue (%s): Meteor AutoReconnect delay could not be read.", source);
        } else if (Math.abs(preflight.autoReconnectSettingDelaySeconds() - preflight.effectiveDelaySeconds()) > 0.5) {
            warning(
                "Elytra Route reconnect preflight issue (%s): Meteor AutoReconnect delay %.1fs does not match service delay %ds.",
                source,
                preflight.autoReconnectSettingDelaySeconds(),
                preflight.effectiveDelaySeconds()
            );
        } else if (!preflight.lastServerConnectionPresent()) {
            warning("Elytra Route reconnect preflight issue (%s): Meteor AutoReconnect does not have a last-server target yet.", source);
        }
    }

    private void disarmOwnedReconnectIfNeeded(String reason) {
        if (activeReconnectCycleId == 0L || !reconnectCycleOwnedByRoute) return;
        ServerReconnectService.ReconnectPreflight preflight = reconnectService().getReconnectPreflight();
        if (preflight.serviceArmed() && preflight.cycleId() == activeReconnectCycleId) {
            reconnectService().disarmReconnect("ElytraRoute:" + reason);
        }
    }

    private void clearReconnectRuntime(boolean clearCycleBinding) {
        reconnectPaused = false;
        reconnectResumeDelayStarted = false;
        reconnectResumeDelayTicks = 0;
        mainServerGateTicks = 0;
        lastMainServerGateWarningAtMs = 0L;
        reconnectArmAnnounced = false;
        if (clearCycleBinding) {
            activeReconnectCycleId = 0L;
            reconnectCycleOwnedByRoute = false;
        }
    }

    private void clearSelectedRestockSource() {
        restockSourcePhase = RestockSourcePhase.SELECT_SOURCE;
        restockSourceInventorySlot = -1;
        restockSourceHotbarSlot = -1;
        selectedRestockSourceKind = RestockKind.NONE;
        selectedRestockSourcePredicate = null;
        selectedRestockSourceLabel = "unknown";
        selectedRestockSourceFingerprint = null;
        sourceReadyRetries = 0;
        hotbarReadyRetries = 0;
    }

    private void clearInvalidSourceTracking() {
        openedInvalidRestockSource = false;
        invalidOpenedSourceFingerprint = null;
        invalidSourceOriginalHotbarSlot = -1;
        invalidSourceParkingHotbarSlot = -1;
        invalidSourceParkingPending = false;
        invalidSourcePreBreakSnapshot = null;
        invalidSourcePreParkingSnapshot = null;
    }

    private void clearRestockQuarantine() {
        elytraQuarantinedSlots.clear();
        elytraQuarantinedFingerprints.clear();
    }

    private void clearRestockRuntimeForReconnectResume() {
        activeRestockKind = RestockKind.NONE;
        resumeStateAfterRestock = ResumeState.ROUTE_FLIGHT;
        queuedFoodRestock = false;
        queuedElytraRestock = false;
        restockPad = null;
        resetRestockPadSearch();
        placedShulkerPos = null;
        clearSelectedRestockSource();
        pickupTicks = 0;
        pickupTarget = null;
        currentSourceFailureTicks = 0;
        expectedShulkerCountAfterPickup = -1;
        invalidRestockSourceAttempts = 0;
        restockOpenedSourceValidated = false;
        clearInvalidSourceTracking();
        clearDeferredRestockFailure();
        resetRepairRuntime();
    }

    private void resetRuntime() {
        minerStarted = false;
        minerRuns = 0;
        mineFailed.clear();
        state = State.TAKEOFF_AWAIT_GROUND;
        landingReason = LandingReason.RESTOCK;
        activeRestockKind = RestockKind.NONE;
        resumeStateAfterRestock = ResumeState.ROUTE_FLIGHT;
        routeStartPending = true;
        previousAutoReconnectToggleState = autoReconnect.get();
        stateTicks = 0;
        takeoffFailures = 0;
        actionDelayTicks = 0;
        queuedFoodRestock = false;
        queuedElytraRestock = false;
        foodRestockSuppressed = false;
        managedFreeLook = false;
        rotationSnapshotTaken = false;
        routeRotationOwned = false;
        bounceSettingsSnapshotTaken = false;
        bounceElytraFlyPaused = false;
        restockPad = null;
        resetRestockPadSearch();
        placedShulkerPos = null;
        clearSelectedRestockSource();
        pickupTicks = 0;
        pickupTarget = null;
        currentSourceFailureTicks = 0;
        expectedShulkerCountAfterPickup = -1;
        invalidRestockSourceAttempts = 0;
        restockOpenedSourceValidated = false;
        clearInvalidSourceTracking();
        clearRestockQuarantine();
        lastLandingChunkWarningAtMs = 0L;
        clearReconnectRuntime(true);
        clearDeferredRestockFailure();
        repairSuppressed = false;
        resetRepairRuntime();
        navigator.reset();
        navSteer = null;
        finalHomeSaveSent = false;
        homeOverrideUntilMs = 0L;
    }

    private boolean isElytraFlyCompatible(ElytraFly elytraFly, boolean announce) {
        if (elytraFly == null) {
            if (announce) error("Enable ElytraFly before starting Elytra Route.");
            return false;
        }

        boolean routeBounceMode = shouldUseRouteBounceMode(elytraFly);
        boolean bounceMode = isBounceElytraFly(elytraFly);
        if (!elytraFly.isActive() && !routeBounceMode) {
            if (announce) error("Enable ElytraFly before starting Elytra Route.");
            return false;
        }

        ElytraFlightModes mode = elytraFly.flightMode.get();
        if (mode != ElytraFlightModes.Vanilla && mode != ElytraFlightModes.Packet && mode != ElytraFlightModes.Bounce) {
            String modeName = mode.name();
            if (announce) error("ElytraFly mode %s is not supported.", modeName);
            return false;
        }

        if (!bounceMode && elytraFly.autoTakeOff.get()) {
            if (announce) error("Disable ElytraFly auto-take-off.");
            return false;
        }

        if (!bounceMode && elytraFly.autoPilot.get()) {
            if (announce) error("Disable ElytraFly auto-pilot.");
            return false;
        }

        if (!bounceMode && elytraFly.useFireworks.get()) {
            if (announce) error("Disable ElytraFly use-fireworks.");
            return false;
        }

        if (!bounceMode && elytraFly.autoHover.get()) {
            if (announce) error("Disable ElytraFly auto-hover.");
            return false;
        }

        if (!elytraFly.replace.get()) {
            if (announce) error("Enable ElytraFly elytra-replace.");
            return false;
        }

        return true;
    }

    private boolean isBounceElytraFly(ElytraFly elytraFly) {
        return elytraFly != null && elytraFly.flightMode.get() == ElytraFlightModes.Bounce;
    }

    private void configureBounceRouteMode(ElytraFly elytraFly, float yaw) {
        if (!shouldUseRouteBounceMode(elytraFly)) return;

        takeBounceSettingsSnapshotIfNeeded(elytraFly);
        if (!isBounceElytraFly(elytraFly)) elytraFly.flightMode.set(ElytraFlightModes.Bounce);
        elytraFly.yawLockMode.set(Rotation.LockMode.Simple);
        elytraFly.yaw.set(toBounceYaw(yaw));
        elytraFly.manualTakeoff.set(false);
        elytraFly.autoJump.set(true);
        elytraFly.restart.set(true);
    }

    private void takeBounceSettingsSnapshotIfNeeded(ElytraFly elytraFly) {
        if (bounceSettingsSnapshotTaken || elytraFly == null) return;

        bounceSettingsSnapshotTaken = true;
        bouncePrevFlightMode = elytraFly.flightMode.get();
        bouncePrevYawLockMode = elytraFly.yawLockMode.get();
        bouncePrevYaw = elytraFly.yaw.get();
        bouncePrevAutoJump = elytraFly.autoJump.get();
        bouncePrevManualTakeoff = elytraFly.manualTakeoff.get();
        bouncePrevRestart = elytraFly.restart.get();
    }

    private void restoreBounceRouteSettings() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (!bounceSettingsSnapshotTaken || elytraFly == null) {
            bounceSettingsSnapshotTaken = false;
            bounceElytraFlyPaused = false;
            return;
        }

        if (bouncePrevFlightMode != null) elytraFly.flightMode.set(bouncePrevFlightMode);
        elytraFly.yawLockMode.set(bouncePrevYawLockMode);
        elytraFly.yaw.set(bouncePrevYaw);
        elytraFly.autoJump.set(bouncePrevAutoJump);
        elytraFly.manualTakeoff.set(bouncePrevManualTakeoff);
        elytraFly.restart.set(bouncePrevRestart);
        bounceSettingsSnapshotTaken = false;
        bounceElytraFlyPaused = false;
    }

    private boolean shouldUseRouteBounceMode(ElytraFly elytraFly) {
        return elytraFly != null && (useBounceMode.get() || isBounceElytraFly(elytraFly));
    }

    private double toBounceYaw(float yaw) {
        double normalized = normalizeYaw(yaw);
        return normalized < 0 ? normalized + 360.0 : normalized;
    }

    private void pauseBounceElytraFlyForGroundControl() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (!shouldUseRouteBounceMode(elytraFly) || bounceElytraFlyPaused) return;

        configureBounceRouteMode(elytraFly, computeBearingYaw());

        if (elytraFly.isActive()) {
            elytraFly.toggle();
            releaseMovement();
        }

        bounceElytraFlyPaused = true;
    }

    private boolean resumeBounceElytraFlyForFlight() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (!shouldUseRouteBounceMode(elytraFly)) return true;

        configureBounceRouteMode(elytraFly, computeBearingYaw());
        if (!elytraFly.isActive()) elytraFly.toggle();
        bounceElytraFlyPaused = false;
        return elytraFly.isActive();
    }

    private void stopBounceElytraFlyForRouteStop() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (!shouldUseRouteBounceMode(elytraFly)) return;

        if (elytraFly.isActive()) {
            elytraFly.toggle();
            releaseMovement();
        }

        bounceElytraFlyPaused = true;
    }

    @SuppressWarnings("unchecked")
    private void cacheRotationSettings() {
        if (rotationModule == null) return;

        rotationYawLockMode = (Setting<Rotation.LockMode>) rotationModule.settings.get("yaw-lock-mode");
        rotationYawAngle = (Setting<Double>) rotationModule.settings.get("yaw-angle");
        rotationPitchLockMode = (Setting<Rotation.LockMode>) rotationModule.settings.get("pitch-lock-mode");
        rotationPitchAngle = (Setting<Double>) rotationModule.settings.get("pitch-angle");
    }

    private void steerRouteYaw(float yaw) {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (isBounceElytraFly(elytraFly) && !bounceElytraFlyPaused) {
            configureBounceRouteMode(elytraFly, yaw);
            if (mc.player != null) mc.player.setYRot(normalizeYaw(yaw));
            return;
        }

        // Set the yaw directly: driving Meteor's Rotation module left it saved on (camera locked) when the game closed mid-route.
        if (mc.player != null) mc.player.setYRot(normalizeYaw(yaw));
    }

    private void restoreRotationSnapshot() {
        if (rotationModule == null || !rotationSnapshotTaken) return;
        if (rotationYawLockMode == null || rotationYawAngle == null || rotationPitchLockMode == null || rotationPitchAngle == null) return;

        if (routeRotationOwned && rotationModule.isActive()) rotationModule.toggle();

        rotationYawLockMode.set(rotationPrevYawLockMode);
        rotationYawAngle.set(rotationPrevYawAngle);
        rotationPitchLockMode.set(rotationPrevPitchLockMode);
        rotationPitchAngle.set(rotationPrevPitchAngle);

        if (rotationWasActive && !rotationModule.isActive()) rotationModule.toggle();

        routeRotationOwned = false;
    }

    private void enterFlightState() {
        state = State.FLIGHT;
        stateTicks = 0;
        navigator.reset();
        navSteer = null;
        steerRouteYaw(computeBearingYaw());
    }

    private void tickTakeoffAwaitGround() {
        releaseMovement();
        restoreRotationSnapshot();

        if (mc.player.isFallFlying()) {
            takeoffFailures = 0;
            enterFlightState();
            return;
        }

        if (!mc.player.onGround()) return;

        if (handleGroundedRouteInterruptBeforeTakeoff()) return;

        if (!hasElytraEquipped() && !equipFlightReadyElytraForTakeoff()) {
            error("Unable to equip a flight-ready elytra before takeoff.");
            toggle();
            return;
        }

        if (hasUnusableWornElytra() && hasFlightReadySpareInInventory() && !repairWornElytraIfNeeded()) {
            error("Unable to swap in a grounded backup elytra.");
            toggle();
            return;
        }

        if (actionDelayTicks > 0) return;

        if (!resumeBounceElytraFlyForFlight()) {
            error("Unable to resume ElytraFly Bounce mode for takeoff.");
            toggle();
            return;
        }

        mc.options.keyJump.setDown(true);
        state = State.TAKEOFF_FIRST_JUMP;
        stateTicks = 0;
    }

    private void tickTakeoffFirstJump() {
        mc.options.keyJump.setDown(stateTicks == 0);
        stateTicks++;

        if (stateTicks > 1) {
            mc.options.keyJump.setDown(false);
            state = State.TAKEOFF_SECOND_JUMP_WAIT;
            stateTicks = 0;
        }
    }

    private void tickTakeoffSecondJumpWait() {
        mc.options.keyJump.setDown(false);
        stateTicks++;

        if (stateTicks >= TAKEOFF_SECOND_JUMP_DELAY) {
            steerRouteYaw(computeBearingYaw());
            mc.options.keyJump.setDown(true);
            state = State.TAKEOFF_WAIT_GLIDE;
            stateTicks = 0;
        }
    }

    private void tickTakeoffWaitGlide() {
        steerRouteYaw(computeBearingYaw());
        mc.options.keyJump.setDown(stateTicks == 0);
        stateTicks++;

        if (mc.player.isFallFlying()) {
            takeoffFailures = 0;
            enterFlightState();
            return;
        }

        if (stateTicks > TAKEOFF_GLIDE_TIMEOUT) {
            mc.options.keyJump.setDown(false);
            restoreRotationSnapshot();
            takeoffFailures++;
            if (takeoffFailures >= TAKEOFF_MAX_FAILURES) {
                // No room to take off here: walk on with Baritone and try again further along.
                if (baritoneFallback.get() && minerRuns < 3) {
                    warning("Takeoff failed here. Walking on with Baritone to find more room.");
                    takeoffFailures = 0;
                    minerStarted = false;
                    state = State.MINER;
                    stateTicks = 0;
                    return;
                }
                error("Glide takeoff failed after %d attempts.", TAKEOFF_MAX_FAILURES);
                toggle();
                return;
            }

            state = State.TAKEOFF_AWAIT_GROUND;
            stateTicks = 0;
        }
    }

    private void tickFlight() {
        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        boolean routeBounceMode = shouldUseRouteBounceMode(elytraFly);

        if (!mc.player.isFallFlying()) {
            if (routeBounceMode && !mc.player.onGround()) {
                if (!resumeBounceElytraFlyForFlight()) {
                    error("Unable to enable ElytraFly Bounce mode for flight.");
                    toggle();
                    return;
                }

                if (handleFlightInterrupts()) return;

                float yaw = computeBearingYaw();
                steerRouteYaw(yaw);
                mc.options.keyUp.setDown(true);
                mc.options.keyJump.setDown(true);
                return;
            }

            releaseMovement();
            restoreRotationSnapshot();
            state = State.TAKEOFF_AWAIT_GROUND;
            stateTicks = 0;
            return;
        }

        if (!resumeBounceElytraFlyForFlight()) {
            error("Unable to enable ElytraFly Bounce mode for flight.");
            toggle();
            return;
        }

        if (handleFlightInterrupts()) return;

        float yaw = computeFlightYaw(elytraFly);
        if (navSteer != null && navSteer.mode() == ElytraNavigator.Mode.FAILED) {
            navSteer = null;
            stopMining();
            if (baritoneFallback.get() && minerRuns < 3) {
                warning("No way through the air was found. Landing to continue on foot with Baritone.");
                beginLanding(LandingReason.MINER);
            } else {
                warning("No way around the obstacle was found. Landing.");
                beginLanding(LandingReason.BLOCKED);
            }
            return;
        }

        steerRouteYaw(yaw);

        miningHover = tickFlightMining();
        if (debugRoute.get() && debugTicks++ % 20 == 0) printRouteDebug();
        if (miningHover) {
            holdForMining();
            return;
        }

        mc.options.keyUp.setDown(true);
        if (!isBounceElytraFly(elytraFly)) applyFlightAltitudeControl(false);
    }

    private boolean handleFlightInterrupts() {
        if (isInsideArrivalRadius()) {
            beginLanding(LandingReason.ARRIVAL);
            return true;
        }

        if (needsFoodRestock()) {
            startRestockInterrupt(RestockKind.FOOD, ResumeState.ROUTE_FLIGHT);
            return true;
        }

        if (needsRepair()) {
            beginRepair(true);
            return true;
        }

        if (needsElytraRestock()) {
            if (!restockElytras.get()) {
                beginLanding(LandingReason.OUT_OF_ELYTRAS);
            } else {
                startRestockInterrupt(RestockKind.ELYTRA, ResumeState.ROUTE_FLIGHT);
            }
            return true;
        }

        return false;
    }

    private void beginLanding(LandingReason reason) {
        landingReason = reason;
        state = State.LANDING;
        stateTicks = 0;
        releaseMovement();
        pauseBounceElytraFlyForGroundControl();
        if (reason == LandingReason.RESTOCK && mc.gui.screen() != null) mc.gui.screen().onClose();
    }

    private void tickLanding() {
        stopLandingHorizontalMovement();

        if (mc.player.isFallFlying() && !areLandingChunksLoaded()) {
            holdLandingForChunkLoad();
            warnLandingChunksUnloaded();
            return;
        }

        lastLandingChunkWarningAtMs = 0L;
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(mc.player.isFallFlying());

        if (mc.player.isFallFlying()) return;

        mc.options.keyShift.setDown(false);
        restoreRotationSnapshot();
        if (!mc.player.onGround()) return;

        switch (landingReason) {
            case ARRIVAL -> state = State.ARRIVED_WAIT;
            case OUT_OF_ELYTRAS -> state = State.OUT_OF_ELYTRAS_WAIT;
            case RESTOCK -> state = State.RESTOCK_MOVE_TO_PAD;
            case REPAIR -> state = State.REPAIR;
            case MINER -> {
                if (minerStarted || baritoneSettingsSaved) stopBaritoneFallback();
                minerStarted = false;
                state = State.MINER;
            }
            case BLOCKED -> {
                error("Elytra Route stopped: the way to the destination is blocked.");
                toggle();
                return;
            }
        }

        stateTicks = 0;
    }

    private void holdLandingForChunkLoad() {
        stopLandingHorizontalMovement();
        dampenLandingHorizontalVelocity();
        mc.options.keyShift.setDown(false);
        mc.options.keyJump.setDown(true);
    }

    private void stopLandingHorizontalMovement() {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
    }

    private void dampenLandingHorizontalVelocity() {
        if (mc.player == null) return;
        Vec3 velocity = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(velocity.x * 0.25, velocity.y, velocity.z * 0.25);
    }

    private boolean areLandingChunksLoaded() {
        if (mc.player == null || mc.level == null) return false;

        int centerChunkX = mc.player.getBlockX() >> 4;
        int centerChunkZ = mc.player.getBlockZ() >> 4;

        for (int x = -LANDING_CHUNK_LOAD_RADIUS; x <= LANDING_CHUNK_LOAD_RADIUS; x++) {
            for (int z = -LANDING_CHUNK_LOAD_RADIUS; z <= LANDING_CHUNK_LOAD_RADIUS; z++) {
                if (mc.level.getChunkSource().getChunk(centerChunkX + x, centerChunkZ + z, ChunkStatus.FULL, false) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    private void warnLandingChunksUnloaded() {
        long now = System.currentTimeMillis();
        if (now - lastLandingChunkWarningAtMs < LANDING_CHUNK_WARNING_INTERVAL_MS) return;
        lastLandingChunkWarningAtMs = now;
        warning("Landing paused until nearby chunks finish loading.");
    }

    private void startRestockInterrupt(RestockKind kind, ResumeState resumeState) {
        if (kind == RestockKind.NONE) return;

        boolean continuingRestockCycle = isRestockState(state) || (state == State.LANDING && landingReason == LandingReason.RESTOCK);
        if (!continuingRestockCycle) invalidRestockSourceAttempts = 0;

        if (kind == RestockKind.FOOD) {
            queuedFoodRestock = false;
            queuedElytraRestock = resumeState == ResumeState.ROUTE_FLIGHT && needsElytraRestock();
        } else {
            queuedElytraRestock = false;
            queuedFoodRestock = needsFoodRestock();
        }

        activeRestockKind = kind;
        resumeStateAfterRestock = resumeState;
        restockPad = null;
        resetRestockPadSearch();
        placedShulkerPos = null;
        clearSelectedRestockSource();
        pickupTicks = 0;
        pickupTarget = null;
        currentSourceFailureTicks = 0;
        expectedShulkerCountAfterPickup = -1;
        restockOpenedSourceValidated = false;
        clearInvalidSourceTracking();
        clearDeferredRestockFailure();

        beginLanding(LandingReason.RESTOCK);
    }

    private void updateQueuedRestocksWhileRestocking() {
        if (activeRestockKind != RestockKind.FOOD && needsFoodRestock()) queuedFoodRestock = true;
        if (resumeStateAfterRestock == ResumeState.ROUTE_FLIGHT
            && activeRestockKind != RestockKind.ELYTRA
            && needsElytraRestock()
            && !isInsideArrivalRadius()) {
            queuedElytraRestock = true;
        }
    }

    private void tickRestockMoveToPad() {
        releaseMovement();

        if (countEmptyLooseSlots() < minimumEmptySlots.get()) {
            handleRestockFailure(RestockFailureReason.TARGET_UNMET, "Not enough empty inventory slots remain for safe restocking.");
            return;
        }

        if (shouldRestockFoodThisPass() && !hasUsableFoodRestockTarget()) {
            handleRestockFailure(RestockFailureReason.TARGET_UNMET, "Not enough inventory capacity remains to satisfy the configured food restock target.");
            return;
        }

        if (shouldRestockElytraThisPass() && !hasUsableElytraRestockTarget()) {
            handleRestockFailure(RestockFailureReason.TARGET_UNMET, "Not enough inventory capacity remains to satisfy the configured elytra restock target.");
            return;
        }

        if (restockPad == null) {
            restockPad = findNextRestockPad();
            if (restockPad == null) return;
        }

        moveToward(restockPad.playerBase.getX() + 0.5, restockPad.playerBase.getZ() + 0.5, () -> {
            releaseMovement();
            state = State.RESTOCK_PLACE;
            stateTicks = 0;
        });
    }

    private void tickRestockPlace() {
        if (actionDelayTicks > 0) return;

        if (isCurrentRestockRequestSatisfied()) {
            finishCurrentRestockPass();
            return;
        }

        if (restockSourcePhase == null) restockSourcePhase = RestockSourcePhase.SELECT_SOURCE;

        switch (restockSourcePhase) {
            case SELECT_SOURCE -> selectRestockSourceForPlacement();
            case EVICT_HOTBAR -> evictUnhelpfulHotbarShulkerForRestock();
            case MOVE_SOURCE -> moveSelectedRestockSourceToHotbar();
            case VERIFY_SOURCE -> verifySelectedRestockSource();
            case PLACE -> placeVerifiedRestockSource();
        }
    }

    private RestockKind getCurrentPrimaryRestockNeed() {
        boolean needFood = shouldRestockFoodThisPass() && needsFoodRestock();
        boolean needElytra = needsElytraSourceThisPass();
        return getPrimaryRestockNeed(needFood, needElytra);
    }

    private void selectRestockSourceForPlacement() {
        RestockKind primary = getCurrentPrimaryRestockNeed();
        if (primary == RestockKind.NONE) {
            finishCurrentRestockPass();
            return;
        }

        ShulkerCandidate candidate = findBestInventoryShulker();
        if (candidate == null) {
            handleRestockFailure(RestockFailureReason.NO_MATCHING_SHULKER, "No matching inventory shulker was found for the current restock need.");
            return;
        }

        RestockKind selectedKind = primary;
        restockSourceInventorySlot = candidate.slot();
        restockSourceHotbarSlot = SlotUtils.isHotbar(candidate.slot()) ? candidate.slot() : -1;
        selectedRestockSourceKind = selectedKind;
        selectedRestockSourcePredicate = stack -> isValidRestockSourceForKind(-1, stack, selectedKind);
        selectedRestockSourceLabel = selectedKind == RestockKind.ELYTRA ? "elytra shulker" : "food shulker";
        selectedRestockSourceFingerprint = null;
        sourceReadyRetries = 0;

        restockSourcePhase = SlotUtils.isHotbar(restockSourceInventorySlot)
            ? RestockSourcePhase.VERIFY_SOURCE
            : RestockSourcePhase.MOVE_SOURCE;
    }

    private void moveSelectedRestockSourceToHotbar() {
        if (!isSelectedInventorySourceStillValid()) {
            clearSelectedRestockSource();
            actionDelayTicks = Math.max(1, inventoryDelay.get());
            return;
        }

        if (SlotUtils.isHotbar(restockSourceInventorySlot)) {
            restockSourceHotbarSlot = restockSourceInventorySlot;
            restockSourcePhase = RestockSourcePhase.VERIFY_SOURCE;
            return;
        }

        int hotbarSlot = findHotbarMoveTarget();
        if (hotbarSlot == -1) {
            int hotbarSourceSlot = findValidHotbarRestockSourceSlot(selectedRestockSourceKind);
            if (hotbarSourceSlot != -1) {
                restockSourceInventorySlot = hotbarSourceSlot;
                restockSourceHotbarSlot = hotbarSourceSlot;
                restockSourcePhase = RestockSourcePhase.VERIFY_SOURCE;
                return;
            }

            restockSourcePhase = RestockSourcePhase.EVICT_HOTBAR;
            return;
        }

        InvUtils.move().from(restockSourceInventorySlot).toHotbar(hotbarSlot);
        if (!clearCursorStackToEmptySlot()) {
            clearSelectedRestockSource();
            handleRestockFailure(RestockFailureReason.PLACE_FAILED, "Unable to clear cursor after moving the restock shulker.");
            return;
        }

        restockSourceHotbarSlot = hotbarSlot;
        restockSourcePhase = RestockSourcePhase.VERIFY_SOURCE;
        actionDelayTicks = Math.max(1, inventoryDelay.get());
    }

    private void evictUnhelpfulHotbarShulkerForRestock() {
        int evictSlot = findEvictableUnhelpfulHotbarShulkerSlot();
        if (evictSlot == -1) {
            hotbarReadyRetries++;
            if (hotbarReadyRetries >= SOURCE_READY_MAX_RETRIES) {
                clearSelectedRestockSource();
                handleRestockFailure(RestockFailureReason.PLACE_FAILED, "Unable to find a safe hotbar slot for the restock shulker.");
                return;
            }

            actionDelayTicks = Math.max(1, inventoryDelay.get());
            return;
        }

        shiftClickHotbarItemOut(evictSlot);
        clearSelectedRestockSource();
        actionDelayTicks = Math.max(1, inventoryDelay.get());
    }

    private void verifySelectedRestockSource() {
        if (!isSelectedRestockSourceReady()) {
            sourceReadyRetries++;
            if (sourceReadyRetries >= SOURCE_READY_MAX_RETRIES) {
                invalidRestockSourceAttempts++;
                clearSelectedRestockSource();

                if (invalidRestockSourceAttempts > RESTOCK_INVALID_SOURCE_RETRY_LIMIT) {
                    handleRestockFailure(RestockFailureReason.PLACE_FAILED, "Restock shulker source readiness did not stabilize.");
                    return;
                }
            }

            actionDelayTicks = Math.max(1, inventoryDelay.get());
            return;
        }

        selectedRestockSourceFingerprint = fingerprintShulkerStack(mc.player.getInventory().getItem(restockSourceHotbarSlot));
        sourceReadyRetries = 0;
        restockSourcePhase = RestockSourcePhase.PLACE;
        placeVerifiedRestockSource();
    }

    private void placeVerifiedRestockSource() {
        if (!isSelectedRestockSourceReady()) {
            restockSourcePhase = RestockSourcePhase.VERIFY_SOURCE;
            verifySelectedRestockSource();
            return;
        }

        if (selectedRestockSourceFingerprint == null) {
            selectedRestockSourceFingerprint = fingerprintShulkerStack(mc.player.getInventory().getItem(restockSourceHotbarSlot));
        }

        BlockPos placePos = restockPad.containerBase.above();
        expectedShulkerCountAfterPickup = countInventoryShulkerBoxes();
        clearInvalidSourceTracking();
        invalidSourceOriginalHotbarSlot = restockSourceHotbarSlot;

        boolean placed = BlockUtils.place(placePos, InteractionHand.MAIN_HAND, restockSourceHotbarSlot, false, 0, true, true, true);
        if (!placed) {
            expectedShulkerCountAfterPickup = -1;
            handleRestockFailure(RestockFailureReason.PLACE_FAILED, "Failed to place the restock shulker.");
            return;
        }

        placedShulkerPos = placePos.immutable();
        actionDelayTicks = inventoryDelay.get();
        currentSourceFailureTicks = 0;
        restockOpenedSourceValidated = false;
        state = State.RESTOCK_OPEN;
    }

    private void tickRestockOpen() {
        if (placedShulkerPos == null) {
            handleRestockFailure(RestockFailureReason.OPEN_FAILED, "Placed shulker position was lost before opening.");
            return;
        }

        currentSourceFailureTicks++;
        if (currentSourceFailureTicks == 1) {
            mc.gameMode.useItemOn(
                mc.player,
                InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(placedShulkerPos), net.minecraft.core.Direction.UP, placedShulkerPos, false)
            );
            return;
        }

        if (mc.gui.screen() != null && mc.player.containerMenu != null && mc.player.containerMenu.slots.size() > PLAYER_INVENTORY_SLOTS) {
            currentSourceFailureTicks = 0;
            actionDelayTicks = inventoryDelay.get();
            state = State.RESTOCK_LOOT;
            return;
        }

        if (currentSourceFailureTicks > 40) {
            handleRestockFailure(RestockFailureReason.OPEN_FAILED, "The restock shulker did not open.");
        }
    }

    private void tickRestockLoot() {
        if (mc.gui.screen() == null || mc.player.containerMenu == null) {
            state = State.RESTOCK_CLOSE;
            return;
        }

        if (actionDelayTicks > 0) return;

        boolean wantFood = shouldRestockFoodThisPass();
        boolean wantElytra = shouldRestockElytraThisPass();

        if (!restockOpenedSourceValidated) {
            restockOpenedSourceValidated = true;
            if (activeRestockKind == RestockKind.ELYTRA && findBestFlightReadyElytraContainerSlot() == -1) {
                openedInvalidRestockSource = true;
                invalidOpenedSourceFingerprint = fingerprintOpenedRestockContainer();
                invalidRestockSourceAttempts++;
                state = State.RESTOCK_CLOSE;
                currentSourceFailureTicks = 0;
                return;
            }
        }

        if (wantElytra) {
            int containerSlot = findBestFlightReadyElytraContainerSlot();
            int brokenInventorySlot = findReplaceableLooseElytraSlot();
            if (containerSlot != -1 && brokenInventorySlot != -1) {
                swapContainerElytraWithBrokenInventorySlot(containerSlot, brokenInventorySlot);
                actionDelayTicks = inventoryDelay.get();
                return;
            }
        }

        if (wantElytra && canPullMoreLooseElytras()) {
            int containerSlot = findBestFlightReadyElytraContainerSlot();
            if (containerSlot != -1) {
                InvUtils.shiftClick().slotId(containerSlot);
                actionDelayTicks = inventoryDelay.get();
                return;
            }
        }

        if (wantFood && shouldPullMoreConfiguredFood()) {
            int containerSlot = findBestFoodContainerSlot();
            if (containerSlot != -1) {
                InvUtils.shiftClick().slotId(containerSlot);
                actionDelayTicks = inventoryDelay.get();
                return;
            }
        }

        currentSourceFailureTicks++;
        if (currentSourceFailureTicks > 20) {
            state = State.RESTOCK_CLOSE;
            currentSourceFailureTicks = 0;
        }
    }

    private void tickRestockClose() {
        if (mc.player.containerMenu != null && !mc.player.containerMenu.getCarried().isEmpty()) {
            clearCursorAfterSwap();
            actionDelayTicks = inventoryDelay.get();
            return;
        }

        if (mc.gui.screen() != null) {
            mc.gui.screen().onClose();
            actionDelayTicks = inventoryDelay.get();
            return;
        }

        state = State.RESTOCK_BREAK;
        stateTicks = 0;
    }

    private void tickRestockBreak() {
        if (placedShulkerPos == null) {
            state = State.RESTOCK_PICKUP;
            pickupTicks = 0;
            return;
        }

        if (openedInvalidRestockSource
            && ((mc.player.containerMenu != null && !mc.player.containerMenu.getCarried().isEmpty()) || mc.gui.screen() != null)) {
            state = State.RESTOCK_CLOSE;
            return;
        }

        if (openedInvalidRestockSource && invalidSourcePreBreakSnapshot == null) {
            invalidSourcePreBreakSnapshot = snapshotPlayerInventory();
        }

        if (mc.level.getBlockState(placedShulkerPos).getBlock() instanceof ShulkerBoxBlock) {
            BlockUtils.breakBlock(placedShulkerPos, true);
            return;
        }

        pickupTicks = 0;
        pickupTarget = null;
        state = State.RESTOCK_PICKUP;
    }

    private void tickRestockPickup() {
        if (actionDelayTicks > 0) return;

        if (invalidSourceParkingPending) {
            finishVerifiedShulkerPickup();
            return;
        }

        if (isPlacedShulkerPickedUp()) {
            finishVerifiedShulkerPickup();
            return;
        }

        List<ItemEntity> drops = collectShulkerDrops();
        if (!drops.isEmpty()) {
            pickupTicks++;

            if (countEmptyLooseSlots() <= 0 && pickupTicks % SHULKER_PICKUP_REACQUIRE_INTERVAL == 0) {
                dropReplaceableLooseElytraForPickup();
            }

            if (pickupTarget == null || !pickupTarget.isAlive() || pickupTicks % SHULKER_PICKUP_REACQUIRE_INTERVAL == 0) {
                pickupTarget = drops.stream()
                    .min(Comparator.comparingDouble(entity -> mc.player.distanceToSqr(entity.getX(), entity.getY(), entity.getZ())))
                    .orElse(null);
            }

            if (pickupTarget != null) {
                moveToward(pickupTarget.getX(), pickupTarget.getZ(), this::releaseMovement);
            } else {
                releaseMovement();
            }

            if (pickupTicks > SHULKER_PICKUP_TIMEOUT) {
                handleRestockFailure(RestockFailureReason.PICKUP_FAILED, "Restock shulker was not picked up in time.");
            }
            return;
        }

        pickupTicks++;
        pickupTarget = null;
        releaseMovement();

        if (pickupTicks > SHULKER_PICKUP_TIMEOUT) {
            handleRestockFailure(RestockFailureReason.PICKUP_FAILED, "Restock shulker pickup could not be verified in time.");
        }
    }

    private boolean isPlacedShulkerPickedUp() {
        return expectedShulkerCountAfterPickup >= 0 && countInventoryShulkerBoxes() >= expectedShulkerCountAfterPickup;
    }

    private boolean handleInvalidRestockSourcePickup() {
        quarantineInvalidSourceFingerprint();

        if (invalidSourceParkingPending) {
            return finishInvalidSourceParking();
        }

        ItemStack[] afterPickup = snapshotPlayerInventory();
        int returnedSlot = findChangedShulkerSlot(invalidSourcePreBreakSnapshot, afterPickup, invalidOpenedSourceFingerprint, false);
        if (returnedSlot == -1 && invalidSourceOriginalHotbarSlot >= 0 && SlotUtils.isHotbar(invalidSourceOriginalHotbarSlot)) {
            ItemStack originalSlotStack = mc.player.getInventory().getItem(invalidSourceOriginalHotbarSlot);
            if (isShulkerBox(originalSlotStack) && matchesShulkerFingerprint(originalSlotStack, invalidOpenedSourceFingerprint)) {
                returnedSlot = invalidSourceOriginalHotbarSlot;
            }
        }

        if (returnedSlot >= 0 && SlotUtils.isHotbar(returnedSlot) && hasEmptyMainInventorySlot()) {
            invalidSourcePreParkingSnapshot = snapshotPlayerInventory();
            invalidSourceParkingHotbarSlot = returnedSlot;
            invalidSourceParkingPending = true;
            shiftClickHotbarItemOut(returnedSlot);
            actionDelayTicks = Math.max(1, inventoryDelay.get());
            return false;
        }

        if (returnedSlot != -1) {
            blockElytraQuarantinedSlot(returnedSlot);
        } else if (invalidSourceOriginalHotbarSlot >= 0 && SlotUtils.isHotbar(invalidSourceOriginalHotbarSlot)) {
            blockElytraQuarantinedSlot(invalidSourceOriginalHotbarSlot);
        }

        return true;
    }

    private boolean finishInvalidSourceParking() {
        ItemStack[] afterParking = snapshotPlayerInventory();
        int parkedSlot = findChangedShulkerSlot(invalidSourcePreParkingSnapshot, afterParking, invalidOpenedSourceFingerprint, true);
        if (parkedSlot == -1) {
            parkedSlot = findMatchingShulkerSlot(invalidOpenedSourceFingerprint, 9, PLAYER_INVENTORY_SLOTS);
        }

        if (parkedSlot != -1) {
            blockElytraQuarantinedSlot(parkedSlot);
        } else if (invalidSourceParkingHotbarSlot >= 0 && SlotUtils.isHotbar(invalidSourceParkingHotbarSlot)) {
            blockElytraQuarantinedSlot(invalidSourceParkingHotbarSlot);
        } else if (invalidSourceOriginalHotbarSlot >= 0 && SlotUtils.isHotbar(invalidSourceOriginalHotbarSlot)) {
            blockElytraQuarantinedSlot(invalidSourceOriginalHotbarSlot);
        }

        invalidSourceParkingPending = false;
        invalidSourceParkingHotbarSlot = -1;
        invalidSourcePreParkingSnapshot = null;
        return true;
    }

    private void finishVerifiedShulkerPickup() {
        RestockFailureReason deferredReason = deferredRestockFailureReason;
        String deferredMessage = deferredRestockFailureMessage;

        releaseMovement();
        placedShulkerPos = null;
        restockPad = null;
        resetRestockPadSearch();
        pickupTicks = 0;
        pickupTarget = null;
        expectedShulkerCountAfterPickup = -1;
        clearDeferredRestockFailure();

        if (openedInvalidRestockSource) {
            if (!handleInvalidRestockSourcePickup()) return;

            clearInvalidSourceTracking();
            clearSelectedRestockSource();
            restockOpenedSourceValidated = false;

            if (invalidRestockSourceAttempts > RESTOCK_INVALID_SOURCE_RETRY_LIMIT) {
                handleRestockFailure(RestockFailureReason.NO_MATCHING_SHULKER, "Too many invalid elytra shulker sources were opened.");
                return;
            }

            if (hasFlightReadyElytraShulkerInInventory()) {
                startRestockInterrupt(RestockKind.ELYTRA, resumeStateAfterRestock);
                return;
            }

            if (countTotalFlightReadyElytras() > 0) {
                resumeAfterPartialElytraRestock("No more valid elytra shulkers were found after cleaning up an invalid source.");
                return;
            }

            handleRestockFailure(RestockFailureReason.NO_FLIGHT_READY_ELYTRA, "No flight-ready elytras were found in remaining shulkers.");
            return;
        }

        clearSelectedRestockSource();

        if (deferredReason != null) {
            applyRestockFailureOutcome(deferredReason, deferredMessage);
            return;
        }

        if (requiresFlightReadyStateAfterRestock() && !repairWornElytraIfNeeded()) {
            handleRestockFailure(RestockFailureReason.NO_FLIGHT_READY_ELYTRA, "No flight-ready elytra remained after restock.");
            return;
        }

        finishCurrentRestockPass();
    }

    private void finishCurrentRestockPass() {
        RestockKind completed = activeRestockKind;
        activeRestockKind = RestockKind.NONE;

        if (completed == RestockKind.FOOD && !needsFoodRestock()) queuedFoodRestock = false;
        if (completed == RestockKind.ELYTRA && hasSatisfiedLooseElytraTargetAfterRepair()) queuedElytraRestock = false;

        if (shouldContinueElytraRestockAfterPass(completed)) {
            if (!restockElytras.get()) {
                state = State.OUT_OF_ELYTRAS_WAIT;
            } else {
                startRestockInterrupt(RestockKind.ELYTRA, resumeStateAfterRestock);
            }
            return;
        }

        if (shouldAcceptPartialElytraRestock(completed)) {
            resumeAfterPartialElytraRestock("Elytra restock target was only partially met because no more valid elytra shulkers were found.");
            return;
        }

        if (queuedFoodRestock && !foodRestockSuppressed && needsFoodRestock()) {
            startRestockInterrupt(RestockKind.FOOD, resumeStateAfterRestock);
            return;
        }

        switch (resumeStateAfterRestock) {
            case ARRIVED_WAIT -> {
                state = State.ARRIVED_WAIT;
                return;
            }
            case OUT_OF_ELYTRAS_WAIT -> {
                state = State.OUT_OF_ELYTRAS_WAIT;
                return;
            }
            case ROUTE_FLIGHT -> {
                if (isInsideArrivalRadius()) {
                    state = State.ARRIVED_WAIT;
                    return;
                }

                if (countTotalFlightReadyElytras() <= 0) {
                    state = State.OUT_OF_ELYTRAS_WAIT;
                    return;
                }

                if (needsFoodRestock() && !foodRestockSuppressed) {
                    startRestockInterrupt(RestockKind.FOOD, ResumeState.ROUTE_FLIGHT);
                    return;
                }

                if (shouldContinueElytraRestockAfterPass(completed) && !isInsideArrivalRadius()) {
                    if (!restockElytras.get()) {
                        state = State.OUT_OF_ELYTRAS_WAIT;
                        return;
                    }
                    startRestockInterrupt(RestockKind.ELYTRA, ResumeState.ROUTE_FLIGHT);
                    return;
                }

                state = State.TAKEOFF_AWAIT_GROUND;
                stateTicks = 0;
            }
        }
    }

    private void tickArrivedWait() {
        releaseMovement();
        restoreRotationSnapshot();

        if (needsFoodRestock() && !foodRestockSuppressed) {
            startRestockInterrupt(RestockKind.FOOD, ResumeState.ARRIVED_WAIT);
            return;
        }

        if (!periodicHomeSave.get()) {
            info("Arrived at destination.");
            toggle();
            return;
        }

        // Wait out a possible override dialog so the route does not switch off with it still open.
        if (finalHomeSaveSent) {
            if (isHomeOverrideBusy()) return;
            info("Arrived at destination and saved home.");
            toggle();
            return;
        }

        // With two homes this can take one extra cooldown: the older home is deleted first, then the new one set.
        if (!isHomeSaveDue()) return;
        if (sendHomeSave()) finalHomeSaveSent = true;
    }

    private void tickOutOfElytrasWait() {
        releaseMovement();
        restoreRotationSnapshot();

        if (needsFoodRestock() && !foodRestockSuppressed) {
            startRestockInterrupt(RestockKind.FOOD, ResumeState.OUT_OF_ELYTRAS_WAIT);
            return;
        }

        if (periodicHomeSave.get()) {
            if (!finalHomeSaveSent) {
                if (!isHomeSaveDue() || !sendHomeSave()) return;
                finalHomeSaveSent = true;
            }
            if (isHomeOverrideBusy()) return;
        }

        String reason = "[ElytraRoute] Out of flight-ready elytras.";
        warning("Out of elytras. Disconnecting.");
        toggle();
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal(reason));
        }
    }

    private boolean shouldPauseForEating() {
        if (isActivelyEating()) return true;
        if (isFoodRestockFlowActive()) return false;
        return doesAutoEatWantToEat() || doesAutoGapWantToEat();
    }

    private boolean isActivelyEating() {
        return isAutoEatEating()
            || isAutoGapEating()
            || shouldPauseForActiveFoodUse();
    }

    private boolean isAutoEatEating() {
        AutoEat autoEat = Modules.get().get(AutoEat.class);
        if (autoEat == null || !autoEat.isActive() || mc.player == null) return false;
        return autoEat.eating;
    }

    private boolean doesAutoEatWantToEat() {
        AutoEat autoEat = Modules.get().get(AutoEat.class);
        if (autoEat == null || !autoEat.isActive() || mc.player == null) return false;
        return autoEat.shouldEat();
    }

    private boolean isAutoGapEating() {
        AutoGap autoGap = Modules.get().get(AutoGap.class);
        if (autoGap == null || !autoGap.isActive() || mc.player == null) return false;
        if (autoGap.isEating()) return true;
        return mc.player.isUsingItem() && isUsingGapLikeFood(mc.player.getUseItem());
    }

    @SuppressWarnings("unchecked")
    private boolean doesAutoGapWantToEat() {
        AutoGap autoGap = Modules.get().get(AutoGap.class);
        if (autoGap == null || !autoGap.isActive() || mc.player == null) return false;

        Setting<Boolean> alwaysSetting = (Setting<Boolean>) autoGap.settings.get("always");
        Setting<Boolean> allowEgapSetting = (Setting<Boolean>) autoGap.settings.get("allow-egap");
        if (alwaysSetting == null || allowEgapSetting == null) return false;

        boolean requiresEGap = false;
        if (!alwaysSetting.get()) {
            boolean regenTrigger = shouldAutoGapForRegeneration(autoGap);
            requiresEGap = requiresEGapForPotions(autoGap, allowEgapSetting.get());
            if (!regenTrigger && !requiresEGap && !shouldAutoGapForHealth(autoGap)) return false;
        }

        return hasAutoGapHotbarFood(allowEgapSetting.get(), requiresEGap);
    }

    private boolean isFoodRestockFlowActive() {
        return needsFoodRestock()
            || shouldRestockFoodThisPass()
            || (state == State.LANDING && landingReason == LandingReason.RESTOCK && shouldRestockFoodThisPass());
    }

    @SuppressWarnings("unchecked")
    private boolean shouldAutoGapForHealth(AutoGap autoGap) {
        Setting<Boolean> healthEnabledSetting = (Setting<Boolean>) autoGap.settings.get("health-enabled");
        Setting<Integer> healthThresholdSetting = autoGap.settings.get("health-threshold", Integer.class);
        if (healthEnabledSetting == null || healthThresholdSetting == null || !healthEnabledSetting.get() || mc.player == null) return false;

        int health = Math.round(mc.player.getHealth() + mc.player.getAbsorptionAmount());
        return health < healthThresholdSetting.get();
    }

    @SuppressWarnings("unchecked")
    private boolean shouldAutoGapForRegeneration(AutoGap autoGap) {
        Setting<Boolean> regenSetting = (Setting<Boolean>) autoGap.settings.get("potions-regeneration");
        return regenSetting != null && regenSetting.get() && isPotionMissingOrExpiring(MobEffects.REGENERATION, autoGap);
    }

    @SuppressWarnings("unchecked")
    private boolean requiresEGapForPotions(AutoGap autoGap, boolean allowEgap) {
        if (!allowEgap) return false;

        Setting<Boolean> fireResSetting = (Setting<Boolean>) autoGap.settings.get("potions-fire-resistance");
        if (fireResSetting != null && fireResSetting.get() && isPotionMissingOrExpiring(MobEffects.FIRE_RESISTANCE, autoGap)) return true;

        Setting<Boolean> absorptionSetting = (Setting<Boolean>) autoGap.settings.get("potions-absorption");
        return absorptionSetting != null && absorptionSetting.get() && isPotionMissingOrExpiring(MobEffects.ABSORPTION, autoGap);
    }

    @SuppressWarnings("unchecked")
    private boolean isPotionMissingOrExpiring(Holder<MobEffect> effect, AutoGap autoGap) {
        if (mc.player == null) return false;

        Setting<Boolean> beforeExpirySetting = (Setting<Boolean>) autoGap.settings.get("before-expiry");
        Setting<Integer> expiryThresholdSetting = autoGap.settings.get("expiry-threshold", Integer.class);

        MobEffectInstance instance = mc.player.getEffect(effect);
        if (instance == null) return true;
        return beforeExpirySetting != null
            && beforeExpirySetting.get()
            && expiryThresholdSetting != null
            && instance.getDuration() <= expiryThresholdSetting.get();
    }

    private boolean hasAutoGapHotbarFood(boolean allowEgap, boolean requiresEGap) {
        if (mc.player == null) return false;

        for (int i = 0; i < 9; i++) {
            Item item = mc.player.getInventory().getItem(i).getItem();
            if (item == Items.ENCHANTED_GOLDEN_APPLE && allowEgap) return true;
            if (item == Items.GOLDEN_APPLE && !requiresEGap) return true;
        }

        return false;
    }

    private boolean isUsingGapLikeFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE;
    }

    private boolean shouldPauseForActiveFoodUse() {
        boolean inventoryEating = InventoryManager.getInstance().isEating();
        boolean playerUsingFood = mc.player.isUsingItem() && mc.player.getUseItem().has(DataComponents.FOOD);
        return inventoryEating || playerUsingFood;
    }

    private void applyFlightAltitudeControl(boolean landing) {
        if (landing) {
            mc.options.keyJump.setDown(false);
            mc.options.keyShift.setDown(mc.player.isFallFlying());
            return;
        }

        if (navSteer != null) {
            mc.options.keyJump.setDown(navSteer.vertical() > 0);
            mc.options.keyShift.setDown(navSteer.vertical() < 0);
            return;
        }

        double playerY = mc.player.getY();
        boolean tooLow = playerY < flyYLevel.get() - 1;
        boolean tooHigh = playerY > flyYLevel.get() + 1;

        mc.options.keyJump.setDown(tooLow);
        mc.options.keyShift.setDown(tooHigh);
    }

    private boolean needsFoodRestock() {
        return foodRestock.get()
            && hasConfiguredFoodTypes()
            && !foodRestockSuppressed
            && countConfiguredFoodItemsInInventory() <= saveFood.get();
    }

    private boolean needsElytraRestock() {
        if (!restockElytras.get()) return countTotalFlightReadyElytras() <= 0;
        if (state == State.ARRIVED_WAIT || isInsideArrivalRadius()) return false;
        return countTotalFlightReadyElytras() <= 0;
    }

    private boolean handleGroundedRouteInterruptBeforeTakeoff() {
        if (isInsideArrivalRadius()) {
            state = State.ARRIVED_WAIT;
            stateTicks = 0;
            return true;
        }

        if (needsFoodRestock()) {
            startRestockInterrupt(RestockKind.FOOD, ResumeState.ROUTE_FLIGHT);
            return true;
        }

        if (needsRepair()) {
            beginRepair(false);
            return true;
        }

        if (countTotalFlightReadyElytras() <= 0) {
            if (restockElytras.get()) {
                startRestockInterrupt(RestockKind.ELYTRA, ResumeState.ROUTE_FLIGHT);
            } else {
                state = State.OUT_OF_ELYTRAS_WAIT;
                stateTicks = 0;
            }
            return true;
        }

        return false;
    }

    private boolean shouldRestockFoodThisPass() {
        return activeRestockKind == RestockKind.FOOD || queuedFoodRestock;
    }

    private boolean shouldRestockElytraThisPass() {
        return activeRestockKind == RestockKind.ELYTRA || queuedElytraRestock;
    }

    private boolean isCurrentRestockRequestSatisfied() {
        return switch (activeRestockKind) {
            case ELYTRA -> hasSatisfiedLooseElytraTargetAfterRepair();
            case FOOD -> !needsFoodRestock();
            case NONE -> !queuedFoodRestock && !queuedElytraRestock;
        };
    }

    private boolean isElytraRestockFailure(RestockFailureReason reason) {
        if (!shouldRestockElytraThisPass()) return false;
        return reason == RestockFailureReason.NO_MATCHING_SHULKER
            || reason == RestockFailureReason.PLACE_FAILED
            || reason == RestockFailureReason.OPEN_FAILED
            || reason == RestockFailureReason.LOOT_TIMEOUT
            || reason == RestockFailureReason.TARGET_UNMET
            || reason == RestockFailureReason.NO_FLIGHT_READY_ELYTRA;
    }

    private void handleRestockFailure(RestockFailureReason reason, String message) {
        releaseMovement();
        restoreRotationSnapshot();
        if (mc.gui.screen() != null) mc.gui.screen().onClose();

        if (reason != RestockFailureReason.PICKUP_FAILED && placedShulkerPos != null) {
            deferredRestockFailureReason = reason;
            deferredRestockFailureMessage = message;
            state = State.RESTOCK_BREAK;
            stateTicks = 0;
            currentSourceFailureTicks = 0;
            return;
        }

        applyRestockFailureOutcome(reason, message);
    }

    private void applyRestockFailureOutcome(RestockFailureReason reason, String message) {
        if (reason == RestockFailureReason.PICKUP_FAILED) {
            error(message);
            toggle();
            return;
        }

        boolean noFlightReady = countTotalFlightReadyElytras() <= 0 && requiresFlightReadyStateAfterRestock();
        boolean foodOnlyFailure = !noFlightReady && shouldRestockFoodThisPass() && !shouldRestockElytraThisPass();

        if (isElytraRestockFailure(reason) && restockElytras.get()) {
            if (noFlightReady && hasFlightReadyElytraShulkerInInventory() && reason != RestockFailureReason.PLACE_FAILED) {
                warning(message);
                startRestockInterrupt(RestockKind.ELYTRA, resumeStateAfterRestock);
                return;
            }

            if (countTotalFlightReadyElytras() > 0 && !hasFlightReadyElytraShulkerInInventory()) {
                resumeAfterPartialElytraRestock(message);
                return;
            }
        }

        if (noFlightReady) {
            warning(message);
            state = State.OUT_OF_ELYTRAS_WAIT;
            activeRestockKind = RestockKind.NONE;
            queuedElytraRestock = false;
            return;
        }

        if (foodOnlyFailure) {
            foodRestockSuppressed = true;
            if (resumeStateAfterRestock == ResumeState.ROUTE_FLIGHT) {
                warning(message);
                toggle();
            } else {
                warning(message);
                state = resumeStateAfterRestock == ResumeState.ARRIVED_WAIT ? State.ARRIVED_WAIT : State.OUT_OF_ELYTRAS_WAIT;
            }
            activeRestockKind = RestockKind.NONE;
            queuedFoodRestock = false;
            return;
        }

        error(message);
        toggle();
    }

    private void clearDeferredRestockFailure() {
        deferredRestockFailureReason = null;
        deferredRestockFailureMessage = null;
    }

    private boolean usesTwoHomes() {
        return !homeName2.get().isBlank();
    }

    /** Sends the next home command; returns true only when a home was actually set. */
    private boolean sendHomeSave() {
        if (!periodicHomeSave.get() || homeName.get().isBlank() || mc.player == null || mc.gui.screen() != null || shouldPauseForEating()) return false;
        long now = System.currentTimeMillis();

        if (!usesTwoHomes()) {
            ChatUtils.sendPlayerMsg("/sethome " + homeName.get());
            homeSaveNextAtMs = now + (homeSaveIntervalMinutes.get() * 60_000L);
            homeOverrideUntilMs = now + HOME_OVERRIDE_WINDOW_MS;
            homeOverrideName = homeName.get();
            return true;
        }

        // Cycle: delete A, set A, delete B, set B. Overwriting asks in a GUI and every home command shares one cooldown,
        // so the older home is deleted one cooldown before it is set again; the newest home is never touched.
        int step = homeCycleStep.get() & 3;
        String name = step < 2 ? homeName.get() : homeName2.get();
        boolean set = (step & 1) == 1;
        ChatUtils.sendPlayerMsg((set ? "/sethome " : "/delhome ") + name);
        lastHomeCommandMs = now;
        if (set) {
            lastHomeSetMs = now;
            homeOverrideUntilMs = now + HOME_OVERRIDE_WINDOW_MS;
            homeOverrideName = name;
        }
        homeCycleStep.set((step + 1) & 3);
        info(set ? "Saved home %s." : "Deleted older home %s for the next save.", name);
        return set;
    }

    /** Presses "Override" on the server's "Override home" dialog that follows our own /sethome. */
    private void tickHomeOverride() {
        if (System.currentTimeMillis() > homeOverrideUntilMs) return;
        if (!(mc.gui.screen() instanceof DialogScreen<?> dialog)) return;
        if (!dialog.getTitle().getString().toLowerCase(Locale.ROOT).contains("override home")) return;

        for (GuiEventListener element : dialog.children()) {
            if (element instanceof AbstractButton button && button.getMessage().getString().equalsIgnoreCase("Override")) {
                button.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                homeOverrideUntilMs = 0L;
                info("Overwrote home %s.", homeOverrideName);
                return;
            }
        }
    }

    private boolean isHomeOverrideBusy() {
        return System.currentTimeMillis() <= homeOverrideUntilMs;
    }

    private boolean isHomeSaveDue() {
        if (!periodicHomeSave.get()) return false;
        long now = System.currentTimeMillis();
        if (!usesTwoHomes()) return now >= homeSaveNextAtMs;

        if (now < lastHomeCommandMs + homeCommandCooldown.get() * 60_000L + HOME_COOLDOWN_MARGIN_MS) return false;
        boolean setStep = (homeCycleStep.get() & 1) == 1;
        return !setStep || now >= lastHomeSetMs + homeSaveIntervalMinutes.get() * 60_000L;
    }

    private boolean isInsideArrivalRadius() {
        return getRemainingDistance() <= arrivalRadius.get();
    }

    private float computeBearingYaw() {
        double dx = destinationX.get() - mc.player.getX();
        double dz = destinationZ.get() - mc.player.getZ();
        return normalizeYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);
    }

    private float computeFlightYaw(ElytraFly elytraFly) {
        navSteer = null;
        if (!smartPathing.get() || isBounceElytraFly(elytraFly)) return computeBearingYaw();

        navSteer = updateNavigator();
        double dx = navSteer.x() - mc.player.getX();
        double dz = navSteer.z() - mc.player.getZ();
        if (Math.hypot(dx, dz) < 0.25) return normalizeYaw(mc.player.getYRot());
        return normalizeYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);
    }

    private ElytraNavigator.Steer updateNavigator() {
        worldTerrain.newTick();
        miningActive = mineObstacles.get() && !isBounceElytraFly(Modules.get().get(ElytraFly.class)) && updateMineTool();
        navConfig.preferredY = flyYLevel.get();
        navConfig.minY = mc.level.getMinY();
        navConfig.maxY = mc.level.getMaxY();
        navConfig.range = pathRange.get();
        navConfig.lookahead = pathLookahead.get();
        navConfig.nodesPerTick = pathBudget.get();
        navConfig.stuckTicks = stuckTicks.get();

        AABB box = mc.player.getBoundingBox();
        Vec3 velocity = mc.player.getDeltaMovement();
        return navigator.update(
            (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5,
            velocity.x, velocity.z, destinationX.get(), destinationZ.get(), arrivalRadius.get()
        );
    }

    private float normalizeYaw(float yaw) {
        return Mth.wrapDegrees(yaw);
    }

    private void releaseMovement() {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
    }

    private void moveToward(double targetX, double targetZ, Runnable onArrival) {
        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();
        double distSq = dx * dx + dz * dz;

        if (distSq < 0.04) {
            mc.options.keyUp.setDown(false);
            onArrival.run();
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        mc.player.setYRot(yaw);
        mc.options.keyUp.setDown(true);
    }

    private int countConfiguredFoodItemsInInventory() {
        int count = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isConfiguredFoodStack(stack)) count += stack.getCount();
        }
        return count;
    }

    private boolean hasConfiguredFoodTypes() {
        return !foodTypes.get().isEmpty();
    }

    private boolean isConfiguredFoodStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && foodTypes.get().contains(stack.getItem());
    }

    private void handleFoodTypesChanged(List<Item> selected) {
        if (clampingFoodTypes || selected == null || selected.size() <= 1) return;

        clampingFoodTypes = true;
        try {
            foodTypes.set(new ArrayList<>(selected.subList(0, 1)));
        } finally {
            clampingFoodTypes = false;
        }

        warning("Maximum 1 food type.");
    }

    private int countConfiguredFoodSlots() {
        int count = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isConfiguredFoodStack(stack)) count++;
        }
        return count;
    }

    private int countEmptyLooseSlots() {
        int count = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) count++;
        }
        return count;
    }

    private int countUsableEmptyLooseSlotsAfterMinimumReserve() {
        return Math.max(0, countEmptyLooseSlots() - minimumEmptySlots.get() - pendingShulkerPickupSlotReserve());
    }

    private int pendingShulkerPickupSlotReserve() {
        return expectedShulkerCountAfterPickup >= 0 ? 1 : 0;
    }

    private int countHudUsableElytras() {
        int count = 0;
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (isHudUsableElytra(chest)) count++;

        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (isHudUsableElytra(mc.player.getInventory().getItem(i))) count++;
        }

        return count;
    }

    private int countTotalFlightReadyElytras() {
        int count = 0;
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (isFlightReadyElytra(chest)) count++;

        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (isFlightReadyElytra(mc.player.getInventory().getItem(i))) count++;
        }

        return count;
    }

    private int countLooseFlightReadyElytras() {
        int count = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (isFlightReadyElytra(mc.player.getInventory().getItem(i))) count++;
        }
        return count;
    }

    private int countLooseReplaceableElytras() {
        int count = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (isReplaceableElytra(mc.player.getInventory().getItem(i))) count++;
        }
        return count;
    }

    private boolean isHudUsableElytra(ItemStack stack) {
        return stack != null && stack.is(Items.ELYTRA) && THMUtils.getDamage(stack) > 1.0;
    }

    private boolean isFlightReadyElytra(ItemStack stack) {
        if (stack == null || !stack.is(Items.ELYTRA)) return false;

        ElytraFly elytraFly = Modules.get().get(ElytraFly.class);
        if (elytraFly == null) return false;

        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        return remaining > elytraFly.replaceDurability.get();
    }

    private boolean isReplaceableElytra(ItemStack stack) {
        return stack != null && stack.is(Items.ELYTRA) && !isFlightReadyElytra(stack);
    }

    private int getConfiguredFoodMaxStackSize() {
        if (!hasConfiguredFoodTypes()) return 0;
        Item foodItem = foodTypes.get().get(0);
        return foodItem != null ? foodItem.getDefaultMaxStackSize() : 0;
    }

    private int countConfiguredFoodMergeCapacityInInventory() {
        int capacity = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isConfiguredFoodStack(stack)) continue;

            capacity += Math.max(stack.getMaxStackSize() - stack.getCount(), 0);
        }

        return capacity;
    }

    private int countConfiguredFoodAdditionalCapacityInInventory() {
        int maxStackSize = getConfiguredFoodMaxStackSize();
        if (maxStackSize <= 0) return countConfiguredFoodMergeCapacityInInventory();

        int availableFoodSlots = Math.min(Math.max(2 - countConfiguredFoodSlots(), 0), countUsableEmptyLooseSlotsAfterMinimumReserve());
        return countConfiguredFoodMergeCapacityInInventory() + (availableFoodSlots * maxStackSize);
    }

    private int getConfiguredFoodTargetIncrease(int currentLooseFoodCount) {
        return Math.max((saveFood.get() + 1) - currentLooseFoodCount, 0);
    }

    private boolean hasUsableFoodRestockTarget() {
        if (!shouldRestockFoodThisPass()) return true;

        int currentLooseFood = countConfiguredFoodItemsInInventory();
        int targetIncrease = getConfiguredFoodTargetIncrease(currentLooseFood);
        if (targetIncrease <= 0) return true;

        return countConfiguredFoodAdditionalCapacityInInventory() >= targetIncrease;
    }

    private boolean shouldPullMoreConfiguredFood() {
        if (!shouldRestockFoodThisPass()) return false;
        return countConfiguredFoodItemsInInventory() < (saveFood.get() + 1) && countConfiguredFoodAdditionalCapacityInInventory() > 0;
    }

    private long getTotalFlightTimeSeconds() {
        long totalSeconds = 0;

        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (isHudUsableElytra(chest)) totalSeconds += flightTimeSecondsForStack(chest);

        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isHudUsableElytra(stack)) totalSeconds += flightTimeSecondsForStack(stack);
        }

        return totalSeconds;
    }

    private long flightTimeSecondsForStack(ItemStack stack) {
        return Math.max(stack.getMaxDamage() - stack.getDamageValue(), 0);
    }

    private void resetRestockPadSearch() {
        restockPadSearchAnchor = null;
        restockPadSearchRadius = 0;
        restockPadSearchX = 0;
        restockPadSearchZ = 0;
    }

    private RestockPad findNextRestockPad() {
        if (restockPadSearchAnchor == null) {
            restockPadSearchAnchor = mc.player.blockPosition().below().immutable();
            restockPadSearchRadius = 0;
            restockPadSearchX = restockPadSearchAnchor.getX();
            restockPadSearchZ = restockPadSearchAnchor.getZ();
        }

        int checks = 0;
        int y = restockPadSearchAnchor.getY();
        while (checks++ < PAD_SEARCH_CHECKS_PER_TICK) {
            BlockPos base = new BlockPos(restockPadSearchX, y, restockPadSearchZ);

            RestockPad xPad = toPadIfValid(base, base.east());
            if (xPad != null) return xPad;

            RestockPad zPad = toPadIfValid(base, base.south());
            if (zPad != null) return zPad;

            advanceRestockPadSearchCursor();
        }

        return null;
    }

    private void advanceRestockPadSearchCursor() {
        int anchorX = restockPadSearchAnchor.getX();
        int anchorZ = restockPadSearchAnchor.getZ();
        int minX = anchorX - restockPadSearchRadius;
        int maxX = anchorX + restockPadSearchRadius;
        int minZ = anchorZ - restockPadSearchRadius;
        int maxZ = anchorZ + restockPadSearchRadius;

        do {
            if (restockPadSearchX < maxX) {
                restockPadSearchX++;
            } else {
                restockPadSearchX = minX;
                restockPadSearchZ++;
            }

            if (restockPadSearchZ > maxZ) {
                restockPadSearchRadius++;
                restockPadSearchX = anchorX - restockPadSearchRadius;
                restockPadSearchZ = anchorZ - restockPadSearchRadius;
                return;
            }
        } while (restockPadSearchRadius != 0
            && Math.abs(restockPadSearchX - anchorX) != restockPadSearchRadius
            && Math.abs(restockPadSearchZ - anchorZ) != restockPadSearchRadius);
    }

    private RestockPad toPadIfValid(BlockPos a, BlockPos b) {
        if (!isValidPadBase(a) || !isValidPadBase(b)) return null;

        double aDist = squaredHorizontalDistanceTo(a);
        double bDist = squaredHorizontalDistanceTo(b);
        return aDist <= bDist ? new RestockPad(a, b) : new RestockPad(b, a);
    }

    private boolean isValidPadBase(BlockPos pos) {
        return mc.level.getBlockState(pos).isRedstoneConductor(mc.level, pos)
            && mc.level.getBlockState(pos.above()).isAir();
    }

    private double squaredHorizontalDistanceTo(BlockPos pos) {
        double dx = (pos.getX() + 0.5) - mc.player.getX();
        double dz = (pos.getZ() + 0.5) - mc.player.getZ();
        return dx * dx + dz * dz;
    }

    private ShulkerCandidate findBestInventoryShulker() {
        boolean needFood = shouldRestockFoodThisPass() && needsFoodRestock();
        boolean needElytra = needsElytraSourceThisPass();
        RestockKind primary = getPrimaryRestockNeed(needFood, needElytra);
        if (primary == RestockKind.NONE) return null;

        ShulkerCandidate best = null;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isShulkerBox(stack)) continue;
            if (isRestockSourceBlockedForKind(i, stack, primary)) continue;

            int food = countConfiguredFoodInShulker(stack);
            int elytras = countFlightReadyElytrasInShulker(stack);
            if (primary == RestockKind.ELYTRA && elytras <= 0) continue;
            if (primary == RestockKind.FOOD && food <= 0) continue;

            int tier;
            if (needFood && needElytra && food > 0 && elytras > 0) tier = 3;
            else if (primary == RestockKind.FOOD && food > 0) tier = 2;
            else if (primary == RestockKind.ELYTRA && elytras > 0) tier = 2;
            else tier = 1;

            int score = (tier * 100_000) + (food * 100) + elytras;
            if (score > bestScore) {
                bestScore = score;
                best = new ShulkerCandidate(i, food, elytras);
            }
        }

        return best;
    }

    private boolean isSelectedInventorySourceStillValid() {
        if (restockSourceInventorySlot < 0 || restockSourceInventorySlot >= PLAYER_INVENTORY_SLOTS) return false;
        ItemStack stack = mc.player.getInventory().getItem(restockSourceInventorySlot);
        return isValidRestockSourceForKind(restockSourceInventorySlot, stack, selectedRestockSourceKind);
    }

    private boolean needsElytraSourceThisPass() {
        return shouldRestockElytraThisPass()
            && (needsElytraRestock() || canPullMoreLooseElytras() || hasUnusableWornElytra());
    }

    private RestockKind getPrimaryRestockNeed(boolean needFood, boolean needElytra) {
        if (activeRestockKind == RestockKind.ELYTRA && needElytra) return RestockKind.ELYTRA;
        if (activeRestockKind == RestockKind.FOOD && needFood) return RestockKind.FOOD;
        if (needElytra) return RestockKind.ELYTRA;
        if (needFood) return RestockKind.FOOD;
        return RestockKind.NONE;
    }

    private boolean isSelectedRestockSourceReady() {
        if (!SlotUtils.isHotbar(restockSourceHotbarSlot)) return false;
        if (selectedRestockSourcePredicate == null) return false;
        ItemStack stack = mc.player.getInventory().getItem(restockSourceHotbarSlot);
        if (isRestockSourceBlockedForKind(restockSourceHotbarSlot, stack, selectedRestockSourceKind)) return false;
        return selectedRestockSourcePredicate.test(stack);
    }

    private boolean isValidRestockSourceForKind(int slot, ItemStack stack, RestockKind kind) {
        if (!isShulkerBox(stack)) return false;
        if (isRestockSourceBlockedForKind(slot, stack, kind)) return false;

        if (kind == RestockKind.ELYTRA) return countFlightReadyElytrasInShulker(stack) > 0;
        if (kind == RestockKind.FOOD) return countConfiguredFoodInShulker(stack) > 0;
        return false;
    }

    private boolean hasFlightReadyElytraShulkerInInventory() {
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isValidRestockSourceForKind(i, stack, RestockKind.ELYTRA)) return true;
        }

        return false;
    }

    private boolean isShulkerBox(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private int countInventoryShulkerBoxes() {
        int count = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isShulkerBox(stack)) count += Math.max(1, stack.getCount());
        }
        return count;
    }

    private int countConfiguredFoodInShulker(ItemStack shulker) {
        if (!isShulkerBox(shulker) || !hasConfiguredFoodTypes()) return 0;
        ItemContainerContents container = shulker.get(DataComponents.CONTAINER);
        if (container == null) return 0;

        int count = 0;
        for (ItemStack stack : container.nonEmptyItemCopyStream().toList()) {
            if (isConfiguredFoodStack(stack)) count += stack.getCount();
        }
        return count;
    }

    private int countFlightReadyElytrasInShulker(ItemStack shulker) {
        if (!isShulkerBox(shulker)) return 0;
        ItemContainerContents container = shulker.get(DataComponents.CONTAINER);
        if (container == null) return 0;

        int count = 0;
        for (ItemStack stack : container.nonEmptyItemCopyStream().toList()) {
            if (isFlightReadyElytra(stack)) count += Math.max(1, stack.getCount());
        }
        return count;
    }

    private void quarantineInvalidSourceFingerprint() {
        if (invalidOpenedSourceFingerprint != null && !invalidOpenedSourceFingerprint.isBlank()) {
            elytraQuarantinedFingerprints.add(invalidOpenedSourceFingerprint);
        }
    }

    private void blockElytraQuarantinedSlot(int slot) {
        if (slot >= 0 && slot < PLAYER_INVENTORY_SLOTS) {
            elytraQuarantinedSlots.add(slot);
        }
    }

    private boolean hasEmptyMainInventorySlot() {
        for (int i = 9; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return true;
        }
        return false;
    }

    private ItemStack[] snapshotPlayerInventory() {
        ItemStack[] snapshot = new ItemStack[PLAYER_INVENTORY_SLOTS];
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            snapshot[i] = mc.player.getInventory().getItem(i).copy();
        }
        return snapshot;
    }

    private int findChangedShulkerSlot(ItemStack[] before, ItemStack[] after, String preferredFingerprint, boolean mainInventoryOnly) {
        if (after == null) return -1;

        int start = mainInventoryOnly ? 9 : 0;
        int fallback = -1;
        for (int i = start; i < Math.min(after.length, PLAYER_INVENTORY_SLOTS); i++) {
            ItemStack afterStack = after[i];
            if (!isShulkerBox(afterStack)) continue;

            boolean changed = before == null || i >= before.length || !sameSnapshotStack(before[i], afterStack);
            if (!changed) continue;

            if (matchesShulkerFingerprint(afterStack, preferredFingerprint)) return i;
            if (fallback == -1) fallback = i;
        }

        return fallback;
    }

    private int findMatchingShulkerSlot(String fingerprint, int startInclusive, int endExclusive) {
        if (fingerprint == null || fingerprint.isBlank()) return -1;

        int start = Math.max(0, startInclusive);
        int end = Math.min(PLAYER_INVENTORY_SLOTS, endExclusive);
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (matchesShulkerFingerprint(stack, fingerprint)) return i;
        }

        return -1;
    }

    private boolean sameSnapshotStack(ItemStack before, ItemStack after) {
        if (before == null || after == null) return before == after;
        if (before.isEmpty() || after.isEmpty()) return before.isEmpty() && after.isEmpty();
        return before.getCount() == after.getCount() && ItemStack.isSameItemSameComponents(before, after);
    }

    private boolean matchesShulkerFingerprint(ItemStack stack, String fingerprint) {
        return fingerprint != null && !fingerprint.isBlank() && isShulkerBox(stack) && fingerprint.equals(fingerprintShulkerStack(stack));
    }

    private String fingerprintOpenedRestockContainer() {
        if (mc.player == null || mc.player.containerMenu == null) return null;

        List<String> entries = new ArrayList<>();
        int containerSlots = getContainerSlotCount();
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = mc.player.containerMenu.slots.get(i).getItem();
            if (!stack.isEmpty()) entries.add(fingerprintContainedStack(stack));
        }

        return buildShulkerFingerprint(getPlacedShulkerItemId(), entries);
    }

    private String fingerprintShulkerStack(ItemStack shulker) {
        if (!isShulkerBox(shulker)) return "";

        List<String> entries = new ArrayList<>();
        ItemContainerContents container = shulker.get(DataComponents.CONTAINER);
        if (container != null) {
            for (ItemStack stack : container.nonEmptyItemCopyStream().toList()) {
                entries.add(fingerprintContainedStack(stack));
            }
        }

        return buildShulkerFingerprint(BuiltInRegistries.ITEM.getKey(shulker.getItem()).toString(), entries);
    }

    private String buildShulkerFingerprint(String shulkerItemId, List<String> entries) {
        entries.sort(String::compareTo);
        return "shulker=" + (shulkerItemId == null ? "unknown" : shulkerItemId) + "|contents=" + String.join(";", entries);
    }

    private String getPlacedShulkerItemId() {
        if (mc.level != null && placedShulkerPos != null && mc.level.getBlockState(placedShulkerPos).getBlock() instanceof ShulkerBoxBlock block) {
            return BuiltInRegistries.ITEM.getKey(block.asItem()).toString();
        }

        return "unknown";
    }

    private String fingerprintContainedStack(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
            + "x" + stack.getCount()
            + "d" + stack.getDamageValue()
            + "m" + stack.getMaxDamage();
    }

    private int findHotbarMoveTarget() {
        for (int i = 0; i < 9; i++) {
            if (isHotbarSlotBlockedForCurrentNeed(i)) continue;
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }

        for (int i = 0; i < 9; i++) {
            if (isHotbarSlotBlockedForCurrentNeed(i)) continue;
            if (!isProtectedRestockHotbarStack(mc.player.getInventory().getItem(i))) return i;
        }

        return -1;
    }

    private int findEvictableUnhelpfulHotbarShulkerSlot() {
        if (!hasEmptyMainInventorySlot()) return -1;

        RestockKind primary = getCurrentPrimaryRestockNeed();
        if (primary == RestockKind.NONE) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isShulkerBox(stack)) continue;
            if (isHelpfulRestockShulkerForKind(i, stack, primary)) continue;
            return i;
        }

        return -1;
    }

    private int findValidHotbarRestockSourceSlot(RestockKind kind) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isValidRestockSourceForKind(i, stack, kind)) return i;
        }
        return -1;
    }

    private boolean isHelpfulRestockShulkerForKind(int slot, ItemStack stack, RestockKind kind) {
        if (kind == RestockKind.ELYTRA) return isValidRestockSourceForKind(slot, stack, RestockKind.ELYTRA);
        if (kind == RestockKind.FOOD) return isValidRestockSourceForKind(slot, stack, RestockKind.FOOD);
        return false;
    }

    private boolean isHotbarSlotBlockedForCurrentNeed(int slot) {
        if (selectedRestockSourceKind == RestockKind.ELYTRA || getCurrentPrimaryRestockNeed() == RestockKind.ELYTRA) {
            return elytraQuarantinedSlots.contains(slot);
        }
        return false;
    }

    private boolean isRestockSourceBlockedForKind(int slot, ItemStack stack, RestockKind kind) {
        if (kind != RestockKind.ELYTRA) return false;
        if (slot >= 0 && elytraQuarantinedSlots.contains(slot)) return true;
        return isShulkerBox(stack) && elytraQuarantinedFingerprints.contains(fingerprintShulkerStack(stack));
    }

    private boolean isProtectedRestockHotbarStack(ItemStack stack) {
        return isConfiguredFoodStack(stack)
            || stack.has(DataComponents.FOOD)
            || isFlightReadyElytra(stack)
            || stack.is(Items.ELYTRA)
            || stack.has(DataComponents.EQUIPPABLE)
            || isWeaponStack(stack)
            || isToolStack(stack)
            || isShulkerBox(stack);
    }

    private boolean equipFlightReadyElytraForTakeoff() {
        if (isFlightReadyElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST))) return true;

        int sourceSlot = findBestFlightReadyLooseElytraSlot();
        if (sourceSlot == -1) return false;

        int hotbarSlot = SlotUtils.isHotbar(sourceSlot) ? sourceSlot : prepareHotbarSlotForTakeoffElytra();
        if (hotbarSlot == -1) return false;

        if (!SlotUtils.isHotbar(sourceSlot)) {
            InvUtils.move().from(sourceSlot).toHotbar(hotbarSlot);
            actionDelayTicks = Math.max(actionDelayTicks, inventoryDelay.get());
        }

        if (rightClickEquipHotbarElytra(hotbarSlot)) return true;

        // Right-click cannot equip over an occupied chest slot, so fall back to the cursor-clear swap path.
        return repairWornElytraIfNeeded();
    }

    private boolean hasElytraEquipped() {
        return mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
    }

    private int findBestFlightReadyLooseElytraSlot() {
        int bestSlot = -1;
        int bestRemaining = -1;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isFlightReadyElytra(stack)) continue;

            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            if (remaining > bestRemaining) {
                bestRemaining = remaining;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int prepareHotbarSlotForTakeoffElytra() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }

        int target = findTakeoffHotbarEvictionTarget(false);
        if (target == -1) target = findTakeoffHotbarEvictionTarget(true);
        if (target == -1) return -1;

        if (!isProtectedTakeoffHotbarStack(mc.player.getInventory().getItem(target))) {
            shiftClickHotbarItemOut(target);
        }

        return target;
    }

    private int findTakeoffHotbarEvictionTarget(boolean allowProtected) {
        int selected = mc.player.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            int slot = (selected + 1 + i) % 9;
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) return slot;
            if (!allowProtected && isProtectedTakeoffHotbarStack(stack)) continue;
            return slot;
        }
        return selected;
    }

    private boolean isProtectedTakeoffHotbarStack(ItemStack stack) {
        return isConfiguredFoodStack(stack)
            || stack.has(DataComponents.FOOD)
            || isWeaponStack(stack)
            || stack.is(Items.ELYTRA)
            || stack.has(DataComponents.EQUIPPABLE)
            || isShulkerBox(stack);
    }

    private boolean isWeaponStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.WOODEN_SWORD
            || item == Items.STONE_SWORD
            || item == Items.IRON_SWORD
            || item == Items.GOLDEN_SWORD
            || item == Items.DIAMOND_SWORD
            || item == Items.NETHERITE_SWORD
            || item == Items.WOODEN_AXE
            || item == Items.STONE_AXE
            || item == Items.IRON_AXE
            || item == Items.GOLDEN_AXE
            || item == Items.DIAMOND_AXE
            || item == Items.NETHERITE_AXE
            || item == Items.BOW
            || item == Items.CROSSBOW
            || item == Items.TRIDENT
            || item == Items.MACE;
    }

    private boolean isToolStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.WOODEN_PICKAXE
            || item == Items.STONE_PICKAXE
            || item == Items.IRON_PICKAXE
            || item == Items.GOLDEN_PICKAXE
            || item == Items.DIAMOND_PICKAXE
            || item == Items.NETHERITE_PICKAXE
            || item == Items.WOODEN_SHOVEL
            || item == Items.STONE_SHOVEL
            || item == Items.IRON_SHOVEL
            || item == Items.GOLDEN_SHOVEL
            || item == Items.DIAMOND_SHOVEL
            || item == Items.NETHERITE_SHOVEL
            || item == Items.WOODEN_HOE
            || item == Items.STONE_HOE
            || item == Items.IRON_HOE
            || item == Items.GOLDEN_HOE
            || item == Items.DIAMOND_HOE
            || item == Items.NETHERITE_HOE
            || item == Items.SHEARS
            || item == Items.FLINT_AND_STEEL
            || item == Items.FISHING_ROD
            || item == Items.BRUSH;
    }

    private void shiftClickHotbarItemOut(int hotbarSlot) {
        if (mc.player.getInventory().getItem(hotbarSlot).isEmpty()) return;
        InvUtils.shiftClick().slot(hotbarSlot);
        actionDelayTicks = Math.max(actionDelayTicks, inventoryDelay.get());
    }

    private boolean rightClickEquipHotbarElytra(int hotbarSlot) {
        if (!SlotUtils.isHotbar(hotbarSlot) || !isFlightReadyElytra(mc.player.getInventory().getItem(hotbarSlot))) return false;

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        if (!InvUtils.swap(hotbarSlot, true)) return false;

        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND);
        actionDelayTicks = Math.max(actionDelayTicks, inventoryDelay.get());
        InvUtils.swapBack();

        if (previousSlot >= 0 && previousSlot <= 8 && mc.player.getInventory().getSelectedSlot() != previousSlot) {
            InvUtils.swap(previousSlot, false);
        }

        return isFlightReadyElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST));
    }

    private int getContainerSlotCount() {
        if (mc.player == null || mc.player.containerMenu == null) return 0;
        return Math.max(0, mc.player.containerMenu.slots.size() - PLAYER_INVENTORY_SLOTS);
    }

    private int findBestFlightReadyElytraContainerSlot() {
        int bestSlot = -1;
        int bestRemaining = -1;
        int containerSlots = getContainerSlotCount();
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = mc.player.containerMenu.slots.get(i).getItem();
            if (!isFlightReadyElytra(stack)) continue;

            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            if (remaining > bestRemaining) {
                bestRemaining = remaining;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int findBestFoodContainerSlot() {
        int bestSlot = -1;
        int bestCount = -1;
        int containerSlots = getContainerSlotCount();
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = mc.player.containerMenu.slots.get(i).getItem();
            if (!isConfiguredFoodStack(stack)) continue;

            if (stack.getCount() > bestCount) {
                bestCount = stack.getCount();
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private int findReplaceableLooseElytraSlot() {
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (isReplaceableElytra(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private boolean dropReplaceableLooseElytraForPickup() {
        int slot = findReplaceableLooseElytraSlot();
        if (slot == -1) return false;

        InvUtils.drop().slot(slot);
        return true;
    }

    private int getRequiredLooseFlightReadyBeforeRepair() {
        int targetLoose = fixedElytraRestockAmount.get() ? elytraRestockAmount.get() : countLooseFlightReadyElytras() + countAvailableLooseElytraCapacity();
        return targetLoose + (hasUnusableWornElytra() ? 1 : 0);
    }

    private int countAvailableLooseElytraCapacity() {
        return Math.max(0, countUsableEmptyLooseSlotsAfterMinimumReserve() - reservedFoodSlotsNeeded()) + countLooseReplaceableElytras();
    }

    private boolean canPullMoreLooseElytras() {
        int capacity = countAvailableLooseElytraCapacity();
        if (fixedElytraRestockAmount.get()) {
            return countLooseFlightReadyElytras() < getRequiredLooseFlightReadyBeforeRepair() && capacity > 0;
        }

        return capacity > 0;
    }

    private int reservedFoodSlotsNeeded() {
        if (!foodRestock.get() || !hasConfiguredFoodTypes()) return 0;
        return Math.max(0, 2 - countConfiguredFoodSlots());
    }

    private boolean hasUsableElytraRestockTarget() {
        if (!shouldRestockElytraThisPass()) return true;
        if (!restockElytras.get()) return countTotalFlightReadyElytras() > 0;

        if (fixedElytraRestockAmount.get()) {
            int neededIncrease = Math.max(getRequiredLooseFlightReadyBeforeRepair() - countLooseFlightReadyElytras(), 0);
            return neededIncrease <= countAvailableLooseElytraCapacity();
        }

        if (hasUnusableWornElytra() && countLooseFlightReadyElytras() <= 0) {
            return countAvailableLooseElytraCapacity() > 0;
        }

        return countAvailableLooseElytraCapacity() > 0 || countTotalFlightReadyElytras() > 0;
    }

    private boolean hasSatisfiedLooseElytraTargetAfterRepair() {
        if (!fixedElytraRestockAmount.get()) return countAvailableLooseElytraCapacity() <= 0;
        return countLooseFlightReadyElytras() >= elytraRestockAmount.get();
    }

    private boolean shouldAcceptPartialElytraRestock(RestockKind completed) {
        if (isInsideArrivalRadius()) return false;
        if (!(completed == RestockKind.ELYTRA || queuedElytraRestock)) return false;
        if (!restockElytras.get()) return false;
        if (hasSatisfiedLooseElytraTargetAfterRepair()) return false;
        if (hasFlightReadyElytraShulkerInInventory()) return false;
        return countTotalFlightReadyElytras() > 0;
    }

    private boolean shouldContinueElytraRestockAfterPass(RestockKind completed) {
        if (isInsideArrivalRadius()) return false;
        if (!(completed == RestockKind.ELYTRA || queuedElytraRestock)) return false;
        if (!restockElytras.get()) return countTotalFlightReadyElytras() <= 0;
        return !hasSatisfiedLooseElytraTargetAfterRepair() && hasFlightReadyElytraShulkerInInventory();
    }

    private void resumeAfterPartialElytraRestock(String message) {
        int usable = countTotalFlightReadyElytras();
        warning("%s Resuming with %d flight-ready elytra%s.", message, usable, usable == 1 ? "" : "s");

        activeRestockKind = RestockKind.NONE;
        queuedElytraRestock = false;
        clearSelectedRestockSource();
        currentSourceFailureTicks = 0;
        invalidRestockSourceAttempts = 0;
        restockOpenedSourceValidated = false;
        clearInvalidSourceTracking();

        if (queuedFoodRestock && !foodRestockSuppressed && needsFoodRestock()) {
            startRestockInterrupt(RestockKind.FOOD, resumeStateAfterRestock);
            return;
        }

        if (isInsideArrivalRadius() || resumeStateAfterRestock == ResumeState.ARRIVED_WAIT) {
            state = State.ARRIVED_WAIT;
            stateTicks = 0;
            return;
        }

        if (usable <= 0) {
            state = State.OUT_OF_ELYTRAS_WAIT;
            stateTicks = 0;
            return;
        }

        state = State.TAKEOFF_AWAIT_GROUND;
        stateTicks = 0;
    }

    private void swapContainerElytraWithBrokenInventorySlot(int containerSlotId, int inventorySlotIndex) {
        int inventorySlotId = SlotUtils.indexToId(inventorySlotIndex);
        int syncId = mc.player.containerMenu.containerId;

        mc.gameMode.handleContainerInput(syncId, containerSlotId, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(syncId, inventorySlotId, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(syncId, containerSlotId, 0, ContainerInput.PICKUP, mc.player);
    }

    private boolean repairWornElytraIfNeeded() {
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (isFlightReadyElytra(chest)) return true;

        int bestSlot = -1;
        int bestRemaining = -1;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isFlightReadyElytra(stack)) continue;

            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            if (remaining > bestRemaining) {
                bestRemaining = remaining;
                bestSlot = i;
            }
        }

        if (bestSlot == -1) return false;

        THMUtils.fakeInventoryOpen(true);
        try {
            int syncId = mc.player.containerMenu.containerId;
            int spareId = SlotUtils.indexToId(bestSlot);
            int chestId = SlotUtils.indexToId(CHEST_SLOT_INDEX);

            mc.gameMode.handleContainerInput(syncId, spareId, 0, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(syncId, chestId, 0, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(syncId, spareId, 0, ContainerInput.PICKUP, mc.player);

            ItemStack cursor = mc.player.containerMenu.getCarried();
            if (!cursor.isEmpty()) {
                clearCursorAfterSwap();
            }

            actionDelayTicks = Math.max(actionDelayTicks, inventoryDelay.get());
        } finally {
            THMUtils.fakeInventoryOpen(false);
        }

        return isFlightReadyElytra(mc.player.getItemBySlot(EquipmentSlot.CHEST)) || countTotalFlightReadyElytras() > 0;
    }

    private void clearCursorAfterSwap() {
        if (!InventoryManager.parkCursor()) InvUtils.dropHand();
    }

    private boolean clearCursorStackToEmptySlot() {
        return InventoryManager.parkCursor();
    }

    private boolean hasUnusableWornElytra() {
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        return !isFlightReadyElytra(chest);
    }

    private boolean isRestockState(State state) {
        return state == State.RESTOCK_MOVE_TO_PAD
            || state == State.RESTOCK_PLACE
            || state == State.RESTOCK_OPEN
            || state == State.RESTOCK_LOOT
            || state == State.RESTOCK_CLOSE
            || state == State.RESTOCK_BREAK
            || state == State.RESTOCK_PICKUP;
    }

    private boolean requiresFlightReadyStateAfterRestock() {
        return resumeStateAfterRestock == ResumeState.ROUTE_FLIGHT || activeRestockKind == RestockKind.ELYTRA || queuedElytraRestock;
    }

    private boolean hasFlightReadySpareInInventory() {
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (isFlightReadyElytra(mc.player.getInventory().getItem(i))) return true;
        }

        return false;
    }

    private List<ItemEntity> collectShulkerDrops() {
        BlockPos center = placedShulkerPos != null ? placedShulkerPos : mc.player.blockPosition();
        AABB area = new AABB(center).inflate(SHULKER_PICKUP_SEARCH_RADIUS, 2.0, SHULKER_PICKUP_SEARCH_RADIUS)
            .minmax(mc.player.getBoundingBox().inflate(SHULKER_PICKUP_SEARCH_RADIUS, 2.0, SHULKER_PICKUP_SEARCH_RADIUS));
        return mc.level.getEntitiesOfClass(
            ItemEntity.class,
            area,
            entity -> entity != null && entity.isAlive() && isShulkerBox(entity.getItem())
        );
    }

    /** Solid / hazardous check for the route planner; cells in unloaded chunks count as free. */
    private final class WorldTerrain implements ElytraPathPlanner.Terrain {
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private final Map<Long, Boolean> loadedChunks = new HashMap<>();

        void newTick() {
            loadedChunks.clear();
        }

        @Override
        public boolean loaded(int x, int z) {
            if (mc.level == null) return false;
            int cx = x >> 4;
            int cz = z >> 4;
            long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
            Boolean cached = loadedChunks.get(key);
            if (cached != null) return cached;

            boolean loaded = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) != null;
            loadedChunks.put(key, loaded);
            return loaded;
        }

        @Override
        public boolean blocked(int x, int y, int z) {
            if (mc.level == null) return true;
            if (y < mc.level.getMinY() || y > mc.level.getMaxY()) return true;
            if (!loaded(x, z)) return false;

            BlockState state = mc.level.getBlockState(pos.set(x, y, z));
            if (state.isAir()) return false;
            if (!state.getFluidState().isEmpty()) return true;
            if (state.is(Blocks.COBWEB) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return true;
            return !state.getCollisionShape(mc.level, pos).isEmpty();
        }

        @Override
        public float mineCost(int x, int y, int z) {
            if (!miningActive || mc.level == null) return -1f;
            if (y <= mc.level.getMinY() || y >= mc.level.getMaxY() || !loaded(x, z)) return -1f;
            if (mineFailed.contains(packCell(x, y, z))) return -1f;

            BlockState state = mc.level.getBlockState(pos.set(x, y, z));
            if (state.isAir() || !state.getFluidState().isEmpty()) return -1f;
            if (state.is(Blocks.COBWEB) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.MAGMA_BLOCK)) return -1f;
            if (state.getCollisionShape(mc.level, pos).isEmpty()) return -1f;

            float hardness = state.getDestroySpeed(mc.level, pos);
            if (hardness < 0f) return -1f;
            if (mineAvoidContainers.get() && state.hasBlockEntity()) return -1f;
            if (!BlockUtils.canBreak(pos, state)) return -1f;

            float ticks = estimateBreakTicks(state, hardness);
            if (ticks > maxBlockTicks.get()) return -1f;
            if (liquidNextTo(x, y, z)) return -1f;
            return ticks * digCost.get().floatValue();
        }

        private boolean liquidNextTo(int x, int y, int z) {
            return fluidAt(x + 1, y, z) || fluidAt(x - 1, y, z) || fluidAt(x, y + 1, z)
                || fluidAt(x, y - 1, z) || fluidAt(x, y, z + 1) || fluidAt(x, y, z - 1);
        }

        private boolean fluidAt(int x, int y, int z) {
            return !mc.level.getBlockState(pos.set(x, y, z)).getFluidState().isEmpty();
        }
    }

    // ---- In-flight mining: breaks blocks the route flies into without leaving the flight ----

    private static long packCell(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    private static boolean isPickaxe(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.WOODEN_PICKAXE || item == Items.STONE_PICKAXE || item == Items.IRON_PICKAXE
            || item == Items.GOLDEN_PICKAXE || item == Items.DIAMOND_PICKAXE || item == Items.NETHERITE_PICKAXE;
    }

    private float estimateBreakTicks(BlockState state, float hardness) {
        float speed = mineTool.isEmpty() ? 1f : mineTool.getDestroySpeed(state);
        boolean slow = state.requiresCorrectToolForDrops() && speed <= 1f;
        // Vanilla mines 5x slower while not on the ground.
        float airborne = mc.player != null && !mc.player.onGround() ? 5f : 1f;
        return Math.max(1f, hardness * (slow ? 100f : 30f) * airborne / Math.max(1f, speed));
    }

    private boolean updateMineTool() {
        if (mc.player == null) return false;

        int age = mc.player.tickCount;
        if (mineToolSlot != -1 && age - mineToolCheckTick < 20) {
            ItemStack current = mc.player.getInventory().getItem(mineToolSlot);
            if (isPickaxe(current) && elytraRemaining(current) >= mineMinDurability.get()) return true;
        }

        mineToolCheckTick = age;
        int best = -1;
        float bestSpeed = 0f;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isPickaxe(stack) || elytraRemaining(stack) < mineMinDurability.get()) continue;

            float speed = stack.getDestroySpeed(Blocks.OBSIDIAN.defaultBlockState());
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }

        mineToolSlot = best;
        mineTool = best == -1 ? ItemStack.EMPTY : mc.player.getInventory().getItem(best);
        return best != -1;
    }

    private void selectMineTool() {
        if (mineToolSlot == -1) return;

        if (mineToolSlot >= 9) {
            int hotbar = mc.player.getInventory().getSelectedSlot();
            InvUtils.move().from(mineToolSlot).toHotbar(hotbar);
            mineToolSlot = hotbar;
            mineTool = mc.player.getInventory().getItem(hotbar);
        }

        InvUtils.swap(mineToolSlot, true);
        miningSwapped = true;
    }

    private void stopMining() {
        if (miningSwapped) {
            InvUtils.swapBack();
            miningSwapped = false;
        }
        miningHover = false;
        hoverClimb = 0;
        mineTarget = null;
        mineTargetTicks = 0;
    }

    /** Returns true while the player should hold still for a block that is being broken right in front of it. */
    private boolean tickFlightMining() {
        // Only dig once the navigator found no way through the air; otherwise blocks in the way are flown around.
        if (!miningActive || navSteer == null || mc.player == null || !navigator.digging()) {
            stopMining();
            return false;
        }

        AABB box = mc.player.getBoundingBox();
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double dx = navSteer.x() - cx;
        double dy = navSteer.y() - cy;
        double dz = navSteer.z() - cz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.5) {
            stopMining();
            return false;
        }
        dx /= len;
        dy /= len;
        dz /= len;

        double hx = (box.maxX - box.minX) * 0.5 + 0.05;
        double hy = (box.maxY - box.minY) * 0.5 + 0.05;
        double hz = (box.maxZ - box.minZ) * 0.5 + 0.05;

        BlockPos found = null;
        scan:
        for (double t = 0.5; t <= MINE_LOOKAHEAD; t += 0.5) {
            double sx = cx + dx * t, sy = cy + dy * t, sz = cz + dz * t;
            for (int ix = Mth.floor(sx - hx); ix <= Mth.floor(sx + hx); ix++) {
                for (int iy = Mth.floor(sy - hy); iy <= Mth.floor(sy + hy); iy++) {
                    for (int iz = Mth.floor(sz - hz); iz <= Mth.floor(sz + hz); iz++) {
                        if (!worldTerrain.blocked(ix, iy, iz)) continue;
                        if (worldTerrain.mineCost(ix, iy, iz) >= 0f) found = new BlockPos(ix, iy, iz);
                        break scan;
                    }
                }
            }
        }

        int want = 0;
        if (found == null) {
            want = wantedVertical(cy);
            if (want != 0) found = scanVertical(cx, cy, cz, want, hx, hy, hz);
        }

        if (found == null) {
            hoverClimb = 0;
            stopMining();
            return false;
        }

        boolean climbing = want != 0;
        navigator.hold(6);

        double dist = mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(found));
        if (dist > MINE_REACH) {
            mineTarget = null;
            return false;
        }

        if (!found.equals(mineTarget)) {
            mineTarget = found;
            mineTargetTicks = 0;
        }

        if (++mineTargetTicks > MINE_TARGET_TIMEOUT) {
            mineFailed.add(packCell(found.getX(), found.getY(), found.getZ()));
            warning("A block could not be broken. Routing around it.");
            navigator.reset();
            stopMining();
            return false;
        }

        selectMineTool();
        if (mineRotate.get()) Rotations.rotate(Rotations.getYaw(found), Rotations.getPitch(found));
        BlockUtils.breakBlock(found, true);

        if (climbing) {
            if (!miningHover) hoverY = mc.player.getY();
            hoverClimb = want;
            return true;
        }

        hoverClimb = 0;
        Vec3 velocity = mc.player.getDeltaMovement();
        double brake = Math.min(MINE_REACH - 0.2, brakeDistance.get() + Math.hypot(velocity.x, velocity.z));
        boolean hover = dist <= brake;
        if (hover && !miningHover) hoverY = mc.player.getY();
        return hover;
    }

    private void printRouteDebug() {
        AABB box = mc.player.getBoundingBox();
        double cy = (box.minY + box.maxY) * 0.5;
        String mode = navSteer == null ? "none" : navSteer.mode().name();
        int vertical = navSteer == null ? 0 : navSteer.vertical();
        int want = navSteer == null ? 0 : wantedVertical(cy);
        String target = mineTarget == null ? "-" : mineTarget.toShortString();
        info("nav=%s vert=%d want=%d mining=%s hover=%s climb=%d target=%s y=%.1f/%d",
            mode, vertical, want, miningActive, miningHover, hoverClimb, target, cy, flyYLevel.get());
    }

    /** Which way the route wants to change altitude: the navigator's own vertical move, or the fly-y-level when flying straight. */
    private int wantedVertical(double centerY) {
        if (navSteer.vertical() != 0) return navSteer.vertical();
        if (navSteer.mode() != ElytraNavigator.Mode.DIRECT) return 0;
        if (centerY < flyYLevel.get() - 0.7) return 1;
        if (centerY > flyYLevel.get() + 1.3) return -1;
        return 0;
    }

    /** First breakable block straight above or below the player; stops at anything that cannot be broken. */
    private BlockPos scanVertical(double cx, double cy, double cz, int dir, double hx, double hy, double hz) {
        for (double t = 0.5; t <= 3.0; t += 0.5) {
            double sy = cy + dir * t;
            for (int ix = Mth.floor(cx - hx); ix <= Mth.floor(cx + hx); ix++) {
                for (int iy = Mth.floor(sy - hy); iy <= Mth.floor(sy + hy); iy++) {
                    for (int iz = Mth.floor(cz - hz); iz <= Mth.floor(cz + hz); iz++) {
                        if (!worldTerrain.blocked(ix, iy, iz)) continue;
                        return worldTerrain.mineCost(ix, iy, iz) >= 0f ? new BlockPos(ix, iy, iz) : null;
                    }
                }
            }
        }
        return null;
    }

    private boolean hitboxBlockedAt(double offsetY) {
        AABB box = mc.player.getBoundingBox().move(0.0, offsetY, 0.0);
        for (int ix = Mth.floor(box.minX); ix <= Mth.floor(box.maxX); ix++) {
            for (int iy = Mth.floor(box.minY); iy <= Mth.floor(box.maxY); iy++) {
                for (int iz = Mth.floor(box.minZ); iz <= Mth.floor(box.maxZ); iz++) {
                    if (worldTerrain.blocked(ix, iy, iz)) return true;
                }
            }
        }
        return false;
    }

    private void holdForMining() {
        mc.options.keyUp.setDown(false);
        dampenLandingHorizontalVelocity();

        double y = mc.player.getY();
        if (hoverClimb != 0) {
            boolean free = !hitboxBlockedAt(hoverClimb * 0.45);
            mc.options.keyJump.setDown(hoverClimb > 0 && free);
            mc.options.keyShift.setDown(hoverClimb < 0 && free);
            hoverY = y;
            return;
        }

        mc.options.keyJump.setDown(y < hoverY - 0.3);
        mc.options.keyShift.setDown(y > hoverY + 0.5);
    }

    // ---- Baritone fallback: walk (and dig) on the ground like #goto when the air route is closed ----

    private void tickMinerFallback() {
        if (!minerStarted) {
            releaseMovement();
            restoreRotationSnapshot();
            pauseBounceElytraFlyForGroundControl();
            if (!startBaritoneFallback()) {
                error("Elytra Route stopped: Baritone could not be started.");
                toggle();
                return;
            }
            minerStarted = true;
            takeoffClearTicks = 0;
            stateTicks = 0;
            return;
        }

        IBaritone baritone = primaryBaritone();
        boolean walking = !fallbackStopped && baritone != null && baritone.getCustomGoalProcess().isActive();
        double dist = Math.sqrt(mc.player.blockPosition().distSqr(fallbackGoal));
        if (dist < fallbackBestDist - 1.0) {
            fallbackBestDist = dist;
            fallbackProgressMs = System.currentTimeMillis();
        }

        if (walking) {
            // Fly on as soon as the air ahead is open, not only once the goal is reached.
            if (++stateTicks % 10 == 0 && canResumeFlight()) {
                info("The way through the air is open again. Taking off.");
                stopBaritoneFallback();
            } else if (System.currentTimeMillis() - fallbackProgressMs > fallbackTimeout.get() * 1000L) {
                warning("Baritone made no progress for %d seconds.", fallbackTimeout.get());
                stopBaritoneFallback();
            } else {
                return;
            }
        } else if (!fallbackStopped) {
            stopBaritoneFallback();
        }

        // Only failed runs count toward giving up; a reached goal is progress.
        boolean reached = dist <= FALLBACK_GOAL_REACHED || canResumeFlight();
        if (!clearTakeoffRoom()) return;
        stopMining();

        minerStarted = false;
        minerRuns = reached ? 0 : minerRuns + 1;
        navigator.reset();
        navSteer = null;
        stateTicks = 0;

        if (isInsideArrivalRadius()) {
            state = State.ARRIVED_WAIT;
        } else if (minerRuns >= 3) {
            error("Elytra Route stopped: Baritone could not find a way after several tries.");
            toggle();
        } else {
            state = State.TAKEOFF_AWAIT_GROUND;
        }
    }

    private IBaritone primaryBaritone() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Sends Baritone toward the destination for up to fallback-distance blocks, like {@code #goto x z}. */
    private boolean startBaritoneFallback() {
        IBaritone baritone = primaryBaritone();
        if (baritone == null) return false;

        double dx = destinationX.get() + 0.5 - mc.player.getX();
        double dz = destinationZ.get() + 0.5 - mc.player.getZ();
        double len = Math.hypot(dx, dz);
        double step = Math.min(fallbackDistance.get(), len);
        int gx = len < 1.0 ? destinationX.get() : Mth.floor(mc.player.getX() + dx / len * step);
        int gz = len < 1.0 ? destinationZ.get() : Mth.floor(mc.player.getZ() + dz / len * step);

        if (!baritoneSettingsSaved) {
            savedAllowBreak = BaritoneAPI.getSettings().allowBreak.value;
            savedAllowPlace = BaritoneAPI.getSettings().allowPlace.value;
            baritoneSettingsSaved = true;
        }
        BaritoneAPI.getSettings().allowBreak.value = fallbackBreak.get();
        BaritoneAPI.getSettings().allowPlace.value = fallbackPlace.get();

        fallbackGoal = new BlockPos(gx, mc.player.getBlockY(), gz);
        fallbackBestDist = Math.sqrt(mc.player.blockPosition().distSqr(fallbackGoal));
        fallbackProgressMs = System.currentTimeMillis();
        fallbackStopped = false;
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(gx, gz));
        return true;
    }

    private void stopBaritoneFallback() {
        fallbackStopped = true;
        IBaritone baritone = primaryBaritone();
        if (baritone != null) {
            try {
                baritone.getPathingBehavior().cancelEverything();
                baritone.getCustomGoalProcess().setGoal(null);
                baritone.getInputOverrideHandler().clearAllKeys();
            } catch (Throwable ignored) {
            }
        }
        if (baritoneSettingsSaved) {
            BaritoneAPI.getSettings().allowBreak.value = savedAllowBreak;
            BaritoneAPI.getSettings().allowPlace.value = savedAllowPlace;
            baritoneSettingsSaved = false;
        }
        releaseMovement();
    }

    /** Feet Y the fallback digs toward: one below fly-y-level, so a takeoff from there reaches it at once. */
    private int minerTargetFeetY() {
        int min = mc.level.getMinY() + 2;
        int max = mc.level.getMaxY() - 4;
        return Mth.clamp(flyYLevel.get() - 1, min, max);
    }

    /** True on the ground when the air toward the destination is open: at flight height, or in a space tall enough to climb in flight. */
    private boolean canResumeFlight() {
        if (!mc.player.onGround()) return false;
        if (!hasTakeoffHeadroom()) return false;

        double dx = destinationX.get() + 0.5 - mc.player.getX();
        double dz = destinationZ.get() + 0.5 - mc.player.getZ();
        double len = Math.hypot(dx, dz);
        if (len < 1.0) return false;

        worldTerrain.newTick();
        double ahead = Math.min(RESUME_FLIGHT_CHECK_DISTANCE, len);
        double ex = mc.player.getX() + dx / len * ahead, ez = mc.player.getZ() + dz / len * ahead;
        double y = mc.player.getY() + 1.0;
        if (!ElytraPathPlanner.lineClear(worldTerrain, mc.player.getX(), y, mc.player.getZ(), ex, y, ez, 0.35)) return false;
        if (Math.abs(mc.player.getBlockY() - minerTargetFeetY()) <= 2) return true;

        double top = y + RESUME_OPEN_HEIGHT;
        return ElytraPathPlanner.lineClear(worldTerrain, mc.player.getX(), top, mc.player.getZ(), ex, top, ez, 0.35);
    }

    /** True when the cells a double-jump takeoff passes through, up to {@link #TAKEOFF_HEADROOM} above the feet, are open. */
    private boolean hasTakeoffHeadroom() {
        BlockPos feet = mc.player.blockPosition();
        worldTerrain.newTick();
        for (int dy = 2; dy <= TAKEOFF_HEADROOM; dy++) {
            if (worldTerrain.blocked(feet.getX(), feet.getY() + dy, feet.getZ())) return false;
        }
        return true;
    }

    /** Breaks the blocks above the head that a jump-takeoff needs; true once clear, unbreakable or timed out. */
    private boolean clearTakeoffRoom() {
        if (++takeoffClearTicks > TAKEOFF_CLEAR_TIMEOUT) return true;

        BlockPos feet = mc.player.blockPosition();
        worldTerrain.newTick();
        for (int dy = 2; dy <= TAKEOFF_HEADROOM; dy++) {
            BlockPos pos = feet.above(dy);
            if (!worldTerrain.blocked(pos.getX(), pos.getY(), pos.getZ())) continue;
            if (worldTerrain.mineCost(pos.getX(), pos.getY(), pos.getZ()) < 0f || !updateMineTool()) return true;

            selectMineTool();
            BlockUtils.breakBlock(pos, true);
            return false;
        }
        return true;
    }

    // ---- Elytra repair: mend nearly broken elytras with XP bottles, optionally fetched from an Ender Chest ----

    private boolean needsRepair() {
        if (!repairElytras.get() || repairSuppressed || mc.player == null) return false;

        boolean anyElytra = false;
        ItemStack worn = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (worn.is(Items.ELYTRA)) {
            anyElytra = true;
            if (elytraRemaining(worn) > repairBelow.get()) return false;
        }

        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.is(Items.ELYTRA)) continue;
            anyElytra = true;
            if (elytraRemaining(stack) > repairBelow.get()) return false;
        }

        return anyElytra && hasRepairSupplies();
    }

    private boolean hasRepairSupplies() {
        if (countXpBottles() > 0) return true;
        if (findInventoryXpShulker() != -1) return true;
        if (!enderChestXp.get()) return false;
        return InvUtils.find(Items.ENDER_CHEST).found() || (mc.player.onGround() && findNearbyEnderChest() != null);
    }

    private int elytraRemaining(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    private int allowedElytraDamage(ItemStack stack) {
        return (int) Math.floor(stack.getMaxDamage() * (1.0 - repairTarget.get() / 100.0));
    }

    private boolean elytraNeedsMend(ItemStack stack) {
        return stack != null && stack.is(Items.ELYTRA) && stack.getDamageValue() > allowedElytraDamage(stack);
    }

    private int countXpBottles() {
        return InvUtils.find(Items.EXPERIENCE_BOTTLE).count();
    }

    private int totalMissingElytraDurability() {
        int missing = 0;
        ItemStack worn = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (elytraNeedsMend(worn)) missing += worn.getDamageValue() - allowedElytraDamage(worn);
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (elytraNeedsMend(stack)) missing += stack.getDamageValue() - allowedElytraDamage(stack);
        }
        return missing;
    }

    private void beginRepair(boolean fromFlight) {
        resetRepairRuntime();
        repairStep = RepairStep.PLAN;
        if (fromFlight) {
            beginLanding(LandingReason.REPAIR);
        } else {
            releaseMovement();
            state = State.REPAIR;
            stateTicks = 0;
        }
    }

    private void resetRepairRuntime() {
        repairStep = RepairStep.PLAN;
        repairFailed = false;
        repairChestPlaced = false;
        repairChestPos = null;
        repairShulkerPos = null;
        repairShulkerKey = null;
        repairTicks = 0;
        repairShulkerCount = 0;
        repairChestCount = 0;
        repairBottleCount = 0;
        repairLastDamage = -1;
        repairThrowsWithoutProgress = 0;
        repairActionIssued = false;
    }

    private void setRepairStep(RepairStep step) {
        repairStep = step;
        repairTicks = 0;
        repairActionIssued = false;
    }

    private void abortRepair(String message) {
        warning(message);
        repairFailed = true;
        repairSuppressed = true;
        setRepairStep(RepairStep.CLEANUP);
    }

    private void tickRepair() {
        if (mc.player == null || mc.level == null) return;
        if (actionDelayTicks > 0) return;

        switch (repairStep) {
            case PLAN -> repairPlan();
            case MOVE -> repairMove();
            case CHEST_PLACE -> repairPlaceChest();
            case CHEST_OPEN -> openRepairContainer(repairChestPos, "Ender Chest", RepairStep.CHEST_TAKE);
            case CHEST_TAKE -> repairTakeFromChest();
            case CHEST_CLOSE -> closeRepairScreen(repairShulkerKey != null ? RepairStep.SHULKER_PLACE : RepairStep.MEND);
            case SHULKER_PLACE -> repairPlaceShulker();
            case SHULKER_OPEN -> openRepairContainer(repairShulkerPos, "XP shulker", RepairStep.SHULKER_TAKE);
            case SHULKER_TAKE -> repairTakeBottles();
            case SHULKER_CLOSE -> closeRepairScreen(RepairStep.SHULKER_BREAK);
            case SHULKER_BREAK -> repairBreakShulker();
            case SHULKER_PICKUP -> repairPickupShulker();
            case MEND -> repairMend();
            case RETURN_OPEN -> openRepairContainer(repairChestPos, "Ender Chest", RepairStep.RETURN_PUT);
            case RETURN_PUT -> repairReturnShulker();
            case RETURN_CLOSE -> closeRepairScreen(repairChestPlaced ? RepairStep.CHEST_BREAK : RepairStep.DONE);
            case CHEST_BREAK -> repairBreakChest();
            case CHEST_PICKUP -> repairPickupChest();
            case CLEANUP -> repairCleanup();
            case DONE -> finishRepair();
        }
    }

    private void repairPlan() {
        releaseMovement();

        if (countXpBottles() > 0) {
            setRepairStep(RepairStep.MEND);
            return;
        }

        int shulkerSlot = findInventoryXpShulker();
        if (shulkerSlot != -1) {
            repairShulkerKey = nonXpKey(mc.player.getInventory().getItem(shulkerSlot));
            setRepairStep(RepairStep.SHULKER_PLACE);
            return;
        }

        if (!enderChestXp.get()) {
            abortRepair("No XP bottles to repair with.");
            return;
        }

        BlockPos nearby = findNearbyEnderChest();
        if (nearby != null) {
            repairChestPos = nearby;
            repairChestPlaced = false;
            setRepairStep(RepairStep.CHEST_OPEN);
            return;
        }

        if (!InvUtils.find(Items.ENDER_CHEST).found()) {
            abortRepair("No XP bottles and no Ender Chest to fetch them from.");
            return;
        }

        restockPad = null;
        resetRestockPadSearch();
        setRepairStep(RepairStep.MOVE);
    }

    private void repairMove() {
        if (++repairTicks > 300) {
            abortRepair("Could not find a spot to place the Ender Chest.");
            return;
        }

        if (restockPad == null) {
            restockPad = findNextRestockPad();
            if (restockPad == null) return;
        }

        moveToward(restockPad.playerBase.getX() + 0.5, restockPad.playerBase.getZ() + 0.5, () -> {
            releaseMovement();
            setRepairStep(RepairStep.CHEST_PLACE);
        });
    }

    private void repairPlaceChest() {
        if (countEmptyLooseSlots() < 1) {
            abortRepair("Inventory is full.");
            return;
        }

        int slot = prepareRepairHotbarItem(Items.ENDER_CHEST);
        if (slot == -1) {
            if (++repairTicks > 20) abortRepair("Could not move the Ender Chest to the hotbar.");
            return;
        }

        BlockPos pos = restockPad.containerBase.above();
        repairChestCount = InvUtils.find(Items.ENDER_CHEST).count();
        if (!BlockUtils.place(pos, InteractionHand.MAIN_HAND, slot, false, 0, true, true, true)) {
            abortRepair("Failed to place the Ender Chest.");
            return;
        }

        repairChestPos = pos.immutable();
        repairChestPlaced = true;
        actionDelayTicks = inventoryDelay.get();
        setRepairStep(RepairStep.CHEST_OPEN);
    }

    /** Right-clicks the block until its screen opens; moves on to {@code next} once it has. */
    private void openRepairContainer(BlockPos pos, String label, RepairStep next) {
        if (pos == null) {
            abortRepair("Lost track of the " + label + ".");
            return;
        }

        if (mc.gui.screen() != null && mc.player.containerMenu != null
            && mc.player.containerMenu.slots.size() > PLAYER_INVENTORY_SLOTS) {
            actionDelayTicks = inventoryDelay.get();
            setRepairStep(next);
            return;
        }

        repairTicks++;
        if (mc.level.getBlockState(pos).isAir()) {
            if (repairTicks > 40) abortRepair("The " + label + " is gone.");
            return;
        }

        if (repairTicks % 20 == 1) {
            mc.gameMode.useItemOn(
                mc.player,
                InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false)
            );
        } else if (repairTicks > 80) {
            abortRepair("The " + label + " did not open.");
        }
    }

    private void closeRepairScreen(RepairStep next) {
        if (mc.player.containerMenu != null && !mc.player.containerMenu.getCarried().isEmpty()) {
            clearCursorAfterSwap();
            actionDelayTicks = inventoryDelay.get();
            return;
        }

        if (mc.gui.screen() != null) {
            mc.gui.screen().onClose();
            actionDelayTicks = inventoryDelay.get();
            return;
        }

        setRepairStep(next);
    }

    private void repairTakeFromChest() {
        if (mc.gui.screen() == null || mc.player.containerMenu == null) {
            setRepairStep(RepairStep.CHEST_OPEN);
            return;
        }

        if (repairActionIssued) {
            boolean arrived = repairShulkerKey != null
                ? countInventoryShulkerBoxes() > repairShulkerCount
                : countXpBottles() > repairBottleCount;
            if (arrived) {
                setRepairStep(RepairStep.CHEST_CLOSE);
            } else if (++repairTicks > 20) {
                abortRepair("Could not take the XP supplies out of the Ender Chest.");
            }
            return;
        }

        if (countEmptyLooseSlots() < 2) {
            abortRepair("Not enough free inventory slots to take the XP supplies.");
            return;
        }

        int loose = findContainerSlot(stack -> stack.is(Items.EXPERIENCE_BOTTLE));
        if (loose != -1) {
            repairShulkerKey = null;
            repairBottleCount = countXpBottles();
            InvUtils.shiftClick().slotId(loose);
        } else {
            int shulker = findContainerSlot(stack -> isShulkerBox(stack) && countXpBottlesInShulker(stack) > 0);
            if (shulker == -1) {
                abortRepair("No XP bottles or XP shulker were found in your Ender Chest.");
                return;
            }

            repairShulkerKey = nonXpKey(mc.player.containerMenu.slots.get(shulker).getItem());
            repairShulkerCount = countInventoryShulkerBoxes();
            InvUtils.shiftClick().slotId(shulker);
        }

        repairActionIssued = true;
        repairTicks = 0;
        actionDelayTicks = inventoryDelay.get();
    }

    private void repairPlaceShulker() {
        int slot = findShulkerByKey(repairShulkerKey, true);
        if (slot == -1) {
            abortRepair("The XP shulker went missing from your inventory.");
            return;
        }

        if (!SlotUtils.isHotbar(slot)) {
            if (++repairTicks > 20) {
                abortRepair("Could not move the XP shulker to the hotbar.");
                return;
            }

            InvUtils.move().from(slot).toHotbar(findRepairHotbarSlot());
            if (!clearCursorStackToEmptySlot()) {
                abortRepair("Unable to clear the cursor after moving the XP shulker.");
                return;
            }
            actionDelayTicks = Math.max(1, inventoryDelay.get());
            return;
        }

        BlockPos pos = findShulkerPlacePos();
        if (pos == null) {
            abortRepair("No free spot next to you to set the XP shulker down.");
            return;
        }

        repairShulkerCount = countInventoryShulkerBoxes();
        if (!BlockUtils.place(pos, InteractionHand.MAIN_HAND, slot, false, 0, true, true, true)) {
            abortRepair("Failed to place the XP shulker.");
            return;
        }

        repairShulkerPos = pos.immutable();
        actionDelayTicks = inventoryDelay.get();
        setRepairStep(RepairStep.SHULKER_OPEN);
    }

    private void repairTakeBottles() {
        if (mc.gui.screen() == null || mc.player.containerMenu == null) {
            setRepairStep(RepairStep.SHULKER_OPEN);
            return;
        }

        int wanted = Math.max(8, (int) Math.ceil(totalMissingElytraDurability() / 10.0) + 4);
        int slot = findContainerSlot(stack -> stack.is(Items.EXPERIENCE_BOTTLE));
        boolean room = countEmptyLooseSlots() >= 2;

        if (slot == -1 || countXpBottles() >= wanted || !room) {
            if (countXpBottles() <= 0) {
                abortRepair(slot == -1 ? "The XP shulker is empty." : "Not enough free inventory slots to take XP bottles.");
                return;
            }

            setRepairStep(RepairStep.SHULKER_CLOSE);
            return;
        }

        InvUtils.shiftClick().slotId(slot);
        actionDelayTicks = inventoryDelay.get();
    }

    private void repairBreakShulker() {
        if (mc.gui.screen() != null) {
            mc.gui.screen().onClose();
            return;
        }

        if (repairShulkerPos != null && mc.level.getBlockState(repairShulkerPos).getBlock() instanceof ShulkerBoxBlock) {
            if (++repairTicks > 400) {
                warning("The XP shulker could not be broken.");
                setRepairStep(RepairStep.DONE);
                return;
            }

            BlockUtils.breakBlock(repairShulkerPos, true);
            return;
        }

        setRepairStep(RepairStep.SHULKER_PICKUP);
    }

    private void repairPickupShulker() {
        if (countInventoryShulkerBoxes() >= repairShulkerCount) {
            releaseMovement();
            repairShulkerPos = null;
            setRepairStep(repairFailed ? RepairStep.CLEANUP : RepairStep.MEND);
            return;
        }

        if (++repairTicks > SHULKER_PICKUP_TIMEOUT * 2) {
            releaseMovement();
            warning("The XP shulker was not picked up in time; it is lying on the ground nearby.");
            repairShulkerPos = null;
            repairShulkerKey = null;
            setRepairStep(repairFailed ? RepairStep.CLEANUP : RepairStep.MEND);
            return;
        }

        if (countEmptyLooseSlots() <= 0 && repairTicks % SHULKER_PICKUP_REACQUIRE_INTERVAL == 0) {
            dropReplaceableLooseElytraForPickup();
        }

        ItemEntity nearest = collectShulkerDrops().stream()
            .min(Comparator.comparingDouble(entity -> mc.player.distanceToSqr(entity.getX(), entity.getY(), entity.getZ())))
            .orElse(null);
        if (nearest != null) moveToward(nearest.getX(), nearest.getZ(), this::releaseMovement);
        else releaseMovement();
    }

    private void repairMend() {
        releaseMovement();

        if (mc.gui.screen() != null) {
            mc.gui.screen().onClose();
            return;
        }

        ItemStack worn = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (elytraNeedsMend(worn)) {
            mendWornElytra(worn);
            return;
        }

        int loose = -1;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            if (elytraNeedsMend(mc.player.getInventory().getItem(i))) {
                loose = i;
                break;
            }
        }

        if (loose != -1) {
            swapInventoryElytraWithChestSlot(loose);
            repairLastDamage = -1;
            repairThrowsWithoutProgress = 0;
            actionDelayTicks = Math.max(1, inventoryDelay.get());
            return;
        }

        finishMend();
    }

    private void mendWornElytra(ItemStack worn) {
        int missing = worn.getDamageValue() - allowedElytraDamage(worn);
        int pending = pendingRepairDurability();
        if (missing <= pending) return;

        if (repairLastDamage == -1 || worn.getDamageValue() < repairLastDamage) {
            repairLastDamage = worn.getDamageValue();
            repairThrowsWithoutProgress = 0;
        } else if (repairThrowsWithoutProgress >= 6 && pending == 0) {
            warning("The XP is not repairing the elytra (no Mending, or another Mending item absorbed it). Stopping repair.");
            repairSuppressed = true;
            finishMend();
            return;
        }

        FindItemResult bottle = InvUtils.find(stack -> stack.is(Items.EXPERIENCE_BOTTLE), 0, PLAYER_INVENTORY_SLOTS - 1);
        if (!bottle.found()) {
            warning("Out of XP bottles.");
            finishMend();
            return;
        }

        int slot = bottle.slot();
        repairThrowsWithoutProgress++;
        actionDelayTicks = repairThrowDelay.get();
        Rotations.rotate(mc.player.getYRot(), 90, () -> {
            InventoryManager.swapTo(slot, false, true);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            InventoryManager.swapBack(false);
        });
    }

    /** Durability that nearby orbs and in-flight bottles will still repair (Mending: 2 per XP, bottle about 7 XP). */
    private int pendingRepairDurability() {
        int pending = 0;
        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(8))) {
            if (entity instanceof ExperienceOrb orb) pending += orb.getValue() * 2;
            else if (entity instanceof ThrownExperienceBottle) pending += 14;
        }
        return pending;
    }

    private void swapInventoryElytraWithChestSlot(int inventorySlot) {
        THMUtils.fakeInventoryOpen(true);
        try {
            int syncId = mc.player.containerMenu.containerId;
            int spareId = SlotUtils.indexToId(inventorySlot);
            int chestId = SlotUtils.indexToId(CHEST_SLOT_INDEX);

            mc.gameMode.handleContainerInput(syncId, spareId, 0, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(syncId, chestId, 0, ContainerInput.PICKUP, mc.player);
            mc.gameMode.handleContainerInput(syncId, spareId, 0, ContainerInput.PICKUP, mc.player);

            if (!mc.player.containerMenu.getCarried().isEmpty()) clearCursorAfterSwap();
        } finally {
            THMUtils.fakeInventoryOpen(false);
        }
    }

    /** Leaves the most durable elytra on, then heads to the shulker/chest clean-up. */
    private void finishMend() {
        ItemStack worn = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        int bestSlot = -1;
        int bestRemaining = worn.is(Items.ELYTRA) ? elytraRemaining(worn) : -1;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.is(Items.ELYTRA) || elytraRemaining(stack) <= bestRemaining) continue;
            bestRemaining = elytraRemaining(stack);
            bestSlot = i;
        }

        if (bestSlot != -1) swapInventoryElytraWithChestSlot(bestSlot);
        actionDelayTicks = Math.max(1, inventoryDelay.get());

        boolean returnIt = returnShulker.get() && repairShulkerKey != null && repairChestPos != null && !repairFailed;
        if (returnIt) setRepairStep(RepairStep.RETURN_OPEN);
        else setRepairStep(repairChestPlaced ? RepairStep.CHEST_BREAK : RepairStep.DONE);
    }

    private void repairReturnShulker() {
        if (mc.gui.screen() == null || mc.player.containerMenu == null) {
            setRepairStep(RepairStep.RETURN_OPEN);
            return;
        }

        int slot = findShulkerByKey(repairShulkerKey, false);
        if (slot == -1) {
            setRepairStep(RepairStep.RETURN_CLOSE);
            return;
        }

        if (++repairTicks > 15) {
            warning("The Ender Chest has no room for the XP shulker; it stays in your inventory.");
            setRepairStep(RepairStep.RETURN_CLOSE);
            return;
        }

        InvUtils.shiftClick().slot(slot);
        actionDelayTicks = inventoryDelay.get();
    }

    private void repairBreakChest() {
        if (mc.gui.screen() != null) {
            mc.gui.screen().onClose();
            return;
        }

        if (repairChestPos == null || !repairChestPlaced || !mc.level.getBlockState(repairChestPos).is(Blocks.ENDER_CHEST)) {
            setRepairStep(repairChestPlaced ? RepairStep.CHEST_PICKUP : RepairStep.DONE);
            return;
        }

        int tool = findSilkTouchPickaxeSlot();
        if (tool == -1) {
            warning("No Silk Touch pickaxe found. Leaving the Ender Chest where it is.");
            setRepairStep(RepairStep.DONE);
            return;
        }

        if (!SlotUtils.isHotbar(tool)) {
            if (++repairTicks > 20) {
                warning("Could not move the pickaxe to the hotbar. Leaving the Ender Chest where it is.");
                setRepairStep(RepairStep.DONE);
                return;
            }

            InvUtils.move().from(tool).toHotbar(findRepairHotbarSlot());
            if (!clearCursorStackToEmptySlot()) {
                warning("Unable to clear the cursor. Leaving the Ender Chest where it is.");
                setRepairStep(RepairStep.DONE);
                return;
            }
            actionDelayTicks = Math.max(1, inventoryDelay.get());
            return;
        }

        if (++repairTicks > 400) {
            warning("The Ender Chest could not be broken. Leaving it where it is.");
            setRepairStep(RepairStep.DONE);
            return;
        }

        InvUtils.swap(tool, true);
        BlockUtils.breakBlock(repairChestPos, true);
    }

    private void repairPickupChest() {
        InvUtils.swapBack();

        if (InvUtils.find(Items.ENDER_CHEST).count() >= repairChestCount) {
            releaseMovement();
            setRepairStep(RepairStep.DONE);
            return;
        }

        if (++repairTicks > SHULKER_PICKUP_TIMEOUT * 2) {
            releaseMovement();
            warning("The Ender Chest was not picked up in time; it is lying on the ground nearby.");
            setRepairStep(RepairStep.DONE);
            return;
        }

        AABB area = mc.player.getBoundingBox().inflate(SHULKER_PICKUP_SEARCH_RADIUS, 2.0, SHULKER_PICKUP_SEARCH_RADIUS);
        ItemEntity nearest = mc.level.getEntitiesOfClass(ItemEntity.class, area, entity -> entity.isAlive() && entity.getItem().is(Items.ENDER_CHEST))
            .stream()
            .min(Comparator.comparingDouble(entity -> mc.player.distanceToSqr(entity.getX(), entity.getY(), entity.getZ())))
            .orElse(null);
        if (nearest != null) moveToward(nearest.getX(), nearest.getZ(), this::releaseMovement);
        else releaseMovement();
    }

    private void repairCleanup() {
        if (mc.player.containerMenu != null && !mc.player.containerMenu.getCarried().isEmpty()) {
            clearCursorAfterSwap();
            actionDelayTicks = inventoryDelay.get();
            return;
        }

        if (mc.gui.screen() != null) {
            mc.gui.screen().onClose();
            actionDelayTicks = inventoryDelay.get();
            return;
        }

        if (repairShulkerPos != null && mc.level.getBlockState(repairShulkerPos).getBlock() instanceof ShulkerBoxBlock) {
            setRepairStep(RepairStep.SHULKER_BREAK);
            return;
        }

        if (repairChestPlaced && repairChestPos != null && mc.level.getBlockState(repairChestPos).is(Blocks.ENDER_CHEST)) {
            setRepairStep(RepairStep.CHEST_BREAK);
            return;
        }

        setRepairStep(RepairStep.DONE);
    }

    private void finishRepair() {
        InvUtils.swapBack();
        releaseMovement();
        if (mc.gui.screen() != null) mc.gui.screen().onClose();

        if (needsRepair()) repairSuppressed = true;

        restockPad = null;
        resetRestockPadSearch();
        resetRepairRuntime();

        if (isInsideArrivalRadius()) state = State.ARRIVED_WAIT;
        else state = State.TAKEOFF_AWAIT_GROUND;
        stateTicks = 0;
    }

    private int prepareRepairHotbarItem(Item item) {
        FindItemResult hotbar = InvUtils.findInHotbar(item);
        if (hotbar.found()) return hotbar.slot();

        FindItemResult inventory = InvUtils.find(item);
        if (!inventory.found()) return -1;

        InvUtils.move().from(inventory.slot()).toHotbar(findRepairHotbarSlot());
        if (!clearCursorStackToEmptySlot()) {
            abortRepair("Unable to clear the cursor after moving an item.");
            return -1;
        }

        actionDelayTicks = Math.max(1, inventoryDelay.get());
        return -1;
    }

    private int findRepairHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return i;
        }

        for (int i = 0; i < 9; i++) {
            if (!isProtectedRestockHotbarStack(mc.player.getInventory().getItem(i))) return i;
        }

        return 8;
    }

    private int findContainerSlot(Predicate<ItemStack> predicate) {
        int containerSlots = getContainerSlotCount();
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = mc.player.containerMenu.slots.get(i).getItem();
            if (!stack.isEmpty() && predicate.test(stack)) return i;
        }
        return -1;
    }

    private BlockPos findNearbyEnderChest() {
        BlockPos origin = mc.player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (!mc.level.getBlockState(pos).is(Blocks.ENDER_CHEST)) continue;

                    double dist = mc.player.distanceToSqr(Vec3.atCenterOf(pos));
                    if (dist < bestDist && dist <= 20.0) {
                        bestDist = dist;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }

    private BlockPos findShulkerPlacePos() {
        BlockPos below = mc.player.blockPosition().below();
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos base = below.relative(dir);
            BlockPos pos = base.above();
            if (pos.equals(repairChestPos)) continue;
            if (!isValidPadBase(base)) continue;
            if (!mc.level.getBlockState(pos.above()).isAir()) continue;
            return pos;
        }
        return null;
    }

    private int findSilkTouchPickaxeSlot() {
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || !Utils.hasEnchantment(stack, Enchantments.SILK_TOUCH)) continue;
            if (stack.getDestroySpeed(Blocks.ENDER_CHEST.defaultBlockState()) > 1.0f) return i;
        }
        return -1;
    }

    private int countXpBottlesInShulker(ItemStack shulker) {
        if (!isShulkerBox(shulker)) return 0;
        ItemContainerContents container = shulker.get(DataComponents.CONTAINER);
        if (container == null) return 0;

        int count = 0;
        for (ItemStack stack : container.nonEmptyItemCopyStream().toList()) {
            if (stack.is(Items.EXPERIENCE_BOTTLE)) count += stack.getCount();
        }
        return count;
    }

    private int findInventoryXpShulker() {
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            int count = countXpBottlesInShulker(mc.player.getInventory().getItem(i));
            if (count > bestCount) {
                bestCount = count;
                best = i;
            }
        }
        return best;
    }

    /** Identifies a shulker by colour and everything except its XP bottles, so it can be found again after looting. */
    private String nonXpKey(ItemStack shulker) {
        List<String> entries = new ArrayList<>();
        ItemContainerContents container = shulker.get(DataComponents.CONTAINER);
        if (container != null) {
            for (ItemStack stack : container.nonEmptyItemCopyStream().toList()) {
                if (stack.is(Items.EXPERIENCE_BOTTLE)) continue;
                entries.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount());
            }
        }

        Collections.sort(entries);
        return BuiltInRegistries.ITEM.getKey(shulker.getItem()) + "|" + String.join(",", entries);
    }

    private int findShulkerByKey(String key, boolean requireBottles) {
        if (key == null) return -1;
        for (int i = 0; i < PLAYER_INVENTORY_SLOTS; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isShulkerBox(stack) || !key.equals(nonXpKey(stack))) continue;
            if (requireBottles && countXpBottlesInShulker(stack) <= 0) continue;
            return i;
        }
        return -1;
    }
}
