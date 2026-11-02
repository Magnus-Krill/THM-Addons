/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.IBuilderProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.schematic.ISchematic;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.gui.BaritoneBuildScreen;
import xyz.thm.addon.utils.BuildMaterialIndex;
import xyz.thm.addon.utils.BuildSchematic;
import xyz.thm.addon.utils.InventoryManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class BetterBaritoneBuild extends Module {
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup rules = settings.createGroup("Build rules");
    public final Setting<Boolean> linkStorage = general.add(new BoolSetting.Builder()
        .name("link-storage").description("Index containers when you open them.").defaultValue(true).build());
    private final Setting<Integer> stacks = general.add(new IntSetting.Builder()
        .name("stacks-per-material").description("Stacks to fetch per missing material.")
        .defaultValue(1).range(1, 16).sliderRange(1, 8).build());
    private final Setting<Integer> clickDelay = general.add(new IntSetting.Builder()
        .name("click-delay").description("Ticks between confirmed transfers.")
        .defaultValue(4).range(1, 40).sliderRange(1, 20).build());
    private final Setting<Integer> travelTimeout = general.add(new IntSetting.Builder()
        .name("travel-timeout").description("Seconds allowed to reach each container.")
        .defaultValue(120).range(10, 600).sliderRange(10, 300).build());
    public final Setting<Boolean> stripLogs = rules.add(new BoolSetting.Builder()
        .name("strip-logs").description("Place raw logs and strip them with a hotbar axe.")
        .defaultValue(false).onChanged(value -> refreshRules()).build());
    public final Setting<Boolean> grassAsDirt = rules.add(new BoolSetting.Builder()
        .name("grass-as-dirt").description("Accept dirt and grass interchangeably and place dirt.")
        .defaultValue(false).onChanged(value -> refreshRules()).build());
    private final Setting<Boolean> ignoreExisting = rules.add(new BoolSetting.Builder()
        .name("ignore-existing").description("Keep existing non-air blocks.")
        .defaultValue(false).onChanged(value -> refreshRules()).build());
    private final Setting<Boolean> ignoreDirection = rules.add(new BoolSetting.Builder()
        .name("ignore-direction").description("Accept existing blocks with different orientations.")
        .defaultValue(false).onChanged(value -> refreshRules()).build());
    private final Setting<List<Block>> skipped = rules.add(new BlockListSetting.Builder()
        .name("skip-blocks").description("Ignore schematic positions requesting these blocks.")
        .defaultValue(List.of()).onChanged(value -> refreshRules()).build());
    private final Setting<List<Block>> ignored = rules.add(new BlockListSetting.Builder()
        .name("keep-in-air").description("Keep these blocks where the schematic requests air.")
        .defaultValue(List.of()).onChanged(value -> refreshRules()).build());
    private final Setting<List<String>> replacementRules = rules.add(new StringListSetting.Builder()
        .name("replacement-rules").description("Schematic block = replacement block.")
        .defaultValue(List.of()).visible(() -> false).onChanged(value -> refreshRules()).build());
    private final Setting<List<String>> acceptanceRules = rules.add(new StringListSetting.Builder()
        .name("acceptance-rules").description("Schematic block = accepted existing block.")
        .defaultValue(List.of()).visible(() -> false).onChanged(value -> refreshRules()).build());

    private final BuildMaterialIndex index = new BuildMaterialIndex();
    private final RestockProcess process = new RestockProcess();
    private IBaritone baritone;
    private IBuilderProcess builder;
    private BuildSchematic schematic;
    private Vec3i origin;
    private ClientLevel level;
    private Path indexPath;
    private boolean dirty;
    private int tick, lastSave;
    private BlockPos pendingOpen, observedPos;
    private int pendingUntil, observedMenu = -1;
    private Map<Item, Integer> pendingMaterials;
    private final Map<Item, Integer> wanted = new LinkedHashMap<>();
    private final Set<BlockPos> visited = new HashSet<>();
    private BuildMaterialIndex.Storage target;
    private GoalGetToBlock goal;
    private boolean fetching, opening, receivedItems;
    private int targetSince, openedAt, nextClick, clickedAt;
    private Item clickedItem;
    private int countBeforeClick;
    private BlockPos stripPos;
    private int stripAt, stripAttempts;
    private String status = "Ready";

    public BetterBaritoneBuild() {
        super(THMAddon.MAIN, "better-baritone-build", "Build rules and automatic material fetching from indexed storage.");
    }

    public static BetterBaritoneBuild get() {
        return Modules.get() == null ? null : Modules.get().get(BetterBaritoneBuild.class);
    }

    @Override public void onActivate() {
        IBaritone current = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (baritone != current) {
            baritone = current;
            baritone.getPathingControlManager().registerProcess(process);
        }
        ensureWorld();
    }

    @Override public void onDeactivate() {
        stopFetching("Disabled");
        saveIndex();
        if (schematic != null) schematic.rules = defaultRules();
        schematic = null;
        builder = null;
        level = null;
        clearObservation();
    }

    @Override public WWidget getWidget(GuiTheme theme) {
        WButton button = theme.button("Build and storage manager");
        button.action = () -> mc.gui.setScreen(new BaritoneBuildScreen(theme, this));
        return button;
    }

    public String status() { return status; }
    public Collection<BuildMaterialIndex.Storage> storage() {
        return ensureWorld() ? List.copyOf(index.entries()) : List.of();
    }
    public List<String> ruleList(boolean accept) { return List.copyOf((accept ? acceptanceRules : replacementRules).get()); }

    public void putRule(Block from, Block to, boolean accept) {
        String prefix = BuiltInRegistries.BLOCK.getKey(from) + "=";
        Setting<List<String>> setting = accept ? acceptanceRules : replacementRules;
        List<String> next = new ArrayList<>(setting.get());
        if (!accept) next.removeIf(rule -> rule.startsWith(prefix));
        String rule = prefix + BuiltInRegistries.BLOCK.getKey(to);
        if (!next.contains(rule)) next.add(rule);
        setting.set(next);
    }

    public void removeRule(String rule, boolean accept) {
        Setting<List<String>> setting = accept ? acceptanceRules : replacementRules;
        List<String> next = new ArrayList<>(setting.get());
        next.remove(rule);
        setting.set(next);
    }

    public void forgetStorage(BlockPos pos) {
        if (!ensureWorld()) return;
        if (target != null && target.pos().equals(pos)) stopFetching("Storage removed; build paused");
        index.remove(pos);
        dirty = true;
        saveIndex();
    }

    public void retry() {
        if (baritone == null || fetching) return;
        stripAttempts = 0;
        stripPos = null;
        IBuilderProcess current = baritone.getBuilderProcess();
        if (current.isActive()) current.resume();
    }

    public ISchematic prepareBuild(IBuilderProcess current, ISchematic source, Vec3i start) {
        ensureWorld();
        stopFetching("Building");
        builder = current;
        origin = new Vec3i(start.getX(), start.getY(), start.getZ());
        schematic = new BuildSchematic(source, configuredRules());
        stripPos = null;
        stripAttempts = 0;
        return schematic;
    }

    public void buildStopped(IBuilderProcess current) {
        if (builder != current) return;
        stopFetching("Build stopped");
        schematic = null;
        builder = null;
    }

    public void missingMaterials(IBuilderProcess current, Map<BlockState, Integer> missing) {
        if (fetching || !ensureWorld() || current != baritone.getBuilderProcess()) return;
        builder = current;
        pendingMaterials = new LinkedHashMap<>();
        missing.forEach((state, count) -> {
            Item item = state.getBlock().asItem();
            if (item != Items.AIR && count > 0) pendingMaterials.merge(item, count, Integer::sum);
        });
    }

    @EventHandler private void onLeft(GameLeftEvent event) {
        stopFetching("Disconnected");
        saveIndex();
        level = null;
        schematic = null;
        builder = null;
        index.clear();
        indexPath = null;
        clearObservation();
    }

    private boolean ensureWorld() {
        if (mc.level == null || mc.player == null) return false;
        if (level == mc.level) return true;
        stopFetching("Ready");
        saveIndex();
        dirty = false;
        if (schematic != null) schematic.rules = defaultRules();
        schematic = null;
        builder = null;
        level = mc.level;
        clearObservation();
        String dimension = level.dimension().identifier().toString().replace(':', '_').replace('/', '_');
        indexPath = THMAddon.GetConfigFile("baritone-build", dimension + ".json").toPath();
        try {
            index.load(indexPath);
        } catch (IOException e) {
            index.clear();
            error("Cannot load storage index: %s", e.getMessage());
        }
        return true;
    }

    private void saveIndex() {
        if (!dirty || indexPath == null) return;
        try {
            index.save(indexPath);
            dirty = false;
            lastSave = tick;
        } catch (IOException e) {
            error("Cannot save storage index: %s", e.getMessage());
            lastSave = tick;
        }
    }

    @EventHandler private void onInteract(InteractBlockEvent event) {
        if (!ensureWorld() || event.isCancelled()) return;
        BlockPos pos = event.result.getBlockPos();
        if (!supported(mc.level.getBlockState(pos))) { pendingOpen = null; return; }
        if (!linkStorage.get() && index.get(canonical(pos)) == null) return;
        pendingOpen = canonical(pos);
        pendingUntil = tick + 60;
    }

    @EventHandler private void onInventory(InventoryEvent event) {
        if (!ensureWorld()) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (event.packet.containerId() != menu.containerId || containerSize(menu) == 0) return;
        if (pendingOpen != null && tick <= pendingUntil) {
            if (!validMenu(pendingOpen, menu)) return;
            observedPos = pendingOpen;
            observedMenu = menu.containerId;
            pendingOpen = null;
        }
        if (observedPos != null && observedMenu == menu.containerId) snapshot(menu);
    }

    @EventHandler private void onTick(TickEvent.Post event) {
        tick++;
        if (!ensureWorld()) return;
        if (pendingOpen != null && tick > pendingUntil) pendingOpen = null;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (observedPos != null) {
            if (menu.containerId != observedMenu || !validMenu(observedPos, menu)) {
                observedPos = null;
                observedMenu = -1;
            } else snapshot(menu);
        }
        if (dirty && tick - lastSave >= 40) saveIndex();
        if (!fetching && pendingMaterials != null) startFetching();
    }

    private void snapshot(AbstractContainerMenu menu) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (int slot = 0; slot < containerSize(menu); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!stack.isEmpty()) items.merge(itemId(stack.getItem()), stack.getCount(), Integer::sum);
        }
        try {
            dirty |= index.update(observedPos, blockId(mc.level.getBlockState(observedPos).getBlock()), items);
        } catch (IllegalStateException e) {
            error("%s", e.getMessage());
            clearObservation();
        }
    }

    private void startFetching() {
        Map<Item, Integer> missing = pendingMaterials;
        pendingMaterials = null;
        if (builder == null || !builder.isActive() || !builder.isPaused() || mc.player.containerMenu != mc.player.inventoryMenu) return;
        wanted.clear();
        for (var entry : missing.entrySet()) {
            int count = fetchTarget(entry.getValue(), stacks.get(), entry.getKey().getDefaultMaxStackSize());
            if (inventoryCount(entry.getKey()) < count) wanted.put(entry.getKey(), count);
        }
        if (wanted.isEmpty()) return;
        visited.clear();
        receivedItems = false;
        fetching = true;
        chooseStorage();
    }

    static int fetchTarget(int missing, int stacks, int stackSize) {
        return Math.min(missing, stacks * stackSize);
    }

    private Set<String> neededIds() {
        Set<String> needed = new HashSet<>();
        wanted.forEach((item, count) -> { if (inventoryCount(item) < count) needed.add(itemId(item)); });
        return needed;
    }

    private void chooseStorage() {
        Set<String> needed = neededIds();
        if (needed.isEmpty()) { finishFetching(); return; }
        target = index.nearest(mc.player.blockPosition(), needed, visited);
        if (target == null) {
            if (receivedItems) finishFetching();
            else fail("No usable indexed storage for: " + String.join(", ", needed));
            return;
        }
        visited.add(target.pos());
        goal = new GoalGetToBlock(target.pos());
        targetSince = tick;
        opening = false;
        clickedItem = null;
        status = "Fetching from " + target.pos().toShortString();
    }

    private void finishFetching() {
        closeOwnedMenu();
        stopFetching("Building");
        if (builder != null && builder.isActive()) builder.resume();
    }

    private void stopFetching(String message) {
        closeOwnedMenu();
        fetching = false;
        pendingMaterials = null;
        wanted.clear();
        visited.clear();
        target = null;
        goal = null;
        clickedItem = null;
        opening = false;
        status = message;
    }

    private void fail(String message) {
        stopFetching(message + "; build paused");
        warning("%s. Fix it and press Resume build.", message);
    }

    private void closeOwnedMenu() {
        if (fetching && mc.player != null && target != null && target.pos().equals(observedPos)
            && mc.player.containerMenu.containerId == observedMenu) mc.player.closeContainer();
    }

    private void clearObservation() {
        pendingOpen = observedPos = null;
        observedMenu = -1;
    }

    private boolean validMenu(BlockPos pos, AbstractContainerMenu menu) {
        if (!mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            || !mc.player.isWithinBlockInteractionRange(pos, 1)) return false;
        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() instanceof ShulkerBoxBlock) return menu instanceof ShulkerBoxMenu;
        if (state.getBlock() instanceof BarrelBlock) return menu instanceof ChestMenu chest && chest.getRowCount() == 3;
        if (state.getBlock() instanceof ChestBlock) return menu instanceof ChestMenu chest
            && chest.getRowCount() == (state.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? 3 : 6);
        return false;
    }

    private static int containerSize(AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu chest) return chest.getRowCount() * 9;
        return menu instanceof ShulkerBoxMenu ? 27 : 0;
    }

    private static boolean supported(BlockState state) {
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock || state.getBlock() instanceof ShulkerBoxBlock;
    }

    private BlockPos canonical(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) == ChestType.RIGHT
            ? pos.relative(ChestBlock.getConnectedDirection(state)) : pos.immutable();
    }

    private BlockHitResult reachable(BlockPos pos) {
        if (!mc.player.isWithinBlockInteractionRange(pos, 0)) return null;
        BlockHitResult hit = mc.level.clip(new ClipContext(mc.player.getEyePosition(), Vec3.atCenterOf(pos),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) ? hit : null;
    }

    private int inventoryCount(Item item) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private boolean hasRoom(ItemStack source) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty() || (ItemStack.isSameItemSameComponents(stack, source) && stack.getCount() < stack.getMaxStackSize())) return true;
        }
        return false;
    }

    private static String itemId(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
    private static String blockId(Block block) { return BuiltInRegistries.BLOCK.getKey(block).toString(); }

    private final class RestockProcess implements IBaritoneProcess {
        @Override public boolean isActive() { return BetterBaritoneBuild.this.isActive() && fetching; }
        @Override public boolean isTemporary() { return true; }
        @Override public double priority() { return -0.5; }
        @Override public String displayName0() { return "THM material retrieval"; }
        @Override public void onLostControl() { if (fetching) stopFetching("Restock cancelled; build paused"); }

        @Override public PathingCommand onTick(boolean calcFailed, boolean safeToCancel) {
            PathingCommand pause = new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            if (level != mc.level || mc.player == null || builder == null || !builder.isActive() || !builder.isPaused()) {
                stopFetching("Build changed; restock cancelled");
                return null;
            }
            if (target == null) return pause;
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                if (!target.pos().equals(observedPos) || mc.player.containerMenu.containerId != observedMenu) {
                    if (opening && tick - openedAt < 100) return pause;
                    fail("Another container was opened");
                    return pause;
                }
                loot(mc.player.containerMenu);
                return pause;
            }
            if (opening) {
                if (tick - openedAt >= 100) {
                    warning("Cannot open storage at %s; trying another.", target.pos().toShortString());
                    chooseStorage();
                }
                return pause;
            }
            if (calcFailed || tick - targetSince > travelTimeout.get() * 20) {
                warning("Cannot reach storage at %s; trying another.", target.pos().toShortString());
                chooseStorage();
                return pause;
            }
            if (mc.level.getChunkSource().hasChunk(target.pos().getX() >> 4, target.pos().getZ() >> 4)
                && !target.block().equals(blockId(mc.level.getBlockState(target.pos()).getBlock()))) {
                index.remove(target.pos());
                dirty = true;
                chooseStorage();
                return pause;
            }
            BlockHitResult hit = reachable(target.pos());
            if (hit == null) {
                if (goal.isInGoal(mc.player.blockPosition())) {
                    warning("Storage at %s is obstructed; trying another.", target.pos().toShortString());
                    chooseStorage();
                    return pause;
                }
                return new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
            }
            if (!safeToCancel || mc.player.isUsingItem() || mc.player.isShiftKeyDown()
                || InventoryManager.getInstance().getCurrentPriority() > InventoryManager.Priority.NORMAL) return pause;
            opening = true;
            openedAt = tick;
            nextClick = tick + clickDelay.get();
            BlockPos openingPos = target.pos();
            Rotations.rotate(Rotations.getYaw(hit.getLocation()), Rotations.getPitch(hit.getLocation()), () -> {
                if (!fetching || target == null || !target.pos().equals(openingPos) || mc.level != level
                    || mc.player.containerMenu != mc.player.inventoryMenu) return;
                pendingOpen = openingPos;
                pendingUntil = tick + 100;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            });
            return pause;
        }
    }

    private void loot(AbstractContainerMenu menu) {
        if (!menu.getCarried().isEmpty()) { fail("Clear the cursor before fetching"); return; }
        if (clickedItem != null) {
            if (inventoryCount(clickedItem) > countBeforeClick) {
                receivedItems = true;
                clickedItem = null;
                nextClick = tick + clickDelay.get();
            } else if (tick - clickedAt >= 100) {
                fail("Server did not confirm the item transfer");
            }
            return;
        }
        if (tick < nextClick) return;
        if (neededIds().isEmpty()) { finishFetching(); return; }
        boolean full = false;
        for (int slot = 0; slot < containerSize(menu); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.isEmpty() || inventoryCount(stack.getItem()) >= wanted.getOrDefault(stack.getItem(), 0)) continue;
            if (!hasRoom(stack)) { full = true; continue; }
            clickedItem = stack.getItem();
            countBeforeClick = inventoryCount(clickedItem);
            clickedAt = tick;
            // No local prediction: empty change hashes make the server send authoritative slot updates.
            mc.getConnection().send(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
                (short) slot, (byte) 0, ContainerInput.QUICK_MOVE, new Int2ObjectOpenHashMap<>(), HashedStack.EMPTY));
            return;
        }
        if (full) {
            if (receivedItems) finishFetching();
            else fail("Inventory is full");
            return;
        }
        snapshot(menu);
        closeOwnedMenu();
        chooseStorage();
    }

    public boolean stripNextLog(IBuilderProcess current, List<BlockState> placeable) {
        if (current != builder || schematic == null || !stripLogs.get() || fetching || current.isPaused()
            || mc.player == null || mc.level != level || mc.player.containerMenu != mc.player.inventoryMenu) return false;
        if (stripPos != null && tick - stripAt < 6) return true;
        if (placeable == null) placeable = List.of();
        for (BlockPos pos : BlockPos.betweenClosed(mc.player.blockPosition().offset(-4, -4, -4), mc.player.blockPosition().offset(4, 4, 4))) {
            BlockState state = mc.level.getBlockState(pos);
            int x = pos.getX() - origin.getX(), y = pos.getY() - origin.getY(), z = pos.getZ() - origin.getZ();
            if (!schematic.inSchematic(x, y, z, state)) continue;
            BlockState desired = schematic.target(x, y, z, state, placeable);
            if (desired == null || BuildSchematic.unstripped(desired.getBlock()) != state.getBlock()
                || !schematic.desiredState(x, y, z, state, placeable).is(desired.getBlock())) continue;
            BlockHitResult hit = reachable(pos);
            if (hit == null) continue;
            int axe = -1;
            for (int slot = 0; slot < 9; slot++) {
                if (mc.player.getInventory().getItem(slot).getItem() instanceof AxeItem) { axe = slot; break; }
            }
            if (axe == -1) {
                current.pause();
                warning("Put an axe in your hotbar and press Resume build.");
                return true;
            }
            if (mc.player.isUsingItem() || InventoryManager.getInstance().getCurrentPriority() > InventoryManager.Priority.NORMAL) return true;
            BlockPos immutable = pos.immutable();
            stripAttempts = immutable.equals(stripPos) ? stripAttempts + 1 : 1;
            stripPos = immutable;
            stripAt = tick;
            if (stripAttempts > 3) {
                current.pause();
                warning("Cannot strip log at %s; build paused.", pos.toShortString());
                return true;
            }
            int axeSlot = axe;
            Rotations.rotate(Rotations.getYaw(hit.getLocation()), Rotations.getPitch(hit.getLocation()), () -> {
                if (!isActive() || builder != current || mc.level != level || !current.isActive() || current.isPaused()
                    || fetching || mc.player.containerMenu != mc.player.inventoryMenu || mc.player.isUsingItem()) return;
                int previous = mc.player.getInventory().getSelectedSlot();
                InventoryManager inventory = InventoryManager.getInstance();
                inventory.setClientSlot(axeSlot);
                inventory.setSlot(axeSlot);
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                inventory.setClientSlot(previous);
                inventory.setSlot(previous);
            });
            return true;
        }
        return false;
    }

    private void refreshRules() {
        if (schematic != null) {
            if (fetching) stopFetching("Rules changed; press Resume build");
            schematic.rules = configuredRules();
        }
    }

    private BuildSchematic.Rules configuredRules() {
        Map<Block, Block> replacements = new HashMap<>();
        Map<Block, Set<Block>> accepted = new HashMap<>();
        for (String rule : replacementRules.get()) {
            Block[] pair = parseRule(rule);
            if (pair != null) replacements.put(pair[0], pair[1]);
        }
        for (String rule : acceptanceRules.get()) {
            Block[] pair = parseRule(rule);
            if (pair != null) accepted.computeIfAbsent(pair[0], key -> new HashSet<>()).add(pair[1]);
        }
        accepted.replaceAll((block, choices) -> Set.copyOf(choices));
        return new BuildSchematic.Rules(Map.copyOf(replacements), Map.copyOf(accepted), Set.copyOf(skipped.get()),
            Set.copyOf(ignored.get()), grassAsDirt.get(), ignoreExisting.get(), ignoreDirection.get(), stripLogs.get());
    }

    private static Block[] parseRule(String rule) {
        String[] parts = rule.split("=", -1);
        if (parts.length != 2) return null;
        Identifier from = Identifier.tryParse(parts[0]), to = Identifier.tryParse(parts[1]);
        if (from == null || to == null || !BuiltInRegistries.BLOCK.containsKey(from) || !BuiltInRegistries.BLOCK.containsKey(to)) return null;
        return new Block[] { BuiltInRegistries.BLOCK.getValue(from), BuiltInRegistries.BLOCK.getValue(to) };
    }

    private static BuildSchematic.Rules defaultRules() {
        return new BuildSchematic.Rules(Map.of(), Map.of(), Set.of(), Set.of(), false, false, false, false);
    }
}
