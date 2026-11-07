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
import xyz.thm.addon.modules.KitbotFrontend;
import xyz.thm.addon.utils.PacketBudget;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.LinkedHashMap;

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
                verifyKitbotUpdateIsolation();
                Files.writeString(client.gameDirectory.toPath().resolve("startup-ok"), Long.toString(now - readyAt));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Module regression check failed", e);
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

    private static void verifyKitbotUpdateIsolation() throws ReflectiveOperationException {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        Field runtimeField = KitbotFrontend.class.getDeclaredField("RUNTIME");
        runtimeField.setAccessible(true);
        Object runtime = runtimeField.get(null);
        Field request = runtime.getClass().getDeclaredField("windowRequest");
        Field deadline = runtime.getClass().getDeclaredField("windowEndsAtMs");
        request.setAccessible(true);
        deadline.setAccessible(true);
        Object previousRequest = request.get(runtime);
        long previousDeadline = deadline.getLong(runtime);
        var saved = new LinkedHashMap<Field, Object>();
        for (String name : new String[]{"state", "kitbotPeriodicUpdateActive", "kitbotPeriodicUpdatePending",
            "kitbotPeriodicUpdateNextAtMs", "kitbotOrderInFlight", "kitbotOrderFrontendSucceeded"}) {
            Field field = field(name);
            saved.put(field, field.get(builder));
        }
        try {
            Field state = field("state"), active = field("kitbotPeriodicUpdateActive");
            Field inFlight = field("kitbotOrderInFlight"), succeeded = field("kitbotOrderFrontendSucceeded");
            Object forward = null, restock = null;
            for (Object value : state.getType().getEnumConstants()) {
                if (((Enum<?>) value).name().equals("Forward")) forward = value;
                if (((Enum<?>) value).name().equals("KitbotOrder")) restock = value;
            }
            if (forward == null || restock == null) throw new IllegalStateException("Missing builder states");
            state.set(builder, forward);
            active.setBoolean(builder, false);
            inFlight.setBoolean(builder, false);
            succeeded.setBoolean(builder, false);
            var kit = Class.forName("xyz.thm.addon.modules.KitbotFrontend$ManagedRequest")
                .getDeclaredMethod("kitRequest", KitbotFrontend.Mode.class, String.class, int.class, boolean.class);
            kit.setAccessible(true);
            Object order = kit.invoke(null, KitbotFrontend.Mode.Kit, "echest", 6, true);
            request.set(runtime, order);
            deadline.setLong(runtime, System.currentTimeMillis() + 100_000L);
            var send = HighwayBuilderTHM.class.getDeclaredMethod("sendKitbotPeriodicUpdate");
            send.setAccessible(true);
            field("kitbotPeriodicUpdatePending").setBoolean(builder, false);
            send.invoke(builder);
            if (!field("kitbotPeriodicUpdatePending").getBoolean(builder) || active.getBoolean(builder)
                || request.get(runtime) != order) {
                throw new IllegalStateException("Periodic update did not defer to the active KitBot order");
            }
            request.set(runtime, null);
            deadline.setLong(runtime, 0L);
            state.set(builder, restock);
            inFlight.setBoolean(builder, true);
            field("kitbotPeriodicUpdatePending").setBoolean(builder, false);
            send.invoke(builder);
            if (!field("kitbotPeriodicUpdatePending").getBoolean(builder) || active.getBoolean(builder)) {
                throw new IllegalStateException("Periodic update was not deferred during restock");
            }
            var lifecycle = HighwayBuilderTHM.class.getDeclaredMethod("onKitbotRestockLifecycle", KitbotFrontend.LifecycleEvent.class);
            lifecycle.setAccessible(true);
            for (var result : new KitbotFrontend.LifecycleState[]{KitbotFrontend.LifecycleState.Succeeded, KitbotFrontend.LifecycleState.Failed}) {
                active.setBoolean(builder, true);
                lifecycle.invoke(builder, new KitbotFrontend.LifecycleEvent(result, KitbotFrontend.RequestOrigin.API,
                    KitbotFrontend.Mode.Update, KitbotFrontend.FailureReason.PermissionDenied, "Update result", 0));
                if (active.getBoolean(builder) || !inFlight.getBoolean(builder) || succeeded.getBoolean(builder)
                    || state.get(builder) != restock) {
                    throw new IllegalStateException("Update outcome changed the restock order");
                }
            }
        } finally {
            request.set(runtime, previousRequest);
            deadline.setLong(runtime, previousDeadline);
            for (var entry : saved.entrySet()) entry.getKey().set(builder, entry.getValue());
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = HighwayBuilderTHM.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
