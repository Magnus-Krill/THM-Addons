/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

// BepHax 0.5.5 theme by Dekto, adapted for Meteor 26.2.
// https://bep.dek.to/changelog
//To Dekto, if you want this removed then open a issue and ill remove it

package xyz.thm.addon.gui.themes.bephax;

import static com.mojang.blaze3d.platform.InputConstants.KEY_F;
import static com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL;
import static com.mojang.blaze3d.platform.InputConstants.MOD_SUPER;

import com.mojang.datafixers.util.Pair;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.render.DisplayItemUtils;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.Items;

import org.apache.commons.lang3.SystemUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BepModulesScreen extends TabScreen {
    private final BepGuiTheme bepTheme;
    private WCategoryController controller;
    private WWindow searchWindow;
    private WTextBox searchTextBox;

    public BepModulesScreen(BepGuiTheme theme) {
        super(theme, Tabs.get().getFirst());
        this.bepTheme = theme;
    }

    @Override
    public void initWidgets() {
        this.controller = this.add(new WCategoryController()).top().widget();
        if (this.bepTheme.modulesHelpText()) {
            WVerticalList help = this.add(this.theme.verticalList()).pad(6).bottom().widget();
            help.add(this.theme.label("Left click - Toggle module"));
            help.add(this.theme.label("Right click - Open module settings"));
        }

        this.add(new WBrand()).pad(6).bottom().right();
    }

    @Override
    protected void init() {
        super.init();
        this.controller.refresh();
    }

    protected WWindow createCategory(WContainer c, Category category, List<Module> moduleList) {
        WWindow w = this.theme.window(category.name);
        w.id = category.name;
        w.padding = 4;
        w.spacing = 0;
        if (this.theme.categoryIcons()) {
            w.beforeHeaderInit =
                    (wContainer) -> wContainer.add(this.theme.item(category.icon.get())).pad(2);
        }

        c.add(w);
        w.view.scrollOnlyWhenMouseOver = true;
        w.view.hasScrollBar = false;
        w.view.spacing = 2;

        for (Module module : moduleList) {
            w.add(this.theme.module(module)).expandX();
        }

        return w;
    }

    protected void createSearchW(WContainer w, String text) {
        if (!text.isEmpty()) {
            List<Pair<Module, String>> modules = Modules.get().searchTitles(text);
            if (!modules.isEmpty()) {
                WSection section = w.add(this.theme.section("Modules")).expandX().widget();
                section.spacing = 2;
                int count = 0;

                for (Pair<Module, String> p : modules) {
                    if (count >= Config.get().moduleSearchCount.get() || count >= modules.size()) {
                        break;
                    }

                    section.add(this.theme.module(p.getFirst(), p.getSecond())).expandX();
                    ++count;
                }
            }

            Set<Module> settings = Modules.get().searchSettingTitles(text);
            if (!settings.isEmpty()) {
                WSection section = w.add(this.theme.section("Settings")).expandX().widget();
                section.spacing = 2;
                int count = 0;

                for (Module module : settings) {
                    if (count >= Config.get().moduleSearchCount.get() || count >= settings.size()) {
                        break;
                    }

                    section.add(this.theme.module(module)).expandX();
                    ++count;
                }
            }
        }
    }

    protected WWindow createSearch(WContainer c) {
        WWindow w = this.theme.window("Search");
        w.id = "search";
        this.searchWindow = w;
        if (this.theme.categoryIcons()) {
            w.beforeHeaderInit =
                    (wContainer) ->
                            wContainer
                                    .add(this.theme.item(DisplayItemUtils.toStack(Items.COMPASS)))
                                    .pad(2);
        }

        c.add(w);
        w.view.scrollOnlyWhenMouseOver = true;
        w.view.hasScrollBar = false;
        w.view.maxHeight -= 20;
        WVerticalList l = this.theme.verticalList();
        WTextBox text =
                w.add(this.theme.textBox("", "Search modules...")).minWidth(160).expandX().widget();
        text.setFocused(true);
        this.searchTextBox = text;
        text.action =
                () -> {
                    l.clear();
                    this.createSearchW(l, text.get());
                };
        w.add(l).expandX();
        this.createSearchW(l, text.get());
        return w;
    }

    @Override
    public boolean keyPressed(KeyEvent value) {
        if (this.locked) {
            return false;
        } else {
            boolean control =
                    value.modifiers() == (SystemUtils.IS_OS_MAC ? MOD_SUPER : MOD_CONTROL);
            if (control && value.key() == KEY_F) {
                if (this.searchWindow != null) {
                    this.searchWindow.setExpanded(true);
                }

                if (this.searchTextBox != null) {
                    this.searchTextBox.setFocused(true);
                    this.searchTextBox.setCursorMax();
                }

                return true;
            } else {
                return super.keyPressed(value);
            }
        }
    }

    protected Cell<WWindow> createFavorites(WContainer c) {
        boolean hasFavorites =
                Modules.get().getAll().stream().anyMatch((module) -> module.favorite);
        if (!hasFavorites) {
            return null;
        } else {
            WWindow w = this.theme.window("Favorites");
            w.id = "favorites";
            w.padding = 4;
            w.spacing = 0;
            if (this.theme.categoryIcons()) {
                w.beforeHeaderInit =
                        (wContainer) ->
                                wContainer
                                        .add(
                                                this.theme.item(
                                                        DisplayItemUtils.toStack(
                                                                Items.NETHER_STAR)))
                                        .pad(2);
            }

            Cell<WWindow> cell = c.add(w);
            w.view.scrollOnlyWhenMouseOver = true;
            w.view.hasScrollBar = false;
            w.view.spacing = 2;
            this.createFavoritesW(w);
            return cell;
        }
    }

    protected boolean createFavoritesW(WWindow w) {
        List<Module> modules = new ArrayList<>();

        for (Module module : Modules.get().getAll()) {
            if (module.favorite) {
                modules.add(module);
            }
        }

        modules.sort((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.name, o2.name));

        for (Module module : modules) {
            w.add(this.theme.module(module)).expandX();
        }

        return !modules.isEmpty();
    }

    @Override
    public boolean toClipboard() {
        return NbtUtils.toClipboard(Modules.get());
    }

    @Override
    public boolean fromClipboard() {
        return NbtUtils.fromClipboard(Modules.get());
    }

    @Override
    public void reload() {}

    protected class WCategoryController extends WContainer {
        private Cell<WWindow> favorites;

        @Override
        public void init() {
            List<Module> moduleList = new ArrayList<>();

            for (Category category : Modules.loopCategories()) {
                for (Module module : Modules.get().getGroup(category)) {
                    if (!Config.get().hiddenModules.get().contains(module)) {
                        moduleList.add(module);
                    }
                }

                if (!moduleList.isEmpty()) {
                    BepModulesScreen.this.createCategory(this, category, moduleList);
                    moduleList.clear();
                }
            }

            BepModulesScreen.this.createSearch(this);
            this.refresh();
        }

        protected void refresh() {
            if (this.favorites == null) {
                this.favorites = BepModulesScreen.this.createFavorites(this);
            } else {
                this.favorites.widget().clear();
                if (!BepModulesScreen.this.createFavoritesW(this.favorites.widget())) {
                    this.remove(this.favorites);
                    this.favorites = null;
                }
            }
        }

        @Override
        protected void onCalculateWidgetPositions() {
            double pad = this.theme.scale(8);
            double step = this.theme.scale(40);
            double x = this.x + pad;
            double y = this.y + BepModulesScreen.this.bepTheme.topBarHeight() + pad;

            for (Cell<?> cell : this.cells) {
                double windowWidth = Utils.getWindowWidth();
                double windowHeight = Utils.getWindowHeight();
                double w = cell.widget().width;
                double h = cell.widget().height;
                if (x + w > windowWidth) {
                    y += step;
                    x = Math.max(0, windowWidth - w - pad);
                }

                if (y > windowHeight) {
                    y = Math.max(0, windowHeight / 2 - h / 2);
                }

                cell.x = x;
                cell.y = y;
                cell.width = w;
                cell.height = h;
                cell.alignWidget();
                x += w + pad;
            }
        }
    }

    private static class WBrand extends WWidget {

        @Override
        protected void onCalculateSize() {
            this.width = this.theme.textWidth(BepGuiTheme.Propaganda, BepGuiTheme.Propaganda.length(), true);
            this.height = this.theme.textHeight(true);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            BepGuiTheme theme = (BepGuiTheme) this.theme;
            renderer.text(BepGuiTheme.Propaganda, this.x, this.y, theme.accentColor.get(), true);
        }
    }
}
