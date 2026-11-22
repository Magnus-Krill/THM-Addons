/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.tabs.builtin.GuiTab;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.render.color.Color;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import xyz.thm.addon.gui.ClickGuiAnimation;
import xyz.thm.addon.gui.MainMenuFx;
import xyz.thm.addon.gui.MainMenuSettingsScreen;
import xyz.thm.addon.gui.ServerFolderSidebar;
import xyz.thm.addon.gui.ThmStyledButtons;
import xyz.thm.addon.gui.themes.LiquidGlassTheme;
import xyz.thm.addon.gui.themes.bephax.BepGuiTheme;
import xyz.thm.addon.accessor.ServerFolderView;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.modules.HotbarManager;
import xyz.thm.addon.modules.KitbotFrontend;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.DeepSwapManager;
import xyz.thm.addon.utils.PacketBudget;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Arrays;

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
                verifyHighwayUtilityMigration();
                verifyHotbarManager();
                verifyKitbotUpdateIsolation();
                verifyServerFolders(client);
                verifyClickGuiAnimations(client);
                verifyBepHaxTheme(client);
                verifyGlassThemes(client);
                if (Arrays.asList(GuiThemes.getNames()).contains("Catppuccin")) {
                    String theme = GuiThemes.get().name;
                    try {
                        GuiThemes.select("Catppuccin");
                        verifyJoinButtonLayout(client, client.gui.screen());
                        verifyClickGuiAnimations(client);
                    } finally {
                        GuiThemes.select(theme);
                    }
                }
                Files.writeString(client.gameDirectory.toPath().resolve("startup-ok"), Long.toString(now - readyAt));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (ReflectiveOperationException | CommandSyntaxException e) {
                throw new IllegalStateException("Module regression check failed", e);
            }
            client.stop();
        });
    }

    private static void verifyBepHaxTheme(Minecraft client) throws ReflectiveOperationException {
        var original = GuiThemes.get();
        var title = client.gui.screen();
        try {
            GuiThemes.select("BepHax");
            if (!(GuiThemes.get() instanceof BepGuiTheme)) {
                throw new IllegalStateException("BepHax is missing from the theme selector");
            }
            Tabs.get(GuiTab.class).openScreen(GuiThemes.get());
            var settings = (WidgetScreen) client.gui.screen();
            settings.renderCustom(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), 0, 0, 4);
            client.gui.setScreen(title);
            verifyClickGuiAnimations(client);
        } finally {
            GuiThemes.select(original.name);
            client.gui.setScreen(title);
        }
    }

    private static void verifyGlassThemes(Minecraft client) {
        var original = GuiThemes.get();
        var title = client.gui.screen();
        var names = Arrays.asList(GuiThemes.getNames());
        if (names.contains("Blue Hour") || names.contains("Teal Slate")) {
            throw new IllegalStateException("Removed themes are still registered");
        }
        try {
            for (String name : new String[] {"Violet Glass", "Liquid Glass"}) {
                GuiThemes.select(name);
                var theme = GuiThemes.get();
                if (!theme.name.equals(name)) throw new IllegalStateException("Missing theme " + name);

                Tabs.get(GuiTab.class).openScreen(theme);
                var settings = (WidgetScreen) client.gui.screen();
                settings.renderCustom(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), 0, 0, 14);
                if (theme instanceof LiquidGlassTheme glass && !glass.renderedGlass()) {
                    throw new IllegalStateException("Liquid Glass did not render on the GUI tab");
                }

                var modules = theme.modulesScreen();
                client.gui.setScreen(modules);
                modules.renderCustom(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), 0, 0, 14);
                if (theme instanceof LiquidGlassTheme glass) {
                    if (!glass.renderedGlass()) throw new IllegalStateException("Liquid Glass did not render on the Modules screen");
                    verifyLiquidGlassControls(client, glass);
                    verifyLiquidGlassOverscroll(client, glass);
                }
            }
        } finally {
            GuiThemes.select(original.name);
            client.gui.setScreen(title);
        }
    }

    private static void verifyLiquidGlassOverscroll(Minecraft client, LiquidGlassTheme theme) {
        var window = theme.window("Scrollable");
        var first = theme.label("First row");
        var controls = new WidgetScreen(theme, "Liquid Glass scrolling") {
            @Override
            public void initWidgets() {
                add(window).center();
                window.view.maxHeight = theme.scale(70);
                window.add(first);
                for (int i = 0; i < 10; i++) window.add(theme.label("Another row"));
            }
        };
        client.gui.setScreen(controls);
        controls.renderCustom(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), 0, 0, 14);

        window.view.mouseOver = true;
        double top = first.y;
        if (!window.view.onMouseScrolled(1)) throw new IllegalStateException("Liquid Glass did not handle overscroll at the top");
        window.view.onMouseScrolled(-100);
        controls.renderCustom(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), 0, 0, 14);
        double bottom = first.y;
        if (bottom >= top || !window.view.onMouseScrolled(-1)) {
            throw new IllegalStateException("Liquid Glass did not scroll to and handle the bottom edge");
        }
        controls.renderCustom(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), 0, 0, 14);
        if (first.y != bottom) throw new IllegalStateException("Liquid Glass overscroll moved the control positions");

        window.view.mouseOver = false;
        if (window.view.onMouseScrolled(-1)) throw new IllegalStateException("Liquid Glass handled scroll outside the view");
    }

    private static void verifyLiquidGlassControls(Minecraft client, LiquidGlassTheme theme) {
        var dropdown = theme.dropdown(new String[]{"First", "Second"}, "First");
        var checkbox = theme.checkbox(false);
        var controls = new WidgetScreen(theme, "Liquid Glass controls") {
            @Override
            public void initWidgets() {
                var list = add(theme.horizontalList()).center().widget();
                list.add(dropdown).minWidth(140).expandWidgetX();
                list.add(checkbox);
            }
        };
        client.gui.setScreen(controls);
        controls.renderCustom(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), 0, 0, 14);

        var left = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
        double x = dropdown.x + dropdown.width - theme.scale(8);
        double y = dropdown.y + dropdown.height / 2;
        dropdown.mouseMoved(x, y, x, y);
        var open = new MouseButtonEvent(x, y, left);
        dropdown.mouseClicked(open, false);
        dropdown.mouseReleased(open);
        controls.renderCustom(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), 0, 0, 14);

        double optionY = y + dropdown.height * 2;
        dropdown.mouseMoved(x, optionY, x, y);
        var select = new MouseButtonEvent(x, optionY, left);
        dropdown.mouseClicked(select, false);
        dropdown.mouseReleased(select);
        if (!"Second".equals(dropdown.get())) throw new IllegalStateException("Liquid Glass string select did not select the second option");

        double switchX = checkbox.x + checkbox.width / 2;
        double switchY = checkbox.y + checkbox.height / 2;
        checkbox.mouseMoved(switchX, switchY, x, optionY);
        var toggle = new MouseButtonEvent(switchX, switchY, left);
        checkbox.mouseClicked(toggle, false);
        checkbox.mouseReleased(toggle);
        if (!checkbox.checked) throw new IllegalStateException("Liquid Glass switch did not toggle");
    }

    private static void verifyClickGuiAnimations(Minecraft client) throws ReflectiveOperationException {
        Screen title = client.gui.screen();
        var setting = THMSystem.get().clickGuiAnimation;
        var savedAnimation = setting.get();
        var speed = THMSystem.get().clickGuiAnimationSpeed;
        int savedSpeed = speed.get();
        var icons = GuiThemes.get() instanceof MeteorGuiTheme theme ? theme.categoryIcons : null;
        boolean savedIcons = icons != null && icons.get();
        var shift = new KeyEvent(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, GLFW.GLFW_MOD_SHIFT);
        try {
            Tabs.get(GuiTab.class).openScreen(GuiThemes.get());
            var window = WindowTabScreen.class.getDeclaredField("window");
            window.setAccessible(true);
            if (!hasAnimationDropdown((WContainer) window.get(client.gui.screen()))) {
                throw new IllegalStateException("Click GUI Animation is missing from the GUI tab");
            }
            client.gui.setScreen(title);
            if (icons != null) icons.set(true);
            speed.set(100);
            for (ClickGuiAnimation animation : ClickGuiAnimation.values()) {
                if (animation == ClickGuiAnimation.Default) continue;
                setting.set(animation);
                MeteorClient.EVENT_BUS.post(KeyInputEvent.get(shift, KeyAction.Press));
                if (!(client.gui.screen() instanceof WidgetScreen modules)
                    || !GuiThemes.get().isModulesScreen(modules) || modules.animProgress != 0) {
                    throw new IllegalStateException(animation + " did not start when opening Modules");
                }

                var midOpen = new GuiRenderState();
                modules.renderCustom(new GuiGraphicsExtractor(client, midOpen, 0, 0), 0, 0, 4);
                if (modules.animProgress <= 0 || modules.animProgress >= 1) {
                    throw new IllegalStateException(animation + " did not draw an intermediate opening frame");
                }
                if (animation == ClickGuiAnimation.Fade) {
                    if (icons != null) {
                        boolean[] translucentIcon = {false};
                        midOpen.forEachItem(item -> {
                            int alpha = ((ClickGuiAnimation.ItemTint) (Object) item).thm$itemTint() >>> 24;
                            if (alpha > 0 && alpha < 255) translucentIcon[0] = true;
                        });
                        if (!translucentIcon[0]) throw new IllegalStateException("Category icons did not fade with the panels");
                    } else if (GuiThemes.get().name.equals("Catppuccin")) {
                        var roundedUniforms = Class.forName("me.pindour.catppuccin.renderer.rounded.modern.RoundedUniforms");
                        var setColor = roundedUniforms.getDeclaredMethod("setColor", Vector4f.class, Color.class);
                        setColor.setAccessible(true);
                        var rgba = new Vector4f();
                        setColor.invoke(null, rgba, new Color(255, 255, 255, 255));
                        if (Math.abs(rgba.w - modules.animProgress) > 1.0 / 255.0) {
                            throw new IllegalStateException("Catppuccin panel chrome did not fade");
                        }
                    }

                    double normalProgress = modules.animProgress;
                    speed.set(50);
                    modules.animProgress = 0;
                    modules.renderCustom(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), 0, 0, 4);
                    if (modules.animProgress != normalProgress / 2) {
                        throw new IllegalStateException("Animation Speed did not scale the transition");
                    }
                    speed.set(100);
                }

                modules.renderCustom(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), 0, 0, 8);
                var scatter = (ClickGuiAnimation.ScatterSeed) modules;
                int openingSeed = scatter.thm$scatterSeed();
                MeteorClient.EVENT_BUS.post(KeyInputEvent.get(shift, KeyAction.Press));
                if (animation == ClickGuiAnimation.Scatter && scatter.thm$scatterSeed() == openingSeed) {
                    throw new IllegalStateException("Scatter reused its opening paths on close");
                }
                modules.renderCustom(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), 0, 0, 4);
                if (client.gui.screen() != modules || modules.animProgress <= 0 || modules.animProgress >= 1) {
                    throw new IllegalStateException(animation + " did not draw an intermediate closing frame");
                }
                modules.renderCustom(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), 0, 0, 8);
                if (client.gui.screen() != title) throw new IllegalStateException(animation + " did not return to the title screen");
            }
        } finally {
            if (icons != null) icons.set(savedIcons);
            setting.set(savedAnimation);
            speed.set(savedSpeed);
            if (client.gui.screen() != title) client.gui.setScreen(title);
        }
    }

    private static boolean hasAnimationDropdown(WContainer container) {
        return container.cells.stream().anyMatch(cell ->
            cell.widget() instanceof WDropdown<?> dropdown && dropdown.get() instanceof ClickGuiAnimation
                || cell.widget() instanceof WContainer child && hasAnimationDropdown(child));
    }

    private static void verifyServerFolders(Minecraft client) {
        Screen title = client.gui.screen();
        verifyJoinButtonLayout(client, title);

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

    private static void verifyJoinButtonLayout(Minecraft client, Screen title) {
        var window = THMSystem.get().mainMenuWindow;
        var shortcut = THMSystem.get().mainMenuJoin6b6t;
        boolean savedWindow = window.get(), savedShortcut = shortcut.get();
        int width = title.width, height = title.height;
        var left = new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
        try {
            for (boolean styled : new boolean[] {true, false}) {
                window.set(styled);
                shortcut.set(true);
                title.resize(420, 240);
                var join = button(title, MainMenuFx.JOIN_6B6T);
                var multiplayer = button(title, I18n.get("menu.multiplayer"));
                if (join.getX() != multiplayer.getX() || join.getWidth() != multiplayer.getWidth()
                    || join.getY() < button(title, I18n.get("menu.options")).getBottom()
                    || join.getY() < button(title, I18n.get("menu.quit")).getBottom() || join.getBottom() > title.height - 10) {
                    throw new IllegalStateException("Join 6b6t did not fit below Options and Quit Game, styled=" + styled);
                }
                if (ThmStyledButtons.isStyled(join) != styled) {
                    throw new IllegalStateException("Join 6b6t did not follow the title screen style");
                }
                button(title, "THM Menu").onPress(left);
                if (!(client.gui.screen() instanceof MainMenuSettingsScreen menu)) {
                    throw new IllegalStateException("THM Menu did not open");
                }
                menu.children().stream().filter(Checkbox.class::isInstance).map(Checkbox.class::cast)
                    .filter(box -> box.getMessage().getString().equals("Join 6b6t Button"))
                    .findFirst().orElseThrow().onPress(left);
                menu.onClose();
                if (shortcut.get() || title.children().stream().anyMatch(child -> child instanceof Button button
                    && button.getMessage().getString().equals(MainMenuFx.JOIN_6B6T))) {
                    throw new IllegalStateException("Disabling Join 6b6t in THM Menu did not hide the shortcut");
                }
            }
        } finally {
            window.set(savedWindow);
            shortcut.set(savedShortcut);
            if (client.gui.screen() != title) client.gui.setScreen(title);
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
        Field state = field("state");
        Object savedState = state.get(builder);
        try {
            builder.packetBudget.set(true);
            builder.settings.get("packet-build", Boolean.class).set(true);
            builder.settings.get("packet-dig", Boolean.class).set(false);
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
            builder.settings.get("packet-dig", Boolean.class).set(true);
            builder.settings.get("legacy-mode", Boolean.class).set(false);
            builder.settings.get("rotation", HighwayBuilderTHM.Rotation.class).set(HighwayBuilderTHM.Rotation.None);
            Object forward = java.util.Arrays.stream(state.getType().getEnumConstants())
                .filter(value -> ((Enum<?>) value).name().equals("Forward"))
                .findFirst().orElseThrow();
            state.set(builder, forward);
            mine.setInt(builder, 1000);
            apply.invoke(builder);
            costs = (PacketBudget.Costs) costsField.get(builder);
            int packetDigs = costs.dig() == 0 ? 1000 : Math.max(1, costs.available() / costs.dig());
            if (mine.getInt(builder) != packetDigs) {
                throw new IllegalStateException("Packet Dig did not reserve one dig packet per block");
            }
            builder.packetBudget.set(false);
            mine.setInt(builder, 1000);
            place.setInt(builder, Integer.MAX_VALUE);
            apply.invoke(builder);
            if (costsField.get(builder) != null || mine.getInt(builder) != 1000 || place.getInt(builder) != Integer.MAX_VALUE) {
                throw new IllegalStateException("Disabled budget still throttles mining or placing");
            }
        } finally {
            state.set(builder, savedState);
            builder.loadSettingsSnapshot(saved);
        }
    }

    private static void verifyHighwayUtilityMigration() throws CommandSyntaxException {
        var modules = Modules.get();
        var saved = modules.toTag();
        var builder = modules.get(HighwayBuilderTHM.class);
        var legacy = TagParser.parseCompoundFully("""
            {modules:[
                {name:"THM-HighwayBuilder",settings:{groups:[{name:"Inventory",settings:[
                    {name:"anti-drop-items",value:["minecraft:diamond"]}
                ]}]}},
                {name:"catch-up-pacing",active:true},
                {name:"AntiDrop",active:true,settings:{groups:[{name:"General",settings:[
                    {name:"check-shulkers",value:true},{name:"items",value:["minecraft:obsidian"]}
                ]}]}}
            ]}
            """);
        try {
            modules.fromTag(legacy);
            if (!builder.settings.get("catch-up-pacing", Boolean.class).get()
                || !builder.settings.get("Anti-drop", Boolean.class).get()
                || !builder.settings.get("anti-drop-check-shulkers", Boolean.class).get()
                || !builder.settings.get("anti-drop-items").get().equals(java.util.List.of(Items.DIAMOND))) {
                throw new IllegalStateException("HighwayBuilder lost migrated settings or overwrote its item filter");
            }
            if (modules.get("catch-up-pacing") != null || modules.get("AntiDrop") != null
                || builder.limitCatchUpTicks(8, null) != 8) {
                throw new IllegalStateException("Highway utilities ran independently of HighwayBuilder");
            }
        } finally {
            modules.fromTag(saved);
        }
    }

    private static void verifyHotbarManager() throws ReflectiveOperationException {
        var modules = Modules.get();
        var manager = modules.get(HotbarManager.class);
        var builder = modules.get(HighwayBuilderTHM.class);
        var preferred = HighwayBuilderTHM.class.getDeclaredMethod("getPreferredHotbarSlot", Item.class);
        preferred.setAccessible(true);
        var first = (ItemSetting) manager.settings.get("slot-1");
        var legacy = new CompoundTag();
        var items = new ListTag();
        items.add(StringTag.valueOf("minecraft:obsidian"));
        legacy.put("value", items);

        if (!builder.isHotbarSlotReserved(8) || (int) preferred.invoke(builder, Items.OBSIDIAN) != 8) {
            throw new IllegalStateException("HighwayBuilder lost its default placement slot");
        }
        manager.enable();
        try {
            first.fromTag(legacy);
            if (!builder.isHotbarSlotReserved(0) || !DeepSwapManager.get().reserved(0)
                || (int) preferred.invoke(builder, Items.OBSIDIAN) != 0) {
                throw new IllegalStateException("HighwayBuilder ignored the restored custom slot");
            }
            first.set(Items.AIR);
            if (builder.isHotbarSlotReserved(0) || (int) preferred.invoke(builder, Items.OBSIDIAN) != 8) {
                throw new IllegalStateException("Clearing a custom slot did not release it");
            }
        } finally {
            first.reset();
            manager.disable();
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
