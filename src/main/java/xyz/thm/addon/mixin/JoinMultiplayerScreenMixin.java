/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.gui.ServerFolderSidebar;
import xyz.thm.addon.accessor.ServerFolderView;
import xyz.thm.addon.mixin.accessor.ServerSelectionListAccessor;
import xyz.thm.addon.system.ServerFolders;
import xyz.thm.addon.system.THMSystem;

import java.util.List;
import java.util.Locale;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen implements ServerFolderView {
    @Shadow protected ServerSelectionList serverSelectionList;
    @Shadow private ServerData editingServer;
    @Shadow public abstract ServerList getServers();
    @Unique private String thm$selectedFolder = ServerFolders.ALL;
    @Unique private String thm$query = "";
    @Unique private ServerFolderSidebar thm$sidebar;
    @Unique private EditBox thm$search;
    @Unique private Button thm$newFolder, thm$editFolder, thm$removeFolder, thm$organize;
    @Unique private Button thm$moveUp, thm$moveDown;
    @Unique private ServerSelectionList.OnlineServerEntry thm$dragEntry;
    @Unique private MouseButtonEvent thm$dragPress;
    @Unique private boolean thm$dragStarted, thm$dragDoubleClick;
    @Unique private ServerData thm$lastClickedServer;

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Override
    public String thm$folder() {
        return thm$selectedFolder;
    }

    @Override
    public String thm$serverQuery() {
        return thm$query;
    }

    @Override
    public ServerSelectionList thm$serverList() {
        return serverSelectionList;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void thm$createFolderControls(CallbackInfo ci) {
        if (THMSystem.get() == null) return;
        thm$sidebar = addRenderableWidget(new ServerFolderSidebar(minecraft, id -> {
            thm$selectedFolder = id;
            thm$search.setValue("");
            thm$refreshServers();
        }));
        thm$sidebar.rebuild(THMSystem.get().serverFolders, thm$selectedFolder);
        thm$search = addRenderableWidget(new EditBox(font, 0, 0, 100, 18, Component.literal("Search servers")));
        thm$search.setHint(Component.literal("Search all servers"));
        thm$search.setValue(thm$query);
        thm$search.setResponder(value -> {
            thm$query = value.strip().toLowerCase(Locale.ROOT);
            thm$refreshServers();
        });
        thm$newFolder = addRenderableWidget(Button.builder(Component.literal("New"), b -> thm$editFolder(null, thm$selectedFolder)).build());
        thm$editFolder = addRenderableWidget(Button.builder(Component.literal("Edit"), b ->
            thm$editFolder(THMSystem.get().serverFolders.get(thm$selectedFolder), thm$selectedFolder)).build());
        thm$removeFolder = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> thm$deleteFolder()).build());
        thm$moveUp = thm$moveButton("↑", "Move folder up", -1);
        thm$moveDown = thm$moveButton("↓", "Move folder down", 1);
        thm$organize = addRenderableWidget(Button.builder(Component.literal("Organize server"), b -> thm$organizeServer()).build());
        addRenderableOnly(this::thm$extractDragIcon);
        thm$layout(serverSelectionList.getBottom());
        thm$updateControls();
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void thm$resizeFolderControls(CallbackInfo ci) {
        if (thm$sidebar != null) {
            thm$clearDrag();
            thm$layout(serverSelectionList.getBottom());
            thm$sidebar.rebuild(THMSystem.get().serverFolders, thm$selectedFolder);
            thm$refreshServers();
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void thm$cancelDragOnClose(CallbackInfo ci) {
        thm$clearDrag();
        thm$lastClickedServer = null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (thm$sidebar != null) {
            if (event.button() == 1 && (thm$sidebar.isMouseOver(event.x(), event.y())
                || serverSelectionList.isMouseOver(event.x(), event.y()))) {
                String folder = thm$sidebar.folderAt(event.x(), event.y());
                thm$editFolder(null, folder != null ? folder : thm$selectedFolder);
                return true;
            }
            if (event.button() == 0) {
                thm$clearDrag();
                var entry = thm$serverAt(event.x(), event.y());
                if (entry != null) {
                    thm$dragEntry = entry;
                    thm$dragPress = event;
                    thm$dragDoubleClick = doubleClick && thm$lastClickedServer == entry.getServerData();
                    // Vanilla joins from the icon on mouse-down, so defer its click until release.
                    return true;
                }
                thm$lastClickedServer = null;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() != 0 || thm$dragPress == null) return super.mouseDragged(event, dx, dy);
        thm$dragStarted |= thm$movedFromPress(event);
        if (thm$dragStarted) thm$sidebar.setDropTarget(thm$dropTarget(event));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != 0 || thm$dragPress == null) return super.mouseReleased(event);
        var entry = thm$dragEntry;
        var press = thm$dragPress;
        boolean dragged = thm$dragStarted || thm$movedFromPress(event);
        boolean doubleClick = thm$dragDoubleClick;
        thm$clearDrag();
        if (dragged) {
            thm$lastClickedServer = null;
            String target = thm$dropTarget(event);
            if (target != null) {
                var system = THMSystem.get();
                String address = entry.getServerData().ip;
                var details = system.serverFolders.details(address);
                if (!details.folder().equals(target)) {
                    system.serverFolders.setDetails(address, target, details.alias());
                    system.save();
                    thm$refreshServers();
                }
            }
        } else if (thm$serverAt(press.x(), press.y()) == entry) {
            thm$lastClickedServer = entry.getServerData();
            super.mouseClicked(press, doubleClick);
            super.mouseReleased(event);
        }
        return true;
    }

    @Unique
    private void thm$extractDragIcon(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (thm$dragStarted) {
            var icon = ((ServerFolderView.EntryIcon) thm$dragEntry).thm$iconLocation();
            int x = Math.clamp(mouseX + 10, 0, Math.max(0, width - 32));
            int y = Math.clamp(mouseY + 8, 0, Math.max(0, height - 32));
            graphics.nextStratum();
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, 32, 32, 32, 32);
        }
    }

    @Unique
    private ServerSelectionList.OnlineServerEntry thm$serverAt(double x, double y) {
        if (!serverSelectionList.isMouseOver(x, y)) return null;
        var child = serverSelectionList.getChildAt(x, y).orElse(null);
        return child instanceof ServerSelectionList.OnlineServerEntry entry ? entry : null;
    }

    @Unique
    private String thm$dropTarget(MouseButtonEvent event) {
        String folder = thm$sidebar.folderAt(event.x(), event.y());
        return ServerFolders.ALL.equals(folder) ? null : folder;
    }

    @Unique
    private boolean thm$movedFromPress(MouseButtonEvent event) {
        double x = event.x() - thm$dragPress.x(), y = event.y() - thm$dragPress.y();
        return x * x + y * y >= 16;
    }

    @Unique
    private void thm$clearDrag() {
        thm$dragEntry = null;
        thm$dragPress = null;
        thm$dragStarted = false;
        if (thm$sidebar != null) thm$sidebar.setDropTarget(null);
    }

    @Inject(method = "onSelectedChange", at = @At("TAIL"))
    private void thm$updateFolderControls(CallbackInfo ci) {
        if (thm$organize != null) thm$updateControls();
    }

    @Inject(method = "addServerCallback", at = @At("HEAD"))
    private void thm$assignNewServer(boolean confirmed, CallbackInfo ci) {
        THMSystem system = THMSystem.get();
        if (!confirmed || system == null || !thm$query.isEmpty()
            || system.serverFolders.get(thm$selectedFolder) == null) return;
        var details = system.serverFolders.details(editingServer.ip);
        system.serverFolders.setDetails(editingServer.ip, thm$selectedFolder, details.alias());
        system.save();
    }

    @Inject(method = "editServerCallback", at = @At("HEAD"))
    private void thm$keepServerDetails(boolean confirmed, CallbackInfo ci) {
        THMSystem system = THMSystem.get();
        if (!confirmed || system == null
            || !(serverSelectionList.getSelected() instanceof ServerSelectionList.OnlineServerEntry row)) return;
        ServerData current = row.getServerData();
        if (current.ip.strip().equalsIgnoreCase(editingServer.ip.strip())) return;
        boolean keepOld = false;
        ServerList servers = getServers();
        for (int i = 0; i < servers.size(); i++) {
            ServerData other = servers.get(i);
            if (other != current && other.ip.strip().equalsIgnoreCase(current.ip.strip())) {
                keepOld = true;
                break;
            }
        }
        if (system.serverFolders.changeAddress(current.ip, editingServer.ip, keepOld)) system.save();
    }

    @Unique
    private void thm$layout(int bottom) {
        int sidebarWidth = Math.clamp(width / 4, 106, 170);
        int listX = sidebarWidth + 12;
        int listWidth = width - listX - 6;
        thm$sidebar.updateSizeAndPosition(sidebarWidth, Math.max(0, bottom - 99), 6, 55);
        serverSelectionList.updateSizeAndPosition(listWidth, Math.max(0, bottom - 55), listX, 55);
        thm$search.setRectangle(Math.min(305, listWidth - 12), 18, listX + Math.max(6, (listWidth - 305) / 2), 34);
        int controlWidth = (sidebarWidth - 4) / 3;
        thm$newFolder.setRectangle(controlWidth, 18, 6, 34);
        thm$editFolder.setRectangle(controlWidth, 18, 8 + controlWidth, 34);
        thm$removeFolder.setRectangle(controlWidth, 18, 10 + controlWidth * 2, 34);
        int half = (sidebarWidth - 2) / 2;
        thm$moveUp.setRectangle(half, 18, 6, bottom - 42);
        thm$moveDown.setRectangle(half, 18, 8 + half, bottom - 42);
        thm$organize.setRectangle(sidebarWidth, 18, 6, bottom - 22);
    }

    @Unique
    private void thm$refreshServers() {
        ((ServerSelectionListAccessor) serverSelectionList).thm$refreshEntries();
        if (serverSelectionList.getSelected() == null) serverSelectionList.setSelected(null);
    }

    @Unique
    private void thm$updateControls() {
        ServerFolders folders = THMSystem.get().serverFolders;
        ServerFolders.Folder folder = folders.get(thm$selectedFolder);
        thm$editFolder.active = folder != null;
        thm$removeFolder.active = folder != null;
        var siblings = folder == null ? List.<ServerFolders.Folder>of() : folders.children(folder.parent());
        int index = folder == null ? -1 : siblings.indexOf(folder);
        thm$moveUp.active = index > 0;
        thm$moveDown.active = index >= 0 && index < siblings.size() - 1;
        thm$organize.active = serverSelectionList.getSelected() instanceof ServerSelectionList.OnlineServerEntry;
    }

    @Unique
    private Button thm$moveButton(String label, String tooltip, int direction) {
        return addRenderableWidget(Button.builder(Component.literal(label), button -> {
            var system = THMSystem.get();
            if (system.serverFolders.moveFolder(thm$selectedFolder, direction)) {
                system.save();
                thm$sidebar.rebuild(system.serverFolders, thm$selectedFolder);
                thm$updateControls();
            }
        }).tooltip(Tooltip.create(Component.literal(tooltip))).build());
    }

    @Unique
    private void thm$editFolder(ServerFolders.Folder folder, String target) {
        ServerFolders folders = THMSystem.get().serverFolders;
        String id = folder == null ? "" : folder.id();
        String parent = folder != null ? folder.parent() : folders.get(target) != null ? target : ServerFolders.ROOT;
        minecraft.gui.setScreen(new ServerFolderSidebar.EditScreen(this, folder == null ? "New Server Folder" : "Edit Server Folder",
            "Folder name", folder == null ? "" : folder.name(), parent, id, (name, destination) -> {
                if (!name.isBlank()) {
                    thm$selectedFolder = folders.putFolder(id, name, destination);
                    thm$query = "";
                    thm$search.setValue("");
                }
            }));
    }

    @Unique
    private void thm$deleteFolder() {
        ServerFolders folders = THMSystem.get().serverFolders;
        ServerFolders.Folder folder = folders.get(thm$selectedFolder);
        if (folder == null) return;
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                folders.removeFolder(folder.id());
                thm$selectedFolder = folder.parent().isEmpty() ? ServerFolders.ROOT : folder.parent();
                THMSystem.get().save();
            }
            minecraft.gui.setScreen(this);
        }, Component.literal("Delete " + folder.name() + "?"), Component.literal("Servers in this folder will move to Unfiled.")));
    }

    @Unique
    private void thm$organizeServer() {
        if (!(serverSelectionList.getSelected() instanceof ServerSelectionList.OnlineServerEntry entry)) return;
        var server = entry.getServerData();
        ServerFolders folders = THMSystem.get().serverFolders;
        var details = folders.details(server.ip);
        minecraft.gui.setScreen(new ServerFolderSidebar.EditScreen(this, "Organize " + server.name,
            "Display alias", details.alias(), details.folder(), null,
            (alias, destination) -> folders.setDetails(server.ip, destination, alias)));
    }
}
