/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import meteordevelopment.meteorclient.systems.modules.Modules;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.utils.PacketBudget;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;

public final class StartupClient implements ClientModInitializer {
    private long readyAt;
    private boolean closing;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (closing || !(client.gui.screen() instanceof TitleScreen) || client.gui.overlay() != null) return;
            long now = System.nanoTime();
            if (readyAt == 0) readyAt = now;
            if (now - readyAt < 1_000_000_000L) return;
            closing = true;
            try {
                verifyPacketBudget();
                Files.writeString(client.gameDirectory.toPath().resolve("startup-ok"), Long.toString(now - readyAt));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Packet budget regression check failed", e);
            }
            client.stop();
        });
    }

    private static void verifyPacketBudget() throws ReflectiveOperationException {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        var saved = builder.settings.toTag();
        var apply = HighwayBuilderTHM.class.getDeclaredMethod("applyPacketBudget");
        var allowance = HighwayBuilderTHM.class.getDeclaredMethod("currentPlaceActionsThisTick");
        apply.setAccessible(true);
        allowance.setAccessible(true);
        Field costsField = field("packetCostsThisTick"), mine = field("mineActionsThisTick");
        Field place = field("placeActionsThisTick"), remaining = field("minePacketsLeft");
        try {
            builder.packetBudget.set(true);
            builder.settings.get("packet-build", Boolean.class).set(true);
            builder.settings.get("packet-borer", Boolean.class).set(false);
            builder.settings.get("enable-experimental", Boolean.class).set(false);
            builder.settings.get("packet-build-normal-rate", Boolean.class).set(false);
            mine.setInt(builder, 1000);
            place.setInt(builder, Integer.MAX_VALUE);
            apply.invoke(builder);
            var costs = (PacketBudget.Costs) costsField.get(builder);
            if (costs == null || (int) allowance.invoke(builder) != costs.placements(costs.available())) {
                throw new IllegalStateException("Enabled budget was not applied to fast placement");
            }
            var charge = HighwayBuilderTHM.class.getDeclaredMethod("chargeDigPackets", int.class);
            charge.setAccessible(true);
            charge.invoke(builder, 3);
            if (remaining.getInt(builder) != costs.available() - 3 * costs.dig()) {
                throw new IllegalStateException("Early double-mine packets did not reduce the current budget");
            }
            int left = costs.available() - 2 * costs.mine(false);
            remaining.setInt(builder, left);
            if ((int) allowance.invoke(builder) != costs.placements(left) || (int) allowance.invoke(builder) <= 0) {
                throw new IllegalStateException("Unused mining budget was not reclaimed with experimental settings off");
            }
            builder.packetBudget.set(false);
            mine.setInt(builder, 1000);
            place.setInt(builder, Integer.MAX_VALUE);
            apply.invoke(builder);
            if (costsField.get(builder) != null || mine.getInt(builder) != 1000 || place.getInt(builder) != Integer.MAX_VALUE) {
                throw new IllegalStateException("Disabled budget still throttles mining or placing");
            }
        } finally {
            builder.loadSettingsSnapshot(saved);
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = HighwayBuilderTHM.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
