/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.MeteorClient;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.*;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class APIUtils {
    private static final String API_ORIGIN = "https://api.highwaymen.cc";
    private static final String MEMBERS_URL = API_ORIGIN + "/users";
    private static final String HIGHWAY_REPORT_URL = API_ORIGIN + "/";
    private static final String HIGHWAY_STATUS_URL = API_ORIGIN + "/status";
    private static final String HIGHWAY_LENGTHS_URL = API_ORIGIN + "/highway-lengths";
    private static final String CAPE_LIST_URL = API_ORIGIN + "/cape/";
    private static final String CAPE_INDEX_URL = "https://highwaymen.cc/cape/index";
    private static final String KITS_URL = API_ORIGIN + "/kits";
    static final String UPDATE_FEED_URL = API_ORIGIN + "/updates/feed.json";
    static final String UPDATE_PUBLIC_KEY = "MCowBQYDK2VwAyEABOC016sw9YhavsewbR+jG1sQglaxVKGv9ikvq1kSYYA=";

    private static final Gson GSON = new Gson();
    private static final int MAX_MEMBERS = 4_000;
    private static final int MAX_USERNAMES_PER_MEMBER = 32;
    private static final int MAX_CAPES = 64;
    private static final int MAX_HIGHWAY_ROWS = 8_000;
    private static final int MAX_HIGHWAY_WORKERS = 8;

    private APIUtils() {}

    private static volatile String cachedPassword;

    /**
     * Key for the local on-disk stats-cache encryption (see HighwayBuilderTHM's stats artifacts) -
     * not an API secret. Persisted to disk so it survives client restarts (the cache written before
     * a restart must still decrypt after it); generated once on first use.
     */
    public static synchronized String getPassword() {
        if (cachedPassword != null) return cachedPassword;

        File file = new File(new File(MeteorClient.FOLDER, "thm"), "stats-key");
        try {
            if (file.isFile()) {
                String existing = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty()) {
                    cachedPassword = existing;
                    return cachedPassword;
                }
            }

            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            StringBuilder hex = new StringBuilder();
            for (byte b : random) hex.append(String.format("%02x", b));

            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), hex.toString(), StandardCharsets.UTF_8);
            cachedPassword = hex.toString();
        } catch (IOException e) {
            THMAddon.LOG.warn("Failed to load/generate stats cache key: {}", e.getMessage());
            cachedPassword = "thm-fallback-stats-key";
        }
        return cachedPassword;
    }

    static String apiToken() {
        try {
            THMSystem system = THMSystem.get();
            return system == null ? "" : system.getApiToken();
        } catch (Throwable t) {
            return "";
        }
    }

    private static volatile String lastSkipReason;

    /** API traffic requires a world and a valid token. Logs the reason when it changes. */
    public static boolean canRequest() {
        String reason = skipReason();
        if (reason == null) {
            lastSkipReason = null;
            return true;
        }
        if (!reason.equals(lastSkipReason)) {
            lastSkipReason = reason;
            THMAddon.LOG.warn("API request skipped: {}", reason);
        }
        return false;
    }

    private static String skipReason() {
        try {
            THMSystem system = THMSystem.get();
            if (system == null) return "settings not loaded";
            if (!system.hasApiToken()) return "no valid API token set (must be a UUID)";
            if (mc.level == null) return "not in a world";
            return null;
        } catch (Throwable t) {
            return "check failed: " + t.getClass().getSimpleName();
        }
    }

    private static String apiGet(String url) {
        return TrustedHttp.getString(url, TrustedHttp.Kind.PUBLIC_API, TrustedHttp.MAX_JSON_BYTES);
    }

    public static TrustedHttp.Response fetchKitCatalog(String token, String etag) {
        if (!token.isEmpty()) {
            var member = TrustedHttp.postMemberRead(KITS_URL + "/member", token, TrustedHttp.MAX_JSON_BYTES);
            if (member != null && member.status() == 200) return member;
            if (member != null && member.status() != 401 && member.status() != 403 && member.status() != 404) return member;
        }
        return TrustedHttp.getPublicConditional(KITS_URL, etag, TrustedHttp.MAX_JSON_BYTES);
    }

    public record HighwayLength(int paved, int total) {}

    public static Map<String, Map<String, HighwayLength>> fetchHighwayLengths() {
        String response = apiGet(HIGHWAY_LENGTHS_URL);
        if (response == null) return null;
        try {
            return parseHighwayLengths(GSON.fromJson(response, JsonObject.class));
        } catch (RuntimeException e) {
            THMAddon.LOG.warn("Invalid highway lengths response");
            return null;
        }
    }

    static Map<String, Map<String, HighwayLength>> parseHighwayLengths(JsonObject root) {
        Map<String, Map<String, HighwayLength>> dimensions = new HashMap<>();
        for (String dimension : List.of("nether", "overworld", "end")) {
            Map<String, HighwayLength> roads = new HashMap<>();
            for (var road : root.getAsJsonObject(dimension).entrySet()) {
                if (!road.getValue().isJsonObject()) continue;
                JsonObject length = road.getValue().getAsJsonObject();
                int paved = highwayBlocks(length.get("paved"));
                int total = highwayBlocks(length.get("total"));
                if (paved >= 0 && total > 0 && paved <= total) roads.put(road.getKey(), new HighwayLength(paved, total));
            }
            dimensions.put(dimension, Map.copyOf(roads));
        }
        return Map.copyOf(dimensions);
    }

    private static int highwayBlocks(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return -1;
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return -1;
        }
    }

    private static void apiPost(String name, String url, String json) {
        if (canRequest()) THMUtils.async(name, () -> TrustedHttp.postJson(url, json, TrustedHttp.Kind.API, apiToken()));
    }

    private static String jsonMessage(String field, String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty(field, message);
        return payload.toString();
    }

    public static void sendStatus(String message) {
        apiPost("status", HIGHWAY_REPORT_URL, jsonMessage("report", message + ":v" + THMAddon.VERSION));
    }

    public static void sendStatistics(String message) {
        apiPost("statistics", HIGHWAY_REPORT_URL, jsonMessage("report", message + ":v" + THMAddon.VERSION));
    }

    // Discord webhook URL, supplied by the player at runtime - never attach our API token to it.
    public static void sendToWebhook(String url, String message) {
        THMUtils.async("webhook", () ->
            TrustedHttp.postJson(url, jsonMessage("content", message), TrustedHttp.Kind.USER_WEBHOOK, null));
    }

    private static String stringField(JsonObject o, String field) {
        return o.has(field) && o.get(field).isJsonPrimitive() ? o.get(field).getAsString() : "";
    }

    static List<ThmMembers.Member> fetchMembersFromApi(String token) {
        if (!token.isEmpty()) {
            List<ThmMembers.Member> members = parseMembers(TrustedHttp.getString(MEMBERS_URL, TrustedHttp.Kind.API, TrustedHttp.MAX_JSON_BYTES));
            if (members != null) return members;
        }
        return parseMembers(apiGet(MEMBERS_URL));
    }

    static List<ThmMembers.Member> parseMembers(String response) {
        if (response == null) return null;
        try {
            JsonArray jsonArray = GSON.fromJson(response, JsonArray.class);
            if (jsonArray == null) {
                THMAddon.LOG.warn("Member list response was empty");
                return null;
            }
            if (jsonArray.size() > MAX_MEMBERS) {
                THMAddon.LOG.warn("Member list exceeded {} entries; ignoring remote payload", MAX_MEMBERS);
                return null;
            }

            List<ThmMembers.Member> members = new ArrayList<>();
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement element = jsonArray.get(i);
                if (element == null || !element.isJsonObject()) continue;
                JsonObject jsonObject = element.getAsJsonObject();
                if (!(jsonObject.get("usernames") instanceof JsonArray usernamesArray)) continue;

                int count = Math.min(usernamesArray.size(), MAX_USERNAMES_PER_MEMBER);
                List<String> valid = new ArrayList<>(count);
                for (int j = 0; j < count; j++) {
                    JsonElement nameEl = usernamesArray.get(j);
                    if (nameEl == null || !nameEl.isJsonPrimitive()) continue;
                    String name = nameEl.getAsString();
                    if (isMinecraftUsername(name)) valid.add(name);
                }
                if (valid.isEmpty()) continue;

                String rank = stringField(jsonObject, "rank");
                String rankId = stringField(jsonObject, "rankId");
                String branch = stringField(jsonObject, "branch");
                String discordName = stringField(jsonObject, "discordname");
                String displayName = valid.get(0);
                if (discordName.isEmpty()) discordName = displayName;

                members.add(new ThmMembers.Member(displayName, valid.toArray(new String[0]), rank, rankId, branch, discordName));
            }
            return members;
        } catch (RuntimeException e) {
            THMAddon.LOG.warn("Invalid member list response");
            return null;
        }
    }

    private static boolean isMinecraftUsername(String name) {
        if (name == null || name.isEmpty() || name.length() > 16) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    public static Map<String, String> fetchHighwayStatusFromApi() {
        try {
            String url = HIGHWAY_STATUS_URL;
            String body = apiGet(url + (url.contains("?") ? "&" : "?") + "routes=1");
            if (body == null) return null;

            JsonObject root = GSON.fromJson(body, JsonObject.class);
            if (root == null) return null;
            if (root.size() > MAX_HIGHWAY_ROWS) {
                THMAddon.LOG.warn("Highway status payload exceeded {} entries; ignoring remote payload", MAX_HIGHWAY_ROWS);
                return null;
            }
            return parseHighwayStatus(root);
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching highway status from API: {}", e.getMessage());
            return null;
        }
    }

    static Map<String, String> parseHighwayStatus(JsonObject root) {
        Map<String, String> highwayByName = new HashMap<>();
        Map<String, Long> newestTimestampByName = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject value = entry.getValue().getAsJsonObject();
            JsonElement workers = value.get("workers");
            // workers is authoritative; the top-level username mirrors only the newest worker.
            List<JsonElement> rows = workers != null && workers.isJsonArray()
                ? workers.getAsJsonArray().asList() : List.of(value);
            for (int i = 0; i < Math.min(rows.size(), MAX_HIGHWAY_WORKERS); i++) {
                if (!rows.get(i).isJsonObject()) continue;
                JsonObject worker = rows.get(i).getAsJsonObject();
                try {
                    String name = stringField(worker, "username").trim().toLowerCase(Locale.ROOT);
                    if (!isMinecraftUsername(name) || "unknown".equals(name)) continue;
                    long timestamp = worker.has("timestamp") ? worker.get("timestamp").getAsLong() : Long.MIN_VALUE;
                    Long existing = newestTimestampByName.get(name);
                    if (existing == null || timestamp >= existing) {
                        newestTimestampByName.put(name, timestamp);
                        String route = stringField(worker, "routeLabel");
                        String direction = stringField(value, "direction");
                        String mode = "dug".equals(stringField(value, "mode")) ? "Dug " : "";
                        highwayByName.put(name, route.isBlank() || route.length() > 80 || direction.length() > 2
                            ? entry.getKey() : route + " · " + mode + direction);
                    }
                } catch (RuntimeException ignored) {
                    // A malformed worker must not hide the rest of the board.
                }
            }
        }
        return highwayByName;
    }

    public static Map<String, String> fetchCapeListFromApi() {
        try {
            String body = apiGet(CAPE_LIST_URL);
            if (body == null) return null;

            JsonObject root = GSON.fromJson(body, JsonObject.class);
            if (root == null || !root.has("players")) {
                THMAddon.LOG.warn("Cape list response missing 'players'");
                return null;
            }

            JsonObject players = root.getAsJsonObject("players");
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
                if (result.size() >= MAX_MEMBERS) break;
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject p = entry.getValue().getAsJsonObject();
                if (p.has("cape")) result.put(key, p.get("cape").getAsString());
            }
            THMAddon.LOG.info("Fetched Cape List");
            return result;
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching cape list: {}", e.getMessage());
            return null;
        }
    }

    public static void postCapeSelection(String cape) {
        if (mc.player == null) return;
        String username = mc.player.getGameProfile().name();
        String token = apiToken();
        if (token.isEmpty()) return;

        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("cape", cape);
        payload.addProperty("timestamp", System.currentTimeMillis());
        payload.addProperty("token", token);
        apiPost("cape-post", CAPE_LIST_URL, payload.toString());
    }

    public static List<CapeManager.CapeEntry> fetchCapeIndexFromApi() {
        try {
            String body = apiGet(CAPE_INDEX_URL);
            if (body == null) return null;

            JsonObject root = GSON.fromJson(body, JsonObject.class);
            if (root == null || !root.has("capes")) {
                THMAddon.LOG.warn("Cape index response missing 'capes'");
                return null;
            }

            JsonArray arr = root.getAsJsonArray("capes");
            List<CapeManager.CapeEntry> result = new ArrayList<>();
            for (int i = 0; i < arr.size() && result.size() < MAX_CAPES; i++) {
                JsonElement element = arr.get(i);
                if (element == null || !element.isJsonObject()) continue;
                JsonObject o = element.getAsJsonObject();
                if (!o.has("id") || !o.has("url")) continue;
                result.add(new CapeManager.CapeEntry(o.get("id").getAsString(), o.get("url").getAsString()));
            }
            return result;
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching cape index from API: {}", e.getMessage());
            return null;
        }
    }
}
