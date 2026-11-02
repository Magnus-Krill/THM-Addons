/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.WMeteorButton;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetScreenRootMixinTest {
    @ParameterizedTest
    @ValueSource(doubles = {1, 1.25, 1.5, 2})
    void layoutRefreshKeepsStationaryCursorClickableAtDisplayScale(double scale) throws Exception {
        var button = new WPressable() {};
        button.x = 550;
        button.y = 350;
        button.width = 100;
        button.height = 50;
        int[] clicks = {0};
        button.action = () -> clicks[0]++;

        double mouseX = 600, mouseY = 375;
        var click = new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(0, 0));
        button.mouseMoved(mouseX / scale, mouseY / scale, mouseX / scale, mouseY / scale);
        assertEquals(scale == 1, button.mouseClicked(click, false));
        button.mouseReleased(click);
        clicks[0] = 0;
        var refresh = WidgetScreenRootMixin.class.getDeclaredMethod("thm$layoutMouse", Args.class,
            GuiRenderer.class, double.class, double.class, double.class);
        refresh.setAccessible(true);
        var mixin = new WidgetScreenRootMixin() {};

        for (int layout = 0; layout < 3; layout++) {
            var args = new MouseArgs(mouseX / scale, mouseY / scale);
            refresh.invoke(mixin, args, null, mouseX, mouseY, 0);
            assertEquals(mouseX, (double) args.get(0));
            assertEquals(mouseY, (double) args.get(1));
            assertEquals((double) args.get(0), (double) args.get(2), "A layout refresh must not simulate dragging.");
            assertEquals((double) args.get(1), (double) args.get(3));
            button.mouseMoved(args.get(0), args.get(1), args.get(2), args.get(3));
            assertTrue(button.mouseClicked(click, false));
            button.mouseReleased(click);
        }
        assertEquals(3, clicks[0]);
    }

    @ParameterizedTest
    @ValueSource(doubles = {1, 1.25, 1.5, 2})
    void customButtonRowsAndTabGridReceiveClicksThroughTheWindowView(double scale) throws Exception {
        var refresh = WidgetScreenRootMixin.class.getDeclaredMethod("thm$layoutMouse", Args.class,
            GuiRenderer.class, double.class, double.class, double.class);
        refresh.setAccessible(true);
        var mixin = new WidgetScreenRootMixin() {};

        for (WContainer row : new WContainer[] {new WHorizontalList(), new WTable()}) {
            var view = new WView() {};
            view.x = 500; view.y = 300; view.width = 500; view.height = 500;
            row.x = 550; row.y = 350; row.width = 100; row.height = 50;
            var button = new WMeteorButton("Stop builder", null);
            button.x = row.x; button.y = row.y; button.width = row.width; button.height = row.height;
            row.parent = view;
            button.parent = row;
            view.cells.add(new Cell<>(row));
            row.cells.add(new Cell<>(button));
            int[] clicks = {0};
            button.action = () -> clicks[0]++;

            view.mouseMoved(600, 375, 600, 375);
            for (int layout = 0; layout < 3; layout++) {
                var args = new MouseArgs(600 / scale, 375 / scale);
                refresh.invoke(mixin, args, null, 600, 375, 0);
                view.mouseMoved(args.get(0), args.get(1), args.get(2), args.get(3));
                assertTrue(view.mouseOver);
                var click = new MouseButtonEvent(600, 375, new MouseButtonInfo(0, 0));
                assertTrue(view.mouseClicked(click, false));
                view.mouseReleased(click);
            }
            assertEquals(3, clicks[0], row.getClass().getSimpleName());
        }
    }

    private static final class MouseArgs extends Args {
        private MouseArgs(double x, double y) {
            super(new Object[] {x, y, x, y});
        }

        @Override
        public <T> void set(int index, T value) {
            values[index] = value;
        }

        @Override
        public void setAll(Object... args) {
            assertEquals(values.length, args.length);
            System.arraycopy(args, 0, values, 0, args.length);
        }
    }
}
