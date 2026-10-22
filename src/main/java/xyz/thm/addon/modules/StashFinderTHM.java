/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.StashTracker;
import xyz.thm.addon.utils.THMUtils;
import xyz.thm.addon.utils.TrustedHttp;
import xyz.thm.addon.utils.server.ServerStatusHandler;
import xyz.thm.addon.utils.server.ServerStatusHandler.ServerState;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class StashFinderTHM extends Module {
    private static final double WAYPOINT_LEAD = 48.0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSearch = settings.createGroup("Search");
    private final SettingGroup sgDiscord = settings.createGroup("Discord");

    private final Setting<Integer> minDoubleChests = sgGeneral.add(new IntSetting.Builder()
        .name("min-double-chests")
        .description("Double chests an area needs to count as a stash.")
        .defaultValue(30)
        .min(1)
        .sliderRange(5, 200)
        .build()
    );

    private final Setting<Integer> clusterRadius = sgGeneral.add(new IntSetting.Builder()
        .name("cluster-radius")
        .description("Chunks around a chunk that are added up into one stash.")
        .defaultValue(3)
        .range(0, 8)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<Integer> ignoreSpawnRadius = sgGeneral.add(new IntSetting.Builder()
        .name("ignore-spawn-radius")
        .description("Ignores chests closer than this to 0 0.")
        .defaultValue(2000)
        .min(0)
        .sliderRange(0, 20000)
        .build()
    );

    private final Setting<Integer> reportSpacing = sgGeneral.add(new IntSetting.Builder()
        .name("report-spacing")
        .description("A new stash must be this far from an already reported one.")
        .defaultValue(256)
        .min(16)
        .sliderRange(64, 1024)
        .build()
    );

    private final Setting<Boolean> chatNotify = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notify")
        .description("Prints found stashes to chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoFly = sgSearch.add(new BoolSetting.Builder()
        .name("auto-fly")
        .description("Flies a spiral search pattern with Elytra Route.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> startAtPlayer = sgSearch.add(new BoolSetting.Builder()
        .name("start-at-player")
        .description("Centres a new search on your position.")
        .defaultValue(true)
        .visible(autoFly::get)
        .build()
    );

    private final Setting<Integer> centerX = sgSearch.add(new IntSetting.Builder()
        .name("center-x")
        .description("Search centre X.")
        .defaultValue(0)
        .noSlider()
        .visible(autoFly::get)
        .build()
    );

    private final Setting<Integer> centerZ = sgSearch.add(new IntSetting.Builder()
        .name("center-z")
        .description("Search centre Z.")
        .defaultValue(0)
        .noSlider()
        .visible(autoFly::get)
        .build()
    );

    private final Setting<Integer> laneSpacing = sgSearch.add(new IntSetting.Builder()
        .name("lane-spacing")
        .description("Blocks between spiral lanes. Keep it under twice your render distance.")
        .defaultValue(160)
        .min(32)
        .sliderRange(64, 512)
        .visible(autoFly::get)
        .build()
    );

    private final Setting<Integer> maxRadius = sgSearch.add(new IntSetting.Builder()
        .name("max-radius")
        .description("Stops when the spiral gets this far from the centre.")
        .defaultValue(30000)
        .min(100)
        .sliderRange(1000, 100000)
        .visible(autoFly::get)
        .build()
    );

    private final Setting<Boolean> restartRoute = sgSearch.add(new BoolSetting.Builder()
        .name("restart-route")
        .description("Restarts Elytra Route when it stops because of an error.")
        .defaultValue(true)
        .visible(autoFly::get)
        .build()
    );

    private final Setting<Integer> progress = sgSearch.add(new IntSetting.Builder()
        .name("progress")
        .description("Next spiral corner. Set to 0 to start a new search.")
        .defaultValue(0)
        .min(0)
        .noSlider()
        .visible(autoFly::get)
        .build()
    );

    private final Setting<String> webhookUrl = sgDiscord.add(new StringSetting.Builder()
        .name("webhook-link")
        .description("Discord webhook that gets the stash coordinates.")
        .defaultValue("")
        .build()
    );

    private final StashTracker tracker = new StashTracker();
    private String dimension = "";
    private boolean routeStarted;
    // True only when this module switched Elytra Route on itself.
    private boolean ownsRoute;
    private boolean startPending;
    private int pruneTicks;
    private int restartTicks;
    private int restartsInARow;
    private static final int RESTART_DELAY_TICKS = 200;
    private static final int MAX_RESTARTS = 3;

    public StashFinderTHM() {
        super(THMAddon.MAIN, "stash-finder-thm", "Flies a search spiral and reports chest stashes in chat or to Discord.");
    }

    @Override
    public void onActivate() {
        tracker.clearChunks();
        dimension = "";
        routeStarted = false;
        ownsRoute = false;
        restartTicks = 0;
        restartsInARow = 0;
        // Meteor re-enables saved modules before a world exists; start once the player is on the main server, not in the lobby.
        startPending = mc.level == null || mc.player == null || !onMainServer();
        if (!startPending) beginSearch();
    }

    private void beginSearch() {
        syncDimension();
        scanLoadedChunks();

        if (!autoFly.get()) return;
        if (progress.get() == 0) {
            if (startAtPlayer.get()) {
                centerX.set(mc.player.getBlockX());
                centerZ.set(mc.player.getBlockZ());
            }
            progress.set(1);
        }
        startRoute();
    }

    @Override
    public void onDeactivate() {
        // Stop a route this module started or still steers along the spiral; a route to another destination keeps flying.
        ElytraRoute route = ElytraRoute.INSTANCE;
        if (route != null && route.isActive() && (ownsRoute || (autoFly.get() && aimedAtWaypoint(route)))) route.toggle();
        ownsRoute = false;
        routeStarted = false;
        startPending = false;
        restartTicks = 0;
    }

    @Override
    public String getInfoString() {
        return autoFly.get() ? "#" + progress.get() : null;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        tracker.clearChunks();
        dimension = "";
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (mc.level == null) return;
        syncDimension();
        scanChunk(event.chunk());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;
        if (startPending) {
            if (!onMainServer()) return;
            startPending = false;
            beginSearch();
            return;
        }
        syncDimension();
        if (++pruneTicks >= 100) {
            pruneTicks = 0;
            tracker.pruneChunks(mc.player.chunkPosition().x(), mc.player.chunkPosition().z(),
                mc.options.renderDistance().get() + clusterRadius.get() + 2);
        }
        if (restartTicks > 0 && --restartTicks == 0) {
            if (onMainServer()) {
                info("Restarting Elytra Route (%d/%d).", restartsInARow, MAX_RESTARTS);
                startRoute();
            } else {
                restartTicks = RESTART_DELAY_TICKS;
            }
            return;
        }
        if (!autoFly.get() || !routeStarted) return;

        ElytraRoute route = ElytraRoute.INSTANCE;
        if (route == null || !route.isActive()) {
            routeStarted = false;
            // A stopped route is no longer ours; a restart claims it again, a manual start never does.
            boolean owned = ownsRoute;
            ownsRoute = false;
            if (owned && restartRoute.get() && route != null && route.stoppedByError() && restartsInARow < MAX_RESTARTS) {
                restartsInARow++;
                restartTicks = RESTART_DELAY_TICKS;
                warning("Elytra Route stopped. Restarting it in %d seconds.", RESTART_DELAY_TICKS / 20);
            } else {
                warning("Elytra Route stopped. Still scanning; re-enable Stash Finder to keep flying.");
            }
            return;
        }

        if (route.getRemainingDistance() <= route.getArrivalRadius() + WAYPOINT_LEAD) {
            restartsInARow = 0;
            progress.set(progress.get() + 1);
            if (!aimAtWaypoint(route)) {
                info("Search area finished.");
                toggle();
            }
        }
    }

    private void startRoute() {
        ElytraRoute route = ElytraRoute.INSTANCE;
        if (route == null) {
            error("Elytra Route is not available.");
            return;
        }
        if (!aimAtWaypoint(route)) {
            info("Search area finished. Set progress to 0 to start again.");
            return;
        }
        if (!route.isActive()) {
            route.toggle();
            ownsRoute = route.isActive();
        }
        routeStarted = route.isActive();
    }

    private boolean onMainServer() {
        return ServerStatusHandler.getInstance().getCommittedState() == ServerState.MAIN_SERVER;
    }

    private boolean aimedAtWaypoint(ElytraRoute route) {
        int[] wp = StashTracker.spiralWaypoint(centerX.get(), centerZ.get(), laneSpacing.get(), progress.get());
        return route.getDestinationX() == wp[0] && route.getDestinationZ() == wp[1];
    }

    /** Points the route at the current spiral corner; false once the spiral leaves the search area. */
    private boolean aimAtWaypoint(ElytraRoute route) {
        int cx = centerX.get(), cz = centerZ.get();
        int[] wp = StashTracker.spiralWaypoint(cx, cz, laneSpacing.get(), progress.get());
        if (Math.max(Math.abs(wp[0] - cx), Math.abs(wp[1] - cz)) > maxRadius.get()) return false;
        route.setDestination(wp[0], wp[1]);
        return true;
    }

    private void syncDimension() {
        String current = mc.level.dimension().identifier().toString();
        if (current.equals(dimension)) return;
        dimension = current;
        tracker.clearChunks();
        tracker.clearReported();
        loadReported();
    }

    private void scanLoadedChunks() {
        int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
        int r = mc.options.renderDistance().get() + 1;
        for (int x = pcx - r; x <= pcx + r; x++) {
            for (int z = pcz - r; z <= pcz + r; z++) {
                if (mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) instanceof LevelChunk chunk) {
                    scanChunk(chunk);
                }
            }
        }
    }

    private void scanChunk(LevelChunk chunk) {
        int cx = chunk.getPos().x(), cz = chunk.getPos().z();
        int ignore = ignoreSpawnRadius.get();
        if (Math.abs(cx * 16 + 8) < ignore && Math.abs(cz * 16 + 8) < ignore) return;

        int halves = 0, singles = 0, shulkers = 0, barrels = 0, other = 0;
        long sx = 0, sy = 0, sz = 0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity) {
                BlockState state = be.getBlockState();
                boolean single = !state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE;
                if (single) singles++;
                else halves++;
            } else if (be instanceof ShulkerBoxBlockEntity) {
                shulkers++;
            } else if (be instanceof BarrelBlockEntity) {
                barrels++;
            } else if (be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity) {
                other++;
            } else {
                continue;
            }
            BlockPos pos = be.getBlockPos();
            sx += pos.getX();
            sy += pos.getY();
            sz += pos.getZ();
        }

        int blocks = halves + singles + shulkers + barrels + other;
        StashTracker.Counts counts = new StashTracker.Counts(halves, singles, shulkers, barrels, other, sx, sy, sz, blocks);
        StashTracker.Stash stash = tracker.update(cx, cz, counts, clusterRadius.get(), minDoubleChests.get(), reportSpacing.get());
        if (stash != null) report(stash);
    }

    private void report(StashTracker.Stash stash) {
        String dim = dimensionName();
        if (chatNotify.get()) {
            info("Stash at %d %d %d (%s): %d double chests, %d single, %d shulkers.",
                stash.x(), stash.y(), stash.z(), dim, stash.doubleChests(), stash.singleChests(), stash.shulkers());
        }
        saveReported(stash);
        sendWebhook(stash, dim);
    }

    private void sendWebhook(StashTracker.Stash stash, String dim) {
        String url = webhookUrl.get();
        if (url.isBlank()) return;

        ServerData server = mc.getCurrentServer();
        String serverName = server == null ? "singleplayer" : server.ip;

        // embeds, not content, so the message can't ping anyone
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Stash found");
        embed.addProperty("color", 0x913CFF);
        JsonArray fields = new JsonArray();
        fields.add(field("Coordinates", stash.x() + " " + stash.y() + " " + stash.z()));
        fields.add(field("Dimension", dim));
        fields.add(field("Double chests", String.valueOf(stash.doubleChests())));
        fields.add(field("Single chests", String.valueOf(stash.singleChests())));
        fields.add(field("Shulkers", String.valueOf(stash.shulkers())));
        fields.add(field("Barrels", String.valueOf(stash.barrels())));
        fields.add(field("Server", serverName));
        embed.add("fields", fields);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);
        String json = payload.toString();

        THMUtils.async("stash-finder-webhook", () -> {
            if (!TrustedHttp.postJson(url, json, TrustedHttp.Kind.USER_WEBHOOK, null)) {
                THMAddon.LOG.warn("Stash Finder: failed to send to webhook");
            }
        });
    }

    private static JsonObject field(String name, String value) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", true);
        return f;
    }

    private String dimensionName() {
        if (dimension.endsWith("the_nether")) return "Nether";
        if (dimension.endsWith("the_end")) return "End";
        if (dimension.endsWith("overworld")) return "Overworld";
        return dimension;
    }

    private File stashFile() {
        return THMAddon.GetConfigFile("stashes", "stashes.csv");
    }

    // One line per stash: dimension,x,y,z,doubles,singles,shulkers,barrels,time
    private void loadReported() {
        File file = stashFile();
        if (!file.isFile()) return;
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                String[] parts = line.split(",");
                if (parts.length < 4 || !parts[0].equals(dimension)) continue;
                try {
                    tracker.addReported(Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[3].trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            THMAddon.LOG.warn("Stash Finder: could not read {}", file, e);
        }
    }

    private void saveReported(StashTracker.Stash stash) {
        File file = stashFile();
        String line = String.join(",", dimension, String.valueOf(stash.x()), String.valueOf(stash.y()), String.valueOf(stash.z()),
            String.valueOf(stash.doubleChests()), String.valueOf(stash.singleChests()), String.valueOf(stash.shulkers()),
            String.valueOf(stash.barrels()), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)) + System.lineSeparator();
        try {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            THMAddon.LOG.warn("Stash Finder: could not write {}", file, e);
        }
    }
}
