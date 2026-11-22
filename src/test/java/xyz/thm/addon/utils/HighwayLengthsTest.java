/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.utils;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import xyz.thm.addon.utils.APIUtils.HighwayLength;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HighwayLengthsTest {
    @Test
    void readsRecordedLengthsByDimensionAndSkipsInvalidRows() {
        var json = JsonParser.parseString("""
            {
              "nether": {
                "N": { "paved": 234, "total": 1234 },
                "ring-25000": { "paved": 200000, "total": 200000 },
                "E": { "paved": 100, "total": 99 },
                "NW": { "paved": -1, "total": 30 },
                "W": { "paved": 0, "total": "40" },
                "S": { "paved": 0, "total": 2.5 },
                "NE": { "paved": 0, "total": 4294967306 }
              },
              "overworld": {},
              "end": { "N": { "paved": 0, "total": 12 } }
            }
            """).getAsJsonObject();

        assertEquals(Map.of(
            "nether", Map.of("N", new HighwayLength(234, 1234), "ring-25000", new HighwayLength(200000, 200000)),
            "overworld", Map.of(),
            "end", Map.of("N", new HighwayLength(0, 12))
        ), APIUtils.parseHighwayLengths(json));
    }
}
