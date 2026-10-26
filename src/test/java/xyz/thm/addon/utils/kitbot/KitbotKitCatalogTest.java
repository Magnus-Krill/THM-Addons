/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils.kitbot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import xyz.thm.addon.utils.TrustedHttp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class KitbotKitCatalogTest {
    private static final String TOKEN_A = "00112233-4455-6677-8899-aabbccddeeff";
    private static final String TOKEN_B = "11112233-4455-6677-8899-aabbccddeeff";
    private static final String CATALOG = """
        {"success":true,"kits":[{"name":"pvp","price":0.5},{"name":"obby","price":0}]}
        """;
    @TempDir Path directory;
    private final AtomicReference<String> token = new AtomicReference<>(TOKEN_A);
    private final AtomicLong now = new AtomicLong(1_000_000_000_000L);
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private final List<String> sentEtags = new ArrayList<>();
    private final List<String> sentTokens = new ArrayList<>();
    private TrustedHttp.Response response;
    private KitbotKitCatalog catalog;

    @BeforeEach
    void setup() {
        response = response(200, CATALOG, "\"v1\"");
        catalog = newCatalog();
    }

    private KitbotKitCatalog newCatalog() {
        return new KitbotKitCatalog(directory, token::get, (credential, etag) -> {
            sentTokens.add(credential);
            sentEtags.add(etag);
            return response;
        }, pending::add, now::get);
    }

    private void fetch() {
        catalog.current();
        pending.remove().run();
    }

    @Test
    void cachesToDiskAndRevalidatesOnlyAfterSixHours() throws Exception {
        assertTrue(catalog.current().isEmpty());
        catalog.current();
        assertEquals(1, pending.size());
        pending.remove().run();
        assertEquals(List.of("pvp", "obby"), catalog.current().stream().map(KitbotKitCatalog.Kit::name).toList());
        assertTrue(pending.isEmpty());
        catalog = newCatalog();
        assertEquals(2, catalog.current().size());
        assertTrue(pending.isEmpty());
        try (var files = Files.list(directory)) {
            Path file = files.findFirst().orElseThrow();
            assertFalse(file.getFileName().toString().contains(TOKEN_A));
            assertFalse(Files.readString(file).contains(TOKEN_A));
        }

        now.addAndGet(KitbotKitCatalog.REFRESH_MS);
        catalog = newCatalog();
        assertEquals(2, catalog.current().size());
        response = response(304, "", "\"v1\"");
        fetch();
        assertEquals("\"v1\"", sentEtags.getLast());
        assertEquals(2, catalog.current().size());
        catalog = newCatalog();
        assertEquals(2, catalog.current().size());
        assertTrue(pending.isEmpty());
    }

    @Test
    void changedCatalogReplacesOldNamesAndPrices() {
        fetch();
        String[] previousNames = catalog.currentNames();
        assertArrayEquals(new String[] {"pvp", "obby"}, previousNames);
        assertSame(previousNames, catalog.currentNames());
        now.addAndGet(KitbotKitCatalog.REFRESH_MS);
        response = response(200, "{\"success\":true,\"kits\":[{\"name\":\"future-kit\",\"price\":2}]}", "\"v2\"");
        fetch();
        assertEquals(List.of(new KitbotKitCatalog.Kit("future-kit", 2)), catalog.current());
        assertArrayEquals(new String[] {"future-kit"}, catalog.currentNames());
        assertNotSame(previousNames, catalog.currentNames());
        now.addAndGet(KitbotKitCatalog.REFRESH_MS);
        response = response(304, "", null);
        fetch();
        assertEquals("\"v2\"", sentEtags.getLast());
    }

    @Test
    void networkErrorsRateLimitsAndInvalidJsonKeepLastGoodCatalogWithBackoff() {
        fetch();
        List<KitbotKitCatalog.Kit> expected = catalog.current();
        for (TrustedHttp.Response failure : new TrustedHttp.Response[] {
            null, response(429, "", null), response(503, "", null), response(200, "{broken", "\"bad\"")
        }) {
            now.addAndGet(KitbotKitCatalog.REFRESH_MS);
            response = failure;
            fetch();
            assertEquals(expected, catalog.current());
            assertTrue(pending.isEmpty());
            now.addAndGet(KitbotKitCatalog.RETRY_MS - 1);
            catalog.current();
            assertTrue(pending.isEmpty());
            now.incrementAndGet();
            fetch();
            assertEquals("\"v1\"", sentEtags.getLast());
        }
    }

    @Test
    void tokenChangesNeverReuseAnotherTokensCatalogOrEtag() {
        fetch();
        token.set(TOKEN_B);
        assertTrue(catalog.current().isEmpty());
        response = response(200, "{\"success\":true,\"kits\":[]}", "\"empty\"");
        pending.remove().run();
        assertNull(sentEtags.getLast());
        assertEquals(TOKEN_B, sentTokens.getLast());
        assertTrue(catalog.current().isEmpty());
        token.set(TOKEN_A);
        assertEquals(2, catalog.current().size());
        assertTrue(pending.isEmpty());
    }

    @Test
    void responsesArrivingAfterATokenChangeAreDiscarded() {
        catalog = new KitbotKitCatalog(directory, token::get, (credential, etag) -> {
            token.set(TOKEN_B);
            return response;
        }, pending::add, now::get);
        fetch();
        assertTrue(catalog.current().isEmpty());
    }

    @Test
    void queuedRequestsDoNotSendOldTokens() {
        catalog.current();
        token.set(TOKEN_B);
        pending.remove().run();
        assertTrue(sentTokens.isEmpty());
        assertTrue(catalog.current().isEmpty());
    }

    @Test
    void rejectedTokensClearTheirPersistentCatalog() throws Exception {
        for (int status : new int[] {401, 403}) {
            response = response(200, CATALOG, "\"v1\"");
            fetch();
            now.addAndGet(KitbotKitCatalog.REFRESH_MS);
            response = response(status, "", null);
            fetch();
            assertTrue(catalog.current().isEmpty());
            try (var files = Files.list(directory)) {
                assertEquals(0, files.count());
            }
            now.addAndGet(KitbotKitCatalog.RETRY_MS);
        }
    }

    @Test
    void invalidTokensAndUnsolicited304DoNotCreateACatalog() {
        token.set("invalid");
        assertTrue(catalog.current().isEmpty());
        assertTrue(pending.isEmpty());
        token.set(TOKEN_A);
        response = response(304, "", "\"unknown\"");
        fetch();
        assertTrue(catalog.current().isEmpty());
        assertTrue(pending.isEmpty());
    }

    @Test
    void damagedDiskCacheIsIgnoredAndFetchedAgain() throws Exception {
        fetch();
        try (var files = Files.list(directory)) {
            Files.writeString(files.findFirst().orElseThrow(), "{broken");
        }
        catalog = newCatalog();
        assertTrue(catalog.current().isEmpty());
        pending.remove().run();
        assertNull(sentEtags.getLast());
        assertEquals(2, catalog.current().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"success\":false,\"kits\":[]}", "{\"success\":\"true\",\"kits\":[]}", "{\"success\":true}",
        "{\"success\":true,\"kits\":[{\"name\":\"pvp\",\"price\":-1}]}",
        "{\"success\":true,\"kits\":[{\"name\":\"pvp\",\"price\":\"1\"}]}",
        "{\"success\":true,\"kits\":[{\"name\":\"pvp\",\"price\":1e999}]}",
        "{\"success\":true,\"kits\":[{\"name\":\"pvp 1 $token\",\"price\":0}]}",
        "{\"success\":true,\"kits\":[{\"name\":\"pvp\",\"price\":0},{\"name\":\"PVP\",\"price\":1}]}"
    })
    void rejectsMalformedCatalogs(String json) {
        assertThrows(RuntimeException.class, () -> KitbotKitCatalog.parse(json));
    }

    @Test
    void pricesIncludeFreeKitsAndConciseDescriptions() {
        var kits = KitbotKitCatalog.parse(CATALOG);
        assertEquals(0, kits.getLast().price());
        assertEquals("Order this kit for 0 tokens each.", kits.getLast().description());
        assertEquals("Order this kit for 0.5 tokens each.", kits.getFirst().description());
        assertSame(kits.getLast(), KitbotKitCatalog.find(kits, " OBBY "));
    }

    private static TrustedHttp.Response response(int status, String body, String etag) {
        return new TrustedHttp.Response(status, body.getBytes(StandardCharsets.UTF_8), etag);
    }
}
