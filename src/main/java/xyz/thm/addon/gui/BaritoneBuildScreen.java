/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */







package xyz.thm.addon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BlockSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.Settings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import xyz.thm.addon.modules.BetterBaritoneBuild;
import xyz.thm.addon.settings.DescribedOption;

public final class BaritoneBuildScreen extends WindowScreen {
    private final BetterBaritoneBuild module;
    private final Settings editor = new Settings();
    private final Setting<Block> from = editor.getDefaultGroup().add(new BlockSetting.Builder()
        .name("schematic-block").description("Block requested by the schematic.").defaultValue(Blocks.COBBLESTONE).build());
    private final Setting<Block> to = editor.getDefaultGroup().add(new BlockSetting.Builder()
        .name("replacement-block").description("Block to place or accept.").defaultValue(Blocks.STONE_BRICKS).build());
    private final Setting<RuleMode> mode = editor.getDefaultGroup().add(new EnumSetting.Builder<RuleMode>()
        .name("rule").description("How to use the replacement block.").defaultValue(RuleMode.Replace).build());
    private WLabel status;
    private WVerticalList content;
    private String tab = "Settings";

    public BaritoneBuildScreen(GuiTheme theme, BetterBaritoneBuild module) {
        super(theme, "Better Baritone Build");
        this.module = module;
    }

    @Override public void initWidgets() {
        status = add(theme.label(module.status(), 500)).expandX().widget();
        WHorizontalList actions = add(theme.horizontalList()).expandX().widget();
        WButton toggle = actions.add(theme.button(module.isActive() ? "Disable" : "Enable")).widget();
        toggle.action = () -> { module.toggle(); reload(); };
        WButton resume = actions.add(theme.button("Resume build")).widget();
        resume.action = module::retry;
        WHorizontalList tabs = add(theme.horizontalList()).expandX().widget();
        for (String name : new String[] { "Settings", "Block rules", "Storage" }) {
            WButton button = tabs.add(theme.button(name)).expandX().widget();
            button.action = () -> { tab = name; renderContent(); };
        }
        content = add(theme.verticalList()).expandX().widget();
        renderContent();
    }

    private void renderContent() {
        content.clear();
        switch (tab) {
            case "Settings" -> {
                content.add(theme.label("Enable before starting a Baritone build to apply build rules.", 500));
                content.add(theme.settings(module.settings)).expandX();
            }
            case "Block rules" -> {
                content.add(theme.label("Replace changes what gets built. Accept keeps matching existing blocks.", 500));
                content.add(theme.settings(editor)).expandX();
                WButton addRule = content.add(theme.button("Add rule")).expandX().widget();
                addRule.action = () -> {
                    module.putRule(from.get(), to.get(), mode.get() == RuleMode.Accept);
                    renderContent();
                };
                for (boolean accept : new boolean[] { false, true }) {
                    content.add(theme.horizontalSeparator(accept ? "Accept existing" : "Replace schematic blocks")).expandX();
                    WTable table = content.add(theme.table()).expandX().widget();
                    for (String rule : module.ruleList(accept)) {
                        table.add(theme.label(rule.replace("minecraft:", "").replace("=", " → "))).expandX();
                        WButton remove = table.add(theme.button("Remove")).widget();
                        remove.action = () -> { module.removeRule(rule, accept); renderContent(); };
                        table.row();
                    }
                    if (module.ruleList(accept).isEmpty()) table.add(theme.label("No rules."));
                }
            }
            case "Storage" -> {
                content.add(theme.label("Enable Link storage, then open chests, barrels or placed shulkers to index them.", 500));
                content.add(theme.label("Only contents seen on this server and in this dimension are used.", 500));
                WButton refresh = content.add(theme.button("Refresh list")).expandX().widget();
                refresh.action = this::renderContent;
                WTable table = content.add(theme.table()).expandX().widget();
                for (var storage : module.storage()) {
                    table.add(theme.label(storage.pos().toShortString() + " • " + storage.items().values().stream().mapToInt(Integer::intValue).sum() + " items")).expandX();
                    WButton inspect = table.add(theme.button("Contents")).widget();
                    inspect.action = () -> {
                        content.clear();
                        content.add(theme.label("Storage " + storage.pos().toShortString()));
                        storage.items().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                            .forEach(entry -> content.add(theme.label(entry.getValue() + " × " + entry.getKey())));
                        WButton back = content.add(theme.button("Back")).widget();
                        back.action = this::renderContent;
                    };
                    WButton forget = table.add(theme.button("Forget")).widget();
                    forget.action = () -> { module.forgetStorage(storage.pos()); renderContent(); };
                    table.row();
                }
                if (module.storage().isEmpty()) table.add(theme.label("No indexed storage in this dimension."));
            }
        }
    }

    @Override public void tick() {
        super.tick();
        status.set(module.status());
    }

    public enum RuleMode implements DescribedOption {
        Replace, Accept;

        @Override public String description() {
            return this == Replace ? "Build the replacement instead of the schematic block."
                : "Keep the replacement when it already exists in the world.";
        }
    }
}
