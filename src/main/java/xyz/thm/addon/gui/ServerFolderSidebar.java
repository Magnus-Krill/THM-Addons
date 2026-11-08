/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import xyz.thm.addon.system.ServerFolders;
import xyz.thm.addon.system.THMSystem;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ServerFolderSidebar extends ObjectSelectionList<ServerFolderSidebar.Row> {
    private final Consumer<String> onSelect;
    private String dropTarget;

    public ServerFolderSidebar(Minecraft client, Consumer<String> onSelect) {
        super(client, 100, 100, 0, 20);
        this.onSelect = onSelect;
    }

    public void rebuild(ServerFolders folders, String selected) {
        clearEntries();
        addEntry(new Row(ServerFolders.ALL, "All Servers", 0));
        addEntry(new Row(ServerFolders.ROOT, "Unfiled", 0));
        Deque<Row> pending = new ArrayDeque<>();
        pushFolders(pending, folders, ServerFolders.ROOT, 0);
        while (!pending.isEmpty()) {
            Row row = pending.pop();
            addEntry(row);
            pushFolders(pending, folders, row.id, row.depth + 1);
        }
        for (Row row : children()) {
            if (row.id.equals(selected)) {
                setSelected(row);
                break;
            }
        }
    }

    private void pushFolders(Deque<Row> pending, ServerFolders folders, String parent, int depth) {
        for (ServerFolders.Folder folder : folders.children(parent).reversed()) {
            pending.push(new Row(folder.id(), folder.name(), depth));
        }
    }

    @Override
    public int getRowWidth() {
        return getWidth() - 12;
    }

    public String folderAt(double x, double y) {
        Row row = isMouseOver(x, y) ? getEntryAtPosition(x, y) : null;
        return row == null ? null : row.id;
    }

    public void setDropTarget(String id) {
        dropTarget = id;
    }

    public final class Row extends ObjectSelectionList.Entry<Row> {
        private final String id;
        private final String name;
        private final int depth;

        private Row(String id, String name, int depth) {
            this.id = id;
            this.name = name;
            this.depth = depth;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
            if (id.equals(dropTarget)) {
                graphics.fill(getContentX(), getContentY(), getContentRight(), getContentY() + getContentHeight(), 0xCC365B79);
            }
            int indent = Math.min(depth * 8, getContentWidth() / 3);
            graphics.text(minecraft.font, minecraft.font.plainSubstrByWidth(name, getContentWidth() - indent - 3),
                getContentX() + indent + 2, getContentY() + 4, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0) return false;
            onSelect.accept(id);
            return true;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!event.isSelection()) return super.keyPressed(event);
            onSelect.accept(id);
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.literal(name);
        }
    }

    public static final class EditScreen extends Screen {
        private final Screen parent;
        private final String textLabel;
        private final String excludedFolder;
        private final BiConsumer<String, String> save;
        private String destination;
        private String textValue;

        public EditScreen(Screen parent, String title, String textLabel, String initialText, String destination,
                          String excludedFolder, BiConsumer<String, String> save) {
            super(Component.literal(title));
            this.parent = parent;
            this.textLabel = textLabel;
            this.textValue = initialText;
            this.destination = destination;
            this.excludedFolder = excludedFolder;
            this.save = save;
        }

        @Override
        protected void init() {
            int x = (width - 224) / 2;
            int y = height / 2 - 42;
            EditBox text = addRenderableWidget(new EditBox(font, x, y, 224, 20, Component.literal(textLabel)));
            text.setMaxLength(80);
            text.setHint(Component.literal(textLabel));
            text.setValue(textValue);
            ServerFolders folders = THMSystem.get().serverFolders;
            addRenderableWidget(CycleButton.builder(id -> Component.literal(folders.path(id)), destination)
                .withValues(folders.destinations(excludedFolder))
                .create(x, y + 26, 224, 20, Component.literal("Folder"), (button, id) -> destination = id));
            Button done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
                save.accept(text.getValue(), destination);
                THMSystem.get().save();
                onClose();
            }).bounds(x, y + 56, 110, 20).build());
            done.active = excludedFolder == null || !textValue.isBlank();
            text.setResponder(value -> {
                textValue = value;
                done.active = excludedFolder == null || !value.isBlank();
            });
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(x + 114, y + 56, 110, 20).build());
            setInitialFocus(text);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);
            graphics.centeredText(font, title, width / 2, height / 2 - 64, 0xFFFFFFFF);
        }

        @Override
        public void onClose() {
            minecraft.gui.setScreen(parent);
        }
    }
}
