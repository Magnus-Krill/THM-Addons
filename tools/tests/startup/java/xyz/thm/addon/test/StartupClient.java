/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.lwjgl.glfw.GLFW;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.gui.ServerFolderSidebar;
import xyz.thm.addon.accessor.ServerFolderView;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.modules.KitbotFrontend;
import xyz.thm.addon.system.THMSystem;
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
                verifyServerFolders(client);
                Files.writeString(client.gameDirectory.toPath().resolve("startup-ok"), Long.toString(now - readyAt));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Module regression check failed", e);
            }
            client.stop();
        });
    }

    private static void verifyServerFolders(Minecraft client) {
        Screen title = client.gui.screen();
        verifyJoinButtonLayout(title);

        var folders = THMSystem.get().serverFolders;
        var saved = folders.toTag();
        var servers = new ServerList(client);
        servers.add(new ServerData("First", "first.invalid", ServerData.Type.OTHER), false);
        servers.add(new ServerData("Second", "second.invalid", ServerData.Type.OTHER), false);
        servers.add(new ServerData("Third", "third.invalid", ServerData.Type.OTHER), false);
        servers.save();
        String folder = folders.putFolder("", "Startup", "");
        folders.setDetails("second.invalid", folder, "Test alias");
        folders.setDetails("third.invalid", folder, "");
        try {
            var multiplayer = new JoinMultiplayerScreen(title);
            client.gui.setScreen(multiplayer);
            var list = ((ServerFolderView) multiplayer).thm$serverList();
            var sidebar = multiplayer.children().stream().filter(ServerFolderSidebar.class::isInstance)
                .map(ServerFolderSidebar.class::cast).findFirst().orElseThrow();
            var search = multiplayer.children().stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow();
            if (sidebar.children().size() != 3 || list.getX() < sidebar.getRight()) {
                throw new IllegalStateException("Server folders sidebar is missing or overlaps the server list");
            }
            sidebar.children().getLast().keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            if (!(list.children().getFirst() instanceof ServerSelectionList.OnlineServerEntry row)
                || !row.getServerData().ip.equals("second.invalid")) {
                throw new IllegalStateException("Selecting a folder did not hide unrelated saved servers");
            }
            row.keyPressed(new KeyEvent(GLFW.GLFW_KEY_DOWN, 0, GLFW.GLFW_MOD_SHIFT));
            if (!multiplayer.getServers().get(0).ip.equals("first.invalid")
                || !multiplayer.getServers().get(1).ip.equals("third.invalid")) {
                throw new IllegalStateException("Reordering a filtered server moved an unrelated saved entry");
            }
            search.setValue("Test alias");
            var visible = list.children().stream().filter(ServerSelectionList.OnlineServerEntry.class::isInstance)
                .map(ServerSelectionList.OnlineServerEntry.class::cast).toList();
            if (visible.size() != 1 || !visible.getFirst().getServerData().ip.equals("second.invalid")
                || !visible.getFirst().getNarration().getString().contains("Test alias")) {
                throw new IllegalStateException("Server folder alias search did not select the expected server");
            }
            var server = visible.getFirst();
            server.getServerData().setState(ServerData.State.UNREACHABLE);
            server.getServerData().motd = CommonComponents.EMPTY;
            server.getServerData().status = CommonComponents.EMPTY;
            var target = sidebar.children().get(1);
            var left = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
            var press = new MouseButtonEvent(server.getContentX() + 20, server.getContentY() + 8, left);
            var drop = new MouseButtonEvent(target.getContentX() + 4, target.getContentY() + 5, left);
            if (!multiplayer.mouseClicked(press, false) || !multiplayer.mouseDragged(drop, drop.x() - press.x(), drop.y() - press.y())) {
                throw new IllegalStateException("Server drag did not start at " + press + " inside " + list.getRectangle()
                    + ", found " + list.getChildAt(press.x(), press.y()));
            }
            var render = new GuiRenderState();
            multiplayer.extractRenderState(new GuiGraphicsExtractor(client, render, (int) drop.x(), (int) drop.y()),
                (int) drop.x(), (int) drop.y(), 0);
            boolean[] icon = {false};
            render.forEachElement(element -> {
                if (element instanceof BlitRenderState blit && blit.x0() == (int) drop.x() + 10
                    && blit.y0() == (int) drop.y() + 8 && blit.x1() - blit.x0() == 32 && blit.y1() - blit.y0() == 32) icon[0] = true;
            }, GuiRenderState.TraverseRange.ALL);
            multiplayer.mouseReleased(drop);
            var details = folders.details("second.invalid");
            if (!details.folder().isEmpty() || !details.alias().equals("Test alias")) {
                throw new IllegalStateException("Dropping a server did not preserve its alias and move it to Unfiled");
            }
            if (!icon[0]) throw new IllegalStateException("Server icon did not follow the dragged row");

            var parent = sidebar.children().getLast();
            var folderDrop = new MouseButtonEvent(parent.getContentX() + 4, parent.getContentY() + 5, left);
            multiplayer.mouseClicked(press, false);
            multiplayer.mouseDragged(folderDrop, folderDrop.x() - press.x(), folderDrop.y() - press.y());
            multiplayer.mouseReleased(folderDrop);
            if (!folders.details("second.invalid").folder().equals(folder)) {
                throw new IllegalStateException("Dropping a server into a folder did not assign it to that folder");
            }
            var right = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0);
            multiplayer.mouseClicked(new MouseButtonEvent(parent.getContentX() + 4, parent.getContentY() + 5, right), false);
            if (!(client.gui.screen() instanceof ServerFolderSidebar.EditScreen editor)) {
                throw new IllegalStateException("Right-click did not open the server folder editor");
            }
            editor.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow().setValue("Nested");
            button(editor, CommonComponents.GUI_DONE.getString()).onPress(left);
            if (folders.children(folder).stream().noneMatch(child -> child.name().equals("Nested"))) {
                throw new IllegalStateException("Right-click folder was not created under the clicked folder");
            }
        } finally {
            client.gui.setScreen(title);
            folders.fromTag(saved);
        }
    }

    private static void verifyJoinButtonLayout(Screen title) {
        var window = THMSystem.get().mainMenuWindow;
        boolean saved = window.get();
        int width = title.width, height = title.height;
        try {
            for (boolean styled : new boolean[] {true, false}) {
                window.set(styled);
                title.resize(420, 240);
                var join = button(title, MainMenuFx.JOIN_6B6T);
                var multiplayer = button(title, I18n.get("menu.multiplayer"));
                if (join.getX() != multiplayer.getX() || join.getWidth() != multiplayer.getWidth()
                    || join.getY() < button(title, I18n.get("menu.options")).getBottom()
                    || join.getY() < button(title, I18n.get("menu.quit")).getBottom() || join.getBottom() > title.height - 10) {
                    throw new IllegalStateException("Join 6b6t did not fit below Options and Quit Game, styled=" + styled);
                }
            }
        } finally {
            window.set(saved);
            title.resize(width, height);
        }
    }

    private static Button button(Screen screen, String label) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
            .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow();
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
