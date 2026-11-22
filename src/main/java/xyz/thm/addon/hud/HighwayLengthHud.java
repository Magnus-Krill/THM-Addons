/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.hud;

import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.world.level.Level;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.APIUtils;
import xyz.thm.addon.utils.APIUtils.HighwayLength;
import xyz.thm.addon.utils.THMUtils;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class HighwayLengthHud extends HudElement {
    public static final HudElementInfo<HighwayLengthHud> INFO = new HudElementInfo<>(
        THMAddon.HUD_GROUP, "highway-length-hud", "Shows the mapped length of the current 6b6t highway.", HighwayLengthHud::new
    );

    // All HUD instances share one request and snapshot on the client thread.
    private static Map<String, Map<String, HighwayLength>> lengths;
    private static boolean fetching;
    private static long nextFetch = System.nanoTime();

    private final NumberFormat blocks = NumberFormat.getIntegerInstance(Locale.US);
    private int ticksUntilUpdate;
    private String text;

    public HighwayLengthHud() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        if (!isActive() || mc.level == null || mc.getCurrentServer() == null
            || !THMUtils.is6b6tAddress(mc.getCurrentServer().ip)) {
            text = null;
            ticksUntilUpdate = 0;
            return;
        }
        long now = System.nanoTime();
        if (ticksUntilUpdate-- > 0) return;
        ticksUntilUpdate = 9;
        var dimension = mc.level.dimension();
        String world = dimension == Level.NETHER ? "nether" : dimension == Level.OVERWORLD ? "overworld"
            : dimension == Level.END ? "end" : null;
        String road = world == null ? null : THMUtils.getCurrentMappedHighway();
        if (road == null) {
            text = null;
            return;
        }

        String label = roadLabel(road) + " · ";
        if (!fetching && now - nextFetch >= 0) {
            fetching = true;
            THMUtils.async("highway-lengths", () -> {
                var result = APIUtils.fetchHighwayLengths();
                mc.execute(() -> {
                    fetching = false;
                    lengths = result;
                    nextFetch = System.nanoTime() + TimeUnit.MINUTES.toNanos(result == null ? 1 : 5);
                });
            });
        }
        if (lengths == null) {
            text = label + (fetching ? "Loading length…" : "Length unavailable");
            return;
        }
        HighwayLength length = lengths.get(world).get(road);
        text = label + (length == null ? "No recorded length" : blocks.format(length.total()) + " blocks");
        if (length != null && length.paved() < length.total()) text += " · " + blocks.format(length.paved()) + " paved";
    }

    private String roadLabel(String road) {
        if (road.startsWith("ring-")) return "Ring road " + blocks.format(Integer.parseInt(road.substring(5)));
        if (road.startsWith("diamond-")) return "Diamond highway " + blocks.format(Integer.parseInt(road.substring(8)));
        return road + " Highway";
    }

    @Override
    public void render(HudRenderer renderer) {
        String line = text == null && isInEditor() ? "Highway Length" : text;
        if (line == null) {
            setSize(0, 0);
            return;
        }
        setSize(renderer.textWidth(line, true), renderer.textHeight(true));
        renderer.text(line, x, y, Color.WHITE, true);
    }
}
