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
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.THMStashMover;

import java.util.List;

public class THMStashMoverPairHud extends HudElement {
    public static final HudElementInfo<THMStashMoverPairHud> INFO = new HudElementInfo<>(
        THMAddon.HUD_GROUP,
        "stash-mover-pairs",
        "Shows THM Stash mover pair input and deposit status.",
        THMStashMoverPairHud::new
    );

    public THMStashMoverPairHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        THMStashMover module = Modules.get().get(THMStashMover.class);
        List<THMStashMover.PairHudRow> rows = module == null ? List.of() : module.getPairHudRows();

        String pairHeader = "Pair";
        String inputHeader = "Input";
        String depositHeader = "Deposit";

        double gap = 12;
        double pairWidth = renderer.textWidth(pairHeader, true);
        double inputWidth = renderer.textWidth(inputHeader, true);
        double depositWidth = renderer.textWidth(depositHeader, true);

        for (THMStashMover.PairHudRow row : rows) {
            pairWidth = Math.max(pairWidth, renderer.textWidth(row.pairName(), true));
            inputWidth = Math.max(inputWidth, renderer.textWidth(row.inputStatus(), true));
            depositWidth = Math.max(depositWidth, renderer.textWidth(row.depositStatus(), true));
        }

        double rowHeight = renderer.textHeight(true) + 2;
        double totalWidth = pairWidth + inputWidth + depositWidth + gap * 2;
        double totalHeight = rowHeight * (rows.size() + 1);
        setSize(totalWidth, totalHeight);

        double yCursor = y;
        double inputX = x + pairWidth + gap;
        double depositX = inputX + inputWidth + gap;

        renderer.text(pairHeader, x, yCursor, Color.WHITE, true);
        renderer.text(inputHeader, inputX, yCursor, Color.WHITE, true);
        renderer.text(depositHeader, depositX, yCursor, Color.WHITE, true);
        yCursor += rowHeight;

        for (THMStashMover.PairHudRow row : rows) {
            renderer.text(row.pairName(), x, yCursor, Color.LIGHT_GRAY, true);
            renderer.text(row.inputStatus(), inputX, yCursor, statusColor(row.inputStatus()), true);
            renderer.text(row.depositStatus(), depositX, yCursor, statusColor(row.depositStatus()), true);
            yCursor += rowHeight;
        }
    }

    private Color statusColor(String status) {
        if ("full".equals(status) || "Has kits".equals(status)) return Color.GREEN;
        if (status.startsWith("needs ") || "empty".equals(status)) return Color.YELLOW;
        if ("blocked".equals(status)) return Color.RED;
        return Color.GRAY;
    }
}
