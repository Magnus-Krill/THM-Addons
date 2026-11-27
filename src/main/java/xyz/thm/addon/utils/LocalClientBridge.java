/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.HighwayBuilderTHM;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class LocalClientBridge {
    private static final Gson JSON = new Gson();
    private static final int MAX_REQUEST_BYTES = 2048;
    private static final Pattern SECRET = Pattern.compile("(?i)token|password|passwd|webhook|secret|api-?key|auth");

    private LocalClientBridge() {}

    public static void start(HighwayBuilderTHM builder) {
        if (!Boolean.getBoolean("thm.localClientBridge")) return;
        Thread.ofVirtual().name("THM Local Client Bridge").start(() -> listen(builder));
    }

    private static void listen(HighwayBuilderTHM builder) {
        Path directory = null;
        try (var server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            String runtime = System.getenv("XDG_RUNTIME_DIR");
            Path base = Path.of(runtime == null ? System.getProperty("java.io.tmpdir") : runtime);
            directory = Files.createTempDirectory(base, "thm-client-" + ProcessHandle.current().pid() + "-", PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwx------")));
            Path socket = directory.resolve("control.sock");
            server.bind(UnixDomainSocketAddress.of(socket));
            THMAddon.LOG.info("Local client bridge listening at {}", socket);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> cleanUp(socket), "THM Bridge Cleanup"));
            while (server.isOpen()) {
                try (SocketChannel connection = server.accept()) {
                    handle(connection, builder);
                } catch (IOException failure) {
                    if (!server.isOpen()) break;
                }
            }
        } catch (IOException | UnsupportedOperationException failure) {
            THMAddon.LOG.warn("Could not start the local client bridge", failure);
        } finally {
            if (directory != null) cleanUp(directory.resolve("control.sock"));
        }
    }

    private static void cleanUp(Path socket) {
        try {
            Files.deleteIfExists(socket);
            Files.deleteIfExists(socket.getParent());
        } catch (IOException ignored) {}
    }

    private static void handle(SocketChannel connection, HighwayBuilderTHM builder) throws IOException {
        Thread timeout = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(5_000);
                connection.close();
            } catch (InterruptedException | IOException ignored) {}
        });
        try {
            Object response;
            try {
                JsonObject request = readRequest(connection);
                var action = new FutureTask<>(() -> execute(request, builder));
                mc.execute(action);
                try {
                    response = Map.of("result", action.get(2, TimeUnit.SECONDS));
                } finally {
                    action.cancel(false);
                }
            } catch (Exception failure) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                String message = cause instanceof IllegalArgumentException ? cause.getMessage() : "Client request failed or timed out.";
                response = Map.of("error", message == null ? "Invalid request." : message);
            }
            var bytes = StandardCharsets.UTF_8.encode(JSON.toJson(response) + "\n");
            while (bytes.hasRemaining()) connection.write(bytes);
        } finally {
            timeout.interrupt();
        }
    }

    static JsonObject readRequest(ReadableByteChannel connection) throws IOException {
        ByteBuffer input = ByteBuffer.allocate(MAX_REQUEST_BYTES);
        int scanned = 0;
        while (input.hasRemaining() && connection.read(input) != -1) {
            while (scanned < input.position()) {
                if (input.get(scanned++) != '\n') continue;
                input.flip().limit(scanned);
                try {
                    return JSON.fromJson(StandardCharsets.UTF_8.decode(input).toString(), JsonObject.class);
                } catch (com.google.gson.JsonParseException invalid) {
                    throw new IllegalArgumentException("Expected a JSON object.");
                }
            }
        }
        throw new IllegalArgumentException("Expected one JSON line of at most 2048 bytes.");
    }

    private static Object execute(JsonObject request, HighwayBuilderTHM builder) {
        return switch (requiredString(request, "method")) {
            case "status" -> status(builder);
            case "settings" -> settings(request, builder);
            case "modules" -> Modules.get().getAll().stream().map(module ->
                Map.of("name", module.name, "active", module.isActive())).toList();
            case "inventory" -> inventory();
            case "set" -> {
                var setting = setting(request, builder);
                if (!setting.parse(requiredString(request, "value"))) throw new IllegalArgumentException("Invalid value for " + setting.name + ".");
                yield Map.of(setting.name, readable(setting.get()));
            }
            case "toggle" -> {
                Module module = module(request, builder);
                module.toggle();
                yield Map.of("name", module.name, "active", module.isActive());
            }
            case "start" -> {
                if (mc.player == null || mc.level == null) throw new IllegalArgumentException("Join a world before starting HighwayBuilder.");
                if (!builder.isReturningFromPause()) builder.enable();
                yield builder.diagnosticStatus();
            }
            case "stop" -> {
                if (builder.isManuallyPaused()) builder.stopPausedBuilder();
                else builder.disable();
                yield builder.diagnosticStatus();
            }
            case "chat" -> {
                if (mc.level == null) throw new IllegalArgumentException("Join a world before showing a chat message.");
                String message = requiredString(request, "message");
                if (message.length() > 300 || message.chars().anyMatch(Character::isISOControl)) {
                    throw new IllegalArgumentException("Use a single chat line of at most 300 characters.");
                }
                ChatUtils.infoPrefix("THM Diagnose", "%s", message);
                yield Map.of("shown", true);
            }
            default -> throw new IllegalArgumentException("Unknown method.");
        };
    }

    static String requiredString(JsonObject request, String name) {
        var value = request == null ? null : request.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
            || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("Expected a non-empty string for " + name + ".");
        }
        return value.getAsString();
    }

    private static Map<String, Object> status(HighwayBuilderTHM builder) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("addonVersion", THMAddon.VERSION);
        status.put("fps", mc.getFps());
        status.put("inWorld", mc.player != null && mc.level != null);
        if (mc.player != null && mc.level != null) {
            status.put("position", new double[] {mc.player.getX(), mc.player.getY(), mc.player.getZ()});
            status.put("dimension", mc.level.dimension().identifier().toString());
        }
        status.put("highwayBuilder", builder.diagnosticStatus());
        return status;
    }

    private static Module module(JsonObject request, HighwayBuilderTHM builder) {
        Module module = request != null && request.has("module") ? Modules.get().get(requiredString(request, "module")) : builder;
        if (module == null) throw new IllegalArgumentException("Unknown module.");
        return module;
    }

    private static Setting<?> setting(JsonObject request, HighwayBuilderTHM builder) {
        Setting<?> setting = module(request, builder).settings.get(requiredString(request, "setting"));
        if (setting == null || SECRET.matcher(setting.name).find()) throw new IllegalArgumentException("Unknown setting.");
        return setting;
    }

    private static Object readable(Object value) {
        return value instanceof Boolean || value instanceof Number ? value : String.valueOf(value);
    }

    private static Map<String, Object> settings(JsonObject request, HighwayBuilderTHM builder) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (var group : module(request, builder).settings) {
            for (var setting : group) {
                if (!SECRET.matcher(setting.name).find()) values.put(setting.name, readable(setting.get()));
            }
        }
        return values;
    }

    private static Object inventory() {
        var items = new ArrayList<Map<String, Object>>();
        if (mc.player == null) return items;
        var inventory = mc.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (!stack.isEmpty()) items.add(Map.of("slot", slot, "item",
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "count", stack.getCount()));
        }
        return items;
    }
}
