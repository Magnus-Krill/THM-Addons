/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.kitbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.MeteorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.thm.addon.settings.DescribedOption;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.APIUtils;
import xyz.thm.addon.utils.THMUtils;
import xyz.thm.addon.utils.TrustedHttp;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class KitbotKitCatalog {
    public static final Supplier<String[]> NAMES = KitbotKitCatalog::names;
    private static final Logger LOG = LoggerFactory.getLogger(KitbotKitCatalog.class);
    static final long REFRESH_MS = 6 * 60 * 60 * 1000L;
    static final long RETRY_MS = 5 * 60 * 1000L;
    private static final Pattern KIT_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,64}");
    private final Path directory;
    private final Supplier<String> tokenSupplier;
    private final BiFunction<String, String, TrustedHttp.Response> fetcher;
    private final Consumer<Runnable> executor;
    private final LongSupplier clock;
    private String token = "";
    private List<Kit> kits = List.of();
    private List<Kit> namedKits;
    private String[] names = new String[0];
    private String etag;
    private long nextRefresh;
    private boolean hasCatalog;
    private boolean fetching;

    public record Kit(String name, double price) implements DescribedOption {
        @Override
        public String description() {
            return "Order this kit for " + BigDecimal.valueOf(price).stripTrailingZeros() + " tokens each.";
        }
    }

    private static final class Holder {
        private static final KitbotKitCatalog INSTANCE = new KitbotKitCatalog(
            MeteorClient.FOLDER.toPath().resolve("thm/kitbot-kits"),
            () -> THMSystem.get() == null ? "" : THMSystem.get().getApiToken(),
            APIUtils::fetchKitCatalog, job -> THMUtils.async("kit-catalog", job), System::currentTimeMillis
        );
    }

    KitbotKitCatalog(Path directory, Supplier<String> tokenSupplier,
                     BiFunction<String, String, TrustedHttp.Response> fetcher,
                     Consumer<Runnable> executor, LongSupplier clock) {
        this.directory = directory;
        this.tokenSupplier = tokenSupplier;
        this.fetcher = fetcher;
        this.executor = executor;
        this.clock = clock;
    }

    public static List<Kit> getKits() {
        return Holder.INSTANCE.current();
    }

    public static String[] names() {
        return Holder.INSTANCE.currentNames();
    }

    synchronized String[] currentNames() {
        List<Kit> current = current();
        if (current != namedKits) {
            names = current.stream().map(Kit::name).toArray(String[]::new);
            namedKits = current;
        }
        return names;
    }

    public static Kit find(String name) {
        return find(getKits(), name);
    }

    static Kit find(List<Kit> kits, String name) {
        if (name == null) return null;
        return kits.stream().filter(kit -> kit.name().equalsIgnoreCase(name.trim())).findFirst().orElse(null);
    }

    synchronized List<Kit> current() {
        String configured = tokenSupplier.get();
        if (!TrustedHttp.isValidApiToken(configured)) configured = "";
        long now = clock.getAsLong();
        if (!token.equals(configured)) {
            token = configured;
            kits = List.of();
            etag = null;
            hasCatalog = false;
            nextRefresh = 0;
            if (!token.isEmpty()) load(now);
        }
        if (!token.isEmpty() && !fetching && now >= nextRefresh) {
            fetching = true;
            String requestedToken = token;
            String requestedEtag = etag;
            executor.accept(() -> refresh(requestedToken, requestedEtag));
        }
        return kits;
    }

    private void refresh(String requestedToken, String requestedEtag) {
        try {
            if (!requestedToken.equals(tokenSupplier.get())) return;
            TrustedHttp.Response response = fetcher.apply(requestedToken, requestedEtag);
            synchronized (this) {
                if (!requestedToken.equals(token) || !requestedToken.equals(tokenSupplier.get())) return;
                long now = clock.getAsLong();
                nextRefresh = now + RETRY_MS;
                if (response == null) return;
                if (response.status() == 401 || response.status() == 403) {
                    kits = List.of();
                    etag = null;
                    hasCatalog = false;
                    Files.deleteIfExists(cacheFile());
                    return;
                }
                if (response.status() == 200) {
                    List<Kit> parsed = parse(new String(response.body(), StandardCharsets.UTF_8));
                    kits = parsed;
                    etag = TrustedHttp.isSafeEtag(response.etag()) ? response.etag() : null;
                    hasCatalog = true;
                } else if (response.status() != 304 || !hasCatalog || requestedEtag == null) {
                    return;
                } else if (response.etag() != null && TrustedHttp.isSafeEtag(response.etag())) {
                    etag = response.etag();
                }
                nextRefresh = now + REFRESH_MS;
                save(now);
            }
        } catch (Exception e) {
            synchronized (this) {
                if (requestedToken.equals(token)) nextRefresh = clock.getAsLong() + RETRY_MS;
            }
            LOG.warn("Kit catalog refresh failed ({})", e.getClass().getSimpleName());
        } finally {
            synchronized (this) {
                fetching = false;
            }
        }
    }

    static List<Kit> parse(String json) {
        if (json.length() > TrustedHttp.MAX_JSON_BYTES) throw new IllegalArgumentException("Oversized kit catalog");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("success") || !root.get("success").isJsonPrimitive()
            || !root.getAsJsonPrimitive("success").isBoolean() || !root.get("success").getAsBoolean()) {
            throw new IllegalArgumentException("Unsuccessful kit catalog");
        }
        JsonArray entries = root.getAsJsonArray("kits");
        if (entries == null || entries.size() > 256) throw new IllegalArgumentException("Invalid kit catalog size");
        List<Kit> result = new ArrayList<>(entries.size());
        HashSet<String> names = new HashSet<>();
        for (JsonElement entry : entries) {
            JsonObject kit = entry.getAsJsonObject();
            if (!kit.has("name") || !kit.get("name").isJsonPrimitive() || !kit.getAsJsonPrimitive("name").isString()
                || !kit.has("price") || !kit.get("price").isJsonPrimitive() || !kit.getAsJsonPrimitive("price").isNumber()) {
                throw new IllegalArgumentException("Invalid kit entry");
            }
            String name = kit.get("name").getAsString();
            double price = kit.get("price").getAsDouble();
            if (!KIT_NAME.matcher(name).matches() || !names.add(name.toLowerCase(Locale.ROOT))
                || !Double.isFinite(price) || price < 0) throw new IllegalArgumentException("Invalid kit entry");
            result.add(new Kit(name, price));
        }
        return List.copyOf(result);
    }

    private Path cacheFile() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        return directory.resolve(HexFormat.of().formatHex(digest) + ".json");
    }

    private void load(long now) {
        try {
            Path file = cacheFile();
            if (!Files.isRegularFile(file) || Files.size(file) > TrustedHttp.MAX_JSON_BYTES) return;
            String json = Files.readString(file);
            List<Kit> parsed = parse(json);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            long checkedAt = root.get("checkedAt").getAsLong();
            if (checkedAt < 0 || checkedAt > now) return;
            String cachedEtag = root.has("etag") ? root.get("etag").getAsString() : null;
            if (!TrustedHttp.isSafeEtag(cachedEtag)) return;
            kits = parsed;
            etag = cachedEtag;
            hasCatalog = true;
            nextRefresh = checkedAt + REFRESH_MS;
        } catch (Exception e) {
            LOG.warn("Kit catalog cache ignored ({})", e.getClass().getSimpleName());
        }
    }

    private void save(long checkedAt) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("success", true);
        root.addProperty("checkedAt", checkedAt);
        if (etag != null) root.addProperty("etag", etag);
        JsonArray entries = new JsonArray();
        for (Kit kit : kits) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", kit.name());
            entry.addProperty("price", kit.price());
            entries.add(entry);
        }
        root.add("kits", entries);
        Files.createDirectories(directory);
        Path file = cacheFile();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, root.toString());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
