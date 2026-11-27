/*
 * This file is part of THM Addons — https://github.com/Magnus-Krill/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
By using this code you agree to the license terms and to keep your repo public.

 */





package xyz.thm.addon.settings;

import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.lang.reflect.Field;
import java.util.function.Supplier;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Click-GUI sounds shown in Meteor's GUI tab. */
public final class GuiSoundSettings {
    public enum Sound implements DescribedOption {
        Click(() -> SoundEvents.UI_BUTTON_CLICK.value(), "Vanilla button click."),
        Hat(() -> SoundEvents.NOTE_BLOCK_HAT.value(), "Short note block tick."),
        Snare(() -> SoundEvents.NOTE_BLOCK_SNARE.value(), "Note block snare."),
        Pling(() -> SoundEvents.NOTE_BLOCK_PLING.value(), "Bright note block pling."),
        Bit(() -> SoundEvents.NOTE_BLOCK_BIT.value(), "Retro square-wave note."),
        Harp(() -> SoundEvents.NOTE_BLOCK_HARP.value(), "Soft harp note."),
        Bell(() -> SoundEvents.NOTE_BLOCK_BELL.value(), "Bell note."),
        Chime(() -> SoundEvents.NOTE_BLOCK_CHIME.value(), "Light chime note."),
        Xylophone(() -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), "Wooden xylophone note."),
        CowBell(() -> SoundEvents.NOTE_BLOCK_COW_BELL.value(), "Cow bell note."),
        Amethyst(() -> SoundEvents.AMETHYST_BLOCK_CHIME, "Amethyst chime."),
        ExperienceOrb(() -> SoundEvents.EXPERIENCE_ORB_PICKUP, "Experience pickup ding."),
        ItemPickup(() -> SoundEvents.ITEM_PICKUP, "Item pickup pop."),
        StoneButton(() -> SoundEvents.STONE_BUTTON_CLICK_ON, "Stone button press."),
        WoodenButton(() -> SoundEvents.WOODEN_BUTTON_CLICK_ON, "Wooden button press."),
        Lever(() -> SoundEvents.LEVER_CLICK, "Lever flip."),
        ItemFrame(() -> SoundEvents.ITEM_FRAME_ROTATE_ITEM, "Item frame turn."),
        Toast(() -> SoundEvents.UI_TOAST_IN, "Toast pop-in."),
        ToastOut(() -> SoundEvents.UI_TOAST_OUT, "Toast slide-out."),
        BaseDrum(() -> SoundEvents.NOTE_BLOCK_BASEDRUM.value(), "Deep note block drum."),
        Guitar(() -> SoundEvents.NOTE_BLOCK_GUITAR.value(), "Plucked guitar note."),
        Flute(() -> SoundEvents.NOTE_BLOCK_FLUTE.value(), "Airy flute note."),
        Banjo(() -> SoundEvents.NOTE_BLOCK_BANJO.value(), "Twangy banjo note."),
        IronXylophone(() -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), "Metallic xylophone note."),
        PageTurn(() -> SoundEvents.BOOK_PAGE_TURN, "Book page turn."),
        Tripwire(() -> SoundEvents.TRIPWIRE_CLICK_ON, "Tripwire click."),
        CopperBulb(() -> SoundEvents.COPPER_BULB_TURN_ON, "Copper bulb switch."),
        BundleIn(() -> SoundEvents.BUNDLE_INSERT, "Bundle insert."),
        BundleOut(() -> SoundEvents.BUNDLE_REMOVE_ONE, "Bundle remove."),
        Bookshelf(() -> SoundEvents.CHISELED_BOOKSHELF_INSERT, "Book slid into a shelf.");

        private final Supplier<SoundEvent> event;
        private final String description;
        Sound(Supplier<SoundEvent> event, String description) { this.event = event; this.description = description; }
        @Override public String description() { return description; }
    }

    private static final ClassValue<Boolean> MODULE_WIDGETS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                for (Class<?> c = type; c != null && c != WWidget.class; c = c.getSuperclass()) {
                    for (Field field : c.getDeclaredFields()) if (Module.class.isAssignableFrom(field.getType())) return true;
                }
            } catch (LinkageError ignored) {
                // A field type from a missing optional mod.
            }
            return false;
        }
    };

    private final Setting<Boolean> enabled;
    public final Event hover, optionHover, click, rightClick, toggleOn, toggleOff, checkOn, checkOff,
        slider, dropdown, expand, collapse, typing;

    public GuiSoundSettings(SettingGroup group) {
        enabled = group.add(new BoolSetting.Builder()
            .name("gui-sounds").description("Play sounds in the click GUI.")
            .defaultValue(true).build());
        hover = new Event(group, "hover", "Hovering a module.", Sound.Hat, 25, 1.8);
        optionHover = new Event(group, "option-hover", "Hovering buttons, checkboxes, sliders and fields.", Sound.Hat, 15, 2);
        click = new Event(group, "click", "Clicking buttons.", Sound.Click, 30, 1.4);
        rightClick = new Event(group, "right-click", "Opening module settings.", Sound.Bit, 40, 1.2);
        toggleOn = new Event(group, "toggle-on", "Enabling a module.", Sound.Pling, 40, 1.6);
        toggleOff = new Event(group, "toggle-off", "Disabling a module.", Sound.Pling, 40, 1.1);
        checkOn = new Event(group, "check-on", "Ticking a checkbox.", Sound.WoodenButton, 35, 1.5);
        checkOff = new Event(group, "check-off", "Unticking a checkbox.", Sound.WoodenButton, 35, 1.1);
        slider = new Event(group, "slider", "Moving a slider.", Sound.Hat, 20, 1.3);
        dropdown = new Event(group, "dropdown", "Opening or closing a dropdown.", Sound.Chime, 30, 1.5);
        expand = new Event(group, "expand", "Expanding a category or section.", Sound.PageTurn, 60, 1.1);
        collapse = new Event(group, "collapse", "Collapsing a category or section.", Sound.PageTurn, 60, 0.8);
        typing = new Event(group, "typing", "Typing in a text field.", Sound.Hat, 12, 1.6);
    }

    /** Theme-independent: every theme's module button keeps its {@link Module}. */
    public static boolean isModuleWidget(Object widget) {
        return MODULE_WIDGETS.get(widget.getClass());
    }

    public static boolean guiOpen() {
        return mc.gui.screen() instanceof WidgetScreen;
    }

    public final class Event {
        private final Setting<Boolean> on;
        private final Setting<Sound> sound;
        private final Setting<Integer> volume;
        private final Setting<Double> pitch;
        private long lastPlayed;

        Event(SettingGroup group, String name, String description, Sound sound, int volume, double pitch) {
            on = group.add(new BoolSetting.Builder()
                .name(name + "-sound").description(description)
                .defaultValue(true).visible(enabled::get).build());
            this.sound = group.add(new EnumSetting.Builder<Sound>()
                .name(name + "-type").description("Sound to play.")
                .defaultValue(sound).visible(this::shown).onChanged(v -> preview()).build());
            this.volume = group.add(new IntSetting.Builder()
                .name(name + "-volume").description("Volume in percent.")
                .defaultValue(volume).range(0, 100).sliderRange(0, 100).visible(this::shown).onChanged(v -> preview()).build());
            this.pitch = group.add(new DoubleSetting.Builder()
                .name(name + "-pitch").description("Pitch.")
                .defaultValue(pitch).range(0.5, 2).sliderRange(0.5, 2).decimalPlaces(2).visible(this::shown).build());
        }

        private boolean shown() {
            return enabled.get() && on.get();
        }

        public void play() {
            long now = Util.getMillis();
            // Fast sliders and expand-all buttons would otherwise stack dozens of copies.
            if (now - lastPlayed < 40) return;
            if (shown() && volume.get() > 0 && mc.getSoundManager() != null) {
                lastPlayed = now;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(sound.get().event.get(), pitch.get().floatValue(), volume.get() / 100f));
            }
        }

        // Loading saved settings also fires onChanged; only preview while the GUI is open.
        private void preview() {
            if (guiOpen()) play();
        }
    }
}
